package it.dogior.hadEnough

import java.io.InputStream

class IptvPlaylistParser {
    
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()
        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems: MutableList<TVChannel> = mutableListOf()
        var currentChannel: TVChannel? = null

        reader.forEachLine { line ->
            when {
                line.startsWith(EXT_INF) -> {
                    val title = line.getTitle() ?: "Canale Sconosciuto"
                    val attributes = line.getAttributes()
                    currentChannel = TVChannel(title, attributes)
                }
                line.startsWith("#EXTVLCOPT") -> {
                    val userAgent = line.getTagValue("http-user-agent")
                    val referrer = line.getTagValue("http-referrer")
                    currentChannel = currentChannel?.copy(
                        userAgent = userAgent,
                        headers = currentChannel.headers + mapOf("referrer" to (referrer ?: ""))
                    )
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    currentChannel = currentChannel?.copy(url = line.getUrl())
                    if (currentChannel?.url != null) {
                        playlistItems.add(currentChannel)
                    }
                }
            }
        }

        return Playlist(playlistItems)
    }

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrlParameter(key: String): String? {
        val keyRegex = Regex("$key=([^&]*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").firstOrNull() ?: ""
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
        }.toMap()
    }

    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
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
        val posterUrl = attributes["tvg-logo"] ?: "https://via.placeholder.com/150"
        val group = attributes["group-title"] ?: "Senza Categoria"
        return LiveSearchResponse(
            "$group - $channelName",
            streamUrl,
            apiName,
            TvType.Live,
            posterUrl,
        )
    }
}
