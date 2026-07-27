package com.betterads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.betterads.model.AdModel

/** Bookie-parity compact placement. */
@Composable
fun CompactAdLayout(
    ad: AdModel,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = AdFormatting.colorFromHex(
        ad.backgroundColor,
        Color.Gray.copy(alpha = 0.2f),
    )
    val textColor = AdFormatting.colorFromHex(
        ad.textColor,
        Color.Black.copy(alpha = 0.8f),
    )
    val buttonBackground = AdFormatting.colorFromHex(ad.cta.ctaButtonColor, Color.White)
    val buttonTitleColor = AdFormatting.colorFromHex(ad.cta.ctaTitleColor, Color.Black)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))
            .background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdRemoteImage(
                urls = ad.images.hero,
                size = DpSize(50.dp, 53.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactHeadline(ad = ad, textColor = textColor)
                Text(
                    text = AdFormatting.annotatedDescription(
                        text = ad.description,
                        baseColor = textColor.copy(alpha = 0.8f),
                        baseFontSize = AdTypography.caption12,
                        emphasisFontFamily = FontFamily.Serif,
                    ),
                )
            }

            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBackground,
                    contentColor = buttonTitleColor,
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .widthIn(min = 102.dp)
                    .height(31.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                Text(
                    text = ad.cta.title,
                    fontSize = AdTypography.bodyHeavy13,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        AdAdvertisementLabel(
            style = AdAdvertisementLabelStyle.Short,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CompactHeadline(ad: AdModel, textColor: Color) {
    val hasIcon = ad.images.icon.urlFor(2f) != null
    if (hasIcon) {
        AdRemoteImage(
            urls = ad.images.icon,
            size = DpSize(84.dp, 13.dp),
            contentDescription = ad.headline,
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = ad.headline,
            color = textColor,
            fontSize = AdTypography.serif17,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompactAdLayoutPreview() {
    CompactAdLayout(ad = AdModel.previewFixture(), onCta = {})
}
