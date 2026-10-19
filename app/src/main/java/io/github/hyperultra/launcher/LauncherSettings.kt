package io.github.hyperultra.launcher

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class Preset(val name: String, val color: Int, val theme: Int)

val DEFAULT_ACCENT: Int = 0xFF3478F6.toInt()

val PRESETS = listOf(
    Preset("Azul Hyper", DEFAULT_ACCENT, 0),
    Preset("Coral", 0xFFFF6B57.toInt(), 1),
    Preset("Bosque", 0xFF2E9E6B.toInt(), 2),
    Preset("Violeta", 0xFF7C5CFC.toInt(), 2),
    Preset("Grafito", 0xFF5F6B7A.toInt(), 2),
)

/** Ajustes persistentes (SharedPreferences) expuestos como estado de Compose. */
class LauncherSettings(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    var columns by mutableIntStateOf(sp.getInt("columns", 4))
        private set
    var iconDp by mutableIntStateOf(sp.getInt("icon_dp", 52))
        private set
    /** 0 = sistema, 1 = claro, 2 = oscuro */
    var theme by mutableIntStateOf(sp.getInt("theme", 0))
        private set
    var accent by mutableIntStateOf(sp.getInt("accent", DEFAULT_ACCENT))
        private set
    /** Modo Lite: sin desenfoque ni animaciones. Activado por defecto. */
    var lite by mutableStateOf(sp.getBoolean("lite", true))
        private set
    var showLabels by mutableStateOf(sp.getBoolean("labels", true))
        private set
    /** 0 = Centro de control, 1 = buscar apps */
    var swipeDown by mutableIntStateOf(sp.getInt("swipe_down", 0))
        private set
    var hidden: Set<String> by mutableStateOf(sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet())
        private set
    /** null = dock por defecto (según apps predeterminadas del sistema). */
    var dock: List<String>? by mutableStateOf(readDock())
        private set
    var note by mutableStateOf(sp.getString("note", "") ?: "")
        private set

    // --- Funciones avanzadas (todas desactivadas por defecto) ---
    /** Pantalla de bloqueo propia: se muestra al encender la pantalla, encima de la del sistema. */
    var lockScreen by mutableStateOf(sp.getBoolean("lock_screen", false))
        private set
    /** Burbuja flotante (superposición) con accesos rápidos sobre otras apps. */
    var bubble by mutableStateOf(sp.getBoolean("bubble", false))
        private set
    /** Doble toque en el inicio para apagar la pantalla (requiere el servicio de accesibilidad). */
    var doubleTapLock by mutableStateOf(sp.getBoolean("double_tap_lock", false))
        private set
    /** Color del reloj de la pantalla de bloqueo (ARGB). */
    var lockColor by mutableIntStateOf(sp.getInt("lock_color", 0xFFFFFFFF.toInt()))
        private set
    /** Oscurecimiento del fondo de la pantalla de bloqueo, 0..80 (%). */
    var lockDim by mutableIntStateOf(sp.getInt("lock_dim", 35))
        private set
    var lockShowNotifs by mutableStateOf(sp.getBoolean("lock_notifs", true))
        private set
    /** 0 = solo el desbloqueo del sistema, 1 = huella/rostro/PIN del dispositivo primero, 2 = PIN propio primero. */
    var unlockMode by mutableIntStateOf(sp.getInt("unlock_mode", 0))
        private set
    var hasPin by mutableStateOf(sp.getString("pin_hash", null) != null)
        private set

    private fun readDock(): List<String>? {
        val raw = sp.getString("dock", null) ?: return null
        if (raw == "-") return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }
    }

    fun setColumns(v: Int) { columns = v.coerceIn(3, 6); sp.edit().putInt("columns", columns).apply() }
    fun setIconDp(v: Int) { iconDp = v.coerceIn(40, 72); sp.edit().putInt("icon_dp", iconDp).apply() }
    fun setTheme(v: Int) { theme = v.coerceIn(0, 2); sp.edit().putInt("theme", theme).apply() }
    fun setAccent(v: Int) { accent = v; sp.edit().putInt("accent", v).apply() }
    fun setLite(v: Boolean) { lite = v; sp.edit().putBoolean("lite", v).apply() }
    fun setLabels(v: Boolean) { showLabels = v; sp.edit().putBoolean("labels", v).apply() }
    fun setSwipeDown(v: Int) { swipeDown = v.coerceIn(0, 1); sp.edit().putInt("swipe_down", swipeDown).apply() }
    fun setNote(v: String) { note = v; sp.edit().putString("note", v).apply() }
    fun setLockScreen(v: Boolean) { lockScreen = v; sp.edit().putBoolean("lock_screen", v).apply() }
    fun setBubble(v: Boolean) { bubble = v; sp.edit().putBoolean("bubble", v).apply() }
    fun setDoubleTapLock(v: Boolean) { doubleTapLock = v; sp.edit().putBoolean("double_tap_lock", v).apply() }
    fun setLockColor(v: Int) { lockColor = v; sp.edit().putInt("lock_color", v).apply() }
    fun setLockDim(v: Int) { lockDim = v.coerceIn(0, 80); sp.edit().putInt("lock_dim", lockDim).apply() }
    fun setUnlockMode(v: Int) { unlockMode = v.coerceIn(0, 2); sp.edit().putInt("unlock_mode", unlockMode).apply() }

    // ---- PIN propio: solo se guarda un hash PBKDF2 con sal aleatoria (nunca el PIN) ----
    private fun pinHash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 30000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Guarda un PIN de 4 a 8 dígitos. Devuelve false si el formato no es válido. */
    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", pinHash(pin, salt))
            .putInt("pin_fails", 0).putLong("pin_lock_until", 0L)
            .apply()
        hasPin = true
        return true
    }

    fun clearPin() {
        sp.edit().remove("pin_salt").remove("pin_hash").putInt("pin_fails", 0).putLong("pin_lock_until", 0L).apply()
        hasPin = false
        if (unlockMode == 2) setUnlockMode(0)
    }

    /** Milisegundos restantes de bloqueo por intentos fallidos (0 = se puede intentar). */
    fun pinLockedMs(): Long = (sp.getLong("pin_lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    /** Comprueba el PIN. Tras 5 fallos seguidos bloquea los intentos 30 s (se duplica en cada ronda). */
    fun checkPin(pin: String): Boolean {
        if (pinLockedMs() > 0) return false
        val saltB64 = sp.getString("pin_salt", null) ?: return false
        val saved = sp.getString("pin_hash", null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(pinHash(pin, salt).toByteArray(), saved.toByteArray())
        if (ok) {
            sp.edit().putInt("pin_fails", 0).putLong("pin_lock_until", 0L).apply()
        } else {
            val fails = sp.getInt("pin_fails", 0) + 1
            val e = sp.edit().putInt("pin_fails", fails)
            if (fails % 5 == 0) {
                val round = (fails / 5).coerceAtMost(6)
                e.putLong("pin_lock_until", System.currentTimeMillis() + 30_000L * (1L shl (round - 1)))
            }
            e.apply()
        }
        return ok
    }

    fun setLockShowNotifs(v: Boolean) { lockShowNotifs = v; sp.edit().putBoolean("lock_notifs", v).apply() }

    fun applyPreset(p: Preset) { setAccent(p.color); setTheme(p.theme) }

    fun hide(id: String) {
        hidden = hidden + id
        sp.edit().putStringSet("hidden", hidden).apply()
        dock?.let { d -> if (id in d) setDock(d - id) }
    }

    fun unhide(id: String) {
        hidden = hidden - id
        sp.edit().putStringSet("hidden", hidden).apply()
    }

    fun setDock(ids: List<String>) {
        dock = ids
        sp.edit().putString("dock", if (ids.isEmpty()) "-" else ids.joinToString("|")).apply()
    }

    fun resetAppearance() {
        setColumns(4); setIconDp(52); setTheme(0); setAccent(DEFAULT_ACCENT)
        setLite(true); setLabels(true); setSwipeDown(0)
        setLockColor(0xFFFFFFFF.toInt()); setLockDim(35); setLockShowNotifs(true)
        dock = null
        sp.edit().remove("dock").apply()
    }

    fun exportJson(): String = JSONObject().apply {
        put("v", 1)
        put("columns", columns)
        put("iconDp", iconDp)
        put("theme", theme)
        put("accent", accent)
        put("lite", lite)
        put("labels", showLabels)
        put("swipeDown", swipeDown)
        put("lockColor", lockColor)
        put("lockDim", lockDim)
        put("lockNotifs", lockShowNotifs)
    }.toString()

    fun importJson(text: String): Boolean = try {
        val o = JSONObject(text)
        if (o.optInt("v", 0) != 1) throw IllegalArgumentException("versión no soportada")
        setColumns(o.optInt("columns", columns))
        setIconDp(o.optInt("iconDp", iconDp))
        setTheme(o.optInt("theme", theme))
        setAccent(o.optInt("accent", accent))
        setLite(o.optBoolean("lite", lite))
        setLabels(o.optBoolean("labels", showLabels))
        setSwipeDown(o.optInt("swipeDown", swipeDown))
        setLockColor(o.optInt("lockColor", lockColor))
        setLockDim(o.optInt("lockDim", lockDim))
        setLockShowNotifs(o.optBoolean("lockNotifs", lockShowNotifs))
        true
    } catch (e: Exception) {
        false
    }
}
