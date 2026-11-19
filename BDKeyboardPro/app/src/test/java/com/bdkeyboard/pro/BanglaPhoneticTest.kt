package com.bdkeyboard.pro

import com.bdkeyboard.pro.engine.BanglaPhonetic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaPhoneticTest {

    @Test
    fun appendixAWords() {
        val cases = mapOf(
            "ami" to "আমি", "tumi" to "তুমি", "apni" to "আপনি", "bhalo" to "ভালো",
            "bangla" to "বাংলা", "dhonnobad" to "ধন্যবাদ", "amar" to "আমার", "onek" to "অনেক"
        )
        for ((latin, bn) in cases) {
            assertEquals(latin, bn, BanglaPhonetic.candidates(latin).first())
        }
    }

    @Test
    fun conjunctsAndVariants() {
        assertEquals("শিক্ষা", BanglaPhonetic.candidates("shikkha").first())
        assertEquals("অঙ্ক", BanglaPhonetic.candidates("onko").first())
        assertEquals("বাক্য", BanglaPhonetic.candidates("bakko").first())
        assertEquals("ওকে", BanglaPhonetic.candidates("ok").first())
        assertEquals("তুমি", BanglaPhonetic.candidates("tmi").first())
        assertEquals("যখন", BanglaPhonetic.candidates("jokh").first())
    }

    @Test
    fun lexiconHasSixThousandPlusEntries() {
        assertTrue("Expected 6000+ lexicon entries, got ${BanglaPhonetic.lexiconSize()}", BanglaPhonetic.lexiconSize() >= 6000)
    }

    @Test
    fun typoSuggestions() {
        val eng = BanglaPhonetic.englishSuggestions("tge")
        assertTrue("Expected 'the' in suggestions for 'tge', got $eng", eng.contains("the"))
    }

    @Test
    fun phrasesAndDigits() {
        assertEquals("কেমন আছো", BanglaPhonetic.transliteratePhrase("kemon acho"))
        assertEquals("আমি বাংলায় লিখি", BanglaPhonetic.transliteratePhrase("ami banglay likhi"))
        assertEquals("১২৩", BanglaPhonetic.toBengaliDigits("123"))
    }
}
