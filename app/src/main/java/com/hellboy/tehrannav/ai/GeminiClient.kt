package com.hellboy.tehrannav.ai

import android.util.Log
import com.hellboy.tehrannav.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gemini integration using a PERSONAL API key.
 * Two modes:
 *  - parseDestination: extract target place from a Persian sentence (JSON mode)
 *  - routeAdvice:      after routing, get a natural Persian navigation briefing with
 *    street-by-street directions + distance/time from origin (GPS).
 */
object GeminiClient {
    private const val TAG = "GeminiClient"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Serializable
    data class Req(
        val contents: List<Content>,
        @SerialName("generationConfig") val generationConfig: GenCfg = GenCfg()
    )
    @Serializable data class Content(val parts: List<Part>)
    @Serializable data class Part(val text: String)
    @Serializable data class GenCfg(
        val temperature: Double = 0.2,
        @SerialName("responseMimeType") val responseMimeType: String = "application/json"
    )
    @Serializable data class Resp(
        val candidates: List<Candidate> = emptyList()
    )
    @Serializable data class Candidate(
        val content: Content? = null
    )

    @Serializable
    data class Destination(val destination: String = "")

    @Serializable
    data class Advice(val directions: List<String> = emptyList(), val summary: String = "")

    private val DEST_PROMPT = """
        تو یک دستیار مسیریابی فارسی هستی. از جمله کاربر، نام مقصد (محله، خیابان، میدان، مکان معروف در تهران یا ایران) را استخراج کن.
        فقط JSON برگردان به این شکل: {"destination":"نام کامل مقصد"}
        اگر مقصدی پیدا نشد: {"destination":""}
        جمله کاربر:
    """.trimIndent()

    private fun advicePrompt(destName: String, routeText: String) = """
        تو راهنمای مسیریابی فارسی هستی. مسیر محاسبه‌شده از نقطه مبدا (موقعیت فعلی GPS کاربر) تا «$destName» این است:
        $routeText
        یک راهنمای مختصر و طبیعی فارسی بنویس: اول خلاصه (چقدر راه، حدود چند دقیقه)، بعد ۳-۵ دستور قدم‌به‌قدم (از کدام خیابان برود، کجا بپیچد). 
        فقط JSON با کلیدهای directions (لیست رشته‌ها) و summary (رشته) برگردان.
    """.trimIndent()

    fun isConfigured(settings: Settings) = settings.geminiApiKey.isNotBlank()

    /** Free-form chat used as fallback when no destination matched. Keeps last N turns. */
    suspend fun chat(message: String, history: List<Pair<String, String>>, settings: Settings): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val contextText = history.joinToString("\n") { (role, text) ->
                    (if (role == "user") "کاربر: " else "دستیار: ") + text
                }
                val prompt = "تو دستیار مسیریابی فارسی هستی. به این گفتگو پاسخ کوتاه بده:\n$contextText\nکاربر: $message"
                generate(prompt, "", settings)
            }.getOrDefault("پاسخی دریافت نشد.")
        }

    suspend fun parseDestination(text: String, settings: Settings): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = generate(text, DEST_PROMPT, settings)
                val d = json.decodeFromString<Destination>(resp)
                d.destination.trim()
            }.getOrDefault("")
        }

    suspend fun routeAdvice(destName: String, routeText: String, settings: Settings): Advice? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = generate(routeText, advicePrompt(destName, routeText), settings)
                json.decodeFromString<Advice>(resp)
            }.getOrNull()
        }

    private fun generate(userText: String, systemPrompt: String, settings: Settings): String {
        val key = settings.geminiApiKey
        val model = settings.aiModel
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val full = systemPrompt + "\n" + userText
        val body = json.encodeToString(
            Req.serializer(),
            Req(contents = listOf(Content(listOf(Part(full)))))
        )
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", "TehranNav/1.0")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                Log.e(TAG, "Gemini HTTP ${resp.code}: ${err.take(300)}")
                throw Exception("HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val parsed = json.decodeFromString<Resp>(raw)
            val text = parsed.candidates.firstOrNull()
                ?.content?.parts?.joinToString("") { it.text }.orEmpty().trim()
            if (text.isEmpty()) throw Exception("empty response")
            // strip possible markdown fences
            return text.replace(Regex("^```(json)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*```$"), "")
        }
    }
}