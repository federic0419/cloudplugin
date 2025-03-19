package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Document

class CalcioStreaming : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://hattrick.ws"
    override var name = "CalcioStreaming"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)
    val cfKiller = CloudflareKiller()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/").document

        val matches = document.select("div.details") // Seleziona i match
        val shows = matches.map {
            val name = it.select("div.details a.game-name span").text() // Nome partita
            val hrefs = it.select("div.details button a").map { btn -> btn.attr("href") } // Tutti i link streaming
            val posterUrl = it.select("div.logos img.mascot").attr("src") // Logo evento
        
            LiveSearchResponse(
                name,
                fixUrl(hrefs.firstOrNull() ?: ""), // Prende il primo link valido, se esiste
                this@CalcioStreaming.name,
                TvType.Live,
                fixUrl(posterUrl)
            )
        }


        return HomePageResponse(listOf(HomePageList("Live Matches", shows)))
    }



    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.select("a.game-name span").text()
        val poster = document.select("img.mascot").attr("src")

        return LiveStreamLoadResponse(
            title,
            url,
            this.name,
            url,
            fixUrl(poster)
        )
    }


    private fun getStreamUrl(document: Document) : String? {
        val scripts = document.body().select("script")
        val obfuscatedScript = scripts.findLast { it.data().contains("eval(") }
        val script = obfuscatedScript?.let { getAndUnpack(it.data()) } ?: return null

        val url = script.substringAfter("var src=\"").substringBefore("\";")
//        Log.d("CalcioStreaming", "Url: $url")
        return url
    }

    private suspend fun extractVideoLinks(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        document.select("button.btn").forEach { button ->
            var link = button.attr("data-link")
            var oldLink = link
            var videoNotFound = true
            while (videoNotFound) {
                if (link.toHttpUrlOrNull() == null) break
                val doc = app.get(link).document
                link = doc.selectFirst("iframe")?.attr("src") ?: break
                val newPage = app.get(fixUrl(link), referer = oldLink).document
                oldLink = link
                val streamUrl = getStreamUrl(newPage)
                Log.d("CalcioStreaming", "Url: $streamUrl")
                if (newPage.select("script").size >= 6 && streamUrl != null){
                    videoNotFound = false
                    callback(
                        ExtractorLink(
                            this.name,
                            button.text(),
                            streamUrl,
                            fixUrl(link),
                            quality = 0,
                            true
                        )
                    )
                }
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
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
        return true
    }


    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object :Interceptor{
            override fun intercept(chain: Interceptor.Chain): Response {
                val response = cfKiller.intercept(chain)
                return response
            }
        }
    }
}
