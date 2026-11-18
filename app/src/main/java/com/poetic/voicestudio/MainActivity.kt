package com.example.speedlimiter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.VpnService
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // تبويب السرعة
    private lateinit var speedInput: EditText
    private lateinit var sessionLimitInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var applyButton: Button
    private lateinit var speedListView: ListView
    private lateinit var speedAdapter: AppAdapter

    // تبويب إيقاف الخلفية
    private lateinit var bgSearchInput: EditText
    private lateinit var selectAllBgButton: Button
    private lateinit var bgListView: ListView
    private lateinit var bgAdapter: BackgroundAppAdapter

    // أزرار التنقل بين التبويبات
    private lateinit var tabSpeedBtn: Button
    private lateinit var tabBgBtn: Button
    private lateinit var speedLayoutContainer: LinearLayout
    private lateinit var bgLayoutContainer: LinearLayout

    private val userApps = mutableListOf<AppItem>()
    private val filteredSpeedApps = mutableListOf<AppItem>()
    private val filteredBgApps = mutableListOf<AppItem>()

    private val selectedSpeedPackages = mutableSetOf<String>()
    private val selectedBgKillPackages = mutableSetOf<String>()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "تم رفض إذن الـ VPN", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val savedSpeed = prefs.getInt("speed_kb", 70)
        val savedLimit = prefs.getInt("session_limit_kb", 250)
        selectedSpeedPackages.addAll(prefs.getStringSet("selected_apps", emptySet()) ?: emptySet())
        selectedBgKillPackages.addAll(prefs.getStringSet("bg_kill_apps", emptySet()) ?: emptySet())

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val titleText = TextView(this).apply {
            text = "مُحدّد السرعة وحظر الخلفية"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(titleText)

        // شريط التبويبات العلوي
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        tabSpeedBtn = Button(this).apply {
            text = "⚡ تحديد السرعة"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener { switchTab(0) }
        }

        tabBgBtn = Button(this).apply {
            text = "🛑 حظر الخلفية"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { switchTab(1) }
        }

        tabsRow.addView(tabSpeedBtn)
        tabsRow.addView(tabBgBtn)
        rootLayout.addView(tabsRow)

        // ==================== حاوية تبويب تحديد السرعة ====================
        speedLayoutContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val speedCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }
        speedCol.addView(TextView(this).apply { text = "السرعة (KB/s):"; textSize = 13f; textColor = Color.LTGRAY })
        speedInput = EditText(this).apply {
            setText(savedSpeed.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(16, 12, 16, 12)
        }
        speedCol.addView(speedInput)

        val sessionCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
        }
        sessionCol.addView(TextView(this).apply { text = "حد الجلسة (KB):"; textSize = 13f; textColor = Color.LTGRAY })
        sessionLimitInput = EditText(this).apply {
            setText(savedLimit.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            setPadding(16, 12, 16, 12)
        }
        sessionCol.addView(sessionLimitInput)

        controlsRow.addView(speedCol)
        controlsRow.addView(sessionCol)
        speedLayoutContainer.addView(controlsRow)

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }

        toggleButton = Button(this).apply {
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 6, 0)
            }
            setOnClickListener {
                if (MyVpnService.isServiceRunning) stopVpnService() else prepareAndStartVpn()
            }
        }

        applyButton = Button(this).apply {
            text = "تحديث"
            setBackgroundColor(Color.parseColor("#2962FF"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(6, 0, 0, 0)
            }
            setOnClickListener {
                prepareAndStartVpn()
                Toast.makeText(this@MainActivity, "تم تحديث القيم", Toast.LENGTH_SHORT).show()
            }
        }

        buttonsRow.addView(toggleButton)
        buttonsRow.addView(applyButton)
        speedLayoutContainer.addView(buttonsRow)

        searchInput = EditText(this).apply {
            hint = "ابحث في التطبيقات..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 14, 16, 14)
        }
        speedLayoutContainer.addView(searchInput)

        speedListView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 10, 0, 0)
            }
        }
        speedLayoutContainer.addView(speedListView)
        rootLayout.addView(speedLayoutContainer)

        // ==================== حاوية تبويب إيقاف الخلفية ====================
        bgLayoutContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            visibility = View.GONE
        }

        val bgHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
        }

        bgSearchInput = EditText(this).apply {
            hint = "ابحث عن تطبيق للخلفية..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(16, 14, 16, 14)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        selectAllBgButton = Button(this).apply {
            text = "تحديد الكل"
            setBackgroundColor(Color.parseColor("#37474F"))
            setTextColor(Color.WHITE)
            setPadding(16, 10, 16, 10)
            setOnClickListener {
                toggleSelectAllBackgroundApps()
            }
        }

        bgHeaderRow.addView(bgSearchInput)
        bgHeaderRow.addView(selectAllBgButton)
        bgLayoutContainer.addView(bgHeaderRow)

        val bgInfoText = TextView(this).apply {
            text = "التطبيقات المحددة هنا سيتم منع نشاطها في الخلفية تلقائياً (تطبيقات النظام مستثناة بالكامل لحماية النظام)."
            textSize = 11f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 0, 0, 8)
        }
        bgLayoutContainer.addView(bgInfoText)

        bgListView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        bgLayoutContainer.addView(bgListView)
        rootLayout.addView(bgLayoutContainer)

        setContentView(rootLayout)

        loadInstalledApps()
        updateUiState()
        switchTab(0)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSpeedApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        bgSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterBgApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun switchTab(tabIndex: Int) {
        if (tabIndex == 0) {
            speedLayoutContainer.visibility = View.VISIBLE
            bgLayoutContainer.visibility = View.GONE
            tabSpeedBtn.setBackgroundColor(Color.parseColor("#00C853"))
            tabBgBtn.setBackgroundColor(Color.parseColor("#2A2A2A"))
        } else {
            speedLayoutContainer.visibility = View.GONE
            bgLayoutContainer.visibility = View.VISIBLE
            tabSpeedBtn.setBackgroundColor(Color.parseColor("#2A2A2A"))
            tabBgBtn.setBackgroundColor(Color.parseColor("#D50000"))
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        userApps.clear()

        for (appInfo in packages) {
            if (appInfo.packageName == packageName) continue

            // استبعاد تطبيقات النظام تماماً (لا يُعرض سوى تطبيقات المستخدم)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            if (isSystemApp && !isUpdatedSystemApp) continue

            val label = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            userApps.add(AppItem(label, appInfo.packageName, icon))
        }

        userApps.sortWith(compareByDescending<AppItem> { selectedSpeedPackages.contains(it.packageName) }.thenBy { it.name.lowercase() })

        filteredSpeedApps.clear()
        filteredSpeedApps.addAll(userApps)

        filteredBgApps.clear()
        filteredBgApps.addAll(userApps)

        speedAdapter = AppAdapter()
        speedListView.adapter = speedAdapter

        bgAdapter = BackgroundAppAdapter()
        bgListView.adapter = bgAdapter
    }

    private fun filterSpeedApps(query: String) {
        filteredSpeedApps.clear()
        if (query.isBlank()) {
            filteredSpeedApps.addAll(userApps)
        } else {
            val q = query.lowercase()
            filteredSpeedApps.addAll(userApps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
        }
        speedAdapter.notifyDataSetChanged()
    }

    private fun filterBgApps(query: String) {
        filteredBgApps.clear()
        if (query.isBlank()) {
            filteredBgApps.addAll(userApps)
        } else {
            val q = query.lowercase()
            filteredBgApps.addAll(userApps.filter { it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) })
        }
        bgAdapter.notifyDataSetChanged()
    }

    private fun toggleSelectAllBackgroundApps() {
        val allUserPackages = userApps.map { it.packageName }.toSet()
        if (selectedBgKillPackages.containsAll(allUserPackages)) {
            selectedBgKillPackages.clear()
            selectAllBgButton.text = "تحديد الكل"
        } else {
            selectedBgKillPackages.clear()
            selectedBgKillPackages.addAll(allUserPackages)
            selectAllBgButton.text = "إلغاء التحديد"
        }
        savePreferences()
        bgAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun updateUiState() {
        if (MyVpnService.isServiceRunning) {
            toggleButton.text = "إيقاف الخدمة"
            toggleButton.setBackgroundColor(Color.parseColor("#D50000"))
            applyButton.visibility = View.VISIBLE
        } else {
            toggleButton.text = "تشغيل الخدمة"
            toggleButton.setBackgroundColor(Color.parseColor("#00C853"))
            applyButton.visibility = View.GONE
        }
    }

    private fun savePreferences() {
        val speedKb = speedInput.text.toString().toIntOrNull() ?: 70
        val sessionLimitKb = sessionLimitInput.text.toString().toIntOrNull() ?: 250

        getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).edit()
            .putInt("speed_kb", speedKb)
            .putInt("session_limit_kb", sessionLimitKb)
            .putStringSet("selected_apps", selectedSpeedPackages.toSet())
            .putStringSet("bg_kill_apps", selectedBgKillPackages.toSet())
            .apply()
    }

    private fun prepareAndStartVpn() {
        if (selectedSpeedPackages.isEmpty() && selectedBgKillPackages.isEmpty()) {
            Toast.makeText(this, "اختر تطبيقاً واحداً على الأقل في أي من القائمتين", Toast.LENGTH_SHORT).show()
            return
        }
        savePreferences()
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val speedKb = speedInput.text.toString().toIntOrNull() ?: 70
        val sessionLimitKb = sessionLimitInput.text.toString().toIntOrNull() ?: 250
        savePreferences()

        val intent = Intent(this, MyVpnService::class.java).apply {
            putStringArrayListExtra("TARGET_APPS", ArrayList(selectedSpeedPackages))
            putStringArrayListExtra("BG_KILL_APPS", ArrayList(selectedBgKillPackages))
            putExtra("SPEED_KB", speedKb)
            putExtra("SESSION_LIMIT_KB", sessionLimitKb)
        }
        ContextCompat.startForegroundService(this, intent)

        MyVpnService.isServiceRunning = true
        updateUiState()
    }

    private fun stopVpnService() {
        val intent = Intent(this, MyVpnService::class.java).apply { action = "STOP" }
        startService(intent)
        MyVpnService.isServiceRunning = false
        updateUiState()
    }

    data class AppItem(val name: String, val packageName: String, val icon: Drawable)

    inner class AppAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredSpeedApps.size
        override fun getItem(position: Int): Any = filteredSpeedApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 16, 12, 16)
            }
            row.removeAllViews()
            val item = filteredSpeedApps[position]

            val iconView = ImageView(this@MainActivity).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams(80, 80)
            }
            val nameView = TextView(this@MainActivity).apply {
                text = item.name
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(16, 0, 16, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val checkBox = CheckBox(this@MainActivity).apply {
                setOnCheckedChangeListener(null)
                isChecked = selectedSpeedPackages.contains(item.packageName)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedSpeedPackages.add(item.packageName)
                    else selectedSpeedPackages.remove(item.packageName)
                    savePreferences()
                }
            }

            row.addView(iconView)
            row.addView(nameView)
            row.addView(checkBox)
            row.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            return row
        }
    }

    inner class BackgroundAppAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredBgApps.size
        override fun getItem(position: Int): Any = filteredBgApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 16, 12, 16)
            }
            row.removeAllViews()
            val item = filteredBgApps[position]

            val iconView = ImageView(this@MainActivity).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams(80, 80)
            }
            val nameView = TextView(this@MainActivity).apply {
                text = item.name
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(16, 0, 16, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val checkBox = CheckBox(this@MainActivity).apply {
                setOnCheckedChangeListener(null)
                isChecked = selectedBgKillPackages.contains(item.packageName)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedBgKillPackages.add(item.packageName)
                    else selectedBgKillPackages.remove(item.packageName)
                    savePreferences()
                }
            }

            row.addView(iconView)
            row.addView(nameView)
            row.addView(checkBox)
            row.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            return row
        }
    }
}
