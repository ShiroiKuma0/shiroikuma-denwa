# Changelog — 白い熊 電話

This file carries two histories. The **白い熊 電話 fork's** releases come first, newest first; the
**upstream Fossify Phone** changelog follows below, exactly as upstream maintains it.

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
