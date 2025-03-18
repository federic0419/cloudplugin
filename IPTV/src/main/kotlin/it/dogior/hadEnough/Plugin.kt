package com.m3uplugin

import com.lagradost.cloudstream3.plugin.Plugin
import com.lagradost.cloudstream3.plugin.PluginManager

class M3UPlugin : Plugin() {
    override fun load() {
        PluginManager.registerAPI(MainAPI())
    }
}
