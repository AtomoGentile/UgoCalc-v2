'use strict';

// Lambda: POST /benchmark
// Riceve un evento benchmark dall'app Android (CloudSyncManager) e lo scrive in DynamoDB.
// Scrittura condizionale su eventId per garantire idempotenza lato server.
// IAM: permesso solo dynamodb:PutItem sulle tabelle ugocalc-events.

const { DynamoDBClient, PutItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall } = require('@aws-sdk/util-dynamodb');

const EVENTS_TABLE = process.env.EVENTS_TABLE || 'ugocalc-events';
const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
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
        eventType: 'benchmark',
        createdAt: new Date().toISOString(),
        sessionMode: payload.sessionMode ?? '',
        aiModelId: payload.aiModelId ?? '',
        strategy: payload.strategy ?? '',
        foodCategory: payload.foodCategory ?? '',
        segmentKey,
        payloadJson: JSON.stringify(payload),
      }, { removeUndefinedValues: true }),
      // Scrittura condizionale: rifiuta silenziosamente duplicati (idempotenza).
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
