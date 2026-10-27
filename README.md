# Wahoo Kickr HiiT Android app

A dedicated Android app for interval training on a **Wahoo Kickr Core v2** (Zwift Cog + Zwift Click), with no subscription and no third-party service. It talks to the trainer over Bluetooth LE FTMS, sets the target itself, reads a Bluetooth heart-rate strap, and draws the effort against the plan while you ride.

## What it does

- **Three programmes**, written as data, not screens:
  - *SIT 6 × 30″* — six all-out thirty-second sprints on four minutes of recovery, 40 min.
  - *Pyramid Over-Unders* — three over-under cycles either side of threshold, then a pyramid, 45 min, ERG throughout.
  - *Quick SIT 4 × 30″* — the short version, 24 min.
- **The trainer is driven, not asked.** Steady blocks run in **ERG** (the trainer holds the wattage whatever your cadence). Sprints run in **resistance** mode — a fixed brake level, so the power is whatever you can produce against it. Prescribing watts for a maximal effort is a contradiction; the sprint is the number you are trying to find out.
- **Live graph** of target versus actual power, with cadence and heart rate, on a needle dial and an LCD plotter.
- **Metrics that mean something afterwards**: normalised power, intensity factor, training stress, best 30-second power, kJ and kcal.
- **Spoken coaching**: a countdown into every sprint, and the result read back when it ends.
- **Music matched to cadence**: tracks are analysed for tempo, playback is nudged (within a limit you set) so the beat lands on the pedal stroke, and every track is levelled to EBU R128 so none of them startles you in headphones.

<img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_01.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiit_02.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_03.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_04.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_05.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_06.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_07.jpg" width=30% height=30%> <img src="https://github.com/telecomprofi/WahooKickrHiiT/blob/main/img/WahooKickrHiiT_08.jpg" width=30% height=30%>

## Numbers you have to supply

FTP, in Settings. Every target in every workout is a share of it. There is no ramp test in the app: type a figure, ride a session, and adjust it by five watts if the recoveries felt like work or the intervals felt easy.

## Building

Debug build

```
./gradlew test assembleDebug   && adb install -r app/build/outputs/apk/debug/app-debug.apk

```

Release build

```
./gradlew test assembleRelease  && adb install -r app/build/outputs/apk/release/app-release.apk
```

Release signing reads `keystore.properties` at the project root (`storeFile` / `storePassword` / `keyAlias` / `keyPassword`). That file and the keystore it points at are gitignored and must stay that way: publishing either would let anyone ship an update over your installed app.

Debug builds work with no keystore at all: `./gradlew assembleDebug`.

## Requirements

- Android 13 (API 33) or newer.
- A Bluetooth LE trainer speaking FTMS (0x1826). The Kickr Core v2 does.
- Optionally a Bluetooth heart-rate strap (Heart Rate Service 0x180D).
- Demo mode in Settings invents power and heart rate, so the whole app can be tried off the bike.
