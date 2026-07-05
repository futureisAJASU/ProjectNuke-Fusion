package com.projectnuke.fusion.search

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class FusionSearchResult(
    val title: String,
    val source: String?,
    val url: String?,
    val snippet: String?,
    val publishedAt: String?,
    val providerType: WebSearchProviderType = WebSearchProviderType.FREE_DEFAULT,
    val providerDisplayName: String = WebSearchProviderType.FREE_DEFAULT.defaultDisplayName(),
    val searchCategory: String? = null,
    val queryUsed: String = "",
    val rank: Int = 0,
    val score: Double? = null,
    val qualityHints: List<String> = emptyList()
)

data class FusionSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val intent: SearchIntent,
    val results: List<FusionSearchResult>,
    val debugMessage: String?,
    val sources: List<WebSearchSource> = emptyList(),
    val traces: List<WebSearchExecutionTrace> = emptyList(),
    val quality: WebSearchQuality? = null,
    val plan: WebSearchPlan? = null
)

enum class SearchIntent {
    NEWS,
    STOCK,
    WEATHER,
    CURRENT_INFO,
    GENERAL
}

private data class HttpFetchResult(
    val body: String,
    val statusCode: Int,
    val contentType: String?,
    val finalUrl: String,
    val debugLabel: String
)

private data class ProviderOutcome(
    val results: List<FusionSearchResult>,
    val traces: List<WebSearchExecutionTrace>
)

private data class SearchSelection(
    val results: List<FusionSearchResult>,
    val quality: WebSearchQuality,
    val debugMessage: String?
)

object FusionWebSearch {
    fun shouldAutoUseWebSearch(userInput: String): Boolean {
        val normalized = userInput.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false

        return detectIntent(userInput) != SearchIntent.GENERAL ||
            listOf(
                "current info",
                "stock price",
                "news",
                "검색",
                "검색해줘",
                "찾아줘",
                "웹검색",
                "최신",
                "최근",
                "오늘",
                "뉴스"
            ).any { normalized.contains(it) }
    }

    fun detectIntent(query: String): SearchIntent {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val newsKeywords = listOf("오늘 주요 뉴스", "오늘 뉴스", "최신 뉴스", "뉴스 정리", "뭐가 중요", "뉴스", "속보")
        val stockKeywords = listOf("주가", "시세", "코스피", "코스닥", "나스닥", "005930", "삼성전자")
        val weatherKeywords = listOf("날씨", "기온", "비 와", "비오", "미세먼지")
        val currentKeywords = listOf("오늘", "현재", "지금", "최신", "최근", "업데이트", "검색", "찾아줘", "알아봐")

        return when {
            newsKeywords.any { normalized.contains(it) } -> SearchIntent.NEWS
            stockKeywords.any { normalized.contains(it) } -> SearchIntent.STOCK
            weatherKeywords.any { normalized.contains(it) } -> SearchIntent.WEATHER
            currentKeywords.any { normalized.contains(it) } -> SearchIntent.CURRENT_INFO
            else -> SearchIntent.GENERAL
        }
    }

    fun rewriteQuery(
        userInput: String,
        previousUserMessage: String?
    ): String = buildSearchPlan(userInput, previousUserMessage).primaryQuery

    fun buildSearchPlan(
        userInput: String,
        previousUserMessage: String?
    ): WebSearchPlan {
        val intent = detectIntent(userInput)
        val trimmed = userInput.trim()
        val previousTopic = previousUserMessage?.trim().orEmpty()
        val primary = when {
            isGenericFollowUp(trimmed) && previousTopic.isNotBlank() -> "$previousTopic $trimmed"
            isBroadNewsQuery(trimmed) -> "대한민국 주요 뉴스 오늘"
            trimmed.contains("삼성전자") && trimmed.contains("주가") -> "삼성전자 005930 주가 오늘 네이버 금융"
            else -> trimmed
        }.trim()

        val alternate = buildList {
            if (asksForMoreSources(trimmed) && previousTopic.isNotBlank()) {
                val freshness = when (intent) {
                    SearchIntent.NEWS, SearchIntent.CURRENT_INFO, SearchIntent.STOCK -> "최신 뉴스 분석"
                    else -> "추가 자료 분석"
                }
                add("$previousTopic $trimmed $freshness".trim())
            }
        }

        val preferred = when {
            looksKoreanDomestic(primary, intent) -> listOf(WebSearchProviderType.NAVER, WebSearchProviderType.KAKAO_DAUM)
            intent == SearchIntent.NEWS -> listOf(WebSearchProviderType.NAVER, WebSearchProviderType.BRAVE, WebSearchProviderType.KAKAO_DAUM)
            else -> listOf(WebSearchProviderType.BRAVE, WebSearchProviderType.EXA, WebSearchProviderType.CUSTOM_COMPATIBLE)
        }

        // TODO: Add small local query planner model. Output contract:
        // intent, primary query, alternate queries, provider category, freshness, language/region.
        // External AI planner can be added later only after prompt/history stripping is verified.
        return WebSearchPlan(
            intent = intent,
            primaryQuery = primary,
            alternateQueries = alternate,
            preferredProviderTypes = preferred,
            freshnessRequired = intent == SearchIntent.NEWS || intent == SearchIntent.CURRENT_INFO || intent == SearchIntent.STOCK,
            languageRegion = if (primary.any { it in '가'..'힣' }) "ko-KR" else "en-US"
        )
    }

    suspend fun search(
        userInput: String,
        previousUserMessage: String?,
        providerRepository: WebSearchProviderRepository? = null
    ): FusionSearchResponse {
        val plan = RuleBasedWebSearchPlanner.plan(
            RuleBasedWebSearchPlannerInput(
                currentUserInput = userInput,
                previousUserMessage = previousUserMessage
            )
        )
        val queries = (listOf(plan.primaryQuery) + plan.alternateQueries).filter { it.isNotBlank() }.distinct()

        return withContext(Dispatchers.IO) {
            if (providerRepository == null) {
                val outcome = searchFreeDefault(plan.primaryQuery, plan.intent)
                return@withContext buildResponse(userInput, plan, outcome.results, outcome.traces)
            }

            val mode = providerRepository.selectedMode()
            val providers = providerRepository.listProviders()
            val selectedProvider = providerRepository.selectedProvider()
            val traces = mutableListOf<WebSearchExecutionTrace>()

            if (mode == WebSearchMode.MANUAL) {
                val missing = providerRepository.missingRequirements(selectedProvider)
                if (missing.isNotEmpty()) {
                    val reason = "수동 제공자 필수 설정 누락: ${missing.joinToString(",")}"
                    traces += WebSearchExecutionTrace(
                        providerType = selectedProvider.type,
                        providerDisplayName = selectedProvider.displayName,
                        queryUsed = plan.primaryQuery,
                        fallbackReason = reason
                    )
                    if (!selectedProvider.allowFallbackInManualMode) {
                        return@withContext buildResponse(userInput, plan, emptyList(), traces, reason)
                    }
                    val free = searchFreeDefault(plan.primaryQuery, plan.intent)
                    return@withContext buildResponse(userInput, plan, free.results, traces + free.traces, reason)
                }
                val manual = executeProvider(providerRepository, selectedProvider, plan.primaryQuery, plan.intent)
                val quality = evaluateResultQuality(manual.results, plan.primaryQuery, plan.intent, manual.traces.lastOrNull())
                traces += manual.traces.map { it.copy(qualityScore = quality.score) }
                if (!quality.isUsable && plan.alternateQueries.isNotEmpty()) {
                    val altQuery = plan.alternateQueries.first()
                    val alt = executeProvider(providerRepository, selectedProvider, altQuery, plan.intent)
                    val altQuality = evaluateResultQuality(alt.results, altQuery, plan.intent, alt.traces.lastOrNull())
                    traces += alt.traces.map {
                        it.copy(
                            qualityScore = altQuality.score,
                            fallbackReason = if (altQuality.shouldTryFallback) altQuality.reasons.joinToString("; ") else "alternate query after poor primary"
                        )
                    }
                    if (altQuality.score > quality.score) {
                        if (altQuality.isUsable || !selectedProvider.allowFallbackInManualMode) {
                            return@withContext buildResponse(userInput, plan, alt.results, traces, "수동 제공자에서 대체 검색어를 사용했습니다.")
                        }
                    }
                }
                if (quality.isUsable || !selectedProvider.allowFallbackInManualMode) {
                    return@withContext buildResponse(userInput, plan, manual.results, traces, quality.reasons.joinToString("; ").ifBlank { null })
                }
                val free = searchFreeDefault(plan.primaryQuery, plan.intent)
                return@withContext buildResponse(
                    userInput,
                    plan,
                    if (free.results.isNotEmpty()) free.results else manual.results,
                    traces + free.traces,
                    "수동 제공자 결과 품질 부족으로 무료 기본 검색을 시도했습니다."
                )
            }

            var bestResults = emptyList<FusionSearchResult>()
            var bestQuality = WebSearchQuality(false, 0.0, listOf("not attempted"), true)
            val free = searchFreeDefault(plan.primaryQuery, plan.intent)
            val freeQuality = evaluateResultQuality(free.results, plan.primaryQuery, plan.intent, free.traces.lastOrNull())
            traces += free.traces.map { it.copy(qualityScore = freeQuality.score, fallbackReason = if (freeQuality.shouldTryFallback) freeQuality.reasons.joinToString("; ") else null) }
            bestResults = free.results
            bestQuality = freeQuality
            if (freeQuality.isUsable) {
                return@withContext buildResponse(userInput, plan, bestResults, traces)
            }

            val bounded = runBoundedAutoFallback(
                repository = providerRepository,
                providers = providers,
                plan = plan,
                traces = traces,
                currentBestResults = bestResults,
                currentBestQuality = bestQuality
            )
            return@withContext buildResponse(
                userInput = userInput,
                plan = plan,
                rawResults = bounded.results,
                traces = traces,
                debug = bounded.debugMessage
            )

            val candidates = providers
                .filter { it.type != WebSearchProviderType.FREE_DEFAULT && it.isEnabled }
                .sortedWith(compareBy<WebSearchProviderConfig> { typeRank(it.type, plan.preferredProviderTypes) }.thenBy { it.priority })

            for (provider in candidates) {
                val missing = providerRepository.missingRequirements(provider)
                if (missing.isNotEmpty()) {
                    traces += WebSearchExecutionTrace(
                        providerType = provider.type,
                        providerDisplayName = provider.displayName,
                        queryUsed = plan.primaryQuery,
                        fallbackReason = "필수 설정 누락: ${missing.joinToString(",")}"
                    )
                    continue
                }
                for (query in queries) {
                    val outcome = executeProvider(providerRepository, provider, query, plan.intent)
                    val quality = evaluateResultQuality(outcome.results, query, plan.intent, outcome.traces.lastOrNull())
                    traces += outcome.traces.map { it.copy(qualityScore = quality.score, fallbackReason = if (quality.shouldTryFallback) quality.reasons.joinToString("; ") else null) }
                    if (quality.score > bestQuality.score) {
                        bestQuality = quality
                        bestResults = outcome.results
                    }
                    if (quality.isUsable) {
                        return@withContext buildResponse(
                            userInput,
                            plan,
                            outcome.results,
                            traces,
                            "자동 모드에서 무료 기본 검색 품질이 낮아 ${provider.displayName} 제공자를 사용했습니다."
                        )
                    }
                }
            }

            buildResponse(
                userInput,
                plan,
                bestResults,
                traces,
                if (bestResults.isEmpty()) "검색 결과를 불러오지 못했습니다." else "API 제공자를 사용할 수 없어 무료 기본 검색 결과를 사용했습니다."
            )
        }
    }

    private fun typeRank(type: WebSearchProviderType, preferred: List<WebSearchProviderType>): Int {
        val index = preferred.indexOf(type)
        return if (index >= 0) index else 100
    }

    private suspend fun runBoundedAutoFallback(
        repository: WebSearchProviderRepository,
        providers: List<WebSearchProviderConfig>,
        plan: WebSearchPlan,
        traces: MutableList<WebSearchExecutionTrace>,
        currentBestResults: List<FusionSearchResult>,
        currentBestQuality: WebSearchQuality
    ): SearchSelection {
        var bestResults = currentBestResults
        var bestQuality = currentBestQuality
        var debug: String? = null

        val firstAlternate = plan.alternateQueries.firstOrNull()
        if (firstAlternate != null) {
            val freeAlt = searchFreeDefault(firstAlternate, plan.intent)
            val freeAltQuality = evaluateResultQuality(freeAlt.results, firstAlternate, plan.intent, freeAlt.traces.lastOrNull())
            traces += freeAlt.traces.map {
                it.copy(
                    qualityScore = freeAltQuality.score,
                    fallbackReason = if (freeAltQuality.shouldTryFallback) freeAltQuality.reasons.joinToString("; ") else "free alternate query"
                )
            }
            if (freeAltQuality.score > bestQuality.score) {
                bestResults = freeAlt.results
                bestQuality = freeAltQuality
                debug = "무료 기본 검색에서 대체 검색어를 사용했습니다."
            }
            if (freeAltQuality.isUsable) {
                return SearchSelection(bestResults, bestQuality, debug)
            }
        }

        val provider = providers
            .filter { it.type != WebSearchProviderType.FREE_DEFAULT && it.isEnabled }
            .sortedWith(compareBy<WebSearchProviderConfig> { typeRank(it.type, plan.preferredProviderTypes) }.thenBy { it.priority })
            .firstOrNull { repository.missingRequirements(it).isEmpty() }
            ?: return SearchSelection(
                bestResults,
                bestQuality,
                debug ?: if (bestResults.isEmpty()) "검색 결과를 불러오지 못했습니다." else "API 제공자를 사용할 수 없어 무료 기본 검색 결과를 사용했습니다."
            )

        val apiPrimary = executeProvider(repository, provider, plan.primaryQuery, plan.intent)
        val apiPrimaryQuality = evaluateResultQuality(apiPrimary.results, plan.primaryQuery, plan.intent, apiPrimary.traces.lastOrNull())
        traces += apiPrimary.traces.map {
            it.copy(
                qualityScore = apiPrimaryQuality.score,
                fallbackReason = if (apiPrimaryQuality.shouldTryFallback) apiPrimaryQuality.reasons.joinToString("; ") else "api primary query"
            )
        }
        if (apiPrimaryQuality.score > bestQuality.score) {
            bestResults = apiPrimary.results
            bestQuality = apiPrimaryQuality
            debug = "자동 모드에서 ${provider.displayName} 제공자를 사용했습니다."
        }
        if (apiPrimaryQuality.isUsable) {
            return SearchSelection(bestResults, bestQuality, debug)
        }

        if (firstAlternate != null) {
            val apiAlt = executeProvider(repository, provider, firstAlternate, plan.intent)
            val apiAltQuality = evaluateResultQuality(apiAlt.results, firstAlternate, plan.intent, apiAlt.traces.lastOrNull())
            traces += apiAlt.traces.map {
                it.copy(
                    qualityScore = apiAltQuality.score,
                    fallbackReason = if (apiAltQuality.shouldTryFallback) apiAltQuality.reasons.joinToString("; ") else "api alternate query"
                )
            }
            if (apiAltQuality.score > bestQuality.score) {
                bestResults = apiAlt.results
                bestQuality = apiAltQuality
                debug = "자동 모드에서 ${provider.displayName} 제공자와 대체 검색어를 사용했습니다."
            }
        }

        return SearchSelection(
            bestResults,
            bestQuality,
            debug ?: if (bestResults.isEmpty()) "검색 결과를 불러오지 못했습니다." else "검색 결과 품질이 낮아 가장 나은 결과를 사용했습니다."
        )
    }

    private fun buildResponse(
        userInput: String,
        plan: WebSearchPlan,
        rawResults: List<FusionSearchResult>,
        traces: List<WebSearchExecutionTrace>,
        debug: String? = null
    ): FusionSearchResponse {
        val results = rawResults
            .filter { it.title.isNotBlank() }
            .distinctBy { (it.url ?: it.title).normalizedTitleKey() }
            .take(if (plan.intent == SearchIntent.NEWS) 10 else 8)
            .mapIndexed { index, result -> result.copy(rank = index + 1, queryUsed = result.queryUsed.ifBlank { plan.primaryQuery }) }
        val quality = evaluateResultQuality(results, plan.primaryQuery, plan.intent, traces.lastOrNull())
        val sources = results.map { it.toSource() }
        val traceSummary = traces
            .takeLast(4)
            .joinToString(" / ") { trace ->
                buildString {
                    append(trace.providerDisplayName)
                    append(":")
                    append(trace.parsedResultCount)
                    trace.httpStatus?.let { append(" status=$it") }
                    trace.fallbackReason?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                }
            }
        return FusionSearchResponse(
            query = userInput,
            normalizedQuery = plan.primaryQuery,
            intent = plan.intent,
            results = results,
            debugMessage = listOfNotNull(
                debug,
                if (results.isEmpty()) "검색 결과를 불러오지 못했습니다." else null,
                if (quality.shouldTryFallback && traces.isNotEmpty()) "검색 진단: $traceSummary" else null
            ).distinct().joinToString(" ").ifBlank { null },
            sources = sources,
            traces = traces,
            quality = quality,
            plan = plan
        )
    }

    private suspend fun executeProvider(
        repository: WebSearchProviderRepository,
        provider: WebSearchProviderConfig,
        query: String,
        intent: SearchIntent
    ): ProviderOutcome = when (provider.type) {
        WebSearchProviderType.FREE_DEFAULT -> searchFreeDefault(query, intent)
        WebSearchProviderType.NAVER -> searchNaver(repository, provider, query, intent)
        WebSearchProviderType.KAKAO_DAUM -> searchKakaoDaum(repository, provider, query, intent)
        WebSearchProviderType.EXA -> searchExa(repository, provider, query, intent)
        WebSearchProviderType.BRAVE -> searchBrave(repository, provider, query, intent)
        WebSearchProviderType.CUSTOM_COMPATIBLE -> searchCustomCompatible(repository, provider, query, intent)
    }

    private fun searchFreeDefault(query: String, intent: SearchIntent): ProviderOutcome {
        val traces = mutableListOf<WebSearchExecutionTrace>()
        val results = mutableListOf<FusionSearchResult>()
        fun append(label: String, block: () -> List<FusionSearchResult>) {
            runCatching { block() }
                .onSuccess {
                    traces += WebSearchExecutionTrace(WebSearchProviderType.FREE_DEFAULT, "무료 기본 검색", query, parsedResultCount = it.size, debugLabel = label)
                    results += it
                }
                .onFailure {
                    traces += WebSearchExecutionTrace(
                        providerType = WebSearchProviderType.FREE_DEFAULT,
                        providerDisplayName = "무료 기본 검색",
                        queryUsed = query,
                        exceptionClass = it::class.java.simpleName,
                        exceptionMessage = it.message?.take(120),
                        fallbackReason = "$label 실패",
                        debugLabel = label
                    )
                }
        }

        if (intent == SearchIntent.NEWS) append("Google News RSS") { searchGoogleNews(query) }
        if (results.isEmpty()) append("DuckDuckGo HTML") { searchDuckDuckGoHtml(query) }
        if (results.isEmpty() && (intent == SearchIntent.CURRENT_INFO || intent == SearchIntent.GENERAL || intent == SearchIntent.NEWS)) {
            append("DuckDuckGo Lite") { searchDuckDuckGoLite(query) }
        }
        if (results.isEmpty() && intent != SearchIntent.NEWS) append("Google News RSS") { searchGoogleNews(query) }
        if (results.isEmpty()) append("DuckDuckGo Instant") { searchDuckDuckGoInstant(query) }

        return ProviderOutcome(
            results = results.distinctBy { (it.url ?: it.title).normalizedTitleKey() },
            traces = traces
        )
    }

    private fun searchGoogleNews(query: String): List<FusionSearchResult> {
        val broadNews = isBroadNewsQuery(query) || query == "대한민국 주요 뉴스 오늘"
        val url = if (broadNews) {
            "https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko"
        } else {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            "https://news.google.com/rss/search?q=$encodedQuery&hl=ko&gl=KR&ceid=KR:ko"
        }
        val xml = openUrlResponse(url, "Google News RSS").body
        return parseGoogleNewsRss(xml, query)
    }

    private fun parseGoogleNewsRss(xml: String, query: String): List<FusionSearchResult> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())

        val results = mutableListOf<FusionSearchResult>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var source: String? = null
        var pubDate: String? = null
        var description: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = null
                        link = null
                        source = null
                        pubDate = null
                        description = null
                    }
                    "title" -> if (inItem) title = parser.nextTextClean()
                    "link" -> if (inItem) link = parser.nextTextClean()
                    "source" -> if (inItem) source = parser.nextTextClean()
                    "pubDate" -> if (inItem) pubDate = parser.nextTextClean()
                    "description" -> if (inItem) description = cleanHtmlText(parser.nextTextClean())
                }
            } else if (eventType == XmlPullParser.END_TAG && parser.name == "item") {
                val itemTitle = cleanHtmlText(title.orEmpty())
                if (itemTitle.isNotBlank()) {
                    results.add(
                        FusionSearchResult(
                            title = itemTitle,
                            source = source?.ifBlank { null } ?: sourceFromUrl(link),
                            url = link?.ifBlank { null },
                            snippet = description?.ifBlank { null },
                            publishedAt = pubDate?.ifBlank { null },
                            providerType = WebSearchProviderType.FREE_DEFAULT,
                            providerDisplayName = "Google News",
                            searchCategory = "NEWS",
                            queryUsed = query
                        )
                    )
                }
                inItem = false
            }
            eventType = parser.next()
        }
        return results.distinctBy { it.title.normalizedTitleKey() }.take(12)
    }

    private fun searchDuckDuckGoInstant(query: String): List<FusionSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val jsonText = openUrlResponse(
            "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1",
            "DuckDuckGo Instant"
        ).body
        val json = JSONObject(jsonText)
        val results = mutableListOf<FusionSearchResult>()
        val heading = json.optString("Heading")
        val abstractText = json.optString("AbstractText")
        val abstractUrl = json.optString("AbstractURL")
        val answer = json.optString("Answer")
        val definition = json.optString("Definition")

        fun add(text: String) {
            if (text.isNotBlank()) {
                results.add(FusionSearchResult(heading.ifBlank { query }, "DuckDuckGo", abstractUrl.ifBlank { null }, text, null, providerDisplayName = "DuckDuckGo Instant", queryUsed = query))
            }
        }
        add(answer)
        add(definition)
        add(abstractText)

        val relatedTopics = json.optJSONArray("RelatedTopics")
        if (relatedTopics != null) {
            var index = 0
            while (index < relatedTopics.length() && results.size < 8) {
                val item = relatedTopics.optJSONObject(index)
                val text = item?.optString("Text").orEmpty()
                val url = item?.optString("FirstURL").orEmpty()
                if (text.isNotBlank()) {
                    results.add(FusionSearchResult(text.take(90), sourceFromUrl(url), url.ifBlank { null }, text, null, providerDisplayName = "DuckDuckGo Instant", queryUsed = query))
                }
                index += 1
            }
        }
        return results
    }

    private fun searchDuckDuckGoHtml(query: String): List<FusionSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val html = openUrlResponse("https://html.duckduckgo.com/html/?q=$encodedQuery", "DuckDuckGo HTML").body
        val resultRegex = Regex("""(?s)<div class="result results_links.*?</div>\s*</div>""")
        val titleRegex = Regex("""(?s)<a rel="nofollow" class="result__a" href="(.*?)">(.*?)</a>""")
        val snippetRegex = Regex("""(?s)<a class="result__snippet".*?>(.*?)</a>|<div class="result__snippet".*?>(.*?)</div>""")
        return resultRegex.findAll(html).mapNotNull { blockMatch ->
            val block = blockMatch.value
            val titleMatch = titleRegex.find(block) ?: return@mapNotNull null
            val url = cleanDuckDuckGoUrl(titleMatch.groupValues[1])
            val title = cleanHtmlText(titleMatch.groupValues[2])
            val snippet = snippetRegex.find(block)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.let(::cleanHtmlText)
            if (title.isBlank()) null else FusionSearchResult(title, sourceFromUrl(url), url.ifBlank { null }, snippet?.ifBlank { null }, null, providerDisplayName = "DuckDuckGo HTML", queryUsed = query)
        }.distinctBy { it.title.normalizedTitleKey() }.take(8).toList()
    }

    private fun searchDuckDuckGoLite(query: String): List<FusionSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val html = openUrlResponse("https://lite.duckduckgo.com/lite/?q=$encodedQuery", "DuckDuckGo Lite").body
        val rowRegex = Regex("""(?is)<tr>\s*<td[^>]*>.*?</td>\s*</tr>""")
        val titleRegex = Regex("""(?is)<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>""")
        return rowRegex.findAll(html).mapNotNull { row ->
            val titleMatch = titleRegex.find(row.value) ?: return@mapNotNull null
            val title = cleanHtmlText(titleMatch.groupValues[2])
            val url = cleanDuckDuckGoUrl(titleMatch.groupValues[1])
            if (title.isBlank() || url.contains("duckduckgo.com/y.js").not() && !url.startsWith("http")) {
                null
            } else {
                FusionSearchResult(title, sourceFromUrl(url), url.ifBlank { null }, cleanHtmlText(row.value).removePrefix(title).trim().ifBlank { null }, null, providerDisplayName = "DuckDuckGo Lite", queryUsed = query)
            }
        }.distinctBy { (it.url ?: it.title).normalizedTitleKey() }.take(8).toList()
    }

    private suspend fun searchNaver(repository: WebSearchProviderRepository, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): ProviderOutcome {
        val clientId = repository.getSecretValue(provider.clientIdSecretId).orEmpty()
        val clientSecret = repository.getSecretValue(provider.clientSecretSecretId).orEmpty()
        val endpoint = if (intent == SearchIntent.NEWS) "news" else "webkr"
        val url = "https://openapi.naver.com/v1/search/$endpoint.json?query=${URLEncoder.encode(query, "UTF-8")}&display=8&sort=sim"
        return apiOutcome(provider, query) {
            val res = openUrlResponse(url, provider.displayName, headers = mapOf("X-Naver-Client-Id" to clientId, "X-Naver-Client-Secret" to clientSecret))
            parseNaver(JSONObject(res.body).optJSONArray("items") ?: JSONArray(), provider, query, intent) to res
        }
    }

    private fun parseNaver(items: JSONArray, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): List<FusionSearchResult> = buildList {
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("originallink").ifBlank { item.optString("link") }
            add(FusionSearchResult(cleanHtmlText(item.optString("title")), sourceFromUrl(url) ?: provider.displayName, url.ifBlank { null }, cleanHtmlText(item.optString("description")).ifBlank { null }, item.optString("pubDate").ifBlank { null }, provider.type, provider.displayName, intent.name, query))
        }
    }

    private suspend fun searchKakaoDaum(repository: WebSearchProviderRepository, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): ProviderOutcome {
        val apiKey = repository.getSecretValue(provider.apiKeySecretId).orEmpty()
        val url = "https://dapi.kakao.com/v2/search/web?query=${URLEncoder.encode(query, "UTF-8")}&size=10"
        return apiOutcome(provider, query) {
            val res = openUrlResponse(url, provider.displayName, headers = mapOf("Authorization" to "KakaoAK $apiKey"))
            val docs = JSONObject(res.body).optJSONArray("documents") ?: JSONArray()
            val results = buildList {
                for (i in 0 until docs.length()) {
                    val item = docs.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url")
                    add(FusionSearchResult(cleanHtmlText(item.optString("title")), sourceFromUrl(itemUrl) ?: "Kakao/Daum", itemUrl.ifBlank { null }, cleanHtmlText(item.optString("contents")).ifBlank { null }, item.optString("datetime").ifBlank { null }, provider.type, provider.displayName, intent.name, query))
                }
            }
            results to res
        }
    }

    private suspend fun searchExa(repository: WebSearchProviderRepository, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): ProviderOutcome {
        val apiKey = repository.getSecretValue(provider.apiKeySecretId).orEmpty()
        val body = JSONObject().put("query", query).put("numResults", 8).put("type", "auto").toString()
        return apiOutcome(provider, query) {
            val res = openUrlResponse("https://api.exa.ai/search", provider.displayName, method = "POST", body = body, headers = mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json"))
            val arr = JSONObject(res.body).optJSONArray("results") ?: JSONArray()
            val results = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url")
                    add(FusionSearchResult(item.optString("title").ifBlank { itemUrl }, sourceFromUrl(itemUrl) ?: "Exa", itemUrl.ifBlank { null }, item.optString("text").take(280).ifBlank { null }, item.optString("publishedDate").ifBlank { null }, provider.type, provider.displayName, intent.name, query, score = item.optDouble("score").takeIf { !it.isNaN() }))
                }
            }
            results to res
        }
    }

    private suspend fun searchBrave(repository: WebSearchProviderRepository, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): ProviderOutcome {
        val apiKey = repository.getSecretValue(provider.apiKeySecretId).orEmpty()
        val url = "https://api.search.brave.com/res/v1/web/search?q=${URLEncoder.encode(query, "UTF-8")}&count=8"
        return apiOutcome(provider, query) {
            val res = openUrlResponse(url, provider.displayName, headers = mapOf("X-Subscription-Token" to apiKey))
            val arr = JSONObject(res.body).optJSONObject("web")?.optJSONArray("results") ?: JSONArray()
            val results = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url")
                    add(FusionSearchResult(cleanHtmlText(item.optString("title")), sourceFromUrl(itemUrl) ?: "Brave", itemUrl.ifBlank { null }, cleanHtmlText(item.optString("description")).ifBlank { null }, item.optString("age").ifBlank { null }, provider.type, provider.displayName, intent.name, query))
                }
            }
            results to res
        }
    }

    private suspend fun searchCustomCompatible(repository: WebSearchProviderRepository, provider: WebSearchProviderConfig, query: String, intent: SearchIntent): ProviderOutcome {
        val base = provider.baseUrl?.trim().orEmpty()
        val apiKey = repository.getSecretValue(provider.apiKeySecretId).orEmpty()
        val separator = if (base.contains("?")) "&" else "?"
        val url = "$base${separator}q=${URLEncoder.encode(query, "UTF-8")}"
        return apiOutcome(provider, query) {
            val headers = if (provider.noAuth) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")
            val res = openUrlResponse(url, provider.displayName, headers = headers)
            val json = JSONObject(res.body)
            val arr = json.optJSONArray("results") ?: json.optJSONArray("items") ?: JSONArray()
            val results = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val itemUrl = item.optString("url").ifBlank { item.optString("link") }
                    add(FusionSearchResult(cleanHtmlText(item.optString("title")), sourceFromUrl(itemUrl) ?: provider.displayName, itemUrl.ifBlank { null }, cleanHtmlText(item.optString("snippet").ifBlank { item.optString("description") }).ifBlank { null }, item.optString("publishedAt").ifBlank { null }, provider.type, provider.displayName, intent.name, query))
                }
            }
            results to res
        }
    }

    private fun apiOutcome(
        provider: WebSearchProviderConfig,
        query: String,
        block: () -> Pair<List<FusionSearchResult>, HttpFetchResult>
    ): ProviderOutcome {
        return runCatching {
            val (results, response) = block()
            ProviderOutcome(
                results,
                listOf(WebSearchExecutionTrace(provider.type, provider.displayName, query, httpStatus = response.statusCode, parsedResultCount = results.size, debugLabel = response.debugLabel))
            )
        }.getOrElse { error ->
            ProviderOutcome(
                emptyList(),
                listOf(
                    WebSearchExecutionTrace(
                        providerType = provider.type,
                        providerDisplayName = provider.displayName,
                        queryUsed = query,
                        exceptionClass = error::class.java.simpleName,
                        exceptionMessage = error.message?.take(120),
                        fallbackReason = "제공자 요청 또는 파싱 실패"
                    )
                )
            )
        }
    }

    private fun evaluateResultQuality(
        results: List<FusionSearchResult>,
        query: String,
        intent: SearchIntent,
        trace: WebSearchExecutionTrace?
    ): WebSearchQuality {
        val valid = results.filter { it.title.isNotBlank() && !it.url.isNullOrBlank() }
        val duplicateCount = results.size - results.distinctBy { (it.url ?: it.title).normalizedTitleKey() }.size
        val blocked = results.any { looksBlocked(it.title) || looksBlocked(it.snippet.orEmpty()) } || trace?.httpStatus in listOf(401, 403, 429, 503)
        val queryTokens = queryTokens(query)
        val topText = results.take(3).joinToString(" ") { "${it.title} ${it.snippet.orEmpty()}" }.lowercase(Locale.ROOT)
        val overlap = queryTokens.count { topText.contains(it) }
        var score = 0.0
        if (valid.size >= 2) score += 0.45
        if (intent == SearchIntent.NEWS && results.any { it.title.isNotBlank() && (!it.source.isNullOrBlank() || !it.publishedAt.isNullOrBlank() || !it.snippet.isNullOrBlank()) }) score += 0.35
        if (overlap >= 2 || (queryTokens.size <= 2 && overlap >= 1)) score += 0.25
        if (results.isNotEmpty()) score += 0.1
        if (duplicateCount > 1) score -= 0.15
        if (blocked) score -= 0.35
        score = score.coerceIn(0.0, 1.0)
        val usable = valid.size >= 2 || (intent == SearchIntent.NEWS && score >= 0.35) || overlap >= 2
        val reasons = buildList {
            add("count=${results.size}")
            add("valid=${valid.size}")
            if (duplicateCount > 0) add("duplicates=$duplicateCount")
            if (overlap > 0) add("tokenOverlap=$overlap")
            if (blocked) add("blocked_or_rate_limited")
            trace?.exceptionClass?.let { add("exception=$it") }
            trace?.httpStatus?.let { if (it !in 200..299) add("status=$it") }
        }
        return WebSearchQuality(
            isUsable = usable && !blocked,
            score = score,
            reasons = reasons,
            shouldTryFallback = !usable || blocked || results.isEmpty()
        )
    }

    private fun openUrlResponse(
        url: String,
        label: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpFetchResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
            setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36 Fusion/1.0")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) doOutput = true
        }

        return try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
            val status = runCatching { connection.responseCode }.getOrDefault(-1)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val bytes = stream.use { it.readBytes() }
            val charset = connection.contentType.charsetFromContentType()
            HttpFetchResult(String(bytes, charset), status, connection.contentType, connection.url.toString(), label)
        } finally {
            connection.disconnect()
        }
    }

    private fun String?.charsetFromContentType(): Charset {
        val value = this ?: return StandardCharsets.UTF_8
        val match = Regex("""(?i)charset\s*=\s*["']?([^;"']+)""").find(value)
        return match?.groupValues?.getOrNull(1)?.trim()?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8
    }

    private fun XmlPullParser.nextTextClean(): String = runCatching { nextText() }.getOrDefault("").trim()

    private fun cleanHtmlText(raw: String): String {
        return raw
            .replace(Regex("<.*?>"), " ")
            .replace(Regex("&#(x?[0-9A-Fa-f]+);")) { match -> decodeHtmlEntity(match.groupValues[1]) ?: match.value }
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtmlEntity(value: String): String? {
        val codePoint = runCatching {
            if (value.startsWith("x", ignoreCase = true)) value.substring(1).toInt(16) else value.toInt(10)
        }.getOrNull() ?: return null
        if (!Character.isValidCodePoint(codePoint)) return null
        return String(Character.toChars(codePoint))
    }

    private fun cleanDuckDuckGoUrl(raw: String): String {
        val cleaned = cleanHtmlText(raw).replace("&amp;", "&").trim()
        val uriMarker = "uddg="
        if (!cleaned.contains(uriMarker)) return cleaned
        val encodedTarget = cleaned.substringAfter(uriMarker).substringBefore("&")
        return runCatching { URLDecoder.decode(encodedTarget, "UTF-8") }.getOrDefault(cleaned)
    }

    private fun sourceFromUrl(url: String?): String? {
        val value = url?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { URL(value).host.removePrefix("www.").ifBlank { null } }.getOrNull()
    }

    private fun String.normalizedTitleKey(): String = lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    private fun isGenericFollowUp(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT).trim()
        return listOf("검색", "검색해줘", "검색해서 알려줘", "찾아줘", "찾아서 알려줘", "웹검색", "더 검색", "알아봐").any { normalized == it }
    }

    private fun isBroadNewsQuery(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return listOf("오늘 주요 뉴스", "오늘 뉴스", "최신 뉴스", "뉴스 정리", "뭐가 중요해", "무슨 일").any { normalized.contains(it) }
    }

    private fun asksForMoreSources(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return listOf("다른 정보", "더 찾아봐", "다른 출처", "추가로", "반대 의견", "최신 자료", "another source", "latest").any { normalized.contains(it) }
    }

    private fun looksKoreanDomestic(query: String, intent: SearchIntent): Boolean {
        return query.any { it in '가'..'힣' } || intent == SearchIntent.STOCK || intent == SearchIntent.WEATHER
    }

    private fun queryTokens(query: String): List<String> {
        return Regex("""[A-Za-z0-9가-힣]{2,}""")
            .findAll(query.lowercase(Locale.ROOT))
            .map { it.value }
            .filterNot { it in setOf("검색", "찾아줘", "최신", "오늘", "news", "latest") }
            .distinct()
            .take(12)
            .toList()
    }

    private fun looksBlocked(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return listOf("captcha", "unusual traffic", "access denied", "blocked", "rate limit", "no results").any { normalized.contains(it) }
    }

    private fun FusionSearchResult.toSource(): WebSearchSource {
        return WebSearchSource(title, source, url, snippet, publishedAt, providerType, providerDisplayName, searchCategory, queryUsed, rank, score, qualityHints)
    }
}

fun FusionSearchResponse.toStructuredContext(): String {
    return buildString {
        appendLine("[FUSION_WEB_SEARCH_RESULTS]")
        appendLine("Intent: ${intent.name}")
        appendLine("Original query: $query")
        appendLine("Normalized query: $normalizedQuery")
        appendLine("Result count: ${results.size}")
        debugMessage?.let { appendLine("Debug: $it") }
        appendLine()

        results.forEachIndexed { index, result ->
            appendLine("${index + 1}. Title: ${result.title}")
            appendLine("   Provider: ${result.providerDisplayName}")
            appendLine("   Query: ${result.queryUsed}")
            appendLine("   Source: ${result.source.orEmpty()}")
            appendLine("   Published: ${result.publishedAt.orEmpty()}")
            appendLine("   URL: ${result.url.orEmpty()}")
            appendLine("   Snippet: ${result.snippet.orEmpty()}")
            appendLine()
        }
    }.trim()
}

private const val SearchSourcesStart = "[FUSION_SEARCH_SOURCES_JSON]"
private const val SearchSourcesEnd = "[/FUSION_SEARCH_SOURCES_JSON]"

fun appendSearchSourcesMetadata(visibleAnswer: String, sources: List<WebSearchSource>): String {
    if (sources.isEmpty()) return visibleAnswer
    val array = JSONArray()
    sources.take(12).forEach { source ->
        array.put(
            JSONObject()
                .put("title", source.title)
                .put("source", source.source)
                .put("url", source.url)
                .put("snippet", source.snippet)
                .put("publishedAt", source.publishedAt)
                .put("providerType", source.providerType.name)
                .put("providerDisplayName", source.providerDisplayName)
                .put("searchCategory", source.searchCategory)
                .put("queryUsed", source.queryUsed)
                .put("rank", source.rank)
                .put("score", source.score)
                .put("qualityHints", JSONArray(source.qualityHints))
        )
    }
    return visibleAnswer.trimEnd() + "\n\n$SearchSourcesStart\n${array}\n$SearchSourcesEnd"
}

fun stripSearchSourcesMetadata(content: String): String {
    return Regex("""(?s)\Q$SearchSourcesStart\E.*?\Q$SearchSourcesEnd\E""").replace(content, "").trimEnd()
}

fun parseSearchSourcesMetadata(content: String): List<WebSearchSource> {
    val match = Regex("""(?s)\Q$SearchSourcesStart\E(.*?)\Q$SearchSourcesEnd\E""").find(content) ?: return emptyList()
    return runCatching {
        val array = JSONArray(match.groupValues[1].trim())
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val hints = obj.optJSONArray("qualityHints")
                add(
                    WebSearchSource(
                        title = obj.optString("title"),
                        source = obj.optString("source").takeIf { it.isNotBlank() },
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        snippet = obj.optString("snippet").takeIf { it.isNotBlank() },
                        publishedAt = obj.optString("publishedAt").takeIf { it.isNotBlank() },
                        providerType = runCatching { WebSearchProviderType.valueOf(obj.optString("providerType")) }.getOrDefault(WebSearchProviderType.FREE_DEFAULT),
                        providerDisplayName = obj.optString("providerDisplayName").ifBlank { "무료 기본 검색" },
                        searchCategory = obj.optString("searchCategory").takeIf { it.isNotBlank() },
                        queryUsed = obj.optString("queryUsed"),
                        rank = obj.optInt("rank", i + 1),
                        score = if (obj.has("score") && !obj.isNull("score")) obj.optDouble("score") else null,
                        qualityHints = if (hints == null) emptyList() else (0 until hints.length()).mapNotNull { index -> hints.optString(index).takeIf { it.isNotBlank() } }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
