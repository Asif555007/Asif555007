package com.bdkeyboard.pro.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bdkeyboard.pro.ai.AiAction
import com.bdkeyboard.pro.ai.AiClient
import com.bdkeyboard.pro.ai.AiResult
import com.bdkeyboard.pro.data.Prefs
import com.bdkeyboard.pro.engine.BanglaPhonetic
import com.bdkeyboard.pro.ui.MicPermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BDKeyboardService : InputMethodService() {

    private enum class Panel { NONE, EMOJI, CLIPBOARD, SNIPPETS, AI }

    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceInput
    private val ai = AiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var theme = KbTheme.DARK

    // ---- editor / typing state ----
    private val composing = StringBuilder()
    private var sensitive = false          // password-like field: no learning, suggestions, clipboard, AI, voice
    private var noLearning = false
    private var plainLatin = false         // email / URL / number / phone: no transliteration
    private var shift = 0                  // 0 off, 1 once, 2 caps lock
    private var symbols = false
    private var panel = Panel.NONE
    private var suggestions: List<String> = emptyList()
    private var voiceStatus: String? = null
    private var selStart = 0
    private var selEnd = 0

    // ---- AI panel state ----
    private var aiSource: String = ""
    private var aiResult: String? = null
    private var aiStatus: String? = null
    private var aiJob: Job? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.clipboardEnabled || prefs.incognito || sensitive) return@OnPrimaryClipChangedListener
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
        if (!t.isNullOrBlank() && t.length <= 2000) prefs.addClip(t)
    }

    private val emojis = listOf(
        "😀","😂","🥰","😍","😘","😊","😉","😎","🤔","😢","😭","😡",
        "👍","👎","🙏","👏","🙌","💪","🤝","👋","❤️","💔","🔥","✨",
        "🎉","🎂","🌹","🌙","☀️","⭐","✅","❌","🇧🇩","📞","📧","💬"
    )

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        voice = VoiceInput(this, ::onVoiceState, ::onVoiceText)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .removePrimaryClipChangedListener(clipListener)
        voice.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root = r
        render()
        return r
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing.setLength(0)
        suggestions = emptyList()
        panel = Panel.NONE
        voice.stop(); voiceStatus = null
        val type = attribute?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        sensitive = when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
        plainLatin = sensitive || cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            (cls == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        noLearning = sensitive ||
            ((attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        shift = 0; symbols = false
        resolveTheme()
        render()
    }

    override fun onFinishInput() {
        finishComposing()
        voice.stop()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart; selEnd = newSelEnd
        // Cursor moved away from the composing region: drop our composing state.
        if (composing.isNotEmpty() && candidatesStart == -1 && candidatesEnd == -1) {
            composing.setLength(0); suggestions = emptyList(); renderSuggestionsOnly()
        }
    }

    // ------------------------------------------------------------------ theme

    private fun resolveTheme() {
        theme = when (prefs.theme) {
            1 -> KbTheme.LIGHT
            2 -> KbTheme.DARK
            else -> if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES) KbTheme.DARK else KbTheme.LIGHT
        }
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val r = root ?: return
        r.removeAllViews()
        r.setBackgroundColor(theme.bg)

        // One-hand mode: shrink width, push to one side.
        val screenW = resources.displayMetrics.widthPixels
        val side = (screenW * 0.18f).toInt()
        when (prefs.oneHand) {
            1 -> r.setPadding(0, 0, side, 0)
            2 -> r.setPadding(side, 0, 0, 0)
            else -> r.setPadding(0, 0, 0, 0)
        }

        r.addView(buildSuggestionRow())
        r.addView(buildToolbar())
        if (panel != Panel.NONE) {
            r.addView(buildPanel())
        } else {
            buildKeys(r)
        }
    }

    private fun renderSuggestionsOnly() {
        val r = root ?: return
        if (r.childCount > 0) {
            r.removeViewAt(0)
            r.addView(buildSuggestionRow(), 0)
        }
    }

    private fun buildSuggestionRow(): View {
        val sv = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(theme.strip)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val h = KeyboardUi.dp(this, 40)
        row.minimumHeight = h
        when {
            voiceStatus != null -> row.addView(KeyboardUi.label(this, "🎤 $voiceStatus", theme, 14f).apply { minimumHeight = h; gravity = android.view.Gravity.CENTER_VERTICAL })
            suggestions.isNotEmpty() -> suggestions.forEachIndexed { i, s ->
                row.addView(TextView(this).apply {
                    text = s
                    setTextColor(if (i == 0) theme.accent else theme.text)
                    textSize = 18f
                    minimumHeight = h
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(KeyboardUi.dp(context, 14), 0, KeyboardUi.dp(context, 14), 0)
                    setOnClickListener { pickSuggestion(i) }
                })
            }
            prefs.incognito -> row.addView(KeyboardUi.label(this, "🔒 Private mode on — no learning or clipboard capture", theme, 12f).apply { minimumHeight = h; gravity = android.view.Gravity.CENTER_VERTICAL })
            else -> row.addView(KeyboardUi.label(this, if (prefs.banglaMode && !plainLatin) "বাংলা (phonetic)" else "English", theme, 12f).apply { minimumHeight = h; gravity = android.view.Gravity.CENTER_VERTICAL })
        }
        sv.addView(row)
        return sv
    }

    private fun buildToolbar(): View {
        val sv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun tool(label: String, desc: String, active: Boolean = false, click: () -> Unit) {
            row.addView(TextView(this).apply {
                text = label
                textSize = 16f
                contentDescription = desc
                gravity = android.view.Gravity.CENTER
                setTextColor(if (active) android.graphics.Color.WHITE else theme.text)
                background = KeyboardUi.bg(if (active) theme.accent else theme.keyAlt, 14, context)
                setPadding(KeyboardUi.dp(context, 12), KeyboardUi.dp(context, 4), KeyboardUi.dp(context, 12), KeyboardUi.dp(context, 4))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(KeyboardUi.dp(context, 3), KeyboardUi.dp(context, 3), KeyboardUi.dp(context, 3), KeyboardUi.dp(context, 3)) }
                setOnClickListener { click() }
            })
        }
        tool("🎤", "Voice typing", voiceStatus != null) { toggleVoice() }
        tool("😀", "Emoji", panel == Panel.EMOJI) { togglePanel(Panel.EMOJI) }
        tool("📋", "Clipboard", panel == Panel.CLIPBOARD) { togglePanel(Panel.CLIPBOARD) }
        tool("⚡", "Snippets", panel == Panel.SNIPPETS) { togglePanel(Panel.SNIPPETS) }
        tool("✨ AI", "AI writing tools", panel == Panel.AI) { openAi() }
        tool("🎨", "Switch theme") { prefs.theme = (prefs.theme + 1) % 3; resolveTheme(); render() }
        tool("☝", "One-hand mode", prefs.oneHand != 0) { prefs.oneHand = (prefs.oneHand + 1) % 3; render() }
        tool("🔒", "Private mode", prefs.incognito) {
            prefs.incognito = !prefs.incognito
            if (prefs.incognito) { prefs.clearClips() }
            render()
        }
        tool("⚙", "Settings") {
            startActivity(Intent(this, com.bdkeyboard.pro.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        sv.addView(row)
        return sv
    }

    private fun togglePanel(p: Panel) {
        panel = if (panel == p) Panel.NONE else p
        render()
    }

    // ------------------------------------------------------------------ keys

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symRows = listOf("1234567890", "@#\$_&-+()/", "*\"':;!?%=", "~`|•√π÷×{}")

    private fun buildKeys(parent: LinearLayout) {
        val t = theme
        if (symbols) {
            symRows.forEach { rowChars ->
                val row = KeyboardUi.row(this)
                rowChars.forEach { ch -> row.addView(charKey(ch.toString())) }
                parent.addView(row)
            }
            // Editing controls: cursor, select all, cut/copy/paste
            val edit = KeyboardUi.row(this)
            edit.addView(KeyboardUi.key(this, "◀", t, alt = true, desc = "Cursor left") { moveCursor(KeyEvent.KEYCODE_DPAD_LEFT) })
            edit.addView(KeyboardUi.key(this, "▶", t, alt = true, desc = "Cursor right") { moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT) })
            edit.addView(KeyboardUi.key(this, "All", t, alt = true, desc = "Select all") { menuAction(android.R.id.selectAll) })
            edit.addView(KeyboardUi.key(this, "Cut", t, alt = true) { menuAction(android.R.id.cut) })
            edit.addView(KeyboardUi.key(this, "Copy", t, alt = true) { menuAction(android.R.id.copy) })
            edit.addView(KeyboardUi.key(this, "Paste", t, alt = true) { menuAction(android.R.id.paste) })
            parent.addView(edit)
        } else {
            if (prefs.numberRow) {
                val row = KeyboardUi.row(this)
                "1234567890".forEach { row.addView(charKey(it.toString(), alt = true, height = 38)) }
                parent.addView(row)
            }
            letterRows.forEachIndexed { idx, letters ->
                val row = KeyboardUi.row(this)
                if (idx == 1) row.addView(spacer(0.5f))
                if (idx == 2) {
                    row.addView(KeyboardUi.key(this, if (shift == 2) "⇪" else "⇧", t, 1.5f, alt = true, accent = shift != 0, desc = "Shift") { cycleShift() })
                }
                letters.forEach { c ->
                    val shown = if (shift != 0) c.uppercaseChar() else c
                    row.addView(charKey(shown.toString()))
                }
                if (idx == 1) row.addView(spacer(0.5f))
                if (idx == 2) row.addView(backspaceKey(1.5f))
                parent.addView(row)
            }
        }
        // Bottom row
        val bottom = KeyboardUi.row(this)
        bottom.addView(KeyboardUi.key(this, if (symbols) "ABC" else "?123", t, 1.3f, alt = true, desc = "Symbols") { symbols = !symbols; render() })
        bottom.addView(KeyboardUi.key(this, if (prefs.banglaMode) "বাং" else "EN", t, 1.1f, alt = true, desc = "Switch language") { toggleLanguage() })
        bottom.addView(KeyboardUi.key(this, ",", t, 0.9f) { type(",") })
        bottom.addView(KeyboardUi.key(this, if (symbols) "⌫" else "␣", t, 4f, desc = "Space") {
            if (symbols) onBackspace() else onSpace()
        })
        bottom.addView(KeyboardUi.key(this, if (prefs.banglaMode && !plainLatin) "।" else ".", t, 0.9f) { type(if (prefs.banglaMode && !plainLatin) "।" else ".") })
        bottom.addView(KeyboardUi.key(this, "⏎", t, 1.3f, accent = true, desc = "Enter") { onEnter() })
        parent.addView(bottom)
    }

    private fun moveCursor(code: Int) {
        finishComposing()
        currentInputConnection?.run {
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun menuAction(id: Int) {
        finishComposing()
        currentInputConnection?.performContextMenuAction(id)
    }

    private fun spacer(w: Float) = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, w) }

    private fun charKey(label: String, alt: Boolean = false, height: Int = 46): View =
        KeyboardUi.key(this, label, theme, 1f, heightDp = height, alt = alt) { onChar(label) }

    private fun backspaceKey(weight: Float): View {
        val k = KeyboardUi.key(this, "⌫", theme, weight, alt = true, desc = "Backspace") {}
        val repeat = object : Runnable {
            override fun run() { onBackspace(); handler.postDelayed(this, 50) }
        }
        k.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; onBackspace(); handler.postDelayed(repeat, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; handler.removeCallbacks(repeat) }
            }
            true
        }
        return k
    }

    private fun cycleShift() {
        shift = when (shift) { 0 -> 1; 1 -> 2; else -> 0 }
        render()
    }

    private fun toggleLanguage() {
        finishComposing()
        prefs.banglaMode = !prefs.banglaMode
        render()
    }

    // ------------------------------------------------------------------ text input

    private fun onChar(ch: String) {
        buzz()
        val isLetter = ch.length == 1 && ch[0].isLetter()
        val transliterate = prefs.banglaMode && !plainLatin && isLetter
        if (transliterate) {
            composing.append(ch)
            updateComposing()
        } else {
            type(ch)
        }
        if (shift == 1) { shift = 0; render() }
    }

    /** Commit any composition, then insert literal text (digits honour the digit-style setting). */
    private fun type(s: String) {
        commitTopCandidate(learn = true)
        val out = if (prefs.bengaliDigits && !plainLatin) BanglaPhonetic.toBengaliDigits(s) else s
        currentInputConnection?.commitText(out, 1)
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val latin = composing.toString()
        if (latin.isEmpty()) { ic.commitText("", 1); suggestions = emptyList(); renderSuggestionsOnly(); return }
        val cands = candidatesFor(latin)
        ic.setComposingText(cands.firstOrNull() ?: latin, 1)
        suggestions = if (sensitive) emptyList() else (cands + latin).distinct().take(6)
        renderSuggestionsOnly()
    }

    private fun candidatesFor(latin: String): List<String> {
        val learned = prefs.learnedWords()
            .mapNotNull { e -> e.split("\u0000", limit = 2).takeIf { it.size == 2 && it[0] == latin.lowercase() }?.get(1) }
        return (learned + BanglaPhonetic.candidates(latin)).distinct()
    }

    private fun commitTopCandidate(learn: Boolean) {
        if (composing.isEmpty()) return
        val latin = composing.toString()
        val top = candidatesFor(latin).firstOrNull() ?: latin
        currentInputConnection?.commitText(top, 1)
        composing.setLength(0); suggestions = emptyList(); renderSuggestionsOnly()
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.setLength(0); suggestions = emptyList()
        }
    }

    private fun pickSuggestion(index: Int) {
        val chosen = suggestions.getOrNull(index) ?: return
        val latin = composing.toString()
        val ic = currentInputConnection ?: return
        ic.commitText("$chosen ", 1)
        // Learn only a deliberate non-default choice, never in private mode or sensitive fields.
        if (!prefs.incognito && !noLearning && latin.isNotEmpty() && chosen != latin && index > 0) {
            prefs.addLearned("${latin.lowercase()}\u0000$chosen")
        }
        composing.setLength(0); suggestions = emptyList(); renderSuggestionsOnly()
    }

    private fun onSpace() {
        buzz()
        if (composing.isNotEmpty()) {
            commitTopCandidate(learn = true)
        }
        currentInputConnection?.commitText(" ", 1)
    }

    private fun onBackspace() {
        buzz()
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            updateComposing()
        } else if (ic.getSelectedText(0).isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun onEnter() {
        buzz()
        commitTopCandidate(learn = true)
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        if (!prefs.haptics) return
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            ?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ------------------------------------------------------------------ voice

    private fun toggleVoice() {
        if (voiceStatus != null) { voice.stop(); voiceStatus = null; render(); return }
        if (sensitive) { voiceStatus = null; return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Ask only now, at the moment the user taps the mic.
            startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            voiceStatus = "Allow microphone, then tap 🎤 again"
            render(); return
        }
        finishComposing()
        voice.start(if (prefs.banglaMode) "bn-BD" else "en-US")
    }

    private fun onVoiceState(state: VoiceInput.State, msg: String?) {
        voiceStatus = when (state) {
            VoiceInput.State.LISTENING -> "Listening… tap 🎤 to cancel"
            VoiceInput.State.PROCESSING -> "Processing (speech service may use the network)…"
            VoiceInput.State.DONE -> null
            VoiceInput.State.ERROR -> msg ?: "Voice error"
        }
        render()
        if (state == VoiceInput.State.ERROR) handler.postDelayed({ voiceStatus = null; render() }, 2500)
    }

    private fun onVoiceText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    // ------------------------------------------------------------------ panels

    private fun panelFrame(content: View): View {
        val h = KeyboardUi.dp(this, 46 * 4 + 12)
        val sv = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, h)
            addView(content)
        }
        return sv
    }

    private fun buildPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = KeyboardUi.dp(this, 6)
        col.setPadding(pad, pad, pad, pad)
        when (panel) {
            Panel.EMOJI -> emojis.chunked(8).forEach { chunk ->
                val row = KeyboardUi.row(this)
                chunk.forEach { e -> row.addView(KeyboardUi.key(this, e, theme, sizeSp = 22f) { type(e) }) }
                repeat(8 - chunk.size) { row.addView(spacer(1f)) }
                col.addView(row)
            }
            Panel.CLIPBOARD -> buildClipboardPanel(col)
            Panel.SNIPPETS -> buildSnippetsPanel(col)
            Panel.AI -> buildAiPanel(col)
            Panel.NONE -> {}
        }
        return panelFrame(col)
    }

    private fun buildClipboardPanel(col: LinearLayout) {
        if (!prefs.clipboardEnabled) {
            col.addView(KeyboardUi.label(this, "Clipboard history is off. It is stored only on this device and is never collected from password fields.", theme))
            col.addView(KeyboardUi.key(this, "Turn on clipboard history", theme, accent = true) { prefs.clipboardEnabled = true; render() }.also {
                it.layoutParams = LinearLayout.LayoutParams(-1, KeyboardUi.dp(this, 46))
            })
            return
        }
        val items = prefs.clipItems()
        if (items.isEmpty()) col.addView(KeyboardUi.label(this, "Nothing copied yet.", theme))
        items.forEach { clip ->
            val row = KeyboardUi.row(this)
            row.addView(TextView(this).apply {
                text = clip.take(120)
                maxLines = 2
                setTextColor(theme.text)
                background = KeyboardUi.bg(theme.key, 7, context)
                setPadding(KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8), KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(2, 3, 2, 3) }
                setOnClickListener { type(clip) }
            })
            row.addView(KeyboardUi.key(this, "✕", theme, 0.18f, alt = true, desc = "Delete item") { prefs.removeClip(clip); render() })
            col.addView(row)
        }
        val ctl = KeyboardUi.row(this)
        ctl.addView(KeyboardUi.key(this, "Clear all", theme, alt = true) { prefs.clearClips(); render() })
        ctl.addView(KeyboardUi.key(this, "Turn off", theme, alt = true) { prefs.clipboardEnabled = false; prefs.clearClips(); render() })
        col.addView(ctl)
    }

    private fun buildSnippetsPanel(col: LinearLayout) {
        val s = prefs.snippets()
        if (s.isEmpty()) col.addView(KeyboardUi.label(this, "No snippets yet. Add them in the BD Keyboard Pro app (⚙).", theme))
        s.forEach { (trigger, text) ->
            col.addView(TextView(this).apply {
                this.text = "$trigger  →  ${text.take(60)}"
                setTextColor(theme.text)
                background = KeyboardUi.bg(theme.key, 7, context)
                setPadding(KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 10))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(2, 3, 2, 3) }
                setOnClickListener { type(text) } // explicit insert; text stays editable in the field
            })
        }
    }

    // ------------------------------------------------------------------ AI panel

    private fun openAi() {
        if (panel == Panel.AI) { panel = Panel.NONE; render(); return }
        finishComposing()
        if (sensitive) { voiceStatus = null; return }
        panel = Panel.AI
        aiSource = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        aiResult = null; aiStatus = null
        render()
    }

    private fun buildAiPanel(col: LinearLayout) {
        if (!prefs.aiEnabled) {
            col.addView(KeyboardUi.label(this, "AI features are turned off in settings.", theme)); return
        }
        if (!prefs.aiConsentGiven) {
            col.addView(KeyboardUi.label(this,
                "AI tools send ONLY the text you select, after you tap an action, to our server for processing. Nothing is sent automatically, and nothing is stored by default.", theme))
            col.addView(KeyboardUi.key(this, "I understand — enable AI", theme, accent = true) { prefs.aiConsentGiven = true; render() }
                .also { it.layoutParams = LinearLayout.LayoutParams(-1, KeyboardUi.dp(this, 46)) })
            return
        }
        col.addView(KeyboardUi.label(this, "🌐 Online · sends only the text below", theme, 11f))
        col.addView(TextView(this).apply {
            text = if (aiSource.isBlank()) "Select text in the app first, then reopen ✨ AI — or paste from clipboard below." else aiSource.take(300)
            setTextColor(theme.text)
            background = KeyboardUi.bg(theme.keyAlt, 7, context)
            setPadding(KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8), KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8))
        })
        val src = KeyboardUi.row(this)
        src.addView(KeyboardUi.key(this, "Use selection", theme, alt = true, heightDp = 38) {
            aiSource = currentInputConnection?.getSelectedText(0)?.toString().orEmpty(); render()
        })
        src.addView(KeyboardUi.key(this, "Paste clipboard", theme, alt = true, heightDp = 38) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            aiSource = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty(); render()
        })
        col.addView(src)

        AiAction.values().toList().chunked(3).forEach { chunk ->
            val row = KeyboardUi.row(this)
            chunk.forEach { a -> row.addView(KeyboardUi.key(this, a.label, theme, heightDp = 40, sizeSp = 13f) { runAi(a) }) }
            col.addView(row)
        }
        aiStatus?.let { col.addView(KeyboardUi.label(this, it, theme)) }
        aiResult?.let { result ->
            col.addView(TextView(this).apply {
                text = result
                setTextColor(theme.text)
                textSize = 16f
                background = KeyboardUi.bg(theme.key, 7, context)
                setPadding(KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8), KeyboardUi.dp(context, 10), KeyboardUi.dp(context, 8))
            })
            val act = KeyboardUi.row(this)
            act.addView(KeyboardUi.key(this, "Insert", theme, accent = true, heightDp = 40) { insertAi(result, replace = false) })
            act.addView(KeyboardUi.key(this, "Replace", theme, heightDp = 40) { insertAi(result, replace = true) })
            act.addView(KeyboardUi.key(this, "Copy", theme, alt = true, heightDp = 40) { copyText(result) })
            act.addView(KeyboardUi.key(this, "Cancel", theme, alt = true, heightDp = 40) { aiResult = null; aiStatus = null; render() })
            col.addView(act)
        }
    }

    private fun runAi(action: AiAction) {
        if (aiSource.isBlank()) { aiStatus = "Choose some text first."; render(); return }
        aiJob?.cancel()
        aiStatus = "Working…"; aiResult = null; render()
        aiJob = scope.launch {
            when (val r = ai.run(action, aiSource)) {
                is AiResult.Success -> { aiResult = r.text; aiStatus = null }
                is AiResult.Error -> { aiResult = null; aiStatus = r.message }
            }
            if (panel == Panel.AI) render()
        }
    }

    /** Never silently overwrites: Replace only affects the user's current selection. */
    private fun insertAi(text: String, replace: Boolean) {
        val ic: InputConnection = currentInputConnection ?: return
        if (replace) {
            ic.commitText(text, 1)            // replaces current selection, or inserts at cursor if none
        } else {
            ic.setSelection(selEnd, selEnd)   // collapse selection to its end, keep original text
            ic.commitText(text, 1)
        }
        panel = Panel.NONE; aiResult = null; render()
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("BD Keyboard Pro", text))
        aiStatus = "Copied."; render()
    }
}
