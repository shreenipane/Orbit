package com.shreenipane.orbit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** One question paper from CodeChef-VIT's archive (papers.codechefvit.com). */
@Serializable
data class PastPaper(
    @kotlinx.serialization.SerialName("_id") val id: String,
    val subject: String = "",
    val exam: String = "",
    val year: String = "",
    val slot: String = "",
    val semester: String = "",
    val campus: String = "",
    @kotlinx.serialization.SerialName("file_url") val fileUrl: String = "",
    @kotlinx.serialization.SerialName("answer_key_included") val answerKey: Boolean = false,
)

/**
 * Reads CodeChef-VIT's public papers archive (MIT-licensed project) and keeps
 * opened PDFs on the phone so they work offline. No VTOP session involved.
 */
object PastPapers {
    private const val BASE = "https://papers.codechefvit.com"
    private val http = SharedHttp.base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable private data class Papers(val papers: List<PastPaper> = emptyList())
    @Serializable private data class Response(val status: String = "", val data: Papers? = null, val message: String = "")

    /** Papers for a course, by its code (their subjects read "Operating Systems [BCSE303L]"). Vellore first, newest first. */
    suspend fun forCourse(code: String): List<PastPaper> = withContext(Dispatchers.IO) {
        val url = "$BASE/api/papers".toHttpUrl().newBuilder().addQueryParameter("subject", code).build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("The papers archive didn't answer (${r.code}).")
            val body = json.decodeFromString(Response.serializer(), r.body?.string().orEmpty())
            body.data?.papers.orEmpty()
                .filter { SAFE_ID.matches(it.id) && trustedFile(it.fileUrl) }
                .sortedWith(
                    compareByDescending<PastPaper> { it.campus.equals("Vellore", true) }
                        .thenByDescending { it.year }
                        .thenBy { EXAM_ORDER.indexOf(it.exam).let { i -> if (i < 0) 99 else i } },
                )
        }
    }

    private val EXAM_ORDER = listOf("CAT-1", "CAT-2", "FAT", "Model CAT-1", "Model CAT-2", "Model FAT")

    /** Paper ids become file names, so only plain ids are accepted (no "../" from a tampered response). */
    private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** PDFs are only fetched over HTTPS from the archive's own storage. */
    private fun trustedFile(url: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        return u.isHttps && (u.host == "storage.googleapis.com" || u.host == "papers.codechefvit.com" || u.host.endsWith(".codechefvit.com"))
    }

    /** Question papers are a few MB; anything far bigger isn't one. */
    private const val MAX_BYTES = 40L * 1024 * 1024

    private fun dir(context: Context) = File(context.cacheDir, "papers").apply { mkdirs() }

    fun cached(context: Context, p: PastPaper): File? =
        if (!SAFE_ID.matches(p.id)) null else File(dir(context), "${p.id}.pdf").takeIf { it.length() > 0 }

    /** The paper's PDF on the phone, downloading it the first time. */
    suspend fun file(context: Context, p: PastPaper): File = withContext(Dispatchers.IO) {
        require(SAFE_ID.matches(p.id) && trustedFile(p.fileUrl)) { "That paper's link doesn't look right." }
        cached(context, p)?.let { return@withContext it }
        val out = File(dir(context), "${p.id}.pdf")
        val tmp = File(dir(context), "${p.id}.part")
        http.newCall(Request.Builder().url(p.fileUrl).build()).execute().use { r ->
            if (!r.isSuccessful) error("Couldn't download the paper (${r.code}).")
            val body = r.body ?: error("Empty download.")
            if (body.contentLength() > MAX_BYTES) error("That file is too large to be a question paper.")
            try {
                tmp.outputStream().use { o ->
                    val input = body.byteStream(); val buf = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) error("That file is too large to be a question paper.")
                        o.write(buf, 0, n)
                    }
                }
            } catch (e: Exception) {
                tmp.delete(); throw e
            }
        }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        out
    }

    /** A file name like "BCSE303L CAT-1 2023-24 D2.pdf". */
    fun fileName(p: PastPaper): String {
        val code = Regex("\\[([^\\]]+)]").find(p.subject)?.groupValues?.get(1) ?: p.subject.take(20)
        return listOf(code, p.exam, p.year, p.slot).filter { it.isNotBlank() }.joinToString(" ")
            .replace(Regex("[^A-Za-z0-9 ._-]"), "") + ".pdf"
    }
}
