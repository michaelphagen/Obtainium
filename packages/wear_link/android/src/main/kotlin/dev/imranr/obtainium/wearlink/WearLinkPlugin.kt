package dev.imranr.obtainium.wearlink

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Talks to Obtainium's watch helper (wear_helper/ in this repo) over the
 * Wear OS data layer. The protocol is described in the helper's Protocol.kt.
 * A plugin rather than MainActivity code so background (WorkManager) engines
 * get it too.
 */
class WearLinkPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "dev.imranr.obtainium/wear_link")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        scope.launch {
            val reply: Any? = try {
                when (call.method) {
                    "status" -> withTimeout(REQUEST_TIMEOUT_MS) { status() }
                    "packages" -> withTimeout(REQUEST_TIMEOUT_MS) { request(PATH_PACKAGES) }
                    "install" -> install(
                        call.argument<List<String>>("paths") ?: emptyList(),
                        call.argument<String>("packageName") ?: "",
                    )
                    else -> {
                        withContext(Dispatchers.Main) { result.notImplemented() }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { result.error("wear_link", e.message ?: e.toString(), null) }
                return@launch
            }
            withContext(Dispatchers.Main) { result.success(reply) }
        }
    }

    /** The helper watch to use: a reachable one, nearby (connected directly) first. */
    private suspend fun node(): Node? =
        Wearable.getCapabilityClient(context)
            .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            .nodes.sortedByDescending { it.isNearby }.firstOrNull()

    /** The helper's status JSON with the watch's name added, or null with no watch. */
    private suspend fun status(): String? {
        val node = node() ?: return null
        val body = Wearable.getMessageClient(context).sendRequest(node.id, PATH_STATUS, ByteArray(0)).await()
        return JSONObject(String(body)).put("node", node.displayName).put("nodeId", node.id).toString()
    }

    private suspend fun request(path: String): String? {
        val node = node() ?: return null
        return String(Wearable.getMessageClient(context).sendRequest(node.id, path, ByteArray(0)).await())
    }

    /** Sends the APKs and waits for the watch's result JSON {status, message}. */
    private suspend fun install(paths: List<String>, packageName: String): String {
        require(paths.isNotEmpty() && packageName.isNotEmpty()) { "Nothing to install" }
        val node = node() ?: throw IllegalStateException("No watch with Obtainium's watch helper is connected")
        val token = UUID.randomUUID().toString()
        val done = CompletableDeferred<String>()
        val messages = Wearable.getMessageClient(context)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == PATH_RESULT + token) done.complete(String(event.data))
        }
        messages.addListener(listener).await()
        try {
            val channels = Wearable.getChannelClient(context)
            paths.forEachIndexed { i, path ->
                val file = File(path)
                val ch = channels.openChannel(
                    node.id,
                    "$PATH_INSTALL$token/$i/${paths.size}/${file.length()}/$packageName",
                ).await()
                channels.sendFile(ch, Uri.fromFile(file)).await()
            }
            return withTimeout(INSTALL_TIMEOUT_MS) { done.await() }
        } finally {
            messages.removeListener(listener)
        }
    }

    companion object {
        private const val CAPABILITY = "obtainium_wear_helper"
        private const val PATH_STATUS = "/obtainium/status"
        private const val PATH_PACKAGES = "/obtainium/packages"
        private const val PATH_INSTALL = "/obtainium/install/"
        private const val PATH_RESULT = "/obtainium/result/"
        private const val REQUEST_TIMEOUT_MS = 20_000L

        // Sending over Bluetooth is slow, and a prompt on the watch waits for the user.
        private const val INSTALL_TIMEOUT_MS = 10 * 60_000L
    }
}
