# Task architecture and scheduler (roadmap steps 3 + 5) — draft

Status: **design agreed** (decisions in section 7), no code yet. Branch `feature/task-architecture`
(document only, merged to `main`; the steps in section 4 get their own branches).

## 1. What exists today

### 1.1 Runner and chains

| Part | What it does | Problems |
|---|---|---|
| `tasksystem/TaskRunner` | Flat list of `TaskChain`s. Every tick: ask every active chain for `getPriority()`, tick the highest one, call `onInterrupt` on the previous one if it changed. | Strict `>`, so on equal priority the **first registered chain wins** (registration order in `AltoClef`: User, MobDefense, DeathMenu, PlayerInteractionFix, MLG, Unstuck, PreEquip, WorldSurvival, Food). No hysteresis. |
| `tasksystem/TaskChain` | Registers itself in its constructor. `getPriority()`, `isActive()`, `onTick()`, `onInterrupt()`. Collects the ticked tasks of this tick (`getTasks()`) for debug/queries. | `getPriority()` is called every tick on every chain and is where most chains **do their actual work** (see 1.2). |
| `chains/SingleTaskChain` | One `mainTask`. `setTask` replaces it if not `equals`. On interrupt: `mainTask.interrupt(null)`, then `reset()` on the next tick it runs. | `interrupt(null)` calls `onStop(null)`, and `null` means "stopped cleanly" in the `Task` contract → an interrupted task can't tell it was interrupted. |
| `chains/UserTaskChain` | The user's task (`AltoClef.runUserTask`, 18 callers: commands, butler, `BeatMinecraftTask` restarts …). Priority fixed **50**. Idle command after finish. | Pause/stored task live half in `AltoClef` (FIXME in `cancel`). |

Chain priorities (all on one `float` scale, values from the code):

| Chain | Priority | Works inside `getPriority()`? | Notes |
|---|---|---|---|
| `MLGBucketFallChain` | 100 falling; 60 pick water back up | sets task | implements `ITaskOverridesGrounded` |
| `WorldSurvivalChain` | 100 lava / fire; 90 get to water when burning; 60 pick up extinguish water, 60 shimmy when stuck in portal | sets task | |
| `MobDefenseChain` | creeper `50 + fuse * 50` (50–100), dragon breath 70, other 60 / 65 / 70 / 80, or the last value | sets task, **force field / kill aura, shielding** | 641 lines |
| `UnstuckChain` | 55 | sets task | ties with food → Unstuck wins (registered first) |
| `FoodChain` | 55 collect food; otherwise `-inf` | **eats directly** (`startEat`/`stopEat`), asks MobDefense / MLG for state | |
| `UserTaskChain` | 50 | — | creeper with fuse 0 ties at 50 → User wins (registered first) |
| `PreEquipItemChain` | always −1 | **equips weapon** as a side effect | never "runs", only uses `getPriority` as a hook |
| `PlayerInteractionFixChain` | always `-inf` | **equips better tool, closes screens, …** | same |
| `DeathMenuChain` | always `-inf` | **respawns / reconnects** | same |

So there are really three kinds of things hidden in "chains":
1. **Task sources** that compete for control of the player (MLG, WorldSurvival, MobDefense, Unstuck, Food-collect, User).
2. **Background services** that run every tick and never take over (PreEquip, PlayerInteractionFix, DeathMenu,
   the eat/shield/force-field parts of Food and MobDefense).
3. **Shared state** other code reads: 21 files call `getFoodChain().needsToEat()` (15×),
   `getMLGBucketChain().isFalling/doneMLG/isChorusFruiting` (7× each), `getMobDefenseChain().*`, `getUserTaskChain().*`.
   Chains query each other (Food asks MobDefense whether it's shielding/putting out fire, and MLG whether we're falling)
   instead of the runner knowing who is in charge.

### 1.2 Task

| Part | What it does | Problems |
|---|---|---|
| `tasksystem/Task` | `onStart / onTick → sub task or null / onStop(interruptTask)`. One `sub`; a new sub replaces the old one unless `isEqual`. `stop` vs `interrupt` (interrupt keeps `isActive`). | No priority, no notion of alternatives. If the old sub refuses (`ITaskCanForce`), the new sub returned by `onTick` is **silently dropped** and the old one keeps running. `isFinished` is a virtual poll, there is no "failed". |
| `ITaskCanForce` | A task in the current sub path can refuse to be replaced. | Only checked for subs inside one tree; the runner never asks it when switching chains — chains just interrupt each other. |
| `ITaskRequiresGrounded` / `ITaskOverridesGrounded` | "Don't interrupt me mid-air", except by MLG. | 14 implementers; special case of the emergency tier in step 5. |
| `ITaskUsesCraftingGrid` | Marker, read by `ResourceTask` to decide whether to clear the 2x2 grid. | Implicit coupling via `thisOrChildSatisfies`. |
| `util/progresscheck/*` | Distance / linear / movement progress checkers + retry. | Used in 19 task/chain files, each with its own reaction (wander, give up, blacklist). Not connected to choosing another approach. |
| Timers | `TimerGame` / `TimerReal` in 31 task files. | Many are hysteresis or "try again later" hacks. |

### 1.3 Task classes (179)

Sorted by topic, not by abstraction level: `resources/` 48 (+11 `wood/`), `movement/` 33, `container/` 14,
`slot/` 10, `construction/` 9 (+4 `compound/`), `entity/` 9, top level 9, `misc/` 6, `speedrun/` 6 (+14 beatgame), `squashed/` 4.

Base classes: `ResourceTask` (44 subclasses), `CustomBaritoneGoalTask` (14), `CraftWithMatchingPlanksTask` (10),
`DoStuffInContainerTask` (6), `AbstractDoToEntityTask` / `AbstractDoToClosestObjectTask` (4 each), `MineAndCollectTask` (3).

Largest classes — candidates for splitting:

| Class | Lines | What is hard-coded inside |
|---|---|---|
| `speedrun/beatgame/BeatMinecraftTask` | 2380 | its own priority scheduler (see 1.4) |
| `misc/PlaceBedAndSetSpawnTask` | 635 | structure building + interaction + retries |
| `movement/MLGBucketTask` | 578 | reflex, fine as one task |
| `construction/compound/ConstructNetherPortalSpeedrunTask` | 541 | |
| `resources/CollectFoodTask` | 486 | **strategies**: crops / hay / animals / berries / pickup, scored by if-cascade |
| `container/CraftInTableTask` | 478 | **strategies**: recipe book vs. manual; get table / place table / walk to table |
| `construction/DestroyBlockTask` | 454 | |
| `InteractWithBlockTask` | 453 | L0 primitive with 450 lines of movement/unstuck logic |
| `container/SmeltIn{Furnace,Smoker,BlastFurnace}Task` | ~380 each | three near-copies |

`ResourceTask.onTick` (every L3 "get item" task goes through it) is itself a fixed strategy cascade:
move from inaccessible slot → empty cursor → pick up dropped items → loot known container → mine if present nearby →
clear crafting grid → `onResourceTick` (the subclass's own approach). Example subclass `CollectObsidianTask`:
need diamond pick → finish forced task → mine existing obsidian → trade with piglins (nether) → cast with lava + water
→ wander. Switching only happens where the cascade is written; a strategy that keeps failing is retried forever.

### 1.4 BeatMinecraftTask's private scheduler

`gatherResources`: 30 `PriorityTask` entries (`MineBlock`, `CraftItem`, `Resource`, `RecraftableItem`, `Action`),
each with a `canCall` predicate and a `*PriorityCalculator` (static, by item count, by distance). Hand-picked numbers
from 100 to 25000 (`getOrePriorityTask(Items.DIAMOND, …, 1050, 250, …)`). Thrashing control:
`changedTaskTimer` (3 s), `forcedTaskTimer` (10 s), `bypassForceCooldown`, A→B→A detection over the last 3
`taskChanges`. This is the closest thing to the target model in the code base — and the part with the most hacks.
`PriorityCalculatorTest` already covers the calculators.

### 1.5 Tests today
`TaskLifecycleTest` (start/stop/interrupt of `Task`), `PriorityCalculatorTest`. Nothing for runner or chains.

## 2. Proposed model

Package `adris.altoclef.tasksystem` (core), the decision logic Minecraft-free like `recipes/`.

### 2.1 Priority key

```java
enum Tier { EMERGENCY, SURVIVAL, NORMAL, IDLE }   // compared first; a lower tier never wins

record Priority(Tier tier, double score)          // score = weight × need × proximity × penalty (Q3)
        implements Comparable<Priority>
```

- Tiers replace the magic numbers (assignment decided, see section 7 / Q1):

  | Tier | Content | Order inside the tier |
  |---|---|---|
  | EMERGENCY | 1. falling (MLG) · 2. in lava · 3. burning / standing in fire · 4. creeper about to explode | **fixed rank**, no score |
  | SURVIVAL | mob defense (run, fight, wall, block projectiles), walk to water when burning, collect food below minimum, unstuck, cleanup after an emergency (pick water back up, time-limited) | score |
  | NORMAL | user task, plans (`BeatMinecraftTask`, later `@get`), later swarm | score |
  | IDLE | idle command | — |

  Today a creeper at full fuse (50 + 1 × 50 = 100) ties with MLG and lava at 100 and **wins by registration order**;
  the fixed rank removes that accident.
- Score (decided, Q3). Scores are only compared inside one set — the sources of one tier, or the strategies of
  *one* task — so no global scale is needed:

  ```
  score = weight × need × proximity × penalty
  ```

  | Factor | Range | Meaning |
  |---|---|---|
  | `need` | 0..1 | how urgent right now (have/needed curve, hunger, …); hysteresis over min/target count is built in **explicitly** (today hidden in the latching `minCountSatisfied`) |
  | `proximity` | 0..1 | effort / distance, e.g. `1 / (1 + d/d0)`; close by → 1, not infinite (today `1/d`); unknown location → small value |
  | `penalty` | 0..1 | decaying penalty after failure / no progress (2.4) |
  | `weight` | ≥ 0, default 1 | static importance; default in code, overridable in settings (`weights.<source>`, `weights.<task>.<strategy>`); profiles only list deviations (fits `@modified`) |

  - Not applicable = empty `evaluate`, never `-inf` — "excluded" and "bad" stay separate.
  - Switch only if `new > active × (1 + switchMargin)` (e.g. 20 %) plus `minDwell`. A **relative** margin works at any
    magnitude, so `weight` needs no upper bound.
  - Every log line (2.6) carries the four factors separately, not just the product.
  - Today's `BeatMinecraftTask` calculators are on unrelated scales (food at 1 block with low hunger ≈ `33 × 37 × 22`
    ≈ 27 000 vs. a fixed 410 for the pickaxe). Port: fixed numbers become weights (410 → 4.1, order preserved),
    distance parts become `proximity`, count logic becomes `need`; behaviour will still change → compare against the
    step 0 baseline runs.
- No raising the user task above SURVIVAL. Instead every source gets `enabled` + `weight` settings (today partly
  there: `mobDefense`, `autoEat`, `autoMLGBucket`, `extinguishSelfWithWater`, `dodgeProjectiles`, `avoidDrowning`).
  EMERGENCY sources can be disabled but never outranked. Short critical phases of a task use commitment
  (`ITaskCanForce`), not priority.
- Ties: the currently running candidate wins, then a stable id (registration order made explicit, not accidental).
- No priority on every `Task` and no inherited parent priority (decided, Q2): only sources and strategies have a
  priority; tasks influence others through hints (2.3a).

### 2.2 Scheduler (replaces `TaskRunner`)

```java
interface TaskSource {                     // was: TaskChain that competes for the player
    String id();
    Optional<Candidate> propose(BotContext ctx);   // pure query, no side effects
}
record Candidate(Task task, Priority priority)

interface BackgroundService {              // was: chains that misuse getPriority()
    void tick(BotContext ctx);             // pre-equip, interaction fixes, death menu, auto-eat, force field
}
```

Each tick:
1. Run all background services (fixed order).
2. Collect candidates from all sources.
3. Pick the best candidate:
   - EMERGENCY: preempts immediately, evaluated every tick, no hysteresis.
   - otherwise: switch only if the new candidate is in a higher tier, or in the same tier and better than the running
     one by a margin (`switchMargin`), and the running one is not committed (`ITaskCanForce` / grounded).
   - an EMERGENCY can't be preempted except by another EMERGENCY (today's `ITaskCanForce` on MLG).
4. Interrupt the old one with a clear contract: `onStop(reason)` with `reason ∈ {FINISHED, FAILED, CANCELLED, INTERRUPTED}`
   (replaces `onStop(null)` meaning two things); interrupted tasks resume via `onStart` with their fields intact.
5. Tick the chosen task; record the top N candidates with their keys for a debug command (`@tasks` / HUD).

External task sources (swarm, step 6) are just more `TaskSource`s.

### 2.3 Shared bot state instead of chain-to-chain calls

`BotContext` / `BotState` with the flags other code reads today: `isFalling`, `isEating`, `isShielding`,
`needsToEat`, `currentSource`, … Written by the service/source that owns it, read by everyone else.
Replaces the ~50 `getXChain().foo()` calls in 21 files and lets sources be unit-tested with a fake context.

#### 2.3a Hints from the active task path (instead of inherited priority)

A task has exactly one running sub, so two branches of one tree never run at the same time and never need comparing.
Competition only exists between sources (scheduler) and between strategies (inside one task), and both have their own
score. An inherited number would decide nothing.

Where a deep task does reach outside its tree today, it steers other chains directly:
- `KillEnderDragonWithBedsTask` / `OneCycleTask` call `getFoodChain().shouldStop(true)` so the bot doesn't eat in the
  middle of a bed cycle (also slows down MobDefense). `OneCycleTask` sets it but **never resets it**; only its parent's
  `onStop` does — embedded differently, eating would stay off for good.
- `AbstractDoToEntityTask` sets MobDefense's combat target and force-field radius and has to reset both itself.
- `WorldSurvivalChain` searches the user tree for `EnterNetherPortal…` so it doesn't shimmy while in the portal.

None of these compare priorities; all of them say "while I'm running, behave differently". So:

```java
interface ProvidesHints { void hints(TaskHints h); }
// e.g. h.suppress(Source.FOOD_COLLECT); h.suppressEating(); h.combatTarget(dragon); h.forceFieldRange(2);
```

- Collected every tick along the active path (the chain already collects it via `addTaskToChain`), stored in
  `BotState`, read by scheduler and services.
- A task that leaves the path takes its hints with it → no more "forgot to reset the flag".
- Hints may suppress or adjust SURVIVAL sources and services, **never EMERGENCY** (a bed cycle may delay eating,
  not the MLG).
- Active hints are part of every decision log line (2.6), so "why didn't it eat?" names the task that asked for it.
- "Don't interrupt me right now" stays commitment (`ITaskCanForce`), and the scheduler checks it when switching
  between sources too (missing today).
- Progress calculators (`ResourceTask`: have / needed) stay, as the score of a strategy.

### 2.4 Strategies inside a task

```java
interface Strategy {
    String id();
    Optional<Score> evaluate(BotContext ctx);  // empty = not applicable; Score = factors from 2.1
    Task task(BotContext ctx);                 // called every tick while active, like onTick today
}

abstract class StrategyTask extends Task {     // usually L3
    protected abstract List<Strategy> strategies();
    // onTick: StrategySelector picks the active strategy, then `return active.task(ctx)`
}
```

#### How the child task follows the strategy dynamically

The child is **not** created once per strategy and then kept. While a strategy is active, its `task(ctx)` is called
every tick and returns the task for the current situation — exactly like `onTick` returns a sub task today.
The existing mechanism in `Task.tick` does the rest:
- same strategy, same parameters → `isEqual` is true → the running child keeps running with its state;
- same strategy, new parameters (e.g. a closer obsidian block) → `isEqual` is false → old child stopped, new one started;
- other strategy → its task is returned → old child (and its whole subtree) stopped, new one started.

So the strategy only decides *which* `task(ctx)` is called; the child is always derived from the current world.

Selection happens in a pure `StrategySelector` (unit tested, replayable from the log, same helper as the scheduler):

| Situation | Reaction |
|---|---|
| active strategy becomes not applicable (`evaluate` empty) | switch **immediately** to the best applicable one |
| another strategy scores higher than active × (1 + `switchMargin`) | switch, but only after `minDwell` (e.g. 2 s) on the active one |
| active strategy's task fails or its progress checker reports no progress | penalty (score × factor, decays over e.g. 60 s) → normal comparison does the switch, no special path |
| active child is committed (`ITaskCanForce`, grounded) | switch is **pending**, logged as `blocked_by_commitment`, done as soon as the child releases (today `Task.tick` drops the new sub silently) |
| no strategy applicable | `StrategyTask` reports failed (Q4) → its parent / the scheduler reacts |

Evaluation cost: all strategies are evaluated every `evalInterval` ticks (e.g. 10), the active one every tick
(only "still applicable?"), so block/entity scans don't run 20× per second for every alternative.

Things that can go wrong, and how we see them:
- a wrong `isEqual` restarts the child every tick → counted as "restarts per strategy" in the log, thrashing detector;
- margins too small → A↔B flipping → thrashing detector + dump (2.6);
- a strategy that never makes progress but isn't detected → shows up as long time on one strategy in the run summary.

Example `CollectObsidianTask`: *mine existing* is active (score from distance). The pool turns out to be unreachable →
no progress → penalty → *cast with lava + water* wins. In the nether, *trade with piglins* becomes applicable and takes
over if it is better by the margin. Back in the overworld it is not applicable any more → immediate switch.

- Penalty when the strategy's task reports no progress (existing `progresscheck/`) or fails: score × factor for a while,
  decaying, instead of retrying forever. Uses the failure signal (2.4a).
- Same hysteresis helper as the scheduler (one implementation, tested once).
- `ResourceTask`'s cascade becomes the default strategy list of every resource task
  (pickup / loot container / mine nearby / subclass strategies), subclasses add theirs.
- First ports: `CollectObsidianTask` (mine / trade / cast), `CraftInTableTask` + `CraftInInventoryTask`
  (recipe book / manual — together with removing the recipe adapters 2.5b), `CollectFoodTask`.
- `BeatMinecraftTask.gatherResources` becomes a `StrategyTask` whose strategies are the 30 entries; the
  timer/A→B→A hacks go away.

### 2.4a Failure signal on `Task` (decided, Q4)

Today there is no notion of failure: `isFinished()` is the only answer, a task that can't go on just keeps running;
30 tasks return `TimeoutWanderTask` as a silent "don't know, walk around"; progress checkers know `failed()`, but the
result is consumed inside the task and never reaches the parent. Only `SearchChunksExploreTask.failedSearch()` exists.
Progress checkers alone are not enough: they see "doesn't move", not "no recipe", "no piglins in range",
"obsidian unreachable" — exactly what strategy selection needs for a penalty.

```java
protected final void fail(FailReason reason, String detail);    // in the task
public boolean isFailed(); public Failure failure();
enum FailReason { NO_PROGRESS, NOT_POSSIBLE, TIMEOUT, ERROR }
protected void onChildFailed(Task child, Failure f)             // default: fail self ("child failed: …")
```

- Separate from `isFinished`; `onStop` gets reason `FAILED`.
- Handled **one level up**: `StrategyTask` → penalty + switch; a plain task bubbles up by default and can override
  `onChildFailed` to retry.
- Backwards compatible: old tasks never call `fail`, nothing changes for them; ported when touched.
- Progress checkers stay inside the task but call `fail(NO_PROGRESS)` instead of each having its own reaction.
- Exploring becomes a real, visible strategy with the lowest weight instead of a hidden fallback (2.7).
- Top level: a failed user root → "User task FAILED: <reason>" in chat, flight recorder dump, then the same as
  finished (`onFinish`, idle command) — no endless loop.
- Exceptions in `onTick` → `fail(ERROR)` + dump, so one broken task doesn't end the run. In debug mode they are still
  thrown, so bugs don't get hidden.

### 2.5 Layers

| Layer | Content (examples) |
|---|---|
| L0 Primitives | `slot/ClickSlotTask`, `InteractWithBlockTask` (slimmed), `CustomBaritoneGoalTask` |
| L1 Basic actions | `slot/*`, `movement/GetTo*`, `PickupDroppedItemTask`, mine one block, attack entity |
| L2 Processes | `container/*`, `CraftIn*Task`, `SmeltIn*Task`, `construction/*` |
| L3 Goals | `resources/*`, `SatisfyMiningRequirementTask`, `DefaultGoToDimensionTask` |
| L4 Plans | `BeatMinecraftTask`, `@get` planner (step 4), survival behaviour |

Rule: a task only creates tasks of its **own or a lower** layer (same layer is needed: resources need resources,
planks → logs).

Check (decided, Q5): **source scan in a unit test**, annotation `@TaskLayer(Layer.L2)` as the single source of truth.
- Why source, not class files / ArchUnit: class files exist for one Minecraft version only, the sources contain all
  `//#if` branches; violations are reported as `file:line`; no new dependency, and ArchUnit prefers one package per
  layer, which we don't want to force.
- What the scan detects:
  - `new XxxTask(`;
  - method references `XxxTask::new` (today `DestroyBlockTask::new` 5×, `KillEntityTask::new`, `GetToBlockTask::new`,
    … passed to `DoToClosestBlockTask` etc.);
  - **`TaskCatalogue.*` calls (53 task files) count as creating an L3 task** — the factory returns "get item X";
  - no reflection-based creation exists in the tasks.
- `@TaskLayer` on **every** task class, abstract ones included, not inherited (explicit is easier to read);
  missing annotation fails the test.
- Commands, sources/chains and `Playground` sit above L4 and are not restricted.
- Topic packages (`movement/`, `resources/`, …) stay, they help finding things; packages per layer are at most a later
  option, the annotation decides.
- Two stages: annotate all 179 classes, the test **reports** violations without failing; once the list is empty it
  becomes a **hard failure**, so no new violation can slip in.
- Known gap: a task that receives another task as a constructor parameter isn't seen; the check happens at the caller.

### 2.6 Logging and diagnosis

Problem: the task tree is easy to follow — one path, fixed cascades. With sources, scores, hysteresis and penalties,
*why* the bot did something depends on numbers that change every tick. If a speedrun dies after 40 minutes we need
to see which candidates were on the table and why one of them won.

Today: `Debug.logInternal` → `System.out` ("ALTO CLEF: …"), mixed into the Minecraft log, no tick, no time, no
structure. Every `Task START/STOP` and every debug-state change is a line; nothing about *why* a switch happened.

**Principle: log decisions and their inputs, not ticks.** The decision core (scheduler pick, strategy pick,
hysteresis, penalties) is pure (2.2, 2.4), so if its inputs are logged, a decision can be **replayed in a unit test**
from the log. That's what makes the non-deterministic part debuggable.

Three outputs:

1. **Decision log** (`altoclef/logs/<session>.jsonl`, one JSON object per line, rotated, last N sessions kept).
   Written only when something happens, not every tick:
   - `session_start`: mod + Minecraft version, profile, modified settings (`@modified`), world/server, seed if known.
   - `switch` (scheduler): tick, time, old → new source/task, reason
     (`preempt_emergency | higher_tier | margin | finished | failed | cancelled`), **all candidates with tier/score**,
     blocked-by-commitment if a better candidate was refused.
   - `strategy_switch`: owning task, old → new strategy, all strategy scores, active penalties.
   - `penalty`: strategy, reason (`no_progress | failed`), factor, until when.
   - `task`: start / stop(reason) / failed, with the task path (root → leaf debug strings).
   - `event`: death (cause, position, dimension), damage spikes, dimension change, user command.
   Every line carries `tick`, `t` (ms since session start), `pos`, `dim`, `hp`, `food`.
2. **Flight recorder** (in memory): ring buffer of the last ~30 s of per-tick snapshots — chosen candidate, all
   candidate keys, `BotState` flags, task path, health/food/position. Cheap, never written by default.
   **Dumped** to `altoclef/logs/<session>-<tick>-<trigger>.json` on: death, task failed, thrashing detected,
   unstuck triggered, exception in a task, or a command (`@dump`). The dump is what we look at after a failed run.
3. **Run summary** at task finish / death / `@summary`: time per tier and source, number of switches,
   most-switched pairs, strategies with the most penalties, deaths with cause. Short version in chat, full in the log.

Thrashing detector (replaces the diagnostic side of `BeatMinecraftTask`'s A→B→A check): more than K switches between
the same two candidates within T seconds → warning + flight recorder dump. It doesn't fix anything; it tells us
where the margins are wrong.

Tooling:
- A small script in `scripts/` that reads a `.jsonl` and prints a timeline (and filters by source/task/tick range).
- A test helper that loads a `switch` / `strategy_switch` line and replays it against the scheduler / `StrategyTask`
  core → every bug found in a speedrun can become a regression test.
- In game: `@tasks` shows the current candidate table (same data as the log line), optionally as a HUD overlay.

Settings (`debug.*`): log categories on/off (`scheduler`, `strategy`, `task`, `events`), flight recorder length,
which triggers dump, how many sessions to keep. Default for speedrun testing: everything on except per-task
debug-state lines. Chat stays quiet: only the summary and warnings.

The old `Debug.logInternal` calls stay for now (they go to the normal log); the decision log is a separate channel
so it stays readable. Moving old calls over happens task by task when they are touched.

**Baseline first:** the decision log comes *before* any behaviour change (step 0 below), logging today's chain
decisions. Then speedruns on the old runner give a baseline (deaths, switches, time per source) to
compare the new scheduler against.

### 2.7 Exploration instead of wandering

`TimeoutWanderTask` is one of the main reasons the bot gets stuck in water and never covers distance.

What it does today:
- Calls Baritone's `ExploreProcess.explore(originX, originZ)`. Baritone picks the **uncached chunk closest to the
  origin**, not to the player and not in any direction. Uncached chunks sit at the edge of the render distance in
  every direction, so:
  - an ocean counts as much as land → the bot swims to the next chunk across the water and gets stuck there;
  - targets on opposite sides of the origin have almost the same distance → the bot runs back and forth around the
    origin and never really travels.
- Two different uses share one class: `new TimeoutWanderTask()` / `(true|false)` (27×, infinite distance) = "I don't know where the
  thing is, explore"; `new TimeoutWanderTask(5)` / `(5, true)` / `(100)` (11×) = "get away from this bad spot".
  The infinite version **never finishes** and never reports failure.
- Small bugs: `isAnnoying` returns inside the first loop iteration (only vines + the `instanceof` checks are ever
  tested); the timer block in `onTick` has empty branches; `failCounter` only ends the task if the distance is finite.

Baritone's explore is only useful for **tactical chunk loading** (fill in the area around me once, e.g. after arriving
somewhere). Proposal: split into three tasks, each a normal strategy candidate:

| Task | Purpose | Behaviour |
|---|---|---|
| `MoveAwayTask(radius)` | get away from a bad spot (replaces the 11 finite wanders) | pick a reachable **land** position `radius`…`2×radius` away from loaded chunk data, walk there, finish; fail `NOT_POSSIBLE` if none |
| `TravelExploreTask(purpose)` | find something not yet known (replaces the 27 infinite wanders) | **keep a heading**: choose a direction, set a goal far away (e.g. 300–500 blocks) along it, go straight; re-choose only when blocked or reached. Fails `TIMEOUT` after a budget (distance/time) so the parent can react |
| `LoadChunksAroundTask(radius)` | tactical chunk loading | today's Baritone explore, bounded to a radius around the **player**, finishes when the area is cached |

Choosing a heading for `TravelExploreTask` (scored like a strategy, 2.1):
- candidate directions (e.g. 8–16), each scored from what the client already knows in loaded chunks:
  **water share** along the first ~100 blocks (oceans/rivers strongly penalised), steep terrain / lava (nether),
  and whether that direction was **already visited**;
- optional `purpose` bias from loaded biome data: wood → forest, sand → desert/beach, obsidian/lava → lava pools,
  villages → plains/savanna, …; unknown purpose → just "unvisited land";
- water is a high cost, never a goal: if water must be crossed, take the narrowest crossing (later: boat if available);
- keep the heading unless it gets clearly worse (same relative margin as 2.1) — that's what makes the bot cover
  distance instead of turning around.

Exploration memory lives in `BotState`, **not** in the task: a coarse grid of visited regions per dimension and the
current heading. A strategy switch and back (`task(ctx)` creates a new task, 2.4) then continues in the same direction
instead of starting over. Later this can be persisted together with the "world file" idea
(spawn point, portals, last death point).

Logging (2.6): heading chosen with the candidate scores, re-choose with the reason (`blocked_water`, `blocked_terrain`,
`reached`, `worse`), budget exhausted → failure.

## 3. Mapping today → proposal

| Today | Proposal |
|---|---|
| `MLGBucketFallChain` | source, EMERGENCY (falling) / SURVIVAL (pick water back up) |
| `WorldSurvivalChain` | source, EMERGENCY (lava, fire) / SURVIVAL (rest) |
| `MobDefenseChain` | source (run away, kill, wall: SURVIVAL; fusing creeper close: EMERGENCY) + service (force field, shield) |
| `UnstuckChain` | source, SURVIVAL |
| `FoodChain` | source (collect food, SURVIVAL) + service (eat) |
| `UserTaskChain` | source, NORMAL (IDLE while running the idle command) |
| `PreEquipItemChain`, `PlayerInteractionFixChain`, `DeathMenuChain` | services |
| `ITaskCanForce`, `ITaskRequiresGrounded` | "committed" check in scheduler and `StrategyTask` |
| `ITaskOverridesGrounded` | implied by tier EMERGENCY |
| `TimeoutWanderTask()` / `TimeoutWanderTask(5)` | `TravelExploreTask` / `MoveAwayTask`; Baritone explore only in `LoadChunksAroundTask` (2.7) |
| tasks calling `getFoodChain().shouldStop`, `getMobDefenseChain().setTargetEntity/setForceFieldRange`, tree searches in chains | hints from the active path (2.3a) |
| `BeatMinecraftTask` priority tasks + calculators | `StrategyTask` + strategies; calculators kept, output normalised |
| `changedTaskTimer`, `forcedTaskTimer`, `taskChanges` | shared hysteresis + failure penalty |

## 4. Steps (each compiles on all versions, has tests, behaviour unchanged unless stated)

Order decided (Q6): **strategies before the scheduler**. Strategies are a trial (decision 4) and only act inside one
task, so a failed pilot hurts `CollectObsidianTask`, not the reflexes. The scheduler touches MLG, lava and creepers;
it comes once logging and the speedrun tests are established and the pilot has given first experience with score and
margin. The pure pieces strategies need (score, selector, failure, `onStop(reason)`) are the same the scheduler uses
later, so they are built once. One branch per step (ground rule in `TODO.md`); this branch only holds the document,
is merged to `main` after the review, and all step branches start from `main`.

| # | Step | Branch |
|---|---|---|
| 0 | **Decision log + flight recorder on today's runner** (2.6): `TaskRunner` logs chain switches with all chain priorities, task start/stop, deaths; dumps; run summary. Pure writer/ring buffer with tests | `feature/decision-log` |
| R | **Recipe cleanup** (2.5b, mechanical part, Q7): crafting tasks (`CraftInTableTask`, `CraftInInventoryTask`, `CraftGenericManuallyTask`, `CraftGenericWithRecipeBooksTask`, squashers, `getRecipeTarget` in `BeatMinecraftTask`) work on `recipes.Recipe` + recipe id; removes `LegacyRecipeAdapter`, `GameRecipeLookup`, the legacy part of `RegistryCrafting`, `CraftingRecipeTracker`, `CraftingRecipe` (40 users), `RecipeTarget` (29). No behaviour change; tested with `@recipes check`, recipe tests, in game: table, furnace, bed, stairs. → then **baseline speedruns** (on the cleaned crafting code, so cleanup bugs show up in the baseline) | `feature/recipe-cleanup` |
| 0b | **Exploration** (2.7): `MoveAwayTask`, `TravelExploreTask`, `LoadChunksAroundTask`, exploration memory; replace the 38 `TimeoutWanderTask` uses. Works on today's task tree; compared against the baseline (time in water, distance covered, unstuck count) | `feature/exploration` |
| 1 | **Pure core**: `Score`, selector (relative margin, `minDwell`, penalties), `fail()` / `onChildFailed` (2.4a), `onStop(reason)` replaces `onStop(null)`. Unit tests + replay helper for logged decisions | `feature/strategies` |
| 2 | **`StrategyTask` + pilot `CollectObsidianTask`** → speedruns → keep or revise (decision 4) | ″ |
| 3 | **More ports**: `ResourceTask` cascade as default strategies; crafting: recipe book vs. manual as strategies, `CraftWithMatching*` → one "choose a variant" task (2.4c) incl. the last-resort grids in `Collect*` variant tasks / `CollectBedTask`; `CollectFoodTask` | ″ or follow-up branch |
| 4 | **Scheduler**: core with sources (tiers, emergency preemption, commitment), existing chains as adapters (float priorities mapped onto tiers, today's decisions kept 1:1), side effects out of `getPriority()` into services, `BotState` replaces chain-to-chain calls, hints (2.3a), `@tasks`. In-game test: falling, lava, creeper, food, user task | `feature/scheduler` |
| 5 | **Layers**: `@TaskLayer` on all 179 tasks + scan test (report → hard failure), split the largest violators. Independent, can come in between at any time | `feature/task-layers` |
| 6 | **`BeatMinecraftTask`** gather list on strategies, delete its timers (needs 2 and 4) | `feature/beatminecraft-strategies` |
| — | Later: swarm sources (roadmap step 6); packages per layer only if it turns out useful | — |

## 5. Risks

- Behaviour changes in reflexes are dangerous (death). The scheduler step (4) must keep today's decisions 1:1; changes come after.
- Chain order currently decides ties (Unstuck over Food, User over a creeper at fuse 0). The adapter has to keep that
  explicitly or we decide it differently on purpose.
- `getPriority()` side effects run on *every* chain every tick, even when not chosen; moving them into services must
  keep that "always runs" behaviour (eating while the user task runs).

## 6. Open questions (to go through together)

- ~~Q1 Tiers~~ → decided, see section 7.
- ~~Q2 Inherited priority~~ → decided, see section 7.
- ~~Q3 Score scale~~ → decided, see section 7.
- ~~Q4 Failure~~ → decided, see section 7.
- ~~Q5 Layer check~~ → decided, see section 7.
- ~~Q6 Order of steps~~ → decided, see section 7.
- ~~Q7 Recipe adapters~~ → decided, see section 7.
- ~~Q8 Logging~~ → decided, see section 7.
- ~~Q9 Exploration timing~~ → decided, see section 7.

## 7. Decisions (2026-11-28)

1. **Tiers (Q1)**: EMERGENCY > SURVIVAL > NORMAL > IDLE, assignment as in 2.1. Fixed rank inside EMERGENCY
   (fall > lava > fire > creeper), score in the other tiers. Unstuck and post-emergency cleanup are SURVIVAL
   with a low score. The user task is never raised above SURVIVAL; sources get `enabled` + `weight` settings instead.
   To be checked in speedruns.
2. **Logging**: decisions are logged with their inputs so they can be replayed in tests (2.6); the log comes first
   (step 0) to get a baseline on the old runner.
3. **Log format and flight recorder (Q8)**:
   - **JSON Lines**, written with Jackson (already a shaded dependency for the YAML settings, so nothing new).
     Typed values are needed for replay; `jq` and the `scripts/` timeline cover readability. Every line also gets a
     short human-readable `msg` field.
   - Writing is buffered on a background thread, so the game tick never waits for disk; flushed on death,
     world leave and shutdown. Rough size: ~0.5 KB per decision line, about 1–2 MB per hour-long speedrun.
     Keep the last 10 sessions (`debug.keepSessions`).
   - **Flight recorder: 30 s (600 ticks) default**, configurable. Older history is in the decision log anyway;
     the recorder is for "what exactly happened in the last seconds". ~1 KB per snapshot → under 1 MB in memory.
   - **Dump triggers on by default**: death, task failed, thrashing, exception in a task, `@dump`.
     **Off by default**: unstuck (fires too often, counted in the run summary instead).
     Rate limit: at most one dump per trigger type per minute, at most 20 dumps per session.
4. **Strategies as a trial**: implemented with the selector above, first on one pilot (`CollectObsidianTask`),
   checked in speedruns with the decision log. Kept and rolled out if the pilot shows no flipping and no endless
   retries; otherwise revised before porting more tasks.
5. **No inherited priority (Q2)**: priority only on sources and strategies. Tasks influence other sources and
   services through hints from the active path (2.3a), which disappear when the task leaves the path and can never
   touch EMERGENCY. Commitment stays `ITaskCanForce` and is also checked by the scheduler.
6. **Score (Q3)**: `score = weight × need × proximity × penalty`, factors 0..1, weight ≥ 0 with code defaults and
   settings overrides; not applicable = empty; relative switch margin + `minDwell`; factors logged separately (2.1).
7. **Failure signal (Q4)**: `fail(reason, detail)` / `isFailed()` on `Task`, handled one level up via `onChildFailed`
   (default: bubble up; `StrategyTask`: penalty). Old tasks unchanged until ported (2.4a).
8. **Exploration (Q9)**: `TimeoutWanderTask` is split as in 2.7 on its own branch `feature/exploration`, right after
   step 0, so the effect can be measured against the baseline.
9. **Layer check (Q5)**: source scan unit test (`new`, `::new`, `TaskCatalogue.*` = L3), `@TaskLayer` on every task
   class as the single source of truth, topic packages stay; report first, hard failure once clean (2.5).
10. **Order of steps (Q6)**: strategies before the scheduler; order and branches as in section 4. This document's
    branch is merged to `main` after the review; step branches start from `main`.
11. **Recipe adapters (Q7)**: 2.5b is split. The mechanical part (crafting tasks on `recipes.Recipe`, old types and
    adapters removed) is step R on `feature/recipe-cleanup`, after step 0 and before the baseline runs. Variant tasks
    (2.4c) and recipe book vs. manual go with the strategies in step 3.

All open questions are settled; the draft is ready for implementation, starting with step 0.
