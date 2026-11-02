# Obtainium Wear

This fork adds one thing to Obtainium: apps can be installed and updated on a
Wear OS watch. Everything (sources, tokens, filters, update checks) stays in
Obtainium on the phone; a small helper app on the watch receives the APKs over
the Wear OS data layer and installs them.

## Setup

1. Install `ObtainiumWear-<version>-arm64.apk` on the phone. It installs next
   to Obtainium (package `dev.imranr.obtainium.wear`); move apps over with
   Obtainium's export and import if you like.
2. Install `ObtainiumWearHelper-<version>.apk` on the watch, the first time
   with adb (or Bugjaeger on the phone):
   `adb install ObtainiumWearHelper-<version>.apk`
3. Open the helper on the watch and tap **Allow installs**. If the watch has
   no such setting, run
   `adb shell appops set dev.imranr.obtainium.wear REQUEST_INSTALL_PACKAGES allow`.
4. In Obtainium Wear, add the app and set its **Install method** (in the app's
   settings) to **Wear OS watch**. For a release that also has a phone APK,
   set an APK filter so it picks the watch one.

Add the helper itself the same way (this repo's releases, filter
`ObtainiumWearHelper`, install method Wear OS watch) and it keeps itself up to
date.

## How it works

- `packages/wear_link`: a Flutter plugin (so background update checks have it
  too) that finds the watch by its `obtainium_wear_helper` capability, asks it
  which apps it has, and sends APKs over `ChannelClient`.
- `wear_helper/`: the watch app. `Protocol.kt` describes the messages. It
  installs with a `PackageInstaller` session that asks for no user action, so
  apps it installed (and itself) update silently; others get a "Tap to
  install" notification on the watch.
- Watch apps are stored as `<package>@watch`, so the same app can be tracked
  for the phone and the watch. Their installed versions come from the watch,
  cached in `watch_packages.json` so an out-of-range watch doesn't make them
  look uninstalled.
- The phone and watch apps must share the application id and signing key, or
  the data layer won't connect them.

## Releases

`.github/workflows/wear.yml` builds both APKs and releases them as
`v<upstream version>-wear<WEAR_REVISION>`. The daily run merges upstream
Obtainium's main first (in the runner only), so upstream releases are picked up
automatically. Bump `WEAR_REVISION` for a fork-only change.

Secrets: `OBTAINIUM_WEAR_KEYSTORE_B64` (`base64 -w0 obtainium-wear-signing.jks`)
and `OBTAINIUM_WEAR_KEYSTORE_PASS`; the key alias is `obtainiumwear`.
