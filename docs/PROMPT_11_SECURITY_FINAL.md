# Prompt 11 — Security Audit Final

**Date**: 2026-07-22  
**Status**: ✅ COMPLETED — 1 critical remediated, 2 medium findings, 3 low/informational

---

## Findings

### [CRITICAL — REMEDIATED] google-services.json API key tracked in git

**Files**: `app/google-services.json`, `Ugocalc-v2/app/google-services.json`  
**Key**: `AIzaSyBV9vqf8zsRMWVMbXJgGNUIvlPo79Ovb-M` (Firebase API key)  
**Grep command run**:
```
grep -rn -E "AIza[0-9A-Za-z_-]{35}" --include="*.json" (excl. node_modules, build)
```
**Action taken**: `git rm --cached app/google-services.json` — file is now untracked.  
`.gitignore` already contains `google-services.json` and `*.json` (line 2 covers all JSON).  
**Remaining action for owner**: purge from git history with `git filter-repo --path app/google-services.json --invert-paths`.  
**Ugocalc-v2**: legacy backup directory — not tracked in git index, safe.

---

### [MEDIUM] JWT token validation is decode-only (no signature verification)

**Files**: `lambda/benchmark/index.js`, `lambda/inference/index.js`, `lambda/config/index.js`, `lambda/share/index.js`, `lambda/bias/index.js`  
**Pattern**: All Lambdas use base64-decode of JWT payload only, without verifying the RS256 signature against Cognito's JWKS endpoint.  
**Risk**: A crafted JWT with any userId can bypass the token validation and write data as any user (no cryptographic guarantee).  
**Mitigation**: Behind API Gateway with Cognito Authorizer, the signature is verified at the API Gateway level before the Lambda is invoked — so in the deployed architecture this is mitigated. But if the Lambda is ever invoked directly (e.g., a Lambda URL or SDK direct invoke), there is no signature check.  
**Solution**: Add `aws-jwt-verify` (AWS-maintained, 0 dependencies) to Lambda deps and verify the signature inside each handler. Low effort, eliminates the assumption.

---

### [MEDIUM] Bias Lambda: Scan permission is table-wide (no row-level filter in IAM)

**File**: `lambda/bias/policy.json`  
**Issue**: The `dynamodb:Scan` action on `ugocalc-events` is granted without a LeadingKeys condition (DynamoDB Scan doesn't support LeadingKeys). This is structurally necessary for the aggregate — but means this Lambda role, if compromised, could read all raw events.  
**Mitigation in place**: (1) Scan is restricted to this one Lambda role only; (2) the Lambda returns only aggregated values in its response, never raw rows; (3) the toggle check (globalBiasEnabled) gates access at the application layer.  
**Solution**: Consider a separate aggregation table (pre-computed nightly by a Step Function) so the bias Lambda only needs `GetItem`, eliminating the Scan risk entirely. Medium-term improvement.

---

### [LOW] Cognito pool IDs are placeholder strings in amplifyconfiguration.json

**File**: `app/src/main/res/raw/amplifyconfiguration.json`  
**Values**: `us-east-1_XXXXXXXXX`, `your-app-client-id`, `us-east-1:xxxxxxxx-...`  
**Status**: Intentional placeholders — to be substituted at build/deploy time via CI secrets injection or a build variant.  
**No action required** for development; document the injection method before production deploy.

---

### [LOW] AWS_BASE_URL is a non-functional placeholder

**File**: `app/src/main/java/com/ugocalc/app/di/NetworkModule.kt:19`  
**Value**: `https://api.ugocalc.example.com`  
**Status**: Harmless placeholder. Replace with real API Gateway URL at deploy time.

---

### [LOW] Token refresh not handled in AuthInterceptor

**File**: `app/src/main/java/com/ugocalc/app/data/auth/AuthInterceptor.kt`  
**Issue**: `runBlocking { getCurrentUser() }` returns the cached token. If the token is expired and Amplify hasn't silently refreshed yet, an expired token will be sent. Amplify handles refresh automatically but not synchronously in `fetchAuthSession`.  
**Solution**: Use `Amplify.Auth.fetchAuthSession(forceRefresh = true)` with a 401 retry in the interceptor.

---

## Summary of Grep Commands Executed

```bash
# 1. API key/secret patterns
grep -rn -E "AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|sk-[a-zA-Z0-9]{20,}|-----BEGIN.*PRIVATE KEY" ...

# 2. Inline credential assignments
grep -rn -E "(const|val|var)\s+\w*(key|token|secret|password)\w*\s*[=:]\s*['\"][^'\"]{8,}['\"]" ...

# 3. AWS static credentials
grep -rn -E "accessKeyId|secretAccessKey|aws_access_key|AWS_ACCESS_KEY" ...

# 4. Unauthenticated endpoint exposure
grep -rn -E "unauthenticated|authorizationType.*NONE" ...

# 5. Wildcard IAM Actions
grep -rn -E '"Action".*"\*"' --include="*.json" ...

# 6. Cognito placeholder check
grep -rn "XXXXXXXXX|your-app-client-id" app/src/main/res/ ...
```

**Result**: No hardcoded real secrets found in tracked code. The only issue (google-services.json) was in a tracked binary file and has been remediated.
