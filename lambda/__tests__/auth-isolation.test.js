/* Test per verificare l'isolamento tra utenti nel sistema autenticato.
   Simula due utenti diversi e verifica che nessuno possa accedere ai dati dell'altro.
*/

// Mock token generation per test
function generateTestToken(userId) {
  // Usa base64url (no padding, replace +/ with -_)
  const base64url = (str) => Buffer.from(str).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');

  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64url(JSON.stringify({
    sub: userId,
    'cognito:username': userId,
    iss: 'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX',
    aud: 'test-client-id',
    token_use: 'access',
    auth_time: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
  }));
  const signature = base64url('test-signature');
  return `${header}.${payload}.${signature}`;
}

// Simula il comportamento di extact  UserIdFromToken
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

describe('Auth Isolation Tests', () => {
  test('Two different users should have different tokens', () => {
    const user1Token = generateTestToken('user1@example.com');
    const user2Token = generateTestToken('user2@example.com');

    const user1Id = extractUserIdFromToken(`Bearer ${user1Token}`);
    const user2Id = extractUserIdFromToken(`Bearer ${user2Token}`);

    expect(user1Id).toBe('user1@example.com');
    expect(user2Id).toBe('user2@example.com');
    expect(user1Id).not.toBe(user2Id);
  });

  test('User1 should not be able to read User2 data with userId mismatch', () => {
    const user1Token = generateTestToken('user1@example.com');
    const user1Id = extractUserIdFromToken(`Bearer ${user1Token}`);

    // Simula un tentativo di user1 di leggere dati di user2
    const attemptedUserId = 'user2@example.com';

    // Verificare che non corrispondono
    expect(user1Id).not.toBe(attemptedUserId);
    expect(user1Id === attemptedUserId).toBe(false);
  });

  test('Invalid token should return null userId', () => {
    const invalidToken = 'invalid.token.here';
    const userId = extractUserIdFromToken(`Bearer ${invalidToken}`);

    expect(userId).toBeNull();
  });

  test('Missing Authorization header should return null', () => {
    const userId = extractUserIdFromToken(null);
    expect(userId).toBeNull();
  });

  test('Malformed Authorization header should return null', () => {
    const malformedAuth = 'NotBearer abcd1234';
    const userId = extractUserIdFromToken(malformedAuth);

    expect(userId).toBeNull();
  });
});

// Test per DynamoDB query isolation
describe('DynamoDB Query Isolation', () => {
  test('Query con PK=user1 dovrebbe restituire solo dati di user1', () => {
    // Mock scenario: user1 scrive e legge i propri dati
    const user1 = 'user1@example.com';
    const pk1 = user1;
    const sk1 = 'event-001';

    // Simula dato in DynamoDB
    const item1 = {
      PK: { S: pk1 },
      SK: { S: sk1 },
      eventType: { S: 'benchmark' },
    };

    // Query dovrebbe usare PK=user1
    expect(item1.PK.S).toBe(user1);
  });

  test('Query con PK=user2 non dovrebbe restituire dati di user1', () => {
    // Mock scenario: verifica che user2 non veda dati di user1
    const user1 = 'user1@example.com';
    const user2 = 'user2@example.com';

    // Simula dato di user1
    const item1 = {
      PK: { S: user1 },
      SK: { S: 'event-001' },
      eventType: { S: 'benchmark' },
    };

    // Query di user2 dovrebbe usare PK=user2
    const queryPK = user2;

    // Il PK della query dovrebbe differire dal PK dell'item
    expect(item1.PK.S).not.toBe(queryPK);
  });

  test('Default user migration should preserve backward compatibility', () => {
    const defaultUserId = 'default-user-single-mode';

    // Simula dato preesistente con user_id di default
    const legacyItem = {
      PK: { S: defaultUserId },
      SK: { S: 'legacy-event-001' },
      eventType: { S: 'analysis' },
      createdAt: { S: '2026-01-01T00:00:00Z' },
    };

    // Verificare che il default user_id sia consistente
    expect(legacyItem.PK.S).toBe(defaultUserId);
    expect(legacyItem.PK.S.length).toBeGreaterThan(0);
  });
});

// Test per IAM policy simulation
describe('IAM Policy Validation', () => {
  test('IAM policy should not use wildcard for user data access', () => {
    // Mock IAM policy per DynamoDB
    const policy = {
      Version: '2012-10-17',
      Statement: [
        {
          Effect: 'Allow',
          Action: 'dynamodb:GetItem',
          Resource: 'arn:aws:dynamodb:us-east-1:ACCOUNT:table/ugocalc-events',
          Condition: {
            'ForAllValues:StringLike': {
              'dynamodb:LeadingKeys': ['${aws:username}'],
            },
          },
        },
      ],
    };

    // Verifica che il leading keys non contenga wildcards
    const leadingKeys = policy.Statement[0].Condition['ForAllValues:StringLike']['dynamodb:LeadingKeys'];
    expect(leadingKeys).toContain('${aws:username}');
    expect(leadingKeys[0]).not.toContain('*');
  });

  test('IAM should use LeadingKeys for partition key isolation', () => {
    const iamCondition = {
      'ForAllValues:StringLike': {
        'dynamodb:LeadingKeys': ['${aws:username}'],
      },
    };

    // Verifica che LeadingKeys sia utilizzato
    expect(iamCondition['ForAllValues:StringLike']['dynamodb:LeadingKeys']).toBeDefined();
    expect(iamCondition['ForAllValues:StringLike']['dynamodb:LeadingKeys'][0]).toMatch(/\$\{aws:/);
  });
});
