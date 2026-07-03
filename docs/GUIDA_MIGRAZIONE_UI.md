# Guida per il prossimo agente di sviluppo — cosa c'è già in questo repo

Questo repository (`UgoCalcV2`) parte da una **copia integrale e funzionante** del progetto
Android UgoCalc v1 (stesso `app/`, stesso `build.gradle.kts`, stesse risorse). Non è uno
scheletro vuoto: compila e gira esattamente come v1, perché è più sicuro evolvere un'app che
funziona piuttosto che ripartire da zero e rischiare di rifare lavoro già fatto.

Il piano tecnico del nuovo motore di inferenza è in
[`InferenceEngineV2.doc`](InferenceEngineV2.doc). Questo documento serve a un solo scopo:
**dire esattamente quali file toccare per costruire quel motore, e quali NON toccare perché sono
grafica/infrastruttura già a posto.**

Non è necessario indovinare cosa sia "engine" e cosa sia "grafica": la mappa è qui sotto,
file per file.

---

## 1. Non toccare (grafica e infrastruttura, riusabile così com'è)

Questi package non hanno alcun legame con il motore di inferenza (vecchio o nuovo). Vanno
lasciati esattamente come sono finché non si decide un affinamento estetico volontario, separato
da questo lavoro:

- `ui/theme/` — tema Compose
- `ui/components/` — tutti i componenti condivisi (badge, dialoghi, grafici, loading overlay, ecc.)
- `ui/navigation/` — `Routes.kt` e `UgoCalcNavHost.kt` (le rotte andranno solo estese, non riscritte, quando si aggiungerà la selezione modalità Casa/Ristorante — vedi §4)
- `ui/home/`, `ui/manualentry/`, `ui/barcode/`, `ui/history/`, `ui/modelselect/`
- `ui/settings/` — incluse tutte le schermate (`AccessibilitySettingsScreen`, `ApiSettingsScreen`, `ConfidenceSettingsScreen`, `FavoritesSettingsScreen`, `HistoryChartSettingsScreen`, `PermissionsSettingsScreen`, `PreferredModelSettingsScreen`, `SettingsScreen`, `ChangelogScreen`)
- `data/repository/FavoritesRepository.kt`, `HistoryRepository.kt`, `SettingsRepository.kt`
- `data/repository/BarcodeRepository.kt`, `data/remote/barcode/OpenFoodFactsClient.kt`
- `data/local/db/entity/FavoriteShortcutEntity.kt`, `SearchFrequencyEntity.kt`
- `data/local/prefs/`, `util/`, `domain/model/AiModel.kt`, `domain/model/Changelog.kt`,
  `domain/model/Confidence.kt`, `domain/model/LoadingMessages.kt`

## 2. Invariato per decisione di design (Nutrition Engine, moduli M6/M7 del piano)

Il documento di architettura conferma esplicitamente il motore nutrizionale della v1 (matching
sulle tabelle ufficiali + ricerca online di riserva): non va riscritto, solo eventualmente esteso.

- `data/repository/TableRepository.kt`
- `data/local/db/entity/OfficialTableEntity.kt`, `NormalizedFoodEntryEntity.kt`, `NormalizationStatus.kt`
- `domain/model/NormalizedTable.kt`, `domain/model/OnlineFoodResearchResult.kt`
- `domain/usecase/MatchFoodsAcrossTablesUseCase.kt` (usa `AiOrchestrator.runMatching`, non tocca la Visione)
- `ui/settings/tables/` (editor tabelle, ricerca database) — grafica pura, invariata
- In `domain/prompts/PromptTemplates.kt`: **restano invariati** `NORMALIZATION_PROMPT_HEADER`,
  `normalizationPrompt()`, `matchingPrompt()`, `onlineFoodResearchPrompt()`. Va sostituito
  **solo** `VISION_PROMPT` (vedi §3).
- In `data/remote/ai/dto/AiDtos.kt`: **restano invariati** `NormalizationDto`, `MatchingDto`,
  `MatchingInputFoodDto`, `MatchingInputTableEntryDto`, `OnlineFoodResearchDto`. Va sostituito
  **solo** `VisionAnalysisDto` (e i suoi tipi annidati).
- In `data/remote/ai/AiOrchestrator.kt`: **restano invariati** `runTableNormalization`,
  `runMatching`, `runOnlineFoodResearch`. Va sostituito **solo** `runVisionAnalysis`.
- `data/remote/ai/client/AiClients.kt`, `data/remote/ai/auth/VertexAiAuth.kt` — client HTTP per
  provider, indipendenti dalla forma del prompt: invariati.

## 3. Il punto di innesto esatto (il "seam" tra vecchio e nuovo motore)

Questa è la cosa più importante da capire prima di scrivere codice.

Tutta la parte a valle della Visione — abbinamento tabelle (M6), calcolo finale (M7),
salvataggio nello storico, preferiti — **non lavora mai con pixel o con la scena riconosciuta:
lavora con una lista di `RecognizedFood`.**

```kotlin
// domain/model/VisionResult.kt
data class RecognizedFood(
    val nome: String,
    val pesoStimatoG: Double,
    val commento: String,
    val confidenza: Confidence,
)
```

Da qui in poi (`MatchFoodsAcrossTablesUseCase`, `CalculatedFoodLine`, `AnalysisTotals`,
`FinalResultViewModel`, `HistoryRepository`) **non importa come `pesoStimatoG` sia stato
calcolato.** Che sia il singolo numero dichiarato dall'IA (come in v1) o il risultato di
`Motore fisico (M3)` — geometria, densità, catena di calcolo — per queste classi è lo stesso
identico dato.

**Conseguenza pratica per il nuovo motore:** l'ultimo passo della nuova pipeline (dopo M0→M5,
prima di M6) deve produrre un adattatore che trasforma l'elenco di `WeightEstimate` (uscita di
M3/M4/M5, con formula e fonti) in un elenco di `RecognizedFood` — mantenendo `commento` come
sintesi leggibile della catena di calcolo (es. "165 g — cerchio Ø27 cm, 40% piatto, densità riso
cotto") invece della nota visiva libera di v1. Fatto questo, **tutto il resto dell'app — matching,
calcolo CHO/grassi, schermata finale, storico, preferiti — non richiede nessuna modifica.**

Questo è anche il motivo per cui ha senso arrivare al motore nuovo in due tempi: si può costruire
e testare M0→M5 in isolamento, verificarne l'output contro il benchmark (§12.1 del piano), e solo
alla fine collegarlo con questo singolo adattatore, senza toccare tutto il resto dell'app nel
frattempo.

## 4. File da sostituire integralmente (motore v1, oggetto della riscrittura)

Questi file incarnano il "singolo passaggio di visione" che la V2 sostituisce con la pipeline
M0→M5. Vanno riscritti secondo `InferenceEngineV2.doc`:

- `domain/model/VisionResult.kt` — `RecognizedFood` resta (è il contratto d'uscita, §3 sopra),
  ma `VisionAnalysisResult` e `ModelVisionOutcome` sono legati alla singola chiamata di v1:
  vanno ripensati come output di M1 (`SceneGraph`) e del risultato finale della pipeline.
- `domain/prompts/PromptTemplates.kt` — solo `VISION_PROMPT`: da sostituire con i prompt di M1
  (percezione unificata) e, se serve, M5 (arbitrato selettivo).
- `data/remote/ai/dto/AiDtos.kt` — solo `VisionAnalysisDto` e i tipi annidati: da sostituire con
  i DTO di `SceneGraph`/`WeightEstimate` (§8 del piano).
- `data/remote/ai/AiOrchestrator.kt` — solo `runVisionAnalysis`: diventa il punto che orchestra
  M0→M5 (oggi chiama un solo endpoint, domani probabilmente due: percezione + eventuale
  arbitrato).
- `domain/usecase/RunVisionForModelsUseCase.kt` — oggi lancia una chiamata di Visione in
  parallelo per ogni modello selezionato dall'utente (funzione "confronta più IA"). Da decidere:
  se questa funzionalità di confronto multi-modello ha ancora senso con la pipeline modulare (il
  confronto sposta il costo da 1x a Nx chiamate per ogni modulo) o se va limitata al solo M1.
  **Non deciso in questo documento — richiede una scelta esplicita dell'owner prima di procedere.**
- `ui/camera/CameraCaptureViewModel.kt` — la Screen (UI di scatto/scelta foto) resta identica;
  la ViewModel va ricollegata al nuovo entry point della pipeline invece che a
  `RunVisionForModelsUseCase` diretto.
- `ui/results/VisionResultsViewModel.kt` — stessa cosa: la Screen (lista alimenti, badge di
  confidenza, modifica manuale) è riusabile quasi as-is; la ViewModel oggi legge
  `ModelVisionOutcome`/`VisionAnalysisResult` e andrà aggiornata sul nuovo output di M1, prima
  che l'adattatore di §3 lo trasformi in `RecognizedFood` per il resto del flusso.

## 5. Cosa aggiungere ex novo (non esiste nulla di simile in v1)

Per la modalità Casa/Ristorante e la calibrazione (§8 e §10 del piano):

- Tre nuove entity/DAO Room: `HomeDinnerwareCalibrationEntity`, `UserBodyReferenceEntity`,
  `BiasCorrectionEntity` (schema indicativo nel piano, §8).
- Un campo `modalita` su `AnalysisHistoryEntity` (unica modifica a una tabella esistente).
- Una schermata di impostazioni per la calibrazione (stoviglie di casa + riferimenti corporei) —
  nuova, non sostituisce nulla.
- Uno switch Casa/Ristorante a inizio sessione (probabilmente in `HomeScreen` o come primo passo
  del flusso di analisi) che imposta la modalità in `AnalysisSessionStore`.
- I moduli M0 (quality gate), M2 (router di strategia), M3 (motore fisico), M4 (verifica) sono
  interamente nuovi: nessun file v1 li anticipa, vanno scritti da zero seguendo §6 del piano.

## 6. Ordine di lavoro consigliato

1. Costruire il benchmark pesato (§12.1 del piano) — prerequisito, indipendente dal codice.
2. Scrivere M0, M2, M3, M4 come moduli deterministici isolati, con i loro unit test (§11 del
   piano), senza toccare nessuna Activity/ViewModel esistente.
3. Scrivere il nuovo M1 (prompt + DTO + parsing) e validarlo da solo contro il benchmark.
4. Solo a questo punto collegare il nuovo output alla pipeline tramite l'adattatore verso
   `RecognizedFood` (§3) e aggiornare `CameraCaptureViewModel`/`VisionResultsViewModel` per
   usarlo — il resto dell'app (matching, risultato finale, storico) non richiede modifiche.
5. Aggiungere modalità Casa/Ristorante e calibrazione (§5 sopra) come ultimo passo, quando la
   pipeline di base è già validata.

Ogni fase è verificabile e committabile da sola, in linea con lo sviluppo a piccoli passi già
in uso nel progetto.
