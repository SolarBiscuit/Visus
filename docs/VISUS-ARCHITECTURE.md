# Visus: architecture, evaluation and compatibility contract

- **Date:** 2026-11-06. Companion to `VISUS-ROADMAP.md`; agents read `../CLAUDE.md` first.
- **Target:** Minecraft 1.20.1, Forge 47.x, client only. A drop-in replacement for `oculus-mc1.20.1-1.8.0.jar` that also takes over oculus-flywheel-compat 2.0.3, oculusparticlefix, GeckoLibOculusCompat and RatOpt's `oculus_shaders` module.
- **Licence (decided 2026-11-06):**
  - The Visus jar is AGPL-3.0-only, because the bundled glsl-transformer is AGPL.
  - Files from Oculus and Iris stay LGPL-3.0-only.
  - `visus-api` is a separate clean-room module under MIT. Mods that link to Visus internals should use an AGPL-compatible licence; see `spikes/licences.md`.
- **Status keys used below:** **Adopt**, **Adapt** (take the idea, change the design), **Defer** (off by default, behind a measured gate), **Reject**. Items marked *verify in 0.1.0* are not confirmed yet.

---

## 1. Verdict

**Fork Oculus 1.8.0 and re-architect it in place.** Do not do a clean-room rewrite. Keep the class and member shapes that other mods link against (the ABI) and replace internals one subsystem at a time behind them.

### 1.1 Why this differs from the 2026-11-01 "No-go"
`[1.20.1] Atmosphera/docs/OCULUS-FORK-INVESTIGATION.md` rated a fork and a rewrite **No-go**. That scorecard judged one question: how far *Atmosphera* could override packs, plus whether a fork-only frame-time win existed. Both findings still stand:
- Packs own their G-buffer, lighting, water and composites (§6.3 there). No Iris-compatible loader, forked or rewritten, raises that ceiling. Visus does not promise new pack-visible effects. It can only feed packs better inputs: defines, ids, vertex data, snippet hooks.
- No fork-only frame-time win was found on GPU-bound packs.

Visus is justified on different grounds:
1. **One owner for the shader stack.** Today RatPack runs Oculus 1.8.0 plus irisflw plus RatOpt mixins pinned to exactly 1.8.0, plus CoO's `[oculus]` section. Each piece patches the others. Visus folds them into one jar with one test matrix.
2. **Stability.** About ten documented crash or corruption bugs (§7) are cheap to fix only when you own the code.
3. **Load/apply time and stutter**, not steady FPS: disk caches, parallel compile including the 5 terrain programs, no main-thread transform.
4. **CPU-bound frames.** Shadow-pass entity work (0.39–0.54 ms per frame, about 12–13 % of render-thread busy time on MakeUp), uniform evaluation and batching allocations.
5. **Formal APIs** for NeoVoxy (24 internal mixins today) and Atmosphera (reflection today).

**Honest expectation.** On GPU-bound packs (CU at 2560x1440) frame time moves within bench noise (±5–6 %). The gains you can measure are in apply time, the CPU-bound case (MakeUp), 1 % lows, allocation rate and crash count.

---

## 2. Evidence base
| Source | What it gave |
|---|---|
| `OculusRewriteDocs/Oculus and Iris Code Analysis.md` | 8 bug write-ups, 12 rewrite features |
| `OculusRewriteDocs/Oculus_Iris Rewrite Architectural Blueprint.md` | 7 bugs, the SDK idea, 10 "AAA" features, the JVM technique review |
| `referencemods/oculus-mc1.20.1-1.8.0.jar` | 1,587 classes; 176 mixins in 12 configs; bundles jcpp 1.4.14 (jar-in-jar), ANTLR 4 runtime (shaded `oculus.org.antlr`), glsl-transformer (`io.github.douira`, 370 classes), Ithaka digraph (43), stareval (80), Commons Collections tries (shaded) |
| `referencemods/oculus-flywheel-compat-Forge-2.0.3.jar` | `top.leonx.irisflw`: 57 classes; mixins into Flywheel (`MixinPipelineCompiler`, `MixinProgramStitcher`, `MixinMeshEmitter`...) and Iris (`MixinShadowRenderer`, `ProgramSetMixin`, `MixinIrisRenderingPipeline`...) |
| `referencemods/GeckoLibOculusCompat-Forge-1.0.1.jar` | One `@Overwrite` of `GeoModel.handleAnimations`. It ties animation time to the game tick and render instance, so the shadow render no longer freezes or doubles animation progress. |
| `referencemods/oculusparticlefix-1.0.jar` | `@Redirect` of `Map.keySet()` in `ParticleEngine.render`. It iterates particle types in `ForgeHooksClient.makeParticleRenderTypeComparator` order, so modded `ParticleRenderType`s keep Forge's order inside Iris's phased particle engine. It targets the pre-1.7 `net.coderbot` package, so it no longer applies to 1.8.0; 1.8.0 filters with `Sets.filter` on an unordered key set. |
| `Atmosphera/docs/OCULUS-FORK-INVESTIGATION.md` (§3, §4, §9, §10) | The vertex layout, spare bits, bridge hooks, a requirements catalog, RatPack pain points with measurements |
| RatPack 2.9 TestClient (Prism, 499 jars) | The list of mods that link to Iris internals (§4.2), renderer versions, JVM flags, shader packs |
| `Forge/clientbench`, `Atmosphera/tools` | The test harnesses we reuse (§9) |

**Quality of the research docs.** They are AI-generated "deep research". The bug mechanisms check out against the bytecode for bugs 1, 2, 5, 6 and 8. Several numbers are borrowed from other contexts:
- The "92 % more sections / 7.25→11.90 ms" figure comes from Iris issue #3241 on MC 26.x, where vanilla switched to reversed-Z. Vanilla 1.20.1 uses the standard [-1,1] projection.
- The "10–45 s freeze" figure matches RatPack's measured AMD apply stall (about 45–77 s before RatOpt), so it is plausible.
- The VRAM, GI and "AAA" speed-up figures come from unrelated papers. Treat them as speculation.

---

## 3. Basis evaluation

### 3.1 Fork versus clean room
| Criterion | Fork Oculus 1.8.0 | Clean-room engine + ABI shim |
|---|---|---|
| Pack-format parity: `shaders.properties`, options and profiles, screens, `block/item/entity.properties`, dimension folders, `#include` graph, custom textures and images, about 200 uniforms, `IRIS_FEATURE_*` defines, driver workarounds in `CompatibilityTransformer` | Exact on day 1 | Years of edge cases to rediscover. Every miss is a broken pack for a user who "just swapped the jar". |
| ABI used by dependents (§4.2) | Present already; freeze and test it | The shim has to re-create internal class shapes such as `IrisRenderingPipeline`, `ProgramSet` and `CustomUniforms`, which is effectively a fork again |
| Licence | AGPL-3.0-only for the jar (bundled glsl-transformer is AGPL); Oculus/Iris files stay LGPL-3.0-only. Publish source | Any licence, but only if nothing is copied, which is hard to prove given the shim |
| Time to first usable build | Days | Many months |
| Risk | Inherited tech debt, which we pay down one subsystem at a time | Regressions everywhere at once |
| **Result** | **Chosen** | Rejected |

### 3.2 Keep, rewrite or delete, per Oculus package
| Package / area | Decision | Notes |
|---|---|---|
| `iris.shaderpack.*` (properties, options, `OptionAnnotatedSource`, include graph, id maps, `ProgramSet`, `PackDirectives`) | **Keep**, freeze the ABI | Correctness of the pack format lives here |
| `iris.gui.*`, `iris.config.*`, `assets/iris/lang` | **Keep**, rebrand strings | Users and packs expect these screens. The config file name stays `oculus.properties`, read and written as before. |
| `iris.uniforms.*` catalog (`CommonUniforms`, `CameraUniforms`, `MatrixUniforms`, `CelestialUniforms`...) | **Keep** the catalog, **rewrite** the update runtime (§5 L4) | NeoVoxy and GBF touch it. Keep the methods they call. |
| `kroppeb.stareval` + `iris.uniforms.custom.*` | **Keep** as the reference parser, **add** a compiled evaluator | Differential tests compare the two |
| `iris.pipeline.transform.*` (TransformPatcher and the Sodium, DH, composite and compatibility transformers) + glsl-transformer + jcpp | **Keep**, move off the main thread, put caches in front | Output must stay byte-identical until a deliberate change |
| `iris.pipeline.*` (`IrisRenderingPipeline`, `PipelineManager`, `ShadowRenderer`, `CompositeRenderer`) | **Rewrite internals**, keep the outer shapes | Lifecycle, shadow pass and composite scheduling |
| `batchedentityrendering.*` (17 mixins) | **Rewrite** ordering; keep `FullyBufferedMultiBufferSource` and the `Groupable` shape | ETF links to `batchedentityrendering.impl` |
| `de.odysseus.ithaka.digraph` | **Delete** after 0.6 (kept only behind a debug option for comparison) | |
| `iris.gl.*` (state, textures, `DepthCopyStrategy`, `IrisRenderSystem`) | **Rewrite** state cache and depth copy; keep `IrisRenderSystem` statics | NeoVoxy calls `IrisRenderSystem.bindSamplerToUnit` |
| `iris.compat.sodium.*` (49 mixins) | **Keep**, retarget and test on **Xenon 0.3.31** and Embeddium 0.3.x | |
| `iris.compat.dh.*` | **Keep**, put behind the LOD API (§4.5) | |
| `iris.compat.pixelmon`, `indigo`, `indium` | **Keep** (cheap, optional) | |
| `iris.texture.*` (PBR, mipmaps, `MixinSpriteContents`) | **Keep**; the GPU mipmap generator is a Defer item | |

### 3.3 Upstream Iris
Oculus 1.8.0 is a port of Iris 1.7.x. Upstream Iris 1.8+ targets 1.21.x (Fabric/NeoForge). We do **not** rebase onto it. Instead:
- *0.1.0 task:* diff upstream's feature-flag enum and `StandardMacros` against 1.8.0. List the pack-visible features that are missing.
- Backport them one by one in 0.8. Each backport is a pack-visible change, so it gets its own golden-corpus update.

---

## 4. Compatibility contract

### 4.1 Identity and packaging
- `mods.toml` has two `[[mods]]` entries:
  - `visus`: the real mod, version per the versioning rule.
  - `oculus`: `provides = ["iris"]`, `displayTest = "IGNORE_ALL_VERSION"`. Version `1.8.0` while the internals still match Oculus 1.8.0 (through 0.2.x), so RatOpt's `oculus_shaders` module keeps working; `1.8.100` from 0.3.0, when Visus absorbs that module.
  - The `oculus` entry must be the **first** `[[mods]]` entry: FML's `ModFileInfo.versionString()` returns the first entry's version, and ImmediatelyFast and RatOpt read the file version through `LoadingModList`.
- This satisfies `ModList.isLoaded("oculus")`, `LoadingModList.getModFileById("oculus")` (AtmospheraLib), NeoVoxy's `oculus [1.8.0,)` and irisflw's `[1.7.0,)`.
- RatOpt's `oculus_shaders` module checks for exactly 1.8.0. Through 0.2.x it stays on and patches Visus's unchanged internals. From 0.3.0 the alias is 1.8.100, the module switches itself off, and Visus has absorbed its work (§4.4).
- *Verify in 0.1.0:* whether FML 47 honours `provides`. Oculus ships it, so it is at least harmless.
- Keep `net.irisshaders.iris.api.v0.*` binary-identical: `IrisApi`, `IrisApiConfig`, `IrisTextVertexSink`, `item.IrisItemLightProvider`.
- Also keep the static `Iris` entry points: `getCurrentPackName()`, `getShaderpacksDirectory()`, `getCurrentPack()`, `reload()`, `getPipelineManager()`.
- **Never ship** `assets/sodium/shaders/blocks/block_layer_opaque.vsh`. AtmospheraLib (`AtmosCompat.java:99-108`) turns off all terrain features when any non-Embeddium jar contains it.
- Pack options stay in `shaderpacks/<name>.txt` as Java Properties. AtmospheraLib reads that file (`ShaderPackState.java:86`).

### 4.2 Frozen ABI, built from a census
Mods in RatPack 2.9 that link to `net.irisshaders.*`:

| Depth | Mods |
|---|---|
| Deep internals | NeoVoxy (pipeline, shaderpack, shadows, targets, uniforms, samplers, gl), oculus-flywheel-compat (superseded), RatOptimization 1.12.3 (pipeline, shaderpack, config, compat; its module is absorbed) |
| Some internals | ETF (`batchedentityrendering.impl`, `layer`), GBF (`uniforms`, `vertices`), Fusion (`mixin`, `texture`), Moonlight, Polytone, Wakes (`Iris`, `pipeline`) |
| `api.v0` only | EMF, Collections of Optimizations, CC:Tweaked, OBE |
| Reflection or soft | Xenon, Atmospherics, AtmospheraLib, Nuit, Living Horizon, ImmediatelyFast |

**The census tool** (`tools/abi-census`, 0.1.0) is a small Java CLI that uses ASM. Input is a mods folder. Output is `abi/abi-freeze.txt`, which lists:
- Every class, field and method reference into `net/irisshaders/**` and `net/coderbot/**`, with the referencing jar.
- Every `@Mixin` target and `@Accessor`/`@Invoker` name in those packages.
- Every reflective string constant naming those packages, as a best effort.

`gradlew abiCheck` fails the build when a listed member disappears or changes descriptor. An entry may only be removed together with a dated note naming which dependent moved off it. That is how the NeoVoxy entries retire (§4.5). Re-run the census whenever the pack's mod list changes.

### 4.3 Superseded add-ons guard
| Add-on | Replaced by | Guard |
|---|---|---|
| oculus-flywheel-compat (`irisflw`) | Native Flywheel backend (§4.6) | Remove its mixin configs early |
| oculusparticlefix | Forge comparator order in Visus's phased particle engine | It already fails to apply to 1.8; detect it and warn |
| GeckoLibOculusCompat (`geckoanimfix`) | GeckoLib shadow-pass timing fix (§4.7) | Remove its mixin config |
| Oculus Unlocked (if present) | `ShaderInstance` transpiler (§5 L5) | Remove its mixin config |
| RatOpt `oculus_shaders` | Visus compile service (§5 L2) | Self-disables on version ≠ 1.8.0. Visus also logs it. |
| CoO `[oculus]` section | Visus shadow skip list (§5 L6) | CoO's options become no-ops; Visus reads its config once to migrate the user's choices |

**Outcome (0.2.5).** The config-removal mechanism below was built and tested: a Visus config plugin's `onLoad` runs before Forge registers some mods' mixin configs, and those never pass through `Mixins.getConfigs()`, so it missed Oculus Particle Fix. Visus uses the `(,0)` fallback instead.

**Mechanism (0.3, superseded).** A Visus mixin config plugin runs `onLoad`, walks Mixin's registered configs, and removes those owned by the add-ons above before they are selected. ModernFix uses the same technique. Visus then logs one line and shows a one-time toast: "X is built into Visus; you can remove it". If this proves fragile on Mixin 0.8.5, the fallback is an optional dependency with `versionRange="(,0)"`. That is the RatPack convention for incompatible mods, and it blocks launch with a clear message.

### 4.4 RatOpt and CoO absorption
RatOpt's `oculus_shaders` design becomes Visus's L2 compile service:
- `transform_cache`: a stable cache key.
- `parallel_link` / `parallel_contexts`: 4 hidden shared GL contexts.
- A per-driver self-check, written to a driver properties file.
- `audit_mixins`.
- Plus the missing warm-up of the 5 Sodium/Xenon terrain programs. Today these link in 82–912 ms with an average link wait of 172.6 ms; the slowest is `chunk_shader_for_gbuffers_water` at 532 ms.

RatOpt's `frame_timing` mixin (`FrameTimingOculusMixin`) is needed by clientbench. Visus emits an equivalent JFR event, or keeps that target stable (it is in the census).

CoO's `[oculus]` section: Visus skips sign text, glint, name tags and banner patterns in the shadow pass, plus `skipBatchingWithoutShaders`. These become per-feature options under Visus's shadow settings, with CoO's values as defaults.

### 4.5 NeoVoxy: co-developed LOD API (0.4)
NeoVoxy's current hooks: `Iris.createPipeline` (wrapping `ShaderPack.getProgramSet`); `IrisRenderingPipeline` (`<init>`, `beginLevelRendering`, `destroy`, and accessors `getRenderTargets`, `getPack`, `getPackDirectives`, `getUpdateNotifier`); `CustomUniforms.optimise` and `getLocationMap`; `CommonUniforms.addNonDynamicUniforms`; `IrisSamplers.addRenderTargetSamplers`; `ProgramSet.<init>`; `PackRenderTargetDirectives.<clinit>`; `ShaderPackSourceNames.findPotentialStarts` and `findPresentSources`; `StandardMacros.createStandardEnvironmentDefines`. It also calls `ShadowRenderer.ACTIVE`, `Iris.reload()`, `WorldRenderingSettings.shouldUseSeparateAo()` and `IrisRenderSystem.bindSamplerToUnit`. It reads the pack's `voxy.json`.

**Visus LOD API** (`visus-api`, package `dev.visus.api.lod`):
- `LodProgramProvider`: declares extra program names (for example `voxy_*`) and extra source names for discovery. This replaces the `ShaderPackSourceNames` and `ProgramSet` mixins.
- `LodPipelineListener`: `onPipelineCreated(PipelineView)`, `onBeginLevel(FrameContext)`, `onPipelineDestroyed()`. This replaces the `IrisRenderingPipeline` mixins.
- `PipelineView`: read-only access to render targets (by colortex index, with GL ids and formats), pack directives, the pack name and its `voxy.json` text.
- `UniformContributor`: adds uniforms and defines through the normal catalog. This replaces the `CustomUniforms`, `CommonUniforms` and `StandardMacros` mixins.
- `SamplerUnits.allocate(owner, count)`: a central texture-unit and SSBO-binding allocator. It fixes NeoVoxy review bug #10 (unit 6 overwriting the pack's first sampler).
- `Visus.requestReload(reason)`: deferred to the frame boundary. It fixes bug #30 (`Iris.reload()` re-entered from inside the pipeline constructor).
- The L0 state cache is the one source of truth. Raw GL in NeoVoxy goes through `VisusGl.bind*` helpers. This fixes bug #29 (state drift; packs bind SSBOs at 10+n).
- Distant Horizons uses the same API through an adapter that wraps the existing `iris.compat.dh` code.

**Lockstep rule:** a Visus release that changes the LOD API ships with a matching NeoVoxy release. NeoVoxy keeps its old mixins as a fallback for plain Oculus 1.8.0, and its plugin selects the Visus path when `visus` is loaded.

### 4.6 Flywheel 1.0.x and Create
RatPack ships Vanillin 1.1.3 (which bundles Flywheel 1.0.4) and no Create. Common packs ship Create 6.0.x with Flywheel 1.0.x. Both are in scope.

Visus provides a **native Flywheel backend integration**, replacing irisflw:
- Hook Flywheel's pipeline compiler through its public backend and shader APIs where possible. Fall back to the same mixin points irisflw uses (`PipelineCompiler`, `ProgramStitcher`, `MeshEmitter`).
- Splice the pack's `gbuffers_block`/`gbuffers_entities` and `shadow` programs through glsl-transformer. Read the `flw_light` prototype from the AST and pick the 3-, 4- or 5-argument call. This fixes Create Sable/Aeronautics and newer Flywheel headers.
- In the shadow pass, bind the light LUT and cull data and use the shadow model-view/projection relative to the render origin. This fixes black contraptions and missing shadows.
- Memoise uniform locations, including -1, per program. This fixes the `_flw_` uniform spam and the stalls.
- Tangents for instanced meshes use the guarded tangent helper (§7).
- Tests: Vanillin entities and block entities in RatPack; a separate Create 6 instance with trains, contraptions, shafts and cogs. Main and shadow passes, on all 5 packs.

### 4.7 GeckoLib 4.8.x
RatPack has about 15 GeckoLib mods (Creeper Overhaul, Enderman Overhaul, Critters & Companions, Faunify, Hybrid Aquatic, Ghosts, Fantasy Armor, KnightLib, Clockwork and others).

**Problem:** `GeoModel.handleAnimations` returns early if the same instance already rendered this tick. Shader packs render entities twice per frame (shadow, then main), so the shadow pass consumes the update. Animations then freeze or stutter in one of the two passes.

**Fix (0.3):**
- Animation state is computed once per frame per instance, keyed on frame index and instance id.
- The shadow pass reads it without advancing it.
- This is a targeted mixin injection on `GeoModel`, not an `@Overwrite`, guarded by GeckoLib version.

Test: each GeckoLib mob is animated in both passes. The check is a screenshot diff between consecutive frames of the shadow map debug view.

### 4.8 Atmosphera family (AtmospheraLib, Gale, Lumis, Torrente) and Atmospherics Forged
Visus must hold these behaviours:
1. `IrisApi.getInstance().isShaderPackInUse()` and `getConfig().areShadersEnabled()` are stable and cheap. Atmospherics polls them with a 250 ms cache; AtmospheraLib once a second.
2. **Reload ordering.** When a pack is toggled or applied, the API state updates first, then `LevelRenderer.allChanged()` runs. AtmospheraLib re-snapshots at `allChanged` HEAD. Getting this wrong meshes Lumis light bits into pack geometry.
3. With a pack active, `RenderLevelStageEvent` stages (`AFTER_SKY`, `AFTER_TRANSLUCENT_BLOCKS`, `AFTER_WEATHER`) and the Forge fog events (`ViewportEvent.RenderFog`, `ComputeFogColor`) still fire, with the world FBO bound. Lumis shafts and Torrente fog run under packs that lack those effects (pack profiles).
4. The GL framebuffer-bind cache stays coherent with `GlStateManager` even with shaders off. Atmospherics' `SkyHorizonFade.java:254,273` currently works around stale skips.
5. With no pack active, Visus leaves Xenon's shader loading and chunk renderers alone, so Atmosphera's snippet anchors still match.
6. The cloud path keeps reading `ClientLevel.getCloudColor` (Atmospherics `CloudColorMixin`).

**Bridge API** (`visus-api`, package `dev.visus.api.bridge`, 0.4). This is the scope from §9 of the investigation:
- `PackOptionOverrides`: non-persisting option overrides applied at `ShaderPack` construction, shown as locked in the options screen with the owning mod's name.
- `MaterialIdAssist`: fills `mc_Entity` ids for blocks the pack's `block.properties` misses, at `BlockMaterialMapping.createBlockStateIdMap` RETURN (policy ASSIST). Modded foliage then gets the pack's own waving.
- `VertexSnippets`: a `_vert_init`-after hook for gbuffers **and** shadow (Gale waves, Torrente vertex waves). It is gated by mesh bits (material byte bits 3–7).
- **Light mask.** Visus always masks `a_LightCoord & 0xFF` before packs see it, and exposes the high bytes as `atmos_LightHue`. That removes the light-byte hazard (§3.4 of the investigation).
- `FrameUbo`: lets a mod reserve a UBO binding (AtmospheraLib uses `GL_MAX_UNIFORM_BUFFER_BINDINGS-1` for `AtmosFrame`). It is declared into patched programs on request.
- Policy values `DEFER`, `ASSIST` and `OVERRIDE`, chosen per capability by the calling mod.
- **Licence boundary.** `visus-api` contains only interfaces and records written from scratch (MIT). Atmosphera compiles against it and never ships Oculus-derived code or GLSL.

### 4.9 Other mods to verify every release
- **Xenon 0.3.31 and Embeddium 0.3.x:** the chunk renderer, options pages and vertex format.
- **ImmediatelyFast 1.5.5:** batching interplay.
- **ETF/EMF**, **GBF**, **Fusion** (connected textures), **Moonlight**, **Polytone**, **Wakes**: their internal references are in the census.
- **Living Horizon:** reads the DH-compat classes.
- **Ixeris:** GLFW off the main thread, so GL calls must stay on the render thread.
- **threatengl:** a GL state mod; check the state cache.
- **Sinytra Connector:** Fabric Iris add-ons may load through it; they see `iris` via `provides`.
- **Mekanism Mekasuit:** `MixinRenderMekasuit` exists today.
- **Curios:** nested renders.
- **Mods with their own core or post shaders:** Botania, Mekanism, Quark, Rats, Aether, Supplementaries, Nirvana, Seamless Loading Screen, DarkModeEverywhere and others. See the `ShaderInstance` transpiler in §5 L5.
- **Not in RatPack but in scope for "commonly used":** Create 6, Distant Horizons 2.x, Valkyrien Skies 2, Immersive Engineering (stencil), Ad Astra (space suit nesting), Physics Mod. These run in a second "Common" test instance (§9).

---

## 5. Target architecture
```
L9 Memory/GC discipline (cross-cutting)
L8 Robustness: guards, GL error dedupe, safe fallback
L7 Lifecycle: PipelineManager (AtomicReference + NoOp), frame-boundary swaps, reload contract
L6 Shadow: planes, culling, entity budget, skip list, [cache]
L5 Geometry adapters: terrain | entity batching | Flywheel | LOD | ShaderInstance | particles
L4 Uniforms: catalog -> dirty tracker -> compiled custom uniforms -> [UBO]
L3 Pipeline IR (render graph from shaders.properties)
L2 Compile service: preprocess -> transform -> T1 cache -> compile/link (parallel) -> T2 binary cache
L1 Pack frontend (kept from Iris)
L0 Platform: caps probe, driver quirks DB, state cache, unit allocator
```

**L0 Platform.**
- `GlCaps` probes once: GL version, extensions (`ARB_texture_view`, `ARB_get_program_binary`, `KHR_parallel_shader_compile`, `ARB_buffer_storage`, `ARB_compute_shader`, `ARB_copy_image`), `GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT`, and limits.
- `DriverQuirks` is a data file keyed by vendor, renderer and driver version (for example `parallel_link=off` on RX 7900 XTX), carrying RatOpt's per-driver results forward.
- `VisusGl` is one state cache shared with `GlStateManager`. DSA binds (`glBindTextureUnit`) update `GlStateManager.TEXTURES[unit]`, which fixes the DSA desync (§7 V-06b). Framebuffer binding is tracked in one place.
- `SamplerUnits` is the allocator for texture units and SSBO/UBO bindings: pack, Visus and third-party ranges, with conflict logging.

**L1 Pack frontend.** Kept from Iris. Option parsing, profiles, screens, include graph, id maps, dimension folders and language files are unchanged. Upstream feature-flag backports arrive in 0.8.

**L2 Compile service.**
- **Worker pool.** jcpp and the glsl-transformer stages are pure CPU, so they move to a bounded pool with one parser instance per thread. The render thread only submits GL compile and link.
- **Tier 1 cache:** transformed GLSL on disk at `.minecraft/cache/visus/t1/`.
  - Key: SHA-256 over the Visus build id, transformer version, the hash of each source stage after `#include` resolution, the option set, the macro environment (`StandardMacros` output and GPU caps), the program type, and the patch family.
  - Values are LZ4/zstd-compressed (Zstd if the jar size allows; otherwise Deflate from the JDK).
  - Byte-identical to the uncached output. Tested in T1.
- **Tier 2 cache:** program binaries via `glGetProgramBinary`.
  - Key: Tier 1 key + `GL_VENDOR`, `GL_RENDERER`, `GL_VERSION` + binary format.
  - On load, check the link status. On failure, delete the entry, re-link from source, and count failures per driver. After 2 failures, disable Tier 2 for that driver in the quirks file.
  - Off by default on drivers where it proves unreliable.
- **Parallel compile.** Use `KHR_parallel_shader_compile` where the driver supports it well. Otherwise use RatOpt's hidden shared contexts (4 by default, from the quirks DB).
- **Warm-up** covers every pipeline program and the 5 terrain programs. The apply screen shows a progress bar instead of freezing.
- **Snippet hook points:** vertex `_vert_init`-after, fragment output, and a declarations block, for the bridge API.

**L3 Pipeline IR.** `shaders.properties` and the program set are compiled into a DAG: passes, with their reads (samplers and images) and writes (render targets and SSBOs).
- First uses (0.6):
  - Skip clears for targets nobody reads before they are overwritten.
  - Skip flips with no reader.
  - Put a barrier only where a later pass reads an incoherent `imageStore` or SSBO write. Today a barrier follows every compute dispatch.
- Later (0.9, Defer): transient target aliasing.
- The IR is internal. It is **not** a new pack format, so packs keep authoring for Oculus/OptiFine. An optional `visus.*` directive may supply hints such as "this target is transient"; Oculus ignores it.

**L4 Uniforms.**
1. **Dirty tracking.** Per-program values are uploaded only when they change: `ONCE`, `PER_TICK`, `PER_FRAME`. Most of this exists already; measure it first.
2. **Compiled custom uniforms.**
   - At load, classify each `uniform.*`/`variable.*` expression as static, tick or frame from its inputs. Constant-fold it.
   - Compile the frame-rate set into one flat RPN array or a hidden class (`MethodHandles.Lookup.defineHiddenClass`, Java 15+) that reads a `float[]` register file. There are no allocations and no virtual calls.
   - stareval stays as the parser and the reference evaluator.
   - **Gate:** differential test over every expression in the 5 packs plus 10k random expressions. Results must be bit-identical or within 1 ulp, with NaN treated the same way.
3. **UBO path** (Defer → 0.9):
   - Rewrite plain `uniform` declarations of built-ins into two std140 blocks (per frame, per pass), held in a persistently mapped ring with 3 frames in flight and fences.
   - Opt-in, with per-pack fallback when the rewrite meets type conflicts or `#ifdef`-varying declarations.
   - **Gate:** at least 0.2 ms CPU saved per frame on MakeUp.

**L5 Geometry adapters.**
- **Terrain (Xenon/Embeddium).** Keep the XHFP layout (stride 40) and Atmosphera's spare bits. Fix the tangent helper. UNORM16 and sprite-local UVs are a 0.9 experiment, gated on fixing visible pinholes (Oculus #592, #844) without a frame-time regression.
- **Entity batching.** Covered in §7 V-01.
- **Flywheel.** Covered in §4.6.
- **LOD.** Covered in §4.5.
- **Mod `ShaderInstance`s** (0.7, the Oculus-Unlocked idea):
  - Shaders registered through `RegisterShadersEvent` that Visus does not override today vanish or fall back.
  - Transpile them with a light AST pass: route `fragColor` to the pack's albedo target, write neutral defaults to the other active `RENDERTARGETS`, and keep the mod's uniforms.
  - Allow-list and deny-list by shader `ResourceLocation`, with a debug overlay that names each one.
- **Particles.** Keep the phased engine, but iterate types in Forge comparator order (`ForgeHooksClient.makeParticleRenderTypeComparator`). Classify modded types as opaque or translucent by their render state, not by an equality set.

**L6 Shadow.**
- Clip-plane extraction that works for [-1,1], [0,1] and reversed-Z (§7 V-04).
- An entity and block-entity **budget**: a distance cap and a per-frame time slice, and reuse of the main-pass pose where it is valid (RatOpt `shadow_entity_budget` idea).
- The CoO skip list (§4.4).
- A section culling hook for the RatShadow idea.
- **Defer (0.9):**
  - A static/dynamic shadow cache. Before it can be on for a pack, AST analysis must show that `shadow.vsh`/`shadow.gsh` do not read `frameTimeCounter`, `worldTime`, `frameCounter`, wind or wave uniforms (most packs wave foliage there).
  - GPU frustum and Hi-Z culling with multi-draw indirect.
  - Texel snapping (opt-in, only for packs without shadow distortion).

**L7 Lifecycle.**
- `PipelineManager` holds an `AtomicReference<WorldRenderingPipeline>`, initialised to a stateless `NoOpPipeline` (null-object pattern).
- Mixins never keep a nullable copy. `MixinLevelRenderer` hooks read through an accessor that cannot return null.
- Pipeline destroy and create happen only at the frame boundary, after `RenderSystem.flipFrame`.
- The reload contract: API state updates first, then `allChanged()`, and listeners get `onPipelineDestroyed` and then `onPipelineCreated`.

**L8 Robustness.**
- The NaN and tangent guards (§7).
- GL debug output, where available, is de-duplicated by (id, source), with a rate limit and one summary per minute. This replaces per-frame spam.
- **Safe fallback.** If a pack fails to compile or link, render vanilla, show the error screen (file and line mapped back through `#include`) and a toast. Never crash.
- Group and phase state self-heal at each frame start.

**L9 Memory and GC.**
- **Steady-state frame allocation target:** 0 B/frame on the render thread from Visus code, measured with JFR `ObjectAllocationSample` filtered to Visus packages using `Atmosphera/tools/jfr_alloc.py`.
- Use LWJGL `MemoryUtil` and `MemoryStack` plus pooled `ByteBuffer`s for off-heap data. Never call `sun.misc.Unsafe` directly: JDK 24+ warns, and RatPack already needs `--sun-misc-unsafe-memory-access=allow`.
- No capturing lambdas, boxing, varargs or iterators in hot paths.
- Prefer primitive collections. fastutil already ships with MC.
- Tune for ZGC with `UseCompactObjectHeaders`, which is how RatPack runs: fewer short-lived objects matter more than a small heap.

---

## 6. Platform matrix
| Axis | Supported | Notes |
|---|---|---|
| Java | 17, 21, 25 (Temurin) | Compile with `--release 17` (Java 17 toolchain). Test 25 with RatPack's flags: ZGC, `UseCompactObjectHeaders`, `--enable-native-access=ALL-UNNAMED`. A multi-release jar for FFM or Vector API is used only if a spike proves SecureJar loads `META-INF/versions` correctly (*verify in 0.1.0*). No `jdk.incubator` modules. |
| Forge | 47.2.0+ (dev on 47.4.16, RatPack 47.4.23) | |
| Chunk renderer | Xenon 0.3.31, Embeddium 0.3.x | Also works with neither, on the vanilla path |
| GL | 3.2 core baseline; tiers 4.3 (texture view, copy image, compute), 4.5 (DSA), 4.6 | macOS (4.1): no compute, no texture view; the copy fallbacks are used |
| GPU | AMD (RX 7900 XTX, primary), NVIDIA, Intel, Mesa (Linux) | Per-vendor quirks in the L0 DB |
| OS | Windows (primary), Linux, macOS (best effort) | |

---

## 7. Bug ledger
| ID | Symptom | Root cause | Fix | Proof test | Release |
|---|---|---|---|---|---|
| V-01 | `IllegalStateException: Already in a group` (Iris #2145, #2827, #2879); depthless buffers drawn in reverse (#2897) | `GraphTranslucencyRenderOrderManager` uses a non-re-entrant boolean `inGroup`; a renderer that throws never calls `endGroup`; FAS and toposort lose insertion order | Re-entrant depth counter that self-heals at frame start. A 64-bit sort key: pass (3 bits), group (16), sequence (21), type (24). Opaque types batch by type; translucent and depthless keep FIFO order within the group, and groups stay in submit order. The old graph manager stays behind a debug option until 0.7. | T0: nested start/end, an exception mid-group, FIFO order for depthless types. T5: Curios + armor + Mekasuit nesting. | 0.2 |
| V-02 | Black or blown-out screen after a NaN (exposure, `centerDepthSmooth`) | `SmoothedFloat` and `centerDepth.fsh` feedback lets NaN or Inf latch | Sanitise inputs and the accumulator, clamp `dt`, half-life ≤ ε means pass-through, use `fma`. Same guard in GLSL. | T0 property test (jqwik) over NaN, Inf and zero half-life | 0.2 |
| V-03 | TAA and bloom NaN smears on modded models | `NormalHelper` divides by a zero UV determinant; NaN packs to 0; `normalize(0)` gives NaN | Threshold the determinant, Gram–Schmidt against the normal, Frisvad orthonormal-basis fallback. Shared by entity, terrain and Flywheel paths. | T0: degenerate, collinear and single-texel UVs never produce a zero or non-unit tangent | 0.2 |
| V-04 | Shadow culling overshoot under non-standard projections | Gribb–Hartmann near/far planes assume [-1,1] | Detect the convention from the projection matrix; handle [0,1] and reversed-Z, and the infinite-far case | T0 against JOML-built projections of each convention | 0.2 |
| V-05 | NPE "this.pipeline is null" (Iris #2560) when sky or clouds render outside `renderLevel`, or on a mid-frame reload | Nullable copy of the pipeline in `MixinLevelRenderer` | §5 L7 | T5: call `renderSky`/`renderClouds` outside `renderLevel`; trigger a reload mid-frame | 0.2 |
| V-06 | `GL_INVALID_OPERATION: Depth formats do not match` spam; broken `depthtex1`/`depthtex2` with stencil mods (Oculus #855) | `DepthCopyStrategy` blits or copies between `DEPTH24_STENCIL8` and `DEPTH_COMPONENT*` | Allocate Visus depth textures in the main target's format (stencil aware). Copy-strategy choice requires matching formats; fall back to `glCopyTexSubImage2D`. Texture views in 0.7. | T5: stencil-enabled main target; GL debug log empty | 0.2 |
| V-06b | Mod buffers render in the wrong order after Forge stage events | DSA binds bypass the `GlStateManager` cache; blend and depth overrides are deferred | L0 single state cache | T5 with Atmospherics' horizon fade (remove its workaround in a test build) | 0.2 |
| V-07 | Micro-holes and seams on blocks (Oculus #592, #844) | FP16 UV and midTexCoord quantisation in the compact format | Experimental UNORM16, sprite-local UVs, midpoint computed from quantised endpoints | T3 zoomed screenshots, A/B | 0.9 (Defer) |
| V-08 | Contraptions invisible or black, no shadows, `_flw_` uniform spam (iris-flw-compat #185, #242; Oculus #786) | Hard-coded `flw_light` arity; shadow pass without light LUT or matrices; no negative-location memo | §4.6 | T5 Vanillin + Create 6 | 0.3 |
| V-09 | GeckoLib animations freeze or stutter with shadows on | Shadow pass consumes the per-tick animation update | §4.7 | T5 GeckoLib set | 0.3 |
| V-10 | Modded particles missing or in the wrong order (Oculus #149) | Unordered key-set filter | §5 L5 particles | T5: particle-heavy scene, compared to vanilla order | 0.3 |
| V-11 | 45–77 s apply stall on AMD; terrain programs link late (up to 912 ms) | Serial link; no warm-up for terrain programs | §5 L2 | T4: apply-time A/B | 0.3 (parallel), 0.5 (cache) |
| V-12 | Multi-second main-thread freezes on pack load | ANTLR transform on the render thread; in-memory LRU cache only | Worker pool + Tier 1 cache | T4 cold/warm apply | 0.5 |
| V-13 | NeoVoxy #10, #29, #30 | Unit clash, state drift, re-entrant reload | §4.5 | T5 NeoVoxy LODs on all 5 packs | 0.4 |
| V-14 | Stale framebuffer-bind skips even with shaders off (Atmospherics workaround) | Redundant-bind cache out of sync with real binds | L0 state cache | T5 Atmospherics with its workaround off | 0.2 |
| V-15 | Mod `ShaderInstance` visuals vanish under packs (Oculus #809) | No route into the G-buffer | §5 L5 transpiler | T5 mod-shader gallery scene | 0.7 |
| V-16 | With debug options on, a "Failed to write debug patched shader source" `NoSuchFileException` is logged for every irisflw program (found in 0.1.0 dump runs, same on Oculus) | `ShaderPrinter` builds the file name from a program name containing `/` and never creates the parent folders | Sanitise the name (`/` to `_`) or create the parent folders | T1 dump run with irisflw installed: no warnings, files present | 0.2 |
| V-17 | 7 copies of GL error 0x4AE (API, ERROR, HIGH) in one burst at world join, with debug options on, on Photon, CU, Glimmer and MakeUp. Found by the 0.2.0 L8 logging. The code path is unchanged from Oculus 1.8.0, so Oculus has it too (it only printed to stderr) | `ExtendedShader.apply()` skipped `glUseProgram` when its static `lastApplied` was this shader. irisflw's `bind()` runs `apply()` while Flywheel compiles a program, then Flywheel unbinds with `glUseProgram(0)` without `clear()`. The first real draw skipped the bind and uploaded uniforms with no program bound (`GL_INVALID_OPERATION`). Shown by a diagnostic build: `skippedBind=true`, current program 0, upload error 0x502 | Always bind in `apply()` (same rule as V-14: bind caches never skip) | T1 dump run on Photon with the Flywheel rig: no `[GL] Debug message` lines | 0.2.1 ✅ |
| V-18 | GL error 0x3F6 (API, ERROR, HIGH) from `glDeleteProgram` once per pipeline destroy (dimension change, pack toggle), with irisflw 2.0.3 and a pack loaded. Found by the 0.2.1 T5 run | irisflw's `MixinPipelineManager` injects at `destroyPipeline` TAIL and deletes its Flywheel `GlProgram`s, whose handles are `ExtendedShader` programs the pipeline has already deleted. The same code runs in Oculus 1.8.0 (`destroyPipeline` is unchanged), so this is not a Visus regression. Harmless: a second delete of a freed name changes nothing | The 0.3.0 native Flywheel backend owns those programs and deletes each once | T5 s1/s9 run: no `[GL] Debug message` lines | 0.3 |

Every fix ships with a test that fails without it.

---

## 8. Documentation for pack authors (`docs/packdev/`, 0.8)
1. **Uniforms reference.** Generated at build time from annotations on the uniform catalog: name, GLSL type, update frequency, units, coordinate space, the Oculus/OptiFine equivalent, and notes.
2. **`shaders.properties` reference.** Every directive, with examples and the default when absent. Plus `block`/`item`/`entity.properties`, dimension folders, and the custom texture and image syntax.
3. **Feature flags.** The `IRIS_FEATURE_*` defines and `iris.features.required/optional`, with what Visus supports.
4. **Visus extensions.** Optional `visus.*` directives and `VISUS` defines (`#ifdef VISUS`). Oculus ignores all of them, so one pack works on both.
5. **Debugging guide:**
   - The in-game error screen, with line numbers mapped back through `#include`.
   - `F3+R` behaviour.
   - Labels per pass in RenderDoc via `GL_KHR_debug`.
   - The pass timing overlay (GPU timer queries).
   - A render-target viewer that shows colortex, depth and shadow targets.
   - A dump of the transformed sources (`/visus dump`).
6. **Mod integration.** The `visus-api` overview for mod authors: snippets, id assist, option overrides, LOD providers.
7. **Differences from Oculus.** Target: none. Every deliberate difference is listed with the release that made it.

---

## 9. Testing concept

### 9.1 Tiers
| Tier | What | How | Pass rule | Who runs it |
|---|---|---|---|---|
| T0 Unit | Math (tangent, planes, smoothing), sort keys, parsers, uniform compiler (differential), cache keys, IR passes | JUnit 5 + jqwik, `gradlew test`, no GL | All green | Haiku 5.5 |
| T1 Transform golden corpus | Transformed GLSL for every program of the 5 RatPack packs (plus BSL, Complementary Reimagined + Euphoria Patches, Sildur's and Bliss as extra coverage), for the default and 2 stress option sets | A `transformDump` task feeds Oculus 1.8.0 (reference jar on the classpath) and Visus the same inputs. It diffs the outputs, then runs `glslangValidator` on each (offline, no GPU). | Byte-identical unless the change is listed in `golden/CHANGES.md`; glslang finds no new errors | Haiku 5.5 |
| T2 ABI | `abi-census` + `abiCheck` | Gradle | No missing members | Haiku 5.5 |
| T3 Scenes | Screenshots of fixed scenes against Oculus 1.8.0 references | Prism `RatPack 2.9 - TestClient` copy with `--world VisusTest --offline`, `Atmosphera/tools/*_scene.txt`, `shot_diff.py` | Diff under threshold, or an expected-change note | Sonnet 5.5 |
| T4 Performance | FPS, 1 % lows, p50/p95/p99, render-thread CPU, apply time, allocation rate | `clientbench/bench.py run <tag> --remove-mod oculus-mc1.20.1-1.8.0 --add-mod visus-<v>.jar`, at least 3 runs per side, alternating | A difference counts only if it is ≥ 8–10 % (noise is ±5–6 %); regressions over 3 % block the release | Sonnet 5.5 |
| T5 Compatibility | Scenario list in §9.2 | Prism instances: `RatPack 2.9 - TestClient` and a `Visus - Common` instance (Create 6, DH 2.x, VS2, IE, Ad Astra) | Scenario checklist all green; no new `ERROR` lines in the log | Sonnet 5.5 (triage), Opus (fixes) |

**clientbench changes needed (0.1.0):**
- A `config_patches` entry that writes `oculus.properties`: `enableShaders=true`, the pack, `maxShadowRenderDistance`.
- `BUCKETS` rules for `net.irisshaders`/`dev.visus` classes (around `bench.py:374`).
- Run with AMDium off, because AMDium bypasses Xenon's draw path.
- Record apply time from a Visus log line.

### 9.2 Compatibility scenarios
1. Toggle the pack on, off and on; switch packs; apply options. Then `F3+T` and `F3+R`. Check reload ordering with AtmospheraLib's debug line.
2. Each of the 5 packs: overworld day, night and rain; underwater; Nether; End; Aether (Aether sky).
3. Vanillin entities and block entities (RatPack). Create 6 contraption, train, shafts and cogs (Common), in the main and shadow passes.
4. The GeckoLib mob set, animated in both passes.
5. Player with Curios + modded armor + elytra + Mekasuit. Ad Astra space suit (Common). Look for the "Already in a group" crash.
6. A particle-heavy scene (Visuality, Cave Dust, Wakes, Simple Blood, Falling Leaves).
7. Atmosphera: Gale waving, Torrente water with the pack off; Lumis shafts under MakeUp `VOL_LIGHT=0`; Torrente fog under Solas without `WATER_FOG`.
8. NeoVoxy LODs on all 5 packs, including `voxy.json` packs. DH 2.x (Common).
9. Stencil-enabled main target (IE in Common, or a test mixin); GL debug log stays clean.
10. Mod shader gallery: Botania, Mekanism, Quark, Rats, Supplementaries, Nirvana, Seamless Loading Screen.
11. ETF/EMF custom models, Fusion connected textures, Polytone colormaps.
12. The same launch on Java 17, 21 and 25.

### 9.3 Delegation model
- **Haiku 5.5** gets deterministic runbooks: one command, a known pass rule, summarise failures. That covers T0, T1, T2, grepping logs for new `ERROR`/`WARN` lines, and changelog formatting. Give it the runbook from `VISUS-ROADMAP.md` only.
- **Sonnet 5.5** gets in-game runs with screenshot or bench triage, fixes that already have a failing test, and documentation pages. Give it the release capsule plus the architecture section it names.
- **Opus** gets the transformer and IR, GL lifecycle, the state cache, API design, and any bug without a reproduction.

---

## 10. Risks
| Risk | Mitigation |
|---|---|
| Upstream Iris and Oculus move on and the fork drifts | We only backport pack-visible features (0.8). The 1.20.1 Oculus line is quiet. Track it in `docs/upstream.md`. |
| Program binary blobs misbehave on some driver versions | Per-driver self-check, auto-disable after 2 failures, a user toggle, off by default where it fails |
| Removing add-on mixin configs early is fragile | Fallback to the `(,0)` incompatibility declaration with a clear message |
| LGPL/MIT boundary is crossed by accident | `visus-api` is its own Gradle module that depends on nothing Oculus-derived, plus a CI check that no `net.irisshaders` import appears there. Atmosphera depends only on `visus-api`. |
| NeoVoxy and Visus lockstep slips | NeoVoxy keeps its 1.8.0 mixin path as a fallback. Releases are paired in the roadmap. |
| Mods outside the census reach into internals | Re-run the census for each modpack update; the Common instance covers popular mods |
| "Drop-in" regression in an edge pack | The T1 golden corpus makes any transformer change visible; the safe fallback prevents crashes |
| The perf case is weaker than hoped | Every perf item has a measure-first gate and is dropped if the gate fails (Aegis rule) |

---

## Appendix A: verdicts on every research-doc item
| Item (doc) | Verdict | Reason | Release |
|---|---|---|---|
| Re-entrant groups + sort-key radix (A-B1, A-F2, B-1.1) | **Adapt** | Depth counter adopted. Sort key keeps submit order across translucent groups instead of camera distance, because distance would change pack output. A sort is only needed for opaque. | 0.2 / 0.6 |
| Tangent singularity + Frisvad (A-B2, B-1.4) | **Adopt** | | 0.2 |
| `flw_light` arity, shadow binding, uniform spam (A-B3, B-1.3) | **Adopt** | Folded into the native backend | 0.3 |
| Projection-agnostic planes (A-B4, B-1.2) | **Adopt** (low severity) | Vanilla 1.20.1 is not reversed-Z | 0.2 |
| NaN-safe smoothing (A-B5) | **Adopt** | | 0.2 |
| Depth/stencil format match (A-B6, B-1.6) | **Adopt** | Texture views come in 0.7 | 0.2 |
| XHFP UV quantisation (A-B7, A-F9) | **Defer** | Vertex format change; needs A/B | 0.9 |
| Null pipeline (A-B8, B-1.5) | **Adopt** | Null object + frame-boundary swap | 0.2 |
| ANTLR allocation storm (B-1.7) | **Adapt** | Worker pool + cache, not a new parser | 0.5 |
| Persistent-mapped UBO ring (A-F1, B-2.2) | **Defer** | Measure glUniform cost first; per-pack fallback | 0.9 |
| Compiled custom uniforms (A-F3, B-2.2) | **Adapt** | Differential test against stareval | 0.6 |
| Two-tier disk cache (A-F4) | **Adopt** | | 0.5 |
| GPU shadow culling, Hi-Z + MDI (A-F5, B-3.2) | **Defer** | Needs deep Xenon integration | 0.9 |
| Geometry SPI replacing irisflw (A-F6, B-2.3) | **Adapt** | Native Flywheel backend + LOD API. Lifting shaders to "surface functions" is rejected because it changes pack semantics. | 0.3 / 0.4 |
| Texel snapping (A-F7) | **Defer** | Invalid with shadow distortion; opt-in only | 0.9 |
| Octahedral normals and tangent angle (A-F8, B-3.4 QTangent) | **Defer** | Small gain, format churn | 0.9 |
| GPU SPD mipmaps for PBR (A-F10) | **Defer** | Reload-time gain only | 0.9 |
| Static/dynamic shadow cache (A-F11) | **Defer** | Gated by AST time-uniform analysis | 0.9 |
| `ShaderInstance` transpiler + stencil views (A-F12) | **Adopt** | | 0.7 |
| Declarative render graph (B-2.1) | **Adapt** | Internal IR only; no new pack format | 0.6 |
| Visibility buffer + compute deferred (B-3.1) | **Reject** | Packs own the G-buffer | — |
| Per-pixel linked-list OIT, WBOIT (B-3.3) | **Reject** | Packs own translucency | — |
| 32-byte aligned terrain vertex (B-3.4) | **Reject** | Breaks Atmosphera spare bits and Xenon's format for little gain | — |
| Virtual CSM / SDSM (B-3.5) | **Reject** | Packs own shadow projection and distortion | — |
| 3D light volume LUTs for sub-levels (B-3.6) | **Adapt** | Only as Flywheel's own LUT binding in the shadow pass | 0.3 |
| SVGF SDK (B-3.7) | **Reject** | Pack-side technique; document only | — |
| Bindless textures (B-3.8) | **Reject** | Vendor coverage | — |
| Compute exposure, ACES/AgX (B-3.9) | **Reject** | Pack-side | — |
| DH reversed-Z / G-buffer fusion (B-3.10) | **Adapt** | Keep the DH compat behind the LOD API | 0.4 |
| C2 escape analysis discipline (B-4.1) | **Adopt** as a coding rule | Small hot methods, no escaping temporaries | all |
| Decoupled GL thread + command tape (B-4.2) | **Reject** | Mods call raw GL; Ixeris already moves GLFW | — |
| GraalVM native image or native sidecar (B-4.3) | **Reject** | Distribution, AV flags, platform matrix | — |
| Vector API / AVX2 (B-4.3/4.4) | **Reject** | Needs `--add-modules`; not drop-in | — |
| SoA culling data (B-4.4) | **Adapt** | Only inside L6 if the culling hook needs it | 0.9 |
| Off-heap arenas (B-4.5) | **Adapt** | LWJGL `MemoryUtil`/`MemoryStack` + pools; no FFM unless a multi-release jar is proven | all |
| SVDAG / Aokana GI (B-4.6) | **Reject** | Out of scope for a pack loader | — |
| Tiered compression + BC7 textures (B-4.7) | **Adapt** | Cache compression only; no texture recompression | 0.5 |
| Compute meshing (B-4.8) | **Reject** | That is Xenon's or AMDium's job | — |

(A-Bn / A-Fn = Code Analysis bug n / feature n; B-x.y = Blueprint section x.y.)
