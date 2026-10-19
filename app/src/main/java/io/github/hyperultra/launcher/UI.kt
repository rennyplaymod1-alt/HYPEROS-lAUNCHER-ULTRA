package io.github.hyperultra.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat as AndroidDateFormat
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Screen { Home, Drawer, Settings }

// ---------------------------------------------------------------- utilidades

@Composable
fun Modifier.blockTouches(): Modifier =
    this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})

/** Desenfoque real del fondo (solo Android 12+ y si el sistema lo permite). Radio 0 = sin desenfoque. */
fun setWindowBlur(activity: Activity, radiusPx: Int) {
    if (Build.VERSION.SDK_INT < 31) return
    val wm = activity.getSystemService(WindowManager::class.java)
    if (radiusPx > 0 && !wm.isCrossWindowBlurEnabled) return
    val w = activity.window
    if (radiusPx > 0) w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    else w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    val lp = w.attributes
    lp.blurBehindRadius = radiusPx
    w.attributes = lp
}

fun openSettingsIntent(ctx: Context, intent: Intent, fallback: Intent? = null) {
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        try {
            if (fallback != null) ctx.startActivity(fallback)
            else Toast.makeText(ctx, "No disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        } catch (e2: Exception) {
            Toast.makeText(ctx, "No disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }
}

data class Status(val time: Long, val battery: Int, val charging: Boolean)

fun readStatus(c: Context): Status {
    val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
    return Status(System.currentTimeMillis(), pct, st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}

/** Hora y batería actualizadas por broadcasts del sistema, solo mientras la UI está visible. */
@Composable
fun rememberStatus(): State<Status> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(readStatus(ctx)) }
    DisposableEffect(ctx) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { state.value = readStatus(c) }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = readStatus(ctx)
        onDispose { ctx.unregisterReceiver(r) }
    }
    return state
}

// ---------------------------------------------------------------- raíz

@Composable
fun LauncherRoot(activity: MainActivity, s: LauncherSettings, apps: List<AppEntry>, homeSignal: Int) {
    val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (dark) darkColorScheme(primary = Color(s.accent)) else lightColorScheme(primary = Color(s.accent))
    MaterialTheme(colorScheme = scheme) {
        var screen by remember { mutableStateOf(Screen.Home) }
        var control by remember { mutableStateOf(false) }
        var focusSearch by remember { mutableStateOf(false) }
        val pm = activity.packageManager
        val dockApps = remember(apps, s.dock) { resolveDock(pm, s.dock, apps) }

        LaunchedEffect(homeSignal) { screen = Screen.Home; control = false }

        val blur = !s.lite && (screen != Screen.Home || control)
        DisposableEffect(blur) {
            setWindowBlur(activity, if (blur) 60 else 0)
            onDispose { }
        }
        BackHandler(enabled = control || screen != Screen.Home) {
            if (control) control = false else screen = Screen.Home
        }

        val enterUp: EnterTransition = if (s.lite) EnterTransition.None else fadeIn() + slideInVertically { it / 8 }
        val exitDown: ExitTransition = if (s.lite) ExitTransition.None else fadeOut() + slideOutVertically { it / 8 }
        val enterDown: EnterTransition = if (s.lite) EnterTransition.None else fadeIn() + slideInVertically { -it }
        val exitUp: ExitTransition = if (s.lite) ExitTransition.None else fadeOut() + slideOutVertically { -it }

        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                s = s, dockApps = dockApps,
                onDrawer = { focusSearch = false; screen = Screen.Drawer },
                onSwipeDown = {
                    if (s.swipeDown == 0) control = true else { focusSearch = true; screen = Screen.Drawer }
                },
                onSettings = { screen = Screen.Settings },
            )
            AnimatedVisibility(screen == Screen.Drawer, enter = enterUp, exit = exitDown) {
                DrawerScreen(s, apps, dockApps, focusSearch, onFocusConsumed = { focusSearch = false }, onClose = { screen = Screen.Home })
            }
            AnimatedVisibility(screen == Screen.Settings, enter = enterUp, exit = exitDown) {
                SettingsScreen(activity, s, apps, onClose = { screen = Screen.Home })
            }
            AnimatedVisibility(control, enter = enterDown, exit = exitUp) {
                ControlCenter(activity, s, onClose = { control = false })
            }
        }
    }
}

// ---------------------------------------------------------------- icono

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    app: AppEntry,
    s: LauncherSettings,
    showLabel: Boolean,
    labelColor: Color,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    afterLaunch: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val sizeDp = s.iconDp
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    val bmp by produceState<ImageBitmap?>(IconCache.peek(app, px), app.id, px) {
        value = IconCache.load(ctx.packageManager, app, px)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { launchApp(ctx, view, app, !s.lite); afterLaunch() },
                onLongClick = onLongClick,
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(sizeDp.dp)) {
            bmp?.let { Image(bitmap = it, contentDescription = app.label, modifier = Modifier.fillMaxSize()) }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = labelColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width((sizeDp + 12).dp),
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 3f)),
            )
        }
    }
}

// ---------------------------------------------------------------- inicio

@Composable
fun HomeScreen(
    s: LauncherSettings,
    dockApps: List<AppEntry>,
    onDrawer: () -> Unit,
    onSwipeDown: () -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val status by rememberStatus()
    val is24 = remember { AndroidDateFormat.is24HourFormat(ctx) }
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dateFmt = remember {
        SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault())
    }
    val dayFmt = remember { SimpleDateFormat("d", Locale.getDefault()) }
    val dowFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val d = Date(status.time)
    var editNote by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .pointerInput(s.doubleTapLock) {
                detectTapGestures(
                    onLongPress = { onSettings() },
                    onDoubleTap = {
                        if (s.doubleTapLock) {
                            if (!LauncherAccessibility.lockScreen()) {
                                Toast.makeText(ctx, R.string.need_accessibility, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                val thr = 56.dp.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -thr) onDrawer() else if (total > thr) onSwipeDown() },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(timeFmt.format(d), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
            Text(dateFmt.format(d), color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetCard(Modifier.weight(1f)) {
                    Text("Batería", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(
                        if (status.battery >= 0) "${status.battery}%" else "--",
                        color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium
                    )
                    Text(if (status.charging) "Cargando" else "En uso", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                WidgetCard(Modifier.weight(1f)) {
                    Text(dowFmt.format(d).uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(dayFmt.format(d), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    Text("Hoy", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            WidgetCard(Modifier.fillMaxWidth().clickable { editNote = true }) {
                Text("Nota", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(
                    s.note.ifBlank { "Toca para escribir una nota" },
                    color = Color.White, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.weight(1f))

            if (dockApps.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    dockApps.forEach { AppIcon(it, s, showLabel = false, labelColor = Color.White, onLongClick = onSettings) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 18.dp)
                    .clickable(onClick = onDrawer),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.7f)))
            }
        }
    }

    if (editNote) {
        var text by remember { mutableStateOf(s.note) }
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("Nota") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3) },
            confirmButton = { TextButton(onClick = { s.setNote(text); editNote = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun WidgetCard(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(16.dp)
    ) { content() }
}

// ---------------------------------------------------------------- cajón

@Composable
fun DrawerScreen(
    s: LauncherSettings,
    apps: List<AppEntry>,
    dockApps: List<AppEntry>,
    focusSearch: Boolean,
    onFocusConsumed: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<AppEntry?>(null) }
    val visible = remember(apps, s.hidden, query) {
        val q = query.trim()
        apps.filter { it.id !in s.hidden && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) { focus.requestFocus(); onFocusConsumed() }
    }
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (s.lite) 0.97f else 0.82f))
            .blockTouches()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar apps") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                visible.firstOrNull()?.let { launchApp(ctx, view, it, !s.lite); onClose() }
            }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(s.columns),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(visible, key = { it.id }) { app ->
                AppIcon(app, s, showLabel = s.showLabels, labelColor = textColor, onLongClick = { menu = app }, afterLaunch = onClose)
            }
        }
    }

    menu?.let { app ->
        val inDock = dockApps.any { it.id == app.id }
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(onClick = {
                        openSettingsIntent(
                            ctx,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.component.packageName}"))
                        )
                        menu = null
                    }) { Text("Info de la app / desinstalar") }
                    if (inDock) {
                        TextButton(onClick = { s.setDock(dockApps.filter { it.id != app.id }.map { it.id }); menu = null }) {
                            Text("Quitar del dock")
                        }
                    } else if (dockApps.size < 5) {
                        TextButton(onClick = { s.setDock((dockApps + app).map { it.id }); menu = null }) {
                            Text("Añadir al dock")
                        }
                    }
                    TextButton(onClick = { s.hide(app.id); menu = null }) { Text("Ocultar") }
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("Cerrar") } },
        )
    }
}

// ---------------------------------------------------------------- ajustes

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
}

@Composable
fun SettingsScreen(activity: MainActivity, s: LauncherSettings, apps: List<AppEntry>, onClose: () -> Unit) {
    var showHidden by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouches()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ Atrás") }
            Text("Ajustes del launcher", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SectionTitle("TEMAS PREDISEÑADOS")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { p ->
                        FilterChip(
                            selected = s.accent == p.color && s.theme == p.theme,
                            onClick = { s.applyPreset(p) },
                            label = { Text(p.name) },
                        )
                    }
                }
            }
            item {
                SectionTitle("APARIENCIA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sistema", "Claro", "Oscuro").forEachIndexed { i, n ->
                        FilterChip(selected = s.theme == i, onClick = { s.setTheme(i) }, label = { Text(n) })
                    }
                }
            }
            item {
                Text("Columnas del cajón: ${s.columns}")
                Slider(value = s.columns.toFloat(), onValueChange = { s.setColumns(it.roundToInt()) }, valueRange = 3f..6f, steps = 2)
                Text("Tamaño de iconos: ${s.iconDp} dp")
                Slider(value = s.iconDp.toFloat(), onValueChange = { s.setIconDp(it.roundToInt()) }, valueRange = 40f..72f)
                SwitchRow("Mostrar nombres de apps", s.showLabels) { s.setLabels(it) }
            }
            item {
                SectionTitle("RENDIMIENTO")
                SwitchRow("Modo Lite (sin desenfoque ni animaciones)", s.lite) { s.setLite(it) }
                Text(
                    "El desenfoque real solo funciona en Android 12+ si el sistema lo permite.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SectionTitle("GESTOS")
                Text("Deslizar hacia abajo en inicio:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.swipeDown == 0, onClick = { s.setSwipeDown(0) }, label = { Text("Centro de control") })
                    FilterChip(selected = s.swipeDown == 1, onClick = { s.setSwipeDown(1) }, label = { Text("Buscar apps") })
                }
                Text(
                    "Deslizar hacia arriba abre el cajón. Mantener pulsado en inicio abre estos ajustes.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { AdvancedSection(activity, s) }
            item {
                SectionTitle("SISTEMA")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        openSettingsIntent(activity, Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                    }) { Text("Launcher predeterminado") }
                    OutlinedButton(onClick = {
                        openSettingsIntent(activity, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
                    }) { Text("Fondo") }
                }
            }
            item {
                SectionTitle("APPS OCULTAS")
                OutlinedButton(onClick = { activity.requireAuth { ok -> if (ok) showHidden = true } }) {
                    Text("Administrar (pide tu bloqueo de pantalla)")
                }
            }
            item {
                SectionTitle("COPIA DE CONFIGURACIÓN")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val cm = activity.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("tema", s.exportJson()))
                        Toast.makeText(activity, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }) { Text("Exportar") }
                    OutlinedButton(onClick = {
                        val cm = activity.getSystemService(ClipboardManager::class.java)
                        val clip = cm.primaryClip
                        val t = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(activity).toString() else ""
                        val ok = s.importJson(t)
                        Toast.makeText(activity, if (ok) "Configuración importada" else "El portapapeles no contiene una configuración válida", Toast.LENGTH_SHORT).show()
                    }) { Text("Importar") }
                    OutlinedButton(onClick = { s.resetAppearance() }) { Text("Restablecer") }
                }
            }
            item {
                Text(
                    "Hyper Ultra Launcher es un proyecto independiente inspirado en el estilo de HyperOS. No es software oficial de Xiaomi.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showHidden) {
        val hiddenApps = apps.filter { it.id in s.hidden }
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text("Apps ocultas") },
            text = {
                if (hiddenApps.isEmpty()) Text("No hay apps ocultas.")
                else LazyColumn {
                    items(hiddenApps, key = { it.id }) { app ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { s.unhide(app.id) }) { Text("Mostrar") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHidden = false }) { Text("Cerrar") } },
        )
    }
}
