# Pinned target artifacts

The project compiles and performs development-server runs against the exact unbundled JARs in the repository
root. `verifyTargetJars` runs before compilation and rejects substitutions.

| Artifact | SHA-256 |
|---|---|
| `bluemap-5.7-neoforge.jar` | `827ac3921986d0d5e9c24ef2280f47c110d22dc14d284efb65330010d2e9517d` |
| `skinrestorer-2.11.0+1.21-neoforge.jar` | `a56b18a7f51f66cdb0010c2a1d4d3b056bb65573351f90eb406974bfacfc4bc8` |

BlueMap's API and refresh implementation and SkinRestorer's storage/property implementation were inspected from
these binaries with `javap`. SkinRestorer 2.11.0 does not contain Maven publication metadata, so the supplied
exact artifact is intentionally a checked local compile/runtime dependency rather than an assumed third-party
Maven coordinate.
