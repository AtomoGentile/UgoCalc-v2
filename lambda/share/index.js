'use strict';

// Lambda: POST /share
// Body: { inviteeEmail: string }
// Invites an existing Cognito user to share the caller's account.
// Creates a row { PK: inviteeEmail, accountId: callerAccountId } in ugocalc-accounts.
// IAM: dynamodb:PutItem on ugocalc-accounts only.
// Requires Cognito authentication.

const { DynamoDBClient, PutItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall } = require('@aws-sdk/util-dynamodb');
const { verifyToken } = require('../utils/verifyToken');
const { resolveAccountId } = require('../utils/resolveAccount');

const ACCOUNTS_TABLE = process.env.ACCOUNTS_TABLE || 'ugocalc-accounts';
const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
  const authHeader = event.headers?.authorization || event.headers?.Authorization;
  const callerLoginId = await verifyToken(authHeader);
  if (!callerLoginId) return response(401, { error: 'Unauthorized' });

  let body;
  try { body = JSON.parse(event.body || '{}'); } catch { return response(400, { error: 'Invalid JSON' }); }

  const { inviteeEmail } = body;
  if (!inviteeEmail || typeof inviteeEmail !== 'string' || !inviteeEmail.includes('@')) {
    return response(400, { error: 'inviteeEmail must be a valid email address' });
  }

  // Prevent inviting yourself
  if (inviteeEmail === callerLoginId) {
    return response(400, { error: 'Cannot invite yourself' });
  }

  // Resolve caller's canonical accountId (might itself be a shared account)
  const callerAccountId = await resolveAccountId(callerLoginId);

  // Write invite: invitee now resolves to caller's accountId
  await dynamo.send(new PutItemCommand({
    TableName: ACCOUNTS_TABLE,
    Item: marshall({ PK: inviteeEmail, accountId: callerAccountId }),
    // Overwrite: if invitee was already on a different account, this updates it.
    // The caller owns the account and controls who accesses it.
  }));

  return response(200, { ok: true, accountId: callerAccountId });
};

function response(statusCode, body) {
  return { statusCode, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
