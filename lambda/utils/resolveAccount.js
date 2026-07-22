'use strict';

// resolveAccount.js — looks up the canonical accountId that a loginId has access to.
// Schema: ugocalc-accounts table
//   PK: loginId (Cognito sub / email)
//   accountId: the canonical data owner (usually == loginId for non-shared accounts)
//
// An invite creates an item { PK: inviteeLoginId, accountId: ownerLoginId }.
// The owner's own item has { PK: ownerLoginId, accountId: ownerLoginId }.
// All Lambda functions call this before any DynamoDB read/write to ensure they
// always operate on the canonical accountId, not the raw loginId.

const { DynamoDBClient, GetItemCommand, PutItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');

const ACCOUNTS_TABLE = process.env.ACCOUNTS_TABLE || 'ugocalc-accounts';
const dynamo = new DynamoDBClient({});

async function resolveAccountId(loginId) {
  const result = await dynamo.send(new GetItemCommand({
    TableName: ACCOUNTS_TABLE,
    Key: marshall({ PK: loginId }),
  }));

  if (!result.Item) {
    // First login: create self-mapping and return loginId as accountId.
    await dynamo.send(new PutItemCommand({
      TableName: ACCOUNTS_TABLE,
      Item: marshall({ PK: loginId, accountId: loginId }),
      ConditionExpression: 'attribute_not_exists(PK)',
    })).catch(() => { /* race-safe: ignore if already exists */ });
    return loginId;
  }

  const item = unmarshall(result.Item);
  return item.accountId || loginId;
}

module.exports = { resolveAccountId };
