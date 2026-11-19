package com.bdkeyboard.pro.engine

/**
 * Banglish -> Bengali (Unicode) transliterator & smart suggestion engine.
 */
object BanglaPhonetic {

    private const val HASANTA = "্"

    private val baseDictionary: Map<String, List<String>> = mapOf(
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
        "korte" to listOf("করতে"),
        "likhi" to listOf("লিখি"),
        "ok" to listOf("ওকে"),
        "haa" to listOf("হ্যাঁ"),
        "na" to listOf("না"),
        "ha" to listOf("হ্যাঁ", "হা"),
        "keno" to listOf("কেন"),
        "kothay" to listOf("কোথায়"),
        "kobe" to listOf("কবে"),
        "kokhon" to listOf("কখন"),
        "jokh" to listOf("যখন"),
        "jokhon" to listOf("যখন"),
        "ekhon" to listOf("এখন"),
        "ekhane" to listOf("এখানে"),
        "okhane" to listOf("ওখানে"),
        "ei" to listOf("এই"),
        "oi" to listOf("ওই"),
        "ache" to listOf("আছে"),
        "achi" to listOf("আছি"),
        "achen" to listOf("আছেন"),
        "nai" to listOf("নাই"),
        "hobe" to listOf("হবে"),
        "hoy" to listOf("হয়"),
        "korbo" to listOf("করবো", "করব"),
        "jabo" to listOf("যাবো", "যাব"),
        "asbo" to listOf("আসবো", "আসব"),
        "ar" to listOf("আর"),
        "kintu" to listOf("কিন্তু"),
        "tai" to listOf("তাই"),
        "tobe" to listOf("তবে"),
        "ebong" to listOf("এবং"),
        "ba" to listOf("বা"),
        "jodi" to listOf("যদি"),
        "tahole" to listOf("তাহলে"),
        "amake" to listOf("আমাকে"),
        "tomake" to listOf("তোমাকে"),
        "tomar" to listOf("তোমার"),
        "apnar" to listOf("আপনার"),
        "tar" to listOf("তার"),
        "amra" to listOf("আমরা"),
        "tomra" to listOf("তোমরা"),
        "tara" to listOf("তারা"),
        "se" to listOf("সে"),
        "bhai" to listOf("ভাই"),
        "bon" to listOf("বোন"),
        "bondhu" to listOf("বন্ধু"),
        "ma" to listOf("মা"),
        "baba" to listOf("বাবা"),
        "bari" to listOf("বাড়ি"),
        "kaj" to listOf("কাজ"),
        "taka" to listOf("টাকা"),
        "khabar" to listOf("খাবার"),
        "pani" to listOf("পানি"),
        "bhat" to listOf("ভাত"),
        "din" to listOf("দিন"),
        "rat" to listOf("রাত"),
        "ratri" to listOf("রাত্রি"),
        "shubho" to listOf("শুভ"),
        "shubhokamona" to listOf("শুভকামনা"),
        "shagotom" to listOf("স্বাগতম"),
        "bhalobashi" to listOf("ভালোবাসি"),
        "valobashi" to listOf("ভালোবাসি"),
        "sundor" to listOf("সুন্দর"),
        "kharap" to listOf("খারাপ"),
        "thik" to listOf("ঠিক"),
        "dekho" to listOf("দেখো"),
        "bolo" to listOf("বলো"),
        "jani" to listOf("জানি"),
        "janina" to listOf("জানি না"),
        "kotha" to listOf("কথা"),
        "somoy" to listOf("সময়"),
        "sorry" to listOf("দুঃখিত"),
        "please" to listOf("অনুগ্রহ করে"),
        "bangladesh" to listOf("বাংলাদেশ"),
        "dhaka" to listOf("ঢাকা"),
        "ekta" to listOf("একটা"),
        "ekti" to listOf("একটি"),
        "duita" to listOf("দুইটা"),
        "ekdom" to listOf("একদম"),
        "khub" to listOf("খুব"),
        "sob" to listOf("সব"),
        "shob" to listOf("সব"),
        "kono" to listOf("কোনো"),
        "kichu" to listOf("কিছু"),
        "kichhu" to listOf("কিছু"),
        "bhul" to listOf("ভুল"),
        "porikkha" to listOf("পরীক্ষা"),
        "school" to listOf("স্কুল"),
        "achho" to listOf("আছো"),
        "bhalo achi" to listOf("ভালো আছি"),
        "tomar nam" to listOf("তোমার নাম"),
        "shikkha" to listOf("শিক্ষা"),
        "shiksha" to listOf("শিক্ষা"),
        "onko" to listOf("অঙ্ক"),
        "anko" to listOf("অঙ্ক"),
        "bakko" to listOf("বাক্য"),
        "bakyo" to listOf("বাক্য")
    )

    private val commonEnglishWords: List<String> = listOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
        "her", "was", "one", "our", "out", "day", "get", "has", "him", "his",
        "how", "man", "new", "now", "old", "see", "two", "way", "who", "boy",
        "did", "its", "let", "put", "say", "she", "too", "use", "that", "with",
        "have", "this", "will", "your", "from", "they", "know", "want", "been",
        "good", "much", "some", "time", "very", "when", "come", "here", "just",
        "like", "long", "make", "many", "more", "only", "over", "such", "take",
        "than", "them", "well", "were", "what", "hello", "please", "thank", "thanks"
    )

    private val qwertyNeighbors: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qeas", 'e' to "wrds", 'r' to "etdf", 't' to "ryfg",
        'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
        'a' to "qwsz", 's' to "wedxza", 'd' to "erfcxs", 'f' to "rtgvcd", 'g' to "tyhbvf",
        'h' to "yujnbg", 'j' to "uikmnh", 'k' to "iolmj", 'l' to "opk",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
        'n' to "bhjm", 'm' to "njk"
    )

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
        "oi" to "ঐ", "ou" to "ঔ", "aa" to "আ", "ee" to "ঈ", "ii" to "ঈ", "uu" to "ঊ",
        "a" to "অ", "i" to "ই", "u" to "উ", "e" to "এ", "o" to "ও", "I" to "ঈ", "U" to "ঊ",
        "A" to "আ", "E" to "ঐ", "O" to "ঔ", "rri" to "ঋ"
    )

    private val vowelSigns: Map<String, String> = mapOf(
        "oi" to "ৈ", "ou" to "ৌ", "aa" to "া", "ee" to "ী", "ii" to "ী", "uu" to "ূ",
        "a" to "া", "i" to "ি", "u" to "ু", "e" to "ে", "o" to "",
        "I" to "ী", "U" to "ূ", "A" to "া", "E" to "ৈ", "O" to "ৌ", "rri" to "ৃ"
    )

    private val consonantKeys = consonants.keys.sortedByDescending { it.length }
    private val vowelKeys = vowelsIndependent.keys.sortedByDescending { it.length }

    private val digitsBn = "০১২৩৪৫৬৭৮৯"

    private val expandedLexicon: Map<String, List<String>> by lazy {
        val map = LinkedHashMap<String, MutableList<String>>()
        fun putWord(k: String, bn: String) {
            val list = map.getOrPut(k.lowercase()) { mutableListOf() }
            if (!list.contains(bn)) list.add(bn)
        }
        baseDictionary.forEach { (k, vals) -> vals.forEach { v -> putWord(k, v) } }

        val stems = listOf(
            "kor" to "কর", "bol" to "বল", "dek" to "দেখ", "ja" to "যা", "as" to "আস",
            "kha" to "খা", "likh" to "লিখ", "por" to "পড়", "ni" to "নি", "di" to "দি",
            "rak" to "রাখ", "dhor" to "ধর", "chol" to "চল", "bos" to "বস", "uth" to "উঠ",
            "ghum" to "ঘুম", "khel" to "খেল", "ga" to "গা", "has" to "হাস", "kad" to "কাঁদ",
            "jan" to "জান", "man" to "মান", "bujh" to "বুঝ", "shun" to "শুন", "bhab" to "ভাব"
        )
        val endings = listOf(
            "i" to "ি", "o" to "ো", "e" to "ে", "a" to "া", "en" to "েন",
            "bo" to "বো", "be" to "বে", "lam" to "লাম", "cho" to "চ্ছো", "chi" to "চ্ছি",
            "te" to "তে", "le" to "লে", "ban" to "বান", "ano" to "ানো"
        )
        val prefixes = listOf(
            "" to "", "na" to "না ", "o" to "ও", "e" to "এ", "ba" to "বা",
            "pro" to "প্র", "bi" to "বি", "su" to "সু", "ku" to "কু", "nir" to "নির্"
        )
        for ((pk, pb) in prefixes) {
            for ((sk, sb) in stems) {
                for ((ek, eb) in endings) {
                    val latin = "$pk$sk$ek"
                    val bn = "$pb$sb$eb"
                    putWord(latin, bn)
                    if (latin.contains("sh")) putWord(latin.replace("sh", "s"), bn)
                    if (latin.contains("o")) putWord(latin.replaceFirst("o", "a"), bn)
                }
            }
        }
        map
    }

    fun lexiconSize(): Int = expandedLexicon.size

    fun toBengaliDigits(s: String): String =
        s.map { if (it in '0'..'9') digitsBn[it - '0'] else it }.joinToString("")

    fun candidates(input: String, max: Int = 5): List<String> {
        if (input.isBlank()) return emptyList()
        val lower = input.lowercase()
        val out = LinkedHashSet<String>()

        expandedLexicon[lower]?.let { out.addAll(it) }
        out.add(transliterate(input))
        if (lower != input) out.add(transliterate(lower))

        if (out.size < max && lower.length >= 2) {
            expandedLexicon.entries
                .asSequence()
                .filter { it.key.startsWith(lower) }
                .take(max * 2)
                .forEach { entry -> out.addAll(entry.value) }
        }

        if (out.size < max && lower.length >= 2) {
            for (variant in typoVariants(lower).take(8)) {
                expandedLexicon[variant]?.let { out.addAll(it) }
            }
        }

        return out.filter { it.isNotEmpty() }.take(max)
    }

    fun englishSuggestions(input: String, max: Int = 5): List<String> {
        if (input.isBlank()) return emptyList()
        val lower = input.lowercase()
        val out = LinkedHashSet<String>()
        if (lower in commonEnglishWords) out.add(lower)
        commonEnglishWords.filter { it.startsWith(lower) && it != lower }.take(3).forEach { out.add(it) }
        val variants = typoVariants(lower).toSet()
        commonEnglishWords.filter { it in variants }.take(3).forEach { out.add(it) }
        return out.take(max)
    }

    fun typoVariants(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val res = LinkedHashSet<String>()
        for (i in word.indices) {
            val ch = word[i]
            val neighbors = qwertyNeighbors[ch].orEmpty()
            for (nb in neighbors) {
                res.add(word.substring(0, i) + nb + word.substring(i + 1))
            }
        }
        return res.toList()
    }

    fun transliteratePhrase(text: String): String =
        text.split(" ").joinToString(" ") { w ->
            expandedLexicon[w.lowercase()]?.firstOrNull() ?: transliterate(w)
        }

    fun transliterate(input: String): String {
        val normalized = applyEasyConjunctRules(input)
        val sb = StringBuilder()
        var i = 0
        var prevWasConsonant = false

        while (i < normalized.length) {
            val rest = normalized.substring(i)

            val vKey = vowelKeys.firstOrNull { rest.startsWith(it) }
            val cKey = consonantKeys.firstOrNull { rest.startsWith(it) }

            if (vKey != null && (cKey == null || vKey.length > cKey.length || (cKey.length == 1 && isVowelChar(rest[0])))) {
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
                    sb.append(glyph)
                    prevWasConsonant = false
                } else {
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
                ch in '0'..'9' -> sb.append(ch)
                else -> sb.append(ch)
            }
            prevWasConsonant = false
            i++
        }
        return sb.toString()
    }

    private fun applyEasyConjunctRules(raw: String): String {
        var s = raw
        s = s.replace("nko", "Nko")
        s = s.replace("kko", "kYo")
        s = s.replace("ee", "ii")
        return s
    }

    private fun isVowelChar(c: Char) = c.lowercaseChar() in "aeiou"
}
