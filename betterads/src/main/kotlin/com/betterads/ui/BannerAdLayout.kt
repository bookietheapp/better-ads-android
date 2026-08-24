package com.betterads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.zIndex
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

private val bannerHeroWidth = 205.dp
private val bannerHorizontalPadding = 16.dp
/** Headline and body copy stay in the left half; the CTA may extend over the hero. */
private const val bannerTextColumnWidthFraction = 0.5f

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
        val textMaxWidth = maxWidth * bannerTextColumnWidthFraction - bannerHorizontalPadding
        val ctaMaxWidth = maxWidth - bannerHorizontalPadding * 2

        AdRemoteImage(
            urls = ad.images.hero,
            size = DpSize(bannerHeroWidth, AdLayoutMetrics.bannerHeight),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(bannerHeroWidth)
                .height(AdLayoutMetrics.bannerHeight),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .widthIn(max = ctaMaxWidth)
                .padding(horizontal = bannerHorizontalPadding)
                .padding(top = 24.dp, bottom = 16.dp)
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(max = textMaxWidth),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
            }
            BannerCtaButton(
                title = ad.cta.title,
                buttonBackground = buttonBackground,
                buttonTitleColor = buttonTitleColor,
                onClick = onCta,
                modifier = Modifier.widthIn(max = ctaMaxWidth),
            )
        }

        AdAdvertisementLabel(
            style = AdAdvertisementLabelStyle.Short,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2f),
        )
    }
}

@Composable
private fun BannerCtaButton(
    title: String,
    buttonBackground: Color,
    buttonTitleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(31.dp)
            .defaultMinSize(minWidth = 102.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(buttonBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = AdTypography.bodyHeavy13,
            fontWeight = FontWeight.SemiBold,
            color = buttonTitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun BannerHeadline(ad: AdModel, textColor: Color) {
    val headline = ad.headline.trim()
    val hasIcon = ad.images.icon.urlFor(2f) != null
    if (hasIcon) {
        // Keep a blank wordmark slot while loading / if the asset fails (e.g. SVG).
        AdRemoteImage(
            urls = ad.images.icon,
            size = DpSize(109.dp, 17.dp),
            contentDescription = headline,
            contentScale = ContentScale.Fit,
            placeholder = {},
        )
    } else if (headline.isNotEmpty()) {
        Text(
            text = headline,
            color = textColor,
            fontSize = AdTypography.serif14,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Box(modifier = Modifier.height(17.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun BannerAdLayoutPreview() {
    BannerAdLayout(ad = AdModel.previewFixture(AdFormat.BANNER), onCta = {})
}
