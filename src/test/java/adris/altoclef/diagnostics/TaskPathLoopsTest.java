package adris.altoclef.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TaskPathLoopsTest {

    @Test
    void legitimateNestingIsNoLoop() {
        // iron pickaxe -> stone pickaxe -> wooden pickaxe: the same kind of task several times, different targets.
        List<String> path = List.of("craft(iron_pickaxe)", "table", "collect(iron)", "mine(raw_iron)", "require(STONE)",
                "craft(stone_pickaxe)", "table", "collect(stone)", "require(WOOD)", "craft(wooden_pickaxe)", "table");
        assertEquals(Optional.empty(), TaskPathLoops.find(path));
    }

    @Test
    void repeatedSegmentIsALoop() {
        List<String> cycle = List.of("nether", "portal", "flint_and_steel", "iron", "stone_pickaxe", "blackstone");
        List<String> path = new java.util.ArrayList<>(List.of("iron_pickaxe", "iron"));
        path.addAll(cycle);
        path.addAll(cycle);
        path.add("nether");

        Optional<TaskPathLoops.Loop> loop = TaskPathLoops.find(path);

        assertTrue(loop.isPresent());
        assertEquals(6, loop.get().length());
        assertEquals("nether", path.get(loop.get().start()));
    }

    @Test
    void taskThatReturnsItselfAsSubTask() {
        assertEquals(Optional.of(new TaskPathLoops.Loop(1, 1)), TaskPathLoops.find(List.of("root", "a", "a")));
    }

    @Test
    void shortPathsAreFine() {
        assertEquals(Optional.empty(), TaskPathLoops.find(List.of()));
        assertEquals(Optional.empty(), TaskPathLoops.find(List.of("a")));
        assertEquals(Optional.empty(), TaskPathLoops.find(List.of("a", "b", "a")));
    }
}
