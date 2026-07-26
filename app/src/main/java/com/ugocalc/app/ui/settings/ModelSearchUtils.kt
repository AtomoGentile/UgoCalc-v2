package com.ugocalc.app.ui.settings

import com.ugocalc.app.domain.model.AiModel

fun modelMatchesQuery(model: AiModel, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    val haystack = "${model.displayName} ${model.subtitle} ${model.id}".lowercase()
    if (haystack.contains(q)) return true
    val tokens = q.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    if (tokens.all { token -> haystack.contains(token) }) return true
    val haystackWords = haystack.split("[\\s\\-_.:]+".toRegex()).filter { it.isNotEmpty() }
    return tokens.all { token ->
        haystackWords.any { word -> fuzzyTokenMatch(token, word) }
    }
}

private fun fuzzyTokenMatch(a: String, b: String): Boolean {
    if (a.length <= 2) return b.startsWith(a)
    val maxDist = if (a.length <= 4) 1 else 2
    return levenshtein(a, b) <= maxDist
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
    }
    return dp[a.length][b.length]
}
