package com.betterads.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Color + copy helpers — mirrors iOS `AdFormatting` / Bookie `PlacementAdFormatting`. */
object AdFormatting {
    fun colorFromHex(hex: String, fallback: Color): Color =
        parseHex(hex) ?: fallback

    /**
     * Supports `*emphasis*` markers in ad description copy (Bookie parity).
     */
    fun annotatedDescription(
        text: String,
        baseColor: Color,
        baseFontSize: TextUnit,
        baseFontWeight: FontWeight = FontWeight.Normal,
        baseFontFamily: FontFamily = FontFamily.Default,
        emphasisFontFamily: FontFamily = FontFamily.Serif,
        emphasisFontStyle: FontStyle = FontStyle.Italic,
        emphasisFontWeight: FontWeight = FontWeight.Medium,
    ): AnnotatedString = buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            if (text[index] == '*') {
                val afterOpen = index + 1
                val closeIndex = text.indexOf('*', startIndex = afterOpen)
                if (closeIndex >= 0) {
                    val emphasisText = text.substring(afterOpen, closeIndex)
                    pushStyle(
                        SpanStyle(
                            color = baseColor,
                            fontSize = baseFontSize,
                            fontWeight = emphasisFontWeight,
                            fontFamily = emphasisFontFamily,
                            fontStyle = emphasisFontStyle,
                        ),
                    )
                    append(emphasisText)
                    pop()
                    index = closeIndex + 1
                    continue
                }
            }
            val nextMarker = text.indexOf('*', startIndex = index).let { if (it < 0) text.length else it }
            val plain = text.substring(index, nextMarker)
            if (plain.isNotEmpty()) {
                pushStyle(
                    SpanStyle(
                        color = baseColor,
                        fontSize = baseFontSize,
                        fontWeight = baseFontWeight,
                        fontFamily = baseFontFamily,
                    ),
                )
                append(plain)
                pop()
            }
            index = nextMarker
        }
    }

    private fun parseHex(hex: String): Color? {
        val sanitized = hex.trim().removePrefix("#")
        if (sanitized.length != 6) return null
        val value = sanitized.toLongOrNull(radix = 16) ?: return null
        val red = ((value and 0xFF0000) shr 16) / 255f
        val green = ((value and 0x00FF00) shr 8) / 255f
        val blue = (value and 0x0000FF) / 255f
        return Color(red, green, blue)
    }
}

object AdLayoutMetrics {
    val advertisementLabelInset: Dp = 8.dp
    val advertisementLabelCornerRadius: Dp = 4.dp
    const val advertisementLabelBackgroundOpacity = 0.72f
    val cornerRadius: Dp = 12.dp
    val bannerHeight: Dp = 164.dp
}

object AdTypography {
    val caption10 = 10.sp
    val bodyHeavy13 = 13.sp
    val bodyHeavy16 = 16.sp
    val serif14 = 14.sp
    val serif17 = 17.sp
    val serif22 = 22.sp
    val caption12 = 12.sp
}
