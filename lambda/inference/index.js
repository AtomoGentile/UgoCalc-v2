'use strict';

// Lambda: POST /inference
// Riceve un evento inference (telemetria pipeline V2) dall'app e lo scrive in DynamoDB.
// Scrittura condizionale su eventId per idempotenza.
// Bedrock viene invocato tramite ruolo IAM (nessuna chiave statica).
// IAM: dynamodb:PutItem su ugocalc-events + bedrock:InvokeModel se inference AI è locale alla Lambda.
// Richiede autenticazione Cognito via Authorization header (firma RS256 verificata).
// Account condivisi: PK = accountId (risolto via ugocalc-accounts), non il loginId raw.

const { DynamoDBClient, PutItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall } = require('@aws-sdk/util-dynamodb');
const { verifyToken } = require('../utils/verifyToken');
const { resolveAccountId } = require('../utils/resolveAccount');

const EVENTS_TABLE = process.env.EVENTS_TABLE || 'ugocalc-events';
const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
  const authHeader = event.headers?.authorization || event.headers?.Authorization;
  const tokenUserId = await verifyToken(authHeader);

  if (!tokenUserId) {
    return response(401, { error: 'Unauthorized: missing or invalid Authorization header' });
  }

  let body;
  try {
    body = JSON.parse(event.body || '{}');
  } catch {
    return response(400, { error: 'Invalid JSON body' });
  }

  const { eventId, userId, payload } = body;
  if (!eventId || !userId || !payload) {
    return response(400, { error: 'Missing required fields: eventId, userId, payload' });
  }

  // Resolve canonical accountId (handles shared accounts)
  const accountId = await resolveAccountId(tokenUserId);

  // userId in body must match either the loginId or the resolved accountId
  if (userId !== tokenUserId && userId !== accountId) {
    return response(403, { error: 'Forbidden: userId mismatch' });
  }

  const segmentKey = [
    payload.sessionMode ?? '',
    payload.strategy ?? '',
    payload.foodCategory ?? '',
  ].join('#');

  try {
    await dynamo.send(new PutItemCommand({
      TableName: EVENTS_TABLE,
      Item: marshall({
        PK: accountId,
        SK: eventId,
        eventType: 'inference',
        createdAt: new Date().toISOString(),
        sessionMode: payload.sessionMode ?? '',
        aiModelId: payload.aiModelId ?? '',
        strategy: payload.strategy ?? '',
        foodCategory: payload.foodCategory ?? '',
        segmentKey,
        payloadJson: JSON.stringify(payload),
      }, { removeUndefinedValues: true }),
      ConditionExpression: 'attribute_not_exists(PK)',
    }));
    return response(201, { ok: true });
  } catch (err) {
    if (err.name === 'ConditionalCheckFailedException') {
      return response(409, { ok: true, duplicate: true });
    }
    console.error('DynamoDB error:', err);
    return response(500, { error: 'Internal error' });
  }
};

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}
