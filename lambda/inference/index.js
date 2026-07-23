'use strict';

// Lambda: POST /inference  — salva un record di telemetria V2 con ground truth
//         GET  /inference  — restituisce tutti i record inference dell'account (per sync cross-device)
// Scrittura condizionale su eventId per idempotenza.
// Account condivisi: PK = accountId (risolto via ugocalc-accounts), non il loginId raw.

const { DynamoDBClient, PutItemCommand, QueryCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');
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

  const method = event.requestContext?.http?.method || event.httpMethod || 'POST';

  if (method === 'GET') {
    return handleGet(tokenUserId);
  }
  return handlePost(event, tokenUserId);
};

// ── GET /inference ────────────────────────────────────────────────────────────
async function handleGet(tokenUserId) {
  const accountId = await resolveAccountId(tokenUserId);

  try {
    const result = await dynamo.send(new QueryCommand({
      TableName: EVENTS_TABLE,
      KeyConditionExpression: 'PK = :pk',
      FilterExpression: 'eventType = :et',
      ExpressionAttributeValues: marshall({
        ':pk': accountId,
        ':et': 'inference',
      }),
    }));

    const records = (result.Items || []).map(item => {
      const row = unmarshall(item);
      try {
        return JSON.parse(row.payloadJson);
      } catch {
        return null;
      }
    }).filter(Boolean);

    return response(200, { records });
  } catch (err) {
    console.error('DynamoDB query error:', err);
    return response(500, { error: 'Internal error' });
  }
}

// ── POST /inference ───────────────────────────────────────────────────────────
async function handlePost(event, tokenUserId) {
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

  const accountId = await resolveAccountId(tokenUserId);

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
      ConditionExpression: 'attribute_not_exists(SK)',
    }));
    return response(201, { ok: true });
  } catch (err) {
    if (err.name === 'ConditionalCheckFailedException') {
      return response(409, { ok: true, duplicate: true });
    }
    console.error('DynamoDB error:', err);
    return response(500, { error: 'Internal error' });
  }
}

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  };
}
