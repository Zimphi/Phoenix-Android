# Android navigation and startup performance

## Scope and causes

The repeated navigation scenario includes eID editor to profile list via Back,
eID tabs and card flip, ePassport front/portrait flip, and About/Profile navigation.
Before correction, the device recorded recurring frames above one second.

The UI reconstructed identical multi-megabyte document bitmaps and the country
catalogue for list thumbnails, unnecessarily generated invisible thumbnail MRZs,
and prepared/decoded passport previews twice. Profile snapshots also serialized
and parsed the full group JSON during composition. Editor entry saved the whole
profile store even when the effective credentials and RI state were unchanged.
Account snapshot getters shared a monitor with HTTPS operations, allowing a
navigation read to wait for the network.

Packaged immutable graphics now use one bounded 32 MiB image cache, warmed off the
UI thread during startup. The fixed bundled German country list is shared for the
process lifetime. Neither cache contains user photos, credentials or keys. Profile
portrait decoding stays in the background and the editor supplies one prepared
preview. Read-only account snapshots no longer acquire the network-operation
monitor; mutating/authenticated operations retain their synchronization. The
existing five-minute verified-connection window is reused without extending it.

## Initial reference-device results

SM-S918B, same debug configuration and stored synthetic profiles, five navigation
cycles per run. Statistics come from Android `dumpsys gfxinfo`, reset after startup.
The test includes automation overhead; these are frame durations, not a claim about
the end-to-end speed of every operation.

| Metric | Before | After, first run | After, repeat |
| --- | ---: | ---: | ---: |
| Frames | 1159 | 1126 | 1104 |
| P95 frame duration | 69 ms | 36 ms | 38 ms |
| P99 frame duration | 1150 ms | 113 ms | 109 ms |
| Janky frames | 8.89% | 7.28% | 7.43% |

The repeated test guards P99 below 500 ms on the reference device. Debug rendering
still has short slow frames; it is not claimed to be jank-free. Method sampling
introduced excessive overhead and triggered an ANR, so that run was excluded from
timing comparisons and sampling was stopped. The reported before/after runs use
only frame statistics.

## Startup logo

Android first drew the inset favicon in its system splash; Compose then drew a
larger cropped wordmark in a differently centred column. The startup now uses the
same favicon, 288 dp icon canvas and 20% inset at the same screen centre. The extra
exit animation is removed. Initial loading runs in the background. The normal
Android app-opening animation and the later home-header layout are distinct from
this corrected splash-to-app transition.

A three-start TesterArmy video was captured before and after. Extracted frames
showed the previous size/position jump; the corrected bird stays at the same
location and size across the system-to-Compose handoff. The geometry follows the
[Android splash-screen documentation](https://developer.android.com/develop/ui/views/launch/splash-screen/).

## Regression verification

Build and Android lint passed. Device instrumentation verifies immutable artwork
and country-list reuse, draft snapshot isolation, and account snapshots completing
within 300 ms while the network-operation monitor is deliberately held. TesterArmy
passed the repeated navigation measurement twice, passport/eID page flips, three
cold starts with video, and surname/serial/PKI/address persistence flows. Signed
profiles and protocol cores are unchanged. Full external-reader authentication and
a new QR-code login were not repeated for this UI performance change.
