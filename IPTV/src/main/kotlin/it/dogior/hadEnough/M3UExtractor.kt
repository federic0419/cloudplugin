package com.m3uplugin

import java.net.URL

class M3UExtractor {

    fun parseM3U(url: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        try {
            val lines = URL(url).readText().split("\n")
            var currentTitle = ""
            var currentLogo = ""

            for (line in lines) {
                when {
                    line.startsWith("#EXTINF") -> {
                        val parts = line.split(",")
                        currentTitle = parts.last().trim()
                        val logoMatch = Regex("tvg-logo=\"(.*?)\"").find(line)
                        currentLogo = logoMatch?.groups?.get(1)?.value ?: ""
                    }
                    line.startsWith("http") -> {
                        channels.add(Channel(currentTitle, line.trim(), currentLogo))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }
}

data class Channel(val name: String, val url: String, val logo: String)
