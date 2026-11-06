"""T1 golden transform check: compare two `patched_shaders/` dumps (Iris debug output) file by file.

    python -I tools/golden/diff_dumps.py <reference_dir> <candidate_dir> [--allow golden/CHANGES.md] [--show N]

A dump is produced by a bench run with `--shaders <pack> --dump-shaders` (clientbench), which turns on Iris debug
options so every transformed program is written to patched_shaders/. File names carry a per-session program counter
("00012_gbuffers_terrain.vsh"); the counter can shift when programs compile in a different order, so files are
matched by name with the counter stripped. Exit code 0 when identical (or every difference is allowed), 1 otherwise.

`--allow` reads lines of the form `ALLOW <program-file-name-without-counter> <reason>` from the given file. The name
may be a glob pattern (`gbuffers_flw_pipeline_*`). An allowed name may differ, or exist in only one of the dumps.
"""
import argparse
import difflib
import fnmatch
import re
import sys
from collections import defaultdict
from pathlib import Path

COUNTER = re.compile(r'^\d+_')
STORE_STAMP = re.compile(r'^#\w{3} \w{3} \d{2} [\d:]{8} \w+ \d{4}\r?\n', re.M)


def load(d):
    files = defaultdict(list)
    for p in sorted(Path(d).rglob('*')):
        if p.is_file():
            text = p.read_text(encoding='utf-8', errors='replace')
            if p.suffix == '.properties':
                # java.util.Properties.store writes a "#<date>" comment line; it is not part of the transform
                text = STORE_STAMP.sub('', text, count=1)
            files[COUNTER.sub('', p.name)].append(text)
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('reference')
    ap.add_argument('candidate')
    ap.add_argument('--allow')
    ap.add_argument('--show', type=int, default=3, help='print a unified diff for the first N differing files')
    a = ap.parse_args()
    allowed = set()
    if a.allow and Path(a.allow).exists():
        for line in Path(a.allow).read_text(encoding='utf-8').splitlines():
            parts = line.split(None, 2)
            if len(parts) >= 2 and parts[0] == 'ALLOW':
                allowed.add(parts[1])
    ref, cand = load(a.reference), load(a.candidate)
    only_ref = sorted(set(ref) - set(cand))
    only_cand = sorted(set(cand) - set(ref))
    differ = []
    for name in sorted(set(ref) & set(cand)):
        # a program may be dumped more than once per session (pipeline rebuilt, dimension change); compare the
        # distinct transformed sources, not how often each was printed
        if set(ref[name]) != set(cand[name]):
            differ.append(name)
    def is_allowed(name):
        return any(fnmatch.fnmatchcase(name, pattern) for pattern in allowed)

    bad = [n for n in differ + only_ref + only_cand if not is_allowed(n)]
    n_allowed = len(differ) + len(only_ref) + len(only_cand) - len(bad)
    print('T1: %d reference files, %d candidate files; %d differ, %d only in reference, %d only in candidate (%d of those allowed)'
          % (sum(map(len, ref.values())), sum(map(len, cand.values())), len(differ), len(only_ref), len(only_cand), n_allowed))
    for n in only_ref:
        print(('ALLOWED   ' if is_allowed(n) else 'ONLY-REF  ') + n)
    for n in only_cand:
        print(('ALLOWED   ' if is_allowed(n) else 'ONLY-CAND ') + n)
    for i, n in enumerate(x for x in differ if not is_allowed(x)):
        print('DIFF      ' + n)
        if i < a.show:
            r, c = sorted(set(ref[n]) - set(cand[n]) or ref[n])[0].splitlines(), sorted(set(cand[n]) - set(ref[n]) or cand[n])[0].splitlines()
            for line in list(difflib.unified_diff(r, c, 'reference/' + n, 'candidate/' + n, lineterm='', n=1))[:40]:
                print('    ' + line)
    print('PASS' if not bad else 'FAIL')
    return 0 if not bad else 1


if __name__ == '__main__':
    sys.exit(main())
