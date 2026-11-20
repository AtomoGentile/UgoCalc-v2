package com.ugocalc.app.domain.inference.m0

/**
 * M0 – Controllo qualità e ingestione (piano V2 §4, §5). Modulo deterministico che scarta o segnala
 * le foto inutilizzabili (sfocatura, esposizione estrema, duplicati) **prima** di consumare token in
 * M1. Se tutte le foto sono scadenti, l'app lo dice subito invece di produrre una stima inaffidabile.
 *
 * Qui vive solo la **logica di decisione**, che opera su metriche già calcolate ([PhotoQuality]):
 * è pura e testabile con valori sintetici. L'estrazione delle metriche dal bitmap/EXIF (nitidezza,
 * luminosità, hash percettivo) è responsabilità del livello Android e non passa da questo modulo.
 */
class QualityGate(
    private val sharpnessMin: Double = DEFAULT_SHARPNESS_MIN,
    private val brightnessMin: Double = DEFAULT_BRIGHTNESS_MIN,
    private val brightnessMax: Double = DEFAULT_BRIGHTNESS_MAX,
    private val duplicateMaxDistance: Int = DEFAULT_DUPLICATE_MAX_DISTANCE,
    private val motionMaxRadPerSec: Double = DEFAULT_MOTION_MAX_RAD_PER_SEC,
    private val isoMax: Int = DEFAULT_ISO_MAX,
    private val exposureTimeMaxSeconds: Double = DEFAULT_EXPOSURE_TIME_MAX_SECONDS,
) {

    fun assess(photos: List<PhotoQuality>): IngestionResult {
        val valide = mutableListOf<String>()
        val scartate = mutableListOf<PhotoAssessment>()
        val hashAccettati = mutableListOf<Long>()

        for (foto in photos) {
            val motivo = when {
                foto.motionRadPerSec != null && foto.motionRadPerSec > motionMaxRadPerSec ->
                    PhotoRejectionReason.MOSSA_DAL_SENSORE
                foto.sharpness < sharpnessMin -> PhotoRejectionReason.SFOCATA
                foto.brightness < brightnessMin -> PhotoRejectionReason.TROPPO_SCURA
                foto.brightness > brightnessMax -> PhotoRejectionReason.TROPPO_CHIARA
                foto.cameraMetadata?.isoValue != null && foto.cameraMetadata.isoValue > isoMax ->
                    PhotoRejectionReason.RUMOROSA_ISO
                foto.cameraMetadata?.exposureTimeSeconds != null && foto.cameraMetadata.exposureTimeSeconds > exposureTimeMaxSeconds ->
                    PhotoRejectionReason.MOSSA_ESPOSIZIONE
                hashAccettati.any { hammingDistance(it, foto.hash) <= duplicateMaxDistance } ->
                    PhotoRejectionReason.DUPLICATA
                else -> null
            }
            if (motivo == null) {
                valide += foto.id
                hashAccettati += foto.hash
            } else {
                scartate += PhotoAssessment(foto.id, accettata = false, motivo = motivo)
            }
        }
        return IngestionResult(fotoValide = valide, scartate = scartate)
    }

    companion object {
        /** Varianza del Laplaciano: sotto questa soglia la foto è considerata sfocata. */
        const val DEFAULT_SHARPNESS_MIN = 100.0
        /** Luminosità media (0..255) minima/massima accettabile. */
        const val DEFAULT_BRIGHTNESS_MIN = 40.0
        const val DEFAULT_BRIGHTNESS_MAX = 220.0
        /** Distanza di Hamming massima tra hash percettivi a 64 bit per considerare due foto duplicate. */
        const val DEFAULT_DUPLICATE_MAX_DISTANCE = 10
        /** Velocità angolare (giroscopio, §16) oltre la quale la foto è considerata mossa: soglia fisica, non una stima di nitidezza. */
        const val DEFAULT_MOTION_MAX_RAD_PER_SEC = 1.0
        /** ISO massimo accettabile (§13 punto 7): oltre questa soglia il rumore digitale avvelena la percezione dei dettagli fini. */
        const val DEFAULT_ISO_MAX = 3200
        /** Tempo di esposizione massimo in secondi (§13 punto 7): oltre 1/15s il rischio di micro-mosso (del soggetto o della mano) è troppo alto. */
        const val DEFAULT_EXPOSURE_TIME_MAX_SECONDS = 0.066 // ~1/15s

        fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
    }
}

/**
 * Metriche di qualità di una foto, già calcolate dal livello Android. [hash] è un hash percettivo a
 * 64 bit (bit simili ⇒ immagini simili) usato per riconoscere i duplicati.
 */
data class PhotoQuality(
    val id: String,
    val sharpness: Double,
    val brightness: Double,
    val hash: Long,
    /** Picco di velocità angolare del giroscopio vicino allo scatto (§16), `null` se non disponibile (nessun hardware, o dato non ancora collegato). */
    val motionRadPerSec: Double? = null,
    /** Metadati camera diagnostici (§16): mai letti da [QualityGate.assess], solo trasportati per un uso futuro (es. telemetria M8). */
    val cameraMetadata: CameraMetadata? = null,
    /**
     * Descrizione testuale pronta dell'angolazione REALMENTE misurata dai sensori durante questo
     * scatto (es. "inclinazione 88°, rotazione 3°"), `null` se non disponibile. Mai letta da
     * [QualityGate.assess] (come [cameraMetadata]): solo trasportata fino al prompt di M1
     * ([com.ugocalc.app.domain.inference.m1.PerceptionPrompt]), che la accosta al protocollo di
     * scatto fisso (§13 punto 7) per ciascuna foto nell'ordine in cui vengono inviate.
     */
    val angleDescription: String? = null,
)

/** Metadati EXIF e telemetria ottica Camera2/sensori (§16, CameraX Pro Studio 4.1.0): tutti nullable, i tag sono spesso assenti su foto elaborate o da galleria. */
data class CameraMetadata(
    val isoValue: Int? = null,
    val exposureTimeSeconds: Double? = null,
    /** Lunghezza focale reale in mm. */
    val focalLengthMm: Double? = null,
    /** Lunghezza focale equivalente nel formato 35mm (es. 23mm, 24mm, 70mm). */
    val focalLength35mmEq: Double? = null,
    val aperture: Double? = null,
    /** Dimensione fisica del sensore in millimetri, utile a M3 per correzioni ottiche o stime di scala alternative (§16). */
    val sensorWidthMm: Double? = null,
    val sensorHeightMm: Double? = null,
    /** Distanza di messa a fuoco in diottrie (Camera2 `LENS_FOCUS_DISTANCE`, 1/m). */
    val focusDistanceDiopters: Float? = null,
    /** Distanza stimata fotocamera→piatto in centimetri (`100.0 / focusDistanceDiopters` entro limiti fisici). */
    val estimatedDistanceCm: Double? = null,
    /** Ampiezza fisica orizzontale del campo inquadrato (FoV) al piano di fuoco in centimetri. */
    val fovWidthCm: Double? = null,
    /** Ampiezza fisica verticale del campo inquadrato (FoV) al piano di fuoco in centimetri. */
    val fovHeightCm: Double? = null,
    /** Rapporto di zoom attivo allo scatto (es. `1.0f`, `2.0f`). */
    val zoomRatio: Float? = null,
    /** Stato autofocus Camera2 (`CaptureResult.CONTROL_AF_STATE`). */
    val afState: Int? = null,
    /** Vero se la stabilizzazione ottica (OIS) era attiva allo scatto. */
    val oisEnabled: Boolean? = null,
    /** Rollio laterale del telefono in gradi (`0°` = orizzontale). */
    val rollDeg: Float? = null,
    /** Inclinazione del telefono in gradi (`0°` = verticale, `90°` = piatto dall'alto). */
    val pitchDeg: Float? = null,
    /** Larghezza immagine finale orientata in pixel. */
    val imageWidthPx: Int? = null,
    /** Altezza immagine finale orientata in pixel. */
    val imageHeightPx: Int? = null,
    /** Origine dello scatto: `"CAMERAX_PRO"`, `"SYSTEM_CAMERA"` o `"GALLERY"`. */
    val captureSource: String? = null,
)

/** Motivo per cui una foto è stata scartata da M0. */
enum class PhotoRejectionReason {
    SFOCATA,
    TROPPO_SCURA,
    TROPPO_CHIARA,
    DUPLICATA,
    /** Velocità angolare del giroscopio oltre soglia vicino allo scatto (§16): foto probabilmente mossa. */
    MOSSA_DAL_SENSORE,
    /** ISO troppo alto (§13 punto 7): troppo rumore digitale per un'identificazione affidabile. */
    RUMOROSA_ISO,
    /** Tempo di esposizione troppo lungo (§13 punto 7): alto rischio di mosso. */
    MOSSA_ESPOSIZIONE,
}

/** Esito della valutazione di una singola foto. */
data class PhotoAssessment(
    val id: String,
    val accettata: Boolean,
    val motivo: PhotoRejectionReason?,
)

/**
 * Esito complessivo dell'ingestione. [tutteScadenti] è vero quando non resta alcuna foto valida:
 * l'app deve avvisare subito l'utente invece di procedere con M1 (§5).
 */
data class IngestionResult(
    val fotoValide: List<String>,
    val scartate: List<PhotoAssessment>,
) {
    val tutteScadenti: Boolean get() = fotoValide.isEmpty()
}
