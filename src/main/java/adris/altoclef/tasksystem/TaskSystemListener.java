package adris.altoclef.tasksystem;

import java.util.List;

/**
 * Hook for observing the task system without coupling it to the observer (used by the decision log,
 * {@code adris.altoclef.diagnostics}). All methods run on the client thread and must not throw or change tasks.
 * <p>
 * Set with {@link #set(TaskSystemListener)}; without a listener nothing is called (unit tests).
 */
public interface TaskSystemListener {

    /**
     * Priority of one chain in this tick; {@code active == false} means the chain wasn't asked.
     */
    record ChainPriority(String chain, boolean active, float priority) {
    }

    /**
     * After the runner picked and ticked a chain.
     *
     * @param chains   every registered chain in registration order
     * @param previous the chain that ran last tick (may be null)
     * @param chosen   the chain that ran this tick (may be null)
     */
    default void runnerTicked(List<ChainPriority> chains, TaskChain previous, TaskChain chosen) {
    }

    default void taskStarted(Task task, TaskChain chain) {
    }

    /**
     * @param interruptedBy the task that replaced it, null if stopped without a replacement
     */
    default void taskStopped(Task task, Task interruptedBy) {
    }

    /**
     * The task was suspended (chain interrupted), it keeps its state and restarts when its chain runs again.
     */
    default void taskInterrupted(Task task, Task interruptedBy) {
    }

    TaskSystemListener NONE = new TaskSystemListener() {
    };

    static void set(TaskSystemListener listener) {
        Holder.listener = listener == null ? NONE : listener;
    }

    static TaskSystemListener get() {
        return Holder.listener;
    }

    final class Holder {
        private static volatile TaskSystemListener listener = NONE;

        private Holder() {
        }
    }
}
