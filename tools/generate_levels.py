#!/usr/bin/env python3
"""Design the CubeWars arena rotation and write it into the game.

    ./tools/generate_levels.py                 # regenerate the ten shipped arenas
    ./tools/generate_levels.py --seed 7 --count 12
    ./tools/generate_levels.py --dry-run       # print the layouts, touch nothing

Why a script and not a level editor: an arena is only playable if it agrees with the physics. A
block one tile taller than a jump is a wall across the only route to the gate; a pit one tile wider
than a running jump is a dead end; cover standing on the lip of a hole is a ramp into it. Those are
arithmetic, not taste, so they belong to something that can check them — every layout below is
validated against the real numbers from ``GameConfig`` before it is allowed out, and the same
invariants are re-checked from the Kotlin side by ``ArenaGeometryTest``.

The output is Kotlin source rather than an asset file, which keeps the project's promise that it
ships as code: no file to load, no parser to write, no I/O anywhere near the domain, and the
generated arenas are exercised by the same plain JVM tests as everything else.
"""

from __future__ import annotations

import argparse
import math
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path

# --------------------------------------------------------------------------------------------
# The physics the layouts have to agree with. These mirror GameConfig; CONSISTENCY_NOTE below is
# checked by ArenaGeometryTest, which re-measures them by running the real simulation.
# --------------------------------------------------------------------------------------------

TILE = 16.0
GRAVITY = 1000.0
JUMP_VELOCITY = 372.0
MAX_SPEED = 122.0
GROUND_HEIGHT_TILES = 2
LEDGE_THICKNESS = 8.0
EXIT_INSET_TILES = 4
EXIT_WIDTH_TILES = 2

#: How far a full jump lifts the player, in world units.
JUMP_RISE = JUMP_VELOCITY**2 / (2 * GRAVITY)

#: How far a jump taken at full running speed carries, in world units.
JUMP_REACH = MAX_SPEED * (2 * JUMP_VELOCITY / GRAVITY)

#: Widest pit we are willing to author, in tiles. Kept well inside the real reach: the measured
#: figure assumes a perfect take-off at top speed, and a player who mistimes it should still make it.
MAX_PIT_TILES = 4

#: Cover is two tiles tall — the tallest thing a player can still climb without a running start.
COVER_HEIGHT_TILES = 2

#: How far cover must stand from the pit ahead of it. Running off a two-tile block carries you about
#: thirty units before you land, so anything nearer than this is a launch ramp into the hole.
COVER_PIT_CLEARANCE_TILES = 3

#: Tile rows the one-way ledges sit on. Each step up is three tiles — 48 units — which a full jump
#: clears with room to spare, and the lowest is reachable from the top of a piece of cover.
TIERS = (5, 8, 11, 14)

#: Nothing is authored inside this many tiles of the right-hand wall: that is the gate's ground, and
#: it has to be walked into rather than fallen past.
EXIT_CLEARANCE_TILES = 6

#: Nor inside this many of the left-hand wall. The player starts two tiles in and needs somewhere to
#: get moving; cover any closer is a wall you walk into before you have finished reading the level.
SPAWN_CLEARANCE_TILES = 10

#: How far a ledge must end before the run of floor beneath it does.
#:
#: Stepping off the end of a ledge is a fall, and a fall carries you sideways while it lasts — about
#: two and a half tiles off the lowest tier and nearly five off the highest. A ledge whose end lines
#: up with a pit is therefore not a platform, it is a diving board, and the arena's only route east
#: quietly becomes a hole.
LEDGE_RUN_MARGIN_TILES = 5

THEMES = (
    "FOUNDRY",
    "SCRAPYARD",
    "GANTRY",
    "CINDER",
    "FROST",
    "TOXIC",
    "CIRCUIT",
    "VOID",
    "DUSK",
    "SPIRE",
)

#: Two words per theme, drawn without replacement so no two arenas in a rotation share a name.
NAME_POOL = {
    "FOUNDRY": ["THE FOUNDRY", "SLAG WORKS", "THE CRUCIBLE", "FORGE DECK"],
    "SCRAPYARD": ["THE SCRAPLINE", "BREAKER'S YARD", "THE PICKING FIELD", "RUST ROW"],
    "GANTRY": ["THE GANTRY", "HIGH CATWALK", "LOADING SPINE", "THE DERRICK"],
    "CINDER": ["THE CINDER PITS", "ASH TERRACE", "EMBER SHELF", "BURNOUT"],
    "FROST": ["THE COLD STORE", "RIME YARD", "FROSTLINE", "GLACIER DOCK"],
    "TOXIC": ["THE SPILL", "SUMP LEVEL", "GREENWATER", "THE OUTFALL"],
    "CIRCUIT": ["THE MAINBOARD", "TRACE LINE", "LOGIC WELL", "COLD SOLDER"],
    "VOID": ["THE LONG DARK", "NULL SECTOR", "OUTFIELD", "THE QUIET"],
    "DUSK": ["LAST LIGHT", "THE LOW SUN", "EVENING SPAN", "DUSK MARKET"],
    "SPIRE": ["THE SPIRE GATE", "TOP OF THE WORLD", "THE NEEDLE", "CROWN DECK"],
}


# --------------------------------------------------------------------------------------------
# Layout model
# --------------------------------------------------------------------------------------------


@dataclass
class Run:
    """A run of floor spanning tile columns ``[start, end)``."""

    start: int
    end: int

    @property
    def length(self) -> int:
        return self.end - self.start


@dataclass
class Solid:
    x: int
    y: int
    width: int
    height: int


@dataclass
class Ledge:
    x: int
    y: int
    width: int


@dataclass
class Arena:
    index: int
    name: str
    theme: str
    width_tiles: int
    wave_quota: int
    extra_enemies: int
    spawn_interval_scale: float
    extra_alive: int
    unlock_bonus: int
    runs: list[Run] = field(default_factory=list)
    solids: list[Solid] = field(default_factory=list)
    ledges: list[Ledge] = field(default_factory=list)

    @property
    def pits(self) -> list[tuple[int, int]]:
        """The gaps between runs, as ``(from, to)`` tile columns."""
        return [
            (left.end, right.start)
            for left, right in zip(self.runs, self.runs[1:])
            if right.start > left.end
        ]

    def run_containing(self, start: int, end: int) -> Run | None:
        """The run of floor that fully supports tile columns ``[start, end)``, if any."""
        for run in self.runs:
            if run.start <= start and run.end >= end:
                return run
        return None


# --------------------------------------------------------------------------------------------
# Generation
# --------------------------------------------------------------------------------------------


def difficulty_for(index: int) -> dict:
    """The per-arena dial, ramped so the rotation only ever gets harder.

    ``ArenaGeometryTest`` asserts each of these curves is monotonic, which is the machine-checkable
    half of "the arenas get longer and meaner as the rotation runs".
    """
    return {
        "wave_quota": 3 + index // 3,
        "extra_enemies": index // 2,
        "spawn_interval_scale": round(1.0 - 0.034 * index, 3),
        "extra_alive": index // 3,
        "unlock_bonus": index // 2,
    }


def lay_floor(rng: random.Random, arena: Arena) -> None:
    """Cuts the floor into runs separated by jumpable pits.

    The first run has to be long enough to stand on and get moving, and the last has to carry the
    gate with room to walk into it, so both are given a floor length before the middle is diced up.
    """
    width = arena.width_tiles
    # More holes the deeper into the rotation you are, but never so many that a run gets too short
    # to fight on.
    pit_count = min(2 + arena.index // 2, max(1, (width - 24) // 12))

    opening = rng.randint(11, 15)
    closing = rng.randint(12, 16)
    middle_span = width - opening - closing
    pit_widths = [rng.randint(3, MAX_PIT_TILES) for _ in range(pit_count)]
    floor_span = middle_span - sum(pit_widths)
    if floor_span < 8 * (pit_count - 1):
        raise ValueError(f"arena {arena.index} is too short for {pit_count} pits")

    # Share the leftover floor between the middle runs, then jitter it so the rhythm is not a grid.
    middle_runs = max(pit_count - 1, 0)
    slices = [floor_span // (middle_runs + 1)] * (middle_runs + 1) if middle_runs >= 0 else []
    slices[-1] += floor_span - sum(slices)
    for i in range(len(slices) - 1):
        shift = rng.randint(-2, 2)
        if slices[i] + shift >= 8 and slices[i + 1] - shift >= 8:
            slices[i] += shift
            slices[i + 1] -= shift

    cursor = 0
    arena.runs.append(Run(cursor, cursor + opening))
    cursor += opening
    for i, pit in enumerate(pit_widths):
        cursor += pit
        length = slices[i] if i < len(slices) else 0
        if i == len(pit_widths) - 1:
            arena.runs.append(Run(cursor, width))
            cursor = width
            break
        arena.runs.append(Run(cursor, cursor + length))
        cursor += length

    # Merge the tail: whatever is left over belongs to the closing run so the gate always has floor.
    arena.runs[-1] = Run(arena.runs[-1].start, width)


def place_cover(rng: random.Random, arena: Arena) -> None:
    """Puts climbable cover on the floor — the only way up onto the first tier of ledges.

    Every piece has to sit clear of the pit at either end of its run, and clear of the gate. Cover
    that fails those tests is not placed rather than nudged: an arena with one block fewer is a
    slightly plainer arena, and an arena with a block on the lip of a hole is a broken one.
    """
    gate_edge = arena.width_tiles - EXIT_CLEARANCE_TILES
    for run in arena.runs:
        wanted = 1 if run.length < 13 else rng.randint(2, 3)
        placed: list[Solid] = []
        for _ in range(wanted):
            for _attempt in range(24):
                width = rng.randint(2, 3)
                low = run.start + COVER_PIT_CLEARANCE_TILES
                high = run.end - COVER_PIT_CLEARANCE_TILES - width
                if high < low:
                    break
                x = rng.randint(low, high)
                if x + width > gate_edge or x < SPAWN_CLEARANCE_TILES:
                    continue
                if any(x < other.x + other.width + 4 and other.x < x + width + 4 for other in placed):
                    continue
                block = Solid(x, GROUND_HEIGHT_TILES, width, COVER_HEIGHT_TILES)
                placed.append(block)
                arena.solids.append(block)
                break


def place_ledges(rng: random.Random, arena: Arena) -> None:
    """Stacks the one-way tiers.

    Every ledge is required to sit entirely over a run of floor. A walkway hanging over a pit reads
    beautifully and plays terribly: step off the end of one and the fall that used to cost you a
    jump now costs you the run.
    """
    gate_edge = arena.width_tiles - EXIT_CLEARANCE_TILES
    tiers = TIERS if arena.index >= 3 else TIERS[:3]
    for tier_number, y in enumerate(tiers):
        # Thinner and rarer the higher you climb, so the top of the arena stays a commitment
        # rather than a second floor.
        spacing = 13 + 3 * tier_number
        wanted = max(3, arena.width_tiles // spacing)
        placed: list[Ledge] = []
        for _ in range(wanted):
            for _attempt in range(30):
                width = rng.randint(4, 6 - min(tier_number, 2))
                x = rng.randint(1, max(1, gate_edge - width))
                if x + width > gate_edge:
                    continue
                # Both ends have to sit well inside the floor below, so stepping off either one is a
                # drop onto ground rather than into the pit next to it.
                if arena.run_containing(
                    x - LEDGE_RUN_MARGIN_TILES, x + width + LEDGE_RUN_MARGIN_TILES
                ) is None:
                    continue
                if any(x < other.x + other.width + 3 and other.x < x + other.width + 3 for other in placed):
                    continue
                ledge = Ledge(x, y, width)
                placed.append(ledge)
                arena.ledges.append(ledge)
                break


def generate(index: int, rng: random.Random, used_names: set[str]) -> Arena:
    theme = THEMES[index % len(THEMES)]
    pool = [name for name in NAME_POOL[theme] if name not in used_names]
    name = rng.choice(pool if pool else NAME_POOL[theme])
    used_names.add(name)

    dials = difficulty_for(index)
    arena = Arena(
        index=index,
        name=name,
        theme=theme,
        # Non-decreasing by construction: the rotation gets longer as it gets meaner.
        width_tiles=76 + 6 * index,
        wave_quota=dials["wave_quota"],
        extra_enemies=dials["extra_enemies"],
        spawn_interval_scale=dials["spawn_interval_scale"],
        extra_alive=dials["extra_alive"],
        unlock_bonus=dials["unlock_bonus"],
    )
    lay_floor(rng, arena)
    place_cover(rng, arena)
    place_ledges(rng, arena)
    return arena


# --------------------------------------------------------------------------------------------
# Validation — the whole reason this is a program
# --------------------------------------------------------------------------------------------


def validate(arena: Arena) -> list[str]:
    """Everything that has to be true before an arena is allowed into the game."""
    problems: list[str] = []
    floor_y = GROUND_HEIGHT_TILES * TILE

    # Pits have to be crossable at a run.
    crossable = JUMP_REACH / TILE - 0.25
    for start, end in arena.pits:
        if end - start > crossable:
            problems.append(f"pit at {start}..{end} is {end - start} tiles, wider than {crossable:.2f}")

    # Cover has to be climbable, supported, and clear of the hole in front of it.
    if not arena.solids:
        problems.append("no climbable cover, so the ledges cannot be reached")
    for block in arena.solids:
        top = (block.y + block.height) * TILE
        if top - floor_y > JUMP_RISE:
            problems.append(f"cover at {block.x} tops out at {top}, above the {JUMP_RISE:.1f} a jump rises")
        if arena.run_containing(block.x, block.x + block.width) is None:
            problems.append(f"cover at {block.x} hangs over a pit")
        ahead = next((pit for pit in arena.pits if pit[0] >= block.x + block.width), None)
        if ahead is not None and ahead[0] - (block.x + block.width) < COVER_PIT_CLEARANCE_TILES:
            problems.append(f"cover at {block.x} is a ramp into the pit at {ahead[0]}")

    # The lowest tier has to be reachable from the tallest cover.
    if arena.ledges and arena.solids:
        lowest_ledge = min(ledge.y * TILE + LEDGE_THICKNESS for ledge in arena.ledges)
        highest_cover = max((block.y + block.height) * TILE for block in arena.solids)
        if lowest_ledge - highest_cover > JUMP_RISE:
            problems.append(
                f"lowest ledge at {lowest_ledge} is unreachable from cover topping out at {highest_cover}"
            )

    # Ledges have to stand over floor, with room either side to land in when you step off one.
    for ledge in arena.ledges:
        if arena.run_containing(
            ledge.x - LEDGE_RUN_MARGIN_TILES, ledge.x + ledge.width + LEDGE_RUN_MARGIN_TILES
        ) is None:
            problems.append(f"ledge at {ledge.x},{ledge.y} ends over, or beside, a pit")

    # And there has to be room to get moving before the first thing to walk into.
    for block in arena.solids:
        if block.x < SPAWN_CLEARANCE_TILES:
            problems.append(f"cover at {block.x} is inside the player's starting run-up")

    # The gate needs floor under it and nothing standing in it.
    gate_left = arena.width_tiles - EXIT_INSET_TILES
    gate_right = gate_left + EXIT_WIDTH_TILES
    if arena.run_containing(gate_left, gate_right) is None:
        problems.append("the gate has no floor under it")
    for block in arena.solids:
        if block.x < gate_right and gate_left < block.x + block.width:
            problems.append(f"cover at {block.x} is standing in the gate")
    if arena.runs[0].start != 0 or arena.runs[0].length < 6:
        problems.append("the player spawns off the end of the world")
    if arena.runs[-1].end != arena.width_tiles:
        problems.append("the last run of floor does not reach the far wall")

    return problems


def check_rotation(arenas: list[Arena]) -> list[str]:
    """The curves that have to be monotonic across the whole rotation."""
    problems = []

    def monotonic(name: str, values: list, ascending: bool = True) -> None:
        ordered = sorted(values) if ascending else sorted(values, reverse=True)
        if values != ordered:
            problems.append(f"{name} is not monotonic: {values}")

    monotonic("width", [a.width_tiles for a in arenas])
    monotonic("extra enemies", [a.extra_enemies for a in arenas])
    monotonic("spawn interval", [a.spawn_interval_scale for a in arenas], ascending=False)
    if len({a.name for a in arenas}) != len(arenas):
        problems.append("two arenas share a name")
    return problems


# --------------------------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------------------------

HEADER = '''package com.heixss.cubewars.data.level

import com.heixss.cubewars.domain.model.LevelDifficulty
import com.heixss.cubewars.domain.model.LevelTheme

/**
 * The arena rotation, generated.
 *
 * DO NOT EDIT BY HAND — regenerate with:
 *
 * ```
 * ./tools/generate_levels.py --seed {seed} --count {count}
 * ```
 *
 * Every layout below was checked against the real jump arithmetic before it was written out: no
 * cover taller than a jump rises, no pit wider than a running jump carries, no block on the lip of a
 * hole, nothing standing in the gate. `ArenaGeometryTest` then re-measures all of it by running the
 * actual simulation, so a change to gravity or to the jump is caught here rather than by a player
 * stuck against a wall they cannot climb.
 */
internal object GeneratedLevels {{

    /** {count} arenas, in the order the rotation runs them. */
    val blueprints: List<LevelBlueprint> = listOf(
'''

FOOTER = """    )
}
"""


def render(arenas: list[Arena], seed: int) -> str:
    out = [HEADER.format(seed=seed, count=len(arenas))]
    for arena in arenas:
        out.append(render_arena(arena))
    out.append(FOOTER)
    return "".join(out)


def render_arena(arena: Arena) -> str:
    pits = ", ".join(f"{a}..{b}" for a, b in arena.pits) or "none"
    lines = [
        f"        // {arena.index + 1}. {arena.name} — {arena.width_tiles} tiles, "
        f"{len(arena.runs)} runs of floor, pits at {pits}.\n",
        "        LevelBlueprint(\n",
        f'            name = "{arena.name}",\n',
        f"            theme = LevelTheme.{arena.theme},\n",
        f"            widthTiles = {arena.width_tiles},\n",
        f"            waveQuota = {arena.wave_quota},\n",
        "            difficulty = LevelDifficulty(\n",
        f"                extraEnemies = {arena.extra_enemies},\n",
        f"                spawnIntervalScale = {arena.spawn_interval_scale}f,\n",
        f"                extraAlive = {arena.extra_alive},\n",
        f"                unlockBonus = {arena.unlock_bonus},\n",
        "            ),\n",
        "            ground = listOf(\n",
    ]
    for run in arena.runs:
        lines.append(f"                GroundRun({run.start}, {run.end}),\n")
    lines.append("            ),\n            solids = listOf(\n")
    for block in sorted(arena.solids, key=lambda b: b.x):
        lines.append(
            f"                SolidSpec({block.x}, {block.y}, {block.width}, {block.height}),\n"
        )
    lines.append("            ),\n            ledges = listOf(\n")
    for ledge in sorted(arena.ledges, key=lambda l: (l.y, l.x)):
        lines.append(f"                LedgeSpec({ledge.x}, {ledge.y}, {ledge.width}),\n")
    lines.append("            ),\n        ),\n")
    return "".join(lines)


def sketch(arena: Arena) -> str:
    """A rough picture of an arena, for eyeballing a layout without launching the game."""
    height = max(TIERS) + 2
    grid = [[" "] * arena.width_tiles for _ in range(height)]
    for run in arena.runs:
        for x in range(run.start, min(run.end, arena.width_tiles)):
            grid[0][x] = "="
    for block in arena.solids:
        for x in range(block.x, block.x + block.width):
            for y in range(block.y - GROUND_HEIGHT_TILES + 1, block.y + block.height - GROUND_HEIGHT_TILES + 1):
                if 0 <= y < height and 0 <= x < arena.width_tiles:
                    grid[y][x] = "#"
    for ledge in arena.ledges:
        y = ledge.y - GROUND_HEIGHT_TILES
        for x in range(ledge.x, ledge.x + ledge.width):
            if 0 <= y < height and 0 <= x < arena.width_tiles:
                grid[y][x] = "-"
    gate = arena.width_tiles - EXIT_INSET_TILES
    for y in range(1, 4):
        if gate < arena.width_tiles:
            grid[y][gate] = "|"
    rows = ["".join(row) for row in reversed(grid)]
    return "\n".join(rows)


# --------------------------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--seed", type=int, default=20260815, help="generator seed (default: %(default)s)")
    parser.add_argument("--count", type=int, default=10, help="how many arenas (default: %(default)s)")
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent.parent
        / "app/src/main/java/com/heixss/cubewars/data/level/GeneratedLevels.kt",
        help="where to write the Kotlin source",
    )
    parser.add_argument("--dry-run", action="store_true", help="print the layouts instead of writing them")
    parser.add_argument("--sketch", action="store_true", help="draw each arena as ASCII")
    args = parser.parse_args(argv)

    print(f"a full jump rises {JUMP_RISE:.1f} units; a running one carries {JUMP_REACH:.1f}")

    arenas: list[Arena] = []
    used_names: set[str] = set()
    for index in range(args.count):
        # One generator per arena, derived from the run seed, so arena 7 is the same arena whether
        # you asked for ten of them or twelve.
        rng = random.Random(args.seed * 1000 + index)
        for attempt in range(200):
            candidate = generate(index, rng, set(used_names))
            problems = validate(candidate)
            if not problems:
                arenas.append(candidate)
                used_names.add(candidate.name)
                break
            if attempt == 199:
                print(f"arena {index} could not be laid out:", file=sys.stderr)
                for problem in problems:
                    print(f"  - {problem}", file=sys.stderr)
                return 1

    rotation_problems = check_rotation(arenas)
    if rotation_problems:
        for problem in rotation_problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1

    for arena in arenas:
        print(
            f"  {arena.index + 1:2}. {arena.name:<20} {arena.theme:<10} "
            f"{arena.width_tiles:>4} tiles  {len(arena.pits)} pits  "
            f"{len(arena.solids)} cover  {len(arena.ledges)} ledges  "
            f"quota {arena.wave_quota}"
        )
        if args.sketch:
            print(sketch(arena))
            print()

    source = render(arenas, args.seed)
    if args.dry_run:
        print(source)
        return 0

    args.out.write_text(source)
    print(f"wrote {len(arenas)} arenas to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
