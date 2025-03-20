package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.FormBody
import org.jsoup.nodes.Element

class AltadefinizioneProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://altadefinizione.berlin"  // ⚠️ AGGIORNA L'URL SE CAMBIA!
    override var name = "Altadefinizione"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    private val interceptor = CloudflareKiller()
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/ultimi-film/" to "Ultimi Film",
        "$mainUrl/film-hd/" to "Film in HD",
        "$mainUrl/film-al-cinema/" to "Film al Cinema"
    )

    // 🏠 HomePage: Recupera i film dalle sezioni principali
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val soup = app.get(url, interceptor = interceptor).document
        val home = soup.select("div.moviefilm").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    // 🔍 Funzione per il parsing dei risultati di ricerca
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text()?.trim() ?: return null
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val image = fixUrlNull(this.selectFirst("img")?.attr("data-src")?.trim())

        return newMovieSearchResponse(title, link, TvType.Movie) {
            this.posterUrl = image
        }
    }

    // 🔎 Ricerca film
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val doc = app.get(searchUrl, interceptor = interceptor).document

        return doc.select("div.moviefilm").mapNotNull {
            it.toSearchResult()
        }
    }

    // 📄 Caricamento pagina film (dettagli)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: throw ErrorLoadingException("Nessun titolo trovato")
        val description = document.selectFirst("div.description")?.text()?.trim() ?: "Trama non disponibile"
        val year = document.select("ul.info > li").find { it.text().contains("Anno") }?.text()?.replace("Anno:", "")?.trim()?.toIntOrNull()
        val poster = fixUrlNull(document.selectFirst("div.cover img")?.attr("data-src"))
        val recommendations = document.select("div.moviefilm").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.year = year
            this.plot = description
            this.recommendations = recommendations
            this.posterUrl = poster
        }
    }

    // 🔗 Estrazione link streaming
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, interceptor = interceptor).document
        val videoUrl = doc.select("iframe[src*='player']").attr("src")

        if (videoUrl.isNotEmpty()) {
            loadExtractor(videoUrl, data, subtitleCallback, callback)
        } else {
            doc.select("a.watchbutton").forEach {
                val link = it.attr("href")
                loadExtractor(fixUrl(link), data, subtitleCallback, callback)
            }
        }
        return true
    }
}
