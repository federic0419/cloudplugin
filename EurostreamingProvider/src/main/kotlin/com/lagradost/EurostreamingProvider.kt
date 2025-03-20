package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class EurostreamingProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://eurostreaming.observer"
    override var name = "EuroStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    private val interceptor = CloudflareKiller()
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/serie-tv-archive/" to "Ultime serie Tv",
        "$mainUrl/animazione/" to "Ultime serie Animazione"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val soup = app.get(url, interceptor = interceptor).document
        val home = soup.select("ul.recent-posts li").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2 > a") ?: return null
        val title = titleElement.text()
        val link = titleElement.attr("href")
        val image = this.selectFirst(".post-thumb img")?.attr("src") ?: return null

        return newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
            this.posterUrl = fixUrl(image)
            this.posterHeaders = interceptor.getCookieHeaders(mainUrl).toMap()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val doc = app.get(searchUrl, interceptor = interceptor).document
        return doc.select("ul.recent-posts li").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document
        val title = document.selectFirst("h1")?.text() ?: throw ErrorLoadingException("Nessun titolo trovato")
        val poster = document.selectFirst(".post-thumb img")?.attr("src") ?: ""
        val episodeList = mutableListOf<Episode>()

        // Estrazione episodi
        document.select("div.tab-pane.fade").map { element ->
            val season = element.attr("id").filter { it.isDigit() }.toInt()
            element.select("li").map { episode ->
                val episodeLink = episode.select("div.mirrors > a").map { it.attr("data-link") }.toJson()
                val epTitle = episode.selectFirst("a")?.attr("data-title") ?: "Episodio $season"
                val epNum = episode.selectFirst("a")?.text()?.toIntOrNull() ?: 0
                episodeList.add(Episode(episodeLink, epTitle, season, epNum))
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = fixUrl(poster)
            this.posterHeaders = interceptor.getCookieHeaders(mainUrl).toMap()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        parseJson<List<String>>(data).forEach { videoUrl ->
            loadExtractor(videoUrl, data, subtitleCallback, callback)
        }
        return true
    }
}
