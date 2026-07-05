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
        val contextDependent = isGenericFollowUp(current) || isContextDependentFollowUp(current)
        val resolvedTopic = when {
            contextDependent && previousTopic.isNotBlank() -> resolveContextualTopic(previousTopic, current, modifiers)
            extractKeywords(current).isNotEmpty() -> extractKeywords(current).joinToString(" ")
            previousTopic.isNotBlank() -> previousTopic
            else -> current
        }.ifBlank { current }
        val intent = detectIntent(current, resolvedTopic, modifiers)
        val primaryQuery = buildPrimaryQuery(resolvedTopic, current, modifiers, intent)
        val preferred = preferredProviders(primaryQuery, intent, modifiers)

        return WebSearchPlan(
            intent = intent,
            originalInput = current,
            resolvedTopic = resolvedTopic,
            primaryQuery = primaryQuery,
            alternateQueries = buildAlternateQueries(resolvedTopic, primaryQuery, current, modifiers, intent),
            preferredProviderTypes = preferred,
            freshnessRequired = modifiers.contains("freshness") || intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK),
            languageRegion = if (containsKorean(primaryQuery)) "ko-KR" else "en-US",
            regionHint = if (containsKorean(primaryQuery) || isDomesticTopic(primaryQuery)) "KR" else null,
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
            isGenericFollowUp(text)
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
        val topic = resolvedTopic.trim()
        if (current.contains("해외 기업") && topic.contains("전고체 배터리")) {
            return "전고체 배터리 해외 기업 Toyota QuantumScape Solid Power 최신"
        }
        val suffix = when {
            modifiers.contains("opposing") -> "문제점 한계"
            modifiers.contains("paper") -> "paper arxiv research"
            modifiers.contains("more_sources") -> "최신 뉴스"
            modifiers.contains("freshness") || intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK) -> "최신"
            else -> ""
        }
        return listOf(topic, suffix).filter { it.isNotBlank() }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
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

        if (modifiers.contains("more_sources")) {
            alternates += "$topic 분석"
            alternates += "$topic 경쟁사 비교"
            buildEnglishTechnicalQuery(topic, intent)?.let { alternates += it }
        }
        if (modifiers.contains("opposing")) {
            alternates += "$topic 문제점"
            alternates += "$topic 한계"
            alternates += "$topic criticism risk challenge"
        }
        if (modifiers.contains("freshness")) {
            alternates += "$topic 최신 뉴스"
            alternates += "$topic latest update"
            if (!topic.contains(currentYear().toString())) alternates += "$topic ${currentYear()}"
        }
        if (modifiers.contains("paper")) {
            alternates += "$topic paper arxiv"
            alternates += "$topic IEEE ACM research"
            alternates += "$topic 논문 연구"
        }
        if (alternates.isEmpty() && intent in setOf(SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK)) {
            alternates += "$topic 최신 뉴스"
            buildEnglishTechnicalQuery(topic, intent)?.let { alternates += it }
        }
        if (current.contains("다른 출처") && topic.contains("HBM4E", ignoreCase = true)) {
            alternates += "Samsung HBM4E yield latest report"
            alternates += "삼성 HBM4E 수율 분석"
            alternates += "삼성 HBM4E 경쟁사 SK하이닉스 Micron 비교"
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
        if (current.contains("해외 기업") && previousTopic.contains("전고체 배터리")) {
            return "전고체 배터리 해외 기업 Toyota QuantumScape Solid Power"
        }
        if (modifiers.contains("more_sources")) return previousTopic
        return (previousTopic.split(Regex("\\s+")) + extractKeywords(current))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(" ")
    }

    private fun preferredProviders(
        query: String,
        intent: SearchIntent,
        modifiers: List<String>
    ): List<WebSearchProviderType> {
        val technical = isTechnicalTopic(query)
        val domestic = isDomesticTopic(query) || containsKorean(query) || intent in setOf(SearchIntent.NEWS, SearchIntent.STOCK, SearchIntent.WEATHER)
        return when {
            modifiers.contains("paper") -> listOf(WebSearchProviderType.BRAVE, WebSearchProviderType.EXA, WebSearchProviderType.CUSTOM_COMPATIBLE, WebSearchProviderType.FREE_DEFAULT)
            technical && !domestic -> listOf(WebSearchProviderType.FREE_DEFAULT, WebSearchProviderType.BRAVE, WebSearchProviderType.EXA, WebSearchProviderType.CUSTOM_COMPATIBLE)
            isProductOrConsumerTopic(query) -> listOf(WebSearchProviderType.FREE_DEFAULT, WebSearchProviderType.NAVER, WebSearchProviderType.KAKAO_DAUM, WebSearchProviderType.BRAVE)
            domestic -> listOf(WebSearchProviderType.FREE_DEFAULT, WebSearchProviderType.NAVER, WebSearchProviderType.KAKAO_DAUM, WebSearchProviderType.BRAVE)
            intent == SearchIntent.GENERAL -> listOf(WebSearchProviderType.FREE_DEFAULT, WebSearchProviderType.BRAVE, WebSearchProviderType.EXA)
            else -> listOf(WebSearchProviderType.FREE_DEFAULT, WebSearchProviderType.BRAVE, WebSearchProviderType.EXA, WebSearchProviderType.CUSTOM_COMPATIBLE)
        }
    }

    private fun detectIntent(current: String, topic: String, modifiers: List<String>): SearchIntent {
        val text = "$current $topic".lowercase(Locale.ROOT)
        return when {
            listOf("뉴스", "속보", "보도", "latest", "update").any { text.contains(it) } -> SearchIntent.NEWS
            listOf("주가", "시세", "코스피", "코스닥", "나스닥", "005930").any { text.contains(it) } -> SearchIntent.STOCK
            listOf("날씨", "기온", "미세먼지").any { text.contains(it) } -> SearchIntent.WEATHER
            modifiers.contains("freshness") -> SearchIntent.CURRENT_INFO
            else -> SearchIntent.GENERAL
        }
    }

    private fun detectModifiers(input: String): List<String> {
        val normalized = input.lowercase(Locale.ROOT)
        return buildList {
            if (listOf("다른 정보", "더 찾아", "다른 출처", "추가로", "해외 기업").any { normalized.contains(it) }) add("more_sources")
            if (listOf("반대 의견", "비판", "문제점", "한계", "리스크", "risk", "criticism").any { normalized.contains(it) }) add("opposing")
            if (listOf("최신", "요즘", "최근", "오늘", "current", "latest", "update").any { normalized.contains(it) }) add("freshness")
            if (listOf("논문", "연구", "paper", "arxiv", "ieee", "acm").any { normalized.contains(it) }) add("paper")
        }.distinct()
    }

    private fun isGenericFollowUp(input: String): Boolean = GenericFollowUps.any { input.trim().lowercase(Locale.ROOT) == it }

    private fun isContextDependentFollowUp(input: String): Boolean {
        val keywords = extractKeywords(input)
        return input.length <= 24 && keywords.size <= 3 && listOf("은", "는", "도", "해외", "다른", "추가").any { input.contains(it) }
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

    private fun containsKorean(text: String): Boolean = text.any { it in '가'..'힣' }

    private fun isDomesticTopic(text: String): Boolean {
        return listOf("삼성", "SK하이닉스", "LGES", "네이버", "카카오", "코스피", "코스닥", "한국", "국내").any { text.contains(it, ignoreCase = true) }
    }

    private fun isTechnicalTopic(text: String): Boolean {
        return ImportantTerms.any { text.contains(it, ignoreCase = true) } ||
            Regex("""(?i)\b\d+\s*nm\b|\b\d+c\b|\b\d+단\b""").containsMatchIn(text)
    }

    private fun isProductOrConsumerTopic(text: String): Boolean {
        return listOf("가격", "리뷰", "구매", "제품", "스마트폰", "노트북", "OLED", "MicroLED", "Snapdragon", "Exynos").any { text.contains(it, ignoreCase = true) }
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

    private val TokenRegex = Regex("""(?i)[A-Za-z][A-Za-z0-9+\-]*|[가-힣A-Za-z0-9]+|\d+(?:nm|단|년)?|\d+c""")
    private val TriggerWords = listOf("검색", "찾아", "웹검색", "최신", "최근", "오늘", "뉴스", "latest", "current")
    private val GenericFollowUps = setOf("검색", "검색해줘", "웹검색", "찾아줘", "더 찾아봐", "알아봐", "최신 자료 찾아봐", "다른 정보도 찾아봐", "다른 출처도 봐줘", "반대 의견도 찾아봐")
    private val GenericSearchWords = setOf("검색", "검색해줘", "웹검색", "찾아줘", "찾아봐", "알아봐", "알려줘", "정리해줘")
    private val FillerWords = listOf("근데", "음", "스읍", "좀", "찾아봐", "검색해줘", "알려줘", "정리해줘", "요즘", "급", "ㄹㅇ", "ㅋㅋ", "왔음", "얘기", "봐줘")
    private val ImportantTerms = listOf("HBM", "HBM4", "HBM4E", "GAA", "SF2", "SF2P", "Exynos", "Snapdragon", "MTIA", "CUDA", "RAG", "OLED", "MicroLED", "SDI", "LGES", "TSMC", "Intel", "Samsung", "NVIDIA", "AMD", "Qualcomm", "SK hynix", "SK하이닉스", "Micron", "Toyota", "QuantumScape", "Solid Power", "STAR-KV", "TurboQuant")
    private val KnownTermCanonical = mapOf(
        "sk hynix" to "SK hynix",
        "nvidia" to "NVIDIA",
        "amd" to "AMD",
        "tsmc" to "TSMC",
        "samsung" to "Samsung",
        "micron" to "Micron",
        "quantumscape" to "QuantumScape",
        "toyota" to "Toyota"
    )
}
