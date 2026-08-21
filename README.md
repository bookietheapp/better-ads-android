# Better Ads (Android)

Standalone Android library (`com.betterads`) that returns **ready-to-display Compose ad views**, **loads creatives itself**, opens CTAs, and (when remote) reports impressions / clicks.

Matches the iOS `BetterAds` Swift package API shape.

### Content loading (SDK-owned)

| `BetterAdsContentMode` | Behavior |
|------------------------|----------|
| `FIXTURE` (**spike default**) | Built-in sample creatives, no network / no base URL / no auth |
| `SERVE_V1` (**current remote**) | SDK-owned serve endpoint (`size` + optional `app=` via `appName` while unauthenticated) |
| `BOOKIE_GET_AD` | Legacy: `GET /getAd?size={format}` — host `baseUrl` |
| `DEDICATED_API` | Future: `GET /ads/{format}` — host `baseUrl` |

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

### Tracking + CTA open (owned by the view)

| Event | When |
|-------|------|
| Impression | Loaded creative appears — once per campaign (skipped in fixture mode) |
| Click | CTA tapped → batched event POST when remote (skipped in fixture), then SDK opens |
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
