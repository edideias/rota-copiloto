package br.rota.copiloto

import android.content.Context

enum class Verdict { GREEN, YELLOW, RED }

data class Offer(
    val platform: String,
    val value: Double,
    val pickupKm: Double,
    val pickupMin: Double,
    val tripKm: Double,
    val tripMin: Double
)

data class Analysis(
    val offer: Offer,
    val totalKm: Double,
    val totalMin: Double,
    val perKm: Double,
    val perHour: Double,      // -1 quando o tempo não foi lido
    val profit: Double,
    val verdict: Verdict
)

object Calc {
    fun analyze(o: Offer, p: Prefs): Analysis {
        val km = o.pickupKm + o.tripKm
        val min = o.pickupMin + o.tripMin
        val perKm = if (km > 0) o.value / km else 0.0
        val hourKnown = min > 0
        val perHour = if (hourKnown) o.value / (min / 60.0) else -1.0
        val profit = o.value - km * p.costPerKm

        val red = perKm < p.yellowKm || (hourKnown && perHour < p.yellowHour)
        val green = perKm >= p.greenKm && (!hourKnown || perHour >= p.greenHour)
        val verdict = when {
            red -> Verdict.RED
            green -> Verdict.GREEN
            else -> Verdict.YELLOW
        }
        return Analysis(o, km, min, perKm, perHour, profit, verdict)
    }
}

/** Configurações salvas só no aparelho. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("copiloto", Context.MODE_PRIVATE)

    private fun d(k: String, def: Double): Double = sp.getString(k, null)?.toDoubleOrNull() ?: def
    private fun putD(k: String, v: Double) { sp.edit().putString(k, v.toString()).apply() }

    var greenKm: Double
        get() = d("greenKm", 1.80)
        set(v) = putD("greenKm", v)
    var yellowKm: Double
        get() = d("yellowKm", 1.30)
        set(v) = putD("yellowKm", v)
    var greenHour: Double
        get() = d("greenHour", 35.0)
        set(v) = putD("greenHour", v)
    var yellowHour: Double
        get() = d("yellowHour", 25.0)
        set(v) = putD("yellowHour", v)
    var costPerKm: Double
        get() = d("costPerKm", 0.60)
        set(v) = putD("costPerKm", v)
    var overlayTopDp: Double
        get() = d("overlayTopDp", 80.0)
        set(v) = putD("overlayTopDp", v)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }
    var useUber: Boolean
        get() = sp.getBoolean("useUber", true)
        set(v) { sp.edit().putBoolean("useUber", v).apply() }
    var use99: Boolean
        get() = sp.getBoolean("use99", true)
        set(v) { sp.edit().putBoolean("use99", v).apply() }
    var useOcr: Boolean
        get() = sp.getBoolean("useOcr", true)
        set(v) { sp.edit().putBoolean("useOcr", v).apply() }
    var termsAccepted: Boolean
        get() = sp.getBoolean("termsAccepted", false)
        set(v) { sp.edit().putBoolean("termsAccepted", v).apply() }
}
