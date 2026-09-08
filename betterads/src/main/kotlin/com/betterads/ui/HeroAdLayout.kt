package com.betterads.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import com.betterads.R
import com.betterads.model.AdFormat
import com.betterads.model.AdModel

/** NativeOS image-only Design: hero fills the Template 1x frame; the image is the tap target. */
@Composable
fun HeroAdLayout(
    ad: AdModel,
    format: AdFormat,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = AdLayoutMetrics.templateSize(format)
    val disclosure = stringResource(R.string.better_ads_advertisement)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(frame.width, frame.height)
                .clip(RoundedCornerShape(AdLayoutMetrics.cornerRadius))
                .clickable(onClick = onCta),
        ) {
            AdRemoteImage(
                urls = ad.images.hero,
                size = DpSize(frame.width, frame.height),
                contentDescription = disclosure,
                contentScale = ContentScale.Crop,
            )
            AdAdvertisementLabel(
                style = AdLayoutMetrics.advertisementLabelStyle(format),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HeroAdLayoutBannerPreview() {
    HeroAdLayout(ad = AdModel.previewFixture(AdFormat.BANNER), format = AdFormat.BANNER, onCta = {})
}

@Preview(showBackground = true)
@Composable
private fun HeroAdLayoutCompactPreview() {
    HeroAdLayout(ad = AdModel.previewFixture(AdFormat.COMPACT), format = AdFormat.COMPACT, onCta = {})
}

@Preview(showBackground = true)
@Composable
private fun HeroAdLayoutCardPreview() {
    HeroAdLayout(ad = AdModel.previewFixture(AdFormat.CARD), format = AdFormat.CARD, onCta = {})
}
