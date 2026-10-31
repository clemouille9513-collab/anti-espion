package com.example.antiespion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var swMain: SwitchCompat
    private lateinit var swBand: SwitchCompat
    private lateinit var swGuard: SwitchCompat
    private lateinit var seek: SeekBar
    private lateinit var dimLabel: TextView

    // vrai pendant qu'on met l'interface à jour nous-mêmes (évite de déclencher les écouteurs)
    private var loading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        fun label(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 2, 0, pad / 4)
        }

        fun switch(s: String) = SwitchCompat(this).apply {
            text = s
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
        }

        root.addView(label("Écran anti-espion", 28f, true))
        root.addView(
            label(
                "Assombrit l'écran pour que seul toi, de près, puisses lire. " +
                    "Marche sur écran OLED et LCD.",
                15f
            )
        )

        swMain = switch("Activer l'anti-espion").apply { textSize = 20f }
        root.addView(swMain)

        dimLabel = label("", 16f)
        root.addView(dimLabel)
        seek = SeekBar(this).apply { max = 90 } // 0..90 => 10..100 %
        root.addView(seek)
        root.addView(
            label(
                "Android limite l'assombrissement à environ 80 % pour que les touches " +
                    "passent à travers le filtre. Pour aller plus loin : Réglages > " +
                    "Accessibilité > Luminosité très faible.",
                13f
            )
        )

        swBand = switch("Bande de lecture : seule une bande reste lisible (poignée blanche sur le bord droit)")
        root.addView(swBand)

        swGuard = switch("Cacher l'écran si un 2e visage regarde (caméra frontale)")
        root.addView(swGuard)
        root.addView(
            label(
                "La caméra n'enregistre rien : les images sont analysées en direct " +
                    "puis jetées. Le voisin doit être dans le champ de la caméra. " +
                    "Consomme plus de batterie.",
                13f
            )
        )

        // état initial
        val st = Config.load(this)
        swMain.isChecked = st.enabled && Settings.canDrawOverlays(this)
        seek.progress = st.dim - 10
        swBand.isChecked = st.band
        swGuard.isChecked = st.guard && hasCam()
        updateLabel(st.dim)
        loading = false

        swMain.setOnCheckedChangeListener { _, on ->
            if (loading) return@setOnCheckedChangeListener
            if (on && !Settings.canDrawOverlays(this)) {
                setSilently(swMain, false)
                Toast.makeText(
                    this,
                    "Autorise « Affichage par-dessus les autres applis », puis réactive.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return@setOnCheckedChangeListener
            }
            Config.setEnabled(this, on)
            if (on) askNotificationPermission()
            push()
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress + 10)
                if (fromUser) {
                    Config.setDim(this@MainActivity, progress + 10)
                    push()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        swBand.setOnCheckedChangeListener { _, on ->
            if (loading) return@setOnCheckedChangeListener
            Config.setBand(this, on)
            push()
        }

        swGuard.setOnCheckedChangeListener { _, on ->
            if (loading) return@setOnCheckedChangeListener
            if (on && !hasCam()) {
                setSilently(swGuard, false)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                return@setOnCheckedChangeListener
            }
            Config.setGuard(this, on)
            push()
        }
    }

    override fun onResume() {
        super.onResume()
        // le bouton « Désactiver » de la notification change l'état en dehors de l'appli
        val st = Config.load(this)
        val ok = st.enabled && Settings.canDrawOverlays(this)
        if (st.enabled && !ok) Config.setEnabled(this, false)
        setSilently(swMain, ok)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setSilently(swGuard, true)
            Config.setGuard(this, true)
            push()
        }
    }

    private fun hasCam() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
            )
        }
    }

    private fun setSilently(sw: SwitchCompat, value: Boolean) {
        val before = loading
        loading = true
        sw.isChecked = value
        loading = before
    }

    private fun updateLabel(dim: Int) {
        dimLabel.text = "Obscurité : $dim %"
    }

    /** Envoie les réglages au service (ou l'arrête). */
    private fun push() {
        if (Config.load(this).enabled) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PrivacyService::class.java).setAction(PrivacyService.ACTION_START)
            )
        } else {
            stopService(Intent(this, PrivacyService::class.java))
        }
    }

    companion object {
        private const val REQ_CAMERA = 10
        private const val REQ_NOTIF = 11
    }
}
