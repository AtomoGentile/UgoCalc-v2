package com.ugocalc.app.domain.glycemic

import com.ugocalc.app.domain.model.NormalizedFoodEntry
import java.text.Normalizer

/**
 * Risolve l'Indice Glicemico (IG, scala glucosio = 100) per una voce alimentare:
 * 1. Se [NormalizedFoodEntry.indiceGlicemico] è valorizzato, lo utilizza direttamente (`isEstimated = false`).
 * 2. Se l'alimento ha meno di 1g di carboidrati per 100g, restituisce IG = 0 (`isEstimated = false`).
 * 3. Altrimenti mappa il nome (italiano/inglese) alle fasce cliniche Atkinson et al. (2021)
 *    con `isEstimated = true`, ricadendo su 55 per alimenti non riconosciuti.
 */
object GlycemicIndexResolver {

    private data class GiRule(val keywords: List<String>, val gi: Int)

    /**
     * Regole ordinate dalla più specifica alla più generica per evitare collisioni
     * (es. "riso integrale" / "riso basmati" prima di "riso"; "patata dolce" prima di "patate").
     */
    private val RULES: List<GiRule> = listOf(
        // Zuccheri puri, bevande zuccherate, cereali estrusi / riso soffiato / gallette (72–95)
        GiRule(listOf("glucosio", "destrosio", "maltodestr"), 95),
        GiRule(listOf("gallette", "riso soffiato", "rice cake", "puffed rice"), 82),
        GiRule(listOf("corn flakes", "cornflakes", "fiocchi di mais"), 81),
        GiRule(listOf("latte di riso", "bevanda di riso", "rice milk"), 86),
        GiRule(listOf("patate al forno", "patate arrosto", "baked potato", "pure di patate", "purè di patate", "mashed potato"), 85),
        GiRule(listOf("baguette", "pane in cassetta", "pancarre", "pan carre", "toast bianco"), 75),
        GiRule(listOf("anguria", "cocomero", "watermelon"), 76),
        GiRule(listOf("zucca", "pumpkin"), 75),
        GiRule(listOf("patatine", "french fries", "chips", "patate fritte"), 75),
        GiRule(listOf("patata dolce", "patate dolci", "batata", "sweet potato"), 46),
        GiRule(listOf("patate", "patata", "potato"), 78),
        GiRule(listOf("riso integrale", "riso nero", "riso venere", "riso rosso", "brown rice"), 50),
        GiRule(listOf("riso basmati", "basmati", "riso parboiled", "parboiled"), 52),
        GiRule(listOf("risotto"), 69),
        GiRule(listOf("riso", "white rice", "rice"), 73),
        GiRule(listOf("zucchero", "saccarosio", "sciroppo", "caramell", "caramella", "miele", "honey", "sugar", "candy"), 74),
        GiRule(listOf("bibita", "cola", "aranciata", "gassosa", "energy drink", "soda", "succo di frutta", "succo", "fruit juice"), 66),

        // Pane bianco, pizza, gnocchi, prodotti da forno raffinati (58–72)
        GiRule(listOf("pane integrale", "pane di segale", "pane nero", "pane ai cereali", "wholemeal bread", "whole wheat bread", "rye bread", "sourdough"), 51),
        GiRule(listOf("fette biscottate integrali"), 58),
        GiRule(listOf("fette biscottate", "rusk"), 70),
        GiRule(listOf("cracker", "grissini", "taralli", "focaccia", "piadina", "schiacciata"), 68),
        GiRule(listOf("pizza", "calzone"), 65),
        GiRule(listOf("gnocchi"), 68),
        GiRule(listOf("polenta", "farina di mais"), 68),
        GiRule(listOf("cous cous", "couscous", "cuscus"), 65),
        GiRule(listOf("cornetto", "croissant", "brioche", "panettone", "pandoro", "colomba", "bombolone", "krapfen", "donut", "muffin", "merendina"), 67),
        GiRule(listOf("biscotti integrali", "frollini integrali"), 55),
        GiRule(listOf("biscott", "frollin", "wafer", "biscuit", "cookie", "torta", "crostata", "tiramisu", "dolce", "cake", "pastry", "gelato", "sorbetto"), 62),
        GiRule(listOf("pane", "panino", "rosetta", "ciabatta", "michetta", "bread", "bun", "tramezzino"), 70),
        GiRule(listOf("farina", "flour", "semola"), 70),
        GiRule(listOf("melone", "ananas", "pineapple", "melon", "uvetta", "datteri", "fichi secchi"), 62),

        // Pasta, avena, cereali in chicco, frutta a medio IG (42–56)
        GiRule(listOf("pasta integrale", "spaghetti integrali", "penne integrali", "whole wheat pasta", "wholemeal pasta"), 42),
        GiRule(listOf("pasta", "spaghetti", "penne", "fusilli", "rigatoni", "maccheroni", "tagliatelle", "lasagne", "tortellini", "ravioli", "orecchiette", "trofie", "farfalle", "noodles"), 49),
        GiRule(listOf("avena", "porridge", "oats", "oatmeal", "muesli", "granola"), 52),
        GiRule(listOf("farro", "orzo", "quinoa", "grano saraceno", "miglio", "bulgur", "barley", "spelt", "buckwheat"), 48),
        GiRule(listOf("banana"), 51),
        GiRule(listOf("uva", "mango", "kiwi", "albicocch", "cachi", "fichi", "papaia", "papaya", "grapes"), 48),
        GiRule(listOf("arancia", "arance", "mandarin", "clementin", "pompelmo", "pesca", "pesche", "nettarin", "orange", "peach"), 43),
        GiRule(listOf("marmellata", "confettura", "nutella", "crema spalmabile", "jam"), 52),

        // Cioccolato fondente vs al latte
        GiRule(listOf("cioccolato fondente", "dark chocolate", "cacao amaro"), 25),
        GiRule(listOf("cioccolato", "chocolate", "cacao"), 45),

        // Legumi, frutta a basso IG, latticini (22–38)
        GiRule(listOf("lenticchi", "ceci", "fagiol", "pisell", "fave", "lupini", "soia", "edamame", "hummus", "legum", "lentil", "chickpea", "bean", "pea", "tofu"), 30),
        GiRule(listOf("mela", "mele", "pera", "pere", "apple", "pear"), 36),
        GiRule(listOf("fragol", "mirtill", "lampon", "more", "ribes", "ciliegi", "prugne", "susin", "frutti di bosco", "berries", "strawberry", "blueberry", "cherry", "plum"), 30),
        GiRule(listOf("yogurt", "kefir", "skyr"), 33),
        GiRule(listOf("latte", "milk", "cappuccino", "ricotta"), 32),

        // Verdure non amidacee, frutta secca, semi, avocado (15–20)
        GiRule(
            listOf(
                "insalata", "lattuga", "rucola", "spinaci", "bietol", "cicoria", "radicchio", "valeriana",
                "zucchin", "broccol", "cavolfior", "cavolo", "verza", "cavoletti", "pomodor", "cetriol",
                "peperon", "melanzan", "finocch", "carciof", "asparag", "fungh", "sedano", "cipoll",
                "aglio", "porr", "ravanell", "fagiolin", "verdur", "ortagg", "minestrone", "passato di verdura",
                "salad", "lettuce", "spinach", "broccoli", "tomato", "cucumber", "pepper", "eggplant",
                "mushroom", "vegetable", "zucchini",
            ),
            15,
        ),
        GiRule(listOf("carot", "carrot", "barbabietol"), 35),
        GiRule(
            listOf(
                "avocado", "noci", "noce", "mandorl", "nocciol", "pistacch", "arachid", "anacard",
                "pinoli", "semi", "frutta secca", "burro di arachidi", "peanut", "almond", "walnut",
                "hazelnut", "cashew", "nuts", "olive",
            ),
            18,
        ),
    )

    private const val DEFAULT_FALLBACK_GI = 55

    /** Risolve l'IG per un [NormalizedFoodEntry] della tabella nutrizionale. */
    fun resolve(entry: NormalizedFoodEntry): ResolvedGi {
        entry.indiceGlicemico?.let { explicitGi ->
            return ResolvedGi(gi = explicitGi.coerceIn(0, 100), isEstimated = false)
        }
        if (entry.choPer100g < 1.0) {
            return ResolvedGi(gi = 0, isEstimated = false)
        }
        return resolveByName(entry.nome, entry.choPer100g)
    }

    /** Risolve l'IG stimato tramite nome alimento e contenuto CHO/100g. */
    fun resolveByName(foodName: String, choPer100g: Double = 50.0): ResolvedGi {
        if (choPer100g < 1.0) {
            return ResolvedGi(gi = 0, isEstimated = false)
        }
        val normalized = normalizeName(foodName)
        for (rule in RULES) {
            if (rule.keywords.any { normalized.contains(it) }) {
                return ResolvedGi(gi = rule.gi, isEstimated = true)
            }
        }
        return ResolvedGi(gi = DEFAULT_FALLBACK_GI, isEstimated = true)
    }

    private fun normalizeName(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
