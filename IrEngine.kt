package com.example.remot

import android.content.Context
import android.hardware.ConsumerIrManager
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
            "تم إرسال (${action.label}) • ${action.frequencyHz} Hz"
        }
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

    fun quickSweepActions(): List<RemoteAction> = listOf(
        RemoteAction("sw_1", "تجربة 1 (LG/Display)", 38000, necPattern(0x20DF10EF)),
        RemoteAction("sw_2", "تجربة 2 (Samsung)", 38000, necPattern(0xE0E040BF)),
        RemoteAction("sw_3", "تجربة 3 (Sony)", 40000, necPattern(0x00A90A95)),
        RemoteAction("sw_4", "تجربة 4 (Panasonic)", 36700, necPattern(0x40040100BCBD)),
        RemoteAction("sw_5", "تجربة 5 (Receiver A)", 38000, necPattern(0x807F02FD)),
        RemoteAction("sw_6", "تجربة 6 (Receiver B)", 38000, necPattern(0x00FF00FF)),
        RemoteAction("sw_7", "تجربة 7 (Source/HDMI)", 38000, necPattern(0x20DFD02F)),
        RemoteAction("sw_8", "تجربة 8 (Mute)", 38000, necPattern(0x20DF906F))
    )

    fun defaultProfiles(): List<RemoteProfile> = listOf(
        RemoteProfile(
            id = "campus_displays_pro",
            name = "شاشات الجامعات والمعاهد",
            brand = "LG / Samsung / NEC / Panasonic / Sony",
            category = DeviceCategory.TV,
            actions = listOf(
                RemoteAction("d_pwr", "تشغيل / إطفاء", 38000, necPattern(0x20DF10EF)),
                RemoteAction("d_pwr_alt", "باور بديل (Samsung)", 38000, necPattern(0xE0E040BF)),
                RemoteAction("d_src", "تغيير المدخل (HDMI/Input)", 38000, necPattern(0x20DFD02F)),
                RemoteAction("d_src_alt", "مصدر بديل (Source)", 38000, necPattern(0xE0E0807F)),
                RemoteAction("d_vol_up", "رفع الصوت +", 38000, necPattern(0x20DF40BF)),
                RemoteAction("d_vol_dn", "خفض الصوت -", 38000, necPattern(0x20DFC03F)),
                RemoteAction("d_mute", "كتم الصوت", 38000, necPattern(0x20DF906F)),
                RemoteAction("d_menu", "القائمة (Menu)", 38000, necPattern(0x20DFC23D))
            )
        ),
        RemoteProfile(
            id = "popular_receivers_pro",
            name = "أشهر الرسيفرات",
            brand = "beIN / Spider / Tiger / Starsat / Humax",
            category = DeviceCategory.RECEIVER,
            actions = listOf(
                RemoteAction("r_pwr", "باور الرسيفر", 38000, necPattern(0x807F02FD)),
                RemoteAction("r_pwr_alt", "باور رسيفر بديل", 38000, necPattern(0x00FF00FF)),
                RemoteAction("r_ok", "موافق (OK)", 38000, necPattern(0x807F4AB5)),
                RemoteAction("r_ch_up", "قناة للأعلى ▲", 38000, necPattern(0x807F12ED)),
                RemoteAction("r_ch_dn", "قناة للأسفل ▼", 38000, necPattern(0x807F1AE5)),
                RemoteAction("r_vol_up", "صوت +", 38000, necPattern(0x807F0AF5)),
                RemoteAction("r_vol_dn", "صوت -", 38000, necPattern(0x807F1EE1)),
                RemoteAction("r_exit", "خروج / رجوع (Exit)", 38000, necPattern(0x807F52AD)),
                RemoteAction("r_menu", "القائمة الرئيسية", 38000, necPattern(0x807F629D)),
                RemoteAction("r_info", "معلومات القناة (Info)", 38000, necPattern(0x807F22DD))
            )
        ),
        RemoteProfile(
            id = "quick_sweep_profile",
            name = "مسح وتجربة سريعة",
            brand = "اختبار متعدد للشاشات والرسيفرات",
            category = DeviceCategory.CUSTOM,
            actions = quickSweepActions()
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
