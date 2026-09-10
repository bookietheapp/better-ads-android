# Better Ads (Android)

Standalone Android library (`com.betterads`) that returns **ready-to-display Compose ad views**, **loads creatives itself**, opens CTAs, and (when remote) reports impressions / clicks.

Matches the iOS `BetterAds` Swift package API shape.

### Content loading (SDK-owned)

| `BetterAdsContentMode` | Behavior |
|------------------------|----------|
| `FIXTURE` (**spike default**) | Built-in sample creatives, no network / no base URL / no auth |
| `SERVE_V1` (**current remote**) | SDK-owned serve endpoint (`size` + optional `app=` via `appName`, optional `externalAdId` for keyed Serve) |
| `BOOKIE_GET_AD` | Legacy: `GET /getAd?size={format}` — host `baseUrl` |
| `DEDICATED_API` | Future: `GET /ads/{format}` — host `baseUrl` |

## Formats (NativeOS Templates)

| Format | Template 1x frame (logical dp) |
|--------|--------|
| `COMPACT` | 329 × 51 hero image; “Ad” chip |
| `BANNER` | 345 × 164 hero image; “Ad” chip |
| `CARD` | 336 × 443 hero image; “Advertisement” chip |
| `INTERSTITIAL` | Skipped (Serve has no interstitial template) |

Serve returns a slim Design: `adId`, `campaignId`, `size`, `images.hero` (`1x` / `2x` / `3x`), and `ctaLink`. The SDK renders the hero as the entire ad and opens `ctaLink` on tap.

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

Switch to `SERVE_V1` for production serve. Hosts never configure the fetch URL. Send the NativeOS App API key (`nos_…`) as `apiKey`; the SDK attaches `X-Api-Key` on serve and events. Keep `appName` aligned with the Portal App until key-only auth ships.

Events batch to `POST /api/v1/events` with local queue, retry, and flush every ~30s + on background. See [`docs/IDENTITY_AND_ANALYTICS.md`](../docs/IDENTITY_AND_ANALYTICS.md) and [`docs/BOOKIE_INTEGRATION.md`](../docs/BOOKIE_INTEGRATION.md).

```kotlin
// Application.onCreate — enables persisted device_id
BetterAds.initialize(this)

val client = BetterAdsClient(
    configuration = BetterAdsConfiguration(
        apiKey = BuildConfig.NATIVEOS_APP_API_KEY, // nos_… — NOT in git
        contentMode = BetterAdsContentMode.SERVE_V1,
        appName = "Bookie", // must match Portal App
        userId = userId,    // optional; or call client.setUserId later
    ),
)

// On login / logout — only host identity concern:
client.setUserId(loggedInUserId) // or null when logged out / guest
```

The SDK owns `device_id` (persisted after `BetterAds.initialize`) and `session_id` (rotates on logout when you clear user id). See [`docs/IDENTITY_AND_ANALYTICS.md`](../docs/IDENTITY_AND_ANALYTICS.md).

### Keyed Serve (`externalAdId`)

Unkeyed `BetterAdView(format = …)` still picks from ads **without** an External Ad Id.

To fetch a specific Publisher-owned ad (for example Book of the Week), pass `externalAdId`. On keyed **404**, the SDK surfaces “no ad” and does **not** retry as unkeyed Serve. Impressions and clicks still use `adId` from the payload.

```kotlin
BetterAdView(
    format = AdFormat.BANNER,
    externalAdId = "book_of_the_week_de",
)

val ad = client.fetchAd(
    format = AdFormat.BANNER,
    externalAdId = "book_of_the_week_de",
)
```

### Tracking + CTA open (owned by the view)

| Event | When |
|-------|------|
| Impression | Loaded creative appears — once per `adId` (skipped in fixture mode) |
| Click | Hero image tapped → batched event POST when remote (skipped in fixture), then SDK opens `ctaLink` |
| Open | `http(s)` → Custom Tabs; otherwise `ACTION_VIEW` |

Host `onClick` / `onImpression` are observation-only.

## Local development

```bash
./gradlew :betterads:assembleDebug :betterads:testDebugUnitTest
```

## Consume from a host app

Repository: [github.com/bookietheapp/better-ads-android](https://github.com/bookietheapp/better-ads-android)

### Git submodule (recommended until Maven publish)

From the host app repo root:

```bash
git submodule add https://github.com/bookietheapp/better-ads-android.git external/better-ads-android
git submodule update --init --recursive
```

```kotlin
// settings.gradle.kts
include(":betterads")
project(":betterads").projectDir =
    file("external/better-ads-android/betterads")

// module build.gradle.kts
implementation(project(":betterads"))
```

Pin the submodule to a release tag when cutting host app releases.

### Local path (SDK development)

```kotlin
// settings.gradle.kts
include(":betterads")
project(":betterads").projectDir =
    file("../../better-ads/better-ads-android/betterads")

// module build.gradle.kts
implementation(project(":betterads"))
```
