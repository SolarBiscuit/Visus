# Integrating a mod with Visus (`visus-api`)

Visus 0.5.0 adds a public, MIT-licensed API so that mods can work with shader packs without mixins into Oculus
internals. This page is the short guide. The Javadoc in `visus-api` is the full contract.

## Getting the API
- **Artifact:** `dev.visus:visus-api:0.1.0`. It is plain Java with no Minecraft types.
- **Compile against it, but don't bundle it:** use `compileOnly`. Visus ships the API inside its own jar (jar-in-jar).
- **Declare the dependency.** In `mods.toml`, add an optional dependency on `visus` with `ordering = "AFTER"`, so that
  Visus has installed the API before your mod is constructed.
- **At run time:**
  - `VisusApi.get()` is empty when Visus isn't installed, for example on plain Oculus. Keep your existing path for that
    case.
  - Load classes that use the API only when the `visus` mod is present.
  - `VisusApi.get().map(VisusApi::revision)` tells you what the installed Visus supports.

## What is in it
| Package | For |
|---|---|
| `dev.visus.api` | State and reloads: `isShaderPackInUse()`, `isRenderingShadowPass()`, `currentPackName()`, `shaderpacksDirectory()`, `requestReload(reason)`, and `addReloadListener(...)`. |
| `dev.visus.api.lod` | Level-of-detail renderers: extra pack sources, pipeline events, pack uniforms, samplers and storage buffers, extra defines and uniforms, and the texture-unit allocator. |
| `dev.visus.api.gl` | `openScope(owner)`: run raw GL, and on close Visus restores the state Minecraft and Visus track. |

## Rules that matter
- **Reloads are never immediate.** `requestReload` runs once, at the next frame boundary, however many times it is
  called, including from inside a pipeline callback. Never call `IrisApi...setShadersEnabledAndApply(false)` to
  "recover" from a failure inside a pipeline callback. That re-entered the pipeline constructor (NeoVoxy #30).
- **Reload order:** the API state updates first, then the world renderer is rebuilt if the new pack needs it, then
  reload listeners run.
- **`onPipelineCreated` runs after the pipeline is built and installed.** Build your per-pipeline data there, keyed by
  the `PipelineView`. Visus keeps one pipeline per dimension, so several can be alive at once.
  `PipelineView.directory()` tells you which program set (pack root or dimension folder) a pipeline came from.
- **Pack files you read must be declared** in `LodProgramProvider.extraSourceNames()`, so Visus loads them with the
  pack. Read them in `onProgramSetBuilt` through `PackSources.read`.
- **Custom uniforms you need must be declared** with `PackSources.retainUniforms`. Otherwise the optimiser drops
  those that no pack program uses.
- **Raw GL goes inside a scope.**
  - `GlScope.close()` restores blend, depth, colour mask, cull, the active texture, tracked texture units, sampler
    objects, the program, the viewport, the framebuffers, and the pack's storage-buffer bindings.
  - Per-buffer blend changes (`blendFuncSeparatei` and the like) must go through the scope's methods.
- **Texture units outside a scope:** if your mod binds textures that must stay bound outside its own pass, reserve
  units with `SamplerUnits.allocateTextureUnits(owner, n)` during setup. Pack programs never use those units.
  Inside a scope you can use any unit, because the scope puts the tracked state back.
- **No allocation** in value sources you hand to the API (`FloatArraySource`, suppliers). They run every frame.

## Distant Horizons
Visus's own Distant Horizons support registers its defines through the same contributor path. Its programs and
lifecycle are unchanged from Oculus 1.8.0.

## Differences from Oculus
On plain Oculus none of this exists, and the mixin-based integrations keep working as before. Visus keeps the
Oculus internals that existing mods hook into (see `abi/abi-freeze.txt`), so an older NeoVoxy that uses mixins still
loads on Visus.
