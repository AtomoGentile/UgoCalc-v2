package com.ugocalc.app.domain.inference.m2

import com.ugocalc.app.domain.inference.model.FoodCategory

/**
 * Assegna una [FoodCategory] a un nome di alimento (§8, §14), con la stessa logica di match a
 * parola intera di [StrategyRouter] — stateless, tabella dati in [FoodCategoryRules].
 */
object FoodCategoryRouter {

    fun categorize(nome: String, rules: List<FoodCategoryRule> = FoodCategoryRules.DEFAULT): FoodCategory {
        val normalizzato = StrategyRouter.normalize(nome)
        val regola = rules.firstOrNull { rule -> rule.keywords.any { matchesWholeWord(normalizzato, it) } }
        return regola?.categoria ?: FoodCategoryRules.FALLBACK
    }

    private fun matchesWholeWord(nomeNormalizzato: String, keyword: String): Boolean {
        val kw = StrategyRouter.normalize(keyword)
        if (kw.isEmpty()) return false
        return Regex("\\b" + Regex.escape(kw) + "\\b").containsMatchIn(nomeNormalizzato)
    }
}
