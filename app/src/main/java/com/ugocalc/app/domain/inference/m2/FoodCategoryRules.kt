package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.FoodCategory

/** Una regola della tabella: se il nome contiene una delle [keywords] (a parola intera), usa [categoria]. */
data class FoodCategoryRule(
    val keywords: List<String>,
    val categoria: FoodCategory,
)

/** Tabella locale nome→categoria usata per segmentare il bias (§8, §14). */
object FoodCategoryRules {

    /** Categoria usata quando nessuna regola combacia. */
    val FALLBACK: FoodCategory = FoodCategory.PIATTI_COMPOSTI

    val DEFAULT: List<FoodCategoryRule> = listOf(
        rule(FoodCategory.PASTA, "pasta", "spaghetti", "penne", "rigatoni", "fusilli", "farfalle", "linguine", "tagliatelle", "maccheroni"),
        rule(FoodCategory.RISO_E_CEREALI, "riso", "risotto", "orzo", "farro", "couscous", "cous cous", "quinoa"),
        rule(FoodCategory.PATATE, "patate", "patatine", "pure"),
        rule(FoodCategory.VERDURE_E_LEGUMI, "verdura", "verdure", "insalata", "fagioli", "fagiolini", "piselli", "ceci", "lenticchie", "spinaci", "zucchine", "melanzane", "peperoni", "carote", "broccoli", "cavolfiore", "finocchi"),
        rule(FoodCategory.BEVANDE_E_LIQUIDI, "latte", "yogurt", "succo", "spremuta", "frullato", "smoothie", "centrifuga", "caffe", "cappuccino", "te", "tisana", "camomilla", "acqua", "vino", "birra", "spritz"),
        rule(FoodCategory.TRASCURABILE, "olio", "sale", "pepe", "aceto", "zucchero", "burro", "maionese", "ketchup", "senape", "salsa", "condimento", "spolverata", "grattugiato"),
        rule(FoodCategory.CONTABILE, "mela", "mele", "pera", "pere", "banana", "banane", "arancia", "arance", "mandarino", "uovo", "uova", "polpetta", "polpette", "biscotto", "biscotti", "cracker", "fetta biscottata"),
        rule(FoodCategory.BLOCCO_MISURABILE, "carne", "pollo", "pesce", "formaggio", "torta", "pane", "pizza", "prosciutto", "salame")
    )

    private fun rule(categoria: FoodCategory, vararg keywords: String) =
        FoodCategoryRule(keywords = keywords.toList(), categoria = categoria)
}
