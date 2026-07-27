package com.betterads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

/** Bookie-parity card placement. */
@Composable
fun CardAdLayout(
    ad: AdModel,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = AdFormatting.colorFromHex(
        ad.backgroundColor,
        Color.Gray.copy(alpha = 0.2f),
    )
    val textColor = AdFormatting.colorFromHex(ad.textColor, Color.Black)
    val buttonBackground = AdFormatting.colorFromHex(ad.cta.ctaButtonColor, Color.White)
    val buttonTitleColor = AdFormatting.colorFromHex(ad.cta.ctaTitleColor, Color.Black)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))
            .background(backgroundColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeadline(
                ad = ad,
                textColor = textColor,
                modifier = Modifier.padding(top = 32.dp),
            )
            AdRemoteImage(
                urls = ad.images.hero,
                size = DpSize(205.dp, 164.dp),
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = AdFormatting.annotatedDescription(
                        text = ad.description,
                        baseColor = textColor,
                        baseFontSize = AdTypography.serif22,
                        baseFontFamily = FontFamily.Serif,
                        baseFontWeight = FontWeight.Medium,
                        emphasisFontFamily = FontFamily.Serif,
                    ),
                    letterSpacing = (-0.44).sp,
                    lineHeight = 33.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 99.dp),
                )
                Button(
                    onClick = onCta,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBackground,
                        contentColor = buttonTitleColor,
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text(
                        text = ad.cta.title,
                        fontSize = AdTypography.bodyHeavy16,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        AdAdvertisementLabel(
            style = AdAdvertisementLabelStyle.Full,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CardHeadline(
    ad: AdModel,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val hasIcon = ad.images.icon.urlFor(2f) != null
    if (hasIcon) {
        AdRemoteImage(
            urls = ad.images.icon,
            size = DpSize(109.dp, 17.dp),
            contentDescription = ad.headline,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Text(
            text = ad.headline,
            color = textColor,
            fontSize = AdTypography.serif22,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CardAdLayoutPreview() {
    CardAdLayout(ad = AdModel.previewFixture(AdFormat.CARD), onCta = {})
}
