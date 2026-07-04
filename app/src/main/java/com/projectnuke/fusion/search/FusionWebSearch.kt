package com.projectnuke.fusion.search

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class FusionSearchResult(
    val title: String,
    val source: String?,
    val url: String?,
    val snippet: String?,
    val publishedAt: String?
)

data class FusionSearchResponse(
    val query: String,
    val normalizedQuery: String,
    val intent: SearchIntent,
    val results: List<FusionSearchResult>,
    val debugMessage: String?
)

enum class SearchIntent {
    NEWS,
    STOCK,
    WEATHER,
    CURRENT_INFO,
    GENERAL
}

object FusionWebSearch {
    fun shouldAutoUseWebSearch(userInput: String): Boolean {
        val normalized = userInput.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false

        return detectIntent(userInput) != SearchIntent.GENERAL ||
            listOf("current info", "stock price", "news", "검색", "찾아줘").any {
                normalized.contains(it)
            }
    }

    fun detectIntent(query: String): SearchIntent {
        val normalized = query.trim().lowercase(Locale.ROOT)

        val newsKeywords = listOf(
            "오늘 주요 뉴스",
            "오늘 뉴스",
            "최신 뉴스",
            "뉴스 정리",
            "몇 개 정리",
            "뭐뭐있어",
            "뉴스"
        )
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
    ): String {
        val trimmed = userInput.trim()
        val query = if (isGenericFollowUp(trimmed) && !previousUserMessage.isNullOrBlank()) {
            "${previousUserMessage.trim()} $trimmed"
        } else {
            trimmed
        }

        return when {
            isBroadNewsQuery(query) -> "대한민국 주요 뉴스 오늘"
            query.contains("삼성전자") && query.contains("주가") -> "삼성전자 005930 주가 오늘 네이버 금융"
            query.contains("엑시노스") && query.contains("최신") -> "엑시노스 최신 뉴스 삼성"
            else -> query
        }
    }

    suspend fun search(
        userInput: String,
        previousUserMessage: String?
    ): FusionSearchResponse {
        val intent = detectIntent(userInput)
        val normalizedQuery = rewriteQuery(userInput, previousUserMessage)

        return withContext(Dispatchers.IO) {
            val results = when (intent) {
                SearchIntent.NEWS -> searchGoogleNews(normalizedQuery)
                    .ifEmpty { searchDuckDuckGoHtml(normalizedQuery) }
                    .ifEmpty { searchDuckDuckGoInstant(normalizedQuery) }

                SearchIntent.STOCK,
                SearchIntent.WEATHER,
                SearchIntent.CURRENT_INFO,
                SearchIntent.GENERAL -> searchDuckDuckGoHtml(normalizedQuery)
                    .ifEmpty {
                        if (intent == SearchIntent.CURRENT_INFO || intent == SearchIntent.GENERAL) {
                            searchGoogleNews(normalizedQuery)
                        } else {
                            emptyList()
                        }
                    }
                    .ifEmpty { searchDuckDuckGoInstant(normalizedQuery) }
            }

            FusionSearchResponse(
                query = userInput,
                normalizedQuery = normalizedQuery,
                intent = intent,
                results = results.distinctBy { it.title.normalizedTitleKey() }.take(if (intent == SearchIntent.NEWS) 10 else 8),
                debugMessage = if (results.isEmpty()) "No search results were returned." else null
            )
        }
    }

    private fun isGenericFollowUp(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return listOf(
            "검색",
            "검색해줘",
            "검색해서 알려줘",
            "찾아줘",
            "찾아서 알려줘",
            "웹 검색",
            "웹검색",
            "알아봐"
        ).any { normalized == it }
    }

    private fun isBroadNewsQuery(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return listOf("오늘 주요 뉴스", "오늘 뉴스", "최신 뉴스", "뉴스 정리", "몇 개 정리", "뭐뭐있어")
            .any { normalized.contains(it) }
    }

    private fun searchGoogleNews(query: String): List<FusionSearchResult> {
        val broadNews = isBroadNewsQuery(query) || query == "대한민국 주요 뉴스 오늘"
        val url = if (broadNews) {
            "https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko"
        } else {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            "https://news.google.com/rss/search?q=$encodedQuery&hl=ko&gl=KR&ceid=KR:ko"
        }

        return runCatching {
            val xml = openUrlText(url)
            parseGoogleNewsRss(xml)
        }.getOrElse { emptyList() }
    }

    private fun parseGoogleNewsRss(xml: String): List<FusionSearchResult> {
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
                            publishedAt = pubDate?.ifBlank { null }
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
        return runCatching {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val jsonText = openUrlText(
                "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            )
            val json = JSONObject(jsonText)
            val results = mutableListOf<FusionSearchResult>()

            val heading = json.optString("Heading")
            val abstractText = json.optString("AbstractText")
            val abstractUrl = json.optString("AbstractURL")
            val answer = json.optString("Answer")
            val definition = json.optString("Definition")

            if (answer.isNotBlank()) {
                results.add(FusionSearchResult(heading.ifBlank { query }, "DuckDuckGo", abstractUrl.ifBlank { null }, answer, null))
            }
            if (definition.isNotBlank()) {
                results.add(FusionSearchResult(heading.ifBlank { query }, "DuckDuckGo", abstractUrl.ifBlank { null }, definition, null))
            }
            if (abstractText.isNotBlank()) {
                results.add(FusionSearchResult(heading.ifBlank { query }, "DuckDuckGo", abstractUrl.ifBlank { null }, abstractText, null))
            }

            val relatedTopics = json.optJSONArray("RelatedTopics")
            if (relatedTopics != null) {
                var index = 0
                while (index < relatedTopics.length() && results.size < 8) {
                    val item = relatedTopics.optJSONObject(index)
                    val text = item?.optString("Text").orEmpty()
                    val url = item?.optString("FirstURL").orEmpty()
                    if (text.isNotBlank()) {
                        results.add(FusionSearchResult(text.take(90), sourceFromUrl(url), url.ifBlank { null }, text, null))
                    }
                    index += 1
                }
            }

            results
        }.getOrElse { emptyList() }
    }

    private fun searchDuckDuckGoHtml(query: String): List<FusionSearchResult> {
        return runCatching {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val html = openUrlText("https://html.duckduckgo.com/html/?q=$encodedQuery")
            val resultRegex = Regex("""(?s)<div class="result results_links.*?</div>\s*</div>""")
            val titleRegex = Regex("""(?s)<a rel="nofollow" class="result__a" href="(.*?)">(.*?)</a>""")
            val snippetRegex = Regex("""(?s)<a class="result__snippet".*?>(.*?)</a>|<div class="result__snippet".*?>(.*?)</div>""")

            resultRegex.findAll(html)
                .mapNotNull { blockMatch ->
                    val block = blockMatch.value
                    val titleMatch = titleRegex.find(block) ?: return@mapNotNull null
                    val rawUrl = titleMatch.groupValues[1]
                    val url = cleanDuckDuckGoUrl(rawUrl)
                    val title = cleanHtmlText(titleMatch.groupValues[2])
                    val snippetMatch = snippetRegex.find(block)
                    val snippet = snippetMatch?.groupValues
                        ?.drop(1)
                        ?.firstOrNull { it.isNotBlank() }
                        ?.let(::cleanHtmlText)

                    if (title.isBlank()) {
                        null
                    } else {
                        FusionSearchResult(
                            title = title,
                            source = sourceFromUrl(url),
                            url = url.ifBlank { null },
                            snippet = snippet?.ifBlank { null },
                            publishedAt = null
                        )
                    }
                }
                .distinctBy { it.title.normalizedTitleKey() }
                .take(8)
                .toList()
        }.getOrElse { emptyList() }
    }

    private fun openUrlText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android; Fusion) AppleWebKit/537.36 Chrome Mobile Safari/537.36"
            )
        }

        return try {
            val responseBytes = connection.inputStream.use { it.readBytes() }
            val responseCharset = connection.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(";")
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }
                ?.let { charsetName ->
                    runCatching { Charset.forName(charsetName) }.getOrNull()
                }
                ?: StandardCharsets.UTF_8
            String(responseBytes, responseCharset)
        } finally {
            connection.disconnect()
        }
    }

    private fun XmlPullParser.nextTextClean(): String {
        return runCatching { nextText() }.getOrDefault("").trim()
    }

    private fun cleanHtmlText(raw: String): String {
        return raw
            .replace(Regex("<.*?>"), " ")
            .replace(Regex("&#(x?[0-9A-Fa-f]+);")) { match ->
                decodeHtmlEntity(match.groupValues[1]) ?: match.value
            }
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
            if (value.startsWith("x", ignoreCase = true)) {
                value.substring(1).toInt(16)
            } else {
                value.toInt(10)
            }
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
        return runCatching {
            URL(value).host
                .removePrefix("www.")
                .ifBlank { null }
        }.getOrNull()
    }

    private fun String.normalizedTitleKey(): String {
        return lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
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
            appendLine("   Source: ${result.source.orEmpty()}")
            appendLine("   Published: ${result.publishedAt.orEmpty()}")
            appendLine("   URL: ${result.url.orEmpty()}")
            appendLine("   Snippet: ${result.snippet.orEmpty()}")
            appendLine()
        }
    }.trim()
}
