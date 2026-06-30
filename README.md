<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 電話 icon" />

# 白い熊 電話

**A privacy‑friendly dialer, supercharged for dual‑SIM power users.**

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) with **major additions**: a per‑contact default SIM that even **Android Auto** obeys, a full black/yellow theming system, swipe‑to‑call per SIM, a richer call log, and a deep hand‑off to our Contacts fork.

Installs **side‑by‑side** with Fossify Phone (app id `shiroikuma.denwa`).

**📥 Latest release: [`1.11.1+37`](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases)

</div>

---

## 📶 Per‑contact SIM — even in Android Auto

Give each contact a default SIM and outgoing calls go out on the right one automatically — **including calls placed from Android Auto**, which stock dual‑SIM dialers can't do. Instead of silently stalling on a SIM picker you can't reach in the car, the call just goes through: it uses the contact's chosen SIM, then your saved SIM, then SIM 2 as the default. (You set the per‑contact SIM in our Contacts fork — long‑press a contact → *Set default SIM*.)

---

## 🎨 Granular black & yellow theming

A full theming page with **per‑slot colours** for the dialpad, in‑call screen, contacts, favourites and call log; **per‑element fonts** (family / weight / size with a live sample); an alpha colour picker; and SIM 1 / SIM 2 colour pickers. It seeds to pure black + pure **#FFFF00** yellow — and even the selection toolbar and pop‑up menus follow the theme.

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

## Built on Fossify Phone

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) (app id `shiroikuma.denwa`, so it coexists with the official build). Fossify Phone is a privacy‑focused, open‑source dialer free of ads and trackers — all upstream work and its mission belong to the Fossify team. The code remains under the **GNU GPL v3.0**.

It builds against a lightly‑patched [Fossify Commons](https://github.com/ShiroiKuma0/shiroikuma-commons) (anti‑tamper checks removed so custom‑signed builds run, plus fork‑package fixes and the black/yellow toolbar & menus).

## Building

```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-denwa.git
cd shiroikuma-denwa
./gradlew assembleFossRelease   # a signed release needs keystore.properties
```

Builds resolve our patched Commons from `mavenLocal()` — see this repo's `CLAUDE.md` and the [commons fork](https://github.com/ShiroiKuma0/shiroikuma-commons) for publishing it.
