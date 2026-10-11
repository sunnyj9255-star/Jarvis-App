package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), RecognitionListener {
    private lateinit var status: TextView
    private var speech: SpeechService? = null
    private var tts: TextToSpeech? = null
    private var torchOn = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status = TextView(this).apply {
            textSize = 20f; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40)
            text = "Jarvis loading..."
        }
        setContentView(status)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("hi", "IN"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.US
        } }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 1)
        else loadModel()
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) loadModel()
        else status.text = "Mic permission chahiye"
    }

    private fun loadModel() {
        StorageService.unpack(this, "model", "model",
            { model: Model ->
                speech = SpeechService(Recognizer(model, 16000f), 16000f).also { it.startListening(this) }
                status.text = "बोलो: \"जार्विस, ...\""
            },
            { e: java.io.IOException -> status.text = "Model error: ${e.message}" })
    }

    override fun onResult(h: String?) {
        val text = JSONObject(h ?: return).optString("text").trim()
        val normalizedText = text.lowercase(Locale.getDefault())
        val wake = wakeWords.firstOrNull { normalizedText.contains(it.lowercase(Locale.getDefault())) } ?: return
        val cmd = normalizedText.substringAfter(wake.lowercase(Locale.getDefault())).trim()
        status.text = "Suna: $cmd"
        handle(cmd)
    }

    override fun onPartialResult(h: String?) {}
    override fun onFinalResult(h: String?) {}
    override fun onError(e: Exception?) { status.text = "Error: ${e?.message}" }
    override fun onTimeout() {}

    private fun say(s: String) { status.text = s; tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "j") }

    private val wakeWords = listOf("jarvis", "जार्विस", "जारविस", "जर्विस", "जार्विज", "जारवि")

    private fun has(c: String, vararg k: String): Boolean {
        val normalizedInput = c.lowercase(Locale.getDefault())
        return k.any { normalizedInput.contains(it.lowercase(Locale.getDefault())) }
    }

    private fun handle(c: String) {
        val w = c.split(" ").filter { it.isNotBlank() }
        val n = numbers(w)
        when {
            has(c, "torch", "टॉर्च", "टार्च", "टोर्च", "फ्लैश", "फ़्लैश") -> {
                torchOn = !has(c, "off", "बंद", "ऑफ", "बन्द")
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(cm.cameraIdList[0], torchOn)
                say(if (torchOn) "टॉर्च चालू" else "टॉर्च बंद")
            }
            has(c, "timer", "टाइमर", "टाईमर") && n.isNotEmpty() -> {
                val mult = if (has(c, "hour", "घंटे", "घंटा", "घण्टे")) 3600
                    else if (has(c, "second", "सेकंड", "सेकेंड", "सैकंड")) 1 else 60
                startActivity(Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, n[0] * mult)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                say("टाइमर लग गया")
            }
            has(c, "alarm", "अलार्म", "अलारम") && n.isNotEmpty() -> {
                var h = n[0] % 24
                if (has(c, "p m", "pm", "शाम", "रात", "दोपहर") && h < 12) h += 12
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, h)
                    .putExtra(AlarmClock.EXTRA_MINUTES, n.getOrElse(1) { 0 })
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                say("अलार्म लग गया")
            }
            has(c, "time", "समय", "टाइम", "बजा") ->
                say("अभी " + SimpleDateFormat("h mm a", Locale.US).format(Date()) + " हुए हैं")
            has(c, "open", "खोल", "ओपन") -> {
                var name = c
                listOf("open", "खोलो", "खोल", "दो", "ओपन", "करो").forEach { name = name.replace(it, " ") }
                openApp(name.trim())
            }
            has(c, "call", "कॉल", "फोन", "फ़ोन") && n.isNotEmpty() -> {
                val num = w.mapNotNull { digitOf(it) }.joinToString("")
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> say("समझ नहीं आया")
        }
    }

    private val aliases = mapOf(
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "कैमरा" to "camera", "क्रोम" to "chrome",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "सेटिंग" to "settings", "गैलरी" to "gallery", "कैलकुलेटर" to "calculator", "मैप" to "maps",
        "फोटो" to "photos", "संदेश" to "messages", "घड़ी" to "clock", "इंस्टाग्राम" to "instagram")

    private fun openApp(spoken: String) {
        val normalizedSpoken = spoken.lowercase(Locale.getDefault())
        val key = aliases.entries.firstOrNull { normalizedSpoken.contains(it.key.lowercase(Locale.getDefault())) }?.value ?: normalizedSpoken
        val pm = packageManager
        val l = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val app = pm.queryIntentActivities(l, 0).firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(key.lowercase(Locale.getDefault()))
        }
        val i = app?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (i != null) { startActivity(i); say("खोल रहा हूँ") } else say("ऐप नहीं मिला")
    }

    // --- Hindi + English number words -> ints ---
    private val numMap: Map<String, Int> = HashMap<String, Int>().apply {
        val en = listOf("zero","one","two","three","four","five","six","seven","eight","nine","ten",
            "eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen")
        en.forEachIndexed { i, t -> put(t, i) }
        put("twenty",20); put("thirty",30); put("forty",40); put("fifty",50)
        val hi = listOf("शून्य","एक","दो","तीन","चार","पांच","छह","सात","आठ","नौ","दस","ग्यारह","बारह","तेरह","चौदह","पंद्रह","सोलह","सत्रह","अठारह","उन्नीस","बीस","इक्कीस","बाईस","तेईस","चौबीस","पच्चीस","छब्बीस","सत्ताईस","अट्ठाईस","उनतीस","तीस","इकतीस","बत्तीस","तैंतीस","चौंतीस","चौतीस","पैंतीस","छत्तीस","सैंतीस","अड़तीस","उनतालीस","चालीस","इकतालीस","बयालीस","तैंतालीस","चौवालीस","पैंतालीस","सैंतालीस","अड़तालीस","उनचास","पचास")
        hi.forEachIndexed { i, t -> put(t, i) }
        put("पाँच",5); put("छः",6); put("छे",6); put("छ",6); put("पन्द्रह",15); put("नो",9)
    }

    private fun digitOf(s: String): Int? = (numMap[s.lowercase(Locale.getDefault())] ?: s.lowercase(Locale.getDefault()).toIntOrNull())?.takeIf { it in 0..9 }

    private fun numbers(w: List<String>): List<Int> {
        val out = mutableListOf<Int>(); var i = 0
        while (i < w.size) {
            val t = w[i].lowercase(Locale.getDefault())
            var v = t.toIntOrNull() ?: numMap[t]
            if (v != null && t in listOf("twenty","thirty","forty","fifty")) {
                val d = w.getOrNull(i + 1)?.lowercase(Locale.getDefault())?.let { numMap[it] } ?: -1
                if (d in 1..9) { v += d; i++ }
            }
            if (v != null) out.add(v)
            i++
        }
        return out
    }

    override fun onDestroy() { super.onDestroy(); speech?.shutdown(); tts?.shutdown() }
}
