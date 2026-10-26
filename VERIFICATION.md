# Verification report

Date: 2026-10-26 (Europe/Helsinki)

## Passed automated checks

- Java: Eclipse Temurin 21.0.9, class-file major version 65.
- `./gradlew.bat compileJava`: passed.
- `./gradlew.bat test`: passed, 14 tests, 0 failures, 0 errors.
- `./gradlew.bat clean build`: passed with Gradle 9.2.1 and configuration cache enabled.
- `verifyTargetJars`: passed both pinned SHA-256 checks.
- Artifact inspection: exact requested filename, 37,771 bytes, SHA-256
  `0c186336144c34d988179d66950f6e34ff9a99e78d5273c6c81cc69377206977`; neither `de/bluecolored`
  nor `net/lionarius` classes are bundled.

The tests cover texture-property Base64/JSON and URL decoding, missing URLs, alternative hosts and query
parameters, redirect handling, invalid/oversized/wrong-dimension images, cache expiry/invalidation, concurrent
download deduplication, transient recovery, original-provider fallback, explicit clear, terminal failure cleanup,
selection-generation rejection, and provider shutdown invalidation. Network tests use a loopback fixture only.

## Exact-version source/bytecode compatibility checks

The supplied BlueMap 5.7 and SkinRestorer 2.11.0 JARs were inspected with `jar`, `javap -p -s`, and
`javap -p -s -c`. Verified facts used by the implementation include:

- `SkinProvider.load(UUID): Optional<BufferedImage>` and public provider get/set methods.
- BlueMap lifecycle callbacks and the internal 5.7 updater's one-hour UUID throttle, asynchronous work,
  default icon factory, and `playerheads/<uuid>.png` map-storage write.
- Public BlueMap `AssetStorage.deleteAsset` for clear/reset cleanup.
- `SkinRestorer.getSkinStorage()`, nullable `SkinStorage.getSkin(UUID)`, and `SkinValue.value()`.
- `SkinStorage` uses a concurrent map, but a cold read invokes synchronous `SkinIO.loadSkin`.
- SkinRestorer 2.11.0 has no generic public callback for completed skin application/change/clear.

## Dedicated-server smoke check

`./gradlew.bat runServer` loaded Minecraft 1.21.1, NeoForge 21.1.264, BlueMap 5.7, SkinRestorer
2.11.0+1.21-neoforge, and bridge 0.1.0, entered offline mode, generated a world, and reached `Done` without a
bridge classloading or mod-construction error. The local process was then intentionally terminated.

BlueMap did not enable its API because its first-run required-resource-download consent was unset in
`run/config/bluemap/core.conf`. Consequently, provider installation and real asset writes were not exercised
by this smoke run; its forced termination gives the Gradle run task a non-success exit and is not counted as a
passing automated task.

## Not performed

No real player or production BlueMap web app/proxy was available. The 14 manual integration scenarios in the
request remain unverified live: Mojang, Ely.by, URL/MineSkin, collection, SkinShuffle and custom-provider heads;
source switching; asynchronous join replacement; clear/reset in a browser; persistence across real restarts;
BlueMap reload after full enablement; shared live downloads; induced live network failure; late completion;
non-file BlueMap storage; and browser/proxy cache visibility. Compilation and unit tests must not be interpreted
as proof of those live scenarios.
