# Visus changelog

Versioning:
- +0.0.1 for every rebuilt change.
- +0.1.0 when a change adds or removes 500+ lines (patch resets to 0).
- 1.0.0 only when requested.

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
