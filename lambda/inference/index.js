'use strict';

// Lambda: POST /inference
// Riceve un evento inference (telemetria pipeline V2) dall'app e lo scrive in DynamoDB.
// Scrittura condizionale su eventId per idempotenza.
// Bedrock viene invocato tramite ruolo IAM (nessuna chiave statica).
// IAM: dynamodb:PutItem su ugocalc-events + bedrock:InvokeModel se inference AI è locale alla Lambda.
// Richiede autenticazione Cognito via Authorization header.

const { DynamoDBClient, PutItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall } = require('@aws-sdk/util-dynamodb');
const { CognitoIdentityServiceProvider } = require('@aws-sdk/client-cognito-identity-service-provider');

const EVENTS_TABLE = process.env.EVENTS_TABLE || 'ugocalc-events';
const USER_POOL_ID = process.env.USER_POOL_ID || 'us-east-1_XXXXXXXXX';
const dynamo = new DynamoDBClient({});
const cognito = new CognitoIdentityServiceProvider({ region: 'us-east-1' });

function extractUserIdFromToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }

  const token = authHeader.substring(7);

  try {
    const decoded = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf-8'));
    return decoded.sub || decoded['cognito:username'] || null;
  } catch (e) {
    console.error('Token decode error:', e);
    return null;
  }
}

exports.handler = async (event) => {
  // Verifica Authorization header
  const authHeader = event.headers?.authorization || event.headers?.Authorization;
  const tokenUserId = extractUserIdFromToken(authHeader);

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

  // Verifica che userId nel body corrisponda a quello nel token
  if (userId !== tokenUserId) {
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
        PK: userId,
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
