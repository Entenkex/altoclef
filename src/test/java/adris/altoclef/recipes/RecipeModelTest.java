package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModelTest {

    private static final Ingredient PLANKS = Ingredient.anyOf("#minecraft:planks",
            List.of("oak_planks", "birch_planks"));
    private static final Ingredient STICK = Ingredient.of("stick");
    private static final Ingredient E = Ingredient.EMPTY;

    private static Recipe crafting(String id, RecipeShape shape, String output, int count) {
        return new Recipe(id, Station.CRAFTING, shape, new ItemAmount(output, count), Recipe.Source.GAME);
    }

    @Test
    void idsAreNormalized() {
        assertEquals("minecraft:oak_planks", ItemIds.normalize("Oak_Planks"));
        assertEquals("mymod:thing", ItemIds.normalize("MyMod:Thing"));
        assertTrue(PLANKS.matches("minecraft:oak_planks"));
        assertTrue(PLANKS.matches("birch_planks"));
        assertFalse(PLANKS.matches("stick"));
    }

    @Test
    void sticksAre1x2AndGoIntoTheFirstColumn() {
        // The old tracker filled slots linearly, so this ended up in slot 0 and 1 (side by side).
        RecipeShape.Shaped sticks = RecipeShape.Shaped.of(List.of("#", "#"), Map.of('#', PLANKS));
        assertEquals(1, sticks.width());
        assertEquals(2, sticks.height());
        assertTrue(sticks.fitsInventory());

        List<Ingredient> grid2 = sticks.toGrid(2);
        assertEquals(List.of(PLANKS, E, PLANKS, E), grid2);

        List<Ingredient> grid3 = sticks.toGrid(3);
        assertEquals(List.of(PLANKS, E, E, PLANKS, E, E, E, E, E), grid3);
    }

    @Test
    void pickaxeNeedsTableAndKeepsLayout() {
        RecipeShape.Shaped pickaxe = RecipeShape.Shaped.of(List.of("###", " | ", " | "),
                Map.of('#', PLANKS, '|', STICK));
        assertFalse(pickaxe.fitsInventory());
        assertEquals(List.of(PLANKS, PLANKS, PLANKS, E, STICK, E, E, STICK, E), pickaxe.toGrid(3));
        assertThrows(IllegalArgumentException.class, () -> pickaxe.toGrid(2));
        assertEquals(5, pickaxe.ingredients().size());
    }

    @Test
    void shortPatternRowsArePadded() {
        RecipeShape.Shaped shape = RecipeShape.Shaped.of(List.of("##", "#"), Map.of('#', STICK));
        assertEquals(List.of(STICK, STICK, STICK, E), shape.cells());
    }

    @Test
    void invalidShapesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeShape.Shaped(4, 1, List.of(E, E, E, E)));
        assertThrows(IllegalArgumentException.class, () -> new RecipeShape.Shaped(2, 2, List.of(E)));
        assertThrows(IllegalArgumentException.class, () -> RecipeShape.Shaped.of(List.of("x"), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RecipeShape.Shapeless(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemAmount("stick", 0));
        // Cooking shape at a crafting station
        assertThrows(IllegalArgumentException.class, () -> new Recipe("x", Station.CRAFTING,
                new RecipeShape.Cooking(STICK, 200), new ItemAmount("coal", 1), Recipe.Source.GAME));
    }

    @Test
    void craftsAndInputs() {
        Recipe slab = crafting("minecraft:oak_slab",
                RecipeShape.Shaped.of(List.of("###"), Map.of('#', PLANKS)), "oak_slab", 6);
        assertEquals(0, slab.craftsFor(0));
        assertEquals(1, slab.craftsFor(1));
        assertEquals(1, slab.craftsFor(6));
        assertEquals(2, slab.craftsFor(7));
        assertEquals(Map.of(PLANKS, 3), slab.inputsPerCraft());
        assertTrue(slab.fitsInventory() == false);
    }

    @Test
    void possibleCraftsUsesAlternativesTogether() {
        Recipe table = crafting("minecraft:crafting_table",
                RecipeShape.Shaped.of(List.of("##", "##"), Map.of('#', PLANKS)), "crafting_table", 1);
        // Mixed planks are allowed by the game: 3 oak + 5 birch = 8 planks = 2 tables
        assertEquals(2, table.possibleCrafts(Map.of("minecraft:oak_planks", 3, "minecraft:birch_planks", 5)));
        assertEquals(0, table.possibleCrafts(Map.of("minecraft:oak_planks", 3)));
        assertTrue(table.fitsInventory());
    }

    @Test
    void shapelessAndCooking() {
        Recipe dye = crafting("minecraft:bone_meal", new RecipeShape.Shapeless(List.of(Ingredient.of("bone"))), "bone_meal", 3);
        assertTrue(dye.fitsInventory());
        assertEquals(3, dye.possibleCrafts(Map.of("minecraft:bone", 3)));

        Recipe glass = new Recipe("minecraft:glass", Station.FURNACE,
                new RecipeShape.Cooking(Ingredient.of("sand"), 200), new ItemAmount("glass", 1), Recipe.Source.GAME);
        assertFalse(glass.fitsInventory());
        assertEquals(Map.of(Ingredient.of("sand"), 1), glass.inputsPerCraft());
    }
}
