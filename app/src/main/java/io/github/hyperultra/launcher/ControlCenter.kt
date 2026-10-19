package io.github.hyperultra.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun mediaKey(am: AudioManager, key: Int) {
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
}

@Composable
private fun Tile(
    icon: String, label: String, sub: String, active: Boolean,
    modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Text(label, color = fg, fontSize = 12.sp, maxLines = 1)
        Text(sub, color = fg.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1)
    }
}

/**
 * Centro de control. Android no permite a una app normal activar Wi-Fi, Bluetooth ni modo avión:
 * esos mosaicos abren el panel/ajuste oficial y NO muestran estado de encendido. Solo la linterna
 * (estado confirmado por el sistema), el volumen y el brillo (con permiso) son controles directos.
 */
@Composable
fun ControlCenter(activity: MainActivity, s: LauncherSettings, onClose: () -> Unit) {
    val ctx: Context = activity
    val status by rememberStatus()
    val d = Date(status.time)
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(ctx) }
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()) }
    val dateFmt = remember {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault())
    }

    // --- Linterna: el estado viene del callback del sistema
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    val torchId = remember {
        runCatching {
            cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(torchId) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        cm.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { cm.unregisterTorchCallback(cb) }
    }

    // --- Brillo (requiere WRITE_SETTINGS concedido por el usuario)
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(ctx)) }
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) canWrite = Settings.System.canWrite(ctx)
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    var brightness by remember {
        mutableFloatStateOf(Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toFloat())
    }

    // --- Volumen multimedia
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (s.lite) 0.98f else 0.88f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(timeFmt.format(d), fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text(dateFmt.format(d), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("📶", "Wi-Fi", "Abrir panel", false, Modifier.weight(1f)) {
                    val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    else Intent(Settings.ACTION_WIFI_SETTINGS)
                    openSettingsIntent(ctx, i, Intent(Settings.ACTION_WIFI_SETTINGS))
                }
                Tile("🔵", "Bluetooth", "Abrir ajustes", false, Modifier.weight(1f)) {
                    openSettingsIntent(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
                Tile("✈️", "Avión", "Abrir ajustes", false, Modifier.weight(1f)) {
                    openSettingsIntent(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                }
                Tile(
                    "🔦", "Linterna",
                    if (torchId == null) "No disponible" else if (torchOn) "Encendida" else "Apagada",
                    torchOn, Modifier.weight(1f), enabled = torchId != null,
                ) {
                    try {
                        cm.setTorchMode(torchId!!, !torchOn)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "No se pudo cambiar la linterna", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("🔋", "Ahorro", "Abrir ajustes", false, Modifier.weight(1f)) {
                    openSettingsIntent(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                }
                Tile("⚙️", "Ajustes", "Sistema", false, Modifier.weight(1f)) {
                    openSettingsIntent(ctx, Intent(Settings.ACTION_SETTINGS))
                }
                Spacer(Modifier.weight(2f))
            }

            Column {
                Text("Brillo", fontSize = 13.sp)
                if (canWrite) {
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            try {
                                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
                            } catch (e: Exception) { }
                        },
                        valueRange = 10f..255f,
                    )
                } else {
                    OutlinedButton(onClick = {
                        openSettingsIntent(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }) { Text("Permitir controlar el brillo") }
                }
            }

            Column {
                Text("Volumen multimedia", fontSize = 13.sp)
                Slider(
                    value = vol,
                    onValueChange = {
                        vol = it
                        try { am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) } catch (e: Exception) { }
                    },
                    valueRange = 0f..maxVol.toFloat(),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { mediaKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }) { Text("⏮") }
                Button(onClick = { mediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }) { Text("⏯") }
                OutlinedButton(onClick = { mediaKey(am, KeyEvent.KEYCODE_MEDIA_NEXT) }) { Text("⏭") }
            }

            // --- Música en curso y notificaciones (requieren "Acceso a notificaciones")
            val notifOk = remember(canWrite) { NotifListener.isEnabled(ctx) }
            val media = NotifListener.media.value
            if (media != null) {
                Column {
                    Text(if (media.playing) "Reproduciendo" else "En pausa", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        media.title + if (media.artist.isNotBlank()) " · ${media.artist}" else "",
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!notifOk) {
                OutlinedButton(onClick = {
                    openSettingsIntent(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Permitir ver notificaciones") }
            } else {
                val list = NotifListener.items.value
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (list.isEmpty()) "Sin notificaciones" else "Notificaciones (${list.size})",
                        fontSize = 13.sp, modifier = Modifier.weight(1f),
                    )
                    if (list.isNotEmpty()) TextButton(onClick = { NotifListener.dismissAll() }) { Text("Borrar todo") }
                }
                list.take(3).forEach { n ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                try { n.contentIntent?.send() } catch (e: Exception) { }
                                onClose()
                            }
                            .padding(10.dp)
                    ) {
                        Text(n.app, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(
                            listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" — "),
                            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
