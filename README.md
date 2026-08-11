<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 電話 icon" />

# 白い熊 電話

**A privacy‑friendly dialer, supercharged for dual‑SIM power users.**

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) with **major additions**: a per‑contact default SIM that even **Android Auto** obeys, a full black/yellow theming system, swipe‑to‑call per SIM, a richer call log, one‑zip backup & restore, and a deep hand‑off to our Contacts fork.

Installs **side‑by‑side** with Fossify Phone (app id `shiroikuma.denwa`).

**📥 Latest release: [`1.11.1+055`](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases)

</div>

---

## 📶 Per‑contact SIM — even in Android Auto

Give each contact a default SIM and outgoing calls go out on the right one automatically — **including the calls Android Auto places from the car**, which is the one place a per‑contact SIM normally cannot survive.

Android Auto will not dial at all until a **system‑wide** default calling SIM is set: its only way to resolve the SIM would be a prompt on the handset, which it refuses to raise while driving, so the tap dies silently before it ever reaches Telecom. And once that default *is* set, every call it places arrives with that one SIM already attached, decided long before any dialer gets a say.

So this fork takes the `CALL_REDIRECTION` role and swaps the SIM back to the contact's own in the moment before the call goes out — the car obeys the same per‑contact choice the phone does, with no prompt and no stalling. On the phone itself the SIM picker still appears for contacts you haven't given a SIM to, rather than quietly deferring to that new device‑wide default. (You set the per‑contact SIM in our Contacts fork — long‑press a contact → *Set default SIM*.)

---

## 🎨 Granular black & yellow theming

A full theming page with **per‑slot colours** for the dialpad, in‑call screen, contacts, favourites and call log; **per‑element fonts** (family / weight / size with a live sample); an alpha colour picker; and SIM 1 / SIM 2 colour pickers. It seeds to pure black + pure **#FFFF00** yellow — and it reaches the parts a theme usually can't: the selection toolbar, **every pop‑up menu** (one black surface with a yellow border, top bar and per‑call "⋮" alike), a **yellow frame around every dialog**, which is the only thing giving a dialog an edge against a black screen, and even the **toasts** — the little "Value copied to clipboard" flashes come up black‑on‑yellow instead of the system's white bubble.

---

## ☎️ A real dial‑pad — and tones the far end actually hears

The keypad is a proper dial‑pad: **round keys ringed in your accent colour**, sized to the dial button and packed into a 3×4 block around the centre column, instead of pill‑shaped cells stretched across the whole screen. Both pads get it — the dialer and the one that slides up mid‑call.

Behind it, the in‑call keys were **not reliably reaching automated menus**. Stock fires a fixed 150 ms tone burst regardless of how long you hold the key, and a second digit pressed too soon gets cut short by the first one's timer — which is exactly how a hotline menu ends up ignoring you. Now the tone lasts **as long as you hold the key**, never less than 250 ms, and digits pressed in quick succession are **queued and sent in full** rather than clobbering each other. The in‑call pad also **beeps** when you press a key, which it never did before.

---

## ↔️ Swipe to call on the right SIM

Swipe a recent call **left for SIM 1, right for SIM 2** to dial instantly — the swipe backgrounds use your SIM colours.

---

## 🤝 Hands the Contacts & Favourites tabs to 連絡先

With our Contacts fork (白い熊 連絡先) installed, tapping or swiping the Contacts/Favourites tabs opens it on the matching tab — one contacts experience shared across both apps.

---

## 🕓 A richer call log

Day headers with an underline, configurable thin‑call / thick‑day dividers, Japanese **kanji time & duration formats**, optional **和暦 (imperial‑era) dates**, a themeable date header, a custom icon for unknown callers, and tap‑to‑filter a single contact's recent calls — plus missed‑call notifications with your chosen time format.

---

## 💾 Back up everything in one zip — and restore just the parts you want

An **Export / Import** page at the top of the UI screen writes every setting the app has — behaviour, speed dial, the per‑contact SIM choices, and the whole black/yellow theme with your imported fonts — into a single dated `.zip`. Tick only what you want on the way back in: each category, and its sub‑parts, restore independently, and a backup taken by an older build still restores into a newer one.

Pick a backup folder once and the page tells you, every time you open it, when this app was last saved.

---

## 🤖 One command backs up every 白い熊 app

The dialer answers the family's **保存復元** automation contract, so 白い熊 自由作業盤 can back it up headlessly alongside every sister app in a single run — no screens, no taps. It reports real progress counts while it works and replies with the exact path and size it wrote. Off by default and gated by a per‑device token you copy with one tap.

It also **names the items it can save and says which should start ticked**, so the picker you see is the app's own answer rather than a guess — and a run in progress can be **stopped from outside**: the cancel unwinds the export at the next safe boundary and deletes what it had written, leaving your backup folder exactly as it found it.

---

## Built on Fossify Phone

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) (app id `shiroikuma.denwa`, so it coexists with the official build). Fossify Phone is a privacy‑focused, open‑source dialer free of ads and trackers — all upstream work and its mission belong to the Fossify team. The code remains under the **GNU GPL v3.0**.

It builds against a lightly‑patched [Fossify Commons](https://github.com/ShiroiKuma0/shiroikuma-commons) (anti‑tamper checks removed so custom‑signed builds run, plus fork‑package fixes and the black/yellow toolbar, menus & toasts).

## Building

```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-denwa.git
cd shiroikuma-denwa
./gradlew assembleFossRelease   # a signed release needs keystore.properties
```

Builds resolve our patched Commons from `mavenLocal()` — see this repo's `CLAUDE.md` and the [commons fork](https://github.com/ShiroiKuma0/shiroikuma-commons) for publishing it.
