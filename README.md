<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 電話 icon" />

# 白い熊 電話

**A privacy‑friendly dialer, supercharged for dual‑SIM power users.**

A fork of [Fossify Phone](https://github.com/FossifyOrg/Phone) with **major additions**: a per‑contact default SIM that even **Android Auto** obeys, a full black/yellow theming system, swipe‑to‑call per SIM, a richer call log, a one‑zip backup that carries your call history and blocked numbers and cannot lose them on the way back, and a deep hand‑off to our Contacts fork.

Installs **side‑by‑side** with Fossify Phone (app id `shiroikuma.denwa`).

**📥 Latest release: [`1.11.1+072`](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-denwa/releases)

</div>

---

## 📶 Per‑contact SIM — even in Android Auto

Give each contact a default SIM and outgoing calls go out on the right one automatically — **including the calls Android Auto places from the car**, which is the one place a per‑contact SIM normally cannot survive.

Android Auto will not dial at all until a **system‑wide** default calling SIM is set: its only way to resolve the SIM would be a prompt on the handset, which it refuses to raise while driving, so the tap dies silently before it ever reaches Telecom. And once that default *is* set, every call it places arrives with that one SIM already attached, decided long before any dialer gets a say.

So this fork takes the `CALL_REDIRECTION` role and swaps the SIM back to the contact's own in the moment before the call goes out — the car obeys the same per‑contact choice the phone does, with no prompt and no stalling. On the phone itself the SIM picker still appears for contacts you haven't given a SIM to, rather than quietly deferring to that new device‑wide default. (You set the per‑contact SIM in our Contacts fork — long‑press a contact → *Set default SIM*.)

---

## 🎨 Granular black & yellow theming

A full theming page with **per‑slot colours** for the dialpad, in‑call screen, contacts, favourites and call log; **per‑element fonts** (family / weight / size with a live sample); an alpha colour picker; and SIM 1 / SIM 2 colour pickers. It seeds to pure black + pure **#FFFF00** yellow — and it reaches the parts a theme usually can't: **every selection toolbar** — the bar, its "1 / N" counter, the back arrow and the text of the menu it opens — **every pop‑up menu** (one black surface with a yellow border, top bar and per‑call "⋮" alike, with every item title in your colour rather than the platform's white), a **yellow frame around every dialog**, which is the only thing giving a dialog an edge against a black screen, and even the **toasts** — the little "Value copied to clipboard" flashes come up black‑on‑yellow instead of the system's white bubble.

---

## ☎️ A real dial‑pad — and tones the far end actually hears

The keypad is a proper dial‑pad: **round keys ringed in your accent colour**, sized to the dial button and packed into a 3×4 block around the centre column, instead of pill‑shaped cells stretched across the whole screen. Both pads get it — the dialer and the one that slides up mid‑call.

Behind it, the in‑call keys were **not reliably reaching automated menus**. Stock fires a fixed 150 ms tone burst regardless of how long you hold the key, and a second digit pressed too soon gets cut short by the first one's timer — which is exactly how a hotline menu ends up ignoring you. Now the tone lasts **as long as you hold the key**, never less than 250 ms, and digits pressed in quick succession are **queued and sent in full** rather than clobbering each other. The in‑call pad also **beeps** when you press a key, which it never did before.

Codes like `*#*#2432546#*#*` are **fired as codes, not dialled as numbers** — a toast confirms the code went out, and pressing the green button on one can no longer earn you an operator telling you the number does not exist.

---

## 🔎 The keypad filters the calls you're looking at

Press the dial‑pad button on **Recents** and the keypad rises over the call log rather than taking you to another screen — the calls stay where they are and **narrow as you type**. Matching calls come first; under a heading below them, the **contacts who match but have never called you**, so a number you have dialled before and a number you have never dialled are one gesture apart. Tap either to call it.

When the pad is in the way, **pull it down**. It folds to a single dial line at the bottom — your number, a backspace, a call button and the toggle — and **keeps filtering** from there, with the entire screen above it for results. The call button stays on the line, so a number typed there can be dialled without bringing the pad back. Pull up on the line, or tap it, and the keypad returns.

---

## 🔕 Let it ring — quietly

Not every call you want to take is one you want to hear. A **Silence** button sits on the incoming‑call screen, above Decline and Accept: it stops the ringtone and the vibration while the call **keeps ringing for the caller**, so you can still pick it up a moment later, or let it go to voicemail without ever declining it. Either **volume key** does the same thing, and the volume you were ringing at is put back afterwards — you asked for quiet, not for a quieter phone from now on.

---

## 🛑 Block a nuisance caller in one tap

Declining a nuisance call leaves the number free to ring again a minute later, and blocking it used
to mean letting the call ring out, opening Recents, long‑pressing the entry and picking *Block
number*. A **Block** button now sits beside *Silence* on the incoming‑call screen: one tap hangs up
**and** adds the caller to the blocked list.

It is a red octagon — the stop‑sign silhouette, and the only control on that screen which is not a
circle, because it is the one button whose effect outlives the call and must never be taken for
*Decline* by a thumb moving fast. An **undo bar** follows for six seconds in case it was, and the
button quietly disappears for callers who withhold their number, since there is nothing to block.

---

## ↔️ Swipe to call on the right SIM

Swipe a recent call **left for SIM 1, right for SIM 2** to dial instantly — the swipe backgrounds use your SIM colours.

---

## 🤝 Two apps, one bottom bar

With our Contacts fork (白い熊 連絡先) installed, tapping or swiping the Contacts/Favourites tabs opens it on the matching tab — one contacts experience shared across both apps.

And the tab bar **keeps the dialer's shape while you are over there**: 連絡先 comes up wearing *this* app's tabs — Contacts | Favourites | Recents, its own Groups tab out of the way — so the call log is always one tap away instead of something you have to back out of another app to reach. Hide Favourites here and it disappears there too; the two bars never disagree. Neither app closes the other, so after the first trip the two sit warm side by side and a tap swaps them instantly, with no launch animation to sit through. (Requires 白い熊 連絡先 1.6.0+083 or newer.)

---

## 🕓 A richer call log

Day headers with an underline, configurable thin‑call / thick‑day dividers, Japanese **kanji time & duration formats**, optional **和暦 (imperial‑era) dates**, a themeable date header, a custom icon for unknown callers, and tap‑to‑filter a single contact's recent calls — plus missed‑call notifications with your chosen time format.

---

## 💾 Back up everything in one zip — and restore just the parts you want

An **Export / Import** page at the top of the UI screen writes everything the app is set to *and everything it holds* — behaviour, speed dial, the per‑contact SIM choices, your **call history**, your **blocked numbers**, and the whole black/yellow theme with your imported fonts — into a single dated `.zip`. Tick only what you want on the way back in: each category, and its sub‑parts, restore independently, and a backup taken by an older build still restores into a newer one.

The call log is dumped as it really is, not as the Recents list shows it — which matters more than it sounds, because that list **hides calls from blocked numbers**, and a backup built on it would quietly lose exactly the calls from the people you blocked. Restoring deduplicates on the call's own timestamp, so putting a backup back onto a phone that still has part of its log adds what is missing instead of doubling everything, and restored calls arrive already read rather than as a few hundred fresh missed‑call notifications.

Pick a backup folder once and the page tells you, every time you open it, when this app was last saved. And if the blocked numbers cannot be read at the moment you export — they need the dialer role — the export **fails and says so** instead of writing a perfectly good‑looking archive with an empty list inside it.

---

## 🤖 One command backs up every 白い熊 app

The dialer answers the family's **保存復元** automation contract, so 白い熊 自由作業盤 can back it up headlessly alongside every sister app in a single run — no screens, no taps. It reports real progress counts while it works and replies with the exact path and size it wrote. **It is on out of the box and there is nothing to paste**: the authorization token is now an extra you can switch on, not the gate, because a pasted secret cannot survive the wipe this feature exists to recover from.

It also **names the items it can save and says which should start ticked**, so the picker you see is the app's own answer rather than a guess — and a run in progress can be **stopped from outside**: the cancel unwinds the export at the next safe boundary and deletes what it had written, leaving your backup folder exactly as it found it.

---

## 🔐 Restored with its data onto a clean phone

Beyond exporting itself, the dialer opens a **data door** that 白い熊 応用管理 can drive to back the app up *with its data* and put it back on a wiped phone — the case a settings export alone never covered.

It is deliberately not another broadcast, because **a broadcast cannot tell you who sent it** and the caller is the one naming where the backup goes. The door identifies its caller three ways — the exact package name, the uid the kernel reports for it, and a **pinned signing certificate** — and a package-name prefix is explicitly not enough, since any sideloaded app may name itself whatever it likes. Restoring is available **only** through that door, never over the open broadcast surface, because an import overwrites what the app knows.

The backup itself travels through a **file descriptor the caller opens**, never a path: your archive is encrypted and checksummed file by file, and anything written into it from the outside would sit in plaintext and unverified inside an otherwise sealed backup.

---

## 🛟 A restore can be late — it cannot be lost

Your **blocked numbers are irreplaceable**. There is no second copy of that list anywhere and no way to rebuild it, and the whole point of restoring onto a new phone is that the nuisance callers do not simply start getting through again.

But Android only lets the **default dialer** write to the blocked‑numbers list — and on a phone that has just been wiped, nothing is the default dialer yet at the moment your backup arrives. The two obvious ways to handle that both lose the list: fail the whole restore, or skip the numbers and report success over nothing.

So this fork does neither. A restore that cannot apply something **yet** keeps it. The data is written to the app's private storage the instant it arrives, reported as **HELD** rather than restored, and put in the moment it becomes possible — retried every time you open the app, and immediately when you grant the dialer role. Until the last held number is in, opening the app raises a **red‑framed warning that cannot be dismissed away**, naming exactly what is still waiting and how many, with the button that unblocks it right there.

The same holds for the call history, which needs call‑log access for the same reason. Restore first and set things up afterwards, or the other way round — the order stops mattering.

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
