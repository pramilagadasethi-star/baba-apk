package com.rudra.texthistory

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var adapter: HistoryAdapter

    // ── Lifecycle ─────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.historyList)

        val header = buildHeader()
        listView.addHeaderView(header, null, false)

        adapter = HistoryAdapter()
        listView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
    }

    private fun refreshHistory() {
        val entries = HistoryManager(this).getAll()
        adapter.updateData(entries)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Header (title, instructions, buttons, test box) ───────────────
    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(8))

            // Title
            addView(TextView(this@MainActivity).apply {
                text = "Text History Keyboard"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#212121"))
                setPadding(0, 0, 0, dp(8))
            })

            // Instructions
            addView(TextView(this@MainActivity).apply {
                text = "Enable the keyboard in Settings, then switch to it. " +
                        "Everything you type will be saved here (except passwords and incognito fields)."
                textSize = 14f
                setTextColor(Color.parseColor("#757575"))
                setPadding(0, 0, 0, dp(16))
            })

            // Enable Keyboard
            addView(styledButton("Enable Keyboard") {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            })

            // Switch Keyboard
            addView(styledButton("Switch Keyboard") {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            })

            // Test EditText
            addView(EditText(this@MainActivity).apply {
                hint = "Test your keyboard here\u2026"
                val p = dp(12)
                setPadding(p, p, p, p)
                setBackgroundColor(Color.WHITE)
                layoutParams = lp().apply { bottomMargin = dp(8) }
            })

            // Clear All History
            addView(styledButton("Clear All History") { showClearConfirmation() })

            // Divider
            addView(View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            })

            // Section label
            addView(TextView(this@MainActivity).apply {
                text = "History"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#212121"))
                setPadding(0, dp(12), 0, dp(4))
            })

            // Empty-state text (visibility toggled in refreshHistory)
            emptyView = TextView(this@MainActivity).apply {
                text = "No history yet.\nStart typing with Text History Keyboard!"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#999999"))
                setPadding(0, dp(48), 0, dp(48))
            }
            addView(emptyView)
        }
    }

    // ── Confirmation dialog ───────────────────────────────────────────
    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear All History")
            .setMessage("Are you sure you want to delete all saved text history? This cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                HistoryManager(this).clearAll()
                refreshHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── List adapter ──────────────────────────────────────────────────
    private inner class HistoryAdapter : BaseAdapter() {

        private var entries = listOf<HistoryManager.HistoryEntry>()
        private val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        fun updateData(data: List<HistoryManager.HistoryEntry>) {
            entries = data; notifyDataSetChanged()
        }

        override fun getCount() = entries.size
        override fun getItem(pos: Int) = entries[pos]
        override fun getItemId(pos: Int) = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val entry = entries[pos]

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }

            // Text + timestamp column
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this@MainActivity).apply {
                text = entry.text
                textSize = 15f
                setTextColor(Color.parseColor("#212121"))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
            })
            col.addView(TextView(this@MainActivity).apply {
                text = fmt.format(Date(entry.timestamp))
                textSize = 12f
                setTextColor(Color.parseColor("#9E9E9E"))
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(col)

            // Copy button
            row.addView(Button(this@MainActivity).apply {
                text = "Copy"
                textSize = 12f
                setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("text_history", entry.text))
                    Toast.makeText(this@MainActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            })

            // Wrap with divider
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(row)
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(Color.parseColor("#EEEEEE"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { marginStart = dp(16); marginEnd = dp(16) }
                })
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun styledButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = lp()
        }
    }
}
