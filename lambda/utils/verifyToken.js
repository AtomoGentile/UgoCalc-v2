'use strict';

// Verifica la firma RS256 del JWT Cognito usando aws-jwt-verify.
// Se USER_POOL_ID non è configurato (sviluppo locale / test), ricade sul decode base64 senza firma.
// In produzione USER_POOL_ID deve essere impostato nella variabile d'ambiente Lambda.

const USER_POOL_ID = process.env.COGNITO_USER_POOL_ID || process.env.USER_POOL_ID || '';
const CLIENT_ID = process.env.COGNITO_CLIENT_ID || process.env.APP_CLIENT_ID || '';

let verifier;
if (USER_POOL_ID && USER_POOL_ID !== 'us-east-1_XXXXXXXXX') {
  const { CognitoJwtVerifier } = require('aws-jwt-verify');
  verifier = CognitoJwtVerifier.create({
    userPoolId: USER_POOL_ID,
    tokenUse: 'access',
    clientId: CLIENT_ID || null,
  });
}

async function verifyToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) return null;
  const token = authHeader.substring(7);

  if (verifier) {
    try {
      const payload = await verifier.verify(token);
      return payload.sub || payload['cognito:username'] || null;
    } catch (e) {
      console.error('JWT verification failed:', e.message);
      return null;
    }
  }

  // Fallback: base64 decode senza verifica firma (solo per ambienti senza Cognito configurato)
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf-8'));
    return payload.sub || payload['cognito:username'] || null;
  } catch {
    return null;
  }
}

module.exports = { verifyToken };
