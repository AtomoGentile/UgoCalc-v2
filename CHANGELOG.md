# Changelog

All notable changes to UgoCalc2 will be documented in this file.

## [3.0.1] - 2026-07-23

### Fixed
- **DB migration conflict**: `MIGRATION_18_19` duplicata — la migrazione bias correction (rimozione `sessionMode` dalla chiave primaria) rinominata `MIGRATION_19_20`; versione DB aggiornata a 20
- **Compose BOM**: aggiornato da `2024.06.00` a `2024.12.01` — risolve `MenuAnchorType` unresolved e `animateFloatAsState` unresolved (Material3 1.3.x)
- **SettingsViewModel**: rimosso import duplicato di `AiModelCatalog`

### Added
- `MIGRATION_19_20`: unificazione bias correction tra modalità CASA e RISTORANTE (ricostruzione tabella senza `sessionMode` nella chiave)
- Dipendenza esplicita `androidx.compose.animation:animation`

---

## [3.0.0] - 2026-07-23

### Added
- **AWS Migration**: Complete migration from Firebase to AWS infrastructure
  - Cognito User Pool authentication (signup, signin, signout)
  - Amplify Java SDK v2 integration for Android
  - AWS Lambda functions for inference, benchmarks, configuration, sharing, and bias aggregation
  - API Gateway HTTP v2 for RESTful endpoints
  - DynamoDB tables for users, inference history, events, and bias data

- **Multi-user Account & Sharing**
  - Account settings screen with user profile management
  - Account sharing with invitation system
  - CloudSync queue management for multi-device synchronization
  - Multi-user bias aggregation via Lambda

- **Security Enhancements**
  - Secure API key storage via SecureApiKeyStore
  - JWT token verification (RS256) with Amplify verifyToken Lambda utility
  - Per-Lambda least-privilege IAM roles
  - Session token storage compliant with legal/compliance requirements
  - Removed all Firebase dependencies

- **Infrastructure**
  - CloudFormation template (infra/cloudformation.yaml) for automated AWS deployment
  - Deploy script (infra/deploy.sh) for streamlined deployment
  - Lambda test suite (25/25 passing)
  - Environment-based configuration via amplifyconfiguration.json

### Changed
- **Authentication Flow**: Firebase RealtimeDatabase → Cognito User Pool + Amplify SDK
- **Data Storage**: Firebase Firestore/RealtimeDatabase → DynamoDB
- **API Communication**: Direct REST calls via ShareClient and HTTP POSTs
- **CloudSync Architecture**: User ID now dynamically resolved from AuthRepository.getCurrentUser()
- **AI Model Handling**: Centralized AI model configuration via AiModel domain model
- **Confidence System**: Enhanced with V2VisibleResultMapper for better result presentation

### Fixed
- CognitoAuthManager: Corrected Amplify v2 Java callback API usage
- AuthRepository: Fixed Hilt injection with @ApplicationContext
- UgoCalcNavHost: Auth-gated start destination (AUTH vs HOME flow)
- Performance improvements in PhysicsEngine and inference pipeline

### Removed
- Firebase Realtime Database integration
- Firebase Firestore integration
- Firebase Cloud Messaging dependencies
- Google Services configuration (purged from git history via filter-repo)
- GitHub-based update notifications
- All Firebase-related build dependencies

### Security
- RS256 JWT verification for all Lambda API requests
- Base64 fallback token verification for testing
- Least-privilege IAM roles per Lambda function
- Cognito User Pool password policies and MFA support
- Account-specific data isolation via AWS Identity Pool

## [2.4.0] - 2026-02-19

### Added
- Logo and UI aesthetic improvements
- Enhanced calibration voice commands
- Backup system improvements

### Fixed
- UI bug fixes and minor improvements
- Gyroscope calibration adjustments

### Changed
- Calibration voice commands interface

---

## Installation & Deployment

### Prerequisites
- AWS Account with appropriate permissions
- Android Studio & Gradle
- Node.js for Lambda development

### Deployment Steps for v3.0.0

1. **Deploy AWS Infrastructure**
   ```bash
   cd infra
   ./deploy.sh prod
   ```

2. **Configure Android App**
   - Copy CloudFormation outputs to `app/src/main/res/raw/amplifyconfiguration.json`
   - Required fields: UserPoolId, UserPoolClientId, IdentityPoolId, ApiEndpoint

3. **Build Android App**
   ```bash
   ./gradlew assembleDebug
   ```

---

## Migration Guide from v2.4.0 to v3.0.0

This is a major version bump with significant architectural changes:

- **No backward compatibility** with Firebase-based installations
- Requires AWS infrastructure deployment
- New authentication flow with Cognito User Pool
- Data migration from Firebase to DynamoDB (handled by Lambda auto-provisioning on first login)
- Updated Android Manifest for Cognito redirect URIs
