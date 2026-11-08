package br.rota.copiloto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log só em memória (some ao fechar o serviço). Serve para calibrar a leitura. */
object DebugLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale("pt", "BR"))

    @Synchronized
    fun add(msg: String) {
        val clipped = if (msg.length > 1500) msg.substring(0, 1500) + "…" else msg
        lines.addLast(fmt.format(Date()) + "  " + clipped)
        while (lines.size > 120) lines.removeFirst()
    }

    // Só nomes de pacotes (nunca conteúdo) dos apps que geraram eventos. Fica em memória.
    private val seenPkgs = LinkedHashSet<String>()

    @Synchronized
    fun seen(pkg: String) {
        if (seenPkgs.size < 60) seenPkgs.add(pkg)
    }

    @Synchronized
    fun dump(): String {
        val head = "Pacotes vistos: " + (if (seenPkgs.isEmpty()) "(nenhum)" else seenPkgs.joinToString(", "))
        val body = if (lines.isEmpty()) "(vazio — abra o app do Uber/99 e espere uma oferta)"
        else lines.joinToString("\n\n")
        return head + "\n\n" + body
    }

    @Synchronized
    fun clear() = lines.clear()
}
