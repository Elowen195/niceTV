package com.elowen.niceTV.data.scraper

import com.elowen.niceTV.data.model.HomeSection
import com.elowen.niceTV.data.model.Post
import com.elowen.niceTV.data.model.TagItem
import com.elowen.niceTV.data.model.VideoDetail
import com.elowen.niceTV.data.model.VideoSource
import com.elowen.niceTV.data.network.HttpClient
import com.elowen.niceTV.utils.JsUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class HtmlScraper() {

    private val BASE_LK_URL = "https://lk1.supremejav.com/supjav.php?c="
    private val BASE_LK_INIT_URL = "https://lk1.supremejav.com/supjav.php?l="

    private val SERVER_BG = mapOf(
        "TV" to 1, "FST" to 2, "ST" to 3, "VOE" to 4
    )

    private data class PlayerPage(
        val html: String,
        val referer: String
    )

    private data class ResolvedMediaUrl(
        val url: String,
        val qualityLabel: String? = null,
        val bandwidth: Int? = null
    )

    /**
     * 解析首页的所有板块 (Popular, Latest, etc.)
     */
    suspend fun fetchHomeSections(url: String): List<HomeSection> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        val html = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")
            response.body.string()
        }
        val doc = Jsoup.parse(html)
        val sections = mutableListOf<HomeSection>()
        // 1. 找到所有的板块标题栏
        val titleElements = doc.select(".archive-title")

        for (titleEl in titleElements) {
            val title = titleEl.selectFirst("h1")?.text() ?: "未知板块"
            val moreLink = normalizeUrl(titleEl.selectFirst("a")?.attr("href") ?: "")

            // 2. 找到紧随其后的 .posts.clearfix 兄弟节点
            val postsContainer = titleEl.nextElementSibling()
            if (postsContainer != null && postsContainer.hasClass("posts")) {
                val posts = parsePostsFromElement(postsContainer)
                sections.add(HomeSection(title, posts, moreLink))
            }
        }
        return@withContext sections
    }

    /**
     * 从指定的 HTML 元素中提取 Post 列表
     */
    private fun parsePostsFromElement(container: Element): List<Post> {
        val posts = mutableListOf<Post>()
        val postElements = container.select(".post")
        for (element in postElements) {
            val title = element.select("h3 a").text()
            var link = element.select("h3 a").attr("href")
            var img = element.select("img").let { imgEl ->
                val src = imgEl.attr("src")
                val dataSrc = imgEl.attr("data-src")
                val dataOriginal = imgEl.attr("data-original")
                val dataLazySrc = imgEl.attr("data-lazy-src")

                when {
                    dataOriginal.isNotEmpty() -> dataOriginal
                    dataSrc.isNotEmpty() -> dataSrc
                    dataLazySrc.isNotEmpty() -> dataLazySrc
                    else -> src
                }
            }

            // 处理相对地址
            if (img.isNotEmpty() && !img.startsWith("http", ignoreCase = true) && !img.startsWith("data:", ignoreCase = true)) {
                img = resolveAgainst("https://supjav.com/", img)
            }
            if (link.isNotEmpty() && !link.startsWith("http", ignoreCase = true)) {
                link = normalizeUrl(link)
            }

            if (title.isNotEmpty()) {
                posts.add(Post(title = title, imageUrl = img, link = link))
            }
        }
        return posts
    }

    suspend fun fetchPosts(url: String): Pair<List<Post>, Int> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()
        val html = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")
            response.body.string()
        }
        val doc = Jsoup.parse(html)
        val posts = parsePostsFromDoc(doc)
        val totalPages = extractTotalPages(doc)
        return@withContext Pair(posts, totalPages)
    }

    private fun parsePostsFromDoc(doc: org.jsoup.nodes.Document): List<Post> {
        val postsContainer = doc.selectFirst(".posts")
        return if (postsContainer != null) {
            parsePostsFromElement(postsContainer)
        } else {
            // 备选方案：直接查找所有 .post
            parsePostsFromElement(doc)
        }
    }

    private fun extractTotalPages(doc: org.jsoup.nodes.Document): Int {
        try {
            val pagination = doc.selectFirst("div.pagination") ?: return 1
            // 方案1: 查找 ul li a
            val pageLinks = pagination.select("ul li:not(.next-page) a")
            if (pageLinks.isNotEmpty()) {
                val lastPageText = pageLinks.last()?.text()?.trim() ?: ""
                return lastPageText.toIntOrNull() ?: 1
            }
            // 方案2: 查找所有 a 标签中的页码模式 /page/(\d+)
            val links = pagination.select("a")
            val pagePattern = java.util.regex.Pattern.compile("/page/(\\d+)")
            var maxPage = 1
            for (link in links) {
                val href = link.attr("href")
                val matcher = pagePattern.matcher(href)
                if (matcher.find()) {
                    val p = matcher.group(1)?.toIntOrNull() ?: 1
                    if (p > maxPage) maxPage = p
                }
            }
            return maxPage
        } catch (_: Exception) {
            return 1
        }
    }

    /**
     * 第一阶段：解析页面元数据（立即返回）
     */
    suspend fun fetchPartialDetail(url: String): Pair<VideoDetail, org.jsoup.nodes.Document> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val html = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")
            response.body.string()
        }
        val doc = Jsoup.parse(html)
        
        // --- 1. 元数据解析 ---
        val postMeta = doc.selectFirst(".post-meta")
        if (postMeta == null) {
            val legacyDetail = fetchVideoDetailLegacy(doc, url)
            return@withContext Pair(legacyDetail, doc)
        }

        val title = postMeta.selectFirst("h2")?.text() ?: ""
        val imgElement = postMeta.selectFirst("img")
        val imageUrl = imgElement?.let {
            it.attr("src").ifEmpty { it.attr("data-src").ifEmpty { it.attr("data-original") } }
        }?.let {
            if (it.startsWith("data:", ignoreCase = true)) it else resolveAgainst(url, it)
        } ?: ""

        val cast = mutableListOf<String>()
        val castLinks = linkedMapOf<String, String>()
        var maker = ""
        var makerLink = ""
        val catsElement = postMeta.selectFirst(".cats")
        catsElement?.select("p")?.forEachIndexed { index, p ->
            if (index > 0) { 
                val spanText = p.selectFirst("span")?.text()?.lowercase() ?: ""
                if (spanText.contains("maker")) {
                    val makerAnchor = p.selectFirst("a")
                    maker = makerAnchor?.text() ?: ""
                    makerLink = normalizeUrl(makerAnchor?.attr("href") ?: "")
                } else if (spanText.contains("cast")) {
                    p.select("a").forEach { anchor ->
                        val name = anchor.text()
                        if (name.isNotBlank()) {
                            cast.add(name)
                            val link = normalizeUrl(anchor.attr("href"))
                            if (link.isNotBlank()) {
                                castLinks[name] = link
                            }
                        }
                    }
                }
            }
        }
        val tags = postMeta.select(".tags a").map { it.text() }

        // --- 2. 解析推荐板块 ---
        val recommendations = mutableListOf<HomeSection>()
        val titleElements = doc.select(".archive-title")
        for (i in 1 until titleElements.size) {
            val titleEl = titleElements[i]
            val secTitle = titleEl.selectFirst("h1")?.text() ?: ""
            val moreLink = normalizeUrl(titleEl.selectFirst("a")?.attr("href") ?: "")
            val postsContainer = titleEl.nextElementSibling()
            if (postsContainer != null && postsContainer.hasClass("posts")) {
                val posts = parsePostsFromElement(postsContainer)
                recommendations.add(HomeSection(secTitle, posts, moreLink))
            }
        }

        val detail = VideoDetail(
            title = title,
            postLink = url,
            imageUrl = imageUrl,
            videoUrl = null, // 初始为空
            maker = maker,
            makerLink = makerLink,
            cast = cast,
            castLinks = castLinks,
            tags = tags,
            recommendations = recommendations
        )
        Pair(detail, doc)
    }

    private val ALL_SERVERS = listOf("FST", "TV", "ST", "VOE")

    /**
     * 第二阶段：从文档中提取并解析所有 4 个服务器的视频地址（耗时操作）
     */
    suspend fun resolveVideoUrlFromDoc(doc: org.jsoup.nodes.Document): Map<String, VideoSource> = withContext(Dispatchers.IO) {
        val btnServers = doc.select(".btn-server")
        val dataLinks = linkedMapOf<String, String>()

        for (btn in btnServers) {
            val label = btn.text().trim().uppercase()
            val name = ALL_SERVERS.firstOrNull { server -> label.split(Regex("\\s+")).contains(server) }
                ?: continue
            val link = btn.attr("data-link")
            if (name in ALL_SERVERS && link.isNotEmpty()) {
                dataLinks[name] = link
            }
        }

        if (dataLinks.isEmpty()) return@withContext emptyMap()
        val serverEntries = ALL_SERVERS.mapNotNull { name ->
            dataLinks[name]?.let { link -> name to link }
        }

        return@withContext coroutineScope {
            serverEntries.map { (name, link) ->
                val bg = SERVER_BG[name] ?: 2
                async { name to resolveVideoSource(link, name, bg) }
            }.map { it.await() }
                .mapNotNull { (name, source) ->
                    source?.takeIf { it.url != null }?.let { name to it }
                }
                .toMap()
        }
    }

    /**
     * 深度解析视频地址流水线，支持 bg 参数选择服务器 (TV=1, FST=2, ST=3, VOE=4)
     */
    private suspend fun resolveVideoSource(dataLink: String, sourceType: String, bg: Int): VideoSource? = withContext(Dispatchers.IO) {
        val initUrl = "$BASE_LK_INIT_URL$dataLink&bg=$bg"
        val initRequest = Request.Builder()
            .url(initUrl)
            .header("Referer", "https://supjav.com/")
            .build()
        HttpClient.client.newCall(initRequest).execute().use { initResponse ->
            if (!initResponse.isSuccessful) {
                return@withContext null
            }
        }

        val playerPage = fetchPlayerPage(dataLink) ?: return@withContext null

        return@withContext when (sourceType) {
            "FST" -> parseFst(playerPage.html, playerPage.referer)
            "ST" -> parseSt(playerPage.html, playerPage.referer)
            "TV" -> parseTv(playerPage.html, playerPage.referer)
            "VOE" -> parseVoe(playerPage.html, playerPage.referer)
            else -> null
        }
    }

    private fun fetchPlayerPage(dataLink: String): PlayerPage? {
        val reversedDataLink = dataLink.reversed()
        val lkUrl = BASE_LK_URL + reversedDataLink
        val lkRequest = Request.Builder()
            .url(lkUrl)
            .header("Referer", "https://supjav.com/")
            .build()

        HttpClient.plainClient.newCall(lkRequest).execute().use { lkResponse ->
            if (lkResponse.code in 300..399) {
                val location = lkResponse.header("Location")?.let { resolveAgainst(lkUrl, it) }
                    ?: return null
                val playerRequest = Request.Builder()
                    .url(location)
                    .header("Referer", "https://supjav.com/")
                    .build()
                return HttpClient.client.newCall(playerRequest).execute().use { playerResponse ->
                    if (!playerResponse.isSuccessful) {
                        null
                    } else {
                        PlayerPage(playerResponse.body.string(), location)
                    }
                }
            }

            if (!lkResponse.isSuccessful) return null
            return PlayerPage(lkResponse.body.string(), lkUrl)
        }
    }

    private suspend fun parseFst(html: String, referer: String): VideoSource? {
        val m3u8s = JsUnpacker.getFullHls4UrlFromHtml(html)
        if (m3u8s.isNotEmpty() && !m3u8s[0].startsWith("Error")) {
            return buildVideoSource(m3u8s[0], referer, "FST")
        }

        parseFstLinksBlock(html)?.let { url ->
            return buildVideoSource(url, referer, "FST")
        }

        findFirstMediaUrl(html)?.let { url ->
            return buildVideoSource(url, referer, "FST")
        }

        return null
    }

    private fun parseFstLinksBlock(html: String): String? {
        val unpacked = JsUnpacker.unpackHtmlScript(html).takeUnless { it.startsWith("Error") }
        val candidates = listOfNotNull(unpacked, html)
        val linksPattern = java.util.regex.Pattern.compile(
            """var\s+links\s*=\s*\{([\s\S]*?)\};""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val pairPattern = java.util.regex.Pattern.compile(
            """['"](\w+)['"]\s*:\s*['"]([^'"]*)['"]""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )

        for (content in candidates) {
            val linksMatcher = linksPattern.matcher(content)
            if (!linksMatcher.find()) continue
            val linksBlock = linksMatcher.group(1) ?: continue
            val links = linkedMapOf<String, String>()
            val pairMatcher = pairPattern.matcher(linksBlock)
            while (pairMatcher.find()) {
                val key = pairMatcher.group(1).orEmpty()
                val value = pairMatcher.group(2).orEmpty()
                if (key.isNotBlank() && value.isNotBlank()) {
                    links[key] = value
                }
            }
            listOf("hls4", "hls2", "hls3").forEach { key ->
                links[key]?.takeIf { it.isNotBlank() }?.let { url ->
                    return if (key == "hls4" && url.startsWith("/")) {
                        "https://fc2stream.tv$url"
                    } else {
                        url
                    }
                }
            }
        }
        return null
    }

    private suspend fun parseSt(html: String, referer: String): VideoSource? {
        val regex = "document\\.getElementById\\('botlink'\\)\\.innerHTML\\s*=\\s*'([^']*)'\\s*\\+\\s*\\('([^']*)'\\)\\.substring\\((\\d+)\\)"
        val matcher = java.util.regex.Pattern.compile(regex).matcher(html)
        return if (matcher.find()) {
            val prefix = matcher.group(1) ?: ""
            val encodedPart = matcher.group(2) ?: ""
            val substringIndex = matcher.group(3)?.toInt() ?: 0
            val decodedPart = if (encodedPart.length > substringIndex) encodedPart.substring(substringIndex) else ""
            val joinedUrl = prefix + decodedPart
            var mp4Url = if (joinedUrl.contains("stream=", ignoreCase = true)) joinedUrl else "$joinedUrl&stream=1"
            mp4Url = resolveAgainst(referer, mp4Url)
            VideoSource(mp4Url, referer, "ST", qualityLabel = "MP4")
        } else null
    }

    private suspend fun parseTv(html: String, referer: String): VideoSource? {
        try {
            val doc = Jsoup.parse(html)
            val sourceEl = doc.selectFirst("video source[src]")
            if (sourceEl != null) {
                val src = resolveAgainst(referer, sourceEl.attr("src"))
                if (src.isNotEmpty()) {
                    return buildVideoSource(src, referer, "TV")
                }
            }

            val m3u8Pattern = java.util.regex.Pattern.compile(
                """['"](https?://[^'"]+\.m3u8[^'"]*)['"]""",
                java.util.regex.Pattern.CASE_INSENSITIVE
            )
            val m3u8Matcher = m3u8Pattern.matcher(html)
            if (m3u8Matcher.find()) {
                val rawUrl = m3u8Matcher.group(1) ?: ""
                if (rawUrl.isNotEmpty()) {
                    return buildVideoSource(rawUrl, referer, "TV")
                }
            }

            val mp4Pattern = java.util.regex.Pattern.compile(
                """['"](https?://[^'"]+\.mp4[^'"]*)['"]""",
                java.util.regex.Pattern.CASE_INSENSITIVE
            )
            val mp4Matcher = mp4Pattern.matcher(html)
            if (mp4Matcher.find()) {
                val url = mp4Matcher.group(1) ?: ""
                if (url.isNotEmpty()) return buildVideoSource(url, referer, "TV")
            }

            val filePattern = java.util.regex.Pattern.compile(
                """['"]?file['"]?\s*[:=]\s*['"]([^'"]+)['"]""",
                java.util.regex.Pattern.CASE_INSENSITIVE
            )
            val fileMatcher = filePattern.matcher(html)
            if (fileMatcher.find()) {
                val url = resolveAgainst(referer, fileMatcher.group(1) ?: "")
                if (url.isNotEmpty()) {
                    return buildVideoSource(url, referer, "TV")
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun parseVoe(html: String, referer: String): VideoSource? {
        try {
            findVoeRedirect(html)?.let { redirect ->
                resolveVoeRedirect(redirect, referer)?.let { return it }
            }

            val doc = Jsoup.parse(html)
            val iframe = doc.selectFirst("iframe[src]")
            val iframeSrc = iframe?.attr("src")?.let { resolveAgainst(referer, it) } ?: ""

            if (iframeSrc.isNotEmpty()) {
                val request = Request.Builder().url(iframeSrc).build()
                HttpClient.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val iframeHtml = response.body.string()

                        val m3u8Pattern = java.util.regex.Pattern.compile(
                            """['"](https?://[^'"]+\.m3u8[^'"]*)['"]""",
                            java.util.regex.Pattern.CASE_INSENSITIVE
                        )
                        val m3u8Matcher = m3u8Pattern.matcher(iframeHtml)
                        if (m3u8Matcher.find()) {
                            val url = m3u8Matcher.group(1) ?: ""
                            if (url.isNotEmpty()) return buildVideoSource(url, iframeSrc, "VOE")
                        }

                        val mp4Pattern = java.util.regex.Pattern.compile(
                            """['"](https?://[^'"]+\.mp4[^'"]*)['"]""",
                            java.util.regex.Pattern.CASE_INSENSITIVE
                        )
                        val mp4Matcher = mp4Pattern.matcher(iframeHtml)
                        if (mp4Matcher.find()) {
                            val url = mp4Matcher.group(1) ?: ""
                            if (url.isNotEmpty()) return buildVideoSource(url, iframeSrc, "VOE")
                        }

                        val filePattern = java.util.regex.Pattern.compile(
                            """['"]?file['"]?\s*[:=]\s*['"]([^'"]+)['"]""",
                            java.util.regex.Pattern.CASE_INSENSITIVE
                        )
                        val fileMatcher = filePattern.matcher(iframeHtml)
                        if (fileMatcher.find()) {
                            val url = resolveAgainst(iframeSrc, fileMatcher.group(1) ?: "")
                            if (url.isNotEmpty()) {
                                return buildVideoSource(url, iframeSrc, "VOE")
                            }
                        }
                    }
                }
            }

            findFirstMediaUrl(html)?.let { url ->
                return buildVideoSource(url, referer, "VOE")
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun resolveVoeRedirect(redirectUrl: String, referer: String): VideoSource? {
        val resolvedRedirect = resolveAgainst(referer, redirectUrl)
        val request = Request.Builder()
            .url(resolvedRedirect)
            .header("Referer", referer)
            .build()
        val redirectHtml = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }

        findFirstMediaUrl(redirectHtml)?.let { url ->
            return buildVideoSource(url, resolvedRedirect, "VOE")
        }

        val filePattern = java.util.regex.Pattern.compile(
            """['"]?file['"]?\s*[:=]\s*['"]([^'"]+)['"]""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val fileMatcher = filePattern.matcher(redirectHtml)
        if (fileMatcher.find()) {
            val resolved = resolveAgainst(resolvedRedirect, fileMatcher.group(1).orEmpty())
            if (resolved.isNotBlank()) {
                return buildVideoSource(resolved, resolvedRedirect, "VOE")
            }
        }

        return null
    }

    private fun findVoeRedirect(html: String): String? {
        val locationPattern = java.util.regex.Pattern.compile(
            """window\.location(?:\.href)?\s*=\s*['"]([^'"]+)['"]""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val locationMatcher = locationPattern.matcher(html)
        if (locationMatcher.find()) {
            return locationMatcher.group(1)
        }

        val embedPattern = java.util.regex.Pattern.compile(
            """https?://[^'"\s<>]+/e/[^'"\s<>]+""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val embedMatcher = embedPattern.matcher(html)
        return if (embedMatcher.find()) embedMatcher.group() else null
    }

    private fun findFirstMediaUrl(html: String): String? {
        val m3u8Pattern = java.util.regex.Pattern.compile(
            """https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val m3u8Matcher = m3u8Pattern.matcher(html)
        if (m3u8Matcher.find()) {
            return m3u8Matcher.group()
        }

        val mp4Pattern = java.util.regex.Pattern.compile(
            """https?://[^'"\s<>]+\.mp4[^'"\s<>]*""",
            java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val mp4Matcher = mp4Pattern.matcher(html)
        return if (mp4Matcher.find()) mp4Matcher.group() else null
    }

    private fun fetchVideoDetailLegacy(doc: org.jsoup.nodes.Document, url: String): VideoDetail {
        val title = doc.selectFirst(".post-title")?.text() ?: ""
        val imageUrl = resolveAgainst(url, doc.selectFirst(".entry-content img")?.attr("src") ?: "")
        val cast = mutableListOf<String>()
        val castLinks = linkedMapOf<String, String>()
        doc.select(".cat-links a").forEach { anchor ->
            val name = anchor.text()
            if (name.isNotBlank()) {
                cast.add(name)
                val link = normalizeUrl(anchor.attr("href"))
                if (link.isNotBlank()) {
                    castLinks[name] = link
                }
            }
        }
        val tags = doc.select(".tag-links a").map { it.text() }
        val videoUrl = resolveAgainst(
            url,
            doc.selectFirst("iframe")?.attr("src") ?: doc.selectFirst("video source")?.attr("src") ?: ""
        )
        
        return VideoDetail(
            title = title,
            postLink = url,
            imageUrl = imageUrl,
            videoUrl = videoUrl.ifBlank { null },
            videoReferer = url,
            cast = cast,
            castLinks = castLinks,
            tags = tags
        )
    }

    /**
     * 解析 m3u8 文件并获取最高画质流 (同步自 Java 版老祖逻辑)
     */
    private suspend fun buildVideoSource(rawUrl: String, referer: String, name: String): VideoSource {
        val resolved = resolveAgainst(referer, rawUrl)
        val media = resolveMediaUrl(resolved)
        return VideoSource(
            url = media.url,
            referer = referer,
            name = name,
            qualityLabel = media.qualityLabel,
            qualityBandwidth = media.bandwidth
        )
    }

    private suspend fun resolveMediaUrl(mediaUrl: String): ResolvedMediaUrl = withContext(Dispatchers.IO) {
        if (!isM3u8Url(mediaUrl)) {
            val lower = mediaUrl.substringBefore('?').lowercase()
            val label = when {
                lower.endsWith(".mp4") || mediaUrl.contains("stream=1", ignoreCase = true) -> "MP4"
                else -> null
            }
            return@withContext ResolvedMediaUrl(mediaUrl, qualityLabel = label)
        }

        try {
            val request = Request.Builder().url(mediaUrl).build()
            val content = HttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ResolvedMediaUrl(mediaUrl, qualityLabel = "HLS")
                response.body.string()
            }
            
            if (!content.contains("#EXT-X-STREAM-INF")) {
                return@withContext ResolvedMediaUrl(mediaUrl, qualityLabel = "HLS")
            }

            var maxBandwidth = 0
            var bestHeight: Int? = null
            var highestQualityFile: String? = null
            val lines = content.lines()
            
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val bandwidth = extractBandwidth(line) ?: continue
                    if (bandwidth > maxBandwidth) {
                        maxBandwidth = bandwidth
                        bestHeight = extractResolutionHeight(line)
                        highestQualityFile = lines.drop(i + 1)
                            .map { it.trim() }
                            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                    }
                }
            }

            val selectedPlaylist = highestQualityFile
            if (selectedPlaylist != null) {
                val finalUrl = resolveAgainst(mediaUrl, selectedPlaylist)
                return@withContext ResolvedMediaUrl(
                    url = finalUrl,
                    qualityLabel = qualityLabel(bestHeight, maxBandwidth),
                    bandwidth = maxBandwidth.takeIf { it > 0 }
                )
            }
        } catch (_: Exception) {
            // Log.e("HtmlScraper", "解析 m3u8 失败: ${e.message}", e)
        }
        return@withContext ResolvedMediaUrl(mediaUrl, qualityLabel = "HLS")
    }

    private fun extractBandwidth(streamInfoLine: String): Int? {
        val match = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE).find(streamInfoLine)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractResolutionHeight(streamInfoLine: String): Int? {
        val match = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE).find(streamInfoLine)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun qualityLabel(height: Int?, bandwidth: Int): String {
        return when {
            height != null && height > 0 -> "${height}p"
            bandwidth > 0 -> {
                val mbps = bandwidth / 1_000_000.0
                if (mbps >= 1.0) "%.1fMbps".format(java.util.Locale.ROOT, mbps) else "${bandwidth / 1000}Kbps"
            }
            else -> "HLS"
        }
    }

    private fun resolveAgainst(baseUrl: String, url: String): String {
        if (url.isBlank()) return ""
        return when {
            url.startsWith("http", ignoreCase = true) -> url
            url.startsWith("//") -> "https:$url"
            else -> runCatching { URI(baseUrl).resolve(url).toString() }
                .getOrElse { normalizeUrl(url) }
        }
    }

    private fun isM3u8Url(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        return lower.endsWith(".m3u8") || lower.contains(".m3u8")
    }

    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        return when {
            url.startsWith("http", ignoreCase = true) -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://supjav.com$url"
            else -> "https://supjav.com/$url"
        }
    }

    /**
     * 抓取标签页面并解析
     */
    suspend fun fetchTags(url: String): List<TagItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val html = HttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body.string()
        }
        
        val doc = Jsoup.parse(html)
        val tagItems = mutableListOf<TagItem>()
        val childDivs = doc.select("div.child")
        
        val pattern = "(.+?)\\s*\\((\\d+)\\)".toRegex()
        
        for (div in childDivs) {
            val aTag = div.selectFirst("a") ?: continue
            val href = aTag.attr("href")
            val fullText = aTag.text()
            
            var chineseName = fullText
            var count = 0
            
            val matchResult = pattern.matchEntire(fullText)
            if (matchResult != null) {
                chineseName = matchResult.groupValues[1].trim()
                count = matchResult.groupValues[2].toIntOrNull() ?: 0
            }
            
            val slug = href.trimEnd('/').substringAfterLast('/').ifEmpty { "" }
            
            if (slug.isNotEmpty()) {
                tagItems.add(TagItem(slug = slug, name = chineseName, count = count))
            }
        }
        tagItems
    }
}
