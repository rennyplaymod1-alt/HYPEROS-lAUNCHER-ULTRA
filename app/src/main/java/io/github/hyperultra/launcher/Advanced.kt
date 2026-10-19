package io.github.hyperultra.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

private val LOCK_COLORS = listOf(
    "Blanco" to 0xFFFFFFFF.toInt(),
    "Amarillo" to 0xFFFFE066.toInt(),
    "Cian" to 0xFF7FE7FF.toInt(),
    "Rosa" to 0xFFFF9EC4.toInt(),
)

@Composable
private fun PermRow(title: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                if (granted) "Concedido" else "No concedido",
                fontSize = 12.sp,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * Funciones avanzadas. Cada interruptor solo se puede activar cuando el sistema confirma que el
 * permiso correspondiente está concedido (se vuelve a comprobar al regresar de Ajustes).
 */
@Composable
fun AdvancedSection(activity: MainActivity, s: LauncherSettings) {
    val ctx: Context = activity
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    // tick fuerza la relectura de los permisos al volver de Ajustes
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOk = remember(tick) { NotifListener.isEnabled(ctx) }
    val accOk = remember(tick) { LauncherAccessibility.isEnabled(ctx) }

    var pinDialog by remember { mutableStateOf(false) }
    if (pinDialog) PinDialog(ctx, s) { pinDialog = false }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FUNCIONES AVANZADAS (OPCIONALES)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)

        PermRow("Mostrar sobre otras apps", overlayOk, "Permitir") {
            openSettingsIntent(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
        }
        PermRow("Acceso a notificaciones", notifOk, "Permitir") {
            openSettingsIntent(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermRow("Servicio de accesibilidad", accOk, "Activar") {
            openSettingsIntent(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        ToggleRow("Pantalla de bloqueo propia", s.lockScreen && overlayOk, overlayOk) {
            s.setLockScreen(it); OverlayService.sync(ctx, s)
        }
        ToggleRow("Burbuja flotante (superposición)", s.bubble && overlayOk, overlayOk) {
            s.setBubble(it); OverlayService.sync(ctx, s)
        }
        ToggleRow("Doble toque en inicio para apagar pantalla", s.doubleTapLock && accOk, accOk) {
            s.setDoubleTapLock(it)
        }

        Text("Pantalla de bloqueo", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOCK_COLORS.forEach { (name, c) ->
                FilterChip(selected = s.lockColor == c, onClick = { s.setLockColor(c) }, label = { Text(name) })
            }
        }
        Text("Oscurecer fondo: ${s.lockDim}%", fontSize = 13.sp)
        Slider(value = s.lockDim.toFloat(), onValueChange = { s.setLockDim(it.roundToInt()) }, valueRange = 0f..80f)
        ToggleRow("Mostrar notificaciones en el bloqueo", s.lockShowNotifs, notifOk) { s.setLockShowNotifs(it) }

        Text("Método de desbloqueo (antes del desbloqueo del sistema)", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.unlockMode == 0, onClick = { s.setUnlockMode(0) }, label = { Text("Sistema") })
            FilterChip(selected = s.unlockMode == 1, onClick = { s.setUnlockMode(1) }, label = { Text("Huella / PIN del móvil") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.unlockMode == 2 && s.hasPin,
                enabled = s.hasPin,
                onClick = { s.setUnlockMode(2) },
                label = { Text("PIN propio") },
            )
            OutlinedButton(onClick = { pinDialog = true }) { Text(if (s.hasPin) "Cambiar / quitar PIN" else "Crear PIN propio") }
        }
        Text(
            "Huella/PIN del móvil usa la autenticación del sistema (BiometricPrompt). El PIN propio (4 a 8 dígitos) " +
                "se guarda cifrado con hash; tras 5 fallos se bloquea unos segundos. Es una barrera EXTRA de esta pantalla: " +
                "el desbloqueo del sistema se sigue pidiendo después, y tu PIN real del móvil no se cambia.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = {
            try {
                ctx.startActivity(Intent(ctx, LockScreenActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(ctx, "No se pudo abrir la vista previa", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Vista previa de la pantalla de bloqueo") }

        Text(
            "Límites de Android: esta pantalla de bloqueo se muestra ENCIMA de la del sistema y no puede " +
                "reemplazarla ni saltarse tu PIN, patrón o huella: al desbloquear siempre lo pide el sistema. " +
                "Tras apagar la pantalla con doble toque, la huella/PIN del sistema sigue activa. " +
                "En MIUI/HyperOS puede ser necesario permitir \"Mostrar en pantalla de bloqueo\" y " +
                "\"Inicio automático\" para esta app, y quitarle el ahorro de batería, o el sistema cerrará el servicio.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinDialog(ctx: Context, s: LauncherSettings, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    val hide = PasswordVisualTransformation()
    fun verifyCurrent(): Boolean {
        if (!s.hasPin) return true
        if (s.pinLockedMs() > 0) {
            Toast.makeText(ctx, "Demasiados intentos, espera unos segundos", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!s.checkPin(current)) {
            Toast.makeText(ctx, "PIN actual incorrecto", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (s.hasPin) "Cambiar PIN" else "Crear PIN propio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.hasPin) {
                    OutlinedTextField(current, { current = it.filter(Char::isDigit).take(8) }, label = { Text("PIN actual") },
                        singleLine = true, keyboardOptions = kb, visualTransformation = hide)
                }
                OutlinedTextField(pin1, { pin1 = it.filter(Char::isDigit).take(8) }, label = { Text("Nuevo PIN (4-8 dígitos)") },
                    singleLine = true, keyboardOptions = kb, visualTransformation = hide)
                OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, label = { Text("Repetir PIN") },
                    singleLine = true, keyboardOptions = kb, visualTransformation = hide)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin1.length < 4 -> Toast.makeText(ctx, "Mínimo 4 dígitos", Toast.LENGTH_SHORT).show()
                    pin1 != pin2 -> Toast.makeText(ctx, "Los PIN no coinciden", Toast.LENGTH_SHORT).show()
                    verifyCurrent() -> {
                        if (s.setPin(pin1)) { s.setUnlockMode(2); onDone() }
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (s.hasPin) {
                    TextButton(onClick = { if (verifyCurrent()) { s.clearPin(); onDone() } }) { Text("Quitar PIN") }
                }
                TextButton(onClick = onDone) { Text("Cancelar") }
            }
        },
    )
}
