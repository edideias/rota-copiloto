package br.rota.copiloto

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Paleta branco + roxo. Verde/amarelo/vermelho ficam só para o semáforo. */
object C {
    val PURPLE = Color.parseColor("#6D28D9")
    val PURPLE_DARK = Color.parseColor("#4C1D95")
    val PURPLE_LIGHT = Color.parseColor("#EDE9FE")
    val BG = Color.parseColor("#F6F4FB")
    val WHITE = Color.WHITE
    val TEXT = Color.parseColor("#1E1B3A")
    val MUTED = Color.parseColor("#6B7280")
    val LINE = Color.parseColor("#E5E1F0")
    val GOOD = Color.parseColor("#16A34A")
    val MID = Color.parseColor("#F2B600")
    val BAD = Color.parseColor("#DC2626")
}

object Fonts {
    private val cache = HashMap<Int, Typeface>()

    /** 0 regular, 1 medium, 2 semibold, 3 bold (Poppins). */
    fun get(ctx: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        try {
            ctx.resources.getFont(
                when (weight) {
                    0 -> R.font.poppins_regular
                    1 -> R.font.poppins_medium
                    2 -> R.font.poppins_semibold
                    else -> R.font.poppins_bold
                }
            )
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }
}

class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density).toInt()
    fun dpf(v: Float): Float = v * density
    fun font(w: Int): Typeface = Fonts.get(ctx, w)

    fun tv(s: CharSequence, sp: Float = 14f, color: Int = C.TEXT, w: Int = 0, center: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            textSize = sp
            setTextColor(color)
            typeface = font(w)
            if (center) gravity = Gravity.CENTER
        }

    fun round(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(radius.toFloat())
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    fun vbox(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun hbox(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(): LinearLayout = vbox().apply {
        background = round(C.WHITE, 20)
        elevation = dpf(2f)
        setPadding(dp(18), dp(16), dp(18), dp(16))
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).also { it.topMargin = dp(top) }

    fun space(h: Int): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    fun button(label: String, filled: Boolean = true, onClick: () -> Unit): TextView =
        tv(label, 15f, if (filled) C.WHITE else C.PURPLE, 2, true).apply {
            background = round(if (filled) C.PURPLE else C.PURPLE_LIGHT, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { onClick() }
            layoutParams = lp(top = 10)
        }

    fun toggle(checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(ctx).apply {
        isChecked = checked
        val on = intArrayOf(android.R.attr.state_checked)
        val off = intArrayOf()
        thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(C.WHITE, C.WHITE))
        trackTintList = ColorStateList(arrayOf(on, off), intArrayOf(C.PURPLE, Color.parseColor("#D1D5DB")))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    fun checkbox(checked: Boolean, enabled: Boolean = true): TextView =
        tv(if (checked) "✓" else "", 16f, C.WHITE, 3, true).apply {
            background = if (checked) round(C.PURPLE, 8) else round(C.WHITE, 8, 2, Color.parseColor("#C9C4D8"))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            alpha = if (enabled) 1f else 0.5f
        }

    fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout =
        hbox().apply {
            background = round(C.PURPLE_LIGHT, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            options.forEachIndexed { i, label ->
                val on = i == selected
                addView(
                    tv(label, 13f, if (on) C.WHITE else C.PURPLE, if (on) 2 else 1, true).apply {
                        background = if (on) round(C.PURPLE, 11) else null
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        setOnClickListener { onSelect(i) }
                    },
                    lp(0, weight = 1f)
                )
            }
        }

    fun circleNumber(n: Int): TextView =
        tv(n.toString(), 14f, C.PURPLE, 2, true).apply {
            background = round(C.PURPLE_LIGHT, 16)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }

    /** Lista numerada de passos. */
    fun numbered(items: List<String>): LinearLayout = vbox().apply {
        items.forEachIndexed { i, s ->
            val row = hbox()
            row.addView(circleNumber(i + 1))
            row.addView(tv(s, 14f, C.TEXT, 0).apply { setPadding(dp(12), 0, 0, 0) }, lp(0, weight = 1f))
            addView(row, lp(top = 12))
        }
    }

    fun statusPill(text: String, ok: Boolean): TextView =
        tv((if (ok) "✓  " else "!  ") + text, 14f, if (ok) C.GOOD else Color.parseColor("#B45309"), 2, true).apply {
            background = round(if (ok) Color.parseColor("#E8F7EE") else Color.parseColor("#FEF3C7"), 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = lp(top = 16)
        }
}

/**
 * Barra de faixa com duas alças: abaixo da primeira = vermelho (ruim),
 * entre as duas = amarelo (média), acima da segunda = verde (bom).
 */
class RangeBar(
    ctx: Context,
    private val min: Double,
    private val max: Double,
    private val step: Double,
    private var low: Double,
    private var high: Double,
    private val fmt: (Double) -> String,
    private val onDone: (Double, Double) -> Unit
) : View(ctx) {

    private val ui = Ui(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ui.font(2)
        textSize = ui.dpf(15f)
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private var active = 0 // 1 = ruim, 2 = bom

    private val padX get() = ui.dpf(26f)
    private val trackY get() = height / 2f
    private fun xOf(v: Double): Float = padX + ((v.coerceIn(min, max) - min) / (max - min)).toFloat() * (width - 2 * padX)
    private fun vOf(x: Float): Double {
        val f = ((x - padX) / (width - 2 * padX)).coerceIn(0f, 1f)
        val raw = min + f * (max - min)
        return Math.round(raw / step) * step
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), ui.dp(130))
    }

    override fun onDraw(c: Canvas) {
        val th = ui.dpf(9f)
        val xl = xOf(low)
        val xh = xOf(high)
        val left = padX
        val right = width - padX
        paint.style = Paint.Style.FILL
        val r = th / 2
        paint.color = C.BAD
        c.drawRoundRect(RectF(left, trackY - th / 2, xl + r, trackY + th / 2), r, r, paint)
        paint.color = C.MID
        c.drawRect(xl, trackY - th / 2, xh, trackY + th / 2, paint)
        paint.color = C.GOOD
        c.drawRoundRect(RectF(xh - r, trackY - th / 2, right, trackY + th / 2), r, r, paint)
        thumb(c, xl, C.BAD)
        thumb(c, xh, C.GOOD)
        pill(c, xh, trackY - ui.dpf(48f), fmt(high), C.GOOD, true)
        pill(c, xl, trackY + ui.dpf(48f), fmt(low), C.BAD, false)
    }

    private fun thumb(c: Canvas, x: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(x, trackY, ui.dpf(13f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ui.dpf(4f)
        paint.color = color
        c.drawCircle(x, trackY, ui.dpf(11f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun pill(c: Canvas, cx: Float, cy: Float, text: String, color: Int, above: Boolean) {
        val w = textPaint.measureText(text) + ui.dpf(28f)
        val h = ui.dpf(36f)
        val x = cx.coerceIn(w / 2, width - w / 2)
        paint.color = color
        paint.style = Paint.Style.FILL
        c.drawRoundRect(RectF(x - w / 2, cy - h / 2, x + w / 2, cy + h / 2), ui.dpf(10f), ui.dpf(10f), paint)
        val ty = if (above) cy + h / 2 else cy - h / 2
        val path = android.graphics.Path()
        val tipY = if (above) ty + ui.dpf(8f) else ty - ui.dpf(8f)
        path.moveTo(cx - ui.dpf(7f), ty)
        path.lineTo(cx + ui.dpf(7f), ty)
        path.lineTo(cx, tipY)
        path.close()
        c.drawPath(path, paint)
        val fm = textPaint.fontMetrics
        c.drawText(text, x, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                active = if (Math.abs(e.x - xOf(low)) <= Math.abs(e.x - xOf(high))) 1 else 2
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onDone(low, high)
                active = 0
            }
        }
        return true
    }

    private fun move(x: Float) {
        val v = vOf(x)
        if (active == 1) low = v.coerceAtMost(high - step) else if (active == 2) high = v.coerceAtLeast(low + step)
        invalidate()
    }
}

/** Cartão que aparece por cima do Uber/99 (e na prévia das configurações). */
object OverlayCard {
    fun build(ctx: Context, a: Analysis, p: Prefs): View {
        val ui = Ui(ctx)
        val verdict = when (a.verdict) {
            Verdict.GREEN -> C.GOOD
            Verdict.YELLOW -> C.MID
            Verdict.RED -> C.BAD
        }
        val theme = p.cardTheme
        val bg = when (theme) {
            0 -> Color.WHITE
            1 -> Color.parseColor("#17142A")
            else -> verdict
        }
        val fg = when (theme) {
            0 -> C.TEXT
            1 -> Color.WHITE
            else -> if (a.verdict == Verdict.YELLOW) Color.parseColor("#1A1A1A") else Color.WHITE
        }
        val muted = if (theme == 0) C.MUTED else Color.argb(200, Color.red(fg), Color.green(fg), Color.blue(fg))
        val size = p.cardFont.toFloat()

        val outer = ui.vbox().apply {
            background = ui.round(bg, 14, if (theme == 2) 0 else 3, verdict)
            setPadding(ui.dp(14), ui.dp(8), ui.dp(16), ui.dp(8))
        }

        val row = ui.hbox()
        p.metrics.forEach { key ->
            val col = ui.vbox()
            col.addView(ui.tv(Metrics.label(key), size * 0.55f, muted, 1))
            val vr = ui.hbox()
            val bar = View(ctx).apply {
                background = ui.round(if (theme == 2) fg else Metrics.color(a, key, p), 3)
                layoutParams = LinearLayout.LayoutParams(ui.dp(4), ui.dp((size * 1.25f).toInt()))
            }
            vr.addView(bar)
            vr.addView(ui.tv(Metrics.value(a, key), size, fg, 3).apply { setPadding(ui.dp(6), 0, 0, 0) })
            col.addView(vr)
            row.addView(col, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.marginEnd = ui.dp(14)
            })
        }
        outer.addView(row)

        val totalRounded = Math.round(a.totalMin).toInt()
        val h = totalRounded / 60
        val m = totalRounded % 60
        val info = String.format(java.util.Locale("pt", "BR"), "%dh%02dm  •  %.1fkm", h, m, a.totalKm)
        val foot = ui.hbox()
        val badge = when (a.offer.platform) {
            "Uber" -> "UBER"
            "99" -> "99"
            else -> "TESTE"
        }
        foot.addView(ui.tv(badge, size * 0.5f, fg, 3).apply {
            background = ui.round(Color.TRANSPARENT, 4, 1, fg)
            setPadding(ui.dp(5), ui.dp(1), ui.dp(5), ui.dp(1))
        })
        foot.addView(ui.tv(info, size * 0.8f, fg, 2).apply { setPadding(ui.dp(8), 0, 0, 0) })
        outer.addView(foot, ui.lp(top = 4))
        return outer
    }

    /** Oferta de exemplo para a prévia e o botão de teste. */
    fun sample(p: Prefs): Analysis =
        Calc.analyze(Offer("Uber", 18.50, 1.2, 4.0, 6.8, 17.0), p)
}
