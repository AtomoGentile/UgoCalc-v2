package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.domain.inference.m2.StrategyRouter

/**
 * Tabelle fisiche locali usate da M3 (piano V2 §2, §5): densità (g/cm³), pesi unitari di
 * riferimento (g/pezzo) e capacità dei contenitori (ml).
 */
open class PhysicalData(
    customDensities: List<PhysicalValue> = emptyList()
) {

    private val dynamicDensities = customDensities.map { 
        Entry(it.valore, it.fonte, listOf(it.fonte)) 
    }

    /** Diametro di riferimento di un piatto piano, usato quando manca un'ancora di piatto (§9). */
    companion object {
        const val DEFAULT_PLATE_DIAMETER_CM: Double = 27.0
        val INSTANCE = PhysicalData()
    }

    /** Densità (g/cm³) per gli alimenti a stima geometrica. Fonte primaria: CREA/INRAN. */
    private val densities: List<Entry> = listOf(
        // Cereali e derivati (Fonte: CREA - densità apparente cotto/pronto)
        entry(0.68, "pasta lunga cotta (CREA)", "spaghetti", "linguine", "tagliatelle"),
        entry(0.46, "pasta corta bucata cotta (CREA)", "penne", "rigatoni", "maccheroni", "mezze maniche"),
        entry(0.52, "pasta corta cotta (CREA)", "pasta", "fusilli", "farfalle", "caserecce"),
        entry(0.82, "riso cotto (CREA)", "riso", "risotto"),
        entry(0.88, "cereale cotto (CREA)", "orzo", "farro", "couscous", "cous cous", "quinoa", "polenta"),
        entry(0.38, "cereali secchi (CREA)", "cereali", "muesli", "corn flakes", "fiocchi"),
        entry(0.45, "pane (CREA)", "pane", "panino", "rosetta", "michetta", "baguette"),
        entry(0.58, "pizza/focaccia (CREA)", "pizza", "focaccia"),
        entry(0.48, "torta (CREA)", "torta", "crostata", "tiramisu"),

        // Vegetali
        entry(0.92, "patate (CREA)", "patate", "patatine", "pure"),
        entry(0.85, "legumi (CREA)", "fagioli", "fagiolini", "ceci", "lenticchie", "piselli"),
        entry(0.22, "verdura a foglia (CREA)", "insalata", "spinaci", "verdura", "verdure"),
        entry(0.55, "verdura cotta (CREA)", "zucchine", "melanzane", "peperoni", "carote", "broccoli",
            "cavolfiore", "finocchi"),

        // Proteine e grassi
        entry(1.04, "carne cotta (CREA)", "carne", "bistecca", "fettina", "scaloppina", "cotoletta",
            "pollo", "petto", "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto",
            "prosciutto", "salame", "mortadella", "bresaola", "speck", "pancetta"),
        entry(1.02, "pesce (CREA)", "pesce", "filetto", "salmone", "tonno", "merluzzo", "orata",
            "branzino", "spigola", "gambero", "gamberi"),
        entry(1.06, "formaggio (CREA)", "formaggio", "mozzarella", "parmigiano", "grana", "pecorino",
            "stracchino", "ricotta"),
        entry(0.92, "olio (CREA)", "olio"),

        // Liquidi
        entry(1.00, "brodo/minestra", "minestra", "minestrone", "zuppa", "brodo"),
        entry(1.05, "crema/passato", "vellutata", "passato", "crema", "gazpacho"),
        entry(1.03, "latte/yogurt (CREA)", "latte", "yogurt"),
        entry(1.04, "succo (CREA)", "succo", "spremuta", "frullato", "smoothie", "centrifuga"),
        entry(1.00, "acqua/infusi", "acqua", "te", "tisana", "camomilla", "caffe"),
        entry(0.99, "bevanda alcolica", "vino", "birra", "spritz"),
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

    /**
     * Resa di cottura: peso_cotto / peso_crudo. Fonte: CREA/INRAN, tabelle di conversione.
     * Per alimenti che non cambiano peso con la cottura (frutta fresca, pane, formaggi, latticini,
     * uova, condimenti), resa = 1.0 (default).
     */
    private val cookingYields: List<Entry> = listOf(
        // Cereali (cottura per assorbimento acqua: resa > 1)
        entry(2.2, "pasta lunga secca → cotta (CREA)", "spaghetti", "linguine", "tagliatelle", "fettuccine", "bucatini", "vermicelli"),
        entry(2.1, "pasta corta secca → cotta (CREA)", "penne", "rigatoni", "maccheroni", "fusilli", "farfalle", "caserecce", "mezze maniche", "pasta"),
        entry(2.7, "riso secco → cotto (CREA)", "riso", "risotto"),
        entry(2.5, "orzo/farro/quinoa secchi → cotti (CREA)", "orzo", "farro", "quinoa", "couscous", "cous cous", "grano saraceno"),
        entry(2.2, "legumi secchi → cotti (CREA)", "fagioli", "ceci", "lenticchie", "piselli secchi"),
        // Proteine (perdita acqua in cottura: resa < 1)
        entry(0.75, "carne cruda → cotta (CREA)", "carne", "bistecca", "fettina", "scaloppina", "cotoletta",
            "pollo", "petto", "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto"),
        entry(0.78, "pesce crudo → cotto (CREA)", "pesce", "filetto", "salmone", "tonno", "merluzzo", "orata",
            "branzino", "spigola"),
        entry(0.85, "verdura cruda → cotta (CREA)", "zucchine", "melanzane", "peperoni", "carote", "broccoli",
            "spinaci", "cavolfiore", "finocchi"),
    )

    private val defaultDensity = PhysicalValue(0.90, "densità generica", isDefault = true)
    private val defaultUnitWeight = PhysicalValue(50.0, "peso unitario generico", isDefault = true)
    private val defaultCapacity = PhysicalValue(250.0, "capacità generica", isDefault = true)
    // resa = 1.0: frutta fresca, pane, formaggi, uova, condimenti, ecc. non cambiano peso rilevante
    private val defaultYield = PhysicalValue(1.0, "resa unitaria (nessuna cottura)", isDefault = true)

    fun density(nome: String): PhysicalValue = resolve(nome, densities, defaultDensity)
    fun unitWeight(nome: String): PhysicalValue = resolve(nome, unitWeights, defaultUnitWeight)
    fun capacity(contenitore: String): PhysicalValue = resolve(contenitore, capacities, defaultCapacity)
    fun cookingYield(nome: String): PhysicalValue = resolve(nome, cookingYields, defaultYield)

    private fun resolve(nome: String, table: List<Entry>, default: PhysicalValue): PhysicalValue {
        val normalizzato = StrategyRouter.normalize(nome)
        
        // Priorità 1: Densità dinamiche (inserite dall'utente per questo specifico alimento)
        if (table === densities) {
            val dynamicHit = dynamicDensities.firstOrNull { e -> e.keywords.any { matchesWholeWord(normalizzato, it) } }
            if (dynamicHit != null) return PhysicalValue(dynamicHit.value, dynamicHit.label, isDefault = false)
        }

        // Priorità 2: Tabelle statiche (CREA/INRAN)
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
