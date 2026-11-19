package com.bdkeyboard.pro.data

import android.content.Context
import android.content.SharedPreferences

/** Simple local settings store. Synchronous reads keep the IME responsive. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("bd_keyboard_prefs", Context.MODE_PRIVATE)

    var banglaMode: Boolean
        get() = sp.getBoolean("bangla_mode", true)
        set(v) = sp.edit().putBoolean("bangla_mode", v).apply()

    /** 0 = Phonetic, 1 = Jatiyo (জাতীয়), 2 = Bornomala (বর্ণমালা), 3 = Classic (ক্লাসিক) */
    var banglaLayoutMode: Int
        get() = sp.getInt("bangla_layout_mode", 0)
        set(v) = sp.edit().putInt("bangla_layout_mode", v.coerceIn(0, 3)).apply()

    var phonePad: Boolean
        get() = sp.getBoolean("phone_pad", false)
        set(v) = sp.edit().putBoolean("phone_pad", v).apply()

    var numberRow: Boolean
        get() = sp.getBoolean("number_row", true)
        set(v) = sp.edit().putBoolean("number_row", v).apply()

    var bengaliDigits: Boolean
        get() = sp.getBoolean("bengali_digits", false)
        set(v) = sp.edit().putBoolean("bengali_digits", v).apply()

    /** 0 = system, 1 = light, 2 = dark */
    var theme: Int
        get() = sp.getInt("theme", 2)
        set(v) = sp.edit().putInt("theme", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", false)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    var incognito: Boolean
        get() = sp.getBoolean("incognito", false)
        set(v) = sp.edit().putBoolean("incognito", v).apply()

    var aiEnabled: Boolean
        get() = sp.getBoolean("ai_enabled", true)
        set(v) = sp.edit().putBoolean("ai_enabled", v).apply()

    var aiConsentGiven: Boolean
        get() = sp.getBoolean("ai_consent", false)
        set(v) = sp.edit().putBoolean("ai_consent", v).apply()

    /** 0 = normal, 1 = left, 2 = right */
    var oneHand: Int
        get() = sp.getInt("one_hand", 0)
        set(v) = sp.edit().putInt("one_hand", v).apply()

    var clipboardEnabled: Boolean
        get() = sp.getBoolean("clipboard_enabled", false)
        set(v) = sp.edit().putBoolean("clipboard_enabled", v).apply()

    // ---- personal dictionary (local, user-editable) ----
    fun learnedWords(): Set<String> = sp.getStringSet("learned", emptySet()) ?: emptySet()
    fun addLearned(word: String) {
        sp.edit().putStringSet("learned", learnedWords() + word).apply()
    }
    fun removeLearned(word: String) {
        sp.edit().putStringSet("learned", learnedWords() - word).apply()
    }
    fun clearLearned() = sp.edit().remove("learned").apply()

    // ---- next-word bigrams: prevWord -> nextWord ----
    fun bigrams(): Map<String, String> =
        (sp.getStringSet("bigrams", emptySet()) ?: emptySet())
            .mapNotNull { e -> e.split("\u0000", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    fun recordBigram(prevWord: String, nextWord: String) {
        val p = prevWord.trim()
        val n = nextWord.trim()
        if (p.isEmpty() || n.isEmpty() || p == n) return
        val cur = bigrams().toMutableMap().apply { put(p, n) }
        val trimmed = cur.entries.toList().takeLast(200)
        sp.edit().putStringSet("bigrams", trimmed.map { "${it.key}\u0000${it.value}" }.toSet()).apply()
    }

    fun predictNextWord(prevWord: String): String? = bigrams()[prevWord.trim()]

    // ---- snippets: trigger -> replacement ----
    fun snippets(): Map<String, String> =
        (sp.getStringSet("snippets", emptySet()) ?: emptySet())
            .mapNotNull { e -> e.split("\u0000", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
    fun putSnippet(trigger: String, text: String) {
        val cur = snippets().toMutableMap().apply { put(trigger, text) }
        sp.edit().putStringSet("snippets", cur.map { "${it.key}\u0000${it.value}" }.toSet()).apply()
    }
    fun removeSnippet(trigger: String) {
        val cur = snippets().toMutableMap().apply { remove(trigger) }
        sp.edit().putStringSet("snippets", cur.map { "${it.key}\u0000${it.value}" }.toSet()).apply()
    }

    // ---- clipboard history (opt-in, local, with pinning) ----
    fun pinnedClips(): Set<String> = sp.getStringSet("pinned_clips", emptySet()) ?: emptySet()

    fun isPinned(text: String): Boolean = pinnedClips().contains(text)

    fun togglePinClip(text: String) {
        val cur = pinnedClips().toMutableSet()
        if (cur.contains(text)) cur.remove(text) else cur.add(text)
        sp.edit().putStringSet("pinned_clips", cur).apply()
    }

    fun clipItems(): List<String> {
        val raw = (sp.getString("clips", "") ?: "").split("\u0001").filter { it.isNotEmpty() }
        val pinned = pinnedClips().filter { it.isNotEmpty() }
        return (pinned + raw).distinct()
    }

    fun addClip(text: String) {
        val pinned = pinnedClips()
        val unpinned = (listOf(text) + clipItems().filter { it != text && !pinned.contains(it) }).take(20)
        val combined = (pinned.toList() + unpinned).distinct()
        sp.edit().putString("clips", combined.joinToString("\u0001")).apply()
    }

    fun removeClip(text: String) {
        if (pinnedClips().contains(text)) {
            sp.edit().putStringSet("pinned_clips", pinnedClips() - text).apply()
        }
        sp.edit().putString("clips", clipItems().filter { it != text }.joinToString("\u0001")).apply()
    }

    fun clearClips() {
        val pinned = pinnedClips()
        if (pinned.isEmpty()) {
            sp.edit().remove("clips").apply()
        } else {
            sp.edit().putString("clips", pinned.joinToString("\u0001")).apply()
        }
    }

    fun clearAllClipsIncludingPinned() {
        sp.edit().remove("clips").remove("pinned_clips").apply()
    }
}
