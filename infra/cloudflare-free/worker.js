/**
 * UgoCalc — Drop-in Backend 100% Gratuito su Cloudflare Workers + D1 (SQLite)
 *
 * Replica tutte le API di AWS API Gateway + Lambda + DynamoDB:
 *   GET  /health
 *   GET  /version
 *   GET  /config
 *   GET  /inference      | POST /inference
 *   GET  /calibration    | POST /calibration
 *   POST /benchmark
 *   GET  /bias
 *   POST /share          | POST /share/generate | POST /share/request
 *   GET  /share/status   | POST /share/approve  | GET  /invite
 *   POST /auth/token     (opzionale: emissione token HMAC leggero senza AWS Cognito)
 *
 * Zero dipendenze npm esterne: usa solo Web Crypto API e Cloudflare D1.
 */

const INVITE_TTL_SECONDS = 24 * 60 * 60;
const DEFAULT_TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;

// Cache JWKS in memoria per il worker isolato (evita fetch ripetute verso Cognito)
const jwksCache = new Map();

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') {
      return corsPreflight();
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const method = request.method.toUpperCase();

    try {
      if (method === 'GET' && path === '/health') {
        return jsonResponse(200, { ok: true, provider: 'cloudflare-workers-d1' });
      }
      if (method === 'GET' && path === '/version') {
        return handleVersion(env);
      }
      if (method === 'GET' && path === '/config') {
        return handleConfig(request, env);
      }
      if (path === '/inference') {
        if (method === 'GET') return handleGetEvents(request, env, 'inference');
        if (method === 'POST') return handlePostEvent(request, env, 'inference');
      }
      if (path === '/calibration') {
        if (method === 'GET') return handleGetEvents(request, env, 'calibration');
        if (method === 'POST') return handlePostEvent(request, env, 'calibration');
      }
      if (method === 'POST' && path === '/benchmark') {
        return handlePostEvent(request, env, 'benchmark');
      }
      if (method === 'GET' && path === '/bias') {
        return handleBias(request, env);
      }
      if (method === 'POST' && path === '/share/generate') {
        return handleShareGenerate(request, env, url.origin);
      }
      if (method === 'POST' && path === '/share/request') {
        return handleShareRequest(request, env);
      }
      if (method === 'GET' && path === '/share/status') {
        return handleShareStatus(request, env, url);
      }
      if (method === 'POST' && path === '/share/approve') {
        return handleShareApprove(request, env);
      }
      if (method === 'POST' && path === '/share') {
        return handleLegacyShare(request, env);
      }
      if (method === 'GET' && path === '/invite') {
        return handleInviteRedirect(url);
      }
      if (method === 'POST' && path === '/auth/token') {
        return handleIssueLightweightToken(request, env);
      }

      return jsonResponse(404, { error: 'Not found' });
    } catch (err) {
      console.error('Unhandled worker error:', err);
      return jsonResponse(500, { error: 'Internal error' });
    }
  },
};

// ─────────────────────────────────────────────
// Autenticazione duale: Cognito JWKS oppure token HMAC-SHA256 leggero (zero AWS)
// ─────────────────────────────────────────────

async function verifyToken(request, env) {
  const authHeader = request.headers.get('Authorization') || request.headers.get('authorization');

  // 1) Token Bearer JWT (Cognito RS256 oppure HMAC-SHA256 leggero "ug1.<payload>.<sig>")
  if (authHeader && authHeader.startsWith('Bearer ')) {
    const token = authHeader.slice(7).trim();
    if (!token) return null;

    if (token.startsWith('ug1.') && env.AUTH_SECRET) {
      return verifyLightweightHmacToken(token, env.AUTH_SECRET);
    }

    if (env.COGNITO_USER_POOL_ID) {
      return verifyCognitoJwt(token, env.COGNITO_USER_POOL_ID, env.COGNITO_CLIENT_ID || '');
    }

    // Se AUTH_SECRET è configurato come token statico personale
    if (env.AUTH_SECRET && timingSafeEqual(token, env.AUTH_SECRET)) {
      const explicitUser = (request.headers.get('X-User-Id') || '').trim();
      return explicitUser || 'owner';
    }

    return null;
  }

  // 2) Chiave personale X-Api-Key per uso singolo senza Cognito
  const apiKey = request.headers.get('X-Api-Key');
  if (apiKey && env.AUTH_SECRET && timingSafeEqual(apiKey.trim(), env.AUTH_SECRET)) {
    const explicitUser = (request.headers.get('X-User-Id') || '').trim();
    return explicitUser || 'owner';
  }

  return null;
}

async function handleIssueLightweightToken(request, env) {
  if (!env.AUTH_SECRET) {
    return jsonResponse(503, { error: 'AUTH_SECRET not configured' });
  }
  const secretHeader = request.headers.get('X-Api-Key') || '';
  if (!timingSafeEqual(secretHeader.trim(), env.AUTH_SECRET)) {
    return jsonResponse(401, { error: 'Unauthorized' });
  }
  const body = await safeParseJson(request);
  const userId = typeof body?.userId === 'string' ? body.userId.trim() : '';
  if (!userId) {
    return jsonResponse(400, { error: 'userId required' });
  }
  const ttlSeconds = Number.isFinite(body?.ttlSeconds) && body.ttlSeconds > 0
    ? Math.min(body.ttlSeconds, 365 * 24 * 60 * 60)
    : DEFAULT_TOKEN_TTL_SECONDS;
  const payload = {
    sub: userId,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + ttlSeconds,
  };
  const encodedPayload = base64UrlEncodeString(JSON.stringify(payload));
  const sig = await hmacSign(encodedPayload, env.AUTH_SECRET);
  return jsonResponse(200, {
    token: `ug1.${encodedPayload}.${sig}`,
    expiresAtEpochSeconds: payload.exp,
  });
}

async function verifyLightweightHmacToken(token, secret) {
  const parts = token.split('.');
  if (parts.length !== 3 || parts[0] !== 'ug1') return null;
  const [, encodedPayload, providedSig] = parts;
  const expectedSig = await hmacSign(encodedPayload, secret);
  if (!timingSafeEqual(providedSig, expectedSig)) return null;
  try {
    const payload = JSON.parse(base64UrlDecodeToString(encodedPayload));
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp === 'number' && payload.exp < now) return null;
    return typeof payload.sub === 'string' && payload.sub ? payload.sub : null;
  } catch {
    return null;
  }
}

async function verifyCognitoJwt(token, userPoolId, expectedClientId) {
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  let header;
  let payload;
  try {
    header = JSON.parse(base64UrlDecodeToString(parts[0]));
    payload = JSON.parse(base64UrlDecodeToString(parts[1]));
  } catch {
    return null;
  }

  if (header.alg !== 'RS256' || !header.kid) return null;

  const region = userPoolId.split('_')[0];
  if (!region) return null;

  const expectedIss = `https://cognito-idp.${region}.amazonaws.com/${userPoolId}`;
  if (payload.iss !== expectedIss) return null;

  const now = Math.floor(Date.now() / 1000);
  if (typeof payload.exp !== 'number' || payload.exp < now) return null;

  if (expectedClientId) {
    const tokenClient = payload.client_id || payload.aud;
    if (tokenClient !== expectedClientId) return null;
  }

  const jwk = await getCognitoJwk(expectedIss, header.kid);
  if (!jwk) return null;

  try {
    const cryptoKey = await crypto.subtle.importKey(
      'jwk',
      jwk,
      { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
      false,
      ['verify'],
    );
    const data = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signature = base64UrlDecodeToBytes(parts[2]);
    const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', cryptoKey, signature, data);
    if (!valid) return null;
    return payload.sub || payload['cognito:username'] || null;
  } catch {
    return null;
  }
}

async function getCognitoJwk(issuerUrl, kid) {
  const cached = jwksCache.get(issuerUrl);
  if (cached && Date.now() - cached.fetchedAt < 3600_000) {
    const found = cached.keys.find((k) => k.kid === kid);
    if (found) return found;
  }

  const res = await fetch(`${issuerUrl}/.well-known/jwks.json`);
  if (!res.ok) return null;
  const data = await res.json();
  const keys = Array.isArray(data?.keys) ? data.keys : [];
  jwksCache.set(issuerUrl, { keys, fetchedAt: Date.now() });
  return keys.find((k) => k.kid === kid) || null;
}

// ─────────────────────────────────────────────
// Helpers Account & DB
// ─────────────────────────────────────────────

async function resolveAccountId(env, loginId) {
  const existing = await env.DB.prepare('SELECT account_id FROM accounts WHERE login_id = ?')
    .bind(loginId)
    .first();

  if (existing?.account_id) return existing.account_id;

  await env.DB.prepare(
    'INSERT OR IGNORE INTO accounts (login_id, account_id, global_bias_enabled) VALUES (?, ?, 0)',
  )
    .bind(loginId, loginId)
    .run();

  return loginId;
}

// ─────────────────────────────────────────────
// /version & /config
// ─────────────────────────────────────────────

async function handleVersion(env) {
  const row = await env.DB.prepare(
    'SELECT latest_version, min_required_version, changelog, apk_url, mandatory FROM versions WHERE pk = ?',
  )
    .bind('android')
    .first();

  if (!row) {
    return jsonResponse(200, {
      latestVersion: '3.0.4',
      minRequiredVersion: '3.0.4',
      changelog: 'Local-First & Free Backend',
      apkUrl: '',
      mandatory: false,
    });
  }

  return jsonResponse(200, {
    latestVersion: row.latest_version,
    minRequiredVersion: row.min_required_version,
    changelog: row.changelog || '',
    apkUrl: row.apk_url || '',
    mandatory: Boolean(row.mandatory),
  });
}

async function handleConfig(request, env) {
  const tokenUserId = await verifyToken(request, env);
  let row = null;

  if (tokenUserId) {
    row = await env.DB.prepare(
      'SELECT min_benchmark_meals, v2_shadow_enabled, maintenance_message, force_update_min_version FROM config WHERE pk = ? AND sk = ?',
    )
      .bind(tokenUserId, 'v1')
      .first();
  }

  if (!row) {
    row = await env.DB.prepare(
      'SELECT min_benchmark_meals, v2_shadow_enabled, maintenance_message, force_update_min_version FROM config WHERE pk = ? AND sk = ?',
    )
      .bind('global', 'v1')
      .first();
  }

  if (!row) {
    return jsonResponse(200, {
      min_benchmark_meals: 30,
      v2_shadow_enabled: false,
      maintenance_message: '',
      force_update_min_version: '',
    });
  }

  return jsonResponse(200, {
    min_benchmark_meals: Number(row.min_benchmark_meals ?? 30),
    v2_shadow_enabled: Boolean(row.v2_shadow_enabled),
    maintenance_message: row.maintenance_message || '',
    force_update_min_version: row.force_update_min_version || '',
  });
}

// ─────────────────────────────────────────────
// /inference, /calibration, /benchmark
// ─────────────────────────────────────────────

async function handleGetEvents(request, env, eventType) {
  const tokenUserId = await verifyToken(request, env);
  if (!tokenUserId) {
    return jsonResponse(401, { error: 'Unauthorized: missing or invalid Authorization header' });
  }

  const accountId = await resolveAccountId(env, tokenUserId);
  const { results } = await env.DB.prepare(
    'SELECT payload_json FROM events WHERE account_id = ? AND event_type = ? ORDER BY created_at ASC',
  )
    .bind(accountId, eventType)
    .all();

  const records = (results || [])
    .map((row) => {
      try {
        return JSON.parse(row.payload_json);
      } catch {
        return null;
      }
    })
    .filter(Boolean);

  return jsonResponse(200, { records });
}

async function handlePostEvent(request, env, eventType) {
  const tokenUserId = await verifyToken(request, env);
  if (!tokenUserId) {
    return jsonResponse(401, { error: 'Unauthorized: missing or invalid Authorization header' });
  }

  const body = await safeParseJson(request);
  if (!body) {
    return jsonResponse(400, { error: 'Invalid JSON body' });
  }

  const { eventId, userId, payload } = body;
  if (!eventId || !userId || !payload) {
    return jsonResponse(400, { error: 'Missing required fields: eventId, userId, payload' });
  }

  const accountId = await resolveAccountId(env, tokenUserId);
  if (userId !== tokenUserId && userId !== accountId) {
    return jsonResponse(403, { error: 'Forbidden: userId mismatch' });
  }

  const segmentKey = [
    payload.sessionMode ?? '',
    payload.strategy ?? '',
    payload.foodCategory ?? '',
  ].join('#');

  const insertResult = await env.DB.prepare(
    `INSERT OR IGNORE INTO events (
       account_id, event_id, event_type, created_at,
       session_mode, ai_model_id, strategy, food_category, segment_key, payload_json
     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(
      accountId,
      String(eventId),
      eventType,
      new Date().toISOString(),
      String(payload.sessionMode ?? ''),
      String(payload.aiModelId ?? ''),
      String(payload.strategy ?? ''),
      String(payload.foodCategory ?? ''),
      segmentKey,
      JSON.stringify(payload),
    )
    .run();

  const insertedRows = insertResult?.meta?.changes ?? 0;
  if (insertedRows === 0) {
    return jsonResponse(409, { ok: true, duplicate: true });
  }

  return jsonResponse(201, { ok: true });
}

// ─────────────────────────────────────────────
// /bias (aggregazione anonima globale)
// ─────────────────────────────────────────────

async function handleBias(request, env) {
  const callerLoginId = await verifyToken(request, env);
  if (!callerLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  await resolveAccountId(env, callerLoginId);
  const acct = await env.DB.prepare('SELECT global_bias_enabled FROM accounts WHERE login_id = ?')
    .bind(callerLoginId)
    .first();

  if (!acct?.global_bias_enabled) {
    return jsonResponse(403, { error: 'Global bias not enabled for this account' });
  }

  const { results } = await env.DB.prepare(
    'SELECT segment_key, ai_model_id, payload_json FROM events WHERE event_type = ?',
  )
    .bind('inference')
    .all();

  const buckets = new Map();
  for (const row of results || []) {
    let payload;
    try {
      payload = JSON.parse(row.payload_json || '{}');
    } catch {
      continue;
    }
    const bias = payload.biasCorrectionPercent;
    if (typeof bias !== 'number') continue;
    const aiModelId = row.ai_model_id || 'unknown';
    const segmentKey = row.segment_key || 'unknown';
    const key = `${aiModelId}|${segmentKey}`;
    const bucket = buckets.get(key) || { sum: 0, count: 0, aiModelId, segmentKey };
    bucket.sum += bias;
    bucket.count += 1;
    buckets.set(key, bucket);
  }

  const aggregated = Array.from(buckets.values()).map((b) => ({
    aiModelId: b.aiModelId,
    segmentKey: b.segmentKey,
    meanBiasPercent: Math.round((b.sum / b.count) * 100) / 100,
    sampleCount: b.count,
  }));

  return jsonResponse(200, { aggregated });
}

// ─────────────────────────────────────────────
// /share/* & /invite
// ─────────────────────────────────────────────

async function handleShareGenerate(request, env, requestOrigin) {
  const callerLoginId = await verifyToken(request, env);
  if (!callerLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  const bytes = new Uint8Array(20);
  crypto.getRandomValues(bytes);
  const token = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  const ttl = Math.floor(Date.now() / 1000) + INVITE_TTL_SECONDS;

  await env.DB.prepare(
    'INSERT INTO invites (token, owner_login_id, guest_login_id, status, ttl) VALUES (?, ?, NULL, ?, ?)',
  )
    .bind(token, callerLoginId, 'pending', ttl)
    .run();

  const base = (env.API_BASE_URL || requestOrigin).replace(/\/+$/, '');
  return jsonResponse(200, {
    token,
    deepLink: `${base}/invite?token=${token}`,
  });
}

async function handleShareRequest(request, env) {
  const guestLoginId = await verifyToken(request, env);
  if (!guestLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  const body = await safeParseJson(request);
  if (!body) return jsonResponse(400, { error: 'Invalid JSON' });

  const { token } = body;
  if (!token || typeof token !== 'string') return jsonResponse(400, { error: 'token required' });

  const invite = await env.DB.prepare(
    'SELECT token, owner_login_id, guest_login_id, status, ttl FROM invites WHERE token = ?',
  )
    .bind(token)
    .first();

  if (!invite) return jsonResponse(404, { error: 'Token non valido o scaduto' });
  if (invite.ttl && invite.ttl < Math.floor(Date.now() / 1000)) {
    return jsonResponse(410, { error: 'Token scaduto' });
  }
  if (invite.owner_login_id === guestLoginId) {
    return jsonResponse(400, { error: 'Non puoi invitare te stesso' });
  }
  if (invite.status !== 'pending') {
    return jsonResponse(409, { error: 'Token già utilizzato' });
  }

  const updateRes = await env.DB.prepare(
    'UPDATE invites SET guest_login_id = ?, status = ? WHERE token = ? AND guest_login_id IS NULL AND status = ?',
  )
    .bind(guestLoginId, 'waiting_approval', token, 'pending')
    .run();

  if ((updateRes?.meta?.changes ?? 0) === 0) {
    return jsonResponse(409, { error: 'Token già utilizzato' });
  }

  return jsonResponse(200, { status: 'waiting_approval' });
}

async function handleShareStatus(request, env, url) {
  const callerLoginId = await verifyToken(request, env);
  if (!callerLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  const token = url.searchParams.get('token');
  if (!token) return jsonResponse(400, { error: 'token required' });

  const invite = await env.DB.prepare(
    'SELECT owner_login_id, guest_login_id, status FROM invites WHERE token = ?',
  )
    .bind(token)
    .first();

  if (!invite) return jsonResponse(404, { error: 'Token non trovato' });

  const isOwner = invite.owner_login_id === callerLoginId;
  const isGuest = invite.guest_login_id && invite.guest_login_id === callerLoginId;
  if (!isOwner && !isGuest) return jsonResponse(403, { error: 'Forbidden' });

  return jsonResponse(200, { status: invite.status });
}

async function handleShareApprove(request, env) {
  const ownerLoginId = await verifyToken(request, env);
  if (!ownerLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  const body = await safeParseJson(request);
  if (!body) return jsonResponse(400, { error: 'Invalid JSON' });

  const { token, approved } = body;
  if (!token || typeof approved !== 'boolean') {
    return jsonResponse(400, { error: 'token e approved required' });
  }

  const invite = await env.DB.prepare(
    'SELECT owner_login_id, guest_login_id FROM invites WHERE token = ?',
  )
    .bind(token)
    .first();

  if (!invite) return jsonResponse(404, { error: 'Token non trovato' });
  if (invite.owner_login_id !== ownerLoginId) return jsonResponse(403, { error: 'Forbidden' });
  if (!invite.guest_login_id) return jsonResponse(409, { error: 'Nessun guest ha ancora usato questo token' });

  const newStatus = approved ? 'approved' : 'denied';
  await env.DB.prepare('UPDATE invites SET status = ? WHERE token = ?')
    .bind(newStatus, token)
    .run();

  if (approved) {
    const ownerAccountId = await resolveAccountId(env, ownerLoginId);
    await env.DB.prepare(
      `INSERT INTO accounts (login_id, account_id, global_bias_enabled) VALUES (?, ?, 0)
       ON CONFLICT(login_id) DO UPDATE SET account_id = excluded.account_id`,
    )
      .bind(invite.guest_login_id, ownerAccountId)
      .run();
  }

  return jsonResponse(200, { ok: true, status: newStatus });
}

async function handleLegacyShare(request, env) {
  const callerLoginId = await verifyToken(request, env);
  if (!callerLoginId) return jsonResponse(401, { error: 'Unauthorized' });

  const body = await safeParseJson(request);
  if (!body) return jsonResponse(400, { error: 'Invalid JSON' });

  const { inviteeEmail } = body;
  if (!inviteeEmail || typeof inviteeEmail !== 'string' || !inviteeEmail.includes('@')) {
    return jsonResponse(400, { error: 'inviteeEmail must be a valid email address' });
  }
  if (inviteeEmail === callerLoginId) {
    return jsonResponse(400, { error: 'Cannot invite yourself' });
  }

  const callerAccountId = await resolveAccountId(env, callerLoginId);
  await env.DB.prepare(
    `INSERT INTO accounts (login_id, account_id, global_bias_enabled) VALUES (?, ?, 0)
     ON CONFLICT(login_id) DO UPDATE SET account_id = excluded.account_id`,
  )
    .bind(inviteeEmail, callerAccountId)
    .run();

  return jsonResponse(200, { ok: true, accountId: callerAccountId });
}

function handleInviteRedirect(url) {
  const token = url.searchParams.get('token');
  if (!token) return jsonResponse(400, { error: 'token required' });

  return new Response('', {
    status: 301,
    headers: {
      Location: `ugocalc://invite?token=${encodeURIComponent(token)}`,
      'Cache-Control': 'no-store',
    },
  });
}

// ─────────────────────────────────────────────
// Utility crypto & HTTP
// ─────────────────────────────────────────────

async function safeParseJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

async function hmacSign(data, secret) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
  const sigBuffer = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
  return base64UrlEncodeBytes(new Uint8Array(sigBuffer));
}

function timingSafeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const aBytes = new TextEncoder().encode(a);
  const bBytes = new TextEncoder().encode(b);
  if (aBytes.length !== bBytes.length) return false;
  let diff = 0;
  for (let i = 0; i < aBytes.length; i++) {
    diff |= aBytes[i] ^ bBytes[i];
  }
  return diff === 0;
}

function base64UrlEncodeString(str) {
  return base64UrlEncodeBytes(new TextEncoder().encode(str));
}

function base64UrlEncodeBytes(bytes) {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function base64UrlDecodeToBytes(str) {
  const padded = str.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((str.length + 3) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function base64UrlDecodeToString(str) {
  return new TextDecoder().decode(base64UrlDecodeToBytes(str));
}

function corsPreflight() {
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type, X-Api-Key, X-User-Id',
    },
  });
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  });
}
