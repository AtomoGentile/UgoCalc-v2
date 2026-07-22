'use strict';

// Lambda: GET /bias
// Returns anonymous aggregate bias per model×segment combination across ALL accounts.
// Never returns raw rows or per-account data — only the aggregate (mean) per key.
// IAM: dynamodb:Scan on ugocalc-events is granted only to this Lambda's execution role.
//      No other Lambda can call Scan; all others are restricted to Query with LeadingKeys.
// Requires Cognito authentication (caller must have globalBiasEnabled toggle active —
// enforced server-side by checking ugocalc-accounts item's globalBiasEnabled flag).

const { DynamoDBClient, ScanCommand, GetItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');
const { verifyToken } = require('../utils/verifyToken');
const { resolveAccountId } = require('../utils/resolveAccount');

const EVENTS_TABLE = process.env.EVENTS_TABLE || 'ugocalc-events';
const ACCOUNTS_TABLE = process.env.ACCOUNTS_TABLE || 'ugocalc-accounts';
const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
  const authHeader = event.headers?.authorization || event.headers?.Authorization;
  const callerLoginId = await verifyToken(authHeader);
  if (!callerLoginId) return response(401, { error: 'Unauthorized' });

  // Verify globalBiasEnabled is active for this account
  const accountId = await resolveAccountId(callerLoginId);
  const accountItem = await dynamo.send(new GetItemCommand({
    TableName: ACCOUNTS_TABLE,
    Key: marshall({ PK: callerLoginId }),
  }));
  const acct = accountItem.Item ? unmarshall(accountItem.Item) : {};
  if (!acct.globalBiasEnabled) {
    return response(403, { error: 'Global bias not enabled for this account' });
  }

  // Scan ALL inference/benchmark events — projection only to segment key and bias field
  const allItems = [];
  let lastKey;
  do {
    const scanResult = await dynamo.send(new ScanCommand({
      TableName: EVENTS_TABLE,
      ProjectionExpression: 'segmentKey, aiModelId, payloadJson',
      FilterExpression: 'eventType = :et',
      ExpressionAttributeValues: marshall({ ':et': 'inference' }),
      ExclusiveStartKey: lastKey,
    }));
    allItems.push(...(scanResult.Items || []).map(unmarshall));
    lastKey = scanResult.LastEvaluatedKey;
  } while (lastKey);

  // Aggregate: mean bias per (aiModelId × segmentKey), never expose per-account values
  const buckets = {};
  for (const item of allItems) {
    let payload;
    try { payload = JSON.parse(item.payloadJson || '{}'); } catch { continue; }
    const bias = payload.biasCorrectionPercent;
    if (typeof bias !== 'number') continue;
    const key = `${item.aiModelId || 'unknown'}|${item.segmentKey || 'unknown'}`;
    if (!buckets[key]) buckets[key] = { sum: 0, count: 0, aiModelId: item.aiModelId, segmentKey: item.segmentKey };
    buckets[key].sum += bias;
    buckets[key].count += 1;
  }

  const aggregated = Object.values(buckets).map(b => ({
    aiModelId: b.aiModelId,
    segmentKey: b.segmentKey,
    meanBiasPercent: Math.round((b.sum / b.count) * 100) / 100,
    sampleCount: b.count,
  }));

  return response(200, { aggregated });
};

function response(statusCode, body) {
  return { statusCode, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
