package io.github.hyperultra.launcher

import android.app.KeyguardManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var settings: LauncherSettings
    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val homeSignal = mutableIntStateOf(0)
    private var authCallback: ((Boolean) -> Unit)? = null

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            authCallback?.invoke(r.resultCode == RESULT_OK)
            authCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        settings = LauncherSettings(this)
        reloadApps()
        setContent {
            LauncherRoot(this, settings, apps.value, homeSignal.intValue)
        }
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
        OverlayService.sync(this, settings)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeSignal.intValue++ // botón/gesto Inicio: volver a la pantalla principal
    }

    @Deprecated("Deprecated in Java")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) IconCache.clear()
    }

    private fun reloadApps() {
        lifecycleScope.launch(Dispatchers.Default) {
            apps.value = loadApps(packageManager, packageName)
        }
    }

    /** Pide el PIN/patrón/huella del sistema antes de mostrar apps ocultas. */
    fun requireAuth(onResult: (Boolean) -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            Toast.makeText(this, "Sin bloqueo de pantalla: las apps ocultas no quedan protegidas", Toast.LENGTH_LONG).show()
            onResult(true)
            return
        }
        @Suppress("DEPRECATION")
        val i = km.createConfirmDeviceCredentialIntent("Apps ocultas", null)
        if (i == null) { onResult(true); return }
        authCallback = onResult
        authLauncher.launch(i)
    }
}
