# Visus (Forge 1.20.1 shader mod, fork of Oculus 1.8.0)

Start here, then read only what your task needs:
- `docs/VISUS-ROADMAP.md`: the "Standing context" block, then the capsule for your release
- `docs/VISUS-ARCHITECTURE.md`: only the sections (A§n) your capsule lists
- `OculusRewriteDocs/`: raw AI research. Its verdicts are already in Architecture Appendix A; do not re-read it.
- `referencemods/`: the Oculus 1.8.0 jar and the add-on jars, used only for comparison and golden references

## Decisions (2026-11-06)
- Fork and re-architect Oculus 1.8.0. Licence: the jar is AGPL-3.0-only (because glsl-transformer is AGPL); files from Oculus/Iris stay LGPL-3.0-only (`docs/spikes/licences.md`). Keep `net.irisshaders.*` shapes listed in `abi/abi-freeze.txt`.
- Mod ids: alias `oculus` (FIRST `[[mods]]` entry; 1.8.0 until 0.3.0, then 1.8.100) and `visus`, `provides=["iris"]` (a no-op on FML 47).
- Visus absorbs irisflw, oculusparticlefix, GeckoLibOculusCompat, RatOpt `oculus_shaders` and CoO `[oculus]`.
- NeoVoxy is co-developed against `dev.visus.api.lod`. Release them in lockstep.
- `visus-api` is MIT and written from scratch. No Oculus-derived code or GLSL goes in it.

## Never
- Ship `assets/sodium/shaders/blocks/block_layer_opaque.vsh` (it disables AtmospheraLib terrain features).
- Remove or change a member in `abi/abi-freeze.txt` without a dated note naming the dependent that moved off it.
- Change transformed GLSL without listing the change in `golden/CHANGES.md`.
- Keep a nullable pipeline field in a mixin. Use the accessor that cannot return null.
- Call `sun.misc.Unsafe` directly, or allocate on hot render paths.

## Versioning (global rule)
- +0.0.1 for each rebuilt change.
- +0.1.0 when a change adds or removes 500+ lines (patch resets to 0).
- 1.0.0 only when the user asks.
- Record every bump in `CHANGELOG.md` (`## x.y.z (date)`, with "Measured:" lines for performance work).

## Testing
- Tiers T0–T5 and the runbook template are in the roadmap ("Test tiers").
- Haiku 5.5: T0, T1, T2 and log triage.
- Sonnet 5.5: T3, T4, T5 and fixes that come with a failing test.
- Opus: transformer, IR, GL lifecycle, API design.
- clientbench noise is ±5–6 %. Only trust differences of 8–10 % or more, from ≥ 3 alternating runs per side.
