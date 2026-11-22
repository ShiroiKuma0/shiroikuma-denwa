# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Fossify Phone — an open-source, privacy-focused Android dialer and call management app. Part of the Fossify ecosystem. Written entirely in Kotlin targeting Android API 26–36.

## 白い熊 UI default palette

The 白い熊 電話 UI (granular theming in `ThemeActivity` / `extensions/ThemeColors.kt`) seeds and resets
to black `#000000` + **pure yellow `#FFFF00`** (`PALETTE_BLACK` / `PALETTE_YELLOW` in
`helpers/Constants.kt`). Never use material yellow `#FFEB3B` for fork UI defaults.

The SIM badges (call log, call screen, and the 1/2 glyph on a swipe-to-call row) have their own
defaults in the same file: **SIM 1 pure red `#FF0000`, SIM 2 pure blue `#0000FF`** (`PALETTE_RED` /
`PALETTE_BLUE`), with the number drawn on them in pure yellow and bold (`ThemeSlot.SIM_TEXT` +
`config.simTextBold`). All four are settable under "SIM cards" in the Theme screen. The carrier's own
highlight colour is never used for SIM 1 or 2, and none of these colours is contrast-adjusted on the
way to the screen — an explicitly picked colour reaches the badge unchanged, which is also why the
badge views carry no dimming `android:alpha`.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires signing config)
./gradlew assembleFossDebug      # Build specific flavor+type combo
./gradlew detekt                 # Run static analysis (detekt)
./gradlew lintDebug              # Run Android lint checks
```

**Product flavors:** `foss`, `gplay` (Google Play). Debug builds get `.debug` app ID suffix.

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

Versions live in `gradle.properties` (`VERSION_NAME`, `VERSION_CODE`), which track upstream, plus
`BUILD_NUMBER`, our own per-build counter (bumped automatically by the `buildFoss` task). The two are
combined in `app/build.gradle.kts`: `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER padded to 3>"` and
`versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER`. **Both must carry the counter** — with a bare
`VERSION_CODE` every build of one upstream line shares a code, and Android and any code-comparing
installer report a fresh build as "already installed" (that happened through build +073).

Releases are triggered by CI when `.fossify/release-marker.txt` is modified. The `CHANGELOG.md` follows Keep a Changelog format and drives the prepare-release workflow.

## Patched Fossify Commons (anti-tamper removed + fork-package fixes)

This fork builds against **our patched Fossify Commons**, not the upstream binary. Upstream Commons
6.1.x shows a "You are using a fake version of the app…" dialog (and silently breaks "Customize
colors") whenever the installed app id is not `org.fossify.*` — always the case for us (`shiroikuma.*`).

- **Source:** the `shiroikuma-commons` fork (`~/git/shiroikuma-commons`, branch `custom`), which strips
  Commons' anti-tamper "fake version" / sideloading checks out entirely **and** carries fork-package
  fixes for spots where Commons hard-codes `org.fossify.*` (documented in that repo's CLAUDE.md).
- **Delivery:** published to the local Maven repo, consumed as `commons = "7.1.3-sk1"` in
  `gradle/libs.versions.toml` (`mavenLocal()` is already a repository in `settings.gradle.kts`).
- Because Commons itself no longer nags, this app carries **no** anti-tamper workaround — no
  `getPackageName` spoof, no `SIDELOADING_FALSE`, no `res/raw/keep.xml`.
- **Beyond the fork-package fixes, the pinned revisions also carry fork UI patches this app uses:**
  `-sk3` adds the opt-in dialog accent frame (`BaseConfig.dialogBorderColor` / `dialogBorderWidth`,
  0 = off for stock consumers; we opt in from `syncDialogFrame()` in `extensions/ThemeColors.kt`),
  `-sk4`/`-sk5` paint the contextual action bar in code and make the dark/You popup-menu
  backgrounds black with a yellow border, and `-sk6` replaces the system's white toast bubble with a
  themed one (app background fill, primary-color text and 2dp frame) whenever the toast is raised from
  a live activity — non-activity contexts (services, receivers) still get the plain system toast,
  because API 30+ silently drops custom toast views from a backgrounded app. `-sk7` adds a
  contacts-import fallback for a stale account source (irrelevant here); `-sk8` themes the list-adapter
  contextual action bar and `-sk9` lets a toast from a service or receiver borrow the resumed activity
  for the themed bubble. **`-sk10` is the one this app depends on for contact names:** commons'
  `getMyContactsCursor()` used to ask only `org.fossify.commons.contactsprovider`, which nothing on the
  phone serves, so every renrakusaki private contact ("Phone storage (not visible by other apps)") was
  invisible — no name on the call screen or in the call log, and "block unknown numbers" rejected them.
  sk10 asks `shiroikuma.renrakusaki[.debug].contactsprovider` first (our manifest already holds its
  signature-level `READ_PRIVATE_DATA`). A pin below sk10 silently brings that back. `-sk11` (`Contact.extra`) and
  `-sk12` (opt-in pill dialog buttons, left off here) are renrakusaki's and change nothing for us.
  **`-sk13` holds code we share with renrakusaki instead of copying it:** swipe to call
  (`helpers/SwipeToCallCallback` — the 1/2 glyph colour and the “Dialing…” text are constructor
  parameters — and `extensions/SwipeToCall.kt`'s `setupSwipeToCall` / `removeSwipeToCall`) and the
  call-log formats (`extensions/CallFormats.kt`: the Japanese clock and duration readings, the imperial
  date, `formatCallTime` / `formatCallDuration` by ordinal and the `CALL_TIME_*` / `CALL_DURATION_*`
  ordinals our `CallTimeFormat` / `CallDurationFormat` enums must match). A change to either is made in
  the commons fork, published as the next `-skN`, and both apps move their pin. `-sk14` (per-side swipe
  marks, renrakusaki's) changes nothing for us; **`-sk15` and `-sk16` are ours**: red destructive dialog buttons, the
  family's look on commons' own Compose screens and dialogs, and tight rows on Customize colors, all behind
  `pillDialogButtons`. `-sk17` adds the outlined switch and floating button under that opt-in.
  **`-sk18` is not a pure addition** — see “Colours — commons roles” below. The others are pure
  additions — a bump that drops them shows up as edgeless dialogs, grey menus, white toasts and
  nameless private contacts, not as a build failure.

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

### Commons 7 (`7.1.3-sk1`, from 1.11.1+128, 2026-11-22)

The pin moved from `6.1.6-sk18` to `7.1.3-sk1`: every fork patch of `6.1.6-sk1` – `sk19` replayed onto
upstream Commons 7.1.3 (the `-skN` revisions named above are those of the 6.1.6 line; all of them are in
`7.1.3-sk1`, and its own counter starts again at 1). The app side follows upstream Phone's own
“migrate to commons 7.1.0” commit (`1121f4eb`):

- The “thank you” feature locks are gone upstream — `FeatureLockedDialog`, `addLockedLabelIfNeeded`,
  `isOrWasThankYouInstalled`. Manage blocked numbers, block / unblock and create shortcut are plain
  actions with plain labels.
- The flavour bool `hide_google_relations` became `is_google_play_build` with the opposite meaning
  (false for `foss`, true for `gplay`; the commons default is false); `pretend_thank_you_installed` is
  dropped.
- Commons 7's timed donation / rating prompt is a no-op in the fork.
- sk19 came along: commons' own time picker, a settable week start, month-only / year-only date picker.

**On a fresh machine, or after an upstream bump changes the Commons version — republish before building:**

```bash
cd ~/git/shiroikuma-commons
git checkout <new-commons-tag>     # then re-apply all patches (anti-tamper strip + fork-package fixes)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<ver>-skN
```

Then set this app's `commons` pin to the same `<ver>-skN` (currently `7.1.3-sk1`; `-skN` is our patch
revision — see the commons fork's CLAUDE.md). The patched AAR lives only in `~/.m2`, not in the repo.

## Contacts/Favorites tabs hand off to our Contacts fork (renrakusaki)

When our Contacts fork `shiroikuma.renrakusaki` is installed and the "Open the Contacts app from the
Contacts and Favorites tabs" toggle is on (the default; the Settings row only shows when renrakusaki is
installed), `MainActivity` intercepts taps/swipes on the Contacts and Favorites tabs and launches
renrakusaki's `MainActivity` directly with the `shiroikuma_open_tab` int extra (a commons `TAB_*` mask)
so it lands on the matching tab. The extra name must stay in sync with `OPEN_TAB_INTENT_EXTRA` in the
renrakusaki repo (`~/git/shiroikuma-renrakusaki`), which consumes it in `takeRequestedTab()`. Direct
targeting (with `CLEAR_TOP or SINGLE_TOP`) is required because the launcher intent would not deliver the
extra to an already-running instance. Programmatic tab selection (default tab, last-used page) is
sanitized to never land on a hand-off page, so the dialer never auto-bounces into renrakusaki at launch;
the interception also requires a non-hand-off tab (Recents) to be shown, otherwise it stays inert. Both
renrakusaki package ids are declared in the manifest `<queries>` block for package visibility.

**Four tabs, one bar across three apps (2026-11-17).** 電話, 臨電話 (`shiroikuma-rindenwa`) and 連絡先 each
draw the same bar in the same order — **Favorites, Contacts, 電話, 臨電話** — the last two labelled exactly
so in every language (`tab_denwa` / `tab_rindenwa`, not translatable). Here `tabsList` holds the three pages
we own in that order; 臨電話 is `TAB_RINDENWA`, a tab with no page, drawn only when `shiroikuma.rindenwa` is
installed (and our call history is shown). Tapping it calls `launchRindenwa()` (`extensions/SisterApps.kt`):
`shiroikuma.rindenwa` / `org.linphone.ui.main.MainActivity`, boolean extra `shiroikuma_open_history` = true,
`NEW_TASK or CLEAR_TOP or SINGLE_TOP`, no animation. Rindenwa's 電話 tab opens us the way renrakusaki's does:
`shiroikuma_open_tab` = `TAB_CALL_HISTORY` (4). Favorites and Contacts fall back to our own lists when
renrakusaki is missing rather than hiding. Because every app now draws the bar itself, the old dialer-tabs mask
(`shiroikuma_dialer_tabs`, our `config.showTabs`, which made renrakusaki wear our three tabs for a session)
is gone: we stopped sending it in 1.11.1+108, which therefore needs renrakusaki 1.6.0+123 or later.

The bar is drawn as **card-folder tabs**, the look 臨電話 gave it first: `views/FolderTabsView` sits behind
the (transparent) `TabLayout` and draws one bold accent line along the bar's top edge that dives around the
selected tab, the others thinner at 30 % behind it; the selected label is bold. Measures are Rindenwa's
`SkFolderTabPainter` (3 dp / 1.5 dp strokes, 12 dp corners, 260 ms) — change them in all three apps or none.

**The hand-off is bidirectional.** Renrakusaki's 電話 tab hands straight back, launching our `MainActivity` with the same `shiroikuma_open_tab`
extra set to `TAB_CALL_HISTORY` and the same `CLEAR_TOP or SINGLE_TOP` flags; `takeRequestedTab()` consumes
it in `onCreate` and `onNewIntent`, and honors it only for a tab that stays in the dialer, so a request for
Contacts or Favorites can never ping-pong the two apps. Neither side finishes the other — both activities
stay alive, so alternating taps are a warm task swap — and both start each other with
`ActivityOptions.makeCustomAnimation(0, 0)` so the swap reads as a tab change rather than an app switch
(`overridePendingTransition` is deprecated and ignored on API 34+). The renrakusaki half lives in
`~/git/shiroikuma-renrakusaki`.

### Call history for renrakusaki's contact screen

Renrakusaki's contact screen carries a 発着信履歴 section: that contact's last calls, read from the
system call log by renrakusaki itself and drawn exactly like our Recents (day headings, SIM badge,
arrow, time, duration, place — no avatar or name). Two contracts serve it, and their names must stay in
sync with the renrakusaki repo:

| Contract | Shape | Where |
| --- | --- | --- |
| Recents filter | `shiroikuma_open_tab` = `TAB_CALL_HISTORY`, plus `shiroikuma_recents_filter_numbers` (`ArrayList<String>`, the contact's numbers as stored) and `shiroikuma_recents_filter_name` (`String`, display name) | `MainActivity.takeRecentsFilter()` → `RecentsFragment.applyNumbersFilter()` |
| Call-log look | `content://${applicationId}.calllogstyle/style` (cursor of `key`, `value` strings) and `…/font/<file name>` (read-only font file) | `providers/CallLogStyleProvider.kt` |
| Call stamps | `content://${applicationId}.calllogstyle/stamps` — cursor of `date` (long, the call log's `DATE`), `number` (as logged) and `stamp` (JSON); selection arguments, if any, are numbers to narrow it to | `providers/CallLogStyleProvider.kt` → `helpers/CallStamps.kt` |

- **The filter** is sent by renrakusaki's pill “電話で全履歴 ›”. It is consumed on read (then
  `removeExtra`), in `onCreate` and `onNewIntent`, like the tab request. Recents shows only the calls
  matching any of the numbers — the tapped-row match, plus `PhoneNumberUtils.compare` on normalized
  numbers and the nine-digit tail of `hasSameTailAs` — under the existing “Calls with <name>” banner,
  whose cross (or Back) restores the whole log. Without the extra nothing changes.
- **The style provider** is exported with no permission but answers only `shiroikuma.renrakusaki`, its
  `.debug`, and `shiroikuma.rindenwa` (by `callingPackage`); anyone else gets null. Rindenwa reads it for
  its SIP history rows, which share our three-line row (`item_recent_call.xml`: name / number · label /
  SIM badge, arrow, time, duration, country — then ☎, ✉ and ⋮, the handset on the left since 2026-11-17). Added for that row on 2026-11-17, all pure
  additions under version 1: `color.number`, `color.country`, `color.action_message`, `color.action_call`,
  `color.duration` / `font.duration.*` (the duration and its “·”, which used to share `color.subtitle`),
  `color.number_label` (the contact's label after the “·” on line 2, `#FFFF00`, while `color.number`
  before it is the paler `#FFFFB0`), `number_scale` (0.78 — line 2 is drawn at base size × this unless `font.number.size` is set),
  `color.unanswered` (arrow and time of a call we placed that was not answered; an answered one takes
  `color.outgoing` for both), `color.failure` and `color.rin_badge` / `color.rin_badge_text` (the last three are drawn by Rindenwa
  only, but set on our Theme screen), the menu icon roles `color.role.call` / `.message` / `.contact` /
  `.share` / `.view` / `.block` / `.delete`, `font.number.*` and `font.country.*`. There are no size keys: the picture is a square 90 % as tall as
  the row (`size.picture_dp` was served in +104 to +108 only), the SIM badge is as tall as
  line 3's text, and so with ☎, ✉ and ⋮: here they
  sit on line 2 only, each as tall as that line, so the name keeps the row's whole width on a narrow
  screen (白い熊, 2026-11-17, after +106); the ✉ is an accent-coloured outlined chat bubble.
  Keys of `/style`:
  - `color.name`, `color.subtitle`, `color.date`, `color.day_date`, `color.missed`, `color.incoming`,
    `color.outgoing`, `color.divider`, `color.day_divider`, `color.date_underline`, `color.sim_text`,
    `color.sim1`, `color.sim2`, `color.background` — decimal ARGB ints, resolved through the defaults
    (`themeColor()` / `simColor()`), never unset;
  - `thickness.divider`, `thickness.day_divider`, `thickness.date_underline` — dp;
    `thickness.divider` / `color.divider` are the line **between two calls of one day**: default 1 dp
    in `#8C8C00` (`PALETTE_DIM_YELLOW`), thinner and paler than the day's line (4 dp, accent). Both
    apps draw it inset by 16 dp from both screen edges and never under a day's last call, where the
    day's line follows; 0 hides it (白い熊, 2026-11-17, from 1.11.1+115 and Rindenwa …+025). Here it is
    an `ItemDecoration` in `RecentCallsAdapter` (`callDividerDecoration`), not a view in the row, so it
    stays right when a neighbour comes or goes without a rebind. `migrateCallDividerIfNeeded()` brought
    a stored 0 back to the defaults once;
  - `font.<slot>.family` / `.weight` / `.size` for the slots `name`, `subtitle`, `date`, `day_date` —
    the stored family (`""` system, `@monospace`, or a font file name), weight (0 = default) and size
    in sp (0 = not set);
  - `text_size_px` (the adapter's base `getTextSize()`, float), `day_header_scale`
    (`RecentCallsAdapter.DAY_HEADER_FONT_SCALE`), `sim_text_bold` (1/0), `time_format`
    (`CallTimeFormat` ordinal), `duration_format` (`CallDurationFormat` ordinal), `imperial_date` (1/0),
    `format_numbers` (1/0), `version` (1).
  `/font/<name>` serves a file from `FontHelper.getFontsDir` and rejects any name with “/” or “..”.
  Renrakusaki reads `/style` each time a contact screen opens and falls back to our defaults when the
  provider is missing, so neither app has to be released first. **Renaming or re-meaning a key, or
  reordering the format enums, breaks renrakusaki's rows silently — bump `version` and tell that repo.**

## Call stamps — a call keeps the SIM it was made through

The system call log records only the slot a call used, so the SIM badge used to be worked out on every
read from that and today's settings: a new card in a slot took over the old card's history, and
re-colouring SIM 1 repainted last year's calls. Since 2026-11-18 each call has a **stamp**
(`models/CallStamp.kt`, kept by `helpers/CallStamps.kt` in `call_stamps.db`) and a row with a stamp is
drawn from it alone.

- **Key: the call's date plus number**, never the call-log row id — a restored backup gives every row a
  new id, and `CallHistoryBackup` dedups on the same pair. The stamp travels inside each call's object
  in `call_history.json` as `"sim"` (an added key; archives without it restore as before).
- **What it holds:** the SIM's own number and operator, slot, the badge as it looked (digit, fill, text
  colour, bold), and what only the in-call service knows: disconnect cause, ring time, network
  (VoLTE / Wi-Fi / 3G…), roaming network and country, audio route, signal, caller ID (known only from a
  dialled `#31#` / `*31#`; otherwise “network default”).
- **Where it comes from:** `helpers/CallStampRecorder.kt`, driven by `CallService`, while the call is
  live (source `call`); or, for a call we never saw — Android Auto takes the in-call service — the first
  log read, from the slot and the badge of that moment (source `log`), with the card's number only if
  that card was already in the slot when the call was made; or a **seed** (source `seed`); or a backup.
  A `call` stamp is never replaced by a weaker one.
- **The SIM's number** is read as 自由作業盤 reads it: `SubscriptionManager.getPhoneNumber(subId)`, then
  `SubscriptionInfo.number`, under `READ_PHONE_NUMBERS`.
- **The seed** is a file, `sim-history-seed.json`, dropped into the app's external files directory
  (`Android/data/<app id>/files/`): `{"accounts": {"<account id as logged>": {"sim": 1, "number": "+420…",
  "operator": "…"}}}`. On the next log read every cellular call logged under one of those ids (and not
  recorded live) is stamped with that card and the badge colours of that moment; the file is deleted and
  `sim-history-seed.result.txt` says how many calls it stamped. It is a file because the numbers are
  private and this repository is public — **never put a real number in the source.**
- **The first run** stamps the whole log once, not just the page shown, so the badges of that day are
  frozen for all of it before a colour changes.
- **The dialog** (`dialogs/SimBadgeDialog.kt`, the badge drawn large by `views/SimBadgeView.kt`) opens
  from a tap on a row's badge and shows the stamp; a fact that is not known is left out.
- **Served to the sister apps** at `…calllogstyle/stamps` (same caller allowlist as the style, added
  2026-11-18 in +120, a pure addition under version 1). A reader matches a call-log row on `date` plus
  `number` and draws its badge from the stamp's `digit`, `color`, `text`, `bold`; a row without a stamp
  falls back to the slot and `color.sim1` / `color.sim2`. The JSON keys are those of `CallStamp.toJson()`
  (`src`, `digit`, `color`, `text`, `bold`, `num`, `op`, `slot`, `end`, `ring`, `net`, `roam`, `roam_op`,
  `roam_iso`, `route`, `dbm`, `lvl`, `cid`; an absent key means “not known”). **Renaming or re-meaning
  one breaks the readers and old backups alike.**

## Colours — commons roles (commons sk18, 2026-11-21)

Since sk18 the commons names no colour in XML: its `colors.xml`, the `md_` palette, the stock per-colour
themes, *Customize colors* (`CustomizationActivity`) and the drawables that carried a colour
(`circle_background`, `ripple_background`, `ripple_all_corners`, `selector`, `divider`,
`color_picker_circle`, `top_popup_menu_bg_dark`) are deleted. Every colour it draws is a
`org.fossify.commons.helpers.UiRole`, stored under `ui_role_<key>` in our prefs (absent = default;
`#FFFF00` / `#000000` are written once, in the commons).

- **A `ThemeSlot` with `role = UiRole.X` IS that role** under our label: `themeColor()` returns
  `uiColor(role)`, `setThemeColor` / `resetThemeColor` write the role's key. Mapped: the three foundation
  slots, `SWITCH_OFF` → `SWITCH_OFF_OUTLINE`, the five `SEARCH_*`, `MENU_TEXT` → `MENU_LABEL`,
  `HEADER_TITLE` → `TOOLBAR_TITLE`, `HEADER_ARROW` → `TOOLBAR_ICON`, the three `TAB_*` →
  `BOTTOM_BAR_GROUND` / `BOTTOM_TAB_ACTIVE` / `BOTTOM_TAB_IDLE`. Overrides stored before the move were
  handed over once (`migrateSlotsToRolesIfNeeded`), and a set `MENU_ROLE_DELETE` seeded
  `BUTTON_DESTRUCTIVE`.
- **Nothing is mirrored into the commons any more.** `syncDialogFrame()` only keeps the frame width (2 dp)
  and `pillDialogButtons` on; the dialog frame, the destructive button and the off switch are the roles
  `DIALOG_FRAME`, `BUTTON_DESTRUCTIVE`, `SWITCH_OFF_OUTLINE`. The delete *menu icon* stays
  `MENU_ROLE_DELETE`, a slot of ours — the two are no longer kept equal.
- **The Theme page lists every role no slot stands for**, in the section of its item
  (`ThemeActivity.addRoleRows`); a new commons role appears there by itself. `ui_role_*` keys
  travel in the Appearance part of the export.
- **Shapes the commons used to ship are built in code** (commons `extensions/UiRoleViews.kt`:
  `circleDrawable`, `circleRippleDrawable`, `listDividerDrawable`, `colorPickerRing`,
  `menuPanelDrawable`, …). A swatch has no background in its layout — set `circleDrawable(color)`, never
  `background.setTint`. The popup menu's panel is painted by `PillMenuItemView` on attach.
- **A slot that reads a commons role in code** (no row of its own): pills take `BUTTON_TEXT` / `BUTTON_FRAME` /
  `BUTTON_GROUND` (`asPill()`), the app's own dialogs and the bottom sheet `DIALOG_*`, the main screen's dialpad
  button `FAB_*`, the selection bar `ACTION_BAR_*`, the outlined switch the four `SWITCH_*` roles, a menu row
  `MENU_ROW_FRAME` and `MENU_DIVIDER`, fast-scroller thumbs `SCROLL_BUBBLE` / `SCROLL_BUBBLE_TEXT`.

## Colours — the app's own full rebuild (1.11.1+126, 2026-11-21)

白い熊: “Apply full black-yellow skill.” Section G of the skill is in force here: **no colour in XML, every item a
role of its own, set in the section of its item.**

- **The role table is `ThemeSlot`** (`extensions/ThemeColors.kt`); defaults are in `themeDefault()` and name only
  `PALETTE_*` constants (`helpers/Constants.kt`) or another role. A new widget gets a new slot, a label string
  and a line in `themeDefault()` in the same commit — never `getProperPrimaryColor()` / `getProperTextColor()` /
  `getProperBackgroundColor()`, which no file of the app calls any more, and never a literal.
- **Nothing coloured is inflated.** The accept, decline and end rings (`CallActivity.callRing`), the silence,
  message and block buttons, the undo bar, the car screen's two discs (`CarCallActivity.styleCarDisc` — still
  filled, by 白い熊's decision of 2026-11-19), the launcher shortcut's disc and the notification's three buttons
  (masks in the layout, `setColorFilter` from `NOTIFICATION_*`) are built or tinted in code. `res/values/colors.xml`
  is gone. Left in XML, and only these: the launcher icon's grounds and foreground (the platform draws them), the
  white placeholder fill of vector icons (masks, tinted in code), `?attr/selectableItemBackground` (a shape).
  The notification's ground and text are the system's.
- **`updateTextColors()` paints every text view as plain text**, so a screen's own roles are applied *after* it:
  `CallActivity.applyCallScreenRoles()`, `DialpadPanelBinding.applyDialpadRoles()` (which also colours each key's
  digit and letters through `applyDialpadKeyTextRoles()`), the fragments' `setupColors`.
- **Reset** is per role (the picker's Default) and for all at once: Maintenance → “Reset all colours”
  (`resetAllThemeColors()`: commons roles, our slots, the two SIM fills).

### The UI page is a folding accordion with a chip index

`ThemeActivity.buildRows()` registers the sections (`UiSection`, key + title + what fills it); `addSectionIndex()`
draws one chip per section (tap = unfold and scroll, long-press = drag, the chips reorder live and the sections
follow on drop), `addSection()` the folding block. Every section is folded until opened and its rows are built
only then. Order and fold state are `config.uiSectionOrder` / `uiSectionsOpen`, stored under `theme_ui_…` so they
travel in the Appearance export. **The section keys are stored — never rename one.** Row builders add to `body`
(the section being filled), not to `binding.themeHolder`. Every row takes a `RowIcon`, coloured by the menu icon
roles. Commons roles are listed by `addRoleRows(UiRoleSection, indent)` in the section of their item.

## Diagnostics — behind switches, in `diag/`, never “.log”

EMUI's logcat keeps error-level lines only, so what the app did can only be read from files it writes
itself. All of them go through `helpers/Diag.kt`, laid out as the diagnostics-ui-settings skill says and
shown as the last section of the Theme (UI) page: **every kind behind its own switch, all off by
default**, then Recorded data (stored, keep for N days, Export into a dated subdirectory of a picked
directory, Share, Purge behind a warning that lists what goes). While any switch is on, an ongoing
notification offers Mark and Turn all off (`receivers/DiagReceiver.kt`).

| Kind | Switch | Written by |
| --- | --- | --- |
| `car` | Car-call screen | `CarCallLog.write` — `CarCallMonitor`, `CarCallService` |
| `calls` | Call recording | `CallStampRecorder` — states, resolved SIM, the stored stamp |
| `sim` | SIM cards | `CallStamps` — a new card in a slot, each sweep of the log |

Files are `Android/data/<app id>/files/diag/denwa-<kind>_<yyyy-MM-dd>.txt`, appended one line at a
time (opened and closed around each line, so a deleted file simply starts again), capped at 8 MB a day.
**No file this app writes on the phone may end in “.log”** — EMUI's nightly clean-up deletes those.
The car trail was an always-on `carcall.log` in the files root until 2026-11-19; `Diag.init` moves a
leftover into `diag/`. A new diagnostic is a new `Diag.Kind` plus `Diag.log(kind) { … }` at the call
site — never a file of its own.

## The black-yellow look, applied app-wide (2026-11-19)

The black-yellow-theme skill is the specification; these are the places it is enforced centrally, so a
new screen gets the look without doing anything — and the things to keep when touching them:

- **Dialog buttons are pills**: `syncDialogFrame()` sets commons' `pillDialogButtons` (sk12) on every
  resume — positive thick and bold, the others thin. Commons knows no destructive button, so a
  “Delete” confirm is still an accent pill, not a red one.
- **Every switch is the outlined switch** (`views/OutlinedSwitch.kt`, the same as 予定表's):
  the lifecycle callback in `App.kt` runs `outlineAllSwitches()` over every screen on resume. Off colour:
  `ThemeSlot.SWITCH_OFF`.
- **A selection mark is not a switch** (白い熊, 2026-11-21): the check boxes that say which entries of a list
  are chosen — visible tabs, contact sources, call-screen fields, export items — and every radio button stay
  marks, coloured by `UiRole.CHECK_*` / `RADIO_*` (`MyAppCompatCheckbox.applyRoles()`). Only a lone on/off
  check box is a setting and takes `applyOutlinedSwitch(small = true)` — today just “always use this SIM” in
  `SelectSIMDialog` — as does the checkable row of a menu (`PillMenuItemView`). `outlineAllToggles()` is gone.
- **The stock Settings page** keeps upstream's layout and is restyled in code by
  `restyleSettingsPage()` (`extensions/SettingsPage.kt`): tight rows, bold underlined headings, nothing
  faded, and a pill with › around the label of each row that opens something — **a new such row must be
  added to the list passed from `SettingsActivity.onResume`**.
- **Tappable words are pills**: `TextView.asPill()` / `asPlaceholderPill()`, and round buttons are rings,
  never filled discs: `ImageView.asRingButton()` (`extensions/Pills.kt`). The dialpad's call button is a
  ring in `DIALPAD_CALL_BUTTON`; the `DIALPAD_CALL_ICON` slot is gone.
- **The in-call screen is outlined too** (白い熊, 2026-11-19): accept and decline / end are a green and a
  red ring with the handset in the same colour (`ic_call_accept.xml`, `ic_call_end.xml`). **The car call
  screen is the one exception and stays filled** (`ic_car_call_accept.xml`, `ic_car_call_end.xml`): it is
  read at a glance while driving, where a solid disc is found faster — do not “fix” it; the control buttons are rings (`CallActivity`: an action in `CALL_CONTROL_ACTIVE`, a
  toggle thick in it when on and thinner in `CALL_CONTROL_INACTIVE` — now a ring colour defaulting to
  `SWITCH_OFF` — when off); the block button is an outlined octagon; the conference screen's two buttons
  are thin rings; the dialpad keys are ringed with no tint fill; the snackbar's action is a pill.
- **A button that deletes is a red pill** (commons sk15): `ConfirmationDialog(…, destructive = true)` or
  `setupDialogStuff(…, destructiveButton = AlertDialog.BUTTON_*)`; the colour is
  the commons role `UiRole.BUTTON_DESTRUCTIVE` (Theme page → Buttons). **A new
  delete / purge / reset confirmation must pass it.**
- **Commons' own screens** (About, Manage blocked numbers, its Compose dialogs) take the look from the
  same `pillDialogButtons` opt-in since sk15; `outlineAllSwitches()` runs from the app's lifecycle
  callback (`App.kt`), so commons' Views screens are covered as well.

## Menus are pill menus, with icons coloured by role

Every popup row in the app — a call-log row's ⋮, the top bar's and the action mode's overflow, commons'
screens included — is drawn by `views/PillMenuItemView`, swapped in through our own copies of AppCompat's
`abc_popup_menu_item_layout.xml` / `abc_cascading_menu_item_layout.xml` (verbatim 1.7.1 with only the root
view changed — **diff them against the library when AppCompat is bumped**). Rows are full-width outlined
pills per the black-yellow-theme skill; frame and label stay in the accent (the warning colour for a
destructive row) while the **icon takes the colour of what the row does** — `ThemeSlot.MENU_ROLE_*`,
settable under “Menu icon colors” on the Theme screen: call green, message blue, contact cyan,
copy/share orange, view/open/settings yellow, block magenta, delete red. `helpers/MenuPills` maps each
menu item id to its icon, role and whether it ends in a › — **a new menu item needs a line there**, or it
shows as a view-coloured pill with whatever icon it brought. `MenuPills.show()` opens a `PopupMenu`
shifted up so it never scrolls while the screen has room.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want Claude attribution in the history; this **overrides** the harness's default to append such a trailer. End commit messages at the last line of the body. (The existing history was scrubbed of these trailers on 2026-06-08; the global rule lives in `~/.claude/CLAUDE.md`.)
