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
    val FALLBACK: FoodCategory = FoodCategory.ALTRO_BLOCCO

    val DEFAULT: List<FoodCategoryRule> = listOf(
        rule(FoodCategory.PIATTI_COMPOSTI,
            "lasagna", "lasagne", "cannelloni", "parmigiana", "moussaka", "sformato",
            "ripieno", "ripieni", "tortino", "panino", "tramezzino", "hamburger", "toast",
            "piadina", "burrito", "kebab", "sushi", "involtino", "involtini", "arancino", "arancini",
            "ragu", "ragout", "sugo", "bolognese", "pesto", "carbonara"),

        rule(FoodCategory.BEVANDE,
            "latte", "yogurt", "succo", "spremuta", "frullato", "smoothie", "centrifuga",
            "caffe", "cappuccino", "te", "tisana", "camomilla", "acqua", "vino", "birra", "spritz"),

        rule(FoodCategory.ZUPPE_MINESTRE,
            "minestra", "minestrone", "zuppa", "brodo", "vellutata", "passato", "crema", "gazpacho"),

        rule(FoodCategory.CONTABILE,
            "uovo", "uova", "polpetta", "polpette",
            "biscotto", "biscotti", "cracker", "grissino", "grissini", "fetta biscottata",
            "taralli", "tarallo", "oliva", "olive", "noce", "noci", "mandorla",
            "mandorle", "nocciola", "nocciole", "pomodorino", "pomodorini", "crostino", "crostini",
            "ravioli", "raviolo", "gnocco", "gnocchi", "wurstel", "salsiccia", "salsicce"),

        rule(FoodCategory.FRUTTA,
            "mela", "mele", "pera", "pere", "banana", "banane", "arancia", "arance", "mandarino",
            "mandarini", "clementina", "kiwi", "pesca", "pesche", "albicocca", "albicocche",
            "prugna", "prugne", "susina", "fico", "fichi", "uva", "ciliegie", "fragole", "frutta"),

        rule(FoodCategory.CARNE,
            "carne", "bistecca", "fettina", "scaloppina", "cotoletta", "pollo", "petto", "coscia",
            "tacchino", "maiale", "manzo", "vitello", "agnello", "arrosto", "prosciutto", "salame",
            "mortadella", "bresaola", "speck", "pancetta"),

        rule(FoodCategory.PESCE_FILETTO,
            "pesce", "filetto", "salmone", "tonno", "merluzzo", "orata", "branzino", "spigola"),

        rule(FoodCategory.MOLLUSCHI_GUSCIO,
            "vongole", "cozze", "molluschi", "crostacei", "gambero", "gamberi"),

        rule(FoodCategory.FORMAGGIO,
            "formaggio", "mozzarella", "parmigiano", "grana", "pecorino", "stracchino", "ricotta"),

        rule(FoodCategory.PANE,
            "pane", "panino", "rosetta", "michetta", "baguette"),

        rule(FoodCategory.PIZZA_FOCACCIA,
            "pizza", "focaccia", "impasto da forno"),

        rule(FoodCategory.DOLCI,
            "torta", "crostata", "tiramisu", "frittata", "omelette"),

        rule(FoodCategory.PASTA,
            "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "farfalle", "linguine",
            "tagliatelle", "maccheroni"),

        rule(FoodCategory.RISO_E_CEREALI,
            "riso", "risotto", "orzo", "farro", "couscous", "cous cous", "quinoa", "polenta",
            "cereali", "muesli", "corn flakes", "fiocchi"),

        rule(FoodCategory.PATATE,
            "pure", "patate", "patatine"),

        rule(FoodCategory.VERDURE_FOGLIA,
            "insalata", "spinaci", "bietole", "coste"),

        rule(FoodCategory.VERDURE_COMPATTE,
            "verdura", "verdure", "contorno", "zucchine", "melanzane", "peperoni", "carote",
            "broccoli", "cavolfiore", "finocchi", "zucca"),

        rule(FoodCategory.LEGUMI,
            "fagioli", "fagiolini", "piselli", "ceci", "lenticchie"),

        rule(FoodCategory.TRASCURABILE,
            "olio", "sale", "pepe", "peperoncino", "prezzemolo", "aglio", "spezie", "aceto",
            "zucchero", "burro", "maionese", "ketchup", "senape", "salsa", "condimento",
            "spolverata", "grattugiato", "parmigiano grattugiato"),
    )

    private fun rule(categoria: FoodCategory, vararg keywords: String) =
        FoodCategoryRule(keywords = keywords.toList(), categoria = categoria)
}

/** Una regola della tabella: se il nome contiene una delle [keywords] (a parola intera), usa [categoria]. */
data class FoodCategoryRule(
    val keywords: List<String>,
    val categoria: FoodCategory,
)
