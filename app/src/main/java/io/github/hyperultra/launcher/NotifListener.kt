package io.github.hyperultra.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

data class NotifItem(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
)

data class MediaInfo(val title: String, val artist: String, val playing: Boolean)

/**
 * Escucha las notificaciones activas. Solo funciona si el usuario concede "Acceso a notificaciones"
 * (Android lo exige y no se puede activar desde código). Nada se guarda en disco ni se envía fuera.
 */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        items.value = emptyList()
        media.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = (try { activeNotifications } catch (e: Exception) { null }) ?: return
        items.value = active
            .filter { it.packageName != packageName }
            .filter { !it.isOngoing || it.isClearable }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val ex = sbn.notification.extras
                val label = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (e: Exception) { sbn.packageName }
                NotifItem(
                    key = sbn.key,
                    app = label,
                    title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    clearable = sbn.isClearable,
                    contentIntent = sbn.notification.contentIntent,
                )
            }
        media.value = readMedia(this)
    }

    companion object {
        val items = mutableStateOf<List<NotifItem>>(emptyList())
        val media = mutableStateOf<MediaInfo?>(null)
        private var instance: NotifListener? = null

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun dismiss(key: String) {
            try { instance?.cancelNotification(key) } catch (e: Exception) { }
        }

        fun dismissAll() {
            try { instance?.cancelAllNotifications() } catch (e: Exception) { }
        }

        /** Sesión multimedia activa (título y estado reales). Requiere acceso a notificaciones. */
        fun readMedia(ctx: Context): MediaInfo? {
            if (!isEnabled(ctx)) return null
            return try {
                val msm = ctx.getSystemService(MediaSessionManager::class.java)
                val list: List<MediaController> = msm.getActiveSessions(ComponentName(ctx, NotifListener::class.java))
                val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: list.firstOrNull() ?: return null
                val m = c.metadata ?: return null
                val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                if (title.isBlank()) return null
                MediaInfo(
                    title = title,
                    artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
