package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.SuggestingStringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.multiversion.recipemanager.GameRecipeImporter;
import adris.altoclef.recipes.Ingredient;
import adris.altoclef.recipes.ItemIds;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeComparison;
import adris.altoclef.recipes.RecipeRegistry;
import adris.altoclef.recipes.RecipeShape;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.trackers.RecipeRegistryTracker;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RecipeTarget;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Debug view of the new recipe registry (docs/recipe-model.md).
 * <pre>
 * recipes            how many recipes were imported / are fallback
 * recipes &lt;item&gt;     the recipes for an item, with grid
 * recipes compare    compare the hand-written TaskCatalogue recipes with the registry (report in altoclef/recipe_compare.txt)
 * </pre>
 */
public class RecipesCommand extends Command {

    private static final Path REPORT = Paths.get("altoclef", "recipe_compare.txt");

    public RecipesCommand() {
        super("recipes", "Show recipes: recipes [item | compare]",
                new SuggestingStringArg("item", "", RecipesCommand::suggestions));
    }

    private static Stream<String> suggestions() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getRecipeRegistryTracker() == null) return Stream.of("compare");
        Set<String> outputs = new java.util.TreeSet<>();
        for (Recipe recipe : mod.getRecipeRegistryTracker().getRegistry().all()) {
            String item = recipe.output().item();
            outputs.add(item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item);
        }
        return Stream.concat(Stream.of("compare"), outputs.stream());
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String arg = parser.get(String.class);
        RecipeRegistryTracker tracker = mod.getRecipeRegistryTracker();
        if (arg.isEmpty()) {
            stats(tracker);
        } else if (arg.equalsIgnoreCase("compare")) {
            compare(tracker.getRegistry());
        } else {
            show(tracker.getRegistry(), arg);
        }
        finish();
    }

    private void stats(RecipeRegistryTracker tracker) {
        RecipeRegistry registry = tracker.getRegistry();
        List<String> lines = new ArrayList<>();
        GameRecipeImporter.Result last = tracker.getLastImport();
        if (tracker.hasGameRecipes() && last != null) {
            lines.add("§7  Game: §f" + last.recipes().size() + "§7 imported, " + last.skipped()
                    + " skipped (special, smithing, stonecutter, ...)");
        } else {
            lines.add("§7  Game: §cnot imported yet§7 (join a world)");
        }
        lines.add("§7  Fallback: §f" + registry.size(Recipe.Source.FALLBACK) + "§7 built-in recipes"
                + (tracker.getFallbackWarnings().isEmpty() ? "" : ", §c" + tracker.getFallbackWarnings().size() + " broken"));
        if (!registry.fallbackUsed().isEmpty()) {
            lines.add("§7  Fallback used for: " + String.join(", ", registry.fallbackUsed()));
        }
        lines.add("§7recipes <item> for details, recipes compare to check the old hand-written recipes");
        logLines("§aRecipes", lines);
    }

    private void show(RecipeRegistry registry, String item) {
        String id;
        try {
            id = ItemIds.normalize(item);
        } catch (IllegalArgumentException e) {
            logUserError(e.getMessage());
            return;
        }
        List<Recipe> recipes = registry.byOutput(id);
        if (recipes.isEmpty()) {
            logUserError("No recipe makes " + id);
            return;
        }
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Recipe recipe : recipes) {
            if (shown++ == 6) {
                lines.add("§7  ... and " + (recipes.size() - 6) + " more");
                break;
            }
            lines.add("§f  " + recipe.id() + "§7 (" + recipe.station() + ", " + recipe.source()
                    + ") -> §b" + recipe.output().count());
            lines.addAll(shapeLines(recipe.shape()));
        }
        logLines("§a" + recipes.size() + " recipe(s) for " + id, lines);
    }

    private static List<String> shapeLines(RecipeShape shape) {
        List<String> lines = new ArrayList<>();
        if (shape instanceof RecipeShape.Shaped shaped) {
            for (int y = 0; y < shaped.height(); y++) {
                List<String> cells = new ArrayList<>();
                for (int x = 0; x < shaped.width(); x++) cells.add(shaped.cell(x, y).toString());
                lines.add("§7    [" + String.join(", ", cells) + "]");
            }
        } else if (shape instanceof RecipeShape.Shapeless shapeless) {
            lines.add("§7    any layout: " + shapeless.ingredients());
        } else if (shape instanceof RecipeShape.Cooking cooking) {
            lines.add("§7    " + cooking.input() + ", " + cooking.cookTicks() / 20.0 + "s");
        }
        return lines;
    }

    // ------------------------------------------------------------------ compare

    private void compare(RecipeRegistry registry) {
        Map<String, RecipeTarget> handWritten = collectHandWritten();
        Map<RecipeComparison.Status, List<String>> byStatus = new EnumMap<>(RecipeComparison.Status.class);
        StringBuilder report = new StringBuilder("# Hand-written TaskCatalogue recipes vs. recipe registry\n")
                .append("# SAME / DIFFERENT_COUNT / DIFFERENT_LAYOUT / DIFFERENT_INGREDIENTS / NO_RECIPE\n\n");

        for (Map.Entry<String, RecipeTarget> entry : handWritten.entrySet()) {
            RecipeTarget target = entry.getValue();
            String output = Registries.ITEM.getId(target.getOutputItem()).toString();
            List<Set<String>> grid = new ArrayList<>();
            for (ItemTarget slot : target.getRecipe().getSlots()) grid.add(itemIds(slot));
            RecipeComparison.Result result = RecipeComparison.compare(output, grid, target.getRecipe().outputCount(), registry);
            byStatus.computeIfAbsent(result.status(), k -> new ArrayList<>()).add(entry.getKey());

            report.append(result.status()).append("  ").append(entry.getKey()).append(" -> ").append(output)
                    .append(" x").append(target.getRecipe().outputCount()).append('\n');
            if (result.status() != RecipeComparison.Status.SAME) {
                report.append("    hand: ").append(describeHand(grid)).append('\n');
                if (result.closest() != null) {
                    report.append("    registry: ").append(result.closest()).append('\n');
                    for (String line : shapeLines(result.closest().shape())) {
                        report.append("      ").append(line.replace("§7", "").strip()).append('\n');
                    }
                }
            }
        }
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logUserError("Could not write " + REPORT + ": " + e.getMessage());
        }

        List<String> lines = new ArrayList<>();
        for (RecipeComparison.Status status : RecipeComparison.Status.values()) {
            List<String> names = byStatus.getOrDefault(status, List.of());
            if (names.isEmpty()) continue;
            String color = status == RecipeComparison.Status.SAME ? "§a" : "§e";
            String sample = status == RecipeComparison.Status.SAME ? ""
                    : ": " + String.join(", ", names.subList(0, Math.min(8, names.size()))) + (names.size() > 8 ? ", ..." : "");
            lines.add("  " + color + status + "§7 " + names.size() + sample);
        }
        lines.add("§7Details: " + REPORT.toAbsolutePath());
        logLines("§aCompared " + handWritten.size() + " hand-written recipes", lines);
    }

    /**
     * Catalogue name → hand-written recipe, for all catalogue entries that are a plain crafting task.
     */
    private Map<String, RecipeTarget> collectHandWritten() {
        Map<String, RecipeTarget> result = new LinkedHashMap<>();
        for (String name : new TreeMap<String, Boolean>(toMap(TaskCatalogue.resourceNames())).keySet()) {
            ResourceTask task;
            try {
                task = TaskCatalogue.getItemTask(name, 1);
            } catch (RuntimeException e) {
                continue;
            }
            if (task instanceof CraftInInventoryTask inventory) {
                result.put(name, inventory.getRecipeTarget());
            } else if (task instanceof CraftInTableTask table) {
                RecipeTarget[] targets = table.getRecipeTargets();
                if (targets.length > 0) result.put(name, targets[0]);
            }
        }
        return result;
    }

    private static Map<String, Boolean> toMap(java.util.Collection<String> names) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String name : names) map.put(name, true);
        return map;
    }

    private static Set<String> itemIds(ItemTarget target) {
        Set<String> ids = new LinkedHashSet<>();
        if (target == null || target.isEmpty()) return ids;
        for (Item item : target.getMatches()) ids.add(Registries.ITEM.getId(item).toString());
        return ids;
    }

    private static String describeHand(List<Set<String>> grid) {
        List<Ingredient> cells = new ArrayList<>();
        for (Set<String> cell : grid) {
            cells.add(cell.isEmpty() ? Ingredient.EMPTY : Ingredient.anyOf(cell.size() == 1 ? null : shortList(cell), cell));
        }
        return RecipeComparison.describe(cells, grid.size() == 4 ? 2 : 3);
    }

    private static String shortList(Set<String> items) {
        List<String> names = new ArrayList<>();
        for (String id : items) {
            names.add(id.replace("minecraft:", ""));
            if (names.size() == 2) break;
        }
        return String.join("|", names) + (items.size() > 2 ? "|..." : "");
    }
}
