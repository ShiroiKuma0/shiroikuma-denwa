# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fossify Phone — an open-source, privacy-focused Android dialer and call management app. Part of the Fossify ecosystem. Written entirely in Kotlin targeting Android API 26–36.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires signing config)
./gradlew assembleCoreDebug      # Build specific flavor+type combo
./gradlew detekt                 # Run static analysis (detekt)
./gradlew lintDebug              # Run Android lint checks
```

**Product flavors:** `core` (F-Droid), `foss`, `gplay` (Google Play). Debug builds get `.debug` app ID suffix.

There are no unit or instrumented tests in this repository. CI runs build checks via shared workflows in FossifyOrg/.github.

## Code Style

- Kotlin official style (`kotlin.code.style=official`)
- 4-space indentation, LF line endings, max 160 chars per line (editorconfig) / 120 chars (detekt)
- Star imports allowed after 5 usages
- Detekt enforces: max 120-line methods, max 10 function params, max 4 returns per function
- Detekt and lint both use baseline files (`app/detekt-baseline.xml`, `app/lint-baseline.xml`) — new violations are not allowed

## Architecture

### Call State Machine (`CallManager`)

The central piece of the app. `CallManager` is a singleton (companion object) that tracks active calls and notifies listeners. It models phone state as a sealed class hierarchy:

- `NoCall` — idle
- `SingleCall(call)` — one active/ringing call
- `TwoCalls(active, onHold)` — two simultaneous calls (swap/merge supported)

Conference calls are detected when a `Call.isConference()` exists with children; the manager handles conference vs. non-conference second call logic.

### InCallService Integration

`CallService` extends Android's `InCallService` — the system routes calls through it. It wires into `CallManager` for state and `CallNotificationManager` for ongoing call notifications.

`SimpleCallScreeningService` handles call screening before calls ring.

### Event-Driven Communication

The app uses **EventBus** for decoupled messaging between components (e.g., `Events.RefreshCallLog`). Activities/fragments register and unregister in lifecycle methods.

### Tab-Based Main UI

`MainActivity` uses `ViewPager` with three fragments: **Contacts**, **Favorites**, **Recents** (tabs can be toggled via settings). Each fragment extends `MyViewPagerFragment`.

### Key Helpers

- **`RecentsHelper`** — queries `CallLog.Calls` content provider, groups calls, handles call history export
- **`CallContactHelper`** — resolves phone numbers to contact names/photos via content resolver
- **`CallContactAvatarHelper`** — loads and caches contact avatars (uses Glide)
- **`CallNotificationManager`** — builds/updates foreground notification during calls
- **`Config`** — SharedPreferences wrapper (accessed via `context.config` extension)
- **`ToneGeneratorHelper`** — DTMF tone generation for the dialpad

### Fossify Commons Dependency

Heavy reliance on `org.fossify:commons` (version in `gradle/libs.versions.toml`). It provides base activities, theming, contact utilities, shared UI components, and `ensureBackgroundThread`. Check commons source when base class behavior is unclear.

## Key Configuration Files

- `gradle.properties` — app ID (`org.fossify.phone`), version name/code
- `gradle/libs.versions.toml` — single source of truth for all dependency versions
- `app/build.gradle.kts` — Android config, flavors, signing, detekt/lint setup
- `detekt.yml` — detekt rules (at project root)
- `lint.xml` — Android lint severity overrides (at project root)

## Versioning & Release

Versions live in `gradle.properties` (`VERSION_NAME`, `VERSION_CODE`). Releases are triggered by CI when `.fossify/release-marker.txt` is modified. The `CHANGELOG.md` follows Keep a Changelog format and drives the prepare-release workflow.

## Patched Fossify Commons (anti-tamper removed + fork-package fixes)

This fork builds against **our patched Fossify Commons**, not the upstream binary. Upstream Commons
6.1.x shows a "You are using a fake version of the app…" dialog (and silently breaks "Customize
colors") whenever the installed app id is not `org.fossify.*` — always the case for us (`shiroikuma.*`).

- **Source:** the `shiroikuma-commons` fork (`~/git/shiroikuma-commons`, branch `custom`), which strips
  Commons' anti-tamper "fake version" / sideloading checks out entirely **and** carries fork-package
  fixes for spots where Commons hard-codes `org.fossify.*` (documented in that repo's CLAUDE.md).
- **Delivery:** published to the local Maven repo, consumed as `commons = "6.1.6-sk2"` in
  `gradle/libs.versions.toml` (`mavenLocal()` is already a repository in `settings.gradle.kts`).
- Because Commons itself no longer nags, this app carries **no** anti-tamper workaround — no
  `getPackageName` spoof, no `SIDELOADING_FALSE`, no `res/raw/keep.xml`.

### Commons hard-codes the `org.fossify.phone` package (call-intent fix)

Commons assumes it runs inside the real Fossify Phone app and hard-codes that package name in places
that break for our renamed app id (`shiroikuma.denwa`, namespace still `org.fossify.phone`). The one
that bit us: `BaseSimpleActivity.launchCallIntent` (commons `extensions/Activity.kt`) pins every
outgoing-call intent to `setClassName("org.fossify.phone[.debug]", "…activities.DialerActivity")` when
`isDefaultDialer()` is true. That package isn't installed for us, so calls died with commons' "No valid
app found" toast (`ActivityNotFoundException`). The old `getPackageName` spoof masked it until it was
dropped.

- **Fix (in-app, not in the Commons fork):** `app/src/main/kotlin/org/fossify/phone/extensions/CallExt.kt`
  defines its own `BaseSimpleActivity.launchCallIntent` that mirrors commons but targets the real
  `packageName`; the commons import is dropped so all call sites resolve to ours. Commons has no internal
  caller, so this covers every call path.
- **Watch on upstream bumps:** if commons changes `launchCallIntent`'s signature, keep our override in
  sync; and other commons helpers may similarly hard-code `org.fossify.phone` for forks.
- **Related commons-side fixes (shipped in `-sk2`):** the private-contacts provider allowlist
  (`MyContactsContentProvider`) and the blocked-numbers dialer-id gate (`ManageBlockedNumbersActivity`)
  were similarly tied to `org.fossify.phone`. Both are fixed **in the commons fork** (not here) and
  documented there; denwa benefits from both — as a private-contacts reader and as the dialer.

**On a fresh machine, or after an upstream bump changes the Commons version — republish before building:**

```bash
cd ~/git/shiroikuma-commons
git checkout <new-commons-tag>     # then re-apply all patches (anti-tamper strip + fork-package fixes)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<ver>-skN
```

Then set this app's `commons` pin to the same `<ver>-skN` (currently `6.1.6-sk2`; `-skN` is our patch
revision — see the commons fork's CLAUDE.md). The patched AAR lives only in `~/.m2`, not in the repo.
