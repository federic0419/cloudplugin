package it.dogior.hadEnough

import android.content.SharedPreferences
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class IPTV(
    override var lang: String,
    private val sharedPref: SharedPreferences?
) : MainAPI() {
    
    override var mainUrl = "https://raw.githubusercontent.com/federic0419/mytv/main/channels.m3u"
    override var name = "TV"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override var sequentialMainPage = true
    override val supportedTypes = setOf(TvType.Live)
    private var playlist: Playlist? = null

    private suspend fun getTVChannels(): List<TVChannel> {
        if (playlist == null) {
            val response = app.get(mainUrl)
            if (response.statusCode != 200) throw Exception("Errore nel download del file M3U")
            playlist = IptvPlaylistParser().parseM3U(response.text)
        }
        return playlist!!.items
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = getTVChannels()
        val sectionTitle = "Canali TV"

        val show = data.map { showData ->
            sharedPref?.edit()?.apply {
                putString(showData.url, showData.toJson())
                apply()
            }
            showData.toSearchResponse(apiName = this@TV.name)
        }

        return newHomePageResponse(
            listOf(HomePageList(sectionTitle, show, isHorizontalImages = true)), 
            false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = getTVChannels()
        return data.filter {
            it.attributes["tvg-id"]?.contains(query, ignoreCase = true) ?: false ||
            it.title?.contains(query, ignoreCase = true) ?: false
        }.map { it.toSearchResponse(apiName = this@TV.name) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val tvChannel = sharedPref?.getString(url, null)?.let { parseJson<TVChannel>(it) }
            ?: throw ErrorLoadingException("Errore nel caricamento del canale dalla cache")

        val streamUrl = tvChannel.url.toString()
        val channelName = tvChannel.title ?: tvChannel.attributes["tvg-id"].toString()
        val posterUrl = tvChannel.attributes["tvg-logo"].toString()

        return LiveStreamLoadResponse(
            channelName,
            streamUrl,
            this.name,
            url,
            posterUrl
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            ExtractorLink(
                "Free-TV",
                "Free-TV",
                data,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}

data class Playlist(
    val items: List<TVChannel> = emptyList(),
)

data class TVChannel(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
) {
    fun toSearchResponse(apiName: String): SearchResponse {
        val streamUrl = url.toString()
        val channelName = title ?: attributes["tvg-id"].toString()
        val posterUrl = attributes["tvg-logo"].toString()
        return LiveSearchResponse(
            channelName,
            streamUrl,
            apiName,
            TvType.Live,
            posterUrl,
        )
    }
}
