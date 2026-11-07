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

## 2026-11-07: Visus 0.4.0
- New `dev.visus.compile` (`ShaderBuild`, `CompileContexts`) and `dev.visus.mixin.compile`, ported from
  RatOptimization's `oculus_shaders` (MIT).
- **`ShaderMap`** constructor: begins a build, warms up all programs (`ShaderBuild.warm`), builds them, ends the build.
- **`IrisChunkProgramOverrides.createShaders`**: times the terrain build. New Sodium-compat mixin
  `compile.MixinGlProgramBuilderTiming` times Embeddium/Xenon links.
- **`Parameters`**: `type` is no longer part of `hashCode`/`equals` (it is set during the transform, so the transform
  cache never hit); the separate-AO setting and debug options are captured and compared instead.
- `oculus_alias_version` 1.8.0 to 1.8.100.

## 2026-11-06: Visus 0.3.0
- New `dev.visus.flywheel` (irisflw 2.0.5, MIT; see `NOTICE`), with its mixin configs
  `mixins.visus.flywheel*.json` (applied only with Flywheel installed) and its shaders under
  `assets/flywheel/flywheel/internal/iris_instancing`. Changes from irisflw: package and backend id renamed;
  `IrisFlwCompatGlProgramBase.deleteInternal` is a no-op (V-18); `IrisProgramLinker` creates its own GL program only on
  the non-shader path; the `@Mod` entry point is replaced by `FlywheelSetup` (called from `dev.visus.Visus`).
- `mods.toml`: `irisflw` declared incompatible.
- No Oculus file changed.

## 2026-11-06: Visus 0.2.5
- **Upstream #789** (`b3b278134`, MoePus) cherry-picked: `embeddium_version` 0.3.31-beta.53, `CuboidMixin` implements
  `embeddium$getSimpleCuboid`.
- **`mods.toml`**: `geckoanimfix` and `oculusparticlefix` declared incompatible (optional, `versionRange = "(,0)"`).

## 2026-11-06: Visus 0.2.4
- **`MixinRenderBuffers`** (batched entity rendering): `beginLevelRendering`/`endLevelRendering` are skipped, as a
  counted pair, while no shader pack is in use and `batching.skipWithoutShaders` is on.
- **`Iris.onEarlyInitialize`** loads `dev.visus.config.VisusConfig` (`config/visus.properties`).
- New Visus mixins (`mixins.visus.json`, package `dev.visus.mixin.shadow`) for the shadow skip list. No Oculus file
  changed for them.

## 2026-11-06: Visus 0.2.3
- No Oculus file changed. New `mixins.visus.compat.geckolib.json` (`dev.visus.compat.geckolib`).
- **`MixinParticleEngine`**: the one-time particle classification line is logged at info.

## 2026-11-06: Visus 0.2.2
- **`MixinParticleEngine`** (V-10): the OPAQUE phase draws only opaque types and the TRANSLUCENT phase the rest
  (`dev.visus.particle.ParticlePhases`). A new `@Redirect` on `ParticleRenderType.begin` classifies modded types once
  from the GL blend and depth-write state. The filtered key-set views are cached.

## 2026-11-06: Visus 0.2.1
- **`ExtendedShader`** (V-17): `apply()` always calls `glUseProgram`. The static `lastApplied` skip is removed; it went
  stale when Flywheel bound program 0 between irisflw's compile-time `bind()` and the first draw.

## 2026-11-06: Visus 0.2.0 "Stability"
Each change is tagged with its bug-ledger ID from `docs/VISUS-ARCHITECTURE.md` §7.
- **`batchedentityrendering`** (V-01):
  - `GraphTranslucencyRenderOrderManager` uses a re-entrant `GroupDepth` counter. Nested and unmatched group calls no longer throw.
  - `RenderOrderManager` and `Groupable` gain a default `resetGroupState()`. `FullyBufferedMultiBufferSource` and its wrapper forward it.
  - The batched `MixinLevelRenderer` calls it at the start of each level render.
- **`SmoothedFloat`** (V-02): the smoothing step moved to `dev.visus.math.SafeSmoothing`. Its arithmetic is unchanged for finite input.
- **`centerDepth.fsh`** (V-02): NaN and Inf guards. Listed in `golden/CHANGES.md`.
- **`NormalHelper`** (V-03): the three `computeTangent*` methods fall back to `fallbackTangent` (Frisvad basis) when the tangent is zero, Inf or NaN.
- **`BaseClippingPlanes`, `AdvancedShadowCullingFrustum`** (V-04): planes with no normal (an infinite far plane) are flagged and skipped.
- **Pipeline lifecycle** (V-05):
  - New `NoOpPipeline` (a `VanillaRenderingPipeline` subclass, with a new protected constructor that skips the world settings).
  - `PipelineManager` gains `getPipelineOrNoOp()` and a level-render depth.
  - `MixinLevelRenderer`'s pipeline field is never null and reads through `visus$pipeline()`.
  - `Iris.reload()` defers to the next frame boundary when called during a level render.
- **`TextureInfoCache`, `IrisRenderingPipeline`** (V-06): depth copies are allocated from the queried sized depth format (`getSizedDepthFormat()`).
- **`MixinGlStateManager_FramebufferBinding`** (V-14): records binds in `dev.visus.gl.BindTracker` and no longer cancels any bind.
- **`GLDebug`** (L8): rate-limited through `dev.visus.gl.DebugMessageLimiter`.
- **`Iris.createPipeline`** (L8): shows a toast when the pack fails to load.
- **`ShaderPrinter`** (V-16): sanitises file names, and one failed write no longer skips the remaining files.
- **Upstream fixes:** cherry-picked #760 and #763 (table below).
- **Build:** JUnit 5 T0 tests in `src/test`.

## Upstream commits after 1.8.0
| Commit | Change | Visus |
|---|---|---|
| `a0b9d31a4` (#760) | Fixes memory corruption in `GlyphExtVertexSerializer` | **In 0.2.0** (cherry-picked) |
| `3f70af20f` (#763) | Fixes the Mekanism flamethrower crash (`LightningHandler` vertex format) | **In 0.2.0** (cherry-picked) |
| `b50faac79` (#806) | Adds the `IS_OCULUS` standard macro | 0.8 (pack SDK). It adds a define to every pack program, which 0.2.0's "no pack GLSL change" rule forbids; it goes in with the other feature-flag backports and gets a `golden/CHANGES.md` entry. |
| `b3b278134` (#789) | Embeddium dev dependency 0.3.14 to 0.3.31; `CuboidMixin` implements `embeddium$getSimpleCuboid` | Adopted in 0.2.5. Only Xenon's `features.render.entity.fast_render` mixins call that method, and Visus's `sodium:options` turns them off, so it cannot be reached today. |
