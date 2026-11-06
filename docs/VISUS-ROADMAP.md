# Visus: release roadmap

- **Date:** 2026-11-06. Design and reasons live in `VISUS-ARCHITECTURE.md` (cited as **A§n** below).
- **How to use this file:** read **Standing context**, then only the capsule for the release you are working on, then only the A§ sections that capsule lists. Each capsule is self-contained.

## Standing context (every agent reads this; keep it ≤ 15 lines)
```
Root      C:\Users\epici\Desktop\My Modding Development\Original Mods\Forge\[1.20.1] Visus
Build     Architectury Loom 1.4 (as upstream Oculus), Forge 47.1.105 (neoforged maven), official mappings, Java 17
          JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot ; gradlew --no-daemon build (runs abiCheck)
Commands  gradlew build | gradlew test | gradlew abiCheck | gradlew checkForbiddenResources
Jar       build/libs/visus-1.20.1-<ver>.jar   mod ids: alias oculus (first; 1.8.0 until 0.3.0, then 1.8.100) + visus
T1 dump   bench.py run <tag> --shaders <pack.zip> --dump-shaders [...]; python -I tools/golden/diff_dumps.py <ref> <cand>
Client    Prism "RatPack 2.9 - TestClient" (copy it to "Visus - Test" before swapping jars)
          prismlauncher.exe --launch "Visus - Test" --world VisusTest --offline VisusTester
Bench     cd ..\clientbench && python bench.py run <tag> --remove-mod oculus-mc1.20.1-1.8.0 --add-mod <jar>
Packs     ComplementaryUnbound r5.9.3, Glimmer 1.5.2, MakeUp-UltraFast 9.5e, Solas 3.7 RatPatch, Photon 1.3b RatPatch
Version   +0.0.1 per rebuilt change; +0.1.0 when a change adds/removes 500+ lines (patch -> 0); 1.0.0 only on request
          Every bump gets a CHANGELOG.md entry (## x.y.z (date), bold feature names, "Measured:" lines)
Gate      Perf items: measure first; if the gate fails, drop the item and note it under "Not adopted"
Never     ship assets/sodium/shaders/blocks/block_layer_opaque.vsh | break abi/abi-freeze.txt | copy Oculus code into visus-api
Licence   Visus jar AGPL-3.0-only (glsl-transformer); Oculus/Iris files stay LGPL-3.0; visus-api MIT, from scratch
```

## Rules
- **Budgets:**
  - No frame-time regression ≥ 3 % (p50 or p99) on any of the 5 packs at 2560x1440 and 1280x720, compared with Oculus 1.8.0 in clientbench.
  - Transformed GLSL stays byte-identical to Oculus 1.8.0 (T1) unless the change is listed in `golden/CHANGES.md`.
  - Steady-frame allocation from `dev.visus`/`net.irisshaders` code on the render thread trends to 0 B/frame. Regressions are flagged by `jfr_alloc.py`.
- **Each fix gets a test that fails without it** (Aegis rule).
- **Release numbers.** Most releases below change 500+ lines, so they start a new minor (0.N.0). Patch releases in between follow the bump rule. The numbers here are targets; the actual number follows the rule.
- **NeoVoxy lockstep.** Any release that changes `dev.visus.api.lod` ships with a matching NeoVoxy release, recorded in both changelogs.

## Test tiers (details in A§9)
| Tier | Command / tool | Pass rule | Model |
|---|---|---|---|
| T0 unit | `gradlew test` | all green | Haiku 5.5 |
| T1 golden transform | clientbench `--shaders <pack> --dump-shaders` for Oculus and for Visus, then `python -I tools/golden/diff_dumps.py <oculus>/patched_shaders <visus>/patched_shaders --allow golden/CHANGES.md` | PASS (identical, or listed in `golden/CHANGES.md`) | Sonnet 5.5 runs the game; Haiku 5.5 runs the diff |
| T2 ABI | `gradlew abiCheck` | no missing members | Haiku 5.5 |
| T3 scenes | Prism `Visus - Test` + `..\[1.20.1] Atmosphera\tools\*_scene.txt` + `shot_diff.py` | diff under threshold or an expected-change note | Sonnet 5.5 |
| T4 perf | clientbench, ≥ 3 alternating runs per side | changes ≥ 8–10 % only; regressions ≤ 3 % | Sonnet 5.5 |
| T5 compat | A§9.2 scenario list in `Visus - Test` and `Visus - Common` | checklist green, no new ERROR lines | Sonnet 5.5 triage, Opus fixes |

### Runbook template for delegated test agents (paste as the prompt)
```
You are running Visus test tier <Tn> for release <ver>. Read only: CLAUDE.md, docs/VISUS-ROADMAP.md
"Standing context" and the "Test tiers" row for <Tn>. Run exactly: <command>. Do not edit source files.
Report: PASS/FAIL, counts, and for each failure the test name, the first 15 lines of output and the file:line.
If a command fails to start, report the error and stop.
```

## Overview
| Release | Theme | Needs | Main content | Delegate |
|---|---|---|---|---|
| 0.1.0 | Baseline | none | Fork import, identity, ABI census, golden references, test harness | Opus build; Haiku T1/T2 |
| 0.2.0 | Stability | 0.1 | Bug ledger V-01…V-06b, V-14; GL error dedupe; safe fallback | Sonnet fixes; Haiku T0 |
| 0.3.0 | Absorb | 0.2 | Native Flywheel; RatOpt compile service; CoO skips; GeckoLib and particle fixes; add-on guard | Opus Flywheel; Sonnet rest |
| 0.4.0 | APIs | 0.3 | `visus-api` (MIT): LOD API + NeoVoxy migration; Atmosphera bridge; DH adapter | Opus API; Sonnet NeoVoxy port |
| 0.5.0 | Fast load | 0.3 | Worker-pool transform; Tier 1 / Tier 2 disk caches | Opus; Sonnet T4 |
| 0.6.0 | Frame cost | 0.5 | Dirty uniforms; compiled custom uniforms; sort-key batcher; shadow budget; IR clear/flip skipping | Opus IR; Sonnet T4 |
| 0.7.0 | Mod shaders | 0.6 | `ShaderInstance` transpiler; stencil-aware depth via texture views; macOS tier | Opus |
| 0.8.0 | Pack SDK | 0.7 | `docs/packdev`; debug tools; upstream feature-flag backports | Sonnet docs; Opus backports |
| 0.9.0 | Experimental | 0.8 | Defer items, each gated and off by default | Opus; Sonnet T4 |
| RC | Release candidate | 0.9 | Full matrix, Java 17/21/25, Common instance | Sonnet; Opus fixes |
| 1.0.0 | Only when the user calls it | RC | — | — |

---

## 0.1.0 "Baseline"
- **Goal:** a Visus jar that behaves exactly like Oculus 1.8.0, with the scaffolding every later release needs.
- **Context to load:** A§1, A§3, A§4.1, A§4.2, A§9.1.
- **Scope:**
  1. **Import** the Oculus 1.8.0 source from the Asek3/Oculus tag that matches the jar. Check by diffing class lists against `referencemods/oculus-mc1.20.1-1.8.0.jar`.
     - Keep the copyright headers.
     - Add `LICENSE` (LGPL-3.0), `NOTICE` (Iris and Oculus authors, bundled libs and their licences) and `docs/CHANGES-FROM-OCULUS.md` (dated).
     - Copy nothing from the decompiled jar.
  2. **Build:** convert to the RatPack Gradle style (FG6, official mappings, Java 17 toolchain, `processResources` placeholders). Add Gradle modules `visus` (LGPL) and `visus-api` (MIT, empty for now).
  3. **Identity:** `mods.toml` with `visus` + the `oculus` alias + `provides=["iris"]` (A§4.1). Rebrand the logo and the mod-list name.
     - The config file and the `shaderpacks/` layout stay unchanged.
     - Add a build check that fails if `assets/sodium/shaders/blocks/block_layer_opaque.vsh` is in the jar.
  4. **ABI census:** `tools/abi-census` (ASM CLI) run over the TestClient `mods/` writes `abi/abi-freeze.txt`. Add the `gradlew abiCheck` task.
  5. **Golden references** from the Oculus 1.8.0 jar:
     - **T1:** a `transformDump` task that runs the transformer headlessly over each pack and option set, for both Oculus and Visus. Corpus: `golden/<pack>/<optionset>/`.
     - **T3:** reference screenshots for each scene × pack.
     - **T4:** baseline bench tags `oculus-<pack>-hi-1..3`.
  6. **clientbench changes:**
     - A `config_patches` entry for `oculus.properties`: shaders on, pack pinned.
     - Visus `BUCKETS` rules (around `bench.py:374`).
     - An apply-time log line parser.
  7. **Spikes** (one paragraph each, written to `docs/spikes/`):
     - Does FML 47 honour `provides`?
     - The glsl-transformer licence.
     - Does SecureJar load multi-release jar entries?
     - The upstream Iris feature-flag diff.
- **Invariants:** no behaviour change. Nothing in `net.irisshaders` is renamed.
- **Acceptance:**
  - T1 byte-identical on all 5 packs × 3 option sets.
  - T3 pixel diff ≈ 0, allowing for TAA noise.
  - T4 within noise.
  - T2 green.
  - The TestClient boots with Visus in place of Oculus, with irisflw still installed (the guard comes in 0.3).
- **Delegation:** Opus does steps 1–4. Sonnet does 5–6 and the spikes. Haiku runs T1 and T2 and summarises.
- **Status (2026-11-06):** steps 1–4, 6 and 7 are done. Step 5 is partly done.
  - Oculus 1.8.0 T1 reference dumps exist for all 5 packs: `clientbench/results/oculus-{cu,photon,glimmer,makeup,solas}-dump-1`.
  - Visus 0.1.0 matched on CU and Photon.
  - Still open: option-set variants, T3 screenshots, and T4 baseline tags with ≥ 3 runs per side.
- **Done differently from the plan:**
  - The build stays on **Architectury Loom**, as upstream does, rather than moving to FG6. Changing the build system
    would put byte-for-byte parity at risk. Revisit only if Loom gets in the way.
  - **There is no 1.8.0 tag** upstream. The base is commit `5751a2f6b`, verified against the released jar.
  - **T1 dumps come from the game** (bench `--dump-shaders`, which uses Iris's own `ShaderPrinter`), not from a headless
    `transformDump` task. Pack transforms depend on GL capabilities, so a headless dump would not be equivalent.
  - **The `oculus` alias stays 1.8.0**, not 1.8.100, until 0.3.0. RatOpt's `oculus_shaders` module (parallel compile)
    must keep working until Visus absorbs it. The alias must also be the first `[[mods]]` entry.
  - **Spike result:** glsl-transformer is AGPL-3.0 (`docs/spikes/licences.md`). **Decided:** from 0.1.1 the Visus jar is AGPL-3.0-only, with an attribution term under section 7(b).
  - **irisflw 2.0.3 works unchanged on Visus 0.1.x** and is still required for Flywheel instancing under shaders.
    - Flywheel 1.0.4 marks its own `instancing` and `indirect` backends unsupported while `isShaderPackInUse()` is true.
    - The title-screen fallback from `irisflw:iris_instancing` to `flywheel:indirect` happens before any pack is active, and Oculus logs it too.
    - In the world, `IrisInstancedDrawManager` draws.
    - New bug V-16 (debug-dump warnings) was found here.

## 0.2.0 "Stability"
- **Goal:** remove the known crash and corruption bugs without changing pack output.
- **Context to load:** A§5 (L0, L7, L8), A§7 rows V-01…V-06b and V-14.
- **Scope:**
  - V-01: re-entrant group depth with frame-start self-heal; sort key with FIFO preserved. The graph manager stays as `debug.legacyTranslucencyOrder`.
  - V-02: NaN-safe smoothing (CPU `SmoothedFloat`, `SmoothedVec2f`; GPU `centerDepth.fsh`).
  - V-03: guarded tangent helper, shared by `NormalHelper`, entity and terrain.
  - V-04: clip planes that handle every depth convention.
  - V-05: `PipelineManager` with `AtomicReference` + `NoOpPipeline`; accessor that cannot return null; swaps at the frame boundary.
  - V-06: depth texture format follows the main target; copy strategy requires matching formats.
  - V-06b and V-14: L0 state cache (DSA binds update `GlStateManager`; one framebuffer-binding tracker).
  - L8: GL debug message dedupe and rate limit. Safe fallback when a pack fails to compile: vanilla rendering, error screen, toast.
- **Invariants:** T1 is still identical. `centerDepth.fsh` is the only GLSL change, and it is listed in `golden/CHANGES.md`.
- **Acceptance:**
  - T0: each V-ID has a test that fails without the fix.
  - T5 scenarios 1, 5 and 9 pass.
  - T4 within noise.
  - No `Already in a group` in 30 minutes of the nesting scene.
- **Delegation:** Opus does V-05 and the L0 state cache. Sonnet does V-01…V-04, V-06 and L8, each starting from a failing test. Haiku runs T0, T1 and T2.
- **Status (2026-11-06): built, not yet fully accepted.**
  - Done:
    - V-01 (re-entrant counter and self-heal), V-02, V-03, V-04, V-05, V-06, V-14, V-16 and L8 (rate limit, game-log copies, toast).
    - V-17 in 0.2.1: GL error 0x4AE on the first Flywheel draw (stale `ExtendedShader` bind skip, the same as in Oculus 1.8.0).
    - Upstream Oculus fixes #760 and #763.
  - Passed so far:
    - T0: 36 tests.
    - T1: identical on all 5 packs.
    - T2: 0 missing.
    - T4 on Photon: within noise, 3+3 runs.
  - Still open:
    - T5 scenarios 1, 5 and 9.
    - The 30-minute nesting scene.
    - T4 on a second pack.
- **Done differently:**
  - **V-01:** only the crash fix shipped. The sort-key ordering with FIFO order moved to 0.6, item 4, because it changes draw order and needs T3.
  - **V-02:** Oculus's arithmetic is kept bit for bit (no `fma`), so finite results do not change.
  - **V-03:** the fallback applies only to degenerate tangents. Gram–Schmidt on every tangent would have changed vertex data, and so the pixels.
  - **V-04:** the forward [-1,1] and [0,1] conventions were already conservative, and reversed-Z already worked, as plain plane inequalities. The real defect was the zero-normal plane of an infinite far plane, so that is what was fixed.
  - **V-05:** no `AtomicReference`, because only the render thread touches the pipeline. Instead there is a `NoOpPipeline`, a destroy-generation check, and a deferred `Iris.reload()`.
  - **V-06b:** moved to 0.3, item 8.
  - **V-14:** the bind caches are now track-only. The program cache was a real stale-skip hazard, because Xenon binds programs through raw GL.
  - **L8:** the existing vanilla fallback and chat line are kept, and a toast is added. The `#include`-mapped error screen stays in 0.8.
  - **clientbench:** every run now ends with a GPU cooldown (3 min, or 4 min after a run over 10 min).

## 0.3.0 "Absorb"
- **Goal:** one jar replaces Oculus + irisflw + particle fix + GeckoLib fix + RatOpt `oculus_shaders` + CoO `[oculus]`.
- **Context to load:** A§4.3, A§4.4, A§4.6, A§4.7, A§5 L2 (parallel, warm-up), A§5 L5 particles, A§7 V-08…V-11.
- **Scope:**
  1. **Native Flywheel 1.0.x backend:**
     - Choose the `flw_light` call from the AST (3, 4 or 5 arguments).
     - In the shadow pass, bind the LUT, cull data and matrices.
     - Memoise uniform locations, including -1.
  2. **Compile service:**
     - Port RatOpt's `parallel_contexts` / `parallel_link` design with its per-driver self-check.
     - Seed the quirks DB from `ratoptimization-oculus-driver.properties`.
     - Warm up every program, including the 5 terrain programs, with a progress screen on apply.
     - Port `transform_cache`'s stable key; the in-memory version is enough until 0.5.
  3. **Shadow skip list** from CoO: sign text, glint, name tags, banner patterns. Migrate CoO's config values once.
  4. **GeckoLib 4.8.x:** per-frame animation state with a read-only shadow pass (injection, version guarded).
  5. **Particles:** Forge comparator order; classify by render state.
  6. **Add-on guard:** remove the mixin configs of irisflw, oculusparticlefix, geckoanimfix and Oculus Unlocked; show a toast and log a line. Fall back to `(,0)` declarations if that is unsafe.
  7. A JFR frame event equivalent to RatOpt's `ratopt.Frame`, so clientbench works without RatOpt's Oculus module.
  8. **V-06b (moved here from 0.2.0):** the deferred blend and depth/colour-mask overrides (`MixinGlStateManager_BlendOverride`, `_DepthColorOverride`) against Forge stage events.
     - Start from the T5 reproduction: Atmospherics' horizon fade with its workaround removed.
     - 0.2.0 only made the framebuffer and program bind caches safe (V-14). The DSA texture binds already update `GlStateManager`.
  9. **Oculus upstream #789** (`b3b278134`):
     - Bump the Embeddium dev dependency to 0.3.31.
     - Make `CuboidMixin` implement `embeddium$getSimpleCuboid` (Xenon 0.3.31 declares it).
     - Today it is reached only by Xenon's fast-render entity mixins, which Visus's `sodium:options` turns off.
- **Invariants:**
  - T1 identical for non-Flywheel programs.
  - Flywheel programs get new goldens, listed in `golden/CHANGES.md`.
- **Acceptance:**
  - T5 scenarios 3, 4 and 6 pass in `Visus - Test` (Vanillin) and `Visus - Common` (Create 6).
  - T4: cold apply on the 7900 XTX is no worse than RatOpt's numbers (66 programs in 0.5–8.0 s), and the terrain link wait drops from 172.6 ms average to ≤ 20 ms.
  - Shadow-pass entity CPU on MakeUp drops ≥ 20 % (from 0.54 ms).
- **Delegation:** Opus does the Flywheel backend and the compile service. Sonnet does 3–7. Sonnet runs T4 and T5.

## 0.4.0 "APIs"
- **Goal:** formal, MIT-licensed extension points. NeoVoxy moves off its 24 internal mixins and Atmosphera off reflection.
- **Context to load:** A§4.5, A§4.8, A§10 (LGPL/MIT boundary).
- **Scope:**
  1. `visus-api` module (MIT, no Oculus imports; a CI check enforces this). Packages:
     - `dev.visus.api` (`Visus`, `requestReload`, state queries)
     - `dev.visus.api.lod`: `LodProgramProvider`, `LodPipelineListener`, `PipelineView`, `UniformContributor`, `SamplerUnits`
     - `dev.visus.api.bridge`: `PackOptionOverrides`, `MaterialIdAssist`, `VertexSnippets`, `FrameUbo`, policies `DEFER`/`ASSIST`/`OVERRIDE`
  2. Implementation in `visus` behind those interfaces.
     - Light mask `a_LightCoord & 0xFF` plus `atmos_LightHue`, always on.
     - `_vert_init` snippets also apply in the shadow pass.
  3. **NeoVoxy port** (a separate NeoVoxy release):
     - Implement the LOD API.
     - Keep the 1.8.0 mixin path for plain Oculus.
     - The plugin picks the Visus path when `visus` is loaded.
     - Fixes NeoVoxy #10, #29 and #30.
  4. DH 2.x adapter over the existing `iris.compat.dh` code, through the LOD API.
  5. AtmospheraLib: optional `visus-api` integration. Policy `ASSIST` for waving ids; `VertexSnippets` for Gale and Torrente.
  6. Retire NeoVoxy's entries in `abi-freeze.txt`, with a dated note.
- **Invariants:**
  - With no API users, T1 is identical except for the light-mask change, which is listed.
  - The reload contract (API state, then `allChanged()`, then listeners) is covered by a test.
- **Acceptance:**
  - T5 scenario 7 and 8 pass on all 5 packs.
  - NeoVoxy loads with zero Iris mixins applied when Visus is present (checked with `-Dmixin.debug.countInjections`).
  - The `visus-api` jar has no LGPL classes.
- **Delegation:** Opus designs the API and implements the bridge. Sonnet ports NeoVoxy and the DH adapter. Haiku runs T0–T2.

## 0.5.0 "Fast load"
- **Goal:** apply and reload are fast and never freeze the render thread.
- **Context to load:** A§5 L2, A§7 V-11 and V-12.
- **Scope:**
  - A worker pool for jcpp and the transform, with one parser per thread.
  - Tier 1 disk cache (key per A§5 L2; compressed).
  - Tier 2 program-binary cache with self-check, auto-disable and a user toggle.
  - Cache management: size cap (default 512 MB), LRU eviction, `/visus cache clear`.
- **Gate:** cold apply ≥ 50 % faster than 0.4.0 on the 7900 XTX for CU and Photon, **or** warm apply ≥ 80 % faster. If neither, Tier 2 ships off by default and the result is recorded.
- **Acceptance:**
  - T1: cached output is byte-identical to uncached output.
  - T4 apply-time numbers are recorded in the changelog.
  - No render-thread stall over 100 ms during apply (JFR).
- **Delegation:** Opus implements. Sonnet runs T4. Haiku runs T0 and T1.

## 0.6.0 "Frame cost"
- **Goal:** cut CPU frame cost and per-frame allocation where measurement shows it matters.
- **Context to load:** A§5 L3, L4, L6 and L9, A§7 V-01, Appendix A rows for the uniform compiler and the render graph.
- **Scope, each item measured first:**
  1. JFR profile of all 5 packs: uniform update share, batching allocations, shadow entity share. This decides the order of items 2–5.
  2. Uniform dirty tracking, if not already effective.
  3. Compiled custom-uniform evaluator. Gate: the differential test passes, and ≥ 0.1 ms saved per frame on the pack with the most custom uniforms.
  4. Sort-key batcher with flat arrays and a radix sort, replacing the graph manager. The debug option stays one more release.
     This carries the part of V-01 moved out of 0.2.0: FIFO order for depthless and translucent types (Iris #2897).
     It changes draw order, so it needs T3 screenshots against the graph order before it becomes the default.
  5. Shadow entity and block-entity budget, plus pose reuse.
  6. IR: skip dead clears and flips; minimal barriers.
  7. An allocation sweep of hot paths: render-thread allocations from Visus code at 0 B/frame (`jfr_alloc.py`).
- **Acceptance:**
  - T4 on MakeUp (CPU-bound): p50 ≥ 8 % better **or** p99 ≥ 10 % better. Neither GPU-bound pack regresses.
  - T1 identical; the IR changes scheduling only.
- **Delegation:** Opus does the IR and the batcher. Sonnet does the profile, the budget, the allocation sweep and T4.

## 0.7.0 "Mod shaders"
- **Goal:** modded core and post shaders render correctly under packs; stencil mods work.
- **Context to load:** A§5 L5 (`ShaderInstance`), A§7 V-06 and V-15, A§6.
- **Scope:**
  - The `ShaderInstance` transpiler (albedo routing, neutral defaults for other targets), with allow and deny lists and a debug overlay.
  - Stencil-aware depth through `glTextureView`, with the copy fallback.
  - The macOS GL 4.1 tier: no compute, no texture view.
  - The legacy translucency graph and Ithaka digraph are removed.
- **Acceptance:** T5 scenarios 9 and 10 pass. The GL debug log is clean with a stencil-enabled main target.
- **Delegation:** Opus.

## 0.8.0 "Pack SDK"
- **Goal:** good documentation and tools for pack authors; feature parity with upstream Iris where packs use it.
- **Context to load:** A§8, A§3.3, the spike `docs/spikes/upstream-features.md` (from 0.1.0).
- **Scope:**
  - `docs/packdev/`: the uniforms reference generated from catalog annotations, the properties reference, feature flags, `visus.*` extensions, debugging, mod integration, and differences from Oculus.
  - Debug tools: an error screen with line numbers mapped through `#include`, `KHR_debug` labels, a GPU pass-timing overlay, a render-target viewer, `/visus dump`.
  - Backport the upstream feature flags, one at a time, each with goldens.
  - Oculus upstream #806 (`IS_OCULUS` standard macro, `b50faac79`). It was moved out of 0.2.0 because it adds a define to every pack program.
- **Acceptance:**
  - The docs build from source; every uniform in the catalog is documented (a CI check).
  - Each backport has a test pack in `golden/featurepacks/`.
- **Delegation:** Sonnet writes the docs and the tool UI. Opus does the backports.

## 0.9.0 "Experimental"
- **Goal:** try the Defer items. Each ships off by default; it is kept only if it passes its gate.

| Item | Gate | Context |
|---|---|---|
| UBO uniforms | ≥ 0.2 ms CPU per frame saved on MakeUp; T1 compiles on all packs or falls back per pack | A§5 L4 |
| UNORM16 + sprite-local UVs | Fixes visible pinholes (T3 zoom scenes); no T4 regression | A§7 V-07 |
| Static shadow cache | AST eligibility check; ≥ 15 % shadow GPU time saved on an eligible pack | A§5 L6 |
| GPU shadow culling (Hi-Z + MDI) | ≥ 10 % shadow terrain time saved; works on Xenon | A§5 L6 |
| SPD compute mipmaps | Reload time −30 % with a LabPBR pack | Appendix A |
| Texel snapping | Visible shimmer reduction on an undistorted pack; opt-in | A§5 L6 |
| Octahedral normals/tangents | Measurable bandwidth win; T1 identical | Appendix A |
| Transient target aliasing (IR) | VRAM −15 % on CU/Photon at no cost | A§5 L3 |

- **Delegation:** Opus prototypes. Sonnet runs T4 and T3 for each gate. Items that fail their gate go to "Not adopted" with their numbers.

## RC "Release candidate"
- Full T0–T5 on Java 17, 21 and 25, in the `Visus - Test` and `Visus - Common` instances, on AMD plus at least one NVIDIA or Intel machine (a tester if needed).
- Re-run the census on the current RatPack list.
- Freeze the changelog and write the drop-in guide: "replace the Oculus jar; remove irisflw/particle fix/GeckoLib fix (optional)".
- 1.0.0 happens only when the user explicitly calls it.

## Not planned (see A Appendix A for reasons)
- Visibility buffer / compute deferred shading
- Per-pixel linked-list OIT and WBOIT
- 32-byte aligned terrain vertex
- Virtual CSM / SDSM
- SVGF SDK
- Bindless textures
- Compute exposure / ACES pipeline
- Decoupled GL submission thread
- GraalVM native image or native sidecar
- Vector API / AVX2
- SVDAG / Aokana GI
- BC7 texture recompression
- Compute meshing
- A new non-Iris pack format
