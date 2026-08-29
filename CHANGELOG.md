# Changelog — 白い熊 電話

This file carries two histories. The **白い熊 電話 fork's** releases come first, newest first; the
**upstream Fossify Phone** changelog follows below, exactly as upstream maintains it.

## 白い熊 電話 1.11.1+065 — 2026-08-29
Built on Fossify Phone 1.11.1.

### Added
- **A nuisance caller can be blocked from the incoming‑call screen.** Declining left the number free
  to ring again a minute later; blocking it meant letting the call ring out, opening Recents,
  long‑pressing the entry and picking *Block number*. A **Block** button now sits beside *Silence*
  above the answer row, and one tap rejects the call **and** writes the number to the system
  blocked‑numbers list — the two steps that always went together are one gesture.
- **The button is a red octagon**, the stop‑sign silhouette, and the only control on that screen
  which is not a circle. That matters more there than house style: it is the one button whose effect
  outlives the call, and it must not be taken for *Decline* by a thumb moving fast. It takes the
  existing *Decline call* theme slot rather than a hardcoded red, so it follows the palette like
  everything else, and it **hides itself when the caller ID is withheld**, since a call carrying no
  number gives the blocked‑numbers provider nothing to store.
- **An undo bar holds the screen for six seconds afterwards.** The block itself is written straight
  away rather than held for that window — if the screen is torn down early the caller stays blocked,
  which is what the tap asked for — and **UNDO** deletes the entry again. The call cannot be
  un‑rejected and the bar does not pretend otherwise. `CallActivity` finishes the instant a ringing
  call is rejected, so the bar has to own the finish while it is up: `endCall()` and
  `safeFinishAndRemoveTask()` both defer to it.
- The number blocked is taken from the **telecom handle**, not from the number on screen, which is
  reformatted when *Format phone numbers* is on.

## 白い熊 電話 1.11.1+064 — 2026-08-27
Built on Fossify Phone 1.11.1.

### Added
- **The keypad opens over the call log instead of leaving it.** Pressing the dial‑pad button on the
  Recents tab used to hand you a separate screen listing contacts, so the calls you were looking at
  vanished exactly when you started typing the number of one of them. The keypad now slides up over
  Recents and the list filters underneath it as you type: **matching calls first**, then, under a
  heading, the **contacts that match but have not called you**. A contact already present among the
  matched calls is not listed twice. Tapping either kind of row calls it; back closes the panel and
  puts the full history back, as does switching tabs.
- **The pad pulls down to a dial line.** Drag the keypad downward and it folds away, leaving only
  the dial line at the bottom — the number, a backspace, a call button and the pull‑down toggle at
  the far right. What you typed stays there and **keeps filtering**, with the whole screen above it
  for results, and the call button on the line means that number can still be dialled without
  bringing the pad back. Drag up on the line, or tap the toggle or the line itself, to restore it.
  The gesture is taken in `onInterceptTouchEvent`, before the keys — which swallow their own touches
  — ever see it, and only once a finger has travelled twice the touch slop and more vertically than
  horizontally, so a tap still types a digit.
- **Name matching on the keypad is shared.** Typing `2665` finds "Bonk" both in the panel over
  Recents and on the Dial‑pad screen, through one `T9Helper` rather than two implementations that
  could drift.
- **Secret codes are fired, not dialled.** Typing `*#*#2432546#*#*` placed a real call and earned
  the operator's "this number does not exist". The dispatch to Telephony already worked, but nothing
  about a secret code is visible, so the natural next move was to press the green button — which
  dialled the literal string. The code is now fired from the call button and the SIM‑selector long
  press as well as from the text watcher, so pressing call on one can never place a call, a **toast
  confirms it went out**, and the field clears a second later so nothing is left to dial.

### Changed
- The keypad, dial line and call button live in **one shared layout driven by one controller**, used
  by both the panel over Recents and the Dial‑pad screen, which dropped from 508 lines to 178. The
  keys, the tones, the haptics, speed dial, the SIM‑selector long press and the secret codes cannot
  drift between the two.
- The **Dial‑pad screen keeps its contact list** and does not collapse. It is now reached only by the
  launcher shortcut and by `ACTION_DIAL` from other apps, where a pre‑filled number and a contact
  search are the point.

### Fixed
- **The missed‑call count no longer grows back after every reboot.** A missed‑call notification
  returned on each boot, only ever larger — 50 of them by the end — however many times the app had
  been opened and the Recents tab visited. Telecom rebuilds that notification at boot from every
  call‑log row still flagged `NEW=1`; there were 51 such rows on the phone, going back two months.
  Opening Recents left the bookkeeping to Telecom, whose `clearMissedCalls()` is supposed to write
  `NEW=0, IS_READ=1` before cancelling — but **EMUI's version returns early**
  (`missCallNumberCount should not be null.`) because Huawei only fills that per‑number tally when
  its own notifier drew the notification, and this app holds the dialer role. The flags were
  therefore never cleared, on any build. The app now writes them itself and keeps the Telecom call
  as a best‑effort extra.
- **A missed‑call notification no longer names an already‑acknowledged caller.** It now takes the
  newest *unread* missed call for the name and time, falling back to the unfiltered query only for
  the moment when Telecom has broadcast but not yet written the call‑log row.

## 白い熊 電話 1.11.1+059 — 2026-08-16
Built on Fossify Phone 1.11.1.

### Fixed
- **The selection toolbar follows the theme everywhere.** Long-pressing a call raised the stock
  dark-grey bar with a white "1 / N" counter and a grey back arrow. Commons paints that bar in code
  for one of its two list adapters only, and the call log uses the other one, so Recents kept the
  stock look while Contacts and Favourites were already black and yellow. Every contextual bar in
  the app is now painted from the granular slots — counter from *Menu text*, action icons, back
  arrow and overflow dots from *Menu icon*, the app background behind the bar — and repainted on
  every selection change.
- **Menu item text is no longer white.** "Call from SIM 1", "Block number", "Copy number to
  clipboard", "Select all" and the rest were drawn in the platform theme's text colour on our black
  popup surface, because a menu popup takes its title colour from the theme and no runtime theming
  reaches it. Every contextual menu now carries the *Menu text* colour on its titles, as the top bar
  and the per-call "⋮" menu already did.
- The same applies to **the overflow menu on the other screens** — Settings' "Calling accounts" and
  the sub-screens the fork does not own follow the *Menu text* colour too. Items shown in the bar
  itself carry an icon and no text, so only the popup is affected.

## 白い熊 電話 1.11.1+058 — 2026-08-11
Built on Fossify Phone 1.11.1.

### Added
- **Silence the ringer without rejecting the call.** A **Silence** button on the incoming-call
  screen — centred above Decline and Accept, the same 72 dp circle in the fork's own idiom: app
  background fill, a 2 dp ring and a bell-off glyph in the theme's primary colour, "Silence"
  underneath. It stops the ringtone and the ringing vibration through
  `TelecomManager.silenceRinger()` while the call keeps ringing for the caller, so it can still be
  answered or left to voicemail. Once used, the button dims to "Silenced".
- **Either volume key silences a ringing call.** Which layer gets the key depends on the ROM — AOSP's
  window manager silences the ringer itself and never delivers it, EMUI keeps it for its own volume
  panel — so this is covered from both ends: `CallActivity.dispatchKeyEvent` handles a key that does
  reach the app, and `CallService` watches the ring, notification and media volumes for a change
  while a call is ringing, silences on one, and then **restores the volume it was ringing at**.
- The button reflects a silence the app did not trigger: `CallService.onSilenceRinger()` feeds
  Telecom's own callback back into `CallManager`, which owns the silenced flag and clears it per
  call, so a second incoming call rings again.

### Known limitation
- On a phone where another app grabs the volume keys below the Android input framework (白い熊's
  handset: 自由作業盤's Shizuku key grabber `EVIOCGRAB`s the volume nodes and consumes short presses
  while the screen is on), no key event and no volume change ever escape, so only the on-screen
  button works. That gate has to be opened in the grabbing app.

## 白い熊 電話 1.11.1+055 — 2026-08-11
Built on Fossify Phone 1.11.1.

### Fixed
- **In-call DTMF tones now reach automated menus.** Digits were transmitted as a blind 150 ms burst
  scheduled on a fresh handler per press: holding a key made no difference, and a digit pressed
  within 150 ms of the previous one was cut short by the earlier press's timer, often below the
  duration an IVR gateway will detect. The tone now starts on key-down and lasts until key-up, with
  a 250 ms floor so a quick tap still transmits a solid digit.
- Digits pressed while another is still on air are **queued and sent in full** after a 100 ms gap
  instead of clobbering the one in flight; the pending stop is cancellable, and the DTMF state is
  reset when the call ends so no stray stop can land on the next call.
- Characters that are not DTMF digits are no longer handed to the framework to be silently dropped.

### Added
- The in-call keypad **plays a local tone** when a key is pressed, honouring the existing dialpad
  beeps setting. Previously only the pre-call dialpad made any sound.

### Changed
- **The dial-pad is a real dial-pad.** Every key is a fixed 80 dp square painted as a circle ringed
  in the theme's primary colour, and the 3×4 grid is packed around the centre column instead of
  being stretched edge to edge. Digits and their letters are vertically centred in their circle.
  Both the dialer and the in-call pad share the new layout.
- The dial button sits 14 dp lower, and the pad's height is unchanged from the previous stretched
  layout (key size and gap are tuned together to keep it 344 dp tall).
- Long-pressing 0 during a call no longer appends a `+`: it is not a DTMF digit, and with
  press-and-hold it would transmit a spurious 0 first. The pre-call dialpad keeps `+`.

## 白い熊 電話 — fork baseline (releases up to 1.11.1+051)
Everything this fork adds to stock Fossify Phone, as it stood before this changelog was started.

### Major features
- **Per-contact default SIM** for outgoing calls, read from our Contacts fork (白い熊 連絡先); the
  dialer honours it and a SIM is always resolved for SIM-less calls.
- **Android Auto obeys the per-contact SIM.** The app takes the `CALL_REDIRECTION` role and swaps
  the SIM back to the contact's own just before the call goes out, so calls placed from the car use
  the right SIM with no prompt.
- **Swipe to dial per SIM** from the call log — left for SIM 1, right for SIM 2, with SIM-coloured
  swipe backgrounds.
- **Hand-off to 連絡先**: tapping or swiping the Contacts/Favourites tabs opens our Contacts fork on
  the matching tab.
- **One-zip backup & restore** via an Export/Import panel, plus the 保存復元 automation contract —
  a cancellable export that reports a per-category default in `LIST_CATEGORIES`.
- Tap a call-log entry to **filter recent calls to that contact**.

### UI & theming
- A **granular theming system** with per-slot colours for the foundation, search bar, top bar and
  menus, tabs, call log, dialpad, in-call screen, contacts and favourites, seeded to black + pure
  `#FFFF00`.
- **Per-element fonts** (family / weight / size with a live sample) and an alpha colour picker.
- Custom Primary colour picker and SIM 1 / SIM 2 colour pickers.
- Call log: day line above the date, date underline, configurable thin-call and thick-day divider
  thicknesses, themeable date header, and colours that refresh on return from settings.
- Configurable call-log **time and duration formats** with Japanese kanji defaults, and optional
  **和暦 (imperial-era) dates**.
- Black/yellow pop-up menus (top bar and per-item "⋮" alike), a yellow **frame around every dialog**,
  and themed toasts in place of the system's white bubble.
- A 設定 shortcut in the search bar, and a long-press on the overflow button opens the UI page.
- Custom launcher icon (black field, yellow-traced handset) and a custom icon for unknown callers.
- Settings consolidated into one indented 白い熊 UI page, with reorganised sections.

### Fixes & behaviour
- Fixed outgoing calls failing with "No valid app found": Commons pins the call intent to
  `org.fossify.phone`, which is not our app id — overridden in-app in `CallExt.kt`.
- Fixed swipe-to-call leaving a stuck coloured band and ghost blank rows.
- Fixed the SIM indicator on Huawei devices, and set a Japanese app label.
- Missed calls post our own notification using the configurable time format.

### Packaging
- Forked as `shiroikuma.denwa`, installing side by side with Fossify Phone.
- Builds against our **patched Fossify Commons** (`6.1.6-sk7`, from `mavenLocal`), which strips the
  upstream anti-tamper "fake version" dialog and fixes the spots where Commons hard-codes
  `org.fossify.*`; the app itself carries no sideloading workaround.
- `BUILD_NUMBER` auto-increments on every `buildFoss` run, zero-padded to three digits so builds and
  tags sort in order.

---

# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.11.1] - 2026-02-01
### Changed
- Updated translations

### Fixed
- Fixed truncated long names in call history ([#157])
- Fixed truncated long names on call screen ([#286])

## [1.11.0] - 2026-01-30
### Added
- Added support for custom fonts
- Option to choose contact click action ([#561])

### Changed
- Updated translations

### Fixed
- Fixed incorrect spacing between prefix and last name
- Fixed issues with unknown number blocking in some cases ([#696])

## [1.10.0] - 2025-12-16
### Changed
- Updated translations

### Fixed
- Fixed overlap between the call screen avatar and the camera notch ([#645])
- Fixed overlap between the call-on-hold banner and the status bar
- Fixed search highlighting for characters with accents and diacritics

## [1.9.0] - 2025-11-03
### Added
- Ability to create contact by clicking thumbnail in call history ([#631])

### Changed
- Updated translations

### Fixed
- Fixed invisible status bar icons in calls ([#628])

## [1.8.0] - 2025-10-29
### Changed
- Compatibility updates for Android 15 & 16
- Updated translations

### Fixed
- Fixed incoming call screen hidden by lock screen ([#165])

## [1.7.3] - 2025-10-16
### Changed
- Updated translations

### Fixed
- Fixed crash in call history
- Fixed custom sorting in favorites not taking effect until app restart ([#389])

## [1.7.2] - 2025-10-01
### Changed
- Updated translations

### Fixed
- Fixed wrong contact photo in call history for some contacts ([#585])
- Fixed hidden/private number detection in call history ([#594])
- Fixed search not matching full phone numbers

## [1.7.1] - 2025-09-12
### Changed
- Updated translations

### Fixed
- Fixed USSD code handling in speed dial ([#565])
- Fixed contact number selection on the dial pad screen

## [1.7.0] - 2025-09-01
### Added
- Option to launch system Calling accounts screen ([#67])

### Changed
- Tapping a contact now starts a call; tap the photo for details ([#80])
- Improved speed dial management UX for contacts with multiple numbers
- Updated translations

### Fixed
- Fixed speed dial not showing contact name ([#543])

## [1.6.2] - 2025-08-23
### Changed
- Renamed notification channels to be more user-friendly ([#196])
- Updated translations

### Fixed
- Fixed missing phone number in call history details ([#526])
- Fixed incorrect sorting in call history search results ([#535])
- Fixed frequent crashes in call history ([#378])

## [1.6.1] - 2025-07-31
### Changed
- Updated translations

### Fixed
- It's now possible to unset custom SIM preferences ([#293])

## [1.6.0] - 2025-07-11
### Changed
- Dialpad screen now respects the default SIM preference ([#50])
- Updated translations

## [1.5.1] - 2025-06-17
### Changed
- Updated translations

### Fixed
- Fixed crash when searching in call history ([#378])

## [1.5.0] - 2025-06-06
### Added
- Backspace button on call screen dialpad

### Changed
- SIM indicators now use system-defined colors
- Search query is now preserved when switching tabs ([#94])
- Updated translations

### Fixed
- Calling from the favorites grid view now works as expected ([#357])
- Fixed phone number text direction in RTL layout ([#307])
- Fixed incorrect colors on conference call screen ([#359])

## [1.4.0] - 2025-04-01
### Added
- Added support for caller location (state/country) in call history ([#39])
- Added option to open contact details when contact photo is tapped ([#35])

### Changed
- Other minor bug fixes and improvements
- Added more translations

### Removed
- Removed storage permission requirement

## [1.3.1] - 2025-01-14
### Changed
- Other minor bug fixes and improvements
- Added more translations

### Fixed
- Fixed an issue where call history wasn't refreshing ([#183])
- Fixed index letter sorting in the contacts list ([#186])
- Fixed dialpad search for some characters ([#139])
- Fixed an issue where call hangs up immediately after back press ([#237])

## [1.3.0] - 2024-10-30
### Changed
- Replaced checkboxes with switches
- Other minor bug fixes and improvements
- Added more translations

### Removed
- Removed support for Android 7 and older versions

### Fixed
- Fixed issue with contacts not displaying on Android 14 and above

## [1.2.0] - 2024-05-08
### Added
- Grouped call history entries by date ([#96])
- Added an option to format phone numbers in the call log

### Changed
- Missed call notifications are now automatically dismissed when you view your call history ([#88])
- Moved some actions back into the popup menu to reduce visual clutter
- Updated menu design for better UI/UX
- Disabled call action buttons after a call ends for better UI/UX ([#181])
- Always show the date in the call details dialog ([#133])
- Updated call direction icons and colors in the call history for better clarity ([#81])
- Restructured the in-call UI to be more responsive to different screen sizes ([#147])
- Added some translations

### Fixed
- Fixed an issue where call history wasn't refreshing ([#146])
- Fixed a problem where search items would disappear ([#98])
- Fixed UI freeze that happened when loading call history
- Fixed a bug that caused search not to find older call logs ([#97])
- Fixed a crash that occurred when using the dialpad quick callback feature

## [1.1.1] - 2024-03-21
### Added
- Added quick dial-back feature ([#60])
- Added placeholder avatar for unknown numbers and contacts without photo
- Added a progress indicator to indicate call history retrieval
- Added bottom padding in lists to allow scrolling above the floating action button

### Changed
- The hang-up button is now always visible in the call UI ([#9])
- Enhanced the size of caller avatar and buttons in the call UI ([#118])
- Reorganized dialpad preferences into their own dedicated section ([#116])
- Added some translations

### Removed
- Removed call history limit ([#125])

## [1.1.0] - 2024-03-21
### Added
- Added quick dial-back feature ([#60])
- Added placeholder avatar for unknown numbers and contacts without photo
- Added a progress indicator to indicate call history retrieval
- Added bottom padding in lists to allow scrolling above the floating action button

### Changed
- The hang-up button is now always visible in the call UI ([#9])
- Enhanced the size of caller avatar and buttons in the call UI ([#118])
- Reorganized dialpad preferences into their own dedicated section ([#116])
- Added some translations

### Removed
- Removed call history limit ([#125])

## [1.0.0] - 2024-01-15
### Added
- Initial release

[#9]: https://github.com/FossifyOrg/Phone/issues/9
[#35]: https://github.com/FossifyOrg/Phone/issues/35
[#39]: https://github.com/FossifyOrg/Phone/issues/39
[#50]: https://github.com/FossifyOrg/Phone/issues/50
[#60]: https://github.com/FossifyOrg/Phone/issues/60
[#67]: https://github.com/FossifyOrg/Phone/issues/67
[#80]: https://github.com/FossifyOrg/Phone/issues/80
[#81]: https://github.com/FossifyOrg/Phone/issues/81
[#88]: https://github.com/FossifyOrg/Phone/issues/88
[#94]: https://github.com/FossifyOrg/Phone/issues/94
[#96]: https://github.com/FossifyOrg/Phone/issues/96
[#97]: https://github.com/FossifyOrg/Phone/issues/97
[#98]: https://github.com/FossifyOrg/Phone/issues/98
[#116]: https://github.com/FossifyOrg/Phone/issues/116
[#118]: https://github.com/FossifyOrg/Phone/issues/118
[#125]: https://github.com/FossifyOrg/Phone/issues/125
[#133]: https://github.com/FossifyOrg/Phone/issues/133
[#139]: https://github.com/FossifyOrg/Phone/issues/139
[#146]: https://github.com/FossifyOrg/Phone/issues/146
[#147]: https://github.com/FossifyOrg/Phone/issues/147
[#157]: https://github.com/FossifyOrg/Phone/issues/157
[#165]: https://github.com/FossifyOrg/Phone/issues/165
[#181]: https://github.com/FossifyOrg/Phone/issues/181
[#183]: https://github.com/FossifyOrg/Phone/issues/183
[#186]: https://github.com/FossifyOrg/Phone/issues/186
[#196]: https://github.com/FossifyOrg/Phone/issues/196
[#237]: https://github.com/FossifyOrg/Phone/issues/237
[#286]: https://github.com/FossifyOrg/Phone/issues/286
[#293]: https://github.com/FossifyOrg/Phone/issues/293
[#307]: https://github.com/FossifyOrg/Phone/issues/307
[#357]: https://github.com/FossifyOrg/Phone/issues/357
[#359]: https://github.com/FossifyOrg/Phone/issues/359
[#378]: https://github.com/FossifyOrg/Phone/issues/378
[#389]: https://github.com/FossifyOrg/Phone/issues/389
[#526]: https://github.com/FossifyOrg/Phone/issues/526
[#535]: https://github.com/FossifyOrg/Phone/issues/535
[#543]: https://github.com/FossifyOrg/Phone/issues/543
[#561]: https://github.com/FossifyOrg/Phone/issues/561
[#565]: https://github.com/FossifyOrg/Phone/issues/565
[#585]: https://github.com/FossifyOrg/Phone/issues/585
[#594]: https://github.com/FossifyOrg/Phone/issues/594
[#628]: https://github.com/FossifyOrg/Phone/issues/628
[#631]: https://github.com/FossifyOrg/Phone/issues/631
[#645]: https://github.com/FossifyOrg/Phone/issues/645
[#696]: https://github.com/FossifyOrg/Phone/issues/696

[Unreleased]: https://github.com/FossifyOrg/Phone/compare/1.11.1...HEAD
[1.11.1]: https://github.com/FossifyOrg/Phone/compare/1.11.0...1.11.1
[1.11.0]: https://github.com/FossifyOrg/Phone/compare/1.10.0...1.11.0
[1.10.0]: https://github.com/FossifyOrg/Phone/compare/1.9.0...1.10.0
[1.9.0]: https://github.com/FossifyOrg/Phone/compare/1.8.0...1.9.0
[1.8.0]: https://github.com/FossifyOrg/Phone/compare/1.7.3...1.8.0
[1.7.3]: https://github.com/FossifyOrg/Phone/compare/1.7.2...1.7.3
[1.7.2]: https://github.com/FossifyOrg/Phone/compare/1.7.1...1.7.2
[1.7.1]: https://github.com/FossifyOrg/Phone/compare/1.7.0...1.7.1
[1.7.0]: https://github.com/FossifyOrg/Phone/compare/1.6.2...1.7.0
[1.6.2]: https://github.com/FossifyOrg/Phone/compare/1.6.1...1.6.2
[1.6.1]: https://github.com/FossifyOrg/Phone/compare/1.6.0...1.6.1
[1.6.0]: https://github.com/FossifyOrg/Phone/compare/1.5.1...1.6.0
[1.5.1]: https://github.com/FossifyOrg/Phone/compare/1.5.0...1.5.1
[1.5.0]: https://github.com/FossifyOrg/Phone/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Phone/compare/1.3.1...1.4.0
[1.3.1]: https://github.com/FossifyOrg/Phone/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/FossifyOrg/Phone/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Phone/compare/1.1.1...1.2.0
[1.1.1]: https://github.com/FossifyOrg/Phone/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/FossifyOrg/Phone/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/FossifyOrg/Phone/releases/tag/1.0.0
