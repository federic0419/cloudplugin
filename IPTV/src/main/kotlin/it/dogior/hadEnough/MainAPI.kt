package com.m3uplugin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class MainAPI : MainAPI() {

    override var name = "M3U Reader"
    override var mainUrl = "https://raw.githubusercontent.com/federic0419/mytv/main/channels.m3u"
    override var hasMainPage = false

    private val extractor = M3UExtractor()

    override suspend fun load(url: String): List<SearchResponse> {
        val channels = extractor.parseM3U(url)
        return channels.map {
            newMovieSearchResponse(it.name) {
                this.posterUrl = it.logo
                this.url = it.url
            }
        }
    }
}
