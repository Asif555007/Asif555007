package com.bdkeyboard.pro.engine

/**
 * Rule-based Banglish -> Bengali (Unicode) transliterator.
 *
 * Two layers:
 *  1. A small exact-match dictionary of common words (highest rank).
 *  2. A greedy longest-match rule engine producing consonants, vowel signs,
 *     hasanta conjuncts and independent vowels.
 *
 * Pure Kotlin, no Android dependencies, so it is unit-testable on the JVM.
 */
object BanglaPhonetic {

    private const val HASANTA = "্"

    /** Common words / spelling variants. Extend from a reviewed corpus (see spec Appendix A). */
    private val dictionary: Map<String, List<String>> = mapOf(
        "ami" to listOf("আমি"),
        "tumi" to listOf("তুমি"),
        "tmi" to listOf("তুমি"),
        "apni" to listOf("আপনি"),
        "bhalo" to listOf("ভালো", "ভাল"),
        "valo" to listOf("ভালো", "ভাল"),
        "bangla" to listOf("বাংলা"),
        "banglay" to listOf("বাংলায়"),
        "kemon" to listOf("কেমন"),
        "acho" to listOf("আছো", "আছ"),
        "dhonnobad" to listOf("ধন্যবাদ"),
        "shuvo" to listOf("শুভ"),
        "shokal" to listOf("সকাল"),
        "amar" to listOf("আমার"),
        "nam" to listOf("নাম"),
        "onek" to listOf("অনেক"),
        "ki" to listOf("কি", "কী"),
        "korcho" to listOf("করছো", "করছ"),
        "likhi" to listOf("লিখি"),
        "ok" to listOf("ওকে"),
        "haa" to listOf("হ্যাঁ"),
        "na" to listOf("না"),
        "ha" to listOf("হ্যাঁ", "হা"),
        "keno" to listOf("কেন"), "kothay" to listOf("কোথায়"), "kobe" to listOf("কবে"),
        "kokhon" to listOf("কখন"), "ekhon" to listOf("এখন"), "ekhane" to listOf("এখানে"),
        "okhane" to listOf("ওখানে"), "ei" to listOf("এই"), "oi" to listOf("ওই"),
        "ache" to listOf("আছে"), "achi" to listOf("আছি"), "achen" to listOf("আছেন"),
        "nai" to listOf("নাই"), "hobe" to listOf("হবে"), "hoy" to listOf("হয়"),
        "korbo" to listOf("করবো", "করব"), "jabo" to listOf("যাবো", "যাব"), "asbo" to listOf("আসবো", "আসব"),
        "ar" to listOf("আর"), "kintu" to listOf("কিন্তু"), "tai" to listOf("তাই"), "tobe" to listOf("তবে"),
        "ebong" to listOf("এবং"), "ba" to listOf("বা"), "jodi" to listOf("যদি"), "tahole" to listOf("তাহলে"),
        "amake" to listOf("আমাকে"), "tomake" to listOf("তোমাকে"), "tomar" to listOf("তোমার"),
        "apnar" to listOf("আপনার"), "tar" to listOf("তার"), "amra" to listOf("আমরা"),
        "tomra" to listOf("তোমরা"), "tara" to listOf("তারা"), "se" to listOf("সে"),
        "bhai" to listOf("ভাই"), "bon" to listOf("বোন"), "bondhu" to listOf("বন্ধু"),
        "ma" to listOf("মা"), "baba" to listOf("বাবা"), "bari" to listOf("বাড়ি"),
        "kaj" to listOf("কাজ"), "taka" to listOf("টাকা"), "khabar" to listOf("খাবার"),
        "pani" to listOf("পানি"), "bhat" to listOf("ভাত"), "din" to listOf("দিন"),
        "rat" to listOf("রাত"), "ratri" to listOf("রাত্রি"), "shubho" to listOf("শুভ"),
        "shubhokamona" to listOf("শুভকামনা"), "shagotom" to listOf("স্বাগতম"),
        "bhalobashi" to listOf("ভালোবাসি"), "valobashi" to listOf("ভালোবাসি"),
        "sundor" to listOf("সুন্দর"), "kharap" to listOf("খারাপ"), "thik" to listOf("ঠিক"),
        "dekho" to listOf("দেখো"), "bolo" to listOf("বলো"), "jani" to listOf("জানি"),
        "janina" to listOf("জানি না"), "kotha" to listOf("কথা"), "somoy" to listOf("সময়"),
        "dhonnobad" to listOf("ধন্যবাদ"), "sorry" to listOf("দুঃখিত"), "please" to listOf("অনুগ্রহ করে"),
        "bangladesh" to listOf("বাংলাদেশ"), "dhaka" to listOf("ঢাকা"), "ekta" to listOf("একটা"),
        "ekti" to listOf("একটি"), "duita" to listOf("দুইটা"), "ekdom" to listOf("একদম"),
        "khub" to listOf("খুব"), "sob" to listOf("সব"), "shob" to listOf("সব"), "kono" to listOf("কোনো"),
        "kichu" to listOf("কিছু"), "kichhu" to listOf("কিছু"), "bhul" to listOf("ভুল"),
        "porikkha" to listOf("পরীক্ষা"), "school" to listOf("স্কুল"), "kemon" to listOf("কেমন"),
        "achho" to listOf("আছো"), "bhalo achi" to listOf("ভালো আছি"), "tumi" to listOf("তুমি"),
        "nam" to listOf("নাম"), "tomar nam" to listOf("তোমার নাম")
    )

    // Order matters only through longest-match; tables are checked longest key first.
    private val consonants: Map<String, String> = mapOf(
        "kh" to "খ", "gh" to "ঘ", "ng" to "ং", "chh" to "ছ", "ch" to "চ", "jh" to "ঝ",
        "Th" to "ঠ", "Dh" to "ঢ", "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ",
        "sh" to "শ", "Sh" to "ষ", "rr" to "ড়", "Rh" to "ঢ়",
        "k" to "ক", "g" to "গ", "c" to "চ", "j" to "জ", "T" to "ট", "D" to "ড",
        "N" to "ণ", "t" to "ত", "d" to "দ", "n" to "ন", "p" to "প", "f" to "ফ",
        "b" to "ব", "v" to "ভ", "m" to "ম", "z" to "য", "r" to "র", "l" to "ল",
        "s" to "স", "h" to "হ", "y" to "য়", "R" to "ড়", "w" to "ও"
    )

    private val vowelsIndependent: Map<String, String> = mapOf(
        "oi" to "ঐ", "ou" to "ঔ", "aa" to "আ", "ii" to "ঈ", "uu" to "ঊ",
        "a" to "অ", "i" to "ই", "u" to "উ", "e" to "এ", "o" to "ও", "I" to "ঈ", "U" to "ঊ",
        "A" to "আ", "E" to "ঐ", "O" to "ঔ", "rri" to "ঋ"
    )

    private val vowelSigns: Map<String, String> = mapOf(
        "oi" to "ৈ", "ou" to "ৌ", "aa" to "া", "ii" to "ী", "uu" to "ূ",
        "a" to "া", "i" to "ি", "u" to "ু", "e" to "ে", "o" to "", // inherent 'o'
        "I" to "ী", "U" to "ূ", "A" to "া", "E" to "ৈ", "O" to "ৌ", "rri" to "ৃ"
    )

    private val consonantKeys = consonants.keys.sortedByDescending { it.length }
    private val vowelKeys = vowelsIndependent.keys.sortedByDescending { it.length }

    private val digitsBn = "০১২৩৪৫৬৭৮৯"

    fun toBengaliDigits(s: String): String =
        s.map { if (it in '0'..'9') digitsBn[it - '0'] else it }.joinToString("")

    /** Ranked candidates for a Latin composing string. First item is the best guess. */
    fun candidates(input: String, max: Int = 5): List<String> {
        if (input.isBlank()) return emptyList()
        val lower = input.lowercase()
        val out = LinkedHashSet<String>()
        dictionary[lower]?.let { out.addAll(it) }
        out.add(transliterate(input))
        if (lower != input) out.add(transliterate(lower))
        return out.filter { it.isNotEmpty() }.take(max)
    }

    /** Transliterate a full phrase, word by word, preferring dictionary entries. */
    fun transliteratePhrase(text: String): String =
        text.split(" ").joinToString(" ") { w ->
            dictionary[w.lowercase()]?.firstOrNull() ?: transliterate(w)
        }

    fun transliterate(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var prevWasConsonant = false // true when last emitted glyph is a consonant needing a sign/hasanta decision

        while (i < input.length) {
            val rest = input.substring(i)

            val vKey = vowelKeys.firstOrNull { rest.startsWith(it) }
            val cKey = consonantKeys.firstOrNull { rest.startsWith(it) }

            // Prefer vowel match when it is longer or no consonant matches.
            if (vKey != null && (cKey == null || vKey.length > cKey.length || cKey.length == 1 && isVowelChar(rest[0]))) {
                if (prevWasConsonant) {
                    sb.append(vowelSigns[vKey] ?: "")
                } else {
                    sb.append(vowelsIndependent[vKey])
                }
                prevWasConsonant = false
                i += vKey.length
                continue
            }

            if (cKey != null) {
                val glyph = consonants[cKey]!!
                if (glyph == "ং") {
                    sb.append(glyph); prevWasConsonant = false
                } else {
                    // Consonant followed directly by consonant => conjunct via hasanta.
                    if (prevWasConsonant) sb.append(HASANTA)
                    sb.append(glyph)
                    prevWasConsonant = true
                }
                i += cKey.length
                continue
            }

            val ch = rest[0]
            when {
                ch == '.' -> sb.append("।")
                ch in '0'..'9' -> sb.append(ch) // digit style handled by caller
                else -> sb.append(ch)
            }
            prevWasConsonant = false
            i++
        }
        return sb.toString()
    }

    private fun isVowelChar(c: Char) = c.lowercaseChar() in "aeiou"
}
