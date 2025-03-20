package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.FormBody
import org.jsoup.nodes.Element

class AltadefinizioneProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://altadefinizione.berlin"  // Verifica l'URL attuale
    override var name = "Altadefinizione"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/cerca/anno/2024/page/" to "Ultimi Film",
        "$mainUrl/cerca/openload-quality/HD/page/" to "Film in HD",
        "$mainUrl/cinema/page/" to "Ora al cinema"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page"
        val soup = app.get(url).document
        val home = soup.select("div.film-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".title")?.text() ?: return null
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val image = this.selectFirst("img")?.attr("src") ?: return null
        val quality = getQualityFromString(this.selectFirst(".quality")?.text())

        return newMovieSearchResponse(title, link, TvType.Movie) {
            this.posterUrl = fixUrl(image)
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = FormBody.Builder()
            .addEncoded("do", "search")
            .addEncoded("subaction", "search")
            .addEncoded("story", query)
            .build()
        
        val doc = app.post("$mainUrl/index.php", requestBody = body).document

        return doc.select("div.film-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text() ?: throw ErrorLoadingException("Nessun titolo trovato")
        val description = document.select("div.description").text()
        val rating = document.select("span.rating-value").text().toFloatOrNull() ?: 0f
        val year = document.select("ul.info li:contains(Anno)").text().replace("Anno: ", "").toIntOrNull()
        val poster = document.selectFirst("div.poster img")?.attr("src") ?: throw ErrorLoadingException("Nessun poster trovato")
        val tags = document.select("div.genres a").map { it.text() }
        val trailerUrl = document.selectFirst("iframe.trailer")?.attr("src")

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            dataUrl = "$mainUrl/iframe/${url.substringAfterLast("/")}"
        ) {
            this.year = year
            this.plot = description
            this.tags = tags
            addActors(emptyList<Actor>())  // 👈 Specifico che è una lista di attori
            addPoster(fixUrl(poster))
            addRating(rating.toInt())  // 👈 Conversione esplicita a `Int`
            addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val videoUrls = doc.select("iframe.video-player").map { it.attr("src") }

        videoUrls.forEach { videoUrl ->
            loadExtractor(videoUrl, data, subtitleCallback, callback)
        }

        return true
    }
}
