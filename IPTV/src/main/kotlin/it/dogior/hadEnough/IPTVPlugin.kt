package com.lagradost

import com.lagradost.cloudstream3.plugin.Plugin
import com.lagradost.cloudstream3.plugin.PluginManager

class IPTVPlugin : Plugin() {
    override fun load() {
        PluginManager.registerAPI(IPTV())
    }
}
