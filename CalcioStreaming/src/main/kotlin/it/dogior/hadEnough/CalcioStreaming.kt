package it.dogior.hadEnough

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CalcioStreaming : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://hattrick.ws"
    override var name = "CalcioStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    private val cfKiller = CloudflareKiller()

    // 📌 Carica la homepage con gli eventi live
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, interceptor = cfKiller).document
        val events = document.select("div.row").mapNotNull { it.toSearchResult() }

        return HomePageResponse(
            listOf(HomePageList("Live Matches", events)), hasNext = false
        )
    }

    // 📌 Converte un evento in un SearchResponse con tutti i link di streaming
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".details .game-name span")?.text()?.trim() ?: return null
        val links = this.select("div.details button a").map { it.attr("href") }.filter { it.isNotEmpty() }
        val posterUrl = this.selectFirst(".logos img.mascot")?.attr("src")?.let { fixUrl(it) }

        if (links.isEmpty()) return null

        return LiveSearchResponse(
            title,
            Json.encodeToString(links),  // ✅ Converte la lista in JSON
            this@CalcioStreaming.name,
            TvType.Live,
            posterUrl
        )
    }

    // 📌 Carica i dettagli di un evento e include tutti i link di streaming
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = cfKiller).document
        val title = document.selectFirst("a.game-name span")?.text() ?: "Live Streaming"
        val poster = document.selectFirst("img.mascot")?.attr("src")?.let { fixUrl(it) }
        val links = document.select("div.details button a").map {
            Episode(it.attr("href"), it.text())
        }

        return LiveStreamLoadResponse(title, url, this.name, Json.encodeToString(links), poster)
    }

    // 📌 Estrazione di TUTTI i link di streaming per un evento
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links: List<String> = Json.decodeFromString(data) // ✅ Decodifica JSON in una lista
        links.forEach { link ->
            extractVideoLinks(link, callback)
        }
        return true
    }

    // 📌 Estrae i link video effettivi dai player
    private suspend fun extractVideoLinks(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, interceptor = cfKiller).document
        document.select("button.btn-light a").forEach { button ->
            val link = button.attr("href")
            callback(
                ExtractorLink(
                    this.name,
                    button.text(),
                    fixUrl(link),
                    mainUrl,
                    quality = 0,
                    isM3u8 = link.contains(".m3u8")
                )
            )
        }
    }

    // 📌 Intercetta le richieste video per bypassare Cloudflare
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                return cfKiller.intercept(chain)
            }
        }
    }
}
