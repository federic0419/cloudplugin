package it.dogior.hadEnough

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class Settings : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Crea una semplice vista con un messaggio fisso
        val textView = TextView(requireContext()).apply {
            text = "Questo plugin utilizza la playlist channels.m3u e non ha impostazioni modificabili."
            textSize = 16f
            setPadding(50, 50, 50, 50)
        }
        return textView
    }
}
