package com.betterads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

/** Bookie-parity banner placement. */
@Composable
fun BannerAdLayout(
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(AdLayoutMetrics.bannerHeight)
            .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))
            .background(backgroundColor),
    ) {
        AdRemoteImage(
            urls = ad.images.hero,
            size = DpSize(205.dp, 164.dp),
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(maxWidth * 0.5f)
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            BannerHeadline(ad = ad, textColor = textColor)
            Text(
                text = AdFormatting.annotatedDescription(
                    text = ad.description,
                    baseColor = textColor,
                    baseFontSize = AdTypography.serif14,
                    baseFontFamily = FontFamily.Serif,
                    baseFontWeight = FontWeight.Medium,
                    emphasisFontFamily = FontFamily.Serif,
                ),
                letterSpacing = (-0.28).sp,
                lineHeight = 20.sp,
            )
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
private fun BannerHeadline(ad: AdModel, textColor: Color) {
    val hasIcon = ad.images.icon.urlFor(2f) != null
    if (hasIcon) {
        AdRemoteImage(
            urls = ad.images.icon,
            size = DpSize(109.dp, 17.dp),
            contentDescription = ad.headline,
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            text = ad.headline,
            color = textColor,
            fontSize = AdTypography.serif14,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerAdLayoutPreview() {
    BannerAdLayout(ad = AdModel.previewFixture(AdFormat.BANNER), onCta = {})
}
