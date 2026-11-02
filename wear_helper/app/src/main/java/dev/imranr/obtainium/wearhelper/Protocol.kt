package dev.imranr.obtainium.wearhelper

/**
 * What the phone (packages/wear_link in the Flutter app) and this helper say
 * to each other over the Wear OS data layer. Keep the two sides in step.
 *
 * - Request `/obtainium/status`: JSON {packageName, canInstall, sdk, device,
 *   helperVersion}.
 * - Request `/obtainium/packages`: JSON array of the watch's user-installed
 *   apps {packageName, versionName, versionCode, firstInstallTime,
 *   lastUpdateTime, installer}.
 * - Channel `/obtainium/install/<token>/<index>/<count>/<size>/<packageName>`:
 *   one APK file of `size` bytes per channel (a base APK and its splits share
 *   a token). When all `count` files are in, the helper installs them in one
 *   session.
 * - Message `/obtainium/result/<token>` (watch to phone): JSON {status,
 *   message}. status is a PackageInstaller status: 0 = installed, 3 =
 *   cancelled, anything else is a failure. A session that needs the user's
 *   confirmation posts a notification on the watch and reports only once the
 *   user has answered.
 */
object Protocol {
    const val CAPABILITY = "obtainium_wear_helper"
    const val PATH_STATUS = "/obtainium/status"
    const val PATH_PACKAGES = "/obtainium/packages"
    const val PATH_INSTALL = "/obtainium/install/"
    const val PATH_RESULT = "/obtainium/result/"

    data class InstallPart(val token: String, val index: Int, val count: Int, val size: Long, val packageName: String)

    private val TOKEN = Regex("[A-Za-z0-9-]{1,64}")
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun parseInstall(path: String): InstallPart? {
        if (!path.startsWith(PATH_INSTALL)) return null
        val parts = path.removePrefix(PATH_INSTALL).split("/")
        if (parts.size != 5) return null
        val (token, index, count, size, pkg) = parts
        val i = index.toIntOrNull() ?: return null
        val n = count.toIntOrNull() ?: return null
        val bytes = size.toLongOrNull() ?: return null
        if (!TOKEN.matches(token) || !PACKAGE.matches(pkg) || n !in 1..64 || i !in 0 until n || bytes <= 0) return null
        return InstallPart(token, i, n, bytes, pkg)
    }
}
