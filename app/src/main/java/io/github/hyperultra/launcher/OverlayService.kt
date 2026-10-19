package io.github.hyperultra.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Servicio en primer plano OPCIONAL (solo corre si el usuario activa la burbuja o la pantalla de
 * bloqueo propia y concede "Mostrar sobre otras apps"). Hace dos cosas:
 *  1. Burbuja flotante arrastrable con accesos rápidos (superposición).
 *  2. Escucha SCREEN_ON para mostrar LockScreenActivity.
 * Se detiene solo cuando ambas funciones están desactivadas.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        val s = LauncherSettings(this)
        if (!Settings.canDrawOverlays(this) || (!s.bubble && !s.lockScreen)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (s.bubble) showBubble(s) else removeBubble()
        if (s.lockScreen) registerScreenReceiver() else unregisterScreenReceiver()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removePanel()
        removeBubble()
        unregisterScreenReceiver()
        super.onDestroy()
    }

    // ------------------------------------------------------------- primer plano

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------- pantalla de bloqueo

    private fun registerScreenReceiver() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action != Intent.ACTION_SCREEN_ON) return
                if (!LauncherSettings(c).lockScreen) return
                val it = Intent(c, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                try { c.startActivity(it) } catch (e: Exception) { /* el sistema lo bloqueó: se mantiene la pantalla de bloqueo normal */ }
            }
        }
        // Las acciones SCREEN_ON/OFF solo se pueden recibir con registro dinámico.
        ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    private fun unregisterScreenReceiver() {
        receiver?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        receiver = null
    }

    // ------------------------------------------------------------- burbuja

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayParams(w: Int, h: Int, watchOutside: Boolean = false): WindowManager.LayoutParams {
        var fl = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (watchOutside) fl = fl or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, fl, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun showBubble(s: LauncherSettings) {
        if (bubbleView != null) return
        val size = dp(48)
        val tv = TextView(this).apply {
            text = "✦"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(s.accent)
                alpha = 215
            }
            contentDescription = getString(R.string.overlay_bubble_desc)
        }
        val lp = overlayParams(size, size).apply {
            x = resources.displayMetrics.widthPixels - size
            y = resources.displayMetrics.heightPixels / 3
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt(); lp.y = (startY + dy).toInt()
                        try { wm.updateViewLayout(v, lp) } catch (ex: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel(lp) else snapToEdge(v, lp, size)
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(tv, lp)
            bubbleView = tv
        } catch (e: Exception) {
            Toast.makeText(this, R.string.overlay_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun snapToEdge(v: View, lp: WindowManager.LayoutParams, size: Int) {
        val w = resources.displayMetrics.widthPixels
        lp.x = if (lp.x + size / 2 < w / 2) 0 else w - size
        try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }
    }

    private fun removeBubble() {
        bubbleView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bubbleView = null
    }

    // ------------------------------------------------------------- panel de la burbuja

    private fun togglePanel(anchor: WindowManager.LayoutParams) {
        if (panelView != null) { removePanel(); return }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xF2202226.toInt())
            }
        }
        fun item(label: String, action: () -> Unit) {
            col.addView(TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { removePanel(); action() }
            })
        }
        item(getString(R.string.bubble_home)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        item(getString(R.string.bubble_lock)) {
            if (!LauncherAccessibility.lockScreen()) {
                Toast.makeText(this, R.string.need_accessibility, Toast.LENGTH_LONG).show()
            }
        }
        item(getString(R.string.bubble_notifs)) {
            if (!LauncherAccessibility.openNotifications()) {
                Toast.makeText(this, R.string.need_accessibility, Toast.LENGTH_LONG).show()
            }
        }
        item(getString(R.string.bubble_recents)) {
            if (!LauncherAccessibility.openRecents()) {
                Toast.makeText(this, R.string.need_accessibility, Toast.LENGTH_LONG).show()
            }
        }
        item(getString(R.string.bubble_hide)) {
            LauncherSettings(this).setBubble(false)
            removeBubble()
            if (!LauncherSettings(this).lockScreen) stopSelf()
        }
        col.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) { removePanel(); true } else false
        }
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            val w = resources.displayMetrics.widthPixels
            x = if (anchor.x + dp(24) < w / 2) anchor.x + dp(56) else (anchor.x - dp(210)).coerceAtLeast(0)
            y = anchor.y
        }
        try {
            wm.addView(col, lp)
            panelView = col
        } catch (e: Exception) { }
    }

    private fun removePanel() {
        panelView?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        panelView = null
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 42

        /** Arranca o detiene el servicio según los ajustes. Llamar desde la UI en primer plano. */
        fun sync(ctx: Context, s: LauncherSettings) {
            val want = (s.bubble || s.lockScreen) && Settings.canDrawOverlays(ctx)
            val i = Intent(ctx, OverlayService::class.java)
            if (want) {
                try { ContextCompat.startForegroundService(ctx, i) } catch (e: Exception) { }
            } else {
                ctx.stopService(i)
            }
        }
    }
}
