package com.example.apkextractor

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_EXPORT = 42
        private const val AURORA_PKG = "com.aurora.store"

        /** Japanese store title, used to search in Aurora / Play. */
        private const val PRESET_STORE_QUERY = "アイドリッシュセブン"

        /** Any of these in the app label or package name marks it as IDOLiSH7. */
        private val PRESET_MATCHERS = listOf("アイドリッシュ", "idolish", "i7")
    }

    private var allApps: List<AppEntry> = emptyList()
    private var visibleApps: List<AppEntry> = emptyList()
    private var pendingExport: AppEntry? = null

    private lateinit var listView: ListView
    private lateinit var statusText: TextView
    private lateinit var searchBox: EditText
    private lateinit var presetSwitch: Switch
    private lateinit var systemBox: CheckBox
    private val adapter = AppAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        // Reload every time we come back, e.g. after installing the game in Aurora.
        loadApps()
    }

    // ---------------------------------------------------------------- UI

    private fun buildLayout(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        root.addView(TextView(this).apply {
            text = "Step 1 - Download (Japan region)"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
        })
        root.addView(TextView(this).apply {
            text = "Use Aurora Store signed in with a Japan-region Google account " +
                "(or anonymous + a Japan VPN) and install IDOLiSH7 (アイドリッシュセブン)."
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(Button(this).apply {
            text = "Open IDOLiSH7 (JP) in Aurora Store"
            setOnClickListener { openStoreSearch() }
        })

        root.addView(TextView(this).apply {
            text = "Step 2 - Extract"
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setPadding(0, dp(16), 0, dp(4))
        })

        presetSwitch = Switch(this).apply {
            text = "Show IDOLiSH7 only"
            isChecked = true
            setOnCheckedChangeListener { _, _ -> applyFilter() }
        }
        root.addView(presetSwitch)

        searchBox = EditText(this).apply {
            hint = "Search any app (name or package)"
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!s.isNullOrBlank() && presetSwitch.isChecked) presetSwitch.isChecked = false
                    applyFilter()
                }
            })
        }
        root.addView(searchBox)

        systemBox = CheckBox(this).apply {
            text = "Include system apps"
            setOnCheckedChangeListener { _, _ -> applyFilter() }
        }
        root.addView(systemBox)

        statusText = TextView(this).apply { setPadding(0, dp(4), 0, dp(4)) }
        root.addView(statusText)

        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ -> confirmExport(visibleApps[position]) }
        }
        root.addView(
            listView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return root
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = visibleApps.size
        override fun getItem(position: Int) = visibleApps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                addView(TextView(context).apply { textSize = 16f })
                addView(TextView(context).apply { textSize = 12f; alpha = 0.7f })
            }
            val app = visibleApps[position]
            (row.getChildAt(0) as TextView).text = app.label
            val kind = if (app.isSplit) "${app.apkPaths.size} APKs (split)" else "single APK"
            (row.getChildAt(1) as TextView).text =
                "${app.packageName}\nv${app.versionName} (${app.versionCode}) · $kind · ${formatSize(app.totalBytes)}"
            return row
        }
    }

    // ------------------------------------------------------------- data

    private fun loadApps() {
        statusText.text = "Loading installed apps..."
        Thread {
            val apps = runCatching { AppLoader.load(packageManager) }.getOrElse { emptyList() }
            runOnUiThread {
                allApps = apps
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val query = searchBox.text.toString().trim().lowercase(Locale.ROOT)
        visibleApps = allApps.filter { app ->
            if (!systemBox.isChecked && app.isSystem) return@filter false
            val label = app.label.lowercase(Locale.ROOT)
            val pkg = app.packageName.lowercase(Locale.ROOT)
            when {
                presetSwitch.isChecked -> PRESET_MATCHERS.any { m ->
                    val needle = m.lowercase(Locale.ROOT)
                    label.contains(needle) || pkg.contains(needle)
                }
                query.isEmpty() -> true
                else -> label.contains(query) || pkg.contains(query)
            }
        }
        adapter.notifyDataSetChanged()
        statusText.text = when {
            visibleApps.isNotEmpty() -> "${visibleApps.size} app(s) - tap one to export"
            presetSwitch.isChecked -> "IDOLiSH7 is not installed yet. Do Step 1, then come back."
            else -> "No matching apps."
        }
    }

    // ----------------------------------------------------------- store

    private fun openStoreSearch() {
        val searchUri = Uri.parse("market://search?q=" + Uri.encode(PRESET_STORE_QUERY))
        val intent = Intent(Intent.ACTION_VIEW, searchUri)
        if (isInstalled(AURORA_PKG)) intent.setPackage(AURORA_PKG)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Aurora may not register for market://search; fall back to just opening it.
            val launch = packageManager.getLaunchIntentForPackage(AURORA_PKG)
            if (launch != null) {
                startActivity(launch)
                toast("Search for \"$PRESET_STORE_QUERY\" in Aurora")
            } else {
                toast("Install Aurora Store first (from F-Droid or auroraoss.com)")
            }
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    // ---------------------------------------------------------- export

    private fun confirmExport(app: AppEntry) {
        val how = if (app.isSplit) {
            "This app ships as ${app.apkPaths.size} split APKs. They will be packed into one .apks " +
                "file - install it with SAI (Split APKs Installer) or adb install-multiple."
        } else {
            "This app is a single APK."
        }
        AlertDialog.Builder(this)
            .setTitle("Export ${app.label}?")
            .setMessage("$how\n\nSize: ${formatSize(app.totalBytes)}\n\nGame data the app downloads after launch is NOT included.")
            .setPositiveButton("Choose where to save") { _, _ -> pickDestination(app) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDestination(app: AppEntry) {
        pendingExport = app
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (app.isSplit) "application/octet-stream" else "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_TITLE, app.suggestedFileName())
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_EXPORT)
    }

    @Deprecated("Plain Activity API; fine for this single-screen tool")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_EXPORT) return
        val app = pendingExport ?: return
        pendingExport = null
        val target = data?.data
        if (resultCode != RESULT_OK || target == null) return
        runExport(app, target)
    }

    private fun runExport(app: AppEntry, target: Uri) {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Exporting ${app.label}...")
            .setView(bar)
            .setCancelable(false)
            .show()

        Thread {
            val result = runCatching {
                ApkExporter.export(contentResolver, app, target) { pct ->
                    runOnUiThread { bar.progress = pct }
                }
            }
            runOnUiThread {
                dialog.dismiss()
                result.onSuccess {
                    AlertDialog.Builder(this)
                        .setTitle("Done")
                        .setMessage("Saved ${app.suggestedFileName()} (${formatSize(app.totalBytes)}).")
                        .setPositiveButton("OK", null)
                        .show()
                }.onFailure { e ->
                    runCatching { contentResolver.delete(target, null, null) }
                    AlertDialog.Builder(this)
                        .setTitle("Export failed")
                        .setMessage(e.message ?: e.toString())
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    // ---------------------------------------------------------- helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
        else -> String.format(Locale.ROOT, "%d KB", bytes / 1024)
    }
}
