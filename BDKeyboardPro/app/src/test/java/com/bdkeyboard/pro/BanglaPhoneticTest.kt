package com.bdkeyboard.pro

import com.bdkeyboard.pro.engine.BanglaPhonetic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaPhoneticTest {

    /** Appendix A starter set: dictionary-backed words must be the top candidate. */
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
    fun phrases() {
        assertEquals("কেমন আছো", BanglaPhonetic.transliteratePhrase("kemon acho"))
        assertEquals("আমি বাংলায় লিখি", BanglaPhonetic.transliteratePhrase("ami banglay likhi"))
    }

    @Test
    fun ruleEngineProducesBengaliForUnknownWords() {
        val r = BanglaPhonetic.transliterate("kolom")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.code in 0x0980..0x09FF })
    }

    @Test
    fun digits() {
        assertEquals("১২৩", BanglaPhonetic.toBengaliDigits("123"))
    }
}
