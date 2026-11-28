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
import adris.altoclef.util.RegistryCrafting;
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
 * recipes check      which recipe each crafted catalogue entry uses (report in altoclef/recipe_check.txt)
 * </pre>
 */
public class RecipesCommand extends Command {

    private static final Path REPORT = Paths.get("altoclef", "recipe_check.txt");

    public RecipesCommand() {
        super("recipes", "Show recipes: recipes [item | check]",
                new SuggestingStringArg("item", "", RecipesCommand::suggestions));
    }

    private static Stream<String> suggestions() {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getRecipeRegistryTracker() == null) return Stream.of("check");
        Set<String> outputs = new java.util.TreeSet<>();
        for (Recipe recipe : mod.getRecipeRegistryTracker().getRegistry().all()) {
            String item = recipe.output().item();
            outputs.add(item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item);
        }
        return Stream.concat(Stream.of("check"), outputs.stream());
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String arg = parser.get(String.class);
        RecipeRegistryTracker tracker = mod.getRecipeRegistryTracker();
        if (arg.isEmpty()) {
            stats(tracker);
        } else if (arg.equalsIgnoreCase("check")) {
            check();
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
        lines.add("§7recipes <item> for details, recipes check to see which recipe each catalogue entry uses");
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

    // ------------------------------------------------------------------ check

    /**
     * For every catalogue entry crafted via the registry: which recipe would be used and how each
     * ingredient is collected. Entries without a usable recipe are listed in chat.
     */
    private void check() {
        Map<String, Item> crafted = TaskCatalogue.craftedEntries();
        List<String> missing = new ArrayList<>();
        StringBuilder report = new StringBuilder("# Catalogue entries crafted from the recipe registry\n")
                .append("# name -> recipe used (with an empty inventory) and how each slot is collected\n\n");
        for (Map.Entry<String, Item> entry : crafted.entrySet()) {
            ResourceTask task = RegistryCrafting.craftTask(entry.getValue(), 1);
            RecipeTarget target = null;
            if (task instanceof CraftInInventoryTask inventory) target = inventory.getRecipeTarget();
            else if (task instanceof CraftInTableTask table && table.getRecipeTargets().length > 0) target = table.getRecipeTargets()[0];
            if (target == null) {
                missing.add(entry.getKey());
                report.append("MISSING  ").append(entry.getKey()).append('\n');
                continue;
            }
            report.append("ok       ").append(entry.getKey()).append(" x").append(target.getRecipe().outputCount())
                    .append(task instanceof CraftInTableTask ? " (table)" : " (inventory)").append('\n');
            List<String> slots = new ArrayList<>();
            for (ItemTarget slot : target.getRecipe().getSlots()) {
                if (slot == null || slot.isEmpty()) slots.add("-");
                else if (slot.isCatalogueItem()) slots.add("\"" + slot.getCatalogueName() + "\"");
                else slots.add(Registries.ITEM.getId(slot.getMatches()[0]).getPath());
            }
            int size = slots.size() == 4 ? 2 : 3;
            for (int y = 0; y < size; y++) {
                report.append("    [").append(String.join(", ", slots.subList(y * size, y * size + size))).append("]\n");
            }
        }
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logUserError("Could not write " + REPORT + ": " + e.getMessage());
        }
        report.append("\n# Smelted entries\n");
        Map<String, Item> smelted = TaskCatalogue.smeltedEntries();
        for (Map.Entry<String, Item> entry : smelted.entrySet()) {
            ResourceTask task = RegistryCrafting.smeltTask(entry.getValue(), 1);
            if (task == null) {
                missing.add(entry.getKey());
                report.append("MISSING  ").append(entry.getKey()).append('\n');
            } else {
                report.append("ok       ").append(entry.getKey()).append("  ").append(task).append('\n');
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("  \u00A7aok\u00A77 " + (crafted.size() + smelted.size() - missing.size())
                + " (" + crafted.size() + " crafted, " + smelted.size() + " smelted)");
        if (!missing.isEmpty()) lines.add("  \u00A7cno usable recipe\u00A77 " + missing.size() + ": " + String.join(", ", missing));
        lines.add("\u00A77Details: " + REPORT.toAbsolutePath());
        logLines("\u00A7aChecked " + (crafted.size() + smelted.size()) + " crafted/smelted catalogue entries", lines);
    }
}
