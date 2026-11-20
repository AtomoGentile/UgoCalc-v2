# UgoCalc Backend — 100% Gratis Per Sempre (Cloudflare Workers + D1)

Questo pacchetto replica **tutte** le API del backend AWS (`infra/cloudformation.yaml` + `lambda/*`) su **Cloudflare Workers + Cloudflare D1 (SQLite serverless)** a **0,00 €/mese per sempre**, senza carta di credito obbligatoria e **senza scadenza a 12 mesi**.

---

## 1. Perché AWS rischia di costare nel tempo vs Cloudflare Free

### Rischi di costo su AWS (`infra/cloudformation.yaml`)
| Servizio AWS usato nel repo | Piano Gratuito AWS | Rischio reale di spesa |
|---|---|---|
| **API Gateway HTTP API (`ugocalc-api`)** | Solo **12 mesi** dalla creazione account (1M chiamate/mese) | Dopo 12 mesi paghi ~$1.00/milione di richieste + data transfer in uscita, anche con traffico minimo. |
| **Amazon S3 (`ugocalc-lambda-deployments-prod` + APK)** | Solo **12 mesi** (5 GB) | `deploy.sh` attiva il **Versioning** senza regole di scadenza: ogni zip (`~2.7 MB`) e APK vecchio resta salvato e fatturato dopo il 12° mese. |
| **CloudWatch Logs (6 Lambda)** | 5 GB totali Always Free | `cloudformation.yaml` non imposta `RetentionInDays`: i log restano **per sempre**, crescendo nel tempo. |
| **DynamoDB (`PAY_PER_REQUEST`, 5 tabelle)** | 25 GB Always Free | Nessun **hard cap**: un loop di retry o abuso su `/bias` (che fa `ScanCommand` sull'intera tabella `ugocalc-events`) può generare costi imprevisti. |
| **Route 53 / dominio `api.ugocalc.com`** | **Non gratuito** | **$0,50/mese fissi** per ogni Hosted Zone + costi query DNS. |
| **AWS Cognito (User + Identity Pool)** | Always Free fino a **50.000 MAU** | Gratis per pochi utenti, ma richiede manutenzione di stack CloudFormation, OAuth Google e Runtime Node.js Lambda (`nodejs20.x`). |

### Perché Cloudflare Workers + D1 è Gratis Per Sempre (0 €)
- **Cloudflare Workers Free**: **100.000 richieste/giorno** (~3 milioni/mese) — **Always Free, non scade dopo 12 mesi**.
- **Cloudflare D1 (SQLite serverless) Free**: **5 GB** di storage, **5 milioni di righe lette/giorno**, **100.000 righe scritte/giorno** — **Always Free**.
- **HTTPS gratis automatico**: dominio `https://ugocalc-api.<tuo-subdomain>.workers.dev` con certificato SSL gestito, senza Route 53.
- **Protezione anti-bolletta (Hard Cap)**: nel piano Free, superata la quota giornaliera il Worker risponde semplicemente con errore fino al reset UTC del giorno dopo. **La fattura resta garantita a 0,00 €**.

---

## 2. Cosa copre questo Worker (parità 1:1 con AWS)

- `GET /version` — controllo versione app (`AppVersionClient`)
- `GET /config` — configurazione remota (`RemoteConfigClient`)
- `GET /inference` & `POST /inference` — sync idempotente analisi V2 (`RealCloudSyncClient`)
- `GET /calibration` & `POST /calibration` — sync calibrazioni stoviglie (**aggiunto anche questo endpoint, mancante in `cloudformation.yaml`!**)
- `POST /benchmark` — sync pasti benchmark (`RealCloudSyncClient`)
- `GET /bias` — aggregazione anonima globale
- `POST /share`, `POST /share/generate`, `POST /share/request`, `GET /share/status`, `POST /share/approve`, `GET /invite` — condivisione account (`ShareClient`)
- `POST /auth/token` — opzionale, per autenticare richieste con token leggero HMAC-SHA256 senza usare AWS Cognito

---

## 3. Deploy gratuito in 5 minuti

Prerequisiti: Node.js (`npx`) e un account Cloudflare gratuito (nessuna carta richiesta per Workers/D1 Free).

### Opzione rapida su Windows (1-click guidato, isolato)
Esegui lo script PowerShell dedicato, che tocca **solo** `ugocalc-db` e `ugocalc-api` senza interferire con altri progetti Cloudflare esistenti:

```powershell
.\infra\cloudflare-free\deploy.ps1
```

Lo script verifica il login Cloudflare, crea `ugocalc-db` solo se non esiste già, aggiorna `wrangler.toml`, applica `schema.sql`, configura opzionalmente `AUTH_SECRET` e pubblica `ugocalc-api` stampando l'URL finale e verificando `/health`.

### Opzione manuale (Windows / macOS / Linux)

```bash
cd infra/cloudflare-free

# 1. Login su Cloudflare
npx wrangler login

# 2. Crea il database SQLite serverless D1
npx wrangler d1 create ugocalc-db
```

Copia il `database_id` stampato dal comando dentro `wrangler.toml` al posto di `REPLACE_WITH_YOUR_D1_DATABASE_ID`.

```bash
# 3. Crea le tabelle e i record iniziali (config + version) su D1 remoto
npx wrangler d1 execute ugocalc-db --remote --file=./schema.sql

# 4. Imposta un segreto per autenticazione leggera (se vuoi spegnere anche AWS Cognito)
npx wrangler secret put AUTH_SECRET

# 5. Pubblica il Worker
npx wrangler deploy
```

Alla fine `wrangler deploy` stampa l'URL pubblico gratuito, ad esempio:
`https://ugocalc-api.<tuo-subdomain>.workers.dev`

> **Vuoi tenere AWS Cognito (Always Free fino a 50k MAU) per il login con Google?**
> Imposta `COGNITO_USER_POOL_ID` e `COGNITO_CLIENT_ID` in `wrangler.toml`: il Worker verifica i JWT RS256 di Cognito nativamente con Web Crypto, senza nessuna dipendenza AWS!

---

## 4. Collegare l'app Android

Apri `app/src/main/java/com/ugocalc/app/di/NetworkModule.kt`:

### Opzione A — Backend completo gratis su Cloudflare Workers + D1
Imposta `AWS_BASE_URL` con l'URL del tuo Worker:
```kotlin
private const val AWS_BASE_URL = "https://ugocalc-api.<tuo-subdomain>.workers.dev"
```

### Opzione B — Solo aggiornamenti & config gratis su GitHub (Zero Server)
È già l'impostazione predefinita per gli aggiornamenti dell'app:
1. **`AppUpdateRepository`** usa **GitHub Releases** (`GitHubReleaseClient` su `AtomoGentile/UgoCalc-v2`) come meccanismo primario autonomo per rilevare nuove versioni e scaricare l'APK, senza dipendere da AWS `/version`.
2. **`STATIC_VERSION_URL` e `STATIC_CONFIG_URL`** in `NetworkModule.kt` puntano già ai JSON su `raw.githubusercontent.com/AtomoGentile/UgoCalc-v2/main/...` (CDN gratuita illimitata) senza richiedere AWS.
3. Per disabilitare anche la sync account/share e usare l'app 100% in locale, imposta `AWS_BASE_URL = ""`.

### Opzione C — 100% Locale / Offline (Zero backend)
```kotlin
private const val AWS_BASE_URL = ""
private const val STATIC_VERSION_URL = ""
private const val STATIC_CONFIG_URL = ""
```
L'app si avvia direttamente in Home senza schermata di login e senza alcuna chiamata o retry di rete verso il backend.

---

## 5. Come spegnere lo stack AWS in sicurezza

Dopo aver verificato che l'app punta al nuovo endpoint Cloudflare (o a `""`), puoi eliminare lo stack AWS per evitare costi futuri:

```bash
# 1. Svuota il bucket S3 di deploy (altrimenti CloudFormation o S3 possono trattenerlo)
aws s3 rm s3://ugocalc-lambda-deployments-prod --recursive
aws s3 rb s3://ugocalc-lambda-deployments-prod --force

# 2. Elimina l'intero stack (API Gateway, 6 Lambda, 5 DynamoDB, Cognito, IAM)
aws cloudformation delete-stack --stack-name ugocalc-prod --region us-east-1
```

Controlla nella console AWS Billing / CloudFormation che non restino Log Group CloudWatch vecchi (opzionale: eliminali da CloudWatch -> Log groups `/aws/lambda/ugocalc-*`).
