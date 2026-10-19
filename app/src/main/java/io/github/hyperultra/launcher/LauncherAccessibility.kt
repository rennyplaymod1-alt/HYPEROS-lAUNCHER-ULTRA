package io.github.hyperultra.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Servicio de accesibilidad MÍNIMO: no lee la pantalla ni el contenido de otras apps
 * (no solicita eventos ni retrieval de contenido). Se usa solo para las acciones globales que
 * Android no permite a una app normal: apagar la pantalla, abrir notificaciones y recientes.
 */
class LauncherAccessibility : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no se usa */ }

    override fun onInterrupt() { /* no se usa */ }

    companion object {
        @Volatile private var instance: LauncherAccessibility? = null

        fun isEnabled(ctx: Context): Boolean {
            val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(ctx, LauncherAccessibility::class.java)
            return raw.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** true solo si el servicio está conectado y el sistema aceptó la acción. */
        fun lockScreen(): Boolean =
            Build.VERSION.SDK_INT >= 28 && instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true

        fun openNotifications(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true

        fun openRecents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true
    }
}
