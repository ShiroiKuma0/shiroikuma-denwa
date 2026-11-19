package org.fossify.phone.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * The SIM badge drawn large, as the card it stands for: a square card with its corner cut, the
 * contact pad outlined at the top left and the SIM's number at the bottom right, close under the pad.
 * Fill, text colour and weight are the badge's own — in the SIM dialog, those of the call's stamp.
 */
@Suppress("MagicNumber") // one drawing, in fractions of the view's size
class SimBadgeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val card = Path()
    private val pad = RectF()
    private var digit = ""

    fun setBadge(digit: Int, color: Int, textColor: Int, bold: Boolean) {
        this.digit = digit.toString()
        fill.color = color
        line.color = textColor
        text.color = textColor
        text.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.14f
        val cut = w * 0.30f

        card.rewind()
        card.moveTo(r, 0f)
        card.lineTo(w - cut, 0f)
        card.lineTo(w, cut)
        card.lineTo(w, h - r)
        card.quadTo(w, h, w - r, h)
        card.lineTo(r, h)
        card.quadTo(0f, h, 0f, h - r)
        card.lineTo(0f, r)
        card.quadTo(0f, 0f, r, 0f)
        card.close()
        canvas.drawPath(card, fill)

        // the contact pad: an outlined plate, a contact to each side and the two cuts between them
        line.strokeWidth = w * 0.03f
        pad.set(w * 0.11f, h * 0.12f, w * 0.50f, h * 0.42f)
        val padR = w * 0.07f
        canvas.drawRoundRect(pad, padR, padR, line)
        val third = pad.width() / 3f
        val upper = pad.top + pad.height() * 0.33f
        val lower = pad.top + pad.height() * 0.67f
        canvas.drawLine(pad.left + third, pad.top, pad.left + third, pad.bottom, line)
        canvas.drawLine(pad.right - third, pad.top, pad.right - third, pad.bottom, line)
        canvas.drawLine(pad.left, upper, pad.left + third, upper, line)
        canvas.drawLine(pad.left, lower, pad.left + third, lower, line)
        canvas.drawLine(pad.right - third, upper, pad.right, upper, line)
        canvas.drawLine(pad.right - third, lower, pad.right, lower, line)

        text.textSize = h * 0.66f
        canvas.drawText(digit, w * 0.90f, h * 0.93f, text)
    }
}
