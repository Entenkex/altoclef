# Unified recipe model (roadmap step 2) — draft

Status: **design agreed**; steps 1 and 2 implemented. Branch `feature/recipes`.

## 1. What exists today

| Part | What it does | Problems |
|---|---|---|
| `util/CraftingRecipe` | Flat `ItemTarget[4 or 9]`, width/height derived from the array size, `outputCount`. | `shapeless` is never set. No smelting/smithing. A 1x2 recipe (sticks) can only be written as a full 2x2/3x3 grid. "Must match" is commented out. |
| `util/RecipeTarget` | Output item + wanted count + recipe. | — |
| `TaskCatalogue` | ~130 hand-written crafting recipes (`shapedRecipe2x2/3x3/...Block/Slab/Stairs/Wall`), ~25 `smelt(...)`, a few `smith(...)`. Ingredients are catalogue names (`"planks"`, `"stick"`). | Duplicates what the game already knows; easy to get wrong; new versions need manual work. |
| `trackers/CraftingRecipeTracker` | Imports all crafting recipes from the client's `RecipeManager` once in game. | Fills slots linearly and **ignores the recipe width**: a 1x2 or 2x3 recipe lands in the wrong slots. Keeps only `items[0]` of each ingredient (FIXME) — "any planks" becomes "oak planks only". Skips special recipes. Only used by `CraftingHelper` and one `BeatMinecraftTask` line. |
| `util/JankCraftingRecipeMapping` | Finds the game's recipe id for a `CraftingRecipe` (needed to click the recipe book). | Rebuilds its whole map **on every call**; matches by "ingredients are a subset", which can pick the wrong recipe. |
| `CraftWithMatching{Materials,Planks,Wool,StrippedLogs}Task` + `sameMask` | "These slots must be the same item" (bed wool, stairs planks). | Special task classes per material; mask is a `boolean[9]` tied to the slot layout. |
| `CraftGenericManuallyTask` | Puts items into the grid slot by slot (`recipe.getSlot(i)` → `CraftingTableSlot.getInputSlot(i, isBig)`). | Relies on the flat 4/9 layout. |
| `CraftingHelper.canCraftItemNow` | Recursive "can I craft this from my inventory?". | Copies the list but not the `ItemStack`s, so "trying" a branch changes the counts of the real list. |

47 files use `CraftingRecipe` / `RecipeTarget`, most of them `Collect*Task`s that build one hand-written recipe.

### Observation: "must match" is mostly a non-problem in vanilla
In vanilla, every coloured bed has its **own** recipe (`white_bed` needs white wool, planks may be mixed),
and every wooden stair/slab/door has its own recipe with that wood's planks.
So "must match" only appears because the catalogue asks for *any* bed / *any* stairs.
With real recipes per output item this becomes **choosing one recipe out of several** (pick the colour/wood
we have most of), not a constraint inside a recipe. Mixed planks for sticks, crafting tables etc. are allowed
by the game (`#planks` tag), so those need nothing special either.

## 2. Proposed model

Package `adris.altoclef.recipes` (core). Minecraft-free so it can be unit tested; items are identified by
their id string (`"minecraft:oak_planks"`), converted to `Item` at the task boundary (same idea as
`ItemIdCodec` in the settings).

```java
record Recipe(
    String id,                 // "minecraft:oak_stairs", or "altoclef:..." for fallback recipes
    Station station,           // where it can be made
    RecipeShape shape,         // what goes in
    ItemAmount output,         // what comes out ("minecraft:oak_stairs", 4)
    Source source)             // GAME (imported) | FALLBACK (local data file)

enum Station { CRAFTING /*2x2 or 3x3, see Shaped.fitsInventory()*/, FURNACE, SMOKER, BLAST_FURNACE,
               CAMPFIRE, SMITHING_TABLE, STONECUTTER }

sealed interface RecipeShape {
    record Shaped(int width, int height, List<Ingredient> cells)   // row-major, width*height, EMPTY allowed
    record Shapeless(List<Ingredient> ingredients)
    record Cooking(Ingredient input, int cookTicks)
    record Smithing(Ingredient template /*EMPTY before 1.20*/, Ingredient base, Ingredient addition)
    record Stonecutting(Ingredient input)
}

record Ingredient(Set<String> items, String label)  // alternatives; label e.g. "#planks" for display/logs
record ItemAmount(String item, int count)
```

Helpers on the model (all pure, all tested):
- `Shaped.fitsInventory()` → width ≤ 2 and height ≤ 2.
- `Shaped.toGrid(gridSize)` → the cells placed top-left into a 2x2 or 3x3 grid (fixes the width bug;
  replaces `getSlot(i)` for `CraftGenericManuallyTask`).
- `Recipe.inputs()` → ingredient → count per craft (for the shopping list, step 4).
- `crafts(n)` → how many crafts are needed for `n` outputs.

`RecipeRegistry`:
- `byOutput(item)`, `byId(id)`, `all()`.
- Built **once on world join** (recipes come from the server), cleared on leave.
- Fallback recipes from local data files (section 4) for outputs the game did not provide.
- `choose(output, inventory)` → which of several recipes to use (most ingredients already present).
  Replaces the `CraftWithMatching*` / `sameMask` logic.

## 3. Version differences (importer in `multiversion/recipes/`)

| Topic | Versions | Note |
|---|---|---|
| Recipe id | ≤1.20.1: `recipe.getId()`; ≥1.20.2: `RecipeEntry(id, value)` | already wrapped in `WrappedRecipeEntry` |
| Output stack | ≤1.19.3 `getOutput()`; 1.19.4–1.20.4 `getResult(DynamicRegistryManager)`; ≥1.20.5 `getResult(WrapperLookup)` | partly in `RecipeVer` |
| Shaped size | `ShapedRecipe.getWidth()/getHeight()` in all supported versions | ingredients are row-major `width*height` |
| Ingredient items | `Ingredient.getMatchingStacks()` in all supported versions | the tag name itself is not public → label from matching known tags, optional |
| Cooking time | `AbstractCookingRecipe.getCookTime()` / `getCookingTime()` | name differs, check per version |
| Smithing | ≤1.19.x `SmithingRecipe` (base + addition); ≥1.20 `SmithingTransformRecipe` (template + base + addition); fields private | needs accessor mixins |
| Special recipes | all | firework, armor dye, banner, map copy, repair … skipped (not needed by the bot) |
| Recipe sync to client | ≤1.21.1: the server sends all recipes | **1.21.2+ no longer sends them** → for future versions the overrides/data files become the main source |

## 3b. How other bot systems do it

| System | Item identity | Alternatives ("any planks") | Smelting |
|---|---|---|---|
| minecraft-data / Mineflayer (`prismarine-recipe`) | numeric ids, different per Minecraft version | not modelled; `inShape` is a 2D array of single ids | not in the recipe file |
| Mindcraft (LLM bot on Mineflayer) | names (`"oak_planks"`) outward, numeric ids inside | not modelled; prefers oak and tells the model "any wood works" | hard-coded helper lists |
| Vanilla data packs | `minecraft:` ids, tags as `#minecraft:planks` | tags per slot | own recipe types |

Takeaways: nobody uses raw version-specific numeric ids outward; names/ids are the stable key.
The two bot systems lose "any planks" and hard-code smelting — exactly the weaknesses we want to avoid.
→ **Decision: id strings** (`minecraft:oak_planks`) with an explicit set of alternatives per slot, like vanilla.

## 4. Local fallback recipes (decision)

The bot must be able to fall back to local recipes for all important basic recipes
(tools, armor, crafting table, furnace, chest, sticks, planks, torches, bucket, beds, boats,
ingots/blocks, glass, stone, charcoal, …).

- Stored as **data files in the mod resources** (`assets/altoclef/recipes/*.json`) in the **vanilla
  data pack format** (`pattern` + `key`, `#minecraft:planks` for tags, `minecraft:smelting`).
  The same parser can later read real data packs / vanilla jars for 1.21.2+, where the client no
  longer receives recipes.
- Tags (`#minecraft:planks`) are resolved from the game's item tags at world join; a small local tag
  list is the fallback for the tags the fallback recipes use.
- Order: imported game recipes first; a fallback recipe is only used when the game has no recipe for
  that output (or import failed). Every fallback use is logged once.
- The ~150 hand-written `TaskCatalogue` grids are converted once into these files (and checked against
  the imported ones, see step 2 below), then deleted from the code.

## 5. Steps (each one compiles on all 12 versions and has tests)

1. **Model + registry + fallback parser**, pure Java, unit tests (shapes, grid placement incl. 1x2/2x3,
   shapeless, counts, `choose`, parsing the vanilla JSON format, tags). No game code touched.
2. **Importer** per version → `RecipeRegistry` on world join. Debug command (e.g. `@recipes <item>`)
   to show what was imported; compare against the hand-written catalogue recipes and list differences.
3. **Crafting tasks on the new model**: `CraftGenericManuallyTask` uses `toGrid`, the recipe book task uses
   the imported recipe id → `JankCraftingRecipeMapping` goes away. `CraftingHelper` fixed (real copies).
4. **Catalogue**: crafted/smelted catalogue entries take their recipe from the registry instead of
   hand-written grids; `CraftWithMatching*` tasks become "choose recipe"; wood/colour variants come for free.
5. **Remove** `CraftingRecipe`, `RecipeTarget`, `JankCraftingRecipeMapping`, the `shapedRecipe*` helpers.
6. Later: smithing and stonecutter (needs accessor mixins per version); reading data packs for 1.21.2+.

## 6. Decisions (2026-11-27)

1. Items are id strings (`minecraft:oak_planks`), converted to `Item` at the task boundary.
2. Local fallback recipes for all important basic recipes, as data files in vanilla format (section 4).
3. Scope of step 2: crafting + smelting (furnace, smoker, blast furnace, campfire).
   Smithing and stonecutter later.
