# Visus changelog

Versioning:
- +0.0.1 for every rebuilt change.
- +0.1.0 when a change adds or removes 500+ lines (patch resets to 0).
- 1.0.0 only when requested.

## 0.3.0 (2026-11-06) "Absorb", part 1: Flywheel
- **Oculus Flywheel Compat is built in** (architecture §4.6). Its code (irisflw 2.0.5, MIT, by Red Face) is now part of
  Visus as `dev.visus.flywheel`, with the MIT notice kept (`META-INF/visus/irisflw-LICENSE`, `NOTICE`).
  - Flywheel instances (Vanillin's block entities and entities in RatPack, Create 6 contraptions elsewhere) draw
    through the shader pack's programs, in the main and the shadow pass, as with irisflw.
  - Loaded only when Flywheel is installed. The backend is `visus:iris_instancing`.
  - irisflw itself is now declared incompatible (two copies would register two backends): remove
    `oculus-flywheel-compat` when updating.
- **No more GL error on every pipeline rebuild (V-18).** irisflw deleted its programs on top of the Iris pipeline,
  which had already deleted them, raising GL error 0x3F6 on every dimension change and pack toggle. The pipeline now
  deletes them, once.
- **No leaked program objects.** irisflw created an empty GL program for every Flywheel program it compiled under a
  shader pack and never used or deleted it.
- **V-08 status:** Flywheel 1.0.x already remembers uniform locations, including missing ones, so the `_flw_` uniform
  spam does not happen with it. Choosing the `flw_light` call from the shader's own declaration is still open:
  Flywheel 1.0.4 only has the 3-argument form, and no build with the other forms is available to test against.
- The `oculus` alias stays 1.8.0 until the compile service absorbs RatOpt's `oculus_shaders`.
- **Measured** (clientbench, RatPack 2.9 TestClient with irisflw removed, Photon):
  - T1: all 460 transformed files, Flywheel programs included, identical to Visus 0.2.1 with irisflw 2.0.3.
  - T5 scenarios 1, 5 and 9 against Oculus 1.8.0 + irisflw: PASS, no new or missing ERROR kinds. 0 GL debug messages
    over 15 pipeline rebuilds (irisflw runs: 0x3F6 on each).
  - Not yet checked by eye: Flywheel instances in the shadow map (needs a Vanillin or Create scene in T3).

## 0.2.5 (2026-11-06)
- **Add-on guard** (architecture §4.3).
  - Visus declares GeckoLibOculusCompat (`geckoanimfix`) and Oculus Particle Fix as incompatible
    (`versionRange = "(,0)"`, the RatPack convention). With either installed, Forge stops at load with "Mod visus only
    supports oculusparticlefix below 0" instead of starting. Both jobs are built in (0.2.3, 0.2.2).
  - Before, Oculus Particle Fix's mixin failed to apply to Oculus 1.8 and left the game in a broken mod state, and
    GeckoLib 4.8.4 already refuses to load with GeckoLibOculusCompat.
  - Tried first and dropped: emptying the add-ons' mixin configs at load time (the ModernFix technique). Visus's
    config plugin runs before Forge registers some mods' mixin configs, and those configs never pass through
    `Mixins.getConfigs()` where the plugin could reach them; Oculus Particle Fix's was one of them.
  - irisflw is not declared: it stays active until the native Flywheel backend replaces it.
- **Upstream Oculus #789** (MoePus): built against Embeddium 0.3.31, and `CuboidMixin` implements
  `embeddium$getSimpleCuboid`, which Xenon 0.3.31's `ModelCuboidAccessor` declares. Today only Xenon's fast-render
  entity mixins call it, and Visus turns those off, so nothing changes on screen.
- **Measured:** launching with Oculus Particle Fix installed stops at load with the message above (T5 harness run).

## 0.2.4 (2026-11-06)
- **Collections of Optimizations' `[oculus]` options are built in** (architecture §4.4).
  - In the shadow pass Visus skips sign text, entity name tags and banner pattern layers (the base cloth still casts
    its shadow), and items report no enchantment glint. None of these are visible in shadows; each costs draw calls.
  - While no shader pack is in use, entities draw through vanilla's buffer source instead of Oculus's batched one,
    which only rebuilds a translucency graph per frame for no visible difference. A skipped begin is counted, so it
    always pairs with its own end even when a pack is toggled in between.
  - The switches are in the new `config/visus.properties` (`shadow.skipSignText`, `shadow.skipGlint`,
    `shadow.skipNameTags`, `shadow.skipBannerPatterns`, `batching.skipWithoutShaders`), all on by default. When the
    file is first created, the user's values are copied once from `collections_of_optimizations-common.toml`.
  - CoO's own `[oculus]` mixins still work alongside; they do the same thing.
- **ABI census records injection points.** `tools/abi/AbiCensus.java` now also records members named in
  `@At(target = ...)`. Re-running it on RatPack 2.9 added 5 entries that RatOptimization and NeoVoxy depend on (for
  example `CompositeRenderer#renderAll`, used by RatOpt's frame timing, and `SodiumTerrainPipeline#patchShaders`).
  Nothing was removed. `abiCheck`: 252 entries, 0 missing.
- **Measured** (T5 scenarios 1, 5 and 9 against Oculus 1.8.0 with the same plan, Photon; the scene now also has a
  sign, a patterned banner, a visible name tag, an enchanted sword and modded particles): PASS, no new ERROR kinds.
  `visus.properties` was created from the pack's CoO values. Quark's magnet particles (a custom
  `PARTICLE_SHEET_ADDITIVE_TRANSLUCENT` type) were classified once, into the translucent phase. The 0.2.3 run of the
  same plan also passed, with the GeckoLib fix applied next to AllTheLeaks' own `GeoModel` mixin.
- **Tests:** 3 new T0 tests (`VisusConfigTest`: defaults, one-time migration, bad values).

## 0.2.3 (2026-11-06)
- **GeckoLib animations in both passes (V-09).**
  - GeckoLib 4 skips posing a model's bones when the same instance renders again at the same frame time. With a
    shader pack every entity renders twice per frame, shadow pass first, so the main-pass render of a GeckoLib mob or
    armor piece returned early and skipped `tickAnimation` and `setCustomAnimations`.
  - Visus forgets GeckoLib's "last rendered instance" whenever rendering switches between the shadow and the main
    pass. Animation time still advances once per frame; both passes pose the bones.
  - An injection at the head of `GeoModel.handleAnimations`, applied only to GeckoLib 4.x, with no GeckoLib
    dependency. GeckoLibOculusCompat replaced the whole method with a copy from an older GeckoLib, which dropped
    4.8's partial-tick timing for entities; GeckoLib's own timing is kept here.
- The first time a modded particle render type draws, its phase is logged once (V-10).
- **Tests:** 4 new T0 tests (`RenderPassTrackerTest`), replaying GeckoLib 4.8.4's early return: a lone mob is posed
  only in the shadow pass without the fix and in both with it.

## 0.2.2 (2026-11-06)
- **Modded particles draw once (V-10).**
  - With a pack's default `particles.ordering = mixed`, Oculus drew every modded particle type, and vanilla's
    `TERRAIN_SHEET`, in both of its particle phases: once with the opaque program before translucent terrain, then
    again after it. That doubled the cost and blended translucent particles twice.
  - Each type now draws in exactly one phase. Known opaque sheets draw in the opaque phase; everything else draws
    after translucent terrain, where vanilla draws all particles.
  - The first time a modded type draws, Visus reads the blending and depth-write state its `begin()` set (once per
    type). A type that draws without blending and writes depth moves to the opaque phase.
  - The order is Forge's: `ParticleEngine.particles` is a `TreeMap` sorted by Forge's particle comparator, and the
    phase filters keep it. oculusparticlefix only re-sorted that already sorted map, and it cannot apply to Oculus 1.8 (it
    targets the old `net.coderbot` package).
  - The two filtered views are made once, not every frame.
- **Tests:** 5 new T0 tests (`ParticlePhasesTest`), including the Oculus filters drawing a modded type twice.

## 0.2.1 (2026-11-06)
- **No GL errors on the first Flywheel draw (V-17).**
  - Each new irisflw instancing program drew its first frame with no program bound, so all of its uniform uploads failed.
    With debug options on this logged a burst of GL error 0x4AE (`GL_INVALID_OPERATION`) when Flywheel objects first rendered.
    It was harmless after that one draw.
  - Cause: `ExtendedShader.apply()` skipped `glUseProgram` when it was the last shader applied. Flywheel unbinds
    (`glUseProgram(0)`) after compiling a program without calling `clear()`, so that cache went stale. The code is
    unchanged from Oculus 1.8.0, so Oculus has the same bug; Oculus only printed it to stderr.
  - `apply()` now always binds its program. `clear()` already reset the cache after every normal draw, so the skip
    saved almost nothing.
- **Measured** (clientbench, RatPack 2.9 TestClient, Photon 1.3b RatPatch, debug options on):
  - 0.2.0: 7 copies of 0x4AE per session. A diagnostic build showed `skippedBind=true`, current program 0, upload
    error 0x502, on all 3 irisflw programs.
  - 0.2.1: 0 GL debug messages in the whole session (the Flywheel test rig placed and rendered). T1 against Oculus
    1.8.0: PASS (the only extra files are the allowed irisflw dumps).
  - Frame rate in that single debug-mode run: base 128.1 FPS (p99 15.05 ms), overview 148.2 FPS (p99 13.34 ms). The
    two 0.2.0 debug runs of the same jar gave 126.0–140.4 and 151.6–152.7 FPS, so this is within single-run noise;
    no T4 was run. The 0.3.0 T4 rechecks overview p99.

- **0.2.0 acceptance, measured on this build** (no code change):
  - T5 scenarios 1, 5 and 9 with the new `tools/t5/t5.py` harness: PASS against Oculus 1.8.0 with the same plan
    (no new ERROR kinds). Reload order matches Oculus at every step. No "Already in a group". The stencil main
    target (`GL_DEPTH32F_STENCIL8`, from Voidscape) and 4 dimension changes run without depth-format errors.
  - 30-minute nesting soak: PASS (60 outfit and camera phases, no GL messages).
  - T4, Complementary Unbound r5.9.3 (3 Visus runs alternating with Oculus, plus one extra Oculus run): base
    146.1 → 144.9 FPS (−0.8 %), overview 112.3 → 114.5 FPS (+2.0 %). Within noise.
  - Found V-18: irisflw deletes its programs twice on each pipeline destroy (GL error 0x3F6). The same happens with
    Oculus. Fixed by the 0.3.0 Flywheel backend.

## 0.2.0 (2026-11-06) "Stability"
Crash and corruption fixes. No pack-visible change except `centerDepth.fsh`'s NaN guard. Bug IDs refer to
`docs/VISUS-ARCHITECTURE.md` §7.
- **No more "Already in a group" crash (V-01).**
  - Entity render groups now nest. An entity rendered from inside another entity's renderer (Curios, armor layers, Mekasuit) joins the outer group instead of throwing.
  - An unmatched group end is ignored.
  - Any group left open by a renderer that threw is closed at the start of the next frame, with one log line.
- **NaN-safe smoothing (V-02).**
  - `SmoothedFloat` (exposure and other smoothed uniforms) and `centerDepth.fsh` (`centerDepthSmooth`) can no longer latch NaN or Inf, which caused a black or blown-out screen.
  - Covers a half-life of 0, a frame with no time step, and a NaN sample.
  - For finite input the values are bit-identical to Oculus.
- **No NaN tangents on modded models (V-03).**
  - Degenerate, collinear or single-texel UVs used to produce a zero tangent, which packs normalised to NaN (TAA and bloom smears).
  - They now get a unit tangent perpendicular to the normal.
  - Every other tangent is unchanged.
- **Shadow culling with an infinite far plane (V-04).** A plane with no normal is skipped instead of being used for back-plane and edge-plane tests. Finite projections cull exactly as before.
- **No "pipeline is null" crash (V-05).**
  - Sky, cloud and weather hooks that run outside `renderLevel` use a stateless no-op pipeline.
  - So does the rest of a frame in which the pipeline was destroyed.
  - `Iris.reload()` called during a level render now runs at the end of the frame (NeoVoxy #30).
- **Matching depth formats (V-06).**
  - `depthtex1`/`depthtex2` are allocated in the driver's real sized depth format, read back from the depth and stencil bits.
  - So copying the depth buffer can no longer fail with "depth formats do not match", for example with stencil mods.
- **Bind caches no longer skip binds (V-14).**
  - The framebuffer and program bind caches no longer cancel binds they think are redundant.
  - The program cache went stale when Xenon/Embeddium bound programs through raw GL.
  - Binds are now only recorded, in `dev.visus.gl.BindTracker`.
- **Quieter GL debug output (L8).**
  - The first 5 copies of each message are printed in full.
  - Repeats are counted and summarised once a minute.
- **Pack failure toast (L8).** When a pack fails to load, a toast appears as well as the chat line, and rendering falls back to vanilla as before.
- **Shader dumps (V-16).** Debug dumps now write the Oculus Flywheel Compat programs. Their names contained `/`, so writing failed. One failed file also no longer stops the rest.
- **Upstream Oculus fixes:**
  - #760: memory corruption in `GlyphExtVertexSerializer`.
  - #763: Mekanism flamethrower crash.
- **GL errors in the game log (L8, V-17).**
  - The first copies of each GL debug message now also go to `latest.log`, with a stack trace on the first.
  - Oculus wrote them only to stderr, which launchers often do not save.
  - Doing this found V-17: a burst of 7 copies of GL error 0x4AE at world join. Fixed in 0.2.1.
- **Tests.** 36 JUnit T0 tests (`gradlew test`), each tied to a bug ID. Most compare against the Oculus arithmetic.
- **Measured** (clientbench, RatPack 2.9 TestClient, RX 7900 XTX, Java 25):
  - **T1, all 5 packs: PASS.** Transformed GLSL is identical to Oculus 1.8.0 on every program:
    - Complementary Unbound r5.9.3: 425 files.
    - Photon 1.3b RatPatch: 451 files.
    - Glimmer 1.5.2: 458 files.
    - MakeUp-UltraFast 9.5e: 375 files.
    - Solas 3.7 RatPatch: 427 files.
    - The only additions are the 9–18 irisflw program files that V-16 now writes (`golden/CHANGES.md`).
  - **Log errors.** The same ERROR set as Oculus on all 5 packs, ignoring Xaero's online version check.
  - **Absent from all logs:** "Already in a group", "pipeline is null", leftover render groups, and deferred reloads.
  - **T4, Photon, 3 alternating runs per side, with a 3-minute GPU cooldown after each run** (2560x1421: the window came up 19 px short, the same in every run):
    - Base scene: 138.6 ± 4.9 → 136.0 ± 13.7 FPS (−1.9 %).
    - Overview scene: 155.4 ± 3.8 → 152.4 ± 5.5 FPS (−1.9 %).
    - Both are within the ±5–6 % noise. Overview p99 is 11.14 → 12.24 ms (+9.9 %), but one outlier run causes it (13.55 ms); without that run the change is about +4 %. Recheck in the 0.3.0 T4.
    - Single debug-mode dump runs had shown −17 % on Photon. These runs did not reproduce that: those dump runs ran back to back with no cooldown, in a different session from their references.
- **Moved:**
  - V-01's FIFO draw order goes to 0.6 (it changes draw order, so it needs T3).
  - V-06b's blend/depth override work goes to 0.3.
  - Upstream #806 (`IS_OCULUS`) goes to 0.8.
  - Upstream #789 goes to 0.3.

## 0.1.1 (2026-11-06)
- **Licence: AGPL-3.0-only.**
  - The Visus jar is now licensed under the GNU AGPL v3.0, because the bundled glsl-transformer library is AGPL.
  - Files from Oculus and Iris stay available under LGPL-3.0-only.
  - `NOTICE` adds an attribution term under section 7(b): forks must keep "Based on Visus by SolarBiscuit".
  - The jar now carries `LICENSE` (AGPL), `COPYING.LESSER` (LGPL), `COPYING.GPL-3.0` and `NOTICE` in `META-INF/visus/`.
  - `mods.toml` now says `license="AGPL-3.0-only"`.
  - See `docs/spikes/licences.md`.
- **No code or rendering changes.**
- **Checked:** Oculus Flywheel Compat (irisflw) 2.0.3 loads and draws Flywheel instances under Visus exactly as under
  Oculus. Keep it installed until 0.3.0, which builds its work into Visus.

## 0.1.0 (2026-11-06) "Baseline"
- **Fork of Oculus 1.8.0.** Imported from Asek3/Oculus `1.20.1-new` @ `5751a2f6b`, with the upstream git history.
  The commit was verified against the released jar: the same 1,645 files, and bytecode differs only by javac version.
- **Drop-in identity.**
  - The jar provides two mods: the alias `oculus` 1.8.0 (`provides=["iris"]`) and `visus` 0.1.0.
  - Mods that check for Oculus, or depend on `oculus [1.8.0,)` / `[1.7.0,)`, keep working.
  - The alias entry comes first: mods such as ImmediatelyFast and RatOptimization read the jar's version, which FML
    takes from the first entry. A test run with the alias second showed them reporting "Oculus 0.1.0".
  - The alias stays exactly 1.8.0 until 0.3.0, so RatOptimization's `oculus_shaders` (4 parallel compile contexts
    on the RX 7900 XTX) keeps working. A test run with the alias at 1.8.100 showed RatOpt switching it off.
- **ABI census and check.**
  - `tools/abi/AbiCensus.java` scanned the 497 RatPack 2.9 TestClient jars. Oculus and irisflw were excluded.
  - Result: 268 references into Oculus internals from 26 mods, written to `abi/abi-freeze.txt`.
  - `gradlew abiCheck` fails the build if any of those references disappears.
  - Self-test against Oculus 1.8.0: 247 checked, 0 missing, 1 unverifiable (an inherited vanilla method name).
- **Build guard.** `gradlew checkForbiddenResources` fails if the jar contains the Sodium terrain shader file that
  disables AtmospheraLib.
- **Rebrand.** Mod-list name, logo, F3 lines and logs now say "Visus". Config file, `shaderpacks/` layout and option
  files are unchanged.
- **No rendering changes.** Shader output, uniforms and pipeline behaviour are identical to Oculus 1.8.0.
- **Measured** (clientbench, RatPack 2.9 TestClient, 499 jars, 2560x1440, RX 7900 XTX, Java 25. Single dump runs with
  debug options on, so these are parity checks, not benchmarks):
  - Photon 1.3b RatPatch, Oculus 1.8.0 → Visus 0.1.0:
    - Base scene: 151.2 → 151.4 FPS, p99 12.44 → 12.24 ms.
    - Overview scene: 171.9 → 172.7 FPS, p99 9.81 → 9.66 ms.
  - T1 golden transform, PASS on both packs tested:
    - Photon: 451 dumped files, identical.
    - Complementary Unbound r5.9.3: 225 distinct programs and the id maps, identical.
  - RatOpt `oculus_shaders` starts its 4 compiler contexts under Visus. ImmediatelyFast reports "Iris/Oculus 1.8.0".
  - The set of ERROR log lines is the same as with Oculus. All are pre-existing pack errors.
- **Not adopted / open:** see `docs/spikes/`. One finding needs a decision before public release: glsl-transformer is
  AGPL-3.0 (`docs/spikes/licences.md`).
