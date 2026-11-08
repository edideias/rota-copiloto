package br.rota.copiloto

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var logView: TextView

    private lateinit var fGreenKm: EditText
    private lateinit var fYellowKm: EditText
    private lateinit var fGreenHour: EditText
    private lateinit var fYellowHour: EditText
    private lateinit var fCost: EditText
    private lateinit var fTop: EditText
    private lateinit var swEnabled: Switch
    private lateinit var swUber: Switch
    private lateinit var sw99: Switch

    private val bgColor = Color.parseColor("#101418")
    private val cardColor = Color.parseColor("#1B2128")
    private val textColor = Color.parseColor("#ECEFF1")
    private val mutedColor = Color.parseColor("#9AA5B1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        if (!prefs.termsAccepted) showTerms()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        logView.text = DebugLog.dump()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgColor)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(28), dp(18), dp(40))
        scroll.addView(col)

        col.addView(text("Rota Copiloto", 26f, textColor, bold = true))
        col.addView(text("Lê a oferta do Uber/99, calcula R$/km e R$/hora e mostra um painel. Só lê e calcula: nunca aceita nem recusa corrida.", 14f, mutedColor))

        // Status
        status = text("", 15f, textColor, bold = true)
        col.addView(spacer(18))
        col.addView(status)
        col.addView(button("1) Ativar leitura (Acessibilidade)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(text(
            "Na lista, toque em \"Rota Copiloto\" e ative. Se o Android disser \"configuração restrita\" (app instalado fora da Play Store): " +
                "Configurações > Apps > Rota Copiloto > menu ⋮ (canto superior) > Permitir configurações restritas. Depois volte e ative.",
            12f, mutedColor
        ))

        // Config
        col.addView(spacer(22))
        col.addView(text("Seus limites", 18f, textColor, bold = true))
        fGreenKm = field(col, "Verde a partir de (R$/km)", prefs.greenKm)
        fYellowKm = field(col, "Vermelho abaixo de (R$/km)", prefs.yellowKm)
        fGreenHour = field(col, "Verde a partir de (R$/hora)", prefs.greenHour)
        fYellowHour = field(col, "Vermelho abaixo de (R$/hora)", prefs.yellowHour)
        fCost = field(col, "Custo do carro por km (combustível + manutenção)", prefs.costPerKm)
        fTop = field(col, "Altura do painel na tela (dp, a partir do topo)", prefs.overlayTopDp)

        swEnabled = switch("Painel ligado", prefs.enabled)
        swUber = switch("Ler Uber Driver", prefs.useUber)
        sw99 = switch("Ler 99 Motorista", prefs.use99)
        col.addView(swEnabled); col.addView(swUber); col.addView(sw99)

        col.addView(button("Salvar") { save() })
        col.addView(button("2) Testar painel (oferta falsa)") {
            save()
            val s = CopilotService.instance
            if (s == null) {
                Toast.makeText(this, "Ative a leitura (passo 1) primeiro.", Toast.LENGTH_LONG).show()
            } else {
                s.showSimulation()
                Toast.makeText(this, "Saia do app: o painel aparece por 6 segundos.", Toast.LENGTH_LONG).show()
            }
        })

        // Log
        col.addView(spacer(22))
        col.addView(text("Log de depuração", 18f, textColor, bold = true))
        col.addView(text("Se o painel não aparecer numa corrida real, copie este log e mande para ajustar a leitura.", 12f, mutedColor))
        logView = text("", 11f, mutedColor)
        logView.setTextIsSelectable(true)
        logView.setBackgroundColor(cardColor)
        logView.setPadding(dp(10), dp(10), dp(10), dp(10))
        col.addView(button("Atualizar log") { logView.text = DebugLog.dump() })
        col.addView(button("Copiar log") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", DebugLog.dump()))
            Toast.makeText(this, "Log copiado.", Toast.LENGTH_SHORT).show()
        })
        col.addView(button("Limpar log") { DebugLog.clear(); logView.text = DebugLog.dump() })
        col.addView(logView)

        setContentView(scroll)
    }

    private fun refreshStatus() {
        if (isServiceEnabled()) {
            status.text = "● Leitura ATIVA"
            status.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            status.text = "● Leitura desligada"
            status.setTextColor(Color.parseColor("#FFB300"))
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.contains("$packageName/", ignoreCase = true) && enabled.contains("CopilotService")
    }

    private fun save() {
        fun v(f: EditText, def: Double) = f.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: def
        prefs.greenKm = v(fGreenKm, prefs.greenKm)
        prefs.yellowKm = v(fYellowKm, prefs.yellowKm)
        prefs.greenHour = v(fGreenHour, prefs.greenHour)
        prefs.yellowHour = v(fYellowHour, prefs.yellowHour)
        prefs.costPerKm = v(fCost, prefs.costPerKm)
        prefs.overlayTopDp = v(fTop, prefs.overlayTopDp)
        prefs.enabled = swEnabled.isChecked
        prefs.useUber = swUber.isChecked
        prefs.use99 = sw99.isChecked
        Toast.makeText(this, "Salvo.", Toast.LENGTH_SHORT).show()
    }

    private fun showTerms() {
        AlertDialog.Builder(this)
            .setTitle("Antes de começar")
            .setMessage(
                "O Rota Copiloto é uma calculadora de apoio. Ele apenas lê os valores que aparecem na tela do " +
                    "Uber Driver e do 99 Motorista e mostra uma análise. Ele não aceita, não recusa e não toca em nada.\n\n" +
                    "Não é afiliado, aprovado ou mantido pela Uber nem pela 99. Os números são estimativas; " +
                    "a decisão de aceitar a corrida é sempre sua.\n\n" +
                    "Nada sai do seu celular: não há login nem servidor."
            )
            .setCancelable(false)
            .setPositiveButton("Entendi") { _, _ -> prefs.termsAccepted = true }
            .setNegativeButton("Sair") { _, _ -> finish() }
            .show()
    }

    // ---------- helpers de interface ----------

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun spacer(h: Int) = android.view.View(this).also {
        it.layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 16f
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun switch(label: String, checked: Boolean): Switch {
        val s = Switch(this)
        s.text = label
        s.isChecked = checked
        s.setTextColor(textColor)
        s.textSize = 15f
        s.setPadding(0, dp(10), 0, dp(10))
        return s
    }

    private fun field(parent: LinearLayout, label: String, value: Double): EditText {
        parent.addView(text(label, 13f, mutedColor))
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        e.setText(String.format(Locale.US, "%s", trimNumber(value)))
        e.setTextColor(textColor)
        e.gravity = Gravity.START
        parent.addView(e)
        return e
    }

    private fun trimNumber(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else v.toString()
}
