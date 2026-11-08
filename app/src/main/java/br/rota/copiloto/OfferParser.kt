package br.rota.copiloto

/**
 * Transforma os textos da tela em uma [Offer].
 *
 * É a parte que mais depende do layout do Uber/99. Se o app mudar a tela e a
 * leitura falhar, é só aqui (e em REQUIRE_ACCEPT_WORD no CopilotService) que
 * precisa mexer. O log de depuração mostra exatamente o que foi lido.
 */
object OfferParser {

    // Aceita "R$23,00", "R$ 10,38" e também "R$ | 10,38" (quando o app separa em dois textos).
    private val moneyRx = Regex("""R\$\s*(?:\|\s*)?(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?)""")

    // "4 min (1,2 km)"  |  "4 min • 1,2 km"  |  "4min 1.2km"
    private val minKmRx = Regex(
        """(\d+)\s*min[^\d]{0,12}?(\d+(?:[.,]\d+)?)\s*km""",
        RegexOption.IGNORE_CASE
    )

    // "1,2 km (4 min)"  |  "1,2 km • 4 min"
    private val kmMinRx = Regex(
        """(\d+(?:[.,]\d+)?)\s*km[^\d]{0,12}?(\d+)\s*min""",
        RegexOption.IGNORE_CASE
    )

    private val kmOnlyRx = Regex("""(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)
    private val minOnlyRx = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)

    private data class Pair(val start: Int, val end: Int, val km: Double, val min: Double)

    fun parse(platform: String, text: String): Offer? {
        val value = readValue(text) ?: return null

        val pairs = ArrayList<Pair>()
        for (m in minKmRx.findAll(text)) {
            pairs.add(Pair(m.range.first, m.range.last, num(m.groupValues[2]), m.groupValues[1].toDouble()))
        }
        for (m in kmMinRx.findAll(text)) {
            pairs.add(Pair(m.range.first, m.range.last, num(m.groupValues[1]), m.groupValues[2].toDouble()))
        }
        pairs.sortBy { it.start }
        val chosen = ArrayList<Pair>()
        for (p in pairs) {
            if (chosen.isEmpty() || p.start > chosen.last().end) chosen.add(p)
        }

        if (chosen.size >= 2) {
            val a = chosen[0]
            val b = chosen[1]
            return valid(Offer(platform, value, a.km, a.min, b.km, b.min))
        }

        // Plano B: lê km e minutos soltos, na ordem em que aparecem.
        val kms = kmOnlyRx.findAll(text).map { num(it.groupValues[1]) }.toList()
        val mins = minOnlyRx.findAll(text).map { it.groupValues[1].toDouble() }.toList()
        return when {
            kms.size >= 2 && mins.size >= 2 ->
                valid(Offer(platform, value, kms[0], mins[0], kms[1], mins[1]))
            chosen.size == 1 ->
                valid(Offer(platform, value, 0.0, 0.0, chosen[0].km, chosen[0].min))
            kms.size == 1 ->
                valid(Offer(platform, value, 0.0, 0.0, kms[0], mins.firstOrNull() ?: 0.0))
            else -> null
        }
    }

    private fun valid(o: Offer): Offer? {
        if (o.value <= 0.0) return null
        if (o.pickupKm + o.tripKm <= 0.0) return null
        if (o.pickupKm + o.tripKm > 500.0) return null
        return o
    }

    /** Primeiro valor em R$ que não seja "por km" / "por hora". */
    private fun readValue(text: String): Double? {
        for (m in moneyRx.findAll(text)) {
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 9)).lowercase()
            if (after.contains("/km") || after.contains("/h") || after.trimStart().startsWith("por ")) continue
            val v = m.groupValues[1].replace(".", "").replace(",", ".").toDoubleOrNull() ?: continue
            if (v > 0.0) return v
        }
        return null
    }

    private fun num(s: String): Double = s.replace(",", ".").toDouble()
}
