package com.betterads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.betterads.R

enum class AdAdvertisementLabelStyle {
    /** Compact / banner — short “Ad”. */
    Short,

    /** Card — full “Advertisement”. */
    Full,
}

/** Meta/Google-style frosted “Ad” / “Advertisement” chip. */
@Composable
fun AdAdvertisementLabel(
    style: AdAdvertisementLabelStyle = AdAdvertisementLabelStyle.Short,
    modifier: Modifier = Modifier,
) {
    val fullDisclosure = stringResource(R.string.better_ads_advertisement)
    val title = when (style) {
        AdAdvertisementLabelStyle.Short -> stringResource(R.string.better_ads_advertisement_short)
        AdAdvertisementLabelStyle.Full -> fullDisclosure
    }
    val horizontal = if (style == AdAdvertisementLabelStyle.Short) 6.dp else 8.dp

    Text(
        text = title,
        color = Color.Black.copy(alpha = 0.8f),
        fontSize = AdTypography.caption10,
        fontWeight = FontWeight.Normal,
        modifier = modifier
            .padding(
                top = AdLayoutMetrics.advertisementLabelInset,
                end = AdLayoutMetrics.advertisementLabelInset,
            )
            .background(
                color = Color.White.copy(alpha = AdLayoutMetrics.advertisementLabelBackgroundOpacity),
                shape = RoundedCornerShape(AdLayoutMetrics.advertisementLabelCornerRadius),
            )
            .padding(horizontal = horizontal, vertical = 3.dp)
            .semantics { contentDescription = fullDisclosure },
    )
}
