# bee-san fork: personal build

This fork is for personal use only. It is not offered upstream, and builds of it are not distributed.
The upstream BDSA code keeps its own license (`LICENSE`). Code added in this fork is licensed GPL-3.0-or-later,
and each vendored app keeps its own GPL license (see `vendor/*/LICENSE`).

## What the fork adds

| Area | Where | Notes |
| :--- | :--- | :--- |
| Name: **Better BDSM** | `res/values/strings.xml` (`app_name`, `app_wordmark_*`) | Launcher label, title and header, so it isn't mistaken for upstream BDSA. Package id is unchanged, so settings and grants carry over. |
| **Extras** quick-toggle group | `system/QuickSettingTileId.kt`, `ui/overlay/AssistantOverlayScreen.kt` | On by default on the bottom-screen panel: PULSE, ThorTune and SleepManager tiles, plus Night Light, Extra Dim and Dark Theme. |
| Night Light / Extra Dim / Dark Theme tiles | `system/SystemSettingsRepository.kt`, `QuickSettingTileId.kt` | Android System Toggles category. |
| Schedules for those three | `app/.../extras/schedule/DisplaySchedules.kt` | Run by the foreground service, which sleeps until the next switch time (at most 30 minutes, never while no schedule is on) and catches up on screen-on. Edge-triggered, so a manual toggle holds until the next switch time. Each can follow sunset to sunrise (`SunTimes.kt`, NOAA formula) for a chosen city (London by default) or a one-off network location lookup, repeated only when the time zone changes (`SunLocation.kt`). |
| **Extras** settings category | `app/.../extras/ExtrasSettings.kt` | Opens the built-in apps and edits schedules. |
| PULSE (CPU/GPU tuning, AutoTDP) | `vendor/pulse` (GPL-2.0) | AutoTDP is on by default. Started from `ExtrasRuntime` 5 s after the service starts. |
| ThorTune (JamesDSP engine + saturation) | `vendor/thortune` (GPL-2.0) | Bundles the JamesDSP Manager APK (`james.dsp`), which is still its own app: it's the engine's control UI. The lower-screen companion panel is off by default. |
| Built-in apps open in BDSA's task from Settings | `extras/BuiltInApps.kt`, `MainActivity.onStop` | BACK returns to Extras; from the bottom panel they get their own task so BACK returns to the game. |
| SleepManager | `vendor/sleepmanager` (GPL-3.0) | Self-updater disabled, and no Compatibility Helper (AYN/Retroid control radios directly). |

The vendored modules are upstream sources with small, marked changes: no launcher entries, no `Application`
classes, renamed provider authorities and FileProvider classes, renamed resources that clashed, and a
`bdsa/` bridge package in each one. `UPSTREAM_COMMIT` records the source commit.

## Build

```sh
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Uninstall the standalone PULSE, ThorTune and SleepManager apps first, so two copies don't fight over CPU limits,
radios or the audio engine.
