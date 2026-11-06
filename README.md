<p align="center">
  <img src="docs/logo/visus-banner.png" alt="Visus: shader pack loader for Minecraft Forge 1.20.1" width="100%">
</p>

# Visus

Visus is a shader pack loader for Minecraft 1.20.1 on Forge. It loads OptiFine, ShadersMod and Iris-format shader packs and is built as a drop-in replacement for Oculus 1.8.0.

> **Unofficial fork.** Visus is an independent project. It is **not** affiliated with, endorsed by, or supported by the Iris Shaders team, the Oculus maintainers, OptiFine, Mojang Studios, Microsoft or Minecraft Forge. It is a modified version of [Oculus](https://github.com/Asek3/Oculus) 1.8.0, itself an unofficial Forge port of [Iris](https://github.com/IrisShaders/Iris). Please do not report Visus problems to either upstream project.

## Status

**Current release: 0.1.1 ("Baseline").** This release establishes the fork, its identity, its licence and its test and compatibility tooling. **It does not change rendering.** Shader output, uniforms and pipeline behaviour are identical to Oculus 1.8.0 by design, and this is verified (see [Performance](#performance)). The improvements listed under [Roadmap](#roadmap) are planned, not shipped.

## Features

### Available in 0.1.1

- **Drop-in identity.** The jar provides two mods: `visus` and an `oculus` compatibility alias (`provides = ["iris"]`). Mods that check for Oculus or declare a dependency on `oculus [1.8.0,)` continue to work without changes.
- **Frozen compatibility surface.** 268 references into Oculus internals, taken from a census of 26 mods in a 499-mod test pack, are recorded in [`abi/abi-freeze.txt`](abi/abi-freeze.txt). `gradlew abiCheck` fails the build if any of them disappears.
- **Pack compatibility.** Existing shader packs and option files work unchanged. The `shaderpacks/` layout and configuration file are the same as Oculus.
- **Byte-identical shader transformation.** The transformed GLSL is compared against Oculus 1.8.0 for each release. Differences are permitted only when listed in [`golden/CHANGES.md`](golden/CHANGES.md).
- **Build guards.** The build refuses to package a Sodium terrain shader file that would disable AtmospheraLib terrain features.
- **Flywheel instancing.** Oculus Flywheel Compat (`irisflw`) 2.0.3 is verified to work unchanged. Keep it installed until 0.3.0.

### Planned

Each item is a release gate in [`docs/VISUS-ROADMAP.md`](docs/VISUS-ROADMAP.md). An item that fails its measurement is dropped and reported rather than shipped.

- **Stability.** Fixes for documented crash and corruption bugs, each with a test that fails without the fix.
- **One jar instead of five.** Native Flywheel support, the parallel shader compile service from RatOptimization, the shadow skip list from Collections of Optimizations, and the GeckoLib and particle-order fixes, absorbing `irisflw`, `oculusparticlefix`, GeckoLibOculusCompat and RatOpt `oculus_shaders`.
- **A formal, MIT-licensed API** (`visus-api`) for LOD renderers and shader-aware mods, developed in lockstep with NeoVoxy.
- **Fast pack loading.** Worker-pool transformation and persistent on-disk caches.
- **Lower CPU frame cost.** Dirty-tracked and compiled uniforms, a flat-array sort-key batcher and a shadow-pass entity budget.
- **Modded shader support and pack-author tooling.**

## Performance

Visus 0.1.1 makes **no performance claim**: it is a parity release. The figures below are the project's own measurements, taken with the in-repo harness on the RatPack 2.9 test client (499 mods, Forge 47.4.23, Xenon 0.3.31, Java 25), an RX 7900 XTX, at 2560x1440. The harness noise floor is ±5–6 %, so only differences of 8–10 % or more, from at least three alternating runs per side, are treated as real.

### Measured: parity with Oculus 1.8.0

These are single dump runs with debug options enabled. They confirm that the fork does not regress, and are not benchmarks.

| Photon 1.3b | Oculus 1.8.0 | Visus 0.1.0 |
|---|---|---|
| Base scene, FPS | 151.2 | 151.4 |
| Base scene, p99 frame time | 12.44 ms | 12.24 ms |
| Overview scene, FPS | 171.9 | 172.7 |
| Overview scene, p99 frame time | 9.81 ms | 9.66 ms |

Shader transformation: **PASS** on both packs tested (Photon: 451 dumped files identical; Complementary Unbound r5.9.3: 225 distinct programs and the id maps identical).

### Measured: baselines that Visus is built to improve

These are Oculus 1.8.0 figures from the same test client. They are the numbers the roadmap has to beat.

| Baseline (Oculus 1.8.0) | Value |
|---|---|
| Pack apply stall on AMD, before RatOptimization | about 45–77 s |
| Terrain shader programs, link time | 82–912 ms each, 172.6 ms average wait |
| Shadow-pass entity work on MakeUp | 0.39–0.54 ms per frame, about 12–13 % of render-thread busy time |

### Targets: release gates

<p align="center">
  <img src="docs/img/release-gates.png" alt="Measured baselines and the release gates Visus must pass" width="90%">
</p>

The outlined bars are targets, not results. Each is a pass/fail test, and the roadmap states them in full:

| Release | Gate |
|---|---|
| 0.3.0 | Terrain link wait at or below 20 ms (from 172.6 ms); shadow-pass entity CPU on MakeUp at least 20 % lower |
| 0.5.0 | Cold pack apply at least 50 % faster, **or** warm apply at least 80 % faster |
| 0.6.0 | On the CPU-bound pack (MakeUp), p50 frame time at least 8 % better **or** p99 at least 10 % better, with no regression on GPU-bound packs |
| Every release | No frame-time regression of 3 % or more (p50 or p99) on any of five tested packs, at 2560x1440 and 1280x720 |

**Expectation.** On GPU-bound packs at high resolution, frame time is expected to stay within measurement noise. The measurable gains Visus aims for are in pack load time, CPU-bound scenes, 1 % lows, allocation rate and crash count, not in peak FPS on a saturated GPU.

## Roadmap

| Release | Theme | Content |
|---|---|---|
| 0.1.x | Baseline | Fork, identity, licence, ABI census, golden references (**current**) |
| 0.2.0 | Stability | Documented crash and corruption fixes, GL error de-duplication, safe fallback |
| 0.3.0 | Absorb | Native Flywheel, compile service, shadow skip list, GeckoLib and particle fixes, add-on guard |
| 0.4.0 | APIs | `visus-api` (MIT), LOD API, NeoVoxy and Atmosphera bridges |
| 0.5.0 | Fast load | Worker-pool transform, disk caches |
| 0.6.0 | Frame cost | Uniform and batching work, shadow budget |
| 0.7.0 | Mod shaders | Mod `ShaderInstance` support, stencil-aware depth |
| 0.8.0 | Pack SDK | Pack-author documentation and debug tools |
| 0.9.0 | Experimental | Gated, off-by-default items |

Dates are not promised. Version numbers follow the rules in [`CHANGELOG.md`](CHANGELOG.md). Version 1.0.0 is reserved for when the project is declared complete.

## Requirements

- Minecraft **1.20.1** with Forge **47.x** (built against 47.1.105; tested on 47.4.23)
- Java **17** or newer
- An Embeddium-based terrain renderer: Embeddium 0.3.1 or newer (built against 0.3.14), or Xenon (tested with 0.3.31)
- **Not compatible with OptiFine.**

## Installation

1. Remove Oculus from your `mods` folder. Visus and Oculus both register the mod id `oculus`, so they cannot be installed together.
2. Place the `visus-1.20.1-<version>.jar` in `mods`.
3. Keep `oculus-flywheel-compat` (`irisflw`) installed if you use Flywheel-based mods, until Visus 0.3.0 absorbs it.
4. Place shader packs in `shaderpacks/` as before.

## Building

```bash
./gradlew build
```

The build uses Architectury Loom, requires a JDK 17 and writes `build/libs/visus-1.20.1-<version>.jar`. `gradlew build` also runs `abiCheck` and `checkForbiddenResources`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md): releases and their measurements
- [`docs/CHANGES-FROM-OCULUS.md`](docs/CHANGES-FROM-OCULUS.md): dated record of every difference from Oculus 1.8.0
- [`docs/VISUS-ROADMAP.md`](docs/VISUS-ROADMAP.md): releases, budgets, test tiers and gates
- [`docs/VISUS-ARCHITECTURE.md`](docs/VISUS-ARCHITECTURE.md): design, compatibility contract and bug ledger
- [`docs/spikes/`](docs/spikes/): investigation notes, including the licence analysis

## Licence and attribution

- The Visus mod as a whole is licensed under the **GNU Affero General Public License v3.0 only** ([`LICENSE`](LICENSE)). This follows from the bundled [glsl-transformer](https://github.com/IrisShaders/glsl-transformer) library, which is AGPL-3.0.
- Files that originate from Oculus and Iris remain available under the **GNU Lesser General Public License v3.0 only** ([`COPYING.LESSER`](COPYING.LESSER), [`COPYING.GPL-3.0`](COPYING.GPL-3.0)).
- The planned `visus-api` module is written from scratch and is licensed under the **MIT** licence. Mods that use only `visus-api` are not bound by the AGPL.
- Under AGPL-3.0 section 7(b), copies and modified versions must keep [`NOTICE`](NOTICE) and the attribution **"Based on Visus by SolarBiscuit"** in their legal notices and project page.

Visus is built on the work of the Iris authors (coderbot, IMS212, Justsnoopy30, FoundationGames and contributors) and the Oculus authors (NanoLive, dima_dencep and contributors). Bundled third-party libraries and their licences are listed in [`NOTICE`](NOTICE).

## Reporting issues

Issues belong in the [Visus issue tracker](https://github.com/SolarBiscuit/Visus/issues). Please include the Visus version, your shader pack and its options, and `latest.log`.
