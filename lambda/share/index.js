'use strict';

// Lambda: /share/*
// Routes:
//   POST /share/generate  — owner crea un token invite monouso → restituisce deepLink
//   POST /share/request   — guest attiva il token (chiede l'accesso, deve essere autenticato)
//   GET  /share/status    — guest fa polling (query param: token)
//   POST /share/approve   — owner approva o nega la richiesta
//   POST /share           — (legacy) invito diretto per email senza conferma
//
// Tabella ugocalc-invites: { PK: token, ownerLoginId, guestLoginId?, status, ttl }

const { DynamoDBClient, PutItemCommand, GetItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');
const { marshall, unmarshall } = require('@aws-sdk/util-dynamodb');
const { verifyToken } = require('../utils/verifyToken');
const { resolveAccountId } = require('../utils/resolveAccount');
const crypto = require('crypto');

const INVITES_TABLE  = process.env.INVITES_TABLE  || 'ugocalc-invites';
const ACCOUNTS_TABLE = process.env.ACCOUNTS_TABLE || 'ugocalc-accounts';
const TOKEN_TTL_SECONDS = 24 * 60 * 60; // 24 ore

const dynamo = new DynamoDBClient({});

exports.handler = async (event) => {
  const method  = event.requestContext?.http?.method || event.httpMethod || '';
  const rawPath = event.rawPath || event.path || '';

  if (method === 'POST' && rawPath.endsWith('/share/generate')) return handleGenerate(event);
  if (method === 'POST' && rawPath.endsWith('/share/request'))  return handleRequest(event);
  if (method === 'GET'  && rawPath.endsWith('/share/status'))   return handleStatus(event);
  if (method === 'POST' && rawPath.endsWith('/share/approve'))  return handleApprove(event);
  if (method === 'POST' && rawPath.endsWith('/share'))          return handleLegacyInvite(event);

  return response(404, { error: 'Not found' });
};

// ─── POST /share/generate ────────────────────────────────────────────────────
async function handleGenerate(event) {
  const callerLoginId = await verifyToken(event.headers?.authorization || event.headers?.Authorization);
  if (!callerLoginId) return response(401, { error: 'Unauthorized' });

  const token = crypto.randomBytes(20).toString('hex');
  const ttl   = Math.floor(Date.now() / 1000) + TOKEN_TTL_SECONDS;

  await dynamo.send(new PutItemCommand({
    TableName: INVITES_TABLE,
    Item: marshall({ PK: token, ownerLoginId: callerLoginId, status: 'pending', ttl }),
  }));

  return response(200, {
    token,
    deepLink: `ugocalc://invite?token=${token}`,
  });
}

// ─── POST /share/request ────────────────────────────────────────────────────
async function handleRequest(event) {
  const guestLoginId = await verifyToken(event.headers?.authorization || event.headers?.Authorization);
  if (!guestLoginId) return response(401, { error: 'Unauthorized' });

  let body;
  try { body = JSON.parse(event.body || '{}'); } catch { return response(400, { error: 'Invalid JSON' }); }

  const { token } = body;
  if (!token || typeof token !== 'string') return response(400, { error: 'token required' });

  const result = await dynamo.send(new GetItemCommand({
    TableName: INVITES_TABLE,
    Key: marshall({ PK: token }),
  }));

  if (!result.Item) return response(404, { error: 'Token non valido o scaduto' });

  const invite = unmarshall(result.Item);

  if (invite.ttl && invite.ttl < Math.floor(Date.now() / 1000)) return response(410, { error: 'Token scaduto' });
  if (invite.ownerLoginId === guestLoginId) return response(400, { error: 'Non puoi invitare te stesso' });
  if (invite.status !== 'pending') return response(409, { error: 'Token già utilizzato' });

  await dynamo.send(new UpdateItemCommand({
    TableName: INVITES_TABLE,
    Key: marshall({ PK: token }),
    UpdateExpression: 'SET guestLoginId = :g',
    ConditionExpression: 'attribute_not_exists(guestLoginId)',
    ExpressionAttributeValues: marshall({ ':g': guestLoginId }),
  }));

  return response(200, { status: 'waiting_approval' });
}

// ─── GET /share/status?token=XXX ────────────────────────────────────────────
async function handleStatus(event) {
  const guestLoginId = await verifyToken(event.headers?.authorization || event.headers?.Authorization);
  if (!guestLoginId) return response(401, { error: 'Unauthorized' });

  const token = event.queryStringParameters?.token;
  if (!token) return response(400, { error: 'token required' });

  const result = await dynamo.send(new GetItemCommand({
    TableName: INVITES_TABLE,
    Key: marshall({ PK: token }),
  }));

  if (!result.Item) return response(404, { error: 'Token non trovato' });

  const invite = unmarshall(result.Item);

  if (invite.guestLoginId && invite.guestLoginId !== guestLoginId) return response(403, { error: 'Forbidden' });

  return response(200, { status: invite.status });
}

// ─── POST /share/approve ────────────────────────────────────────────────────
async function handleApprove(event) {
  const ownerLoginId = await verifyToken(event.headers?.authorization || event.headers?.Authorization);
  if (!ownerLoginId) return response(401, { error: 'Unauthorized' });

  let body;
  try { body = JSON.parse(event.body || '{}'); } catch { return response(400, { error: 'Invalid JSON' }); }

  const { token, approved } = body;
  if (!token || typeof approved !== 'boolean') return response(400, { error: 'token e approved required' });

  const result = await dynamo.send(new GetItemCommand({
    TableName: INVITES_TABLE,
    Key: marshall({ PK: token }),
  }));

  if (!result.Item) return response(404, { error: 'Token non trovato' });

  const invite = unmarshall(result.Item);

  if (invite.ownerLoginId !== ownerLoginId) return response(403, { error: 'Forbidden' });
  if (!invite.guestLoginId) return response(409, { error: 'Nessun guest ha ancora usato questo token' });

  const newStatus = approved ? 'approved' : 'denied';

  await dynamo.send(new UpdateItemCommand({
    TableName: INVITES_TABLE,
    Key: marshall({ PK: token }),
    UpdateExpression: 'SET #s = :s',
    ExpressionAttributeNames: { '#s': 'status' },
    ExpressionAttributeValues: marshall({ ':s': newStatus }),
  }));

  if (approved) {
    const ownerAccountId = await resolveAccountId(ownerLoginId);
    await dynamo.send(new PutItemCommand({
      TableName: ACCOUNTS_TABLE,
      Item: marshall({ PK: invite.guestLoginId, accountId: ownerAccountId }),
    }));
  }

  return response(200, { ok: true, status: newStatus });
}

// ─── POST /share (legacy) ───────────────────────────────────────────────────
async function handleLegacyInvite(event) {
  const callerLoginId = await verifyToken(event.headers?.authorization || event.headers?.Authorization);
  if (!callerLoginId) return response(401, { error: 'Unauthorized' });

  let body;
  try { body = JSON.parse(event.body || '{}'); } catch { return response(400, { error: 'Invalid JSON' }); }

  const { inviteeEmail } = body;
  if (!inviteeEmail || typeof inviteeEmail !== 'string' || !inviteeEmail.includes('@')) {
    return response(400, { error: 'inviteeEmail must be a valid email address' });
  }
  if (inviteeEmail === callerLoginId) return response(400, { error: 'Cannot invite yourself' });

  const callerAccountId = await resolveAccountId(callerLoginId);
  await dynamo.send(new PutItemCommand({
    TableName: ACCOUNTS_TABLE,
    Item: marshall({ PK: inviteeEmail, accountId: callerAccountId }),
  }));

  return response(200, { ok: true, accountId: callerAccountId });
}

function response(statusCode, body) {
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
    body: JSON.stringify(body),
  };
}
