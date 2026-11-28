# TODO

## Refactor roadmap

Goal: every task (not only `BeatMinecraftTask`) plans its own prerequisites.
`@get diamond` should figure out on its own that it first needs an iron pickaxe,
maybe armor for combat, etc.

Ground rules:
- Keep the multiversion setup (1.16.5 – 1.21.1, preprocessor). Version differences
  go behind adapters/parsers in `multiversion/`; the core works on one unified format.
- The core keeps using Minecraft classes (they contain a lot of useful logic).
  Only what differs between versions moves into `multiversion/` (version-dependent
  wrappers / `//#if` blocks), no separate Minecraft-free core module.
- One feature branch per step below.
- No unit tests exist today. Each step should add tests for the parts that
  run without Minecraft (parsing, planning, validation).

Suggested order: 0 → 1 → 2 → 3+5 → 4 → 7 → 6.

Problems we ran into during development are recorded in [`DEVLOG.md`](DEVLOG.md).

### 0. Test setup
- [x] Add JUnit to the build (main project + version subprojects).
- [x] First characterization tests: `Task` lifecycle, BeatMinecraft priority calculators, `StringReader`.
- [x] Preprocessor plugin as git submodule (JitPack no longer serves `c2041a3`).
- [x] Version filter `-Paltoclef.versions=...` and memory notes (see `develop.md`).
- [x] CI: submodules, JDK 8 toolchain, actions v4.
- [ ] Keep logic testable without a running client (pure data classes, no `AltoClef.getInstance()` inside them).

### 1. Settings system (YAML, profiles, limits)
Before: `Settings.java` (Jackson POJO, `altoclef_settings.json`), plus
`configs/beat_minecraft.json`, `configs/butler.json`, `configs/food_chain_settings.json`
and `configs/mlg_clutch_settings.json`. No validation, no min/max.
Now (package `adris.altoclef.settings`, branch `feature/settings`):
- [x] Typed `Setting<T>`: key, default, min, max, description, custom check.
- [x] Central `SettingsManager`; parts register their settings with a key prefix
      (`core.`, `beatMinecraft.`, `butler.`, `foodChain.`, `mlgClutch.`). Late registration
      (e.g. by tasks) gets the profile value immediately.
- [x] YAML via `jackson-dataformat-yaml` (added to the shadow config).
- [x] Profiles in `altoclef/profiles/<name>.yml` (only values that differ, `extends:`),
      active profile in `altoclef/profiles.yml`, generated `settings_reference.yml`.
- [x] Above max -> max, below min -> min (warning); wrong type / failed check -> default (warning).
- [x] Unknown keys in a profile are reported (typos).
- [x] In-game commands `@setting [key|group] [value|reset]` and `@profile [name | new <name>]`,
      tab completion, one formatted chat message per listing, readable error texts.
- [x] Baritone-style commands: `@set [name [value]]` (alias `@setting`), `@set list [page|text]`,
      `@modified [page]`, `@reset <name|all>`, `@toggle <name>`; short names (`autoEat` for
      `core.autoEat`, any case, full key required only if ambiguous); paged listings;
      context-aware tab completion for the second argument. Tested in-game on 1.21.1.
- [x] Migrated all 5 JSON configs (88 settings); one-time import of old JSON values into `default.yml`.
- [x] Removed the old JSON config code (`ConfigHelper` only keeps the butler list files).
- [x] `core.showStackTracesInChat` (stack traces of errors in chat only when enabled).
- [x] Unit tests (Setting, SettingsManager, ProfileStore, SettingsEditor); compiles and tests green
      on all 12 versions; tested in-game on 1.21.1.
- [x] Warnings from loading at startup (no world open) are shown once in chat, as one message,
      on the first tick in a world (`StartupWarnings`; respects `core.hideAllWarningLogs`).
- [x] Butler whitelist/blacklist stay separate `.txt` files for now: they are personal and must not
      end up in profiles that get shared.
- [x] Mod jar build checked (1.21.1 and 1.16.5): Jackson, jackson-dataformat-yaml and SnakeYAML are
      packed; 1.16.5 classes downgraded to Java 8 (class version 52); profiles load/save from the
      built jars on Java 21 and Java 8 (standalone smoke test, not in a launcher).
- [ ] Jackson/SnakeYAML are shaded without relocation: may clash with other mods that bundle
      a different version. Relocate (`shadowJar { relocate ... }`) if that ever shows up.
- [ ] Later: GUI (screen API differs per version -> needs a multiversion wrapper); `SettingsEditor` is the backend.
- [ ] Make priority constants (see 3) configurable as settings.

### 2. Unified recipe format
Today: `util/CraftingRecipe` is a flat `ItemTarget[4|9]`; `shapeless` is never set.
About 270 hand-written recipes in `TaskCatalogue` (slot strings).
`trackers/CraftingRecipeTracker` imports game recipes but:
fills slots linearly and ignores recipe width (narrow shaped recipes end up wrong),
keeps only `items[0]` per ingredient (FIXME), skips special recipes.
`JankCraftingRecipeMapping` rebuilds its map on every call.
"Same material" constraints live in `CraftWith*Matching*Task` special classes.
Design and decisions: [`docs/recipe-model.md`](docs/recipe-model.md) (branch `feature/recipes`).
Scope of this step: crafting + smelting (furnace, smoker, blast furnace, campfire).
- [x] 2.1 Own recipe model in `adris.altoclef.recipes` (Minecraft-free, item ids as strings):
      `Recipe`, `RecipeShape` (shaped with width/height + `toGrid`, shapeless, cooking), `Ingredient`
      (alternatives, tag label), `Station`, `RecipeRegistry` (game recipes win, fallback otherwise,
      `choose` replaces the "must match" tasks), `VanillaRecipeParser` (vanilla JSON, old and new
      spellings), `TagResolver` (game tags / local tag file). 20 unit tests.
- [x] 2.2 `GameRecipeImporter` (`multiversion/recipemanager/`, all 12 versions via preprocessor remapping),
      `RecipeRegistryTracker` (fallback at start, game recipes on world join, cleared on leave),
      792 fallback recipes + 21 tags in `assets/altoclef/recipes/` (generated from vanilla 1.21.1 by
      `scripts/gen_fallback_recipes.py`), `@recipes [item|compare]`. In-game 1.21.1: 999 imported,
      291 skipped; compare found 9 wrong hand-written recipes (see DEVLOG).
- [x] 2.3 `CraftingRecipeTracker` now built from the registry via `util/LegacyRecipeAdapter` (real shapes,
      all items per slot, fallback recipes); recipe book uses `util/GameRecipeLookup` (registry match →
      recipe by id) → `JankCraftingRecipeMapping` removed; `CraftInInventoryTask` falls back to manual
      crafting when no game recipe is found; `CraftingHelper` copies stacks. Tested in-game 1.21.1
      (crafting table, wooden pickaxe, sticks, slabs, stairs with and without recipe book).
      Also fixed three old bugs in manual crafting (`CraftGenericManuallyTask`): grid cleared by the
      parent resource task, per-slot amount ignored items already owned, wrong "material left" check.
- [x] 2.4a `TaskCatalogue` crafted entries (`shapedRecipe*` helpers, wooden buttons) take their recipe from
      the registry (`util/RegistryCrafting`): `RecipeRegistry.choose` with a "can collect all ingredients"
      filter (`recipes/IngredientResolver`: ingredient → catalogue name or single item) and main-recipe
      tie-break. `@recipes check`: 284/284 entries have a usable recipe (suspicious stew via a hand-written
      fallback recipe). Fixes the 9 wrong hand-written recipes and the button count (was always 1).
- [x] 2.4b Smelting entries (`smelt(...)`) from the registry (`RegistryCrafting.smeltTask`, furnace);
      concrete wood/colour variants (`oak_stairs`, `red_bed`, ...) crafted from the registry; the generic
      "any variant" entries keep their `CraftWithMatching*` strategy but take recipe + same-material mask from
      the registry (`RegistryCrafting.matchingRecipe`, old grid only as last resort). `choose` ties are now
      alphabetical (game sends recipes in random order). Tested in-game 1.21.1: `@recipes check` 311/311,
      glass, charcoal, wooden stairs, birch door, bed (after the bed-mining fix below).
- [ ] 2.4c Replace the `CraftWithMatching*` tasks with one "choose a variant" task (pick the variant output via
      `RecipeRegistry.choose` across all variants; collect-any-then-craft-majority strategy rebuilt on top);
      fits the shopping list planner (step 4).
- [x] 2.5a Catalogue cleanup: all recipe entries are one line, `crafted(name, item)` / `smelted(name, item)`;
      the old `shapedRecipe*`/`smelt` helpers with hand-written grids are gone (`tools`/`armor` too).
- [x] 2.5b Remove the backwards adapters between the new recipe model and the old crafting code
      (step R, branch `feature/recipe-cleanup`): crafting tasks work on `util/CraftTarget` (registry `Recipe` +
      count + grid of `ItemTarget`s, recipe book via the recipe id). Removed `LegacyRecipeAdapter`,
      `GameRecipeLookup`, `CraftingRecipeTracker`, `CraftingRecipe`, `RecipeTarget`, `CraftingRecipeVer`; the
      hand-written grids in ~15 `Collect*Task`s and the last-resort grids of the variant tasks take their recipe
      from the registry (`RegistryCrafting.craftTaskUsing`); `CraftingHelper.canCraftItemNow` runs on the registry
      (`recipes/CraftableFromInventory`, tested). The variant tasks craft the chosen variant's own recipe.
  - [ ] Left for step 3: the `CraftWithMatching*` tasks themselves (2.4c), recipe book vs. manual as strategies.
- [ ] Later: smithing, stonecutter; reading data packs (1.21.2+ clients get no recipes from the server).
- [ ] Later: prefer recipe variants by their result's usefulness, not only by what is in the inventory
      (e.g. suspicious stew: the flower decides the effect — regeneration/saturation instead of poison or
      blindness; dyes/colours, wood types). Needs a value per variant, ties in with item values (step 7).

### 3. Task architecture: layers, strategies, priorities (together with 5)
Design draft: [`docs/task-architecture.md`](docs/task-architecture.md) (branch `feature/task-architecture`).
Removing the recipe backwards adapters (2.5b) is split: the mechanical part (crafting/smelting tasks work directly on
`recipes.Recipe`, grid via `RecipeShape.Shaped.toGrid`, recipe id for the recipe book) comes first on its own branch
`feature/recipe-cleanup`; the variant tasks (2.4c) and recipe book vs. manual come with the strategies.

#### 3a. Task layers
Today: ~180 task classes sorted by topic (`slot/`, `movement/`, `container/`,
`resources/`, …), not by abstraction level. Many tasks hard-code their whole
procedure instead of composing smaller tasks, e.g. `CollectFoodTask` (~490 lines
of if-cascades + scoring), `CraftInTableTask`.
- [ ] Define layers. A task may only use tasks of its own or a lower layer:
  - **L0 Primitives:** slot clicks, look, place/break one block, interact,
        raw Baritone goals (`slot/ClickSlotTask`, `InteractWithBlockTask`, `CustomBaritoneGoalTask`).
  - **L1 Basic actions:** move item between slots, ensure free slot/cursor,
        go to block/entity/position, pick up dropped item, mine one block, attack entity,
        open/close container (`slot/*`, `movement/GetTo*`, `PickupDroppedItemTask`).
  - **L2 Processes:** craft (inventory/table), smelt, smith, trade, store/loot
        container, place a small structure (`container/*`, `CraftIn*Task`, `construction/*`).
  - **L3 Goals:** obtain item X, satisfy mining requirement, get gear,
        go to dimension (`resources/*`, `SatisfyMiningRequirementTask`, `DefaultGoToDimensionTask`).
  - **L4 Plans:** `@get` planner (shopping list, see 4), `BeatMinecraftTask`, survival behaviour.
- [ ] Mark the layer explicitly with `@TaskLayer` (topic packages stay), checked by a source-scan unit test
      (report first, hard failure once clean; see `docs/task-architecture.md` 2.5).
- [ ] Tasks stay small: one responsibility, composed of lower-layer tasks;
      no copy-pasted inventory/movement logic inside higher tasks.
- [ ] Replace hard-coded values in tasks with parameters / settings (see 1).
- [ ] Move existing tasks into the layers step by step; split the big ones.

#### 3b. Strategies (alternative approaches)
Today: alternative approaches are hard-coded if-cascades inside one task, e.g.
`CollectObsidianTask`: mine existing obsidian → trade with piglins (in nether) →
cast with lava + water bucket; `CraftInTableTask` / `CraftInInventoryTask`:
recipe book vs. manual crafting; `CollectFoodTask`: crops / hay / animals / berries / pickup.
Switching only happens at fixed points in that cascade.
- [ ] A task (usually L3) declares a list of **strategies**, each one a normal sub task
      ("try this; if it fails or something better shows up, try that").
- [ ] Every strategy reports applicability + cost/priority from the current
      surroundings (nearby blocks/entities, inventory, dimension, danger).
- [ ] All strategies are registered as candidate sub tasks; the parent re-evaluates
      them continuously and runs the best one, so the bot can switch strategy
      dynamically when the environment changes. (Only one runs at a time, since
      there is one player.)
- [ ] Failure / no progress (`progresscheck/`) penalises a strategy for a while
      instead of retrying it forever.
- [ ] Hysteresis so strategies don't flip every tick (same mechanism as 3c).

#### 3c. Priorities
Today: `Task` has one `sub` and no priority. Priorities exist only on chains and
inside `BeatMinecraftTask` (`PriorityTask` wrappers + `*PriorityCalculator`,
hard-coded numbers 100–25000). Thrashing is patched with `changedTaskTimer`,
`forcedTaskTimer`, A→B→A detection (`taskChanges`) and pickup special cases.
- [ ] ~~Priority as a first-class part of `Task`; children inherit the parent's priority.~~
      Decided otherwise: priority only on sources and strategies; tasks pass **hints** along the active path
      (see `docs/task-architecture.md` 2.3a).
- [ ] Per-task priority calculators that depend on progress
      (for `ResourceTask`: have / needed; other tasks define their own measure).
- [ ] Built-in hysteresis / commitment instead of the timer hacks
      (only switch when the new task is clearly higher).
- [ ] Port `BeatMinecraftTask`'s gather list onto the new model and shrink the class.

#### 3-0. Decision log (step 0 of `docs/task-architecture.md`, branch `feature/decision-log`)
- [x] Decision log `altoclef/logs/<session>.jsonl`, flight recorder dumps (death, thrashing, `@dump`),
      run summary (`@summary`, chat on task finish / death), thrash detector, task loop detector, `debug.*` settings,
      `scripts/decision_timeline.py`.
- [ ] Baseline speedruns on the old runner (after the recipe cleanup, step R).
- [ ] Exception in a task as dump trigger (comes with the failure signal, step 1).

#### 3d. Exploration (own branch, right after the logging step)
Today `TimeoutWanderTask` (38 uses) runs Baritone explore around a fixed origin: swims across oceans, runs back and
forth, never covers distance, the infinite variant never ends.
- [ ] `MoveAwayTask` (get away from a bad spot, land only), `TravelExploreTask` (keep a heading, avoid water,
      purpose/biome bias, budget → failure), `LoadChunksAroundTask` (tactical chunk loading only).
- [ ] Exploration memory (visited regions, current heading) in shared bot state; later persisted with the world file.
- Design: `docs/task-architecture.md` 2.7.

### 4. Resource shopping list
Today: on-demand recursion. `CollectRecipeCataloguedResourcesTask` recomputes one
recipe's needs every tick (TODO cache) and returns the first missing item.
Squashing (`CataloguedResourceTask`, `CraftSquasher`, `SmithingSquasher`) only
merges top-level crafting/smithing tasks, never mining. Same items can be
gathered in separate trips.
- [ ] Resolve the full dependency tree from the recipe graph (needs 2),
      including tool requirements (`MiningRequirement`) and dimensions.
- [ ] Aggregate raw material demand, subtract inventory.
- [ ] Plan the order by mining tier, dimension and distance; pick up nearby
      needed resources opportunistically (e.g. coal while mining iron).
- [ ] Greedy choice between alternative recipes (avoid planner explosion).
- [ ] `@get <item>` uses the shopping list → plans prerequisites automatically
      (tools, maybe armor for combat-heavy targets).

### 5. Task runner / scheduler (together with 3)
Today: `TaskRunner` keeps a flat list of `TaskChain`s that register themselves
in their constructor; each tick the highest `getPriority()` runs, the rest are interrupted.
- [ ] Turn the runner into a priority scheduler that accepts tasks from several sources.
- [ ] Same priority model as 3, so chains and tasks are comparable.
- [ ] Extension point for external task sources (swarm), no swarm logic yet.
- [ ] **Emergency / reflex tier** above all normal priorities: water bucket clutch
      (`MLGBucketFallChain`), running from creepers (`MobDefenseChain`), escaping lava /
      putting out fire (`WorldSurvivalChain`), etc.
      Today these are magic numbers on the same scale as normal work
      (MLG / lava / fire 100, creeper `50 + fuse * 50`, other defense 60–80,
      unstuck 55, food 55, user tasks 50), so a high user priority could collide.
  - [ ] Separate priority tiers (e.g. EMERGENCY > SURVIVAL > USER/PLAN > IDLE);
        a lower tier can never outrank a higher one, priorities only compete within a tier.
  - [ ] Emergencies preempt immediately: no hysteresis, no commitment, evaluated every tick.
  - [ ] Interrupted tasks resume cleanly afterwards (state kept, `onStop`/`reset` contract).
  - [ ] Emergency tasks themselves can't be interrupted until done (today `ITaskCanForce`),
        except by another emergency.
  - [ ] Thresholds/values configurable (see 1).
- [ ] **Priority scale for hundreds of tasks.** A bigger number range alone doesn't
      prevent conflicts (`double` already has plenty); the conflicts come from
      hand-picked absolute numbers. Instead:
  - [ ] Priority = composite key compared lexicographically:
        `(tier, inherited parent priority, local score, tie-breaker)`.
  - [ ] Local score normalised (e.g. 0..1) from the task's calculator × a configurable weight,
        so tasks never have to know each other's numbers.
  - [ ] Siblings only compete with siblings; across branches the inherited part decides (see 3c).
  - [ ] Deterministic tie-breaker (e.g. currently running task wins, then stable id)
        so equal priorities don't cause flip-flopping.
  - [ ] Debug view / log of the top N candidates with their key parts, to find conflicts.

### 6. Swarm (later)
Each bot is its own client, so the blocker is communication, not the runner.
- [ ] Communication layer (today only chat/whisper-based `butler`).
- [ ] Shared state: claimed blocks/areas, split shopping list.
- [ ] Swarm tasks are slotted into the scheduler by priority.

### 7. Smart inventory management
Today: hard-coded lists. `Settings.throwawayItems` + `shouldThrowawayUnusedItems`,
`BotBehaviour.protectedItems`, `beatgame/UselessItems` (~100 hard-coded items).
`StorageHelper.getGarbageSlot` throws away the first non-block throwaway item,
otherwise the smallest throwaway block stack while keeping
`reservedBuildingBlockCount` blocks. No notion of item value, no compression.
- [ ] Item values live in config files (item value table, same config system as 1).
      Focus is reading/editing that file, not computing values; files should be
      exchangeable between users and shareable in a swarm (6).
- [ ] On world join: only add items missing from the table (new version / mods)
      with a placeholder value, and notify the user (chat message) to fill them in.
- [ ] Values are maintained by hand; later a GUI to edit the table (shares the GUI work from 1).
- [ ] Optional / low priority: generator that suggests values, derived recursively from
      crafting recipes (2) plus a bias per item/category (tools, armor etc. are worth
      more than their ingredients). Earlier implementation exists → reuse once found.
- [ ] Value of a stack depends on context: items on the current shopping list (4)
      or needed by running tasks count as protected / worth more.
- [ ] When the inventory is full:
  1. compress first (diamonds, iron, gold, emeralds, redstone, … into blocks),
     using reversible recipes from the recipe graph (2) instead of a hard-coded list;
  2. then drop the stack with the lowest value among non-protected slots.
- [ ] Idea: use storage to keep more – shulker boxes, barrels, ender chest
      (store low-priority valuables instead of dropping them).
- [ ] Replace `throwawayItems` / `UselessItems` / `getGarbageSlot` with this system.
- [ ] Build as L2 tasks (compress, drop, store) on top of the slot tasks (see 3a).

### 8. World memory (per world / server)
Today: knowledge about a world lives in task fields and is lost when the game is closed, e.g. in
`BeatMinecraftTask` (portal and stronghold positions, bed spawn) and the home base setting.
- [ ] One file per world/server (`altoclef/worlds/<id>.yml`, same YAML infrastructure as step 1); id from the
      singleplayer save name or the server address (+ dimension where needed).
- [ ] Stored: world spawn, own spawn point / bed, nether + end portal locations (both sides), stronghold /
      end portal room, last death point (+ what was lost), home base, other places worth remembering.
- [ ] Speedrun state (`BeatMinecraftTask` variables) read from / written to it, so a run can continue after
      a restart.
- [ ] Loaded on world join, saved on change and on leave; in-game command to show / edit / clear entries.
- [ ] Not part of the shareable settings profiles (personal / per world, like the butler lists).

## Misc
- Water: getting to land is weak. Placing a crafting table/furnace no longer targets water spots, switches to
  `GetOutOfWaterTask` when no dry spot is reachable, and an existing container is preferred over continued
  placing (2026-11-28). But swimming to land itself (Baritone in open water, `GetOutOfWaterTask` vs.
  `UnstuckChain` both escaping) still did not reach land within ~15 s in an ocean test.
  Idea for later: use Tungsten as a second pathfinder (e.g. for water/swimming) next to Baritone.
- Implement configs. (→ roadmap 1)
- Allow any bed color when crafting beds from wool. (→ roadmap 2, "must match" groups)
- Add cherry blossom wood as a valid wood type or something? It doesn't seem to work properly atm.
- Maybe add some sort of system that finds and uses seed of the current world?


## Common death causes
- Improve escaping from lava.
- Prevent from looking endermen in the eyes.
- Do not hit pigmen in the nether (or implement a special behaviour when do).
- Maybe avoid bastions somehow?
