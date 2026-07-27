# Better Ads (Android)

Standalone Android library (`com.betterads`) that returns **ready-to-display Compose ad views**, **loads creatives itself**, opens CTAs, and (when remote) reports impressions / clicks.

Matches the iOS `BetterAds` Swift package API shape.

### Content loading (SDK-owned)

| `BetterAdsContentMode` | Behavior |
|------------------------|----------|
| `FIXTURE` (**spike default**) | Built-in sample creatives, no network / no base URL / no auth |
| `BOOKIE_GET_AD` | Interim: `GET /getAd?size={format}` |
| `DEDICATED_API` | Future: `GET /ads/{format}` |

## Formats (Bookie parity)

| Format | Layout |
|--------|--------|
| `COMPACT` | Row: 50×53 hero, wordmark/headline, description, capsule CTA; “Ad” chip |
| `BANNER` | Fixed height 164; left copy + 205×164 hero; “Ad” chip |
| `CARD` | Vertical card with “Advertisement” chip |
| `INTERSTITIAL` | Skipped (no UI) |

## Usage (spike / fixture)

```kotlin
import com.betterads.BetterAdsClient
import com.betterads.model.AdFormat
import com.betterads.ui.BetterAdView
import com.betterads.ui.ProvideBetterAdsClient

val ads = BetterAdsClient.fixture(apiKey = "YOUR_BETTER_ADS_KEY")

ProvideBetterAdsClient(ads) {
    BetterAdView(format = AdFormat.BANNER)
    BetterAdView(format = AdFormat.COMPACT)
    BetterAdView(format = AdFormat.CARD)
}
```

Or pass `client =` explicitly to `BetterAdView`.

When the ads backend is ready, switch to a remote configuration (`apiKey` + `baseUrl`) instead of fixture mode. The same key is sent as `X-API-Key`.

### Tracking + CTA open (owned by the view)

| Event | When |
|-------|------|
| Impression | Loaded creative appears — once per view model (skipped in fixture mode) |
| Click | CTA tapped → analytics POST when remote (skipped in fixture), then SDK opens |
| Open | `URL` → Custom Tabs; `DEEPLINK` → `ACTION_VIEW` |

Host `onClick` / `onImpression` are observation-only.

## Local development

```bash
./gradlew :betterads:assembleDebug :betterads:testDebugUnitTest
```

## Consume from a host app (local path)

```kotlin
// settings.gradle.kts
include(":betterads")
project(":betterads").projectDir =
    file("../../better-ads/better-ads-android/betterads")

// module build.gradle.kts
implementation(project(":betterads"))
```
