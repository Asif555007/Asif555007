package com.bdkeyboard.pro.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

data class KbTheme(
    val bg: Int, val key: Int, val keyAlt: Int, val text: Int, val accent: Int, val strip: Int
) {
    companion object {
        val DARK = KbTheme(
            Color.parseColor("#0B1F18"), Color.parseColor("#17382D"), Color.parseColor("#102A21"),
            Color.parseColor("#EAF7F1"), Color.parseColor("#1FA67A"), Color.parseColor("#0E2A21")
        )
        val LIGHT = KbTheme(
            Color.parseColor("#E8EEEB"), Color.WHITE, Color.parseColor("#CBD6D1"),
            Color.parseColor("#12201B"), Color.parseColor("#12805F"), Color.parseColor("#DCE5E1")
        )
    }
}

object KeyboardUi {

    fun dp(c: Context, v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics).toInt()

    fun bg(color: Int, radiusDp: Int, c: Context) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, radiusDp).toFloat()
    }

    /** A tappable key/button. weight lets rows distribute width. */
    fun key(
        c: Context, label: String, t: KbTheme, weight: Float = 1f, heightDp: Int = 46,
        alt: Boolean = false, accent: Boolean = false, sizeSp: Float = 17f,
        desc: String? = null, onClick: (View) -> Unit
    ): TextView = TextView(c).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(if (accent) Color.WHITE else t.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = Typeface.DEFAULT
        background = bg(if (accent) t.accent else if (alt) t.keyAlt else t.key, 7, c)
        contentDescription = desc ?: label
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(0, dp(c, heightDp), weight).apply {
            setMargins(dp(c, 2), dp(c, 3), dp(c, 2), dp(c, 3))
        }
        setOnClickListener(onClick)
    }

    fun row(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    fun label(c: Context, text: String, t: KbTheme, sizeSp: Float = 13f): TextView = TextView(c).apply {
        this.text = text
        setTextColor(t.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4))
    }
}
