package com.whatsapp.ios

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("gb_prefs", Context.MODE_PRIVATE)

        // Build settings UI programmatically
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#F2F2F7"))
            setPadding(0, 0, 0, 0)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
            setPadding(16.dp, 52.dp, 16.dp, 16.dp)
        }
        val backBtn = Button(this).apply {
            text = "< Back"
            setTextColor(android.graphics.Color.parseColor("#007AFF"))
            background = null
            setOnClickListener { finish() }
        }
        val titleTv = TextView(this).apply {
            text = "GB Settings"
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.BLACK)
            gravity = android.view.Gravity.CENTER
        }
        header.addView(backBtn)
        header.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        root.addView(sectionLabel("APPEARANCE"))
        root.addView(buildSwitch("iOS Light Theme", "ios_theme", true))
        root.addView(divider())

        root.addView(sectionLabel("PRIVACY"))
        root.addView(buildSwitch("Anti-Delete Messages", "anti_delete", true))
        root.addView(divider())
        root.addView(buildSwitch("Grey Ticks (Blue tick hide attempt)", "hide_blue", false))
        root.addView(divider())

        root.addView(sectionLabel("INFO"))
        val info = TextView(this).apply {
            text = "⚠️ Blue tick hide & online status hide = server side features. WhatsApp controls these — app level bypass කරන්න බෑ.\n\nAnti-delete & iOS theme = 100% work ✅"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#8E8E93"))
            setPadding(16.dp, 8.dp, 16.dp, 8.dp)
        }
        root.addView(info)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildSwitch(label: String, key: String, default: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.WHITE)
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)

            val tv = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
            }
            val sw = Switch(this@SettingsActivity).apply {
                isChecked = prefs.getBoolean(key, default)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }
            addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sw)
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#8E8E93"))
            setPadding(16.dp, 20.dp, 16.dp, 6.dp)
        }
    }

    private fun divider(): android.view.View {
        return android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#C6C6C8"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { marginStart = 16.dp }
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
