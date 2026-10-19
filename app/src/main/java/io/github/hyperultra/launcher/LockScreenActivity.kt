package io.github.hyperultra.launcher

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de bloqueo propia. NO sustituye ni elude la del sistema: se muestra por encima de ella
 * (como lo hace una app de cámara) y para desbloquear siempre se llama a requestDismissKeyguard,
 * que obliga al sistema a pedir tu PIN/patrón/huella. Se cierra sola al apagarse la pantalla.
 */
class LockScreenActivity : FragmentActivity() {
    private lateinit var settings: LauncherSettings
    /** true mientras el sistema muestra su PIN/huella: no cerrar la actividad hasta recibir el resultado. */
    private var dismissing = false
    private val pinRequested = mutableStateOf(false)
    private var pendingAfter: (() -> Unit)? = null

    private val offReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        settings = LauncherSettings(this)
        ContextCompat.registerReceiver(
            this, offReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(settings.accent))) {
                LockContent(
                    s = settings,
                    showPin = pinRequested.value,
                    onUnlock = { requestUnlock(null) },
                    onShortcut = { intent -> requestUnlock { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onNotif = { pi -> requestUnlock { try { pi?.send() } catch (e: Exception) { } } },
                    onPinSubmit = { pin -> submitPin(pin) },
                    onPinCancel = { pinRequested.value = false; pendingAfter = null },
                )
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(offReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    /** Cualquier salida de la pantalla (Inicio, Recientes, otra app) la cierra: nunca atrapa al usuario. */
    override fun onStop() {
        super.onStop()
        if (!dismissing) finish()
    }

    /**
     * Primera barrera (opcional) y después SIEMPRE el desbloqueo del sistema:
     * 1 = huella/rostro/PIN del dispositivo (BiometricPrompt), 2 = PIN propio de la app.
     * Si la barrera elegida no está disponible, se pasa directamente al desbloqueo del sistema.
     */
    private fun requestUnlock(after: (() -> Unit)?) {
        when {
            settings.unlockMode == 1 && Build.VERSION.SDK_INT >= 28 && biometricAvailable() -> promptBiometric(after)
            settings.unlockMode == 2 && settings.hasPin -> { pendingAfter = after; pinRequested.value = true }
            else -> unlock(after)
        }
    }

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric(after: (() -> Unit)?) {
        dismissing = true // el diálogo del sistema pausa esta pantalla; no cerrarla mientras tanto
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock(after)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    dismissing = false // cancelado o bloqueado: se queda en esta pantalla
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear")
            .setSubtitle("Huella, rostro o PIN del dispositivo")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            dismissing = false
            unlock(after)
        }
    }

    /** true si el PIN es correcto (y entonces pasa al desbloqueo del sistema). */
    private fun submitPin(pin: String): Boolean {
        if (!settings.checkPin(pin)) return false
        pinRequested.value = false
        val after = pendingAfter
        pendingAfter = null
        unlock(after)
        return true
    }

    /** Pide desbloqueo al sistema y solo entonces ejecuta [after]. */
    private fun unlock(after: (() -> Unit)?) {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) {
            after?.invoke(); finish(); return
        }
        dismissing = true
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                try { after?.invoke() } catch (e: Exception) { }
                finish()
            }
            override fun onDismissError() { dismissing = false }
            override fun onDismissCancelled() { dismissing = false }
        })
    }
}

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
private fun LockContent(
    s: LauncherSettings,
    showPin: Boolean,
    onPinSubmit: (String) -> Boolean,
    onPinCancel: () -> Unit,
    onUnlock: () -> Unit,
    onShortcut: (Intent) -> Unit,
    onNotif: (PendingIntent?) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val status by rememberStatus()
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(ctx) }
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dateFmt = remember {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault())
    }
    val d = Date(status.time)
    val clock = Color(s.lockColor)
    val notifs = NotifListener.items.value
    val media = NotifListener.media.value
    val showNotifs = s.lockShowNotifs && NotifListener.isEnabled(ctx)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = s.lockDim / 100f))
            .pointerInput(Unit) {
                val thr = 80.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -thr) onUnlock() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(dateFmt.format(d), color = clock.copy(alpha = 0.9f), fontSize = 16.sp)
            Text(timeFmt.format(d), color = clock, fontSize = 96.sp, fontWeight = FontWeight.Thin)
            if (status.battery >= 0) {
                Text(
                    (if (status.charging) "⚡ " else "") + "${status.battery}%",
                    color = clock.copy(alpha = 0.8f), fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(20.dp))

            if (media != null) {
                Card {
                    Text(if (media.playing) "▶ Reproduciendo" else "⏸ En pausa", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    Text(media.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (media.artist.isNotBlank()) {
                        Text(media.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showNotifs) {
                notifs.take(4).forEach { n ->
                    Card(Modifier.clickable { onNotif(n.contentIntent) }) {
                        Text(n.app, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                        if (n.title.isNotBlank()) {
                            Text(n.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (n.text.isNotBlank()) {
                            Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (notifs.size > 4) {
                    Text("+${notifs.size - 4} más", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RoundShortcut("📞") { onShortcut(Intent(Intent.ACTION_DIAL)) }
                Text(
                    when {
                        s.unlockMode == 1 -> "Desliza hacia arriba · huella o PIN"
                        s.unlockMode == 2 && s.hasPin -> "Desliza hacia arriba · PIN"
                        else -> "Desliza hacia arriba para desbloquear"
                    },
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                RoundShortcut("📷") { onShortcut(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 120.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.7f))
            )
        }
        if (showPin) PinPad(s, onPinSubmit, onPinCancel)
    }
}

@Composable
private fun PinPad(s: LauncherSettings, onSubmit: (String) -> Boolean, onCancel: () -> Unit) {
    var pin by remember { androidx.compose.runtime.mutableStateOf("") }
    var msg by remember { androidx.compose.runtime.mutableStateOf("") }
    var wait by remember { androidx.compose.runtime.mutableLongStateOf(s.pinLockedMs()) }
    // cuenta atrás del bloqueo por intentos fallidos
    LaunchedEffect(wait > 0) {
        while (wait > 0) {
            kotlinx.coroutines.delay(500)
            wait = s.pinLockedMs()
        }
    }
    fun submit() {
        if (wait > 0 || pin.length < 4) return
        if (!onSubmit(pin)) {
            pin = ""
            wait = s.pinLockedMs()
            msg = if (wait > 0) "Demasiados intentos" else "PIN incorrecto"
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Introduce tu PIN", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (pin.isEmpty()) "—" else "●".repeat(pin.length),
            color = Color.White, fontSize = 26.sp, letterSpacing = 6.sp,
        )
        Text(
            if (wait > 0) "Espera ${(wait + 999) / 1000} s" else msg,
            color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.height(24.dp),
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier
                            .padding(vertical = 8.dp)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                msg = ""
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "OK" -> submit()
                                    else -> if (pin.length < 8) pin += k
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, color = Color.White, fontSize = 22.sp) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) { Text("Cancelar", color = Color.White.copy(alpha = 0.8f)) }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun RoundShortcut(icon: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 22.sp) }
}
