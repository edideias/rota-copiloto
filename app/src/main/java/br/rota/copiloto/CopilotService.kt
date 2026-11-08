package br.rota.copiloto

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Serviço SOMENTE LEITURA.
 * - Lê os textos da tela do Uber Driver / 99 Motorista.
 * - Calcula e desenha um painel por cima.
 * - NUNCA chama performAction, nunca faz gesto, nunca aceita ou recusa corrida.
 */
class CopilotService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CopilotService? = null

        /**
         * Só considera tela de oferta se tiver o botão da oferta (evita painel em telas de histórico).
         * 99: "Aceitar (1s)".  Uber: "Tenho interesse" (ou "Aceitar", em outras versões).
         */
        private const val REQUIRE_OFFER_BUTTON = true
        private val OFFER_BUTTON_WORDS = listOf("aceitar", "tenho interesse", "selecionar")

        private fun hasOfferButton(text: String): Boolean =
            OFFER_BUTTON_WORDS.any { text.contains(it, ignoreCase = true) }

        private const val HIDE_AFTER_MS = 2500L
    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var card: Card? = null
    private var lastRun = 0L
    private var lastSnapshot = ""
    private var lastSnapshotLog = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        DebugLog.add("Serviço conectado.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        DebugLog.seen(pkg)
        val platform = platformOf(pkg) ?: return
        if (!prefs.enabled) return
        if (platform == "Uber" && !prefs.useUber) return
        if (platform == "99" && !prefs.use99) return

        val now = SystemClock.uptimeMillis()
        if (now - lastRun < 250) return
        lastRun = now

        val root = findRoot(pkg, event)
        if (root == null) {
            diag("[$platform] pkg=$pkg: não consegui abrir a janela do app")
            return
        }

        val texts = ArrayList<String>()
        collect(root, texts, 0)
        if (texts.isEmpty()) {
            diag("[$platform] pkg=$pkg: janela aberta, mas SEM NENHUM TEXTO (o app não expõe a tela)")
            return
        }
        val joined = texts.joinToString(" | ")

        logSnapshot(platform, joined)

        if (REQUIRE_OFFER_BUTTON && !hasOfferButton(joined)) return

        val offer = OfferParser.parse(platform, joined) ?: return
        val analysis = Calc.analyze(offer, prefs)
        show(analysis, HIDE_AFTER_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hide()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hide()
        instance = null
        super.onDestroy()
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 40) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) out.add(t.trim())
        val d = node.contentDescription?.toString()
        if (!d.isNullOrBlank() && d != t) out.add(d.trim())
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    /** Aceita variações do nome do pacote (Uber Driver e 99 Motorista). */
    private fun platformOf(pkg: String): String? {
        val p = pkg.lowercase()
        return when {
            p.contains("uber") && p.contains("driver") -> "Uber"
            p.contains("99") -> "99"
            else -> null
        }
    }

    private fun findRoot(pkg: String, event: AccessibilityEvent): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it }
        try {
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName?.toString() == pkg) return r
            }
        } catch (_: Exception) {}
        return event.source
    }

    private var lastDiag = 0L
    private var lastPlainLog = 0L

    private fun diag(msg: String) {
        val t = SystemClock.uptimeMillis()
        if (t - lastDiag < 3000) return
        lastDiag = t
        DebugLog.add(msg)
    }

    private fun logSnapshot(platform: String, joined: String) {
        if (!joined.contains("R$")) {
            val t = SystemClock.uptimeMillis()
            if (t - lastPlainLog > 4000) {
                lastPlainLog = t
                DebugLog.add("[$platform] tela sem R\$ (${joined.length} caracteres): ${joined.take(200)}")
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        if (joined == lastSnapshot || now - lastSnapshotLog < 2000) return
        lastSnapshot = joined
        lastSnapshotLog = now
        val parsed = OfferParser.parse(platform, joined)
        val tag = if (parsed != null) {
            "OK valor=${fmt(parsed.value)} busca=${parsed.pickupKm}km/${parsed.pickupMin.toInt()}min " +
                "viagem=${parsed.tripKm}km/${parsed.tripMin.toInt()}min"
        } else "SEM LEITURA"
        DebugLog.add("[$platform] $tag\n$joined")
    }

    /** Mostra uma oferta de teste (botão "Testar painel"). */
    fun showSimulation() {
        val offer = Offer("Simulação", 18.50, 1.2, 4.0, 6.8, 17.0)
        show(Calc.analyze(offer, Prefs(this)), 6000L)
    }

    private fun show(a: Analysis, holdMs: Long) {
        try {
            val c = card ?: Card().also { card = it }
            c.bind(a)
            if (!c.attached) {
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = dp(prefs.overlayTopDp.toInt())
                wm.addView(c.root, lp)
                c.attached = true
            }
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, holdMs)
        } catch (e: Exception) {
            DebugLog.add("Erro ao mostrar painel: ${e.message}")
        }
    }

    private fun hide() {
        handler.removeCallbacks(hideRunnable)
        val c = card ?: return
        if (c.attached) {
            try { wm.removeView(c.root) } catch (_: Exception) {}
            c.attached = false
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fmt(v: Double): String = String.format(Locale("pt", "BR"), "R$ %.2f", v)

    /** Painel flutuante. */
    private inner class Card {
        var attached = false
        val root = LinearLayout(this@CopilotService)
        private val title = TextView(this@CopilotService)
        private val line1 = TextView(this@CopilotService)
        private val line2 = TextView(this@CopilotService)
        private val bg = GradientDrawable()

        init {
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(dp(18), dp(10), dp(18), dp(10))
            root.gravity = Gravity.CENTER_HORIZONTAL
            bg.cornerRadius = dp(16).toFloat()
            root.background = bg

            title.textSize = 13f
            title.typeface = Typeface.DEFAULT_BOLD
            line1.textSize = 22f
            line1.typeface = Typeface.DEFAULT_BOLD
            line2.textSize = 13f
            for (t in listOf(title, line1, line2)) {
                t.gravity = Gravity.CENTER
                root.addView(t)
            }
        }

        fun bind(a: Analysis) {
            val (color, textColor, label) = when (a.verdict) {
                Verdict.GREEN -> Triple(Color.parseColor("#1B8F3A"), Color.WHITE, "VALE A PENA")
                Verdict.YELLOW -> Triple(Color.parseColor("#F2B600"), Color.parseColor("#1A1A1A"), "NO LIMITE")
                Verdict.RED -> Triple(Color.parseColor("#C62828"), Color.WHITE, "NÃO COMPENSA")
            }
            bg.setColor(color)
            val loc = Locale("pt", "BR")
            title.text = label
            val hour = if (a.perHour >= 0) String.format(loc, "R$ %.0f/h", a.perHour) else "—/h"
            line1.text = String.format(loc, "R$ %.2f/km  •  ", a.perKm) + hour
            line2.text = String.format(
                loc, "Lucro est. R$ %.2f  •  %.1f km  •  %.0f min",
                a.profit, a.totalKm, a.totalMin
            )
            for (t in listOf(title, line1, line2)) t.setTextColor(textColor)
        }
    }
}
