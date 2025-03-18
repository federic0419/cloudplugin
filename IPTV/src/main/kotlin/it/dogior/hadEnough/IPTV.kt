package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.io.InputStream

class FreeTVProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://raw.githubusercontent.com/federic0419/mytv/main/channels.m3u"
    override var name = "Free-TV"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return HomePageResponse(data.items.groupBy { it.attributes["group-title"] }.map { group ->
            val title = group.key ?: ""
            val channels = group.value.map { channel ->
                val streamUrl = channel.url ?: ""
                val channelName = channel.attributes["tvg-name"] ?: ""
                val posterUrl = channel.attributes["tvg-logo"] ?: ""
                val nation = channel.attributes["group-title"] ?: ""
                
                LiveSearchResponse(
                    channelName,
                    LoadData(streamUrl, channelName, posterUrl, nation).toJson(),
                    this@FreeTVProvider.name,
                    TvType.Live,
                    posterUrl,
                    lang = nation
                )
            }
            HomePageList(title, channels, isHorizontalImages = true)
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)

        return data.items.filter { it.attributes["tvg-name"]?.contains(query, ignoreCase = true) ?: false }
            .map { channel ->
                val streamUrl = channel.url ?: ""
                val channelName = channel.attributes["tvg-name"] ?: ""
                val posterUrl = channel.attributes["tvg-logo"] ?: ""
                val nation = channel.attributes["group-title"] ?: ""
                
                LiveSearchResponse(
                    channelName,
                    LoadData(streamUrl, channelName, posterUrl, nation).toJson(),
                    this@FreeTVProvider.name,
                    TvType.Live,
                    posterUrl
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return LiveStreamLoadResponse(
            data.title,
            data.url,
            this.name,
            url,
            data.poster,
            plot = data.nation
        )
    }

    data class LoadData(val url: String, val title: String, val poster: String, val nation: String)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        callback.invoke(
            ExtractorLink(
                this.name,
                loadData.title,
                loadData.url,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}

// Parser per liste M3U personalizzate
class IptvPlaylistParser {

    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (!reader.readLine().startsWith("#EXTM3U")) throw PlaylistParserException.InvalidHeader()

        val playlistItems = mutableListOf<PlaylistItem>()
        var currentAttributes: Map<String, String> = emptyMap()
        var currentTitle: String? = null

        reader.forEachLine { line ->
            when {
                line.startsWith("#EXTINF") -> {
                    currentAttributes = extractAttributes(line)
                    currentTitle = line.substringAfter(",").trim()
                }
                line.startsWith("#") -> {} // Ignora altri commenti
                line.isNotBlank() -> {
                    playlistItems.add(PlaylistItem(currentTitle, currentAttributes, url = line.trim()))
                    currentAttributes = emptyMap()
                    currentTitle = null
                }
            }
        }
        return Playlist(playlistItems)
    }

    private fun extractAttributes(line: String): Map<String, String> {
        val attributesRegex = Regex("""(\w+)="(.*?)"""")
        return attributesRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
    }

    sealed class PlaylistParserException(message: String) : Exception(message) {
        class InvalidHeader : PlaylistParserException("Il file non inizia con #EXTM3U")
    }
}

data class Playlist(val items: List<PlaylistItem> = emptyList())

data class PlaylistItem(
    val title: String?,
    val attributes: Map<String, String>,
    val url: String? = null
)
