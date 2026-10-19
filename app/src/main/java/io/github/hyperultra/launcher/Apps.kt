package io.github.hyperultra.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.view.View
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val component: ComponentName) {
    val id: String get() = component.flattenToShortString()
}

@Suppress("DEPRECATION")
private fun queryActivities(pm: PackageManager, intent: Intent, flags: Int): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        pm.queryIntentActivities(intent, flags)
    }

fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return queryActivities(pm, main, 0)
        .filter { it.activityInfo.packageName != selfPackage }
        .map {
            AppEntry(
                it.loadLabel(pm).toString(),
                ComponentName(it.activityInfo.packageName, it.activityInfo.name)
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** Dock por defecto: teléfono, mensajes, navegador y cámara predeterminados, si existen. */
fun defaultDock(pm: PackageManager, apps: List<AppEntry>): List<AppEntry> {
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )
    val out = ArrayList<AppEntry>()
    for (i in intents) {
        val pkgs = queryActivities(pm, i, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        val app = apps.firstOrNull { it.component.packageName in pkgs } ?: continue
        if (out.none { it.id == app.id }) out.add(app)
    }
    return out
}

fun resolveDock(pm: PackageManager, saved: List<String>?, apps: List<AppEntry>): List<AppEntry> {
    if (saved == null) return defaultDock(pm, apps)
    val byId = apps.associateBy { it.id }
    return saved.mapNotNull { byId[it] }
}

fun launchApp(ctx: Context, view: View?, app: AppEntry, animate: Boolean) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        val opts = if (animate && view != null) {
            ActivityOptions.makeScaleUpAnimation(view, view.width / 2, view.height / 2, 1, 1).toBundle()
        } else null
        ctx.startActivity(i, opts)
    } catch (e: Exception) {
        Toast.makeText(ctx, "No se pudo abrir ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

/** Caché de iconos acotada por memoria y cargada de forma perezosa fuera del hilo principal. */
object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(2048, 8192)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }

    private fun key(app: AppEntry, px: Int) = app.id + "@" + px

    fun peek(app: AppEntry, px: Int): ImageBitmap? = cache.get(key(app, px))

    suspend fun load(pm: PackageManager, app: AppEntry, px: Int): ImageBitmap {
        peek(app, px)?.let { return it }
        return withContext(Dispatchers.Default) {
            val d = try {
                pm.getActivityIcon(app.component)
            } catch (e: Exception) {
                pm.defaultActivityIcon
            }
            val bmp = d.toBitmap(px, px).asImageBitmap()
            cache.put(key(app, px), bmp)
            bmp
        }
    }

    fun clear() = cache.evictAll()
}
