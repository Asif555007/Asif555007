package com.bdkeyboard.pro.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bdkeyboard.pro.data.Prefs

/** Onboarding + settings home. Built in code to keep the project dependency-light. */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        title("BD Keyboard Pro")
        text("বাংলা + English • Phonetic typing • Voice • AI writing tools • Privacy-first")

        header("1. Set up")
        button("Enable keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("Select keyboard") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        val test = EditText(this).apply { hint = "Tap here to test typing (ami, tumi, bhalo…)" }
        content.addView(test)

        header("2. Typing")
        check("Number row", prefs.numberRow) { prefs.numberRow = it }
        check("Bengali digits (০১২)", prefs.bengaliDigits) { prefs.bengaliDigits = it }
        check("Haptic feedback", prefs.haptics) { prefs.haptics = it }

        header("3. Snippets")
        prefs.snippets().forEach { (k, v) ->
            button("$k → ${v.take(30)}  (tap to delete)") { prefs.removeSnippet(k); render() }
        }
        button("+ Add snippet") { addSnippetDialog() }

        header("4. Personal dictionary")
        val words = prefs.learnedWords()
        if (words.isEmpty()) text("No learned words yet.")
        words.forEach { e ->
            val parts = e.split("\u0000")
            button("${parts.getOrNull(0)} → ${parts.getOrNull(1)}  (tap to delete)") { prefs.removeLearned(e); render() }
        }

        header("5. AI & voice")
        check("Enable AI writing tools", prefs.aiEnabled) { prefs.aiEnabled = it }
        text(getString(com.bdkeyboard.pro.R.string.ai_privacy_note))
        text(getString(com.bdkeyboard.pro.R.string.voice_privacy_note))

        header("6. Privacy & data")
        check("Private (incognito) mode", prefs.incognito) { prefs.incognito = it }
        check("Clipboard history (local only)", prefs.clipboardEnabled) {
            prefs.clipboardEnabled = it; if (!it) prefs.clearClips()
        }
        button("Clear clipboard history") { prefs.clearClips(); toast("Cleared") }
        button("Clear learned words") { prefs.clearLearned(); render() }
        text("Private mode stops learning and clipboard capture inside this keyboard. It cannot hide text from the app you are typing into or from the operating system.")
        text("Password fields are never learned, suggested or captured.")
    }

    private fun addSnippetDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val trig = EditText(this).apply { hint = "Trigger / title (e.g. mymail)" }
        val body = EditText(this).apply { hint = "Text to insert" }
        box.addView(trig); box.addView(body)
        AlertDialog.Builder(this).setTitle("New snippet").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val t = trig.text.toString().trim(); val b = body.text.toString()
                if (t.isNotEmpty() && b.isNotEmpty()) { prefs.putSnippet(t, b); render() }
            }.setNegativeButton("Cancel", null).show()
    }

    // ---- tiny view helpers ----
    private fun title(s: String) = content.addView(TextView(this).apply { text = s; textSize = 26f })
    private fun header(s: String) = content.addView(TextView(this).apply { text = s; textSize = 18f; setPadding(0, 40, 0, 8) })
    private fun text(s: String) = content.addView(TextView(this).apply { text = s; setPadding(0, 8, 0, 8) })
    private fun button(s: String, c: () -> Unit) = content.addView(Button(this).apply { text = s; setOnClickListener { c() } })
    private fun check(s: String, v: Boolean, c: (Boolean) -> Unit) =
        content.addView(CheckBox(this).apply { text = s; isChecked = v; setOnCheckedChangeListener { _, b -> c(b) } })
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
