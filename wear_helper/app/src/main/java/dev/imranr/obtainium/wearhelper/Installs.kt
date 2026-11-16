package dev.imranr.obtainium.wearhelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.io.File

/**
 * APKs arrive one channel at a time into files/incoming/<token>/. Once all of
 * a token's parts are in, they go into one PackageInstaller session. The
 * result goes back to the phone node that sent them.
 */
object Installs {
    private const val CHANNEL_ID = "installs"
    private const val EXTRA_TOKEN = "token"
    private const val EXTRA_NODE = "node"
    private const val EXTRA_PACKAGE = "package"

    private fun dir(context: Context, token: String) =
        File(context.filesDir, "incoming/$token").apply { mkdirs() }

    fun partFile(context: Context, part: Protocol.InstallPart) = File(dir(context, part.token), "${part.index}.apk")

    @Synchronized
    fun received(context: Context, part: Protocol.InstallPart, node: String) {
        val d = dir(context, part.token)
        File(d, "${part.index}.done").createNewFile()
        val done = (0 until part.count).all { File(d, "$it.done").exists() }
        if (!done || !File(d, "committed").createNewFile()) return
        try {
            commit(context, part, node, (0 until part.count).map { File(d, "$it.apk") })
        } catch (e: Exception) {
            Log.w(TAG, "install of ${part.packageName} failed", e)
            reply(context, node, part.token, PackageInstaller.STATUS_FAILURE, e.message ?: e.toString())
            d.deleteRecursively()
        }
    }

    @Synchronized
    fun fail(context: Context, part: Protocol.InstallPart, node: String, message: String) {
        val d = dir(context, part.token)
        if (!File(d, "committed").createNewFile()) return
        reply(context, node, part.token, PackageInstaller.STATUS_FAILURE, message)
        d.deleteRecursively()
    }

    private fun commit(context: Context, part: Protocol.InstallPart, node: String, apks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(part.packageName)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) {
                // Lets updates of apps this helper installed (and of itself) go
                // through without a prompt; anything else still asks.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apks.forEachIndexed { i, f ->
                session.openWrite("$i.apk", 0, f.length()).use { out ->
                    f.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
                .putExtra(EXTRA_TOKEN, part.token)
                .putExtra(EXTRA_NODE, node)
                .putExtra(EXTRA_PACKAGE, part.packageName)
            val pending = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            Log.i(TAG, "committing ${part.packageName} (${apks.size} APKs, session $id)")
            session.commit(pending.intentSender)
        }
    }

    fun onResult(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val node = intent.getStringExtra(EXTRA_NODE) ?: return
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "the app"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        Log.i(TAG, "install of $pkg: status $status $message")
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            askUser(context, pkg, confirm)
            return
        }
        cancelAsk(context, pkg)
        reply(context, node, token, status, message)
        File(context.filesDir, "incoming/$token").deleteRecursively()
    }

    /**
     * The system wants the user to confirm. A background app can't open the
     * prompt itself on newer Wear OS, so it's a notification to tap (and a
     * direct attempt, which works while the helper is on screen).
     */
    private fun askUser(context: Context, pkg: String, confirm: Intent) {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(confirm) }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Installs", NotificationManager.IMPORTANCE_HIGH))
        val tap = PendingIntent.getActivity(
            context, pkg.hashCode(), confirm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Install from Obtainium")
            .setContentText("Tap to install $pkg")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(pkg.hashCode(), n) }
    }

    private fun cancelAsk(context: Context, pkg: String) {
        context.getSystemService(NotificationManager::class.java).cancel(pkg.hashCode())
    }

    private fun reply(context: Context, node: String, token: String, status: Int, message: String) {
        val body = JSONObject().put("status", status).put("message", message).toString().toByteArray()
        Wearable.getMessageClient(context).sendMessage(node, Protocol.PATH_RESULT + token, body)
            .addOnFailureListener { Log.w(TAG, "could not report the result to the phone", it) }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                Installs.onResult(context.applicationContext, intent)
                Thread.sleep(2_000)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
