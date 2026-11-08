package br.rota.copiloto

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var ui: Ui
    private lateinit var root: FrameLayout

    private val stack = ArrayList<() -> View>()
    private var tab = 0
    private val totalSteps = 6

    // rascunho das telas de configuração (só vale depois de tocar em Salvar)
    private var draftMem: MemPrefs? = null

    private fun dp(v: Int) = ui.dp(v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        ui = Ui(this)

        window.statusBarColor = C.WHITE
        window.navigationBarColor = C.WHITE
        var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags

        root = FrameLayout(this)
        root.setBackgroundColor(C.BG)
        setContentView(root)

        stack.add { mainScreen() }
        if (savedInstanceState == null && prefs.termsAccepted && !prefs.wizardDone) {
            stack.add { wizardStep(0) }
        }
        render()
        if (!prefs.termsAccepted) showTerms()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onBackPressed() {
        if (draftMem != null) {
            leaveConfig()
        } else if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            render()
        } else super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(
            stack.last()(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
    }

    private fun push(b: () -> View) {
        stack.add(b)
        render()
    }

    private fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
        render()
    }

    // ---------------------------------------------------------------- tela inicial

    private fun mainScreen(): View {
        val col = ui.vbox()
        val head = ui.hbox().apply {
            setBackgroundColor(C.WHITE)
            setPadding(dp(20), dp(12), dp(16), dp(12))
        }
        head.addView(ui.tv("Rota", 26f, C.PURPLE, 3), ui.lp(0, weight = 1f))
        head.addView(
            ui.tv("?  AJUDA", 12f, C.PURPLE, 2, true).apply {
                background = ui.round(C.WHITE, 10, 1, C.LINE)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener { push { helpScreen() } }
            }
        )
        col.addView(head)

        val body = ui.vbox().apply { setPadding(dp(16), dp(16), dp(16), dp(24)) }
        if (tab == 0) homeBody(body) else diagBody(body)
        val sv = ScrollView(this)
        sv.addView(body)
        col.addView(sv, ui.lp(h = 0, weight = 1f))
        col.addView(bottomBar())
        return col
    }

    private fun bottomBar(): View {
        val bar = ui.hbox().apply {
            setBackgroundColor(C.WHITE)
            elevation = ui.dpf(6f)
            setPadding(0, dp(10), 0, dp(10))
        }
        listOf("Copiloto", "Diagnóstico").forEachIndexed { i, label ->
            val on = tab == i
            bar.addView(
                ui.tv(label, 14f, if (on) C.PURPLE else C.MUTED, if (on) 2 else 1, true).apply {
                    setPadding(0, dp(8), 0, dp(8))
                    setOnClickListener {
                        tab = i
                        stack[0] = { mainScreen() }
                        render()
                    }
                },
                ui.lp(0, weight = 1f)
            )
        }
        return bar
    }

    private fun homeBody(body: LinearLayout) {
        if (!isServiceEnabled()) {
            val c = ui.card()
            c.addView(ui.tv("Leitura desligada", 16f, C.TEXT, 2))
            c.addView(ui.tv("Ative o Rota Copiloto em Acessibilidade para ele ler as ofertas.", 13f, C.MUTED, 0))
            c.addView(ui.button("Ativar leitura") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            c.addView(ui.button("Como liberar (configuração restrita)", false) { push { wizardStep(0) } })
            body.addView(c, ui.lp(top = 0))
        } else {
            body.addView(ui.statusPill("Leitura ativa", true), ui.lp(top = 0))
        }

        val card = ui.card()
        card.addView(
            featureRow(
                "Copiloto", "Ative para utilizar os recursos", null, prefs.enabled,
                { openConfig("Apps de trabalho") { appsContent(it) } }
            ) { v ->
                prefs.enabled = v
                if (v && !isServiceEnabled()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        card.addView(divider())
        val chips = ui.hbox()
        chips.addView(appChip("UBER", prefs.useUber))
        chips.addView(appChip("99", prefs.use99))
        card.addView(
            featureRow(
                "Cálculo de Ganhos", null, chips, prefs.calcEnabled,
                { push { configMenu() } }
            ) { v -> prefs.calcEnabled = v }
        )
        card.addView(divider())
        card.addView(
            featureRow(
                "Captura de Tela", "Salva cada oferta na galeria", null, prefs.saveShots,
                { openConfig("Captura de tela") { captureContent(it) } }
            ) { v -> prefs.saveShots = v }
        )
        body.addView(card, ui.lp(top = 14))

        body.addView(ui.button("Configurar") { push { configMenu() } }, ui.lp(top = 16))

        val two = ui.hbox()
        two.addView(ui.button("Tutorial Guiado", false) { push { wizardStep(0) } }, ui.lp(0, top = 0, weight = 1f))
        two.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        two.addView(ui.button("Ajuda Xiaomi", false) { push { screen("Ajuda Xiaomi", xiaomiHelpContent()) } }, ui.lp(0, top = 0, weight = 1f))
        body.addView(two, ui.lp(top = 4))

        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val gb = Math.round(mi.totalMem / 1073741824.0)
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        body.addView(
            ui.tv(
                "v$ver\nAndroid ${Build.VERSION.RELEASE} · ${gb}GB RAM\n${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                12f, C.MUTED, 0, true
            ),
            ui.lp(top = 24)
        )
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(C.LINE)
        layoutParams = ui.lp(h = 1, top = 14).also { it.bottomMargin = dp(14) }
    }

    private fun appChip(label: String, on: Boolean): TextView =
        ui.tv(label, 10f, if (on) C.PURPLE else Color.parseColor("#9CA3AF"), 3, true).apply {
            background = ui.round(if (on) C.PURPLE_LIGHT else Color.parseColor("#F1F1F4"), 8)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = ui.lp(w = LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(6); it.topMargin = dp(8) }
        }

    private fun featureRow(
        title: String, sub: String?, extra: View?, checked: Boolean,
        onGear: () -> Unit, onToggle: (Boolean) -> Unit
    ): View {
        val row = ui.hbox()
        val left = ui.vbox()
        left.addView(ui.tv(title, 17f, C.TEXT, 2))
        if (sub != null) left.addView(ui.tv(sub, 13f, C.MUTED, 0))
        if (extra != null) left.addView(extra)
        row.addView(left, ui.lp(0, weight = 1f))
        val gear = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(C.PURPLE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onGear() }
        }
        row.addView(gear, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(ui.toggle(checked, onToggle))
        return row
    }

    // ---------------------------------------------------------------- diagnóstico e ajuda

    private fun diagBody(body: LinearLayout) {
        body.addView(ui.tv("Diagnóstico", 20f, C.TEXT, 3))
        body.addView(ui.tv("Se o painel não aparecer numa corrida real, copie o log e mande para ajustar a leitura.", 13f, C.MUTED, 0))

        body.addView(ui.button("Testar painel (oferta falsa)") {
            val s = CopilotService.instance
            if (s == null) {
                Toast.makeText(this, "Ative a leitura primeiro.", Toast.LENGTH_LONG).show()
            } else {
                s.showSimulation()
                Toast.makeText(this, "Saia do app: o painel aparece por 6 segundos.", Toast.LENGTH_LONG).show()
            }
        })

        val log = ui.tv(DebugLog.dump(), 11f, C.MUTED, 0).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            background = ui.round(C.WHITE, 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        body.addView(ui.button("Atualizar log", false) { render() })
        body.addView(ui.button("Copiar log", false) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", DebugLog.dump()))
            Toast.makeText(this, "Log copiado.", Toast.LENGTH_SHORT).show()
        })
        body.addView(ui.button("Limpar log", false) { DebugLog.clear(); render() })
        body.addView(log, ui.lp(top = 14))
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val r = ui.hbox()
        r.addView(ui.tv(label, 14f, C.TEXT, 1), ui.lp(0, weight = 1f))
        r.addView(ui.toggle(checked, onChange))
        return r
    }

    private fun helpScreen(): View {
        val c = ui.vbox().apply { setPadding(dp(16), dp(16), dp(16), dp(24)) }
        val a = ui.card()
        a.addView(ui.tv("O que o Rota faz", 17f, C.TEXT, 2))
        a.addView(
            ui.tv(
                "Lê a oferta que aparece no Uber Driver ou no 99 Motorista, calcula R\$/km, R\$/hora e lucro e mostra um cartão por cima. " +
                    "Só lê e calcula: nunca aceita, recusa nem toca em nada.",
                13f, C.MUTED, 0
            ),
            ui.lp(top = 6)
        )
        c.addView(a)
        val b = ui.card()
        b.addView(ui.tv("Ativar a leitura", 17f, C.TEXT, 2))
        b.addView(
            ui.tv(
                "Configurações > Acessibilidade > Rota Copiloto > ativar. Se o Android disser \"configuração restrita\": " +
                    "Configurações > Apps > Rota Copiloto > menu ⋮ (canto superior) > Permitir configurações restritas. Depois volte e ative.",
                13f, C.MUTED, 0
            ),
            ui.lp(top = 6)
        )
        b.addView(ui.button("Abrir configurações do app (liberar restritas)") { openAppDetails() })
        b.addView(ui.button("Abrir Acessibilidade", false) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        c.addView(b, ui.lp(top = 14))
        return screen("Ajuda", c)
    }

    // ---------------------------------------------------------------- moldura de telas

    private fun screen(title: String, content: View, onBack: () -> Unit = { pop() }, bottom: View? = null): View {
        val col = ui.vbox().apply { setBackgroundColor(C.BG) }
        val bar = ui.hbox().apply {
            setBackgroundColor(C.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(ui.tv("←", 24f, C.PURPLE, 2).apply {
            setPadding(0, 0, dp(16), 0)
            setOnClickListener { onBack() }
        })
        bar.addView(ui.tv(title, 18f, C.TEXT, 2))
        col.addView(bar)
        val sv = ScrollView(this)
        val wrap = ui.vbox().apply { setPadding(dp(16), dp(16), dp(16), dp(28)) }
        wrap.addView(content)
        sv.addView(wrap)
        col.addView(sv, ui.lp(h = 0, weight = 1f))
        if (bottom != null) col.addView(bottom)
        return col
    }

    private operator fun View.plus(other: View): View = ui.vbox().also {
        it.addView(this)
        it.addView(other, ui.lp(top = 16))
    }

    private fun pageHeader(title: String, subtitle: String): View {
        val h = ui.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
        h.addView(ui.tv(title, 22f, C.TEXT, 3, true))
        h.addView(ui.tv(subtitle, 14f, C.MUTED, 0, true), ui.lp(top = 8))
        return h
    }

    private fun bigIcon(glyph: String): View =
        ui.tv(glyph, 28f, C.PURPLE, 3, true).apply {
            background = ui.round(C.PURPLE_LIGHT, 36)
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        }

    // ---------------------------------------------------------------- configurar (com Salvar)

    private fun configMenu(): View {
        val box = ui.vbox()
        fun item(title: String, sub: String, onClick: () -> Unit) {
            val r = ui.hbox().apply {
                background = ui.round(C.WHITE, 18)
                elevation = ui.dpf(2f)
                setPadding(dp(18), dp(16), dp(14), dp(16))
                setOnClickListener { onClick() }
            }
            val l = ui.vbox()
            l.addView(ui.tv(title, 16f, C.TEXT, 2))
            l.addView(ui.tv(sub, 12f, C.MUTED, 0))
            r.addView(l, ui.lp(0, weight = 1f))
            r.addView(ui.tv("›", 26f, C.PURPLE, 2))
            box.addView(r, ui.lp(top = 12))
        }
        item("Apps de trabalho", "Uber, 99 e leitura por imagem") { openConfig("Apps de trabalho") { appsContent(it) } }
        item("Metas e custo", "Ganho por km, por hora e custo do carro") { openConfig("Metas e custo") { goalsContent(it) } }
        item("Aparência do quadro", "Métricas, posição, tema e fonte") { openConfig("Aparência do quadro") { cardContent(it) } }
        item("Tempo na tela", "Quanto tempo o quadro fica visível") { openConfig("Tempo na tela") { timeContent(it) } }
        item("Captura de tela", "Salvar cada oferta na galeria") { openConfig("Captura de tela") { captureContent(it) } }
        return screen("Configurar", box)
    }

    /** Abre uma tela de configuração com rascunho: nada vale até tocar em Salvar. */
    private fun openConfig(title: String, build: (Prefs) -> View) {
        val mem = MemPrefs(prefs.store.all)
        draftMem = mem
        val d = Prefs(this, mem)
        push { configScreen(title, d, build) }
    }

    private fun configScreen(title: String, d: Prefs, build: (Prefs) -> View): View {
        val bar = ui.vbox().apply {
            setBackgroundColor(C.WHITE)
            elevation = ui.dpf(6f)
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        bar.addView(ui.button("Salvar") {
            prefs.commitFrom(d.store)
            draftMem = null
            Toast.makeText(this, "Salvo.", Toast.LENGTH_SHORT).show()
            pop()
        })
        return screen(title, build(d), { leaveConfig() }, bar)
    }

    private fun leaveConfig() {
        val mem = draftMem
        val dirty = mem != null && mem.all != prefs.store.all
        if (!dirty) {
            draftMem = null
            pop()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Descartar alterações?")
            .setMessage("Você mudou algo e não tocou em Salvar.")
            .setPositiveButton("Descartar") { _, _ ->
                draftMem = null
                pop()
            }
            .setNegativeButton("Continuar editando", null)
            .show()
    }

    // ---------------------------------------------------------------- apps de trabalho

    private class AppItem(
        val name: String, val badge: String, val bg: Int, val fg: Int,
        val enabled: Boolean, val get: () -> Boolean, val set: (Boolean) -> Unit
    )

    private fun appsContent(p: Prefs): View {
        val items = listOf(
            AppItem("Uber", "UBER", Color.BLACK, Color.WHITE, true, { p.useUber }, { p.useUber = it }),
            AppItem("99", "99", Color.parseColor("#FFD400"), Color.BLACK, true, { p.use99 }, { p.use99 = it }),
            AppItem("Uber Moto", "MOTO", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {}),
            AppItem("Uber Delivery", "EATS", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {}),
            AppItem("99 Food", "FOOD", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {}),
            AppItem("InDrive", "iD", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {}),
            AppItem("iFood", "iF", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {}),
            AppItem("Keeta", "K", Color.parseColor("#9CA3AF"), Color.WHITE, false, { false }, {})
        )
        val box = ui.vbox()
        box.addView(pageHeader("Selecione seus apps de trabalho", "O Rota analisa apenas corridas dos apps que você selecionar"))
        items.chunked(3).forEachIndexed { idx, rowItems ->
            val row = ui.hbox().apply { weightSum = 3f }
            rowItems.forEach { app ->
                val cell = ui.vbox().apply {
                    background = ui.round(C.WHITE, 18)
                    elevation = ui.dpf(2f)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    alpha = if (app.enabled) 1f else 0.55f
                    if (app.enabled) setOnClickListener {
                        app.set(!app.get())
                        render()
                    }
                }
                val top = ui.hbox()
                top.addView(
                    ui.tv(app.badge, 11f, app.fg, 3, true).apply {
                        background = ui.round(app.bg, 12)
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                    }
                )
                top.addView(View(this), ui.lp(0, weight = 1f))
                top.addView(ui.checkbox(app.get(), app.enabled))
                cell.addView(top)
                cell.addView(ui.tv(app.name, 13f, C.TEXT, 2), ui.lp(top = 18))
                if (!app.enabled) cell.addView(ui.tv("em breve", 11f, C.MUTED, 0))
                row.addView(cell, ui.lp(0, weight = 1f).also { lp -> lp.setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            repeat(3 - rowItems.size) { row.addView(View(this), ui.lp(0, weight = 1f)) }
            box.addView(row, ui.lp(top = if (idx == 0) 20 else 4))
        }
        val ocr = ui.card()
        ocr.addView(switchRow("Leitura por imagem (quando o app não entrega o texto)", p.useOcr) { p.useOcr = it })
        ocr.addView(
            ui.tv(
                "Tira uma foto da tela só enquanto o Uber/99 está aberto, lê os números no próprio celular e descarta a imagem.",
                12f, C.MUTED, 0
            ),
            ui.lp(top = 6)
        )
        box.addView(ocr, ui.lp(top = 16))
        return box
    }

    // ---------------------------------------------------------------- metas

    private fun goalsContent(p: Prefs): View {
        val br = Locale("pt", "BR")
        val box = ui.vbox()
        box.addView(pageHeader("Defina suas metas por km e hora", "O Rota mostra se cada corrida é boa, média ou ruim"))

        val km = ui.card()
        km.addView(
            cardTitle("Ganho por Km") {
                editDialog(
                    "Ganho por Km",
                    listOf("Ruim abaixo de (R\$/km)", "Bom a partir de (R\$/km)"),
                    listOf(p.yellowKm, p.greenKm)
                ) { v -> p.yellowKm = v[0]; p.greenKm = v[1] }
            }
        )
        km.addView(
            RangeBar(
                this, 0.5, 4.0, 0.05, p.yellowKm, p.greenKm,
                { String.format(br, "R\$ %.2f /km", it) }
            ) { l, h -> p.yellowKm = l; p.greenKm = h }
        )
        box.addView(km, ui.lp(top = 20))

        val hr = ui.card()
        hr.addView(
            cardTitle("Ganho por Hora") {
                editDialog(
                    "Ganho por Hora",
                    listOf("Ruim abaixo de (R\$/hora)", "Bom a partir de (R\$/hora)"),
                    listOf(p.yellowHour, p.greenHour)
                ) { v -> p.yellowHour = v[0]; p.greenHour = v[1] }
            }
        )
        hr.addView(
            RangeBar(
                this, 10.0, 100.0, 1.0, p.yellowHour, p.greenHour,
                { String.format(br, "R\$ %.0f /hr", it) }
            ) { l, h -> p.yellowHour = l; p.greenHour = h }
        )
        box.addView(hr, ui.lp(top = 14))

        val cost = ui.card()
        cost.addView(
            cardTitle("Custo do carro por km") {
                editDialog(
                    "Custo do carro por km",
                    listOf("Combustível + manutenção (R\$/km)"),
                    listOf(p.costPerKm)
                ) { v -> p.costPerKm = v[0] }
            }
        )
        cost.addView(ui.tv(String.format(br, "R\$ %.2f /km", p.costPerKm), 22f, C.PURPLE, 3), ui.lp(top = 6))
        cost.addView(ui.tv("Usado para calcular o lucro de cada corrida.", 13f, C.MUTED, 0))
        box.addView(cost, ui.lp(top = 14))
        return box
    }

    private fun cardTitle(title: String, onEdit: () -> Unit): View {
        val r = ui.hbox()
        r.addView(ui.tv(title, 17f, C.TEXT, 2), ui.lp(0, weight = 1f))
        r.addView(
            ui.tv("✎", 18f, C.PURPLE, 2, true).apply {
                background = ui.round(C.PURPLE_LIGHT, 12)
                setOnClickListener { onEdit() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            }
        )
        return r
    }

    private fun editDialog(title: String, labels: List<String>, values: List<Double>, onOk: (List<Double>) -> Unit) {
        val box = ui.vbox().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val fields = labels.mapIndexed { i, l ->
            box.addView(ui.tv(l, 13f, C.MUTED, 1), ui.lp(top = 8))
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(trim(values[i]))
                typeface = ui.font(1)
                setTextColor(C.TEXT)
            }
            box.addView(e)
            e
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                onOk(fields.mapIndexed { i, e -> e.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: values[i] })
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun trim(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()

    // ---------------------------------------------------------------- cartão

    private fun cardContent(p: Prefs): View {
        val box = ui.vbox()
        box.addView(pageHeader("Personalize o Cartão", "Escolha a posição e o tema, e ordene os indicadores do seu jeito"))

        val c = ui.card()
        val prev = ui.vbox().apply {
            background = ui.round(C.BG, 14)
            setPadding(dp(14), dp(18), dp(14), dp(18))
        }
        val g = when (p.cardPos) {
            0 -> Gravity.START
            2 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        prev.addView(
            OverlayCard.build(this, OverlayCard.sample(p), p),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = g }
        )
        c.addView(prev)

        c.addView(ui.tv("Ordem das métricas", 16f, C.TEXT, 2), ui.lp(top = 18))
        c.addView(ui.tv("Toque numa métrica para adicionar. Toque numa já escolhida para tirar.", 12f, C.MUTED, 0))
        val current = p.metrics
        val slots = ui.hbox().apply { weightSum = 4f }
        for (i in 0 until 4) {
            val key = current.getOrNull(i)
            val s = ui.tv(if (key != null) Metrics.label(key) else "", 11f, C.PURPLE, 2, true).apply {
                if (key != null) {
                    background = ui.round(C.PURPLE_LIGHT, 12)
                    setOnClickListener {
                        if (current.size > 1) {
                            p.metrics = current - key
                            render()
                        }
                    }
                } else {
                    background = GradientDrawableDashed.make(ui)
                }
                setPadding(dp(2), dp(14), dp(2), dp(14))
            }
            slots.addView(s, ui.lp(0, weight = 1f).also { it.setMargins(dp(3), 0, dp(3), 0) })
        }
        c.addView(slots, ui.lp(top = 10))

        Metrics.ALL.chunked(3).forEach { rowKeys ->
            val row = ui.hbox()
            rowKeys.forEach { key ->
                val used = key in current
                row.addView(
                    ui.tv(Metrics.label(key), 12f, if (used) Color.parseColor("#9CA3AF") else C.TEXT, 1, true).apply {
                        background = ui.round(Color.parseColor("#F1F1F6"), 12)
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        if (!used) setOnClickListener {
                            if (current.size < 4) {
                                p.metrics = current + key
                                render()
                            } else Toast.makeText(this@MainActivity, "Máximo de 4 métricas.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    ui.lp(0, weight = 1f).also { it.setMargins(dp(3), 0, dp(3), 0) }
                )
            }
            c.addView(row, ui.lp(top = 8))
        }

        c.addView(ui.tv("Posição", 16f, C.TEXT, 2), ui.lp(top = 20))
        c.addView(ui.segmented(listOf("Esquerda", "Centro", "Direita"), p.cardPos) { p.cardPos = it; render() }, ui.lp(top = 8))
        c.addView(ui.tv("Tema", 16f, C.TEXT, 2), ui.lp(top = 16))
        c.addView(ui.segmented(listOf("Claro", "Escuro", "Cheio"), p.cardTheme) { p.cardTheme = it; render() }, ui.lp(top = 8))

        c.addView(stepperRow("Tamanho da fonte", p.cardFont.toString(), { p.cardFont = p.cardFont - 1; render() }, { p.cardFont = p.cardFont + 1; render() }), ui.lp(top = 16))
        c.addView(
            stepperRow(
                "Altura na tela", "${p.overlayTopDp.toInt()}",
                { p.overlayTopDp = (p.overlayTopDp - 10).coerceAtLeast(0.0); render() },
                { p.overlayTopDp = (p.overlayTopDp + 10).coerceAtMost(600.0); render() }
            ),
            ui.lp(top = 12)
        )
        box.addView(c, ui.lp(top = 20))
        return box
    }

    private object GradientDrawableDashed {
        fun make(ui: Ui) = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = ui.dpf(12f)
            setStroke(ui.dp(2), Color.parseColor("#C9C4D8"), ui.dpf(5f), ui.dpf(4f))
        }
    }

    private fun stepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): View {
        val r = ui.hbox()
        r.addView(ui.tv(label, 15f, C.TEXT, 2), ui.lp(0, weight = 1f))
        fun btn(t: String, f: () -> Unit) = ui.tv(t, 20f, C.PURPLE, 2, true).apply {
            background = ui.round(C.PURPLE_LIGHT, 12)
            setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        r.addView(btn("−", onMinus))
        r.addView(ui.tv(value, 16f, C.PURPLE, 3, true), LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.WRAP_CONTENT))
        r.addView(btn("+", onPlus))
        return r
    }

    // ---------------------------------------------------------------- captura de tela

    private fun timeContent(p: Prefs): View {
        val box = ui.vbox()
        box.addView(pageHeader("Tempo na tela", "Por quanto tempo o quadro de cálculo fica visível quando chega uma oferta"))
        val opts = listOf(3, 5, 8, 10, 15, 20, 30, 0)
        val c = ui.card()
        c.addView(ui.tv("Ficar na tela por", 16f, C.TEXT, 2))
        opts.chunked(4).forEach { rowOpts ->
            val row = ui.hbox()
            rowOpts.forEach { sec ->
                val on = p.holdSec == sec
                row.addView(
                    ui.tv(if (sec == 0) "Até tocar" else "${sec}s", 13f, if (on) C.WHITE else C.PURPLE, if (on) 2 else 1, true).apply {
                        background = ui.round(if (on) C.PURPLE else C.PURPLE_LIGHT, 12)
                        setPadding(dp(4), dp(12), dp(4), dp(12))
                        setOnClickListener { p.holdSec = sec; render() }
                    },
                    ui.lp(0, weight = 1f).also { it.setMargins(dp(3), 0, dp(3), 0) }
                )
            }
            c.addView(row, ui.lp(top = 10))
        }
        c.addView(
            ui.tv(
                "Toque no quadro a qualquer momento para fechá-lo. O toque só fecha o quadro: nada acontece no Uber/99. " +
                    "Em \"Até tocar\" ele fica até você tocar (ou até 60 segundos).",
                12f, C.MUTED, 0
            ),
            ui.lp(top = 14)
        )
        box.addView(c, ui.lp(top = 20))
        return box
    }

    private fun captureContent(p: Prefs): View {
        val box = ui.vbox()
        val prev = ui.vbox().apply {
            background = ui.round(C.PURPLE_LIGHT, 20)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        prev.addView(
            OverlayCard.build(this, OverlayCard.sample(p), p),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.gravity = Gravity.CENTER_HORIZONTAL }
        )
        box.addView(prev)
        box.addView(ui.tv("Captura de Tela Automática", 20f, C.TEXT, 3, true), ui.lp(top = 20))
        box.addView(ui.tv("Salva na sua galeria uma captura de tela de cada corrida recebida", 14f, C.MUTED, 0, true), ui.lp(top = 6))
        box.addView(
            ui.segmented(listOf("Desativado", "Ativado"), if (p.saveShots) 1 else 0) { p.saveShots = it == 1; render() },
            ui.lp(top = 18)
        )
        val note = ui.tv(
            "Ative para ter provas dos valores das corridas quando precisar. As imagens ficam em Imagens > RotaCopiloto. " +
                "Lembre-se de limpar a galeria semanalmente para não lotar o celular. Exige Android 11 ou mais novo.",
            13f, C.PURPLE_DARK, 0, true
        ).apply {
            background = ui.round(C.PURPLE_LIGHT, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(note, ui.lp(top = 14))
        return box
    }

    // ---------------------------------------------------------------- permissões e Xiaomi

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.contains("$packageName/", ignoreCase = true) && enabled.contains("CopilotService")
    }

    private fun notifOk(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return granted && nm.areNotificationsEnabled()
    }

    private fun batteryOk(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun tryStart(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, "Não consegui abrir essa tela. Abra manualmente.", Toast.LENGTH_LONG).show()
    }

    private fun appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun accessibilityContent(): View {
        val box = ui.vbox()
        box.addView(bigIcon("1"))
        box.addView(pageHeader("Ative a Leitura (Acessibilidade)", "É assim que o Rota lê o valor e os km da oferta. Ele só lê e calcula: nunca aceita nem toca em nada."), ui.lp(top = 16))
        val ok = isServiceEnabled()
        box.addView(ui.statusPill(if (ok) "Permissão concedida" else "Leitura desligada", ok))
        if (!ok) {
            box.addView(ui.tv("Passo A: liberar configurações restritas", 15f, C.TEXT, 2), ui.lp(top = 18))
            box.addView(ui.tv("Necessário em apps instalados fora da Play Store. Se já liberou antes, pule para o passo B.", 12f, C.MUTED, 0))
            box.addView(ui.button("Abrir configurações do app") { openAppDetails() })
            box.addView(
                ui.numbered(
                    listOf(
                        "Toque no menu ⋮ no canto superior",
                        "Toque em \"Permitir configurações restritas\"",
                        "Confirme e volte para o Rota"
                    )
                )
            )
            box.addView(ui.tv("Passo B: ativar a leitura", 15f, C.TEXT, 2), ui.lp(top = 22))
            box.addView(ui.button("Abrir Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
            box.addView(ui.tv("Na lista, toque em \"Rota Copiloto\" e ative.", 12f, C.MUTED, 0), ui.lp(top = 6))
        }
        return box
    }

    private fun notifContent(): View {
        val box = ui.vbox()
        box.addView(bigIcon("2"))
        box.addView(pageHeader("Ative as Notificações", "Para consultar a última oferta (valor, R\$/km, R\$/hora e lucro) a qualquer momento, direto na central de notificações"), ui.lp(top = 16))
        val ok = notifOk()
        box.addView(ui.statusPill(if (ok) "Permissão concedida" else "Notificações desligadas", ok))
        if (!ok) {
            box.addView(ui.button("Permitir notificações") {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
                else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
            box.addView(ui.button("Abrir configurações de notificação", false) {
                tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName), appDetailsIntent())
            })
        }
        return box
    }

    private fun batteryContent(): View {
        val box = ui.vbox()
        box.addView(bigIcon("3"))
        box.addView(pageHeader("Rota sem restrição de bateria", "O Android encerra apps em segundo plano pra poupar carga. Sem essa permissão, o Rota pode fechar no meio da corrida e o Cálculo de Ganhos para de funcionar"), ui.lp(top = 16))
        val ok = batteryOk()
        box.addView(ui.statusPill(if (ok) "Permissão concedida" else "Com restrição de bateria", ok))
        if (!ok) {
            box.addView(ui.button("Remover restrição") {
                tryStart(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            })
        }
        box.addView(
            ui.tv("Dica: em Configurações > Apps > Rota Copiloto, desative \"Pausar atividade no app quando não usado\" e coloque a economia de bateria em \"Sem restrições\".", 12f, C.MUTED, 0, true),
            ui.lp(top = 16)
        )
        return box
    }

    private fun autostartContent(onDone: (() -> Unit)?): View {
        val box = ui.vbox()
        box.addView(bigIcon("4"))
        box.addView(pageHeader("Inicialização Automática", "Seu celular Xiaomi às vezes fecha o Rota sozinho pra economizar bateria. Ative a inicialização automática pra ele abrir de novo na hora."), ui.lp(top = 16))
        box.addView(
            ui.numbered(
                listOf(
                    "Abra as Configurações do celular",
                    "Toque em Apps",
                    "Entre em \"Inicialização automática em segundo plano\" (ou Permissões > Início automático)",
                    "Ative a opção pro Rota Copiloto"
                )
            )
        )
        box.addView(ui.button("Abrir Configurações do Xiaomi") {
            tryStart(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                appDetailsIntent()
            )
        })
        if (onDone != null) box.addView(ui.button("Já configurei", false) { onDone() })
        return box
    }

    private fun safetyContent(onDone: (() -> Unit)?): View {
        val box = ui.vbox()
        box.addView(bigIcon("5"))
        box.addView(pageHeader("Impeça o Xiaomi de fechar o Rota", "Coloque o Rota na lista de exceções pra o Xiaomi não fechar ele em segundo plano."), ui.lp(top = 16))
        box.addView(
            ui.numbered(
                listOf(
                    "Abra o app \"Segurança\" do Xiaomi",
                    "Toque em \"Otimizar Velocidade\"",
                    "Toque na engrenagem no canto superior direito",
                    "Abra a lista de exceções",
                    "Adicione o Rota Copiloto à lista"
                )
            )
        )
        box.addView(ui.button("Abrir app \"Segurança\" do Xiaomi") {
            tryStart(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", packageName).putExtra("package_label", "Rota Copiloto"),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securitycenter.Main")),
                appDetailsIntent()
            )
        })
        if (onDone != null) box.addView(ui.button("Já configurei", false) { onDone() })
        return box
    }

    private fun xiaomiHelpContent(): View {
        val box = ui.vbox()
        box.addView(autostartContent(null))
        box.addView(View(this), ui.lp(h = 28))
        box.addView(safetyContent(null))
        return box
    }

    // ---------------------------------------------------------------- tutorial guiado

    private fun goStep(i: Int) {
        stack[stack.size - 1] = { wizardStep(i) }
        render()
    }

    private fun closeWizard() {
        prefs.wizardDone = true
        while (stack.size > 1) stack.removeAt(stack.size - 1)
        render()
    }

    private fun wizardStep(i: Int): View {
        val content: View = when (i) {
            0 -> accessibilityContent()
            1 -> notifContent()
            2 -> batteryContent()
            3 -> autostartContent { goStep(4) }
            4 -> safetyContent { goStep(5) }
            else -> doneContent()
        }

        val col = ui.vbox().apply { setBackgroundColor(C.WHITE) }
        val top = ui.hbox().apply { setPadding(dp(20), dp(14), dp(20), 0) }
        top.addView(ui.tv("✕", 22f, C.TEXT, 1).apply { setOnClickListener { closeWizard() } })
        col.addView(top)

        val sv = ScrollView(this)
        val wrap = ui.vbox().apply { setPadding(dp(18), dp(12), dp(18), dp(20)) }
        wrap.addView(content)
        sv.addView(wrap)
        col.addView(sv, ui.lp(h = 0, weight = 1f))

        val nav = ui.hbox().apply { setPadding(dp(20), dp(10), dp(20), dp(16)) }
        val back = ui.tv("←", 22f, C.PURPLE, 2, true).apply {
            background = ui.round(C.PURPLE_LIGHT, 28)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            if (i == 0) visibility = View.INVISIBLE else setOnClickListener { goStep(i - 1) }
        }
        nav.addView(back)
        val dots = ui.hbox().apply { gravity = Gravity.CENTER }
        for (d in 0 until totalSteps) {
            val dot = View(this).apply {
                background = ui.round(if (d <= i) C.PURPLE else C.LINE, 4)
                layoutParams = LinearLayout.LayoutParams(if (d == i) dp(26) else dp(8), dp(8)).also { it.setMargins(dp(3), 0, dp(3), 0) }
            }
            dots.addView(dot)
        }
        nav.addView(dots, ui.lp(0, weight = 1f))
        val next = ui.tv("→", 22f, C.WHITE, 2, true).apply {
            background = ui.round(C.PURPLE, 28)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            setOnClickListener { if (i >= totalSteps - 1) closeWizard() else goStep(i + 1) }
        }
        nav.addView(next)
        col.addView(nav)
        return col
    }

    private fun doneContent(): View {
        val box = ui.vbox()
        box.addView(bigIcon("✓"), ui.lp(top = 24))
        box.addView(pageHeader("Tudo pronto!", "Abra o Uber ou o 99, fique online e o cartão do Rota aparece quando chegar uma oferta."), ui.lp(top = 16))
        val ok = isServiceEnabled()
        box.addView(ui.statusPill(if (ok) "Leitura ativa" else "Falta ativar a leitura (passo 1)", ok))
        box.addView(ui.button("Testar o cartão agora") {
            val s = CopilotService.instance
            if (s == null) Toast.makeText(this, "Ative a leitura primeiro.", Toast.LENGTH_LONG).show()
            else {
                s.showSimulation()
                Toast.makeText(this, "Saia do app: o cartão aparece por 6 segundos.", Toast.LENGTH_LONG).show()
            }
        })
        box.addView(ui.button("Configurar agora", false) {
            closeWizard()
            push { configMenu() }
        })
        box.addView(ui.button("Concluir", false) { closeWizard() })
        return box
    }

    // ---------------------------------------------------------------- termos

    private fun showTerms() {
        AlertDialog.Builder(this)
            .setTitle("Antes de começar")
            .setMessage(
                "O Rota Copiloto é uma calculadora de apoio. Ele apenas lê os valores que aparecem na tela do " +
                    "Uber Driver e do 99 Motorista e mostra uma análise. Ele não aceita, não recusa e não toca em nada.\n\n" +
                    "Não é afiliado, aprovado ou mantido pela Uber nem pela 99. Os números são estimativas; " +
                    "a decisão de aceitar a corrida é sempre sua.\n\n" +
                    "Quando o app não entrega o texto da tela, o Rota Copiloto lê os números por imagem, só enquanto o Uber/99 está aberto. " +
                    "A imagem é analisada no próprio celular e descartada (a não ser que você ligue a Captura de Tela).\n\n" +
                    "Nada sai do seu celular: não há login nem servidor."
            )
            .setCancelable(false)
            .setPositiveButton("Entendi") { _, _ ->
                prefs.termsAccepted = true
                if (!prefs.wizardDone) push { wizardStep(0) }
            }
            .setNegativeButton("Sair") { _, _ -> finish() }
            .show()
    }
}
