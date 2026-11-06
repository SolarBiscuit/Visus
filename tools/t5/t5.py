"""Visus T5 compatibility harness (docs/VISUS-ARCHITECTURE.md A§9.2) on the clientbench instance.

    python -I tools/t5/t5.py run <tag> --plan s5,s9,s1 [--remove-mod PREFIX]... [--add-mod JAR]... [--pack ZIP]
    python -I tools/t5/t5.py run <tag> --plan soak --minutes 30 ...
    python -I tools/t5/t5.py analyze <tag> [--ref <oculus tag>]

It reuses clientbench (bench.py): the instance mirror of RatPack 2.9 - TestClient, the mod swap, the world template
and the GPU cooldown file, so T5 runs and bench runs share one cooldown. Debug options are on, so GL debug messages
reach latest.log.

Scenarios:
- s5 (nesting): creative player on a sky platform wearing a Mekasuit or GeckoLib Fantasy Armor (alternating), Artifacts
  curios, an elytra in the curios back slot and a Sophisticated backpack in the backpack slot; armor stands and
  zombies in the same armor sets.
  F5 cycles first person / back / front so the player model renders. Looks for "Already in a group".
- s9 (stencil): Voidscape's paranoia overlay calls RenderTarget.enableStencil() on the main target mid-game
  (`voidscape debug set paranoia`, run from a command block). Paranoia only stays up for a survival player in
  Voidscape's Void, so the player goes there (a dimension change under the pack) and comes back. The GL debug log
  must stay clean.
- s1 (reload): K off/on twice, switch pack (config edit + R), change a pack option (+ R), F3+T, F3+A. Atmosphera's
  "[Atmosphera] <feature> ..." transitions after each step must name the new pack state.
- soak: s5 phases alternating for --minutes.

Keys are posted to the game window with PostMessage (WM_KEYDOWN/WM_KEYUP), so nothing reaches other windows even if
the game is not focused. Each datapack command sits in its own function file, listed as an optional entry of the
tick tag, so one bad command drops only itself (the log shows which).
"""
import argparse
import ctypes
import ctypes.wintypes as wt
import datetime as dt
import importlib.util
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

CLIENTBENCH = Path(__file__).resolve().parents[3] / 'clientbench'
spec = importlib.util.spec_from_file_location('bench', CLIENTBENCH / 'bench.py')
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)

GAME = bench.GAME
RESULTS = bench.RESULTS
MARK = 'VISUST5'
DEFAULT_PACK = 'photon_v1.3b [RatPatch].zip'
SWITCH_PACK = 'ComplementaryUnbound_r5.9.3.zip'
SWITCH_OPTION = ('BLOOM_STRENGTH', '0.072', '0.090')  # option file key, value before, value written for "apply options"

# Platform high above the bench scene: a clear, fixed background.
PX, PY, PZ = -4450, 150, 17121

ARMOR = {
    'mekasuit': ['mekanism:mekasuit_boots', 'mekanism:mekasuit_pants', 'mekanism:mekasuit_bodyarmor', 'mekanism:mekasuit_helmet'],
    'crucible': ['fantasy_armor:crucible_knight_boots', 'fantasy_armor:crucible_knight_leggings',
                 'fantasy_armor:crucible_knight_chestplate', 'fantasy_armor:crucible_knight_helmet'],
    'chess': ['fantasy_armor:chess_board_knight_boots', 'fantasy_armor:chess_board_knight_leggings',
              'fantasy_armor:chess_board_knight_chestplate', 'fantasy_armor:chess_board_knight_helmet'],
    'obsidian': ['mekanismtools:refined_obsidian_boots', 'mekanismtools:refined_obsidian_leggings', 'minecraft:elytra',
                 'mekanismtools:refined_obsidian_helmet'],
}
SLOTS = ['feet', 'legs', 'chest', 'head']
CURIOS = [('head', 'artifacts:night_vision_goggles'), ('necklace', 'artifacts:lucky_scarf'),
          ('hands', 'artifacts:power_glove'), ('belt', 'artifacts:antidote_vessel'), ('feet', 'artifacts:bunny_hoppers'),
          ('back', 'minecraft:elytra'), ('backpack', 'sophisticatedbackpacks:backpack')]

# Windows virtual-key code and scan code.
KEYS = {'F2': (0x71, 0x3C), 'F3': (0x72, 0x3D), 'F5': (0x74, 0x3F), 'K': (0x4B, 0x25), 'R': (0x52, 0x13),
        'T': (0x54, 0x14), 'A': (0x41, 0x1E)}


def log(msg):
    print('[t5 %s] %s' % (time.strftime('%H:%M:%S'), msg), flush=True)


# ---------------------------------------------------------------------------------------------------- datapack

class Timeline:
    """Commands at tick offsets. Each command becomes its own guarded function file."""

    def __init__(self):
        self.steps = []  # (tick, command)

    def at(self, sec, cmd):
        self.steps.append((int(sec * 20), cmd))

    def mark(self, sec, text):
        self.at(sec, 'tellraw @a {"text":"%s %s"}' % (MARK, text))


def armor_nbt(kind):
    return ','.join('{id:"%s",Count:1b}' % i for i in ARMOR[kind])


def player_phase(tl, sec, kind):
    for slot, item in zip(SLOTS, ARMOR[kind]):
        tl.at(sec, 'item replace entity @a armor.%s with %s' % (slot, item))


def scene_setup(tl):
    tl.at(0, 'gamemode creative @a')
    tl.at(0, 'tp @a %.1f %d %.1f 180 12' % (PX + 0.5, PY + 2, PZ + 0.5))
    tl.at(0, 'effect give @a minecraft:resistance infinite 255 true')
    tl.at(1, 'fill %d %d %d %d %d %d minecraft:smooth_stone' % (PX - 8, PY - 1, PZ - 10, PX + 8, PY - 1, PZ + 6))
    tl.at(1, 'kill @e[type=!player,distance=..40,x=%d,y=%d,z=%d]' % (PX, PY, PZ))
    tl.at(2, 'tp @a %.1f %d %.1f 180 12' % (PX + 0.5, PY, PZ + 0.5))
    for i, kind in enumerate(['crucible', 'chess', 'mekasuit', 'obsidian']):
        x = PX - 3 + i * 2 + 0.5
        tl.at(3, 'summon armor_stand %.1f %d %.1f {ShowArms:1b,NoGravity:1b,Invulnerable:1b,Rotation:[0f,0f],ArmorItems:[%s]}'
              % (x, PY, PZ - 3.5, armor_nbt(kind)))
        tl.at(3, 'summon zombie %.1f %d %.1f {NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Rotation:[0f,0f],ArmorItems:[%s]}'
              % (x, PY, PZ - 6.5, armor_nbt(kind)))
    for slot, item in CURIOS:
        tl.at(4, 'curios replace %s 0 @p with %s' % (slot, item))


def plan_nest(tl, start, phases, phase_s):
    """Alternating player outfits. Returns the end time."""
    t = start
    for n in range(phases):
        if n % 2 == 0:
            player_phase(tl, t, 'mekasuit')
            tl.mark(t, 'nest_a %d' % n)
        else:
            player_phase(tl, t, 'crucible')
            tl.mark(t, 'nest_b %d' % n)
        t += phase_s
    return t


VOID = 'voidscape:void'
VY = 100


def voidscape(tl, sec, cmd):
    """Voidscape registers /voidscape on ServerStartingEvent, after datapack functions are compiled, so a function
    cannot call it. A command block parses its command when it runs: place one (auto, so it fires once) in the Void
    under the player's platform, running as the player."""
    tl.at(sec, 'execute in %s run setblock %d %d %d minecraft:air' % (VOID, PX, VY - 4, PZ))
    tl.at(sec + 0.2, 'execute in %s run setblock %d %d %d minecraft:command_block{auto:1b,Command:"execute as @a run voidscape debug %s"}'
          % (VOID, PX, VY - 4, PZ, cmd))


def plan_stencil(tl, start):
    """Voidscape keeps paranoia at 0 unless a survival player is in its Void dimension, so go there first. The
    dimension change under a shader pack is itself part of the test (a new pipeline for the new dimension)."""
    tl.at(start, 'execute in %s run forceload add %d %d' % (VOID, PX, PZ))
    tl.at(start + 1, 'execute in %s run fill %d %d %d %d %d %d minecraft:smooth_stone' % (VOID, PX - 4, VY - 1, PZ - 4, PX + 4, VY - 1, PZ + 4))
    tl.at(start + 2, 'execute in %s run tp @a %.1f %d %.1f 180 12' % (VOID, PX + 0.5, VY, PZ + 0.5))
    tl.at(start + 3, 'gamemode survival @a')
    tl.mark(start + 3, 'void_arrived')
    voidscape(tl, start + 10, 'set paranoia 600')
    tl.mark(start + 10, 'stencil_on')
    voidscape(tl, start + 60, 'set paranoia 0')
    tl.mark(start + 60, 'stencil_off')
    voidscape(tl, start + 80, 'set paranoia 600')
    tl.mark(start + 80, 'stencil_on2')
    voidscape(tl, start + 120, 'set paranoia 0')
    tl.mark(start + 120, 'stencil_end')
    tl.at(start + 122, 'gamemode creative @a')
    tl.at(start + 122, 'execute in minecraft:overworld run tp @a %.1f %d %.1f 180 12' % (PX + 0.5, PY, PZ + 0.5))
    tl.at(start + 123, 'execute in %s run forceload remove %d %d' % (VOID, PX, PZ))
    return start + 135


def write_datapack(world, tl, end_s):
    shutil.rmtree(world / 'datapacks' / 'ratbench', ignore_errors=True)
    pack = world / 'datapacks' / 'visust5'
    shutil.rmtree(pack, ignore_errors=True)
    fn = pack / 'data' / 'visust5' / 'functions'
    tags = pack / 'data' / 'minecraft' / 'tags' / 'functions'
    fn.mkdir(parents=True)
    tags.mkdir(parents=True)
    (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 15, 'description': 'Visus T5'}}))
    (fn / 'load.mcfunction').write_text('scoreboard objectives add t5 dummy\n')
    setup = ['scoreboard players set #init t5 1', 'scoreboard players set #t t5 0',
             'gamerule sendCommandFeedback false', 'gamerule commandBlockOutput false', 'gamerule logAdminCommands false',
             'gamerule doDaylightCycle false', 'gamerule doWeatherCycle false', 'gamerule doMobSpawning false',
             'gamerule randomTickSpeed 0', 'gamerule doFireTick false',
             'time set %d' % bench.CFG['time_of_day'], 'weather clear 1000000']
    (fn / 'setup.mcfunction').write_text('\n'.join(setup) + '\n')
    (fn / 'clock.mcfunction').write_text(
        'execute unless score #init t5 matches 1 if entity @a run function visust5:setup\n'
        'execute if score #init t5 matches 1 run scoreboard players add #t t5 1\n')
    tl.mark(end_s, 'done')
    values = ['visust5:clock']
    for i, (tick, cmd) in enumerate(tl.steps):
        name = 's%04d' % i
        (fn / (name + '.mcfunction')).write_text('execute if score #t t5 matches %d run %s\n' % (tick + 20, cmd))
        values.append({'id': 'visust5:' + name, 'required': False})
    (tags / 'load.json').write_text(json.dumps({'values': ['visust5:load']}))
    (tags / 'tick.json').write_text(json.dumps({'values': values}))
    (pack / 'steps.json').write_text(json.dumps(tl.steps, indent=0))


# ---------------------------------------------------------------------------------------------------- window input

user32 = ctypes.WinDLL('user32', use_last_error=True)
WNDENUMPROC = ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
user32.EnumWindows.argtypes = [WNDENUMPROC, wt.LPARAM]
user32.GetWindowThreadProcessId.argtypes = [wt.HWND, ctypes.POINTER(wt.DWORD)]
user32.GetClassNameW.argtypes = [wt.HWND, wt.LPWSTR, ctypes.c_int]
user32.IsWindowVisible.argtypes = [wt.HWND]
user32.PostMessageW.argtypes = [wt.HWND, wt.UINT, wt.WPARAM, wt.LPARAM]
WM_KEYDOWN, WM_KEYUP = 0x0100, 0x0101


def game_window(pid):
    found = []

    def cb(hwnd, _):
        p = wt.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(p))
        if p.value == pid and user32.IsWindowVisible(hwnd):
            buf = ctypes.create_unicode_buffer(64)
            user32.GetClassNameW(hwnd, buf, 64)
            if buf.value.startswith('GLFW'):
                found.append(hwnd)
        return True

    user32.EnumWindows(WNDENUMPROC(cb), 0)
    return found[0] if found else None


def key(hwnd, name, down):
    vk, scan = KEYS[name]
    lparam = 1 | (scan << 16)
    if not down:
        lparam |= (1 << 30) | (1 << 31)
    user32.PostMessageW(hwnd, WM_KEYDOWN if down else WM_KEYUP, vk, lparam)


def press(hwnd, *names):
    """press(h, 'K') taps K; press(h, 'F3', 'T') holds F3 while tapping T."""
    for n in names:
        key(hwnd, n, True)
        time.sleep(0.08)
    for n in reversed(names):
        key(hwnd, n, False)
        time.sleep(0.08)


# ---------------------------------------------------------------------------------------------------- config edits

def set_props(path, want):
    out = []
    for line in path.read_text(encoding='utf-8').splitlines():
        k = line.split('=', 1)[0].strip()
        if k in want:
            line = k + '=' + want.pop(k)
        out.append(line)
    out += [k + '=' + v for k, v in want.items()]
    path.write_text('\n'.join(out) + '\n', encoding='utf-8')


# ---------------------------------------------------------------------------------------------------- run

class Runner:
    def __init__(self, tag, out):
        self.tag = tag
        self.out = out
        self.events = []
        self.hwnd = None
        self.pid = None

    def event(self, name, at=None, **kw):
        e = {'time': (at or dt.datetime.now()).strftime('%d%b%Y %H:%M:%S.%f')[:-3], 'event': name}
        e.update(kw)
        self.events.append(e)
        log(name + ('' if not kw else ' ' + json.dumps(kw)))

    def alive(self):
        return bool(bench.game_pids())

    def tap(self, *names):
        if self.hwnd is None:
            self.hwnd = game_window(self.pid)
        if self.hwnd is None:
            self.event('no_window', keys='+'.join(names))
            return
        self.last_tap = dt.datetime.now()
        press(self.hwnd, *names)
        self.event('key', at=self.last_tap, keys='+'.join(names))

    def wait(self, sec):
        end = time.time() + sec
        while time.time() < end:
            if not self.alive():
                raise RuntimeError('game exited')
            time.sleep(0.5)


def log_text():
    p = GAME / 'logs' / 'latest.log'
    return p.read_text(encoding='utf-8', errors='replace') if p.exists() else ''


def wait_marker(r, text, timeout):
    end = time.time() + timeout
    while time.time() < end:
        if MARK + ' ' + text in log_text():
            return True
        if r.pid is not None and not r.alive():
            raise RuntimeError('game exited while waiting for ' + text)
        time.sleep(1)
    raise RuntimeError('timed out waiting for marker ' + text)


def nest_keys(r, phase_s):
    """During one outfit phase: back view, screenshot, front view, screenshot, first person."""
    steps = [(3, 'F5'), (3, 'F2'), (phase_s / 3, 'F5'), (3, 'F2'), (phase_s / 3, 'F5')]
    for delay, k in steps:
        r.wait(delay)
        r.tap(k)


def cmd_run(a):
    out = RESULTS / a.tag
    if out.exists() and not a.force:
        raise SystemExit('results/%s exists (use --force)' % a.tag)
    if bench.game_pids():
        raise SystemExit('a bench game is already running')
    bench.sleep_until(bench.last_run_cooldown_end(), 'previous run ended recently')
    plan = a.plan.split(',')
    phase_s = 30

    tl = Timeline()
    scene_setup(tl)
    t = 8
    nest_phases = 0
    if 'soak' in plan:
        nest_phases = int(a.minutes * 60 // phase_s)
    elif 's5' in plan:
        nest_phases = 6
    t = plan_nest(tl, t, nest_phases, phase_s)
    stencil_at = None
    if 's9' in plan:
        stencil_at = t
        t = plan_stencil(tl, t)
    tl.mark(t, 'scripted_end')
    end_s = t + 2

    bench.sync_instance()
    bench.build_template(False)
    bench.swap_mods(a.remove_mod, a.add_mod)
    bench.write_instance_cfg(bench.CFG['profiles']['hi'])
    bench.write_jfc()
    bench.patch_options()
    bench.patch_configs()
    bench.patch_shaders(a.pack, True)
    opts = GAME / 'shaderpacks' / (SWITCH_PACK + '.txt')
    world = bench.restore_world()
    write_datapack(world, tl, end_s)
    for f in (GAME / bench.JFR_NAME, GAME / 'logs' / 'latest.log'):
        if f.exists():
            f.unlink()
    shots_before = set(p.name for p in (GAME / 'screenshots').glob('*.png')) if (GAME / 'screenshots').exists() else set()
    crashes_before = set(p.name for p in (GAME / 'crash-reports').glob('*')) if (GAME / 'crash-reports').exists() else set()

    r = Runner(a.tag, out)
    started = dt.datetime.now()
    r.event('launch', plan=a.plan, mods_removed=a.remove_mod, mods_added=[Path(m).name for m in a.add_mod])
    subprocess.Popen([bench.CFG['prism_exe'], '--launch', bench.CFG['bench_instance'], '--world', bench.CFG['world_name']])
    error = None
    try:
        deadline = time.time() + bench.CFG['launch_timeout_s']
        while r.pid is None and time.time() < deadline:
            pids = bench.game_pids()
            if pids:
                r.pid = pids[0]
            time.sleep(2)
        if r.pid is None:
            raise RuntimeError('game process never appeared')
        r.event('pid', pid=r.pid)
        for n in range(nest_phases):
            wait_marker(r, 'nest_%s %d' % ('a' if n % 2 == 0 else 'b', n), bench.CFG['launch_timeout_s'] if n == 0 else 120)
            r.event('phase', n=n)
            nest_keys(r, phase_s)
        if stencil_at is not None:
            for m in ('stencil_on', 'stencil_off', 'stencil_on2', 'stencil_end'):
                wait_marker(r, m, bench.CFG['launch_timeout_s'] if nest_phases == 0 and m == 'stencil_on' else 120)
                r.event(m)
                if m.startswith('stencil_on'):
                    r.wait(10)
                    r.tap('F2')
                    r.wait(10)
                    r.tap('F2')
        wait_marker(r, 'scripted_end', bench.CFG['launch_timeout_s'] if nest_phases == 0 and stencil_at is None else 120)
        if 's1' in plan:
            r.wait(5)
            for n in range(4):
                r.tap('K')
                r.event('toggle', to='off' if n % 2 == 0 else 'on', at=r.last_tap)
                r.wait(25)
            props = GAME / 'config' / 'oculus.properties'
            set_props(props, {'shaderPack': SWITCH_PACK})
            r.tap('R')
            r.event('switch_pack', to=SWITCH_PACK, at=r.last_tap)
            r.wait(45)
            if opts.exists():
                text = opts.read_text(encoding='utf-8')
                k, before, after = SWITCH_OPTION
                text2, n = re.subn(r'^%s=.*$' % re.escape(k), '%s=%s' % (k, after), text, flags=re.M)
                opts.write_text(text2 if n else text + '%s=%s\n' % (k, after), encoding='utf-8')
            r.tap('R')
            r.event('apply_option', option='%s=%s' % (SWITCH_OPTION[0], SWITCH_OPTION[2]), at=r.last_tap)
            r.wait(45)
            r.tap('F2')
            r.tap('F3', 'T')
            r.event('resource_reload', at=r.last_tap)
            r.wait(120)
            r.tap('F3', 'A')
            r.event('chunk_reload', at=r.last_tap)
            r.wait(30)
            r.tap('F2')
            r.wait(5)
        r.event('end')
    except RuntimeError as e:
        error = str(e)
        r.event('error', message=error)
    finally:
        if r.pid is not None and r.alive():
            bench.close_game(r.pid)
        ended = time.time()
        duration = ended - started.timestamp()
        cooldown_until = ended + bench.cooldown_seconds(duration)
        RESULTS.mkdir(parents=True, exist_ok=True)
        bench.COOLDOWN_FILE.write_text(json.dumps({'tag': a.tag, 'duration_s': round(duration), 'until': cooldown_until}),
                                       encoding='utf-8')
        log('run took %d s; GPU cooldown of %d s starts now' % (duration, cooldown_until - ended))
        out.mkdir(parents=True, exist_ok=True)
        if (GAME / 'logs' / 'latest.log').exists():
            shutil.copy2(GAME / 'logs' / 'latest.log', out / 'latest.log')
        shots = out / 'screenshots'
        shots.mkdir(exist_ok=True)
        for p in (GAME / 'screenshots').glob('*.png') if (GAME / 'screenshots').exists() else []:
            if p.name not in shots_before:
                shutil.copy2(p, shots / p.name)
        for p in (GAME / 'crash-reports').glob('*') if (GAME / 'crash-reports').exists() else []:
            if p.name not in crashes_before:
                shutil.copy2(p, out / p.name)
                r.event('crash_report', file=p.name)
        # Restore the pack option this run changed (the instance is re-mirrored before every run anyway).
        (out / 'events.json').write_text(json.dumps({'tag': a.tag, 'plan': a.plan, 'error': error, 'started': str(started),
                                                     'duration_s': round(duration), 'events': r.events}, indent=1),
                                         encoding='utf-8')
        try:
            analyze(a.tag, a.ref)
        finally:
            bench.sleep_until(cooldown_until, 'after %s' % a.tag)


# ---------------------------------------------------------------------------------------------------- analysis

TS = re.compile(r'^\[(\d{2}\w{3}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\] \[([^\]]*)/(\w+)\] \[([^\]]*)\]: (.*)$')


def parse_ts(s):
    return dt.datetime.strptime(s, '%d%b%Y %H:%M:%S.%f')


def norm_error(msg):
    """One key per kind of error: lists in brackets and registry ids after 'for' are printed in a different order
    or for a different entry from run to run."""
    msg = re.sub(r'\[[^\]]*\]?', '[...]', msg)
    msg = re.sub(r'\b(for|tag) [a-z0-9_.-]+:[a-z0-9_/.-]+', r'\1 <id>', msg)
    msg = re.sub(r'\d+', '#', msg)
    return msg[:160]


def analyze(tag, ref=None):
    out = RESULTS / tag
    lines = (out / 'latest.log').read_text(encoding='utf-8', errors='replace').splitlines() if (out / 'latest.log').exists() else []
    ev = json.loads((out / 'events.json').read_text(encoding='utf-8'))
    recs = []
    for ln in lines:
        m = TS.match(ln)
        if m:
            recs.append((parse_ts(m.group(1)), m.group(3), m.group(4).split('/')[0], m.group(5)))
    rep = {'tag': tag, 'plan': ev['plan'], 'run_error': ev['error'], 'duration_s': ev['duration_s']}
    text = '\n'.join(lines)
    rep['already_in_group'] = text.count('Already in a group')
    rep['gl_debug_messages'] = len(re.findall(r'\[GL\] Debug message', text))
    rep['gl_suppressed_summaries'] = text.count('Repeated debug messages suppressed')
    rep['depth_format_mismatch'] = text.lower().count('depth formats do not match')
    rep['pipeline_null'] = text.count('pipeline is null')
    rep['failed_functions'] = sorted(set(re.findall(r'Failed to load function (visust5:\w+)', text)))
    rep['crash_reports'] = [e['file'] for e in ev['events'] if e['event'] == 'crash_report']
    rep['markers'] = sorted(set(re.findall(MARK + r' (\w+)', text)))
    rep['screenshots'] = sorted(p.name for p in (out / 'screenshots').glob('*.png')) if (out / 'screenshots').exists() else []
    errs = {}
    for t, lvl, src, msg in recs:
        if lvl in ('ERROR', 'FATAL'):
            k = src + ': ' + norm_error(msg)
            errs[k] = errs.get(k, 0) + 1
    rep['error_kinds'] = len(errs)
    rep['errors'] = errs

    # s1: what happened after each harness step, until the next one.
    steps = [e for e in ev['events'] if e['event'] in ('toggle', 'switch_pack', 'apply_option', 'resource_reload', 'chunk_reload')]
    s1 = []
    for i, e in enumerate(steps):
        # Events from runs before the stamp moved ahead of the key post were up to ~0.3 s late; 1 s margin covers both.
        t0 = parse_ts(e['time']) - dt.timedelta(seconds=1)
        t1 = parse_ts(steps[i + 1]['time']) - dt.timedelta(seconds=1) if i + 1 < len(steps) else dt.datetime.max
        window = [(t, lvl, src, msg) for t, lvl, src, msg in recs if t0 <= t < t1]
        iris = [msg for t, lvl, src, msg in window if src in ('Visus', 'Oculus', 'Iris') and (
            msg.startswith('Using shaderpack') or msg.startswith('Shaders are disabled') or 'Falling back' in msg)]
        atm = [msg for t, lvl, src, msg in window if msg.startswith('[Atmosphera]')]
        packs = sorted(set(re.findall(r"shaderpack '([^']+)'", ' '.join(atm))))
        active_again = sum(1 for m in atm if 'is active again' in m)
        s1.append({'step': e['event'], 'detail': e.get('to') or e.get('option') or '', 'iris': iris,
                   'atmosphera_lines': len(atm), 'atmosphera_packs': packs, 'atmosphera_active_again': active_again,
                   'errors': sum(1 for t, lvl, src, msg in window if lvl == 'ERROR')})
    rep['s1_steps'] = s1
    verdict = []
    if ev['error']:
        verdict.append('run error: ' + ev['error'])
    if rep['crash_reports']:
        verdict.append('crash report')
    if rep['already_in_group']:
        verdict.append('Already in a group x%d' % rep['already_in_group'])
    if rep['failed_functions']:
        verdict.append('scene commands failed to load: %s' % ', '.join(rep['failed_functions']))
    for st in s1:
        if st['step'] == 'toggle' and st['detail'] == 'off' and not st['atmosphera_active_again']:
            verdict.append('toggle off: no Atmosphera feature came back')
        if st['step'] == 'toggle' and st['detail'] == 'on' and st['atmosphera_packs'] and DEFAULT_PACK not in st['atmosphera_packs']:
            verdict.append('toggle on: Atmosphera saw %s' % st['atmosphera_packs'])
        if st['step'] == 'switch_pack' and st['atmosphera_packs'] and st['atmosphera_packs'] != [SWITCH_PACK]:
            verdict.append('pack switch: Atmosphera saw %s' % st['atmosphera_packs'])
    if ref:
        rref = json.loads((RESULTS / ref / 't5.json').read_text(encoding='utf-8'))
        new = sorted(set(errs) - set(rref['errors']))
        rep['errors_new_vs_ref'] = new
        rep['errors_gone_vs_ref'] = sorted(set(rref['errors']) - set(errs))
        if new:
            verdict.append('%d new ERROR kinds vs %s' % (len(new), ref))
    rep['verdict'] = 'PASS' if not verdict else 'CHECK: ' + '; '.join(verdict)
    (out / 't5.json').write_text(json.dumps(rep, indent=1), encoding='utf-8')
    print(json.dumps({k: v for k, v in rep.items() if k != 'errors'}, indent=1))
    return rep


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    p = sub.add_parser('run')
    p.add_argument('tag')
    p.add_argument('--plan', default='s5,s9,s1')
    p.add_argument('--minutes', type=float, default=30)
    p.add_argument('--pack', default=DEFAULT_PACK)
    p.add_argument('--remove-mod', action='append', default=[])
    p.add_argument('--add-mod', action='append', default=[])
    p.add_argument('--ref')
    p.add_argument('--force', action='store_true')
    p = sub.add_parser('analyze')
    p.add_argument('tag')
    p.add_argument('--ref')
    a = ap.parse_args()
    if a.cmd == 'run':
        cmd_run(a)
    else:
        analyze(a.tag, a.ref)


if __name__ == '__main__':
    main()
