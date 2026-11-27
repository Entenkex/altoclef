package adris.altoclef.tasksystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for {@link Task}: they pin down how the current task tree
 * starts, ticks, switches, stops and interrupts sub tasks, so the planned
 * task-system refactor (TODO step 3) can be checked against today's behaviour.
 */
class TaskLifecycleTest {

    private List<String> log;
    private TestChain chain;

    @BeforeEach
    void setUp() {
        log = new ArrayList<>();
        chain = new TestChain(new TaskRunner(null));
    }

    @Test
    void onStartRunsOnceThenOnlyOnTick() {
        RecordingTask task = new RecordingTask("a");

        task.tick(chain);
        task.tick(chain);

        assertEquals(List.of("a.start", "a.tick", "a.tick"), log);
        assertTrue(task.isActive());
    }

    @Test
    void subTaskIsTickedAndRecordedInChain() {
        RecordingTask child = new RecordingTask("child");
        RecordingTask parent = new RecordingTask("parent", () -> child);

        chain.tick(parent);

        assertEquals(List.of("parent.start", "parent.tick", "child.start", "child.tick"), log);
        assertEquals(List.of(parent, child), chain.getTasks());
    }

    @Test
    void equalSubTaskIsKeptAndNotRestarted() {
        RecordingTask parent = new RecordingTask("parent", () -> new RecordingTask("child"));

        chain.tick(parent);
        chain.tick(parent);

        // A new but equal() child instance does not replace the running one.
        assertEquals(List.of("parent.start", "parent.tick", "child.start", "child.tick",
                "parent.tick", "child.tick"), log);
    }

    @Test
    void differentSubTaskStopsOldOneWithInterruptTask() {
        RecordingTask first = new RecordingTask("first");
        RecordingTask second = new RecordingTask("second");
        Task[] next = {first};
        RecordingTask parent = new RecordingTask("parent", () -> next[0]);

        chain.tick(parent);
        next[0] = second;
        log.clear();
        chain.tick(parent);

        assertEquals(List.of("parent.tick", "first.stop(by second)", "second.start", "second.tick"), log);
        assertTrue(first.stopped());
        assertFalse(first.isActive());
    }

    @Test
    void returningNullStopsCurrentSubTaskCleanly() {
        RecordingTask child = new RecordingTask("child");
        Task[] next = {child};
        RecordingTask parent = new RecordingTask("parent", () -> next[0]);

        chain.tick(parent);
        next[0] = null;
        log.clear();
        chain.tick(parent);

        assertEquals(List.of("parent.tick", "child.stop(by null)"), log);
        assertTrue(child.stopped());
    }

    @Test
    void stopPropagatesToSubTasksAndBlocksFurtherTicks() {
        RecordingTask child = new RecordingTask("child");
        RecordingTask parent = new RecordingTask("parent", () -> child);
        chain.tick(parent);
        log.clear();

        parent.stop();
        assertEquals(List.of("parent.stop(by null)", "child.stop(by null)"), log);
        assertTrue(parent.stopped());

        // A stopped task restarts on the next tick (stop() resets "first").
        log.clear();
        chain.tick(parent);
        assertEquals("parent.start", log.get(0));
    }

    @Test
    void interruptRunsOnStopButKeepsTaskActive() {
        RecordingTask child = new RecordingTask("child");
        RecordingTask parent = new RecordingTask("parent", () -> child);
        chain.tick(parent);
        log.clear();

        parent.interrupt(null);

        assertEquals(List.of("parent.stop(by null)", "child.stop(by null)"), log);
        assertTrue(parent.isActive());
        assertFalse(parent.stopped());

        // Next tick runs onStart again for the interrupted task.
        log.clear();
        chain.tick(parent);
        assertEquals("parent.start", log.get(0));
    }

    @Test
    void forcingSubTaskCannotBeReplaced() {
        ForcingTask forced = new ForcingTask("forced");
        RecordingTask other = new RecordingTask("other");
        Task[] next = {forced};
        RecordingTask parent = new RecordingTask("parent", () -> next[0]);

        chain.tick(parent);
        next[0] = other;
        log.clear();
        chain.tick(parent);

        // The forcing child keeps running, the new candidate is ignored.
        assertEquals(List.of("parent.tick", "forced.tick"), log);
        assertTrue(forced.isActive());

        forced.force = false;
        log.clear();
        chain.tick(parent);
        assertEquals(List.of("parent.tick", "forced.stop(by other)", "other.start", "other.tick"), log);
    }

    @Test
    void resetMakesTaskStartAgain() {
        RecordingTask task = new RecordingTask("a");
        task.tick(chain);
        task.reset();
        log.clear();

        task.tick(chain);

        assertEquals(List.of("a.start", "a.tick"), log);
    }

    // ---------------------------------------------------------------------------------

    private class RecordingTask extends Task {
        final String name;
        private final Supplier<Task> child;

        RecordingTask(String name) {
            this(name, () -> null);
        }

        RecordingTask(String name, Supplier<Task> child) {
            this.name = name;
            this.child = child;
        }

        @Override
        protected void onStart() {
            log.add(name + ".start");
        }

        @Override
        protected Task onTick() {
            log.add(name + ".tick");
            return child.get();
        }

        @Override
        protected void onStop(Task interruptTask) {
            String by = interruptTask instanceof RecordingTask r ? r.name : String.valueOf(interruptTask);
            log.add(name + ".stop(by " + by + ")");
        }

        @Override
        protected boolean isEqual(Task other) {
            return other instanceof RecordingTask r && r.name.equals(name);
        }

        @Override
        protected String toDebugString() {
            return name;
        }
    }

    private class ForcingTask extends RecordingTask implements ITaskCanForce {
        boolean force = true;

        ForcingTask(String name) {
            super(name);
        }

        @Override
        public boolean shouldForce(Task interruptingCandidate) {
            return force;
        }
    }

    private static class TestChain extends TaskChain {
        TestChain(TaskRunner runner) {
            super(runner);
        }

        void tick(Task root) {
            getTasks().clear();
            root.tick(this);
        }

        @Override
        protected void onStop() {
        }

        @Override
        public void onInterrupt(TaskChain other) {
        }

        @Override
        protected void onTick() {
        }

        @Override
        public float getPriority() {
            return 0;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
