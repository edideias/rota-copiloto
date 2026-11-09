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

    // Distância em km OU metros ("806 m" vira 0,806 km). "min" nunca é confundido com "m".
    private const val DIST = """(\d+(?:[.,]\d+)?)\s*(km|m)(?![a-zA-Z])"""
    // Tempo "23 min" ou "1 h 05 min"
    private const val TEMPO = """(?:(\d+)\s*h\s*)?(\d+)\s*min"""

    // "4 min (1,2 km)"  |  "4 min • 800 m"  |  "1 h 05 min (25 km)"
    private val minKmRx = Regex(TEMPO + """[^\d]{0,12}?""" + DIST, RegexOption.IGNORE_CASE)

    // "1,2 km (4 min)"  |  "800 m • 4 min"
    private val kmMinRx = Regex(DIST + """[^\d]{0,12}?""" + TEMPO, RegexOption.IGNORE_CASE)

    private val kmOnlyRx = Regex(DIST, RegexOption.IGNORE_CASE)
    private val minOnlyRx = Regex(TEMPO, RegexOption.IGNORE_CASE)

    // "R$1,47/km" que o próprio 99 mostra: serve para conferir a distância lida.
    private val perKmRx = Regex("""R\$\s*(\d+(?:,\d{1,2})?)\s*/\s*km""", RegexOption.IGNORE_CASE)

    private fun dist(v: String, unit: String): Double =
        num(v) / (if (unit.equals("m", true)) 1000.0 else 1.0)
    private fun mins(h: String, m: String): Double =
        (if (h.isEmpty()) 0.0 else h.toDouble() * 60.0) + m.toDouble()

    private data class Pair(val start: Int, val end: Int, val km: Double, val min: Double)

    private val stopsRx = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE)

    /** "1 parada(s)", "2 paradas" ou "Taxa de parada" => ha parada no caminho. */
    private fun readStops(text: String): Int {
        val n = stopsRx.find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (n != null && n > 0) return n
        return if (text.contains("taxa de parada", ignoreCase = true)) 1 else 0
    }

    fun parse(platform: String, text: String): Offer? {
        val o = parseBase(platform, text) ?: return null
        val s = readStops(text)
        return if (s > 0) o.copy(stops = s) else o
    }

    private fun parseBase(platform: String, text: String): Offer? {
        val value = readValue(text) ?: return null

        val pairs = ArrayList<Pair>()
        for (m in minKmRx.findAll(text)) {
            pairs.add(Pair(m.range.first, m.range.last, dist(m.groupValues[3], m.groupValues[4]), mins(m.groupValues[1], m.groupValues[2])))
        }
        for (m in kmMinRx.findAll(text)) {
            pairs.add(Pair(m.range.first, m.range.last, dist(m.groupValues[1], m.groupValues[2]), mins(m.groupValues[3], m.groupValues[4])))
        }
        pairs.sortBy { it.start }
        val chosen = ArrayList<Pair>()
        for (p in pairs) {
            if (chosen.isEmpty() || p.start > chosen.last().end) chosen.add(p)
        }

        if (chosen.size >= 2) {
            val a = chosen[0]
            val b = chosen[1]
            return confere(text, valid(Offer(platform, value, a.km, a.min, b.km, b.min)))
        }

        // Plano B: lê km e minutos soltos, na ordem em que aparecem.
        val kms = kmOnlyRx.findAll(text).map { dist(it.groupValues[1], it.groupValues[2]) }.toList()
        val mins = minOnlyRx.findAll(text).map { mins(it.groupValues[1], it.groupValues[2]) }.toList()
        return confere(text, when {
            kms.size >= 2 && mins.size >= 2 ->
                valid(Offer(platform, value, kms[0], mins[0], kms[1], mins[1]))
            chosen.size == 1 ->
                valid(Offer(platform, value, 0.0, 0.0, chosen[0].km, chosen[0].min))
            kms.size == 1 ->
                valid(Offer(platform, value, 0.0, 0.0, kms[0], mins.firstOrNull() ?: 0.0))
            else -> null
        })
    }

    /**
     * Se a tela mostra "R$ x/km" (como o 99 faz), a distância total tem que bater com valor ÷ (R$/km).
     * Quando a leitura foge mais de 15%, a distância é corrigida por essa referência.
     */
    private fun confere(text: String, o: Offer?): Offer? {
        if (o == null) return null
        val ref = perKmRx.find(text)?.groupValues?.get(1)?.let { num(it) } ?: return o
        if (ref <= 0.0) return o
        val esperado = o.value / ref
        val lido = o.pickupKm + o.tripKm
        if (lido <= 0.0 || Math.abs(lido - esperado) / esperado <= 0.15) return o
        if (esperado > 500.0) return o
        val f = esperado / lido
        return o.copy(pickupKm = o.pickupKm * f, tripKm = o.tripKm * f)
    }

    private fun valid(o: Offer): Offer? {
        if (o.value <= 0.0) return null
        if (o.pickupKm + o.tripKm <= 0.0) return null
        if (o.pickupKm + o.tripKm > 500.0) return null
        return o
    }

    /** Primeiro valor em R$ que não seja "por km" / "por hora". */
    private val acceptRx = Regex("""aceitar\s*por\s*R\$\s*(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:,\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private fun readValue(text: String): Double? {
        // "Aceitar por R$17,19" e o valor real da oferta: tem prioridade sobre qualquer outro R$.
        acceptRx.find(text)?.groupValues?.get(1)?.replace(".", "")?.replace(",", ".")?.toDoubleOrNull()?.let { if (it > 0.0) return it }
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
