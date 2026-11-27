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
      context-aware tab completion for the second argument. Not tested in-game yet.
- [x] Migrated all 5 JSON configs (88 settings); one-time import of old JSON values into `default.yml`.
- [x] Removed the old JSON config code (`ConfigHelper` only keeps the butler list files).
- [x] `core.showStackTracesInChat` (stack traces of errors in chat only when enabled).
- [x] Unit tests (Setting, SettingsManager, ProfileStore, SettingsEditor); compiles and tests green
      on all 12 versions; tested in-game on 1.21.1.
- [ ] Show warnings from startup once in chat when joining a world (today only in the log).
- [ ] Butler whitelist/blacklist (`.txt` list files) into the settings system?
- [ ] Mod jar build (`build`: shadow + downgrade) not checked yet: is the YAML library packed correctly?
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
- [ ] Own recipe model modeled after vanilla: shaped (pattern + key) / shapeless,
      ingredient = item | tag | catalogue name, output count, station
      (inventory, crafting table, furnace/smoker/blast furnace, smithing table).
- [ ] "Must match" groups (bed wool color, planks of one wood type).
- [ ] Per-version parsers in `multiversion/` that turn the game's `RecipeManager`
      into the unified format (recipe API changes across 1.16.5 – 1.21.x).
- [ ] Build the recipe registry on world join (recipes are server-side);
      hand-written recipes only as overrides/fallback.
- [ ] Replace `CraftingRecipe` / `RecipeTarget` usages, drop `JankCraftingRecipeMapping`.
- [ ] Later (once the format is stable): load recipes dynamically / from data files.

### 3. Task architecture: layers, strategies, priorities (together with 5)

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
- [ ] Mark the layer explicitly (package per layer and/or annotation/interface), check it in tests.
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
- [ ] Priority as a first-class part of `Task`.
- [ ] Children inherit the parent's priority; effective priority =
      parent priority combined with the child's own local modifier.
- [ ] Per-task priority calculators that depend on progress
      (for `ResourceTask`: have / needed; other tasks define their own measure).
- [ ] Built-in hysteresis / commitment instead of the timer hacks
      (only switch when the new task is clearly higher).
- [ ] Port `BeatMinecraftTask`'s gather list onto the new model and shrink the class.

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

## Misc
- Implement configs. (→ roadmap 1)
- Allow any bed color when crafting beds from wool. (→ roadmap 2, "must match" groups)
- Add cherry blossom wood as a valid wood type or something? It doesn't seem to work properly atm.
- Maybe add some sort of system that finds and uses seed of the current world?


## Common death causes
- Improve escaping from lava.
- Prevent from looking endermen in the eyes.
- Do not hit pigmen in the nether (or implement a special behaviour when do).
- Maybe avoid bastions somehow?
