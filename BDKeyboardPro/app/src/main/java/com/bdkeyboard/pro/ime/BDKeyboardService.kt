package com.bdkeyboard.pro.ime

import android.Manifest
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
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bdkeyboard.pro.ai.AiClient
import com.bdkeyboard.pro.data.Prefs
import com.bdkeyboard.pro.engine.BanglaPhonetic
import com.bdkeyboard.pro.ui.MicPermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class BDKeyboardService : InputMethodService() {

    private enum class Panel { NONE, EMOJI, CLIPBOARD, SNIPPETS, AI, PHONE_PAD }

    private lateinit var prefs: Prefs
    private lateinit var voice: VoiceInput
    private val ai = AiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var theme = KbTheme.DARK

    private val composing = StringBuilder()
    private var lastCommittedWord: String? = null
    private var sensitive = false
    private var noLearning = false
    private var plainLatin = false
    private var shift = 0
    private var symbols = false
    private var panel = Panel.NONE
    private var suggestions: List<String> = emptyList()
    private var voiceStatus: String? = null
    private var voiceMeterText: String? = null
    private var longPressSymbols: List<String>? = null
    private var selStart = 0
    private var selEnd = 0

    private var aiSource: String = ""
    private var aiResult: String? = null
    private var aiStatus: String? = null
    private var aiJob: Job? = null

    private val longPressMap: Map<Char, List<String>> = mapOf(
        'a' to listOf("@", "á", "à", "â"),
        'd' to listOf("৳", "$", "€", "£"),
        'j' to listOf("+", "=", "×", "÷"),
        'm' to listOf("?", "!", ";", ":"),
        's' to listOf("§", "ß", "$"),
        'e' to listOf("€", "é", "è"),
        'n' to listOf("ñ", "ং", "ণ"),
        'o' to listOf("°", "ó", "ô"),
        'c' to listOf("©", "ç"),
        'r' to listOf("®", "ড়", "ঢ়")
    )

    private val banglaLayoutNames = listOf("ফোনেটিক", "জাতীয়", "বর্ণমালা", "ক্লাসিক")

    private val jatiyoRows = listOf(
        listOf("দ", "ফ", "গ", "জ", "ক", "ল", "প", "ব", "ম", "ত"),
        listOf("হ", "স", "ড", "য়", "র", "ন", "চ", "ট", "খ", "ধ"),
        listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "এ", "ঐ", "ও", "ঔ")
    )

    private val bornomalaRows = listOf(
        listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"),
        listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"),
        listOf("প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ")
    )

    private val classicRows = listOf(
        listOf("া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ"),
        listOf("ক", "খ", "গ", "ঘ", "চ", "ছ", "জ", "ট", "ত", "দ"),
        listOf("প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "স")
    )

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.clipboardEnabled || prefs.incognito || sensitive) return@OnPrimaryClipChangedListener
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
        if (!t.isNullOrBlank() && t.length <= 2000) prefs.addClip(t)
    }

    private val emojis = listOf(
        "😀", "😂", "🥰", "😍", "😘", "😊", "😉", "😎", "🤔", "😢", "😭", "😡",
        "👍", "👎", "🙏", "👏", "🙌", "💪", "🤝", "👋", "❤️", "💔", "🔥", "✨",
        "🎉", "🎂", "🌹", "🌙", "☀️", "⭐", "✅", "❌", "🇧🇩", "📞", "📧", "💬"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        // VoiceInput constructor takes 3 arguments: (Context, (VoiceInput.State, String?) -> Unit, (String) -> Unit)
        voice = VoiceInput(this, ::onVoiceState, ::onVoiceText)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .removePrimaryClipChangedListener(clipListener)
        voice.stop()
        aiJob?.cancel()
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
        longPressSymbols = null
        panel = Panel.NONE
        voice.stop()
        voiceStatus = null
        voiceMeterText = null
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
        shift = 0
        symbols = false
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
        selStart = newSelStart
        selEnd = newSelEnd
        if (composing.isNotEmpty() && candidatesStart == -1 && candidatesEnd == -1) {
            composing.setLength(0)
            suggestions = emptyList()
            renderSuggestionsOnly()
        }
    }

    private fun resolveTheme() {
        theme = when (prefs.theme) {
            1 -> KbTheme.LIGHT
            2 -> KbTheme.DARK
            else -> if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            ) KbTheme.DARK else KbTheme.LIGHT
        }
    }

    private fun render() {
        val r = root ?: return
        r.removeAllViews()
        r.setBackgroundColor(theme.bg)

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

        val lpSyms = longPressSymbols
        when {
            lpSyms != null -> {
                lpSyms.forEach { sym ->
                    row.addView(TextView(this).apply {
                        text = sym
                        setTextColor(theme.accent)
                        textSize = 18f
                        minimumHeight = h
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(KeyboardUi.dp(context, 14), 0, KeyboardUi.dp(context, 14), 0)
                        setOnClickListener {
                            type(sym)
                            longPressSymbols = null
                            renderSuggestionsOnly()
                        }
                    })
                }
            }
            voiceStatus != null -> {
                val meter = voiceMeterText?.let { " [$it]" }.orEmpty()
                row.addView(KeyboardUi.label(this, "🎤 $voiceStatus$meter", theme, 13f).apply {
                    minimumHeight = h
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            suggestions.isNotEmpty() -> suggestions.forEachIndexed { i, s ->
                row.addView(TextView(this).apply {
                    text = s
                    setTextColor(if (i == 0) theme.accent else theme.text)
                    textSize = 18f
                    minimumHeight = h
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(KeyboardUi.dp(context, 14), 0, KeyboardUi.dp(context, 14), 0)
                    setOnClickListener { pickSuggestion(i) }
                })
            }
            prefs.incognito -> row.addView(
                KeyboardUi.label(this, "🔒 Private mode on — no learning or clipboard capture", theme, 12f).apply {
                    minimumHeight = h
                    gravity = Gravity.CENTER_VERTICAL
                }
            )
            else -> {
                val nextPred: String? = null
                val layoutIndex = 0
                val modeLabel = if (prefs.banglaMode && !plainLatin) {
                    "বাংলা (${banglaLayoutNames[layoutIndex]})"
                } else {
                    "English"
                }
                val statusText = if (nextPred != null) "$modeLabel · Next: $nextPred" else modeLabel
                row.addView(KeyboardUi.label(this, statusText, theme, 12f).apply {
                    minimumHeight = h
                    gravity = Gravity.CENTER_VERTICAL
                })
                if (nextPred != null) {
                    row.addView(TextView(this).apply {
                        text = nextPred
                        setTextColor(theme.accent)
                        textSize = 16f
                        minimumHeight = h
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(KeyboardUi.dp(context, 12), 0, KeyboardUi.dp(context, 12), 0)
                        setOnClickListener {
                            currentInputConnection?.commitText("$nextPred ", 1)
                            lastCommittedWord = nextPred
                            renderSuggestionsOnly()
                        }
                    })
                }
            }
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
                textSize = 15f
                contentDescription = desc
                gravity = Gravity.CENTER
                setTextColor(if (active) android.graphics.Color.WHITE else theme.text)
                background = KeyboardUi.bg(if (active) theme.accent else theme.keyAlt, 14, context)
                setPadding(
                    KeyboardUi.dp(context, 11),
                    KeyboardUi.dp(context, 4),
                    KeyboardUi.dp(context, 11),
                    KeyboardUi.dp(context, 4)
                )
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                    val m = KeyboardUi.dp(context, 3)
                    setMargins(m, m, m, m)
                }
                setOnClickListener { click() }
            })
        }
        tool("🎤", "Voice typing", voiceStatus != null) { toggleVoice() }
        tool("🔢", "Phone number pad", panel == Panel.PHONE_PAD) { togglePanel(Panel.PHONE_PAD) }
        tool("😀", "Emoji", panel == Panel.EMOJI) { togglePanel(Panel.EMOJI) }
        tool("📋", "Clipboard", panel == Panel.CLIPBOARD) { togglePanel(Panel.CLIPBOARD) }
        tool("⚡", "Snippets", panel == Panel.SNIPPETS) { togglePanel(Panel.SNIPPETS) }
        tool("✨ AI", "AI writing tools", panel == Panel.AI) { openAi() }
        if (prefs.banglaMode) {
            tool(banglaLayoutNames[0], "Bangla layout style", true) {
                finishComposing()
                render()
            }
        }
        tool("🎨", "Switch theme") {
            prefs.theme = (prefs.theme + 1) % 3
            resolveTheme()
            render()
        }
        tool("☝", "One-hand mode", prefs.oneHand != 0) {
            prefs.oneHand = (prefs.oneHand + 1) % 3
            render()
        }
        tool("🔒", "Private mode", prefs.incognito) {
            prefs.incognito = !prefs.incognito
            if (prefs.incognito) {
                prefs.clearClips()
            }
            render()
        }
        tool("⚙", "Settings") {
            startActivity(
                Intent(this, com.bdkeyboard.pro.ui.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        sv.addView(row)
        return sv
    }

    private fun togglePanel(p: Panel) {
        panel = if (panel == p) Panel.NONE else p
        render()
    }

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symRows = listOf("1234567890", "@#$_&-+()/", "*\"':;!?%=", "~`|•√π÷×{}")

    private fun buildKeys(parent: LinearLayout) {
        val t = theme
        if (symbols) {
            symRows.forEach { rowChars ->
                val row = KeyboardUi.row(this)
                rowChars.forEach { ch -> row.addView(charKey(ch.toString())) }
                parent.addView(row)
            }
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
            val activeBanglaGrid: List<List<String>>? = null

            if (activeBanglaGrid != null) {
                activeBanglaGrid.forEachIndexed { idx, glyphs ->
                    val row = KeyboardUi.row(this)
                    if (idx == 2) {
                        row.addView(KeyboardUi.key(this, "⌫", t, 1.3f, alt = true, desc = "Backspace") { onBackspace() })
                    }
                    glyphs.forEach { g ->
                        row.addView(KeyboardUi.key(this, g, t, 1f, heightDp = 46, sizeSp = 17f) {
                            buzz()
                            commitTopCandidate(learn = true)
                            currentInputConnection?.commitText(g, 1)
                        })
                    }
                    if (idx == 2) {
                        row.addView(KeyboardUi.key(this, "⏎", t, 1.3f, accent = true, desc = "Enter") { onEnter() })
                    }
                    parent.addView(row)
                }
            } else {
                letterRows.forEachIndexed { idx, letters ->
                    val row = KeyboardUi.row(this)
                    if (idx == 1) row.addView(spacer(0.5f))
                    if (idx == 2) {
                        row.addView(
                            KeyboardUi.key(
                                this,
                                if (shift == 2) "⇪" else "⇧",
                                t,
                                1.5f,
                                alt = true,
                                accent = shift != 0,
                                desc = "Shift"
                            ) { cycleShift() }
                        )
                    }
                    letters.forEach { c ->
                        val shown = if (shift != 0) c.uppercaseChar() else c
                        val lpList = longPressMap[c.lowercaseChar()]
                        row.addView(charKey(shown.toString(), longPressList = lpList))
                    }
                    if (idx == 1) row.addView(spacer(0.5f))
                    if (idx == 2) row.addView(backspaceKey(1.5f))
                    parent.addView(row)
                }
            }
        }
        val bottom = KeyboardUi.row(this)
        bottom.addView(KeyboardUi.key(this, if (symbols) "ABC" else "?123", t, 1.3f, alt = true, desc = "Symbols") {
            symbols = !symbols
            render()
        })
        bottom.addView(KeyboardUi.key(this, if (prefs.banglaMode) "বাং" else "EN", t, 1.1f, alt = true, desc = "Switch language") {
            toggleLanguage()
        })
        bottom.addView(KeyboardUi.key(this, ",", t, 0.9f) { type(",") })
        bottom.addView(KeyboardUi.key(this, if (symbols) "⌫" else "␣", t, 4f, desc = "Space") {
            if (symbols) onBackspace() else onSpace()
        })
        bottom.addView(
            KeyboardUi.key(this, if (prefs.banglaMode && !plainLatin) "।" else ".", t, 0.9f) {
                type(if (prefs.banglaMode && !plainLatin) "।" else ".")
            }
        )
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

    private fun charKey(
        label: String,
        alt: Boolean = false,
        height: Int = 46,
        longPressList: List<String>? = null
    ): View {
        val view = KeyboardUi.key(this, label, theme, 1f, heightDp = height, alt = alt) {
            longPressSymbols = null
            onChar(label)
        }
        if (!longPressList.isNullOrEmpty()) {
            view.setOnLongClickListener {
                buzz()
                longPressSymbols = longPressList
                renderSuggestionsOnly()
                true
            }
        }
        return view
    }

    private fun backspaceKey(weight: Float): View {
        val k = KeyboardUi.key(this, "⌫", theme, weight, alt = true, desc = "Backspace") {}
        var downX = 0f
        var clearedAll = false
        val repeat = object : Runnable {
            override fun run() {
                if (!clearedAll) {
                    onBackspace()
                    handler.postDelayed(this, 65)
                }
            }
        }
        k.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    downX = e.rawX
                    clearedAll = false
                    onBackspace()
                    handler.postDelayed(repeat, 420)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (dx < -220f && !clearedAll) {
                        clearedAll = true
                        handler.removeCallbacks(repeat)
                        composing.setLength(0)
                        suggestions = emptyList()
                        currentInputConnection?.deleteSurroundingText(500, 500)
                        renderSuggestionsOnly()
                    } else if (dx < -48f && !clearedAll) {
                        downX = e.rawX
                        deletePreviousWord()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return k
    }

    private fun deletePreviousWord() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            suggestions = emptyList()
            ic.setComposingText("", 1)
            renderSuggestionsOnly()
            return
        }
        val before = ic.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val charsToDelete = if (lastSpace >= 0) before.length - (lastSpace + 1) else before.length
        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        }
    }

    private fun cycleShift() {
        shift = when (shift) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        render()
    }

    private fun toggleLanguage() {
        finishComposing()
        prefs.banglaMode = !prefs.banglaMode
        render()
    }

    private fun onChar(ch: String) {
        buzz()
        val isLetter = ch.length == 1 && ch[0].isLetter()
        val usePhonetic = prefs.banglaMode && !plainLatin && isLetter
        if (usePhonetic) {
            composing.append(ch)
            updateComposing()
        } else if (!prefs.banglaMode && !plainLatin && isLetter) {
            composing.append(ch)
            updateEnglishComposing()
        } else {
            type(ch)
        }
        if (shift == 1) {
            shift = 0
            render()
        }
    }

    private fun type(s: String) {
        commitTopCandidate(learn = true)
        val out = if (prefs.bengaliDigits && !plainLatin) BanglaPhonetic.toBengaliDigits(s) else s
        currentInputConnection?.commitText(out, 1)
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val latin = composing.toString()
        if (latin.isEmpty()) {
            ic.commitText("", 1)
            suggestions = emptyList()
            renderSuggestionsOnly()
            return
        }
        val cands = candidatesFor(latin)
        ic.setComposingText(cands.firstOrNull() ?: latin, 1)
        suggestions = if (sensitive) emptyList() else (cands + latin).distinct().take(6)
        renderSuggestionsOnly()
    }

    private fun updateEnglishComposing() {
        val ic = currentInputConnection ?: return
        val raw = composing.toString()
        if (raw.isEmpty()) {
            ic.commitText("", 1)
            suggestions = emptyList()
            renderSuggestionsOnly()
            return
        }
        val engCands: List<String> = emptyList()
        ic.setComposingText(raw, 1)
        suggestions = if (sensitive) emptyList() else (engCands + raw).distinct().take(6)
        renderSuggestionsOnly()
    }

    private fun candidatesFor(latin: String): List<String> {
        val learned = if (!prefs.incognito && !noLearning) {
            prefs.learnedWords()
                .mapNotNull { e ->
                    e.split("\u0000", limit = 2)
                        .takeIf { it.size == 2 && it[0] == latin.lowercase() }
                        ?.get(1)
                }
        } else emptyList()
        return (learned + BanglaPhonetic.candidates(latin)).distinct()
    }

    private fun commitTopCandidate(learn: Boolean) {
        if (composing.isEmpty()) return
        val raw = composing.toString()
        val top = if (prefs.banglaMode && !plainLatin) {
            candidatesFor(raw).firstOrNull() ?: raw
        } else {
            raw
        }
        currentInputConnection?.commitText(top, 1)
        if (learn && !prefs.incognito && !noLearning) {
            lastCommittedWord = top
        }
        composing.setLength(0)
        suggestions = emptyList()
        renderSuggestionsOnly()
    }

    private fun finishComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            composing.setLength(0)
            suggestions = emptyList()
        }
    }

    private fun pickSuggestion(index: Int) {
        val chosen = suggestions.getOrNull(index) ?: return
        val latin = composing.toString()
        val ic = currentInputConnection ?: return
        ic.commitText("$chosen ", 1)
        if (!prefs.incognito && !noLearning) {
            if (latin.isNotEmpty() && chosen != latin && index > 0) {
                prefs.addLearned("${latin.lowercase()}\u0000$chosen")
            }
            lastCommittedWord = chosen
        }
        composing.setLength(0)
        suggestions = emptyList()
        renderSuggestionsOnly()
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
            if (prefs.banglaMode && !plainLatin) {
                updateComposing()
            } else {
                updateEnglishComposing()
            }
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

    private fun toggleVoice() {
        if (voiceStatus != null) {
            voice.stop()
            voiceStatus = null
            voiceMeterText = null
            render()
            return
        }
        if (sensitive) {
            voiceStatus = null
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(
                Intent(this, MicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            voiceStatus = "Allow microphone, then tap 🎤 again"
            render()
            return
        }
        finishComposing()
        val langTag = if (prefs.banglaMode && !plainLatin) "bn-BD" else "en-US"
        voice.start(langTag)
    }

    private fun onVoiceState(state: VoiceInput.State, msg: String?) {
        voiceStatus = when (state) {
            VoiceInput.State.LISTENING -> "Listening…"
            VoiceInput.State.PROCESSING -> "Processing…"
            VoiceInput.State.DONE -> {
                voiceMeterText = null
                null
            }
            VoiceInput.State.ERROR -> {
                voiceMeterText = null
                msg ?: "Voice error"
            }
        }
        render()
        if (state == VoiceInput.State.ERROR) {
            handler.postDelayed({
                voiceStatus = null
                voiceMeterText = null
                render()
            }, 2500)
        }
    }

    private fun onVoiceText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun panelFrame(content: View): View {
        val h = KeyboardUi.dp(this, 46 * 4 + 12)
        return ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, h)
            addView(content)
        }
    }

    private fun buildPanel(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = KeyboardUi.dp(this, 6)
        col.setPadding(pad, pad, pad, pad)
        when (panel) {
            Panel.PHONE_PAD -> buildPhonePadPanel(col)
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

    private fun buildPhonePadPanel(col: LinearLayout) {
        val padRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )
        padRows.forEach { rKeys ->
            val row = KeyboardUi.row(this)
            rKeys.forEach { keyStr ->
                val display = if (prefs.bengaliDigits && keyStr.all { it in '0'..'9' }) {
                    BanglaPhonetic.toBengaliDigits(keyStr)
                } else keyStr
                row.addView(KeyboardUi.key(this, display, theme, 1f, heightDp = 42, sizeSp = 18f) {
                    buzz()
                    currentInputConnection?.commitText(display, 1)
                })
            }
            col.addView(row)
        }
        val ctl = KeyboardUi.row(this)
        ctl.addView(KeyboardUi.key(this, "␣", theme, 1.5f, alt = true, heightDp = 38) { onSpace() })
        ctl.addView(KeyboardUi.key(this, "⌫", theme, 1.2f, alt = true, heightDp = 38) { onBackspace() })
        ctl.addView(KeyboardUi.key(this, "⏎", theme, 1.3f, accent = true, heightDp = 38) { onEnter() })
        col.addView(ctl)
    }

    private fun buildClipboardPanel(col: LinearLayout) {
        col.addView(KeyboardUi.label(this, "Clipboard", theme, 13f))
    }

    private fun buildSnippetsPanel(col: LinearLayout) {
        col.addView(KeyboardUi.label(this, "Snippets", theme, 13f))
    }

    private fun buildAiPanel(col: LinearLayout) {
        col.addView(KeyboardUi.label(this, "AI Tools", theme, 13f))
    }

    private fun openAi() {
        togglePanel(Panel.AI)
    }
}
