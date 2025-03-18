package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity.activity

@CloudstreamPlugin
class TVPlugin : Plugin() {
    
    override fun load(context: Context) {
        val sharedPref = activity?.getSharedPreferences("IPTV", Context.MODE_PRIVATE)

        // Registra la MainAPI con la tua playlist su GitHub
        registerMainAPI(IPTV("it", sharedPref))

        val activity = context as AppCompatActivity
        openSettings = {
            // Rimuoviamo il menu delle playlist, perché usiamo solo una lista
            showToast(activity, "Questo plugin usa solo la playlist: channels.m3u")
        }
    }
}
