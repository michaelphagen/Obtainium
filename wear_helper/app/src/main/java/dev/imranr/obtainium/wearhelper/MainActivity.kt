package dev.imranr.obtainium.wearhelper

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The helper has no settings: everything is configured in Obtainium on the
 * phone. This screen only shows whether it may install apps, and asks for
 * that permission (and notifications, used for install prompts).
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var allow: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.widthPixels * 0.12).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            text = "Obtainium watch helper"
            textSize = 16f
            gravity = Gravity.CENTER
        })
        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad / 3)
        }
        col.addView(status)
        allow = Button(this).apply {
            text = "Allow installs"
            setOnClickListener { openInstallSetting() }
        }
        col.addView(allow)
        setContentView(ScrollView(this).apply { addView(col) })
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        val canInstall = packageManager.canRequestPackageInstalls()
        allow.visibility = if (canInstall) View.GONE else View.VISIBLE
        status.text = if (canInstall) {
            "Ready. In Obtainium Wear on the phone, set an app's install method to \"Wear OS watch\"."
        } else {
            "This helper isn't allowed to install apps yet."
        }
    }

    private fun openInstallSetting() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            status.text = "This watch has no setting for it. From a computer, run:\n\n" +
                "adb shell appops set $packageName REQUEST_INSTALL_PACKAGES allow"
        }
    }
}
