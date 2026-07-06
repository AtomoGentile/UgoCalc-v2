package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.FoodCategory

/**
 * Tabella locale nome→categoria usata per segmentare il bias per alimento (§8, §14): stesso
 * meccanismo e stesso ordine-conta-il-primo-match di [StrategyRules], ma più granulare dove
 * [StrategyRules.DEFAULT] mescolava alimenti troppo diversi sotto la stessa strategia — in
 * particolare `DISTESA_SUL_PIATTO`, che copriva pasta, riso, patate e verdure con un solo bucket.
 */
object FoodCategoryRules {

    /** Categoria usata quando nessuna regola combacia. */
    val FALLBACK: FoodCategory = FoodCategory.VERDURE_E_LEGUMI

    val DEFAULT: List<FoodCategoryRule> = listOf(
        rule(FoodCategory.PIATTI_COMPOSTI,
            "lasagna", "lasagne", "cannelloni", "parmigiana", "moussaka", "sformato",
            "ripieno", "ripieni", "tortino", "panino", "tramezzino", "hamburger", "toast",
            "piadina", "burrito", "kebab", "sushi", "involtino", "involtini", "arancino", "arancini",
            "ragu", "ragout", "sugo", "bolognese", "pesto", "carbonara"),

        rule(FoodCategory.BEVANDE_E_LIQUIDI,
            "latte", "yogurt", "succo", "spremuta", "frullato", "smoothie", "centrifuga",
            "caffe", "cappuccino", "te", "tisana", "camomilla", "acqua", "vino", "birra", "spritz",
            "minestra", "minestrone", "zuppa", "brodo", "vellutata", "passato", "crema", "gazpacho"),

        rule(FoodCategory.CONTABILE,
            "mela", "mele", "pera", "pere", "banana", "banane", "arancia", "arance", "mandarino",
            "mandarini", "clementina", "kiwi", "pesca", "pesche", "albicocca", "albicocche",
            "prugna", "prugne", "susina", "fico", "fichi", "uovo", "uova", "polpetta", "polpette",
            "biscotto", "biscotti", "cracker", "grissino", "grissini", "fetta biscottata",
            "taralli", "tarallo", "oliva", "olive", "noce", "noci", "mandorla",
            "mandorle", "nocciola", "nocciole", "pomodorino", "pomodorini", "crostino", "crostini",
            "ravioli", "raviolo", "gnocco", "gnocchi", "wurstel", "salsiccia", "salsicce"),

        rule(FoodCategory.BLOCCO_MISURABILE,
            "carne", "bistecca", "fettina", "scaloppina", "cotoletta", "pollo", "petto", "coscia",
            "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto", "pesce", "filetto",
            "salmone", "tonno", "merluzzo", "orata", "branzino", "spigola", "gambero", "gamberi",
            "formaggio", "mozzarella", "parmigiano", "grana", "pecorino", "stracchino", "ricotta",
            "torta", "crostata", "tiramisu", "pane", "pizza", "focaccia", "prosciutto", "salame",
            "mortadella", "bresaola", "speck", "frittata", "omelette", "pancetta"),

        // --- Le quattro categorie che sostituiscono l'unico bucket DISTESA_SUL_PIATTO ---
        rule(FoodCategory.PASTA,
            "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "farfalle", "linguine",
            "tagliatelle", "maccheroni"),

        rule(FoodCategory.RISO_E_CEREALI,
            "riso", "risotto", "orzo", "farro", "couscous", "cous cous", "quinoa", "polenta",
            "cereali", "muesli", "corn flakes", "fiocchi"),

        rule(FoodCategory.PATATE,
            "pure", "patate", "patatine"),

        rule(FoodCategory.VERDURE_E_LEGUMI,
            "insalata", "verdura", "verdure", "contorno", "fagioli", "fagiolini", "piselli", "ceci",
            "lenticchie", "spinaci", "zucchine", "melanzane", "peperoni", "carote", "broccoli",
            "cavolfiore", "finocchi"),

        rule(FoodCategory.TRASCURABILE,
            "olio", "sale", "pepe", "aceto", "zucchero", "burro", "maionese", "ketchup", "senape",
            "salsa", "condimento", "spolverata", "grattugiato", "parmigiano grattugiato"),
    )

    private fun rule(categoria: FoodCategory, vararg keywords: String) =
        FoodCategoryRule(keywords = keywords.toList(), categoria = categoria)
}

/** Una regola della tabella: se il nome contiene una delle [keywords] (a parola intera), usa [categoria]. */
data class FoodCategoryRule(
    val keywords: List<String>,
    val categoria: FoodCategory,
)
