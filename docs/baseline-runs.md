# Baseline speedruns (old task runner)

Speedruns with `@gamer` before the scheduler / strategy changes, recorded with the decision log
(`versions/1.21.1/run/altoclef/logs/`). Purpose: a baseline to compare the new scheduler (step 4) and exploration
(step 0b) against, and a list of problems found. See `docs/task-architecture.md`.

## Run 1 — 2026-11-28 12:26 (session `2026-11-28_12-26-00`)

Discarded as baseline: the sword may have been dropped by accident (Q). Findings that don't depend on it:

- **14 min Mob Defense ↔ User Tasks ping-pong** (min 8–22, ~380 switches each way, within ~1.6 blocks):
  user task "collect water bucket", Mob Defense 80 ("can't deal with the skeleton → run away"), but
  `RunAwayFromHostilesTask` reports "Completing goal" at once and doesn't move; next tick Mob Defense 0 → user task
  → skeleton again. The thrashing detector fired 79×, nothing changed.
- Summary: 33.5 min, 845 switches, walked 3394 blocks (net 365), water 3 s, unstuck 0×; died after `@stop`
  (skeleton) — the bot doesn't defend itself when no task runs.

## Run 2 — 2026-11-28 13:02 (session `2026-11-28_13-02-36`)

Outcome: died in the Nether at 23:31 (wither skeleton), stopped after respawn. Farming / overworld progression was
good: stone tools 2:05, iron pickaxe 7:40, diamond pickaxe 13:05, full diamond armor 15:21, portal 21:58.

| Phase | Time |
|---|---|
| wood → wooden pickaxe | 0:57–1:26 |
| stone tools | 1:26–2:05 |
| food (pigs/cows, smoker) | 2:07–5:45 |
| iron, shield, flint and steel, iron pickaxe | 5:45–7:40 |
| coal/iron, bucket | 7:42–10:37 |
| water bucket, gold, golden helmet | 10:37–12:25 |
| diamonds, sword, armor | 12:25–15:23 |
| smoker meat | 15:24–16:51 |
| find lava, build portal | 16:51–21:58 |
| Nether: fortress, skeletons/blazes, death | 22:03–23:31 |

Summary at the end: 22.7 min, 46 switches (42 Mob Defense ↔ User Tasks), walked 1108 blocks (max 197 from start),
water 0 s, unstuck 0×, Mob Defense 83 s.

Problems:
1. **Run-away that doesn't run (again).** Last 10 s before death: Mob Defense 60–70 with
   `RunAwayFromHostilesTask … Completing goal`, position within ~1 block, HP 17 → 8.5. Same pattern as run 1.
   → Look at `RunAwayFromHostilesTask`: goal counts as reached at once (distance 30, skeletons=true).
2. **Fire while under attack:** at HP 8.5 World Survival "putting out fire" (interact with empty hand, waiting)
   took over; HP 8.5 → 0 in ~4 s. Fire and skeleton damage can't be told apart from the log.
3. **Mob Defense ↔ User Tasks thrashing in the Nether** (22:13–22:23, 23:04–23:17): Mob Defense flips 0 ↔ 60/65
   at the skeleton's detection edge while the user task walks to the fortress / kills blazes. 0 blaze rods collected.
4. **No weapon in both runs**, shovel lost in the Nether skeleton fight (user observation). Item drops are not in the
   log → `item_drop` logging needed (TODO.md, Misc).
5. **Waiting at furnaces/smokers:** ~5 min standing still in total (61 s, 51 s, 51 s, 52 s, 31 s, 21 s).
6. **Coal/raw iron restart ping-pong** 8:01–8:37 (~19 restart pairs at the same spot), cause not visible in the log.
7. Food collection kept going at full hunger (target 220 food units).
8. "Getting within 2147483647 blocks" (Integer.MAX_VALUE as range) in water/lava approach tasks — cosmetic, but
   worth checking.

Log gaps found: item drops, inventory/equipped item (sword?), damage source per hit.

## Fixes after runs 1–2 (branch `feature/baseline-metrics`)

- **Run-away that didn't run:** `EntityTracker.getHostiles()` only lists mobs angry at the player right now and
  within 26 blocks; a skeleton drops out of it for a tick when it loses line of sight. `RunAwayFromHostilesTask`
  used that list as its goal ("no hostile within 30") → goal reached at once → Mob Defense 80 → 0 → user task →
  80 again, the path was cancelled every time. Now the task remembers what it flees from until those mobs are dead,
  unloaded or 30 blocks away; Mob Defense checks the running task instance, not a fresh copy.
- **Logging:** inventory snapshot every minute (`inventory`), `item_lost` (broke / bot_drop / unknown) and
  `item_drop` for tools, weapons, armor; items and durability in the `death` event; lost items in the summary.
  Suspicion to check in the next run: the stone sword broke (131 uses) and was never replaced.

## Run 3 — 2026-11-28 14:16 (session `2026-11-28_14-16-10`), notes while running

- **Mining instead of picking up:** `MineOrCollectTask` picks "drop vs. next block" by distance only
  (`dropSq + 10 <= blockSq`, squared units); in a gravel patch it mined ~20 gravel while ~5 flint lay on the
  ground (user observation), 33 gravel thrown away in one minute. Affects every `MineAndCollectTask`.
  → Fixed (`util/DropOrMine`, tested): drops are picked up first when the items lying within 32 blocks cover
  what is still needed; otherwise a drop wins if it is at most 4 blocks further away than the next block.
- **Throw → re-pickup loop (speedrun-specific):** `BeatMinecraftTask` throws the wooden pickaxe once a stone
  pickaxe exists; standing still, the bot picks it up again after the 2 s pickup delay → 7 throws in ~14 s.
  To move into the general inventory management later (TODO step 7).
- Sword present this time (`stone_sword 119/131` at tick 4801).

## Run 4 — 2026-11-28 14:29 (session `2026-11-28_14-29-57`, aborted after 55 s)

- **Sword thrown away by the bot**, logged as `item_drop` + `item_lost (bot_drop)` at 17.9 s while approaching a
  creeper. Cause (same as the Nether shovel in run 2, user's guess was right):
  1. `ItemHelper.canThrowAwayStack` returned true for every unprotected item when `core.throwAwayUnusedItems` is
     on (default) — tools, weapons and armor counted as "unused".
  2. The cursor clean-up copied into 9 tasks' `onStart` (here `AbstractDoToEntityTask`: every fight starts one)
     ended with an unconditional click outside the window; 6 more places had a last-resort throw. With the
     inventory full, a tool in the cursor (just crafted / being swapped by equip code) was thrown out.
  → Fixed: damageable items are never "unused" (worse tools are still replaced via `canThrowAwayWorseTool`); the
  final throw only happens after swapping in a garbage slot; last-resort throws skip damageable items;
  `ResourceTask` no longer throws the target it just collected.

## Run 5 — 2026-11-28 14:37 (session `2026-11-28_14-37-02`, died after 17 min)

- **Sword flickering** in the hotbar while shield-blocking (screen recording), 4 diamond sword drops at ticks
  19525–19833 while "Killing skeleton"; afterwards the bot fought without a sword and was shot by a skeleton.
  Cause: with `forceFieldStrategy: FASTEST`, `KillAura.tickEnd` equipped the sword (`performDelayedAttack` →
  `equipWeapon`) and took it out again in the same tick (`performFastestAttack` → `forceDeequipHitTool`, a sword is a
  `ToolItem`). `forceDeequip` also searched room for the (empty) hand stack instead of the cursor stack.
  → Fixed: swords stay in hand; `forceDeequip` looks for room for the cursor item outside the hand slot; central guard
  in `SlotHandler`: a click outside the window never throws a damageable item unless the inventory holds one at least
  as good; refused throws are logged as `item_drop_blocked` with the calling class and line.
- **Hunting sheep with a full inventory of meat:** `beatMinecraft.foodUnits` = 220 and the gate was
  `calculateFoodPotential < 220` without hysteresis. Raw meat already counts at its cooked value, so one eaten steak
  (220 → 212) restarted the hunt; cooking could not raise the score. Raw meat (17 pieces) stayed raw: the cooking
  task drops to priority 0.01 once 5 cooked items exist.
  → Fixed: collecting restarts below 75 % of the target and goes on to 100 %; smelt tasks use the leftover heat once
  their target is done for raw meat / ores from the inventory (`ExtraSmelting`, no extra fuel; parent waits);
  `CollectFoodTask` cooks again instead of wandering when cooked food would be enough.
- Mob Defense ↔ User Tasks ping-pong again at the end (pickup of the dropped sword vs. skeleton), 25 switches / 10 s.

## Run 6 — 2026-11-28 14:59 (session `2026-11-28_14-59-54`, 50 min, died in the Nether)

Code: sword fixes in, food/smelting fixes not yet.
- Diamond pickaxe/sword at 15 min, diamond armor at 17 min, Nether at 23.5 min; never reached the End.
- **Death** at tick 58031 ("slain by Enderman"): pearl hunt kept attacking endermen from 18.8 HP down to 0, Mob
  Defense stayed at priority 0 (endermen are not handled as threats while the user task hunts them), no retreat/heal.
- **Fortress search** ~9.7 min of wandering before the first blaze. **Pearl hunt** 8 min for 11/64 pearls
  (`targetEyes: 64` set in this session — far more than one stronghold needs).
- **Thrashing** Mob Defense ↔ User Tasks: 11 events, worst 79 switches in ~2 min during the blaze hunt (piglins),
  ~3.75 min in total. Scheduler topic (step 5).
- **Tool drop loop** ticks 18245–19498: iron/stone pickaxes and stone sword thrown 80 times and picked up again after
  the diamond tools were made (worse-tool garbage while standing still) → inventory management, TODO step 7.
- No sword loss, no `item_drop_blocked` events. Food: animals hunted while holding 17 raw porkchops (old code);
  porkchops cooked only at ~19 min. Min food 12, water time 21.75 s.
