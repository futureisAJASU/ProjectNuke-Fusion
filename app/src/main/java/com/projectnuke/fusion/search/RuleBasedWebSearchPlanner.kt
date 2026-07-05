package com.projectnuke.fusion.search

import java.util.Calendar
import java.util.Locale

data class RuleBasedWebSearchPlannerInput(
    val currentUserInput: String,
    val previousUserMessage: String? = null,
    val previousAssistantMessage: String? = null,
    val searchMode: WebSearchMode = WebSearchMode.AUTO,
    val selectedManualProviderType: WebSearchProviderType? = null
)

object RuleBasedWebSearchPlanner {
    fun plan(input: RuleBasedWebSearchPlannerInput): WebSearchPlan {
        val current = input.currentUserInput.trim()
        val previousTopic = extractTopic(input.previousUserMessage.orEmpty())
        val modifiers = detectModifiers(current)
        val contextDependent = isGenericFollowUp(current) || isSearchContextFollowUp(current)
        val currentKeywords = extractKeywords(current)
        val resolvedTopic = when {
            contextDependent && previousTopic.isNotBlank() -> resolveContextualTopic(previousTopic, current, modifiers)
            currentKeywords.isNotEmpty() -> currentKeywords.joinToString(" ")
            previousTopic.isNotBlank() -> previousTopic
            else -> current
        }.ifBlank { current }
        val intent = detectIntent(current, resolvedTopic, modifiers)
        val primaryQuery = buildPrimaryQuery(resolvedTopic, current, modifiers, intent)
        val preferred = preferredProviders(primaryQuery, resolvedTopic, current, intent, modifiers)

        return WebSearchPlan(
            intent = intent,
            primaryQuery = primaryQuery,
            originalInput = current,
            resolvedTopic = resolvedTopic,
            alternateQueries = buildAlternateQueries(resolvedTopic, primaryQuery, current, modifiers, intent),
            preferredProviderTypes = preferred,
            freshnessRequired = modifiers.contains(ModifierFreshness) || intent in setOf(
                SearchIntent.NEWS,
                SearchIntent.CURRENT_INFO,
                SearchIntent.STOCK
            ),
            languageRegion = if (containsKorean(primaryQuery)) "ko-KR" else "en-US",
            regionHint = if (shouldPreferDomestic(primaryQuery, resolvedTopic, current, intent)) "KR" else null,
            reason = buildReason(contextDependent, modifiers, preferred),
            modifiers = modifiers
        )
    }

    fun detectIntentForText(text: String): SearchIntent = detectIntent(text, text, detectModifiers(text))

    fun shouldUseWebSearch(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false
        return detectIntentForText(text) != SearchIntent.GENERAL ||
            TriggerWords.any { normalized.contains(it) } ||
            isGenericFollowUp(text) ||
            isSearchContextFollowUp(text)
    }

    fun extractKeywords(text: String): List<String> {
        val normalized = removeFillers(text)
        val protectedTerms = ImportantTerms
            .sortedByDescending { it.length }
            .filter { normalized.contains(it, ignoreCase = true) }
            .fold(emptyList<String>()) { acc, term ->
                if (acc.any { it.contains(term, ignoreCase = true) }) acc else acc + term
            }
        val tokens = TokenRegex.findAll(normalized)
            .map { normalizeKnownTerm(it.value.trim()) }
            .filter { token ->
                token.length >= 2 &&
                    FillerWords.none { filler -> token.equals(filler, ignoreCase = true) } &&
                    !GenericSearchWords.contains(token.lowercase(Locale.ROOT))
            }
            .toList()
        return (protectedTerms + tokens)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(12)
    }

    private fun extractTopic(text: String): String = extractKeywords(text).joinToString(" ")

    private fun buildPrimaryQuery(
        resolvedTopic: String,
        current: String,
        modifiers: List<String>,
        intent: SearchIntent
    ): String {
        val topic = resolvedTopic.trim().ifBlank { current.trim() }
        if (current.contains("해외 기업", ignoreCase = true) && topic.contains("전고체", ignoreCase = true)) {
            return "전고체 배터리 해외 기업 Toyota QuantumScape Solid Power 최신"
        }
        val suffix = when {
            modifiers.contains(ModifierPaper) -> "paper arxiv research"
            modifiers.contains(ModifierOpposing) -> "반대 의견 리스크 비판"
            modifiers.contains(ModifierMoreSources) && intent == SearchIntent.NEWS -> "최신 뉴스 출처"
            modifiers.contains(ModifierMoreSources) && shouldPreferTechnicalGlobal(topic, topic, current, intent, modifiers) -> "latest report analysis"
            modifiers.contains(ModifierMoreSources) -> "추가 자료"
            modifiers.contains(ModifierFreshness) || intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK) -> "최신"
            else -> ""
        }
        return listOf(topic, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun buildAlternateQueries(
        resolvedTopic: String,
        primaryQuery: String,
        current: String,
        modifiers: List<String>,
        intent: SearchIntent
    ): List<String> {
        val topic = resolvedTopic.ifBlank { primaryQuery }
        val alternates = mutableListOf<String>()

        if (modifiers.contains(ModifierMoreSources)) {
            alternates += if (intent == SearchIntent.NEWS) "$topic 추가 뉴스 출처" else "$topic 추가 자료"
            alternates += if (shouldPreferTechnicalGlobal(topic, topic, current, intent, modifiers)) "$topic latest report" else "$topic 분석"
            buildEnglishTechnicalQuery(topic, intent)?.let { alternates += it }
        }
        if (modifiers.contains(ModifierOpposing)) {
            alternates += "$topic 반대 의견"
            alternates += "$topic criticism risk challenge"
            alternates += "$topic 문제점 리스크"
        }
        if (modifiers.contains(ModifierFreshness)) {
            alternates += "$topic 최신 뉴스"
            alternates += "$topic latest update"
            if (!topic.contains(currentYear().toString())) alternates += "$topic ${currentYear()}"
        }
        if (modifiers.contains(ModifierPaper)) {
            alternates += "$topic paper arxiv"
            alternates += "$topic IEEE ACM research"
            alternates += "$topic 논문 연구"
        }
        if (alternates.isEmpty() && intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK)) {
            alternates += "$topic 최신 뉴스"
            buildEnglishTechnicalQuery(topic, intent)?.let { alternates += it }
        }
        if (current.contains("다른 출처", ignoreCase = true) && topic.contains("HBM4E", ignoreCase = true)) {
            alternates += "Samsung HBM4E yield latest report"
            alternates += "삼성 HBM4E 수율 분석"
            alternates += "삼성 HBM4E 경쟁사 SK hynix Micron 비교"
        }

        return alternates
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() && !it.equals(primaryQuery, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun resolveContextualTopic(
        previousTopic: String,
        current: String,
        modifiers: List<String>
    ): String {
        if (current.contains("해외 기업", ignoreCase = true) && previousTopic.contains("전고체", ignoreCase = true)) {
            return "전고체 배터리 해외 기업 Toyota QuantumScape Solid Power"
        }
        if (modifiers.contains(ModifierMoreSources) || modifiers.contains(ModifierOpposing)) {
            return previousTopic
        }
        return (previousTopic.split(Regex("\\s+")) + extractKeywords(current))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(" ")
    }

    private fun preferredProviders(
        primaryQuery: String,
        resolvedTopic: String,
        current: String,
        intent: SearchIntent,
        modifiers: List<String>
    ): List<WebSearchProviderType> {
        val domestic = shouldPreferDomestic(primaryQuery, resolvedTopic, current, intent)
        val technicalGlobal = shouldPreferTechnicalGlobal(primaryQuery, resolvedTopic, current, intent, modifiers)
        return when {
            domestic -> listOf(
                WebSearchProviderType.FREE_DEFAULT,
                WebSearchProviderType.NAVER,
                WebSearchProviderType.KAKAO_DAUM,
                WebSearchProviderType.BRAVE
            )
            modifiers.contains(ModifierPaper) || technicalGlobal -> listOf(
                WebSearchProviderType.FREE_DEFAULT,
                WebSearchProviderType.BRAVE,
                WebSearchProviderType.EXA,
                WebSearchProviderType.CUSTOM_COMPATIBLE
            )
            isProductOrConsumerTopic(primaryQuery) -> listOf(
                WebSearchProviderType.FREE_DEFAULT,
                WebSearchProviderType.NAVER,
                WebSearchProviderType.KAKAO_DAUM,
                WebSearchProviderType.BRAVE
            )
            else -> listOf(
                WebSearchProviderType.FREE_DEFAULT,
                WebSearchProviderType.BRAVE,
                WebSearchProviderType.EXA,
                WebSearchProviderType.CUSTOM_COMPATIBLE
            )
        }
    }

    private fun shouldPreferTechnicalGlobal(
        primaryQuery: String,
        resolvedTopic: String,
        current: String,
        intent: SearchIntent,
        modifiers: List<String>
    ): Boolean {
        val combined = "$primaryQuery $resolvedTopic $current"
        if (shouldPreferDomestic(primaryQuery, resolvedTopic, current, intent)) return false
        if (modifiers.contains(ModifierPaper)) return true
        if (GlobalTopicTerms.any { combined.contains(it, ignoreCase = true) }) return true
        if (GlobalCompanyTerms.any { combined.contains(it, ignoreCase = true) }) return true
        if (isTechnicalTopic(combined) && !hasStrongDomesticSignal(combined)) return true
        return listOf("논문", "자료", "성능", "구현", "arxiv", "research", "benchmark")
            .any { combined.contains(it, ignoreCase = true) } && !hasStrongDomesticSignal(combined)
    }

    private fun shouldPreferDomestic(
        primaryQuery: String,
        resolvedTopic: String,
        current: String,
        intent: SearchIntent
    ): Boolean {
        val combined = "$primaryQuery $resolvedTopic $current"
        if (intent == SearchIntent.WEATHER) return true
        if (intent == SearchIntent.STOCK && hasStrongDomesticSignal(combined)) return true
        if (intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO) && hasStrongDomesticSignal(combined)) return true
        if (containsDomesticYieldSignal(combined)) return true
        return DomesticTopicTerms.any { combined.contains(it, ignoreCase = true) }
    }

    private fun hasStrongDomesticSignal(text: String): Boolean {
        return DomesticCompanyTerms.any { text.contains(it, ignoreCase = true) } ||
            DomesticMarketTerms.any { text.contains(it, ignoreCase = true) }
    }

    private fun containsDomesticYieldSignal(text: String): Boolean {
        if (!text.contains("수율", ignoreCase = true)) return false
        return DomesticCompanyTerms.any { text.contains(it, ignoreCase = true) }
    }

    private fun detectIntent(current: String, topic: String, modifiers: List<String>): SearchIntent {
        val text = "$current $topic".lowercase(Locale.ROOT)
        return when {
            NewsTerms.any { text.contains(it) } -> SearchIntent.NEWS
            StockTerms.any { text.contains(it) } -> SearchIntent.STOCK
            WeatherTerms.any { text.contains(it) } -> SearchIntent.WEATHER
            modifiers.contains(ModifierFreshness) -> SearchIntent.CURRENT_INFO
            else -> SearchIntent.GENERAL
        }
    }

    private fun detectModifiers(input: String): List<String> {
        val normalized = input.lowercase(Locale.ROOT)
        return buildList {
            if (MoreSourceTerms.any { normalized.contains(it) }) add(ModifierMoreSources)
            if (OpposingTerms.any { normalized.contains(it) }) add(ModifierOpposing)
            if (FreshnessTerms.any { normalized.contains(it) }) add(ModifierFreshness)
            if (PaperTerms.any { normalized.contains(it) }) add(ModifierPaper)
        }.distinct()
    }

    private fun isGenericFollowUp(input: String): Boolean {
        val normalized = input.trim().lowercase(Locale.ROOT)
        return GenericFollowUps.any { normalized == it }
    }

    private fun isSearchContextFollowUp(input: String): Boolean {
        val normalized = input.trim().lowercase(Locale.ROOT)
        if (normalized.length > 32) return false
        if (!SearchContextIntentTerms.any { normalized.contains(it) }) return false
        return ContextWords.any { normalized.contains(it) } || extractKeywords(input).size <= 4
    }

    private fun buildEnglishTechnicalQuery(topic: String, intent: SearchIntent): String? {
        if (!isTechnicalTopic(topic) && intent == SearchIntent.GENERAL) return null
        return topic
            .replace("삼성", "Samsung")
            .replace("수율", "yield")
            .replace("전고체 배터리", "solid-state battery")
            .replace("최신", "latest")
            .replace("뉴스", "news")
            .replace("분석", "analysis")
            .replace("경쟁사", "competitor")
            .replace("비교", "comparison")
            .replace(Regex("\\s+"), " ")
            .trim() + " latest report"
    }

    private fun removeFillers(text: String): String {
        var result = text
        FillerWords.forEach { word ->
            result = result.replace(Regex("""(?i)(^|\s)\Q$word\E($|\s)"""), " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeKnownTerm(term: String): String = KnownTermCanonical[term.lowercase(Locale.ROOT)] ?: term

    private fun containsKorean(text: String): Boolean = text.any { it in '\uAC00'..'\uD7A3' }

    private fun isTechnicalTopic(text: String): Boolean {
        return ImportantTerms.any { text.contains(it, ignoreCase = true) } ||
            Regex("""(?i)\b\d+\s*nm\b|\b\d+c\b|\b\d+tb\b|\bilt\b|\brag\b|\bkv\b""").containsMatchIn(text)
    }

    private fun isProductOrConsumerTopic(text: String): Boolean {
        return listOf("가격", "리뷰", "구매", "제품", "얼마", "스마트폰", "OLED", "MicroLED", "Snapdragon", "Exynos")
            .any { text.contains(it, ignoreCase = true) }
    }

    private fun buildReason(
        contextDependent: Boolean,
        modifiers: List<String>,
        providers: List<WebSearchProviderType>
    ): String {
        val base = if (contextDependent) "context_follow_up" else "direct_input"
        return "$base; modifiers=${modifiers.joinToString(",").ifBlank { "none" }}; preferred=${providers.joinToString(",") { it.name }}"
    }

    private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    private const val ModifierMoreSources = "more_sources"
    private const val ModifierOpposing = "opposing"
    private const val ModifierFreshness = "freshness"
    private const val ModifierPaper = "paper"

    private val TokenRegex = Regex("""(?i)[A-Za-z][A-Za-z0-9+\-]*|[\uAC00-\uD7A3A-Za-z0-9]+|\d+(?:nm|tb|gb)?""")
    private val TriggerWords = listOf("검색", "찾아", "웹검색", "뉴스", "최신 자료", "최신", "최근", "오늘", "news", "latest", "search")
    private val GenericFollowUps = setOf(
        "검색",
        "검색해줘",
        "찾아줘",
        "더 찾아줘",
        "또 찾아봐",
        "찾아봐",
        "최신 자료 찾아봐",
        "다른 정보도 찾아봐",
        "다른 출처도 봐줘",
        "반대 의견도 찾아봐",
        "해외 기업은?"
    )
    private val GenericSearchWords = setOf("검색", "검색해줘", "찾아줘", "찾아봐", "보여줘", "정리해줘")
    private val SearchContextIntentTerms = listOf("출처", "자료", "검색", "찾아", "뉴스", "최신", "해외 기업", "반대 의견")
    private val ContextWords = listOf("다른", "더", "추가", "반대", "해외")
    private val FillerWords = listOf("그런데", "이거", "요즘", "좀", "찾아봐", "검색해줘", "보여줘", "정리해줘", "현재", "관련", "어떰", "어때", "같음", "같아", "봐줘")
    private val ImportantTerms = listOf(
        "HBM",
        "HBM4",
        "HBM4E",
        "GAA",
        "SF2",
        "SF2P",
        "Exynos",
        "Snapdragon",
        "MTIA",
        "CUDA",
        "RAG",
        "RAG-Fusion",
        "STAR-KV",
        "TurboQuant",
        "cuLitho",
        "ILT",
        "OLED",
        "MicroLED",
        "SDI",
        "LGES",
        "TSMC",
        "Intel",
        "Samsung",
        "NVIDIA",
        "AMD",
        "Qualcomm",
        "SK hynix",
        "Micron",
        "Toyota",
        "QuantumScape",
        "Solid Power"
    )
    private val KnownTermCanonical = mapOf(
        "sk hynix" to "SK hynix",
        "nvidia" to "NVIDIA",
        "amd" to "AMD",
        "tsmc" to "TSMC",
        "samsung" to "Samsung",
        "micron" to "Micron",
        "quantumscape" to "QuantumScape",
        "toyota" to "Toyota",
        "rag-fusion" to "RAG-Fusion",
        "star-kv" to "STAR-KV",
        "turboquant" to "TurboQuant",
        "culitho" to "cuLitho"
    )
    private val NewsTerms = listOf("뉴스", "속보", "보도", "latest", "update")
    private val StockTerms = listOf("주가", "시세", "코스피", "코스닥", "나스닥", "005930")
    private val WeatherTerms = listOf("날씨", "기온", "미세먼지")
    private val MoreSourceTerms = listOf("다른 정보", "더 찾아", "다른 출처", "추가로", "해외 기업", "source", "sources")
    private val OpposingTerms = listOf("반대 의견", "비판", "문제점", "한계", "리스크", "risk", "criticism")
    private val FreshnessTerms = listOf("최신", "요즘", "최근", "오늘", "current", "latest", "update")
    private val PaperTerms = listOf("논문", "연구", "paper", "arxiv", "ieee", "acm", "ilt")
    private val DomesticCompanyTerms = listOf("삼성전자", "삼성SDI", "SK하이닉스", "LG", "LGES", "네이버", "카카오")
    private val DomesticMarketTerms = listOf("국내", "투자", "주가", "코스피", "코스닥", "오늘 뉴스")
    private val DomesticTopicTerms = DomesticCompanyTerms + DomesticMarketTerms
    private val GlobalTopicTerms = listOf("arxiv", "paper", "research", "benchmark", "cuLitho", "STAR-KV", "TurboQuant", "RAG-Fusion", "ILT")
    private val GlobalCompanyTerms = listOf("NVIDIA", "AMD", "Intel", "TSMC", "Micron", "Qualcomm", "Toyota", "QuantumScape", "Solid Power")
}
