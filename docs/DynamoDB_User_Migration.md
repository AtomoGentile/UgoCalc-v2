# DynamoDB User Migration Strategy

## Overview
Migrazione da single-user (implicit) a multi-user (Cognito-authenticated) system, mantenendo backward compatibility con i dati preesistenti.

## Schema Tables

### ugocalc-events
- **PK (Partition Key)**: `userId` — identificatore unico dell'utente Cognito (email o user_id)
- **SK (Sort Key)**: `eventId` — identificatore univoco dell'evento (timestamp-basato o UUID)
- **Attributi**:
  - `eventType`: 'benchmark', 'inference', 'analysis'
  - `createdAt`: timestamp ISO 8601
  - `sessionMode`, `strategy`, `foodCategory`: metadata per segmentazione
  - `payloadJson`: dati dell'evento (string JSON)

### ugocalc-config (Global Configuration)
- **PK**: 'global' per configurazione globale; `userId` per config per-utente (future)
- **SK**: 'v1' (versioning)
- **Attributi**: min_benchmark_meals, v2_shadow_enabled, maintenance_message, etc.

### ugocalc-versions (App Versions)
- **PK**: 'android', 'ios' (platform)
- **SK**: 'latest' (versioning strategy)
- **Attributi**: latestVersion, minRequiredVersion, changelog, apkUrl, mandatory

## Migration Plan

### Phase 1: Add User ID to Preexisting Data
Per i dati preesistenti (single-user mode), assegnare uno `user_id` di default:
```
DEFAULT_USER_ID = "default-user-single-mode"
```

**Operazione**: DynamoDB Scan + Update su `ugocalc-events` e `ugocalc-config`:
```
UPDATE ugocalc-events SET userId = DEFAULT_USER_ID WHERE userId attribute doesn't exist
```

**Timing**: Eseguire una volta durante il deploy, prima di attivare Cognito.

### Phase 2: Introduce Cognito Authentication
1. Creare User Pool in AWS Cognito
2. Distribuire app aggiornata con AuthInterceptor
3. Nuovi utenti login con email/password
4. AuthInterceptor aggiunge `Authorization: Bearer <token>` alle richieste

### Phase 3: IAM Policies for Data Isolation
Configurare IAM Policy su Lambda role per limitare accesso a DynamoDB per PK alone:

```json
{
  "Effect": "Allow",
  "Action": ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query"],
  "Resource": "arn:aws:dynamodb:us-east-1:ACCOUNT:table/ugocalc-events",
  "Condition": {
    "ForAllValues:StringLike": {
      "dynamodb:LeadingKeys": ["${aws:username}"]
    }
  }
}
```

**Nota**: Questo isolamento è a livello di IAM e non di Lambda logic. La Lambda estrae `userId` dal token Cognito e lo valida prima di scrivere.

## Query Patterns

### User-Scoped Queries (Authenticated)
```javascript
// Lambda riceve Authorization header
const tokenUserId = extractUserIdFromToken(authHeader);

// Query solo i dati dell'utente autenticato
const result = await dynamo.send(new QueryCommand({
  TableName: 'ugocalc-events',
  KeyConditionExpression: 'PK = :pk',
  ExpressionAttributeValues: {
    ':pk': { S: tokenUserId }
  }
}));
```

### Global Queries (Non-Authenticated)
Per `/config` e `/version` (senza autenticazione):
```javascript
// Se no Authorization header, usa PK globale
const pk = tokenUserId || 'global';
```

## Data Isolation Verification

### Test per Isolamento
1. **User1** esegue login → riceve token con `sub = user1@example.com`
2. **User1** invia evento con `userId = user1@example.com`
3. **User2** esegue login → riceve token con `sub = user2@example.com`
4. **User2** tenta di leggere evento di User1 → negato (userId mismatch)

### IAM Policy Validation
- Nessun wildcard `*` per cross-user access
- LeadingKeys condizionate a `${aws:username}` (estratto dal token)
- Query condition obbligatorio per PK = user_id

## Backward Compatibility

### Old Data Still Readable
Dati preesistenti con `userId = DEFAULT_USER_ID` rimangono leggibili finché:
1. App cliente non è aggiornata (usa `sendEvent` con `userId = DEFAULT_USER_ID`)
2. Oppure migrare manualmente dopo aver notificato gli utenti

### Deprecation Timeline
- **Week 1-2**: Deploy con DEFAULT_USER_ID support
- **Week 3-4**: Deprecation notice in-app
- **Week 5+**: Rimuovere support per DEFAULT_USER_ID (tutti gli utenti devono fare login)

## IAM Roles

### Lambda Execution Role
- Permessi: `dynamodb:GetItem`, `dynamodb:PutItem`, `dynamodb:Query`
- Condizionato su tabella e LeadingKeys
- No Bedrock access diretto (se AI inference è esternalizzato)

### Cognito User Pool
- Password policy: min 8 chars, uppercase, number, special char
- MFA: opzionale, abilitabile per utenti sensibili
- Token lifetime: 1 hour access token, 30 days refresh token

## Secrets Management
- **Cognito Client ID/Secret**: archiviare in AWS Secrets Manager
- **API Gateway Key** (se usato): archiviare in Secrets Manager
- **No hardcoding** di credenziali nell'app o Lambda

## Monitoring
- CloudWatch Logs per Lambda: all auth attempts, policy violations
- CloudTrail: track DynamoDB access patterns
- Metric: failed auth attempts per user
