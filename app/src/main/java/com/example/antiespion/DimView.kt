package com.example.antiespion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Couche sombre dessinée par-dessus tout l'écran.
 * - dim : force du filtre (10..100)
 * - band : si vrai, une bande reste plus claire autour de bandCenter
 * - intruder : si vrai, tout l'écran devient noir
 */
class DimView(context: Context) : View(context) {

    private val paint = Paint()
    private val halfBand = 64f * resources.displayMetrics.density

    var dim: Int = 60
        set(value) {
            field = value
            invalidate()
        }

    var band: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var bandCenter: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var intruder: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (intruder) {
            canvas.drawColor(Color.BLACK)
            return
        }

        val strong = 255 * dim / 100
        paint.color = Color.argb(strong, 0, 0, 0)

        if (!band) {
            canvas.drawRect(0f, 0f, w, h, paint)
            return
        }

        val top = (bandCenter - halfBand).coerceIn(0f, h)
        val bottom = (bandCenter + halfBand).coerceIn(0f, h)
        canvas.drawRect(0f, 0f, w, top, paint)
        canvas.drawRect(0f, bottom, w, h, paint)

        // dans la bande : beaucoup moins sombre pour pouvoir lire
        paint.color = Color.argb(strong / 4, 0, 0, 0)
        canvas.drawRect(0f, top, w, bottom, paint)
    }
}
