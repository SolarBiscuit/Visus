# Changes from Oculus 1.8.0

Required by the LGPL-3.0: a dated record of how Visus differs from the Oculus sources it is based on.
The base is `Asek3/Oculus` branch `1.20.1-new`, commit `5751a2f6b` ("Bump version", 2024-12-24). That commit was
checked against the released `oculus-mc1.20.1-1.8.0.jar`:
- Both jars contain the same 1,645 files.
- 57 classes differ in bytecode. All of those differences are javac-version noise (for example, string
  concatenation now goes through `String.valueOf`), with no difference in logic.

The full history is in git: upstream commits, then Visus commits on top.

## 2026-11-06: Visus 0.1.0 "Baseline"
- **Identity:**
  - Added the mod `visus` (`dev.visus.Visus`).
  - Kept the mod `oculus` (`net.irisshaders.iris.Iris`) as a compatibility alias, with `provides=["iris"]`. It is the first `[[mods]]` entry, at version `1.8.0`.
  - `Iris.MODNAME` is now "Visus".
  - `Iris.getVersion()` reports the `visus` version.
  - New mod-list logo `visus-logo.png`.
- **Build:**
  - Artifact is `visus-1.20.1-<version>.jar` (group `dev.visus`).
  - Gradle heap raised to 4 GB; `remapSourcesJar` ran out of memory at 3 GB.
  - New verification tasks `abiCheck` and `checkForbiddenResources`, both wired into `check`.
- **Licence files:** added `LICENSE` (LGPL-3.0), `COPYING.GPL-3.0`, `NOTICE` and this file.
- **No rendering, shader-transform or pack-visible behaviour changed.**

## 2026-11-06: Visus 0.1.1
- **Licence:**
  - The combined work is conveyed under AGPL-3.0-only (`LICENSE`), as GPL-3.0 section 13 allows, because glsl-transformer is AGPL-3.0.
  - The LGPL-3.0 text moved to `COPYING.LESSER`.
  - `mods.toml` `license` changed to `AGPL-3.0-only`.
  - `NOTICE` rewritten.
- **No code changes.**

## Upstream commits after 1.8.0 that are not in Visus yet
| Commit | Change | Visus plan |
|---|---|---|
| `a0b9d31a4` (#760) | Fixes memory corruption in `GlyphExtVertexSerializer` | 0.2.0 (stability) |
| `3f70af20f` (#763) | Fixes the Mekanism flamethrower crash (`LightningHandler` vertex format) | 0.2.0 |
| `b50faac79` (#806) | Adds the `IS_OCULUS` standard macro | 0.2.0. Pack-visible, so it is listed in `golden/CHANGES.md` |
| `b3b278134` (#789) | Embeddium update; `CuboidMixin` change | 0.2.0, after a check against Xenon 0.3.31 |
