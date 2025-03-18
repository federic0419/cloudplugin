package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class IPTV : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://raw.githubusercontent.com/federic0419/mytv/main/channels.m3u"
    override var name = "Free-TV"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return HomePageResponse(data.items.groupBy { it.attributes["group-title"] }.map { group ->
            val title = group.key ?: "Altri Canali"
            val channels = group.value.map { channel ->
                LiveSearchResponse(
                    channel.attributes["tvg-name"] ?: "Sconosciuto",
                    LoadData(
                        channel.url ?: "",
                        channel.attributes["tvg-name"] ?: "Sconosciuto",
                        channel.attributes["tvg-logo"] ?: "",
                        channel.attributes["group-title"] ?: "Sconosciuto"
                    ).toJson(),
                    this@IPTV.name,
                    TvType.Live,
                    channel.attributes["tvg-logo"]
                )
            }
            HomePageList(title, channels, isHorizontalImages = true)
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        return data.items.filter { it.attributes["tvg-name"]?.contains(query, ignoreCase = true) ?: false }
            .map { channel ->
                LiveSearchResponse(
                    channel.attributes["tvg-name"] ?: "Sconosciuto",
                    LoadData(
                        channel.url ?: "",
                        channel.attributes["tvg-name"] ?: "Sconosciuto",
                        channel.attributes["tvg-logo"] ?: "",
                        channel.attributes["group-title"] ?: "Sconosciuto"
                    ).toJson(),
                    this@IPTV.name,
                    TvType.Live,
                    channel.attributes["tvg-logo"]
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

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String
    )

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
