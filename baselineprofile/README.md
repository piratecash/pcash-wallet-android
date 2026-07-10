# Baseline Profile generation

This module generates the app's [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles)
— the list of hot/startup code paths that ART ahead-of-time compiles on install, making
cold start noticeably faster. The result is bundled into the APK/AAB and consumed
automatically (applied by `profileinstaller` on F-Droid/APK installs, at install time on
Google Play).

The generator drives the real UI with UiAutomator through a full journey:

> cold start (no wallets) → onboarding → **create wallet #1 → create wallet #2** →
> open the coin search → all bottom tabs → **cold restart with two wallets** → switch wallets

It runs against a **throwaway sandbox app** (`cash.p.terminal.baseline`) that creates its own
temporary wallets. **No real wallet data is ever touched** — the test framework uninstalls
the sandbox app when it finishes, which is exactly why generation must never point at a
package that holds real wallets (e.g. `.dev`).

---

## Requirements

- **A connected device or emulator with USB debugging on.** Check with `adb devices` — it
  must be listed. A physical device is fine; an emulator works too.
- **Device language must be English.** The journey taps buttons by their visible English
  text (`Next`, `New Wallet`, `CREATE`, `I Agree`, `Later`). Any other locale breaks it.
- **A screen lock helps.** On a device with *no* lock set, a one-time "system PIN" gate can
  appear before onboarding. Easiest fix on an emulator: `adb shell locksettings set-pin 1234`.
- **Keep the screen on / device unlocked** during the run.

The build itself uses the non-debuggable, release-code `baselinegen` build type, so the
profile reflects real production (AOT) behaviour.

---

## Run it — from the terminal (recommended)

Single entry point:

```bash
./gradlew :app:bakeBaselineProfile
```

If more than one device/emulator is attached, pin the target (serial from `adb devices`):

```bash
ANDROID_SERIAL=<serial> ./gradlew :app:bakeBaselineProfile
```

What it does, end to end:
1. builds and installs `cash.p.terminal.baseline` fresh,
2. runs the journey (~5 iterations — see *Time & iterations* below),
3. collects the ART profile and **writes it to `app/src/main/baseline-prof.txt`**,
4. the framework uninstalls the sandbox app.

---

## Run it — from Android Studio

Use the Gradle tool window (elephant icon):

`pcash-wallet-android → app → Tasks → baseline profile → bakeBaselineProfile` — double-click.

Pick the target device in the top toolbar device dropdown first. Output goes to the same
`app/src/main/baseline-prof.txt`.

> Android Studio may also show a plugin-provided *"Generate Baseline Profile"* action for the
> `:baselineprofile` module. That one writes to `app/src/baselinegen/generated/…` and does
> **not** copy the result into `src/main`. Prefer `bakeBaselineProfile`, which does the copy.

---

## Output

`app/src/main/baseline-prof.txt` — the bundled profile (commit this). It is picked up
automatically by the release build; no extra wiring needed.

Quick sanity check after a run:

```bash
wc -l app/src/main/baseline-prof.txt          # tens of thousands of rules
grep -c 'cash/p/terminal' app/src/main/baseline-prof.txt
```

---

## Time & iterations

- **Generation:** roughly **~8 minutes** on a mid-range physical device (install + 5
  journey iterations + collecting/merging the profile).
- **Build:** add build time on top — a warm/incremental build is a couple of minutes; a
  cold build (clean caches) can be noticeably longer.
- **Total:** budget **~10–20 minutes** depending on build cache.

The profiler repeats the journey to stabilise the result. This app does a lot of network
work on startup (coin sync, prices, market data), so the profile never fully "converges" —
it would otherwise run the default 15 iterations. We cap it at **`maxIterations = 5`** in
`BaselineProfileGenerator.kt`: the code paths are captured well before then, and the extra
iterations only churn on data. The journey is adaptive — wallets are created **only on the
first (fresh) iteration** and reused afterwards, so nothing piles up.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Stuck on onboarding / doesn't create a wallet | Device language isn't **English**. |
| `No matching client found for package name 'cash.p.terminal.baseline'` | `app/google-services.json` is missing the `.baseline` client entry. |
| A "system PIN" screen before onboarding | Device has no screen lock — set one (`adb shell locksettings set-pin 1234`). |
| Wrong / no device targeted | Set `ANDROID_SERIAL=<serial>` (see `adb devices`). |
| Wallet-switch / second-wallet steps skipped | UI/text changed for a tagged screen — see the `testTag`s (`wallet_switcher`, `terms_item`, `onboarding_create_wallet`) and the English button texts used in `BaselineProfileGenerator.kt`. |
