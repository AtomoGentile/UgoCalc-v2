package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.EstimationStrategy

/**
 * Tabella locale nome→strategia usata da M2 (piano V2 §5). È **dati, non logica**: è il punto in cui
 * si estende il sistema con nuovi alimenti o si correggono errori sistematici visti nel benchmark
 * (§9), senza toccare il codice del router né i prompt.
 *
 * L'ordine conta: [StrategyRouter] usa la prima regola che combacia, quindi i piatti composti e le
 * voci più specifiche vanno prima delle parole più generiche che potrebbero catturarle.
 */
object StrategyRules {

    /**
     * Strategia usata quando nessuna regola combacia. Distesa sul piatto è la forma più comune di
     * un componente di pasto non riconosciuto; l'assegnazione viene marcata come non esplicita
     * ([StrategyAssignment.abbinamentoEsplicito] = false) così da abbassare la confidenza a valle.
     */
    val FALLBACK: EstimationStrategy = EstimationStrategy.DISTESA_SUL_PIATTO

    val DEFAULT: List<StrategyRule> = listOf(
        // --- Composti / occlusi (specifici: prima di tutto per non farli catturare da altre regole) ---
        rule(EstimationStrategy.COMPOSTO_OCCLUSO,
            "lasagna", "lasagne", "cannelloni", "parmigiana", "moussaka", "sformato",
            "ripieno", "ripieni", "tortino", "panino", "tramezzino", "hamburger", "toast",
            "piadina", "burrito", "kebab", "sushi", "involtino", "involtini", "arancino", "arancini",
            "ragu", "ragout", "sugo", "bolognese", "pesto", "carbonara"),

        // --- Distesa sul piatto (superficie × altezza × riempimento × densità) ---
        // Posta in alto perché molte parole di questa categoria (es. pasta, riso) sono i "vettori"
        // principali del pasto e devono avere la precedenza su condimenti o ingredienti secondari
        // che potrebbero attivare altre strategie (es. "pasta al tonno" -> deve essere DISTESA,
        // non BLOCCO per via di "tonno").
        rule(EstimationStrategy.DISTESA_SUL_PIATTO,
            "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "farfalle", "linguine",
            "tagliatelle", "maccheroni", "riso", "risotto", "orzo", "farro", "couscous", "cous cous",
            "quinoa", "polenta", "pure", "patate", "patatine", "insalata", "verdura", "verdure",
            "contorno", "fagioli", "fagiolini", "piselli", "ceci", "lenticchie", "spinaci",
            "zucchine", "melanzane", "peperoni", "carote", "broccoli", "cavolfiore", "finocchi",
            "cereali", "muesli", "corn flakes", "fiocchi"),

        // --- A contenitore (liquidi / semiliquidi in bicchiere, tazza, ciotola, scodella) ---
        rule(EstimationStrategy.A_CONTENITORE,
            "latte", "yogurt", "succo", "spremuta", "frullato", "smoothie", "centrifuga",
            "caffe", "cappuccino", "te", "tisana", "camomilla", "acqua", "vino", "birra", "spritz",
            "minestra", "minestrone", "zuppa", "brodo", "vellutata", "passato", "crema", "gazpacho"),

        // --- Contabile (pezzi numerabili di peso unitario tipico) ---
        rule(EstimationStrategy.CONTABILE,
            "mela", "mele", "pera", "pere", "banana", "banane", "arancia", "arance", "mandarino",
            "mandarini", "clementina", "kiwi", "pesca", "pesche", "albicocca", "albicocche",
            "prugna", "prugne", "susina", "fico", "fichi", "uovo", "uova", "polpetta", "polpette",
            "biscotto", "biscotti", "cracker", "grissino", "grissini", "fetta biscottata",
            "cracker", "taralli", "tarallo", "oliva", "olive", "noce", "noci", "mandorla",
            "mandorle", "nocciola", "nocciole", "pomodorino", "pomodorini", "crostino", "crostini",
            "ravioli", "raviolo", "gnocco", "gnocchi", "wurstel", "salsiccia", "salsicce"),

        // --- Blocco misurabile (forma geometrica × densità) ---
        rule(EstimationStrategy.BLOCCO_MISURABILE,
            "carne", "bistecca", "fettina", "scaloppina", "cotoletta", "pollo", "petto", "coscia",
            "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto", "pesce", "filetto",
            "salmone", "tonno", "merluzzo", "orata", "branzino", "spigola", "gambero", "gamberi",
            "formaggio", "mozzarella", "parmigiano", "grana", "pecorino", "stracchino", "ricotta",
            "torta", "crostata", "tiramisu", "pane", "pizza", "focaccia", "prosciutto", "salame",
            "mortadella", "bresaola", "speck", "frittata", "omelette", "pancetta"),

        // --- Trascurabile (valore fisso, dichiarato incerto) ---
        rule(EstimationStrategy.TRASCURABILE,
            "olio", "sale", "pepe", "aceto", "zucchero", "burro", "maionese", "ketchup", "senape",
            "salsa", "condimento", "spolverata", "grattugiato", "parmigiano grattugiato"),
    )

    private fun rule(strategia: EstimationStrategy, vararg keywords: String) =
        StrategyRule(keywords = keywords.toList(), strategia = strategia)
}
