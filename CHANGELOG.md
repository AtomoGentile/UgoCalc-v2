# Changelog

All notable changes to UgoCalc2 will be documented in this file.

## [4.3.0] - 2026-11-21

### Added
- **Expanded Italian CREA Nutrition Database (600+ Foods across 14 Categories)**: Expanded `StarterFoodDatabase` from ~200 to 600+ Italian foods and dishes with CREA nutritional values (CHO, Protein, Fat per 100g) and Atkinson 2021 Glycemic Index (`IG`), organized into 14 `ItalianFoodCategory` groups (`Pasta e Cereali`, `Primi e Piatti Pronti`, `Pane, Pizza e Forno`, `Patate e Rustici`, `Legumi e Veg`, `Carni`, `Salumi`, `Pesce e Mare`, `Uova e Latticini`, `Frutta e Semi`, `Verdure`, `Condimenti e Salse`, `Dolci e Colazione`, `Bevande`), plus enriched Italian dish keywords in `GlycemicIndexResolver`.
- **Automatic Non-Destructive Starter Catalog Sync (`CATALOG_VERSION = 2`)**: Added versioned catalog synchronization so existing users automatically receive all 600+ new CREA entries and updated macro/GI data on launch without overwriting or losing custom tables or user-edited foods.
- **Category Filter Chips in Food Search**: Added a compact horizontal `LazyRow` of `FilterChip`s (`Tutti` + all 14 categories) with dynamic section item counts in `FoodSearchPicker` across Food Hub, Database Search, Manual Entry, Final Result, and Favorites.

### Changed
- **Smart Accent-Insensitive Multi-Token Search**: Improved `TableRepository` food search with Unicode accent stripping (`café`/`caffè` -> `caffe`), multi-token order-independent matching, and relevance ranking, plus expanded browse capacity (`limit = 1000`) to explore the full 600+ food catalog.
- **Database Search Favorite Propagation**: Updated `DatabaseSearchScreen` to forward `proteinePer100g` and `indiceGlicemico` when saving a favorite from Database Search.
- **CI Artifact Versioning**: Updated `.github/workflows/android-ci.yml` debug APK artifact name to `UgoCalc-4.3.0-debug-apk`.

## [4.2.0] - 2026-11-21

### Added
- **Protein Counting (`Proteine/100g`)**: Full end-to-end protein tracking across the CREA starter database, Room schema (migration 25 → 26), OpenFoodFacts barcode lookup, AI online research, manual food entry, table editor, favorites, meal totals, history, charts, and share summaries (including true 3-macro `4*CHO + 9*Grassi + 4*Prot` kcal calculation).
- **Glycemic Index & Glycemic Load Engine**: Added `indiceGlicemico` (`IG 0–100`) across food models, CREA starter foods, AI online research, and table editor, with automatic category-based GI estimation fallback and color-coded Glycemic Load (`GL` Basso/Medio/Alto) badges.
- **Interactive Glycemic Curve Simulator (`GlycemicCurveCard` & `DailyGlycemicTimelineCard`)**: Added a physiological post-prandial blood glucose simulator (0–240 min) with target range band, peak & 2h readouts, FPU delayed fat/protein curve, interactive tap/drag scrub, inline pre-meal glycemia stepper (`-10`/`+10 mg/dL`), expandable per-food GI/GL breakdown, and daily multi-meal glycemic timeline on Home and History.
- **Glycemic Profile Settings (`Profilo Glicemico`)**: Added dedicated settings screen with clinical presets (`Standard`, `Sensibilità moderata`, `Diabete / Impatto pieno`), baseline/sensitivity/target sliders, and a live interactive curve preview for a sample Italian meal.

### Changed
- **3-Macro Visualization Suite**: Added Cobalt Blue semantic protein tokens (`UgoSemanticColors.protein`), upgraded `MacroProgressRing` and `MacronutrientPieChart` to 3 segments (CHO, Proteine, Grassi), and added the 3rd Protein series to `HistoryColumnChart` and `HistoryLineChart`.
- **CI Artifact Versioning**: Updated `.github/workflows/android-ci.yml` debug APK artifact name to `UgoCalc-4.2.0-debug-apk`.

## [4.1.0] - 2026-11-20

### Added
- **CameraX Pro Studio & 60fps Spirit-Level Reticle**: Integrated in-app CameraX capture studio with a 60fps spirit-level horizon reticle, optical stability feedback, and live camera telemetry (`CameraX Pro · Telemetria 3D`).
- **Live Optical Distance & FoV Telemetry in AI V1/V2**: Enriched EXIF/Camera2 metadata with focus distance, physical FoV dimensions, focal length, ISO, and stability status injected into AI V1 and V2 perception prompts, plus a collapsible `"CameraX · Telemetria attiva"` card on the Vision Results screen.
- **1-Tap Meal Repeat & Share**: Added 1-tap **"Ripeti"** on Recent Meals (preloading items into the editable quantity draft) and 1-tap plain-text meal summary sharing on both Final Result and History Detail screens.

### Changed
- **Home, Results & Manual Entry UX Polish**: Added dynamic Italian time-of-day greeting, 7-day meal counter badge, `MacroProgressRing` on Today's summary card, `AnimatedMetricValue` rolling numbers, inline `-10g` / `+10g` weight steppers on Final Result, and tactile haptic feedback across key interactions.
- **CI Artifact Versioning**: Updated `.github/workflows/android-ci.yml` debug APK artifact name to `UgoCalc-4.1.0-debug-apk`.

## [4.0.0] - 2026-11-19

### Added
- **Local-First Onboarding & Italian Starter Database**: Added a guided first-launch onboarding flow (API keys, scale calibration, starter database setup) and a curated ~180-entry Italian CREA nutritional starter database (`Tabella Base Italiana (CREA)`).
- **CI/CD Pipeline**: Added GitHub Actions workflow (`.github/workflows/android-ci.yml`) running unit tests, Android Lint, debug APK build, and R8/ProGuard release APK verification with artifact uploads.

### Changed
- **Complete UI/UX Overhaul**: Redesigned visual identity, theme tokens, cards, pills, empty states, navigation, camera capture, results, history, and settings screens with modern Material 3 aesthetics and accessibility improvements.
- **Food Search & Catalog**: Improved food search browsing with immediate starter database exploration, recent searches, and dynamic AI model catalog updates across providers.

### Fixed
- **AI Provider & Hybrid Matching Reliability**: Hardened response parsing across Gemini, Vertex AI, OpenAI, and Anthropic clients, added EncryptedSharedPreferences corruption recovery, and improved locale-aware numeric parsing and hybrid food matching reliability.

## [3.0.4] - 2026-08-14

### Fixed
- **Account Sharing Flow**: The invite link navigation is now correctly gated behind authentication, preventing token loss for non-logged-in users.
- **Data Synchronization**: Added an immediate data pull trigger after a guest's sharing request is approved, ensuring instant access to shared history and bias data.
- **API Resilience**: Improved error handling and response validation in `ShareClient` for AWS backend calls.

### Changed
- **Network Logging**: Increased OkHttp logging level to `BODY` for improved debuggability of API interactions.

## [3.0.3] - 2026-07-27

### Added
- **OCR Support for Scanned PDFs and Images**
  - ML Kit Text Recognition for on-device OCR processing
  - Support for JPG and PNG image formats
  - Automatic scanned PDF detection (blank/minimal text extraction triggers OCR prompt)
  - Multi-language support: Latin model (Italian, English, French, German, Spanish, Portuguese, Turkish) + modular packs (Chinese Simplified/Traditional, Japanese, Korean, Devanagari)
  - Async OCR processing with `Dispatchers.Default` to avoid UI blocking

### Fixed
- **Scanned PDF handling**: Users can now retry failed scanned PDFs — second attempt triggers OCR automatically
- **ML Kit OCR initialization**: Use default TextRecognition client without explicit options (Latin script recognition enabled by default)

### Changed
- Extended `OfficialFileType` enum with `JPG` and `PNG` variants
- Updated file picker to accept MIME types: `image/jpeg`, `image/png`
- `DocumentTextExtractor` modified to route JPG/PNG to `extractWithOcr()` path

### Dependencies
- Added: `com.google.mlkit:text-recognition:16.0.0` (base model)
- Added: `com.google.mlkit:text-recognition-{chinese,devanagari,japanese,korean}:16.0.0` (language packs)
- Added: `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1` (Task.await() integration)

---

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
