package com.shreenipane.orbit.core

import com.shreenipane.orbit.core.model.ClassSession
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A timetable packed into one pasteable string ("ORBIT1:…") so friends can
 * swap schedules over WhatsApp or Telegram without any server. Plain text
 * inside, deflated and base64url-encoded.
 */
object ShareCode {
    private const val PREFIX = "ORBIT1:"

    data class Payload(val name: String, val semester: String, val sessions: List<ClassSession>)

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'p' -> sb.append('|'); 'n' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun splitFields(line: String): List<String> {
        // '|' never appears unescaped inside a field, so a plain split is safe.
        return line.split('|').map(::unesc)
    }

    fun encode(p: Payload): String {
        val text = buildString {
            append(esc(p.name)).append('\n').append(esc(p.semester)).append('\n')
            p.sessions.forEach { s ->
                append(listOf(s.day.toString(), s.start, s.end, s.slot, s.courseCode, s.courseTitle, s.typeCode, s.venue)
                    .joinToString("|") { esc(it) }).append('\n')
            }
        }
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(text.toByteArray(Charsets.UTF_8)); finish() }
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    // Limits for untrusted codes (QR scans, pasted messages). A real timetable is
    // ~1 KB packed and a few KB unpacked, so these leave lots of headroom while a
    // crafted "zip bomb" or junk can't exhaust memory or plant oversized text.
    private const val MAX_CODE_CHARS = 12_000
    private const val MAX_TEXT_BYTES = 128 * 1024
    private const val MAX_SESSIONS = 200
    private const val MAX_FIELD = 120
    private val TIME = Regex("^\\d{2}:\\d{2}$")

    /** Accepts the code on its own or buried in a forwarded message. Returns null for anything malformed or oversized. */
    fun decode(raw: String): Payload? = runCatching {
        val code = Regex("ORBIT1:([A-Za-z0-9_-]+)").find(raw)?.groupValues?.get(1) ?: return null
        if (code.length > MAX_CODE_CHARS) return null
        val bytes = Base64.getUrlDecoder().decode(code)
        val inf = Inflater().apply { setInput(bytes) }
        val out = ByteArrayOutputStream(); val buf = ByteArray(1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_TEXT_BYTES) return null
            }
        } finally {
            inf.end()
        }
        val lines = out.toString(Charsets.UTF_8.name()).split('\n')
        if (lines.size < 2) return null
        val rows = lines.drop(2).filter { it.isNotBlank() }
        if (rows.size > MAX_SESSIONS) return null
        val sessions = rows.map { l ->
            val f = splitFields(l)
            require(f.size >= 8 && f.all { it.length <= MAX_FIELD })
            val day = f[0].toInt()
            require(day in 1..7 && TIME.matches(f[1]) && TIME.matches(f[2]))
            ClassSession(day, f[1], f[2], f[3], f[4], f[5], f[6], f[7])
        }
        Payload(unesc(lines[0]).take(40), unesc(lines[1]).take(MAX_FIELD), sessions)
    }.getOrNull()

    /** A readable version for people who don't use Orbit. */
    fun plainText(name: String, sessions: List<ClassSession>): String {
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return buildString {
            append("$name's timetable\n")
            sessions.groupBy { it.day }.toSortedMap().forEach { (d, list) ->
                append("\n").append(days[d - 1]).append('\n')
                list.sortedBy { it.start }.forEach { append("  ${it.start}–${it.end}  ${it.courseTitle} (${it.venue})\n") }
            }
        }
    }
}
