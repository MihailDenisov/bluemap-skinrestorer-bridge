# BlueMap SkinRestorer Bridge

Server-side NeoForge bridge that makes BlueMap 5.7 use the texture selected by
[Lionarius SkinRestorer](https://modrinth.com/mod/skinrestorer). It is intended for offline-mode
Minecraft 1.21.1 servers and requires no client mod or BlueMap web-app changes.

## Compatibility

- Minecraft 1.21.1, Java 21
- NeoForge 21.1.x (build pinned to 21.1.264)
- `bluemap-5.7-neoforge.jar`
- `skinrestorer-2.11.0+1.21-neoforge.jar`

This does **not** integrate with the unrelated SkinsRestorer Bukkit/Spigot plugin. BlueMap and
SkinRestorer are compile/runtime-only dependencies and are not bundled in the bridge JAR.

## Installation

1. Install the exact BlueMap and SkinRestorer JARs above on a NeoForge 1.21.1 dedicated server.
2. Put `bluemap-skinrestorer-bridge-0.1.0+1.21.1-neoforge.jar` in `mods/`.
3. Start the server. NeoForge creates `config/bluemap_skinrestorer_bridge-common.toml`.

The bridge reads SkinRestorer through `SkinRestorer.getSkinStorage()` and therefore respects its
configured `global`, `world`, or other supported storage location. It never writes SkinRestorer data.

## Configuration

Conservative defaults are also shipped as `bluemap_skinrestorer_bridge-default.toml` inside the JAR.

| Setting | Default | Meaning |
|---|---:|---|
| `enabled` | `true` | Install the provider when BlueMap enables. |
| `changeDetectionSeconds` | `10` | Off-thread polling interval for online players. |
| `connectTimeoutSeconds` | `5` | Texture HTTP connection timeout. |
| `readTimeoutSeconds` | `10` | Whole-request timeout. |
| `maxImageBytes` | `2097152` | Maximum response size. |
| `maxCacheEntries` | `256` | Shared URL-image cache limit. |
| `cacheExpireMinutes` | `60` | Fresh cache lifetime; stale data may be used for one additional lifetime after a temporary failure. |
| `retryCount` | `2` | Additional bounded attempts after the first request. |
| `retryBackoffMillis` | `250` | Initial exponential retry delay. |
| `fallbackPolicy` | `ORIGINAL_PROVIDER` | Use BlueMap's prior provider when no compatible selection can be loaded; `EMPTY` disables it. |
| `debugLogging` | `false` | Log selection state/fingerprint changes without texture payloads. |

## Behavior and fallback policy

- A valid selected texture property is decoded without inspecting its provider name. Mojang, Ely.by,
  URL/MineSkin, collections, SkinShuffle, custom providers, and future providers work when they expose
  the normal Authlib `textures.SKIN.url` representation. Query parameters and HTTP redirects are kept.
- No saved selection delegates to BlueMap's original provider when configured.
- An explicit SkinRestorer empty/clear deletes `playerheads/<server UUID>.png` through each map's public
  `AssetStorage`; it never substitutes the source account UUID.
- Invalid properties are diagnosed with player UUID, provider and a short hash, then use the configured
  fallback. Full Base64 values and signatures are never logged.
- Downloads have bounded retries. A still-valid cached image can survive a short outage for one extra
  cache lifetime. After terminal failure the fallback is used; if it is empty, the stale asset is deleted.
- Player failures are isolated. URL downloads are shared and deduplicated, failed requests are not cached,
  cache size is bounded, and selection generations reject old completions.

SkinRestorer 2.11.0 exposes no generic skin-applied event. The bridge therefore snapshots online UUIDs on
the server thread and performs all storage reads, property decoding, HTTP work, and image decoding on bounded
worker pools. Join triggers an immediate scan; subsequent polling catches asynchronous join application and
commands that change, reset, clear, or switch providers.

## Building and tests

Keep the two exact dependency JARs in the project root, then run:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Tests use injected downloaders and a loopback HTTP server; they do not contact Mojang, Ely.by, or MineSkin.
The output is `build/libs/bluemap-skinrestorer-bridge-0.1.0+1.21.1-neoforge.jar`.
A verified copy of the 0.1.0 binary and its checksum is also published in [`artifacts/`](artifacts/).

## Troubleshooting and limitations

- `BlueMap 5.7 compatibility check failed`: use exactly 5.7 and re-run the bytecode checks documented in
  [ARCHITECTURE.md](ARCHITECTURE.md). The bridge deliberately refuses an unverified BlueMap version.
- A head changes later than expected: allow the polling interval plus download time. Reverse proxies should
  not impose long immutable caching on BlueMap's `playerheads/*.png` assets. BlueMap 5.7 itself overwrites
  the stable asset URL and exposes no explicit browser-cache invalidation API.
- HTTP/HTTPS alternative hosts are allowed. Responses over the configured byte limit, undecodable images,
  images outside 64x32 through 1024x1024, non-HTTP redirects, or more than five redirects are rejected.
- Visibility remains entirely BlueMap-owned. The bridge does not add markers, track locations, or reveal
  hidden players.

Compilation and unit tests do not prove live compatibility with every provider or web proxy. Use the manual
acceptance checklist in [ARCHITECTURE.md](ARCHITECTURE.md) on a staging server after upgrades.
