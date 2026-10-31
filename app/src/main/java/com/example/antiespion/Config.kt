package com.example.antiespion

import android.content.Context

/** Réglages de l'appli, partagés entre l'écran de réglage et le service. */
object Config {

    data class State(
        val enabled: Boolean,
        val dim: Int,        // 10..100 : force du filtre sombre
        val band: Boolean,   // bande de lecture
        val guard: Boolean   // détection d'un 2e visage
    )

    private fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun load(c: Context): State {
        val p = prefs(c)
        return State(
            enabled = p.getBoolean("enabled", false),
            dim = p.getInt("dim", 60),
            band = p.getBoolean("band", false),
            guard = p.getBoolean("guard", false)
        )
    }

    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("enabled", v).apply()
    fun setDim(c: Context, v: Int) = prefs(c).edit().putInt("dim", v).apply()
    fun setBand(c: Context, v: Boolean) = prefs(c).edit().putBoolean("band", v).apply()
    fun setGuard(c: Context, v: Boolean) = prefs(c).edit().putBoolean("guard", v).apply()
}
