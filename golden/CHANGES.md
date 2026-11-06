# Intended differences in transformed GLSL (T1)

The rule: transformed GLSL must be identical to Oculus 1.8.0 unless the difference is listed here.

**Format:** one line per program file, with its name taken from `patched_shaders/` and the leading counter
removed. Read by `tools/golden/diff_dumps.py --allow golden/CHANGES.md`.

```
ALLOW <program-file-name> <release> <reason>
```

The name may be a glob pattern. An allowed name may differ, or exist in only one of the two dumps.

## Listed differences
```
ALLOW *_flw_pipeline_* 0.2.0 V-16: the irisflw (Oculus Flywheel Compat) programs, main and shadow pass, are now written to the dump. Oculus failed to write them because their names contain '/'. They come from irisflw, not from the pack.
```

## Changed GLSL that is not in the dumps
These are Visus's own internal programs. They are not pack programs, so `patched_shaders/` does not contain them.
- **0.2.0, `centerDepth.fsh` (V-02).** NaN or Inf in the current depth, the previous smoothed depth or the decay is replaced
  before the `mix()`. The decay factor is clamped to [0, 1]. Finite inputs give the same `centerDepthSmooth` as Oculus 1.8.0.
