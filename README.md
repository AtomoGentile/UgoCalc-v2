# UgoCalc

**UgoCalc** è un'applicazione Android avanzata per l'analisi visiva di alimenti e il calcolo nutrizionale mediante intelligenza artificiale. Utilizza la fotocamera dello smartphone per riconoscere piatti, estrarre informazioni nutrizionali e tracciare l'evoluzione delle tendenze alimentari nel tempo.

## 📱 Caratteristiche Principali

### Analisi Visiva Alimentare
- **Riconoscimento AI dei piatti**: Identifica automaticamente alimenti da foto tramite modelli ML
- **Estrazione OCR multilingue**: Supporto per testo in latino (italiano, inglese, francese, tedesco, spagnolo, portoghese, turco) e pacchi linguistici modulari (cinese semplificato/tradizionale, giapponese, coreano, devanagari)
- **Scansione di documenti PDF e immagini**: Elaborazione di ricette, menù e tabelle nutrizionali tramite OCR on-device (ML Kit Text Recognition)
- **Overlay della fotocamera**: Livella digitale in tempo reale per garantire l'angolazione corretta dello scatto

### Tracciamento e Statistiche
- **Database locale efficiente**: Archivio persistente di tutte le analisi tramite Room database
- **Aggregazione bias**: Analisi statistica dell'evoluzione delle scelte alimentari
- **Modalità multiple**:
  - CASA: tracciamento domestico
  - RISTORANTE: conteggio pasti fuori casa
  - SHADOW MODE: test e validazione senza registrare i dati

### Infrastruttura Cloud
- **Migrazione AWS completa**: Da Firebase a infrastruttura AWS nativa
- **Autenticazione Cognito**: Gestione utenti con User Pool, signup/signin sicuri
- **Multi-utente e condivisione**: Sincronizzazione tra dispositivi, inviti di condivisione, accesso ai dati aggregati
- **Lambda serverless**: Funzioni dedicate per inferenza, benchmarking, configurazione, condivisione e aggregazione bias
- **DynamoDB**: Storage scalabile per utenti, cronologia inferenza, eventi e dati bias

### Sicurezza
- **JWT RS256 verification**: Tutti i token Lambda vengono verificati
- **Secure API Key Store**: Memorizzazione protetta delle credenziali
- **Least-privilege IAM**: Ruoli specifici per ogni funzione Lambda
- **Compliance**: Conformità ai requisiti legali per l'archiviazione dei token di sessione

## 🎯 A Chi Si Rivolge

### Profilo Primario
- **Nutrizionisti e dietologi**: Monitoraggio dettagliato dell'assunzione nutrizionale dei pazienti
- **Sportivi e atleti**: Tracciamento delle calorie e nutrienti per ottimizzare le prestazioni
- **Persone consapevoli della salute**: Controllo consapevole e gestione della dieta quotidiana

### Profilo Secondario
- **Ricercatori**: Studio dei comportamenti alimentari e tendenze nutrizionali
- **Strutture sanitarie**: Programmi di monitoraggio della salute basati su foto
- **Team medico**: Coordinamento multi-dispositivo nel tracciamento paziente

## 🚀 Requisiti Tecnici

### Dispositivo
- **Sistema operativo**: Android 8.0+ (API 26+) — ottimizzato per Android 15 (API 35)
- **RAM**: 2GB minimo (4GB+ consigliato)
- **Hardware obbligatorio**: Fotocamera
- **Hardware opzionale**: Giroscopio (degrada elegantemente se assente)

### Account
- **AWS Account**: Per il deployment dell'infrastruttura backend
- **Credenziali Cognito**: User Pool ID e Client ID per l'autenticazione

### Dipendenze SDK
- Kotlin 2.0.21
- Jetpack Compose (2024.12.01+)
- Hilt per Dependency Injection
- ML Kit Text Recognition (on-device OCR)
- Amplify Java SDK v2 per AWS integration

## 📦 Installazione e Deployment

### 1. Preparazione Ambiente

```bash
# Clone del repository
git clone <repo-url>
cd UgoCalc2

# Installazione dipendenze (via Gradle)
./gradlew build
```

### 2. Deployment Infrastruttura AWS

```bash
cd infra
./deploy.sh prod
```

Lo script CloudFormation provvede automaticamente a:
- Creazione Cognito User Pool
- Setup Identity Pool
- Provisioning DynamoDB tables
- Deployment Lambda functions
- Configurazione API Gateway

### 3. Configurazione App Android

Dopo il deployment AWS, copiare gli output CloudFormation in:
```
app/src/main/res/raw/amplifyconfiguration.json
```

Campi obbligatori:
- `UserPoolId`
- `UserPoolClientId`
- `IdentityPoolId`
- `ApiEndpoint`

### 4. Build e Installazione

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (con minification)
./gradlew assembleRelease
```

## 🔄 Migrazione dalla v2.4.0

La versione 3.0.0+ richiede una migrazione significativa:

- **❌ Non compatible** con installazioni basate su Firebase
- **✅ Nuova autenticazione** tramite Cognito User Pool
- **✅ Nuovo storage** in DynamoDB (auto-provisioning al primo login)
- **✅ Setup Manifest**: Aggiornamento URI redirect Cognito in AndroidManifest.xml

I dati Firebase NON sono migrati automaticamente — pianificare una migrazione manuale se necessario.

## 📊 Struttura Progetto

```
UgoCalc2/
├── app/                              # Android application (Kotlin + Compose)
│   ├── src/main/java/com/ugocalc/app/
│   │   ├── data/                     # Repository, DAO, migrations
│   │   ├── domain/                   # Domain models (AiModel, etc)
│   │   ├── ui/                       # Compose screens & ViewModels
│   │   └── di/                       # Hilt modules
│   └── build.gradle.kts
├── lambda/                           # AWS Lambda functions (Node.js)
│   ├── inference/                    # Model inference dispatcher
│   ├── benchmarks/                   # Performance aggregation
│   ├── config/                       # Configuration endpoint
│   ├── sharing/                      # Account sharing logic
│   └── bias-aggregation/             # Statistical analysis
├── infra/                            # Infrastructure as Code
│   ├── cloudformation.yaml           # Complete AWS stack
│   └── deploy.sh                     # Deployment automation
└── docs/                             # Documentation
```

## 🏗️ Architettura

### Tier Presentation
- **Jetpack Compose**: Modern declarative UI framework
- **Material Design 3**: Design system coerente
- **Navigation Compose**: Type-safe routing

### Tier Domain
- **ViewModels**: State management con Lifecycle awareness
- **UseCases**: Business logic separation

### Tier Data
- **Room Database**: SQLite persistenza locale
- **AWS Lambda**: Backend serverless
- **DynamoDB**: NoSQL storage
- **Cognito**: User management

### Cross-cutting
- **Hilt**: Dependency injection
- **Coroutines**: Async/concurrency
- **ML Kit**: On-device OCR
- **Amplify SDK**: AWS integration

## 🔧 Configurazione Modelli AI

I modelli AI sono configurabili per tre contesti:

| Contesto | Uso | Configurazione |
|----------|-----|----------------|
| **Analysis** | Analisi principale piatti | `AiModel.analysisModel` |
| **Normalization** | Parsing normalizzazione | `AiModel.normalizationModel` |
| **Shadow Mode** | Test & validazione | `AiModel.shadowModeModel` |

Ogni contesto può usare modelli diversi — configurabile da app settings.

## 📝 Versionamento

- **Versione**: 3.0.4
- **Build**: API 35 (Android 15)
- **Rilascio**: 14 agosto 2026

### Versioni Recenti
- `3.0.4`: Fix flusso condivisione account e sincronizzazione dati
- `3.0.3`: OCR multilingue per PDF scansionati e immagini
- `3.0.1`: Fix migrazione DB e dependenze Compose
- `3.0.0`: Migrazione completa AWS + multi-user
- `2.4.0`: Miglioramenti UI e calibrazione

## 🤝 Contribuire

Questo è un progetto attivo con aggiornamenti regolari. Per contribuire:

1. Fork del repository
2. Branch feature: `git checkout -b feature/description`
3. Commit dei cambiamenti
4. Push al branch
5. Apertura Pull Request con descrizione dettagliata

## 📄 Licenza

[Specifica licenza — da definire]

## 📞 Supporto e Contatti

- **Rapporto bug**: Aprire issue nel repository
- **Feature request**: Discussioni GitHub
- **Domande**: [Contatto support]

---

**UgoCalc** — Intelligenza artificiale per una nutrizione consapevole.
