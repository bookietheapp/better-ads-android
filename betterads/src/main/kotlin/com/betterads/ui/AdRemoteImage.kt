package com.betterads.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.betterads.model.AdImageUrls

@Composable
fun AdRemoteImage(
    urls: AdImageUrls,
    size: DpSize,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current.density
    val url = urls.urlFor(density)
    if (url.isNullOrBlank()) {
        Box(modifier = modifier.size(size)) { placeholder() }
        return
    }

    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.size(size),
    )
}
