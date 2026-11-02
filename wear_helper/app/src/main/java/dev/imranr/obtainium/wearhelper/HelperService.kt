package dev.imranr.obtainium.wearhelper

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONArray
import org.json.JSONObject

const val TAG = "ObtainiumWear"

/** Answers the phone's requests and receives the APKs it sends. */
class HelperService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? =
        when (path) {
            Protocol.PATH_STATUS -> Tasks.forResult(status(this).toString().toByteArray())
            Protocol.PATH_PACKAGES -> Tasks.forResult(packages(this).toString().toByteArray())
            else -> null
        }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val part = Protocol.parseInstall(channel.path) ?: return
        val file = Installs.partFile(this, part)
        Log.i(TAG, "receiving ${part.packageName} ${part.index + 1}/${part.count} from ${channel.nodeId}")
        Wearable.getChannelClient(this).receiveFile(channel, Uri.fromFile(file), false)
            .addOnFailureListener { e ->
                Log.w(TAG, "receive failed", e)
                Installs.fail(this, part, channel.nodeId, "Could not receive the APK: ${e.message}")
            }
    }

    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) =
        closed(channel, closeReason)

    override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) =
        closed(channel, closeReason)

    // Either callback may come first (or alone); a part only counts once its
    // file has every byte the phone said it would send.
    private fun closed(channel: ChannelClient.Channel, closeReason: Int) {
        val part = Protocol.parseInstall(channel.path) ?: return
        val file = Installs.partFile(this, part)
        // The last bytes can land in the file just after the close.
        var waited = 0
        while (file.length() < part.size && closeReason == ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL && waited < 10_000) {
            Thread.sleep(250)
            waited += 250
        }
        val size = file.length()
        when {
            size == part.size -> Installs.received(this, part, channel.nodeId)
            closeReason != ChannelClient.ChannelCallback.CLOSE_REASON_NORMAL ->
                Installs.fail(this, part, channel.nodeId, "The transfer from the phone stopped (reason $closeReason)")
            else -> Log.w(TAG, "${part.packageName} part ${part.index}: $size of ${part.size} bytes at close")
        }
    }

    companion object {
        fun status(context: Context): JSONObject {
            val pm = context.packageManager
            return JSONObject()
                .put("packageName", context.packageName)
                .put("canInstall", pm.canRequestPackageInstalls())
                .put("sdk", Build.VERSION.SDK_INT)
                .put("device", Build.MODEL)
                .put("helperVersion", pm.getPackageInfo(context.packageName, 0).versionName)
        }

        fun packages(context: Context): JSONArray {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val all: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getInstalledPackages(0)
            }
            val out = JSONArray()
            for (p in all) {
                val app = p.applicationInfo ?: continue
                val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val updated = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                if (system && !updated) continue
                val installer = runCatching { pm.getInstallSourceInfo(p.packageName).installingPackageName }.getOrNull()
                out.put(
                    JSONObject()
                        .put("packageName", p.packageName)
                        .put("versionName", p.versionName ?: JSONObject.NULL)
                        .put("versionCode", p.longVersionCode)
                        .put("firstInstallTime", p.firstInstallTime)
                        .put("lastUpdateTime", p.lastUpdateTime)
                        .put("installer", installer ?: JSONObject.NULL),
                )
            }
            return out
        }
    }
}
