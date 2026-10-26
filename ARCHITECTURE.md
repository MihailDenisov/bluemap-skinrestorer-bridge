# Architecture and compatibility notes

## Data and refresh flow

At each detection pass, the Minecraft thread contributes only immutable online-player UUIDs. A bounded
two-thread queue calls SkinRestorer storage off-thread, classifies the selection, and fingerprints the texture
property. A changed fingerprint advances a per-server-UUID generation and asks the BlueMap adapter to refresh.
The provider decodes `textures.SKIN.url`, loads it through the shared bounded cache, rechecks the generation,
and returns the full skin image. BlueMap's unchanged `DefaultPlayerIconFactory` then creates its normal head
and outer layer and writes it through configured map storage.

SkinRestorer's map is concurrent, but a cache miss invokes `SkinIO.loadSkin` synchronously. Reads are therefore
both off-thread and serialized; the bridge does not infer a wider thread-safety guarantee from the map type.

## BlueMap 5.7 internal adapter

The public API supports `Plugin.getSkinProvider()` and `setSkinProvider(...)`, but has no force-refresh method.
Exact 5.7 bytecode shows `PlayerSkinUpdater.updateSkin(UUID)` is public yet reachable only through implementation
objects, and it suppresses repeat work for one hour in private `skinUpdates`. `BlueMap57Adapter` is the sole
compatibility boundary: it unwraps `PluginImpl`, obtains `getSkinUpdater()`, removes only the target UUID from
that throttle map, and calls `updateSkin`. Refreshes and deletes are ordered per UUID. Clears use public
`BlueMapMap.getAssetStorage().deleteAsset(...)`, so no filesystem layout is assumed.

Recheck after any BlueMap upgrade with:

```powershell
javap -classpath bluemap-5.7-neoforge.jar -p -s -c de.bluecolored.bluemap.common.plugin.skins.PlayerSkinUpdater
javap -classpath bluemap-5.7-neoforge.jar -p -s de.bluecolored.bluemap.common.api.PluginImpl
javap -classpath bluemap-5.7-neoforge.jar -p -s de.bluecolored.bluemap.common.plugin.Plugin
```

Confirm `skinUpdates`, `getPlugin`, `getSkinUpdater`, `updateSkin(UUID)`, the one-hour guard, the
`playerheads/<uuid>.png` key, and public `AssetStorage.deleteAsset`. The runtime check requires version `5.7`
and logs an actionable error rather than attempting a partially compatible install.

## Lifecycle and concurrency

BlueMap API enable/disable callbacks install one wrapper, preserve the original provider, restore it when safe,
and rebuild the adapter after reload. Bridge providers have an active flag so an old lifecycle cannot return a
late image. Ordered chains plus selection generations prevent an older bridge refresh from overtaking a newer
selection or clear. Executors use bounded queues and rejection defers polling rather than running disk work on
the tick thread. Shutdown marks the lifecycle inactive, clears state/cache, interrupts bridge workers, and
restores the previous provider.

## Manual acceptance checklist

On an offline-mode staging server with the exact JARs, verify Mojang, Ely.by, URL/MineSkin, collection,
SkinShuffle, and configured custom selections; switch between them; clear/reset; join with async refresh;
restart; reload BlueMap with players online; share one texture across players; simulate network failure; and
confirm late downloads cannot overwrite a newer selection. Inspect each configured BlueMap storage backend and
the browser through the production proxy. No full render should be needed.
