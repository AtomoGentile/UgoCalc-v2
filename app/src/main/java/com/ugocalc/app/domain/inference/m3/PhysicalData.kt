package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.m2.StrategyRouter

/**
 * Tabelle fisiche locali usate da M3 (piano V2 §2, §5): densità (g/cm³), pesi unitari di
 * riferimento (g/pezzo) e capacità dei contenitori (ml). Sono **dati, non logica**: una tabella
 * imprecisa produce un errore sistematico visibile nel benchmark, correggibile qui senza toccare il
 * codice del motore (§9).
 *
 * Ogni lookup restituisce anche la propria [PhysicalValue.fonte] (per la catena di calcolo) e un
 * flag [PhysicalValue.isDefault] che segnala quando si è ripiegato sul valore generico: a valle
 * questo giustifica un intervallo di confidenza più ampio.
 */
object PhysicalData {

    /** Diametro di riferimento di un piatto piano, usato quando manca un'ancora di piatto (§9). */
    const val DEFAULT_PLATE_DIAMETER_CM: Double = 27.0

    /** Densità (g/cm³) per gli alimenti a stima geometrica. */
    private val densities: List<Entry> = listOf(
        entry(0.65, "pasta cotta", "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "farfalle",
            "linguine", "tagliatelle", "maccheroni"),
        entry(0.85, "riso cotto", "riso", "risotto"),
        entry(0.90, "cereale cotto", "orzo", "farro", "couscous", "cous cous", "quinoa", "polenta"),
        entry(0.40, "cereali secchi", "cereali", "muesli", "corn flakes", "fiocchi"),
        entry(0.95, "patate", "patate", "patatine", "pure"),
        entry(0.90, "legumi", "fagioli", "fagiolini", "ceci", "lenticchie", "piselli"),
        entry(0.25, "verdura a foglia", "insalata", "spinaci", "verdura", "verdure"),
        entry(0.60, "verdura cotta", "zucchine", "melanzane", "peperoni", "carote", "broccoli",
            "cavolfiore", "finocchi"),
        entry(1.05, "carne cotta", "carne", "bistecca", "fettina", "scaloppina", "cotoletta",
            "pollo", "petto", "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto",
            "prosciutto", "salame", "mortadella", "bresaola", "speck", "pancetta"),
        entry(1.00, "pesce", "pesce", "filetto", "salmone", "tonno", "merluzzo", "orata",
            "branzino", "spigola", "gambero", "gamberi"),
        entry(1.05, "formaggio", "formaggio", "mozzarella", "parmigiano", "grana", "pecorino",
            "stracchino", "ricotta"),
        entry(0.28, "pane", "pane"),
        entry(0.55, "impasto da forno", "pizza", "focaccia"),
        entry(0.45, "torta", "torta", "crostata", "tiramisu"),
        entry(1.00, "brodo/minestra", "minestra", "minestrone", "zuppa", "brodo"),
        entry(1.05, "crema/passato", "vellutata", "passato", "crema", "gazpacho"),
        entry(1.03, "latticino liquido", "latte", "yogurt"),
        entry(1.04, "succo", "succo", "spremuta", "frullato", "smoothie", "centrifuga"),
        entry(1.00, "bevanda acquosa", "acqua", "te", "tisana", "camomilla", "caffe"),
        entry(0.99, "bevanda alcolica", "vino", "birra", "spritz"),
        entry(0.92, "olio", "olio"),
    )

    /** Peso unitario di riferimento (g/pezzo) per gli alimenti contabili. */
    private val unitWeights: List<Entry> = listOf(
        entry(150.0, "mela media", "mela", "mele"),
        entry(160.0, "pera media", "pera", "pere"),
        entry(120.0, "banana media", "banana", "banane"),
        entry(200.0, "arancia media", "arancia", "arance"),
        entry(80.0, "mandarino", "mandarino", "mandarini", "clementina"),
        entry(90.0, "kiwi", "kiwi"),
        entry(150.0, "pesca media", "pesca", "pesche"),
        entry(45.0, "albicocca", "albicocca", "albicocche"),
        entry(50.0, "prugna/susina", "prugna", "prugne", "susina", "fico", "fichi"),
        entry(55.0, "uovo medio", "uovo", "uova"),
        entry(35.0, "polpetta", "polpetta", "polpette"),
        entry(8.0, "biscotto", "biscotto", "biscotti", "fetta biscottata"),
        entry(5.0, "cracker", "cracker"),
        entry(6.0, "grissino", "grissino", "grissini", "tarallo", "taralli"),
        entry(4.0, "oliva", "oliva", "olive"),
        entry(6.0, "noce", "noce", "noci"),
        entry(1.2, "mandorla", "mandorla", "mandorle"),
        entry(1.0, "nocciola", "nocciola", "nocciole"),
        entry(15.0, "pomodorino", "pomodorino", "pomodorini"),
        entry(3.0, "crostino", "crostino", "crostini"),
        entry(6.0, "raviolo/gnocco", "raviolo", "ravioli", "gnocco", "gnocchi"),
        entry(40.0, "wurstel", "wurstel"),
        entry(90.0, "salsiccia", "salsiccia", "salsicce"),
    )

    /** Capacità di riferimento (ml) dei contenitori riconoscibili. */
    private val capacities: List<Entry> = listOf(
        entry(60.0, "tazzina", "tazzina"),
        entry(150.0, "calice", "calice"),
        entry(200.0, "bicchiere", "bicchiere"),
        entry(250.0, "tazza", "tazza"),
        entry(300.0, "mug", "mug"),
        entry(350.0, "ciotola", "ciotola"),
        entry(500.0, "scodella", "scodella", "boccale"),
    )

    private val defaultDensity = PhysicalValue(0.90, "densità generica", isDefault = true)
    private val defaultUnitWeight = PhysicalValue(50.0, "peso unitario generico", isDefault = true)
    private val defaultCapacity = PhysicalValue(250.0, "capacità generica", isDefault = true)

    fun density(nome: String): PhysicalValue = resolve(nome, densities, defaultDensity)
    fun unitWeight(nome: String): PhysicalValue = resolve(nome, unitWeights, defaultUnitWeight)
    fun capacity(contenitore: String): PhysicalValue = resolve(contenitore, capacities, defaultCapacity)

    private fun resolve(nome: String, table: List<Entry>, default: PhysicalValue): PhysicalValue {
        val normalizzato = StrategyRouter.normalize(nome)
        val hit = table.firstOrNull { e -> e.keywords.any { matchesWholeWord(normalizzato, it) } }
        return hit?.let { PhysicalValue(it.value, it.label, isDefault = false) } ?: default
    }

    private fun matchesWholeWord(nomeNormalizzato: String, keyword: String): Boolean {
        val kw = StrategyRouter.normalize(keyword)
        if (kw.isEmpty()) return false
        return Regex("\\b" + Regex.escape(kw) + "\\b").containsMatchIn(nomeNormalizzato)
    }

    private fun entry(value: Double, label: String, vararg keywords: String) =
        Entry(value = value, label = label, keywords = keywords.toList())

    private data class Entry(val value: Double, val label: String, val keywords: List<String>)
}

/** Un valore fisico risolto da una tabella, con la sua provenienza per la catena di calcolo. */
data class PhysicalValue(
    val valore: Double,
    val fonte: String,
    val isDefault: Boolean,
)
