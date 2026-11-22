package com.example.remot

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

enum class DeviceCategory(val label: String) {
    TV("شاشات / قاعات"),
    RECEIVER("رسيفرات"),
    CUSTOM("ريموت مخصص")
}

data class RemoteAction(
    val id: String,
    val label: String,
    val frequencyHz: Int,
    val patternMicros: IntArray
)

data class RemoteProfile(
    val id: String,
    val name: String,
    val brand: String,
    val category: DeviceCategory,
    val actions: List<RemoteAction>
)

object IrEngine {
    private const val PREFS_NAME = "remot_prefs"
    private const val KEY_PROFILES = "saved_profiles_json"

    fun manager(ctx: Context): ConsumerIrManager? =
        ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun hasIrEmitter(ctx: Context): Boolean {
        val m = manager(ctx) ?: return false
        return runCatching { m.hasIrEmitter() }.getOrDefault(false)
    }

    fun transmit(ctx: Context, action: RemoteAction): Result<String> {
        val m = manager(ctx)
        if (m == null || !runCatching { m.hasIrEmitter() }.getOrDefault(false)) {
            return Result.failure(IllegalStateException("هذا الهاتف لا يحتوي على مرسل الأشعة تحت الحمراء (IR Blaster)"))
        }
        return runCatching {
            m.transmit(action.frequencyHz, action.patternMicros)
            "تم الإرسال: ${action.label}"
        }
    }

    suspend fun transmitAutoScan(
        ctx: Context,
        actions: List<RemoteAction>,
        onStep: (String) -> Unit
    ): Result<String> {
        val m = manager(ctx)
        if (m == null || !runCatching { m.hasIrEmitter() }.getOrDefault(false)) {
            return Result.failure(IllegalStateException("هذا الهاتف لا يحتوي على مرسل الأشعة تحت الحمراء (IR Blaster)"))
        }
        if (actions.isEmpty()) {
            return Result.failure(IllegalStateException("لا توجد أكواد للمسح"))
        }
        for ((index, act) in actions.withIndex()) {
            onStep("جاري تجربة (${index + 1}/${actions.size}): ${act.label}...")
            runCatching { m.transmit(act.frequencyHz, act.patternMicros) }
            delay(450L)
        }
        return Result.success("اكتمل إرسال حزمة الأكواد (${actions.size} إشارة)")
    }

    fun necPattern(code32Bit: Long): IntArray {
        val list = ArrayList<Int>(70)
        list.add(9000)
        list.add(4500)
        for (bit in 31 downTo 0) {
            val isOne = ((code32Bit ushr bit) and 1L) == 1L
            list.add(560)
            list.add(if (isOne) 1690 else 565)
        }
        list.add(560)
        list.add(40000)
        return list.toIntArray()
    }

    fun parseCodeInput(rawInput: String, fallbackFreq: Int = 38000): Pair<Int, IntArray> {
        val cleaned = rawInput.trim()
        require(cleaned.isNotEmpty()) { "أدخل كود الإشارة أو التوقيت" }

        val timingTokens = cleaned.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (timingTokens.size >= 4 && timingTokens.all { it.toIntOrNull() != null }) {
            val timings = timingTokens.map { it.toInt() }.filter { it > 0 }.toIntArray()
            require(timings.size >= 4) { "سلسلة النبضات قصيرة جداً" }
            return fallbackFreq to timings
        }

        val hex = cleaned.removePrefix("0x").removePrefix("0X").replace(" ", "")
        require(hex.matches(Regex("[0-9a-fA-F]{1,8}"))) {
            "صيغة الكود غير صحيحة. أدخل كود Hex أو أرقام توقيت مفصولة بفاصلة"
        }
        val value = hex.toLong(16)
        return 38000 to necPattern(value)
    }

    fun defaultProfiles(): List<RemoteProfile> = listOf(
        RemoteProfile(
            id = "campus_displays_pro",
            name = "شاشات الجامعات والمعاهد",
            brand = "LG / Samsung / NEC / Panasonic / Sony",
            category = DeviceCategory.TV,
            actions = listOf(
                RemoteAction("pwr", "POWER", 38000, necPattern(0x20DF10EF)),
                RemoteAction("pwr_alt", "POWER ALT", 38000, necPattern(0xE0E040BF)),
                RemoteAction("src", "SOURCE / INPUT", 38000, necPattern(0x20DFD02F)),
                RemoteAction("menu", "MENU", 38000, necPattern(0x20DFC23D)),
                RemoteAction("exit", "EXIT / BACK", 38000, necPattern(0x20DF14EB)),
                RemoteAction("mute", "MUTE", 38000, necPattern(0x20DF906F)),
                RemoteAction("vol_up", "VOL +", 38000, necPattern(0x20DF40BF)),
                RemoteAction("vol_dn", "VOL -", 38000, necPattern(0x20DFC03F)),
                RemoteAction("ch_up", "CH +", 38000, necPattern(0x20DF00FF)),
                RemoteAction("ch_dn", "CH -", 38000, necPattern(0x20DF807F)),
                RemoteAction("ok", "OK", 38000, necPattern(0x20DF22DD)),
                RemoteAction("up", "▲", 38000, necPattern(0x20DF02FD)),
                RemoteAction("down", "▼", 38000, necPattern(0x20DF827D)),
                RemoteAction("left", "◀", 38000, necPattern(0x20DFE01F)),
                RemoteAction("right", "▶", 38000, necPattern(0x20DF609F))
            )
        ),
        RemoteProfile(
            id = "popular_receivers_pro",
            name = "أشهر الرسيفرات",
            brand = "beIN / Spider / Tiger / Starsat / Humax",
            category = DeviceCategory.RECEIVER,
            actions = listOf(
                RemoteAction("pwr", "POWER", 38000, necPattern(0x807F02FD)),
                RemoteAction("pwr_alt", "POWER ALT", 38000, necPattern(0x00FF00FF)),
                RemoteAction("src", "SOURCE / TV-SAT", 38000, necPattern(0x807F32CD)),
                RemoteAction("menu", "MENU", 38000, necPattern(0x807F629D)),
                RemoteAction("exit", "EXIT", 38000, necPattern(0x807F52AD)),
                RemoteAction("mute", "MUTE", 38000, necPattern(0x807F08F7)),
                RemoteAction("vol_up", "VOL +", 38000, necPattern(0x807F0AF5)),
                RemoteAction("vol_dn", "VOL -", 38000, necPattern(0x807F1EE1)),
                RemoteAction("ch_up", "CH +", 38000, necPattern(0x807F12ED)),
                RemoteAction("ch_dn", "CH -", 38000, necPattern(0x807F1AE5)),
                RemoteAction("ok", "OK", 38000, necPattern(0x807F4AB5)),
                RemoteAction("up", "▲", 38000, necPattern(0x807F2AD5)),
                RemoteAction("down", "▼", 38000, necPattern(0x807F7887)),
                RemoteAction("left", "◀", 38000, necPattern(0x807F9E61)),
                RemoteAction("right", "▶", 38000, necPattern(0x807F38C7))
            )
        )
    )

    fun loadProfiles(ctx: Context): List<RemoteProfile> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILES, null) ?: return defaultProfiles()
        return runCatching {
            val arr = JSONArray(raw)
            val result = mutableListOf<RemoteProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cat = runCatching { DeviceCategory.valueOf(obj.optString("category", "CUSTOM")) }
                    .getOrDefault(DeviceCategory.CUSTOM)
                val actArr = obj.optJSONArray("actions") ?: JSONArray()
                val acts = mutableListOf<RemoteAction>()
                for (j in 0 until actArr.length()) {
                    val aObj = actArr.getJSONObject(j)
                    val patArr = aObj.optJSONArray("pattern") ?: JSONArray()
                    val pat = IntArray(patArr.length()) { idx -> patArr.optInt(idx, 560) }
                    acts.add(
                        RemoteAction(
                            id = aObj.optString("id", "act_$j"),
                            label = aObj.optString("label", "زر"),
                            frequencyHz = aObj.optInt("freq", 38000),
                            patternMicros = pat
                        )
                    )
                }
                result.add(
                    RemoteProfile(
                        id = obj.optString("id", "prof_$i"),
                        name = obj.optString("name", "جهاز"),
                        brand = obj.optString("brand", ""),
                        category = cat,
                        actions = acts
                    )
                )
            }
            if (result.isEmpty()) defaultProfiles() else result
        }.getOrElse { defaultProfiles() }
    }

    fun saveProfiles(ctx: Context, profiles: List<RemoteProfile>) {
        val arr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("brand", p.brand)
                put("category", p.category.name)
                val actArr = JSONArray()
                for (a in p.actions) {
                    val aObj = JSONObject().apply {
                        put("id", a.id)
                        put("label", a.label)
                        put("freq", a.frequencyHz)
                        val patArr = JSONArray()
                        for (t in a.patternMicros) patArr.put(t)
                        put("pattern", patArr)
                    }
                    actArr.put(aObj)
                }
                put("actions", actArr)
            }
            arr.put(obj)
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROFILES, arr.toString())
            .apply()
    }
}
