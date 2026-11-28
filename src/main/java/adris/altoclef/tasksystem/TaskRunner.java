package adris.altoclef.tasksystem;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;

import java.util.ArrayList;
import java.util.List;

public class TaskRunner {

    private final ArrayList<TaskChain> chains = new ArrayList<>();
    private final AltoClef mod;
    private boolean active;

    private TaskChain cachedCurrentTaskChain = null;

    public String statusReport = " (no chain running) ";

    public TaskRunner(AltoClef mod) {
        this.mod = mod;
        active = false;
    }

    public void tick() {
        if (!active || !AltoClef.inGame()) {
            statusReport = " (no chain running) ";
            return;
        }

        // Get highest priority chain and run
        TaskSystemListener listener = TaskSystemListener.get();
        List<TaskSystemListener.ChainPriority> evaluated = listener == TaskSystemListener.NONE ? null : new ArrayList<>(chains.size());
        TaskChain maxChain = null;
        float maxPriority = Float.NEGATIVE_INFINITY;
        for (TaskChain chain : chains) {
            if (!chain.isActive()) {
                if (evaluated != null) evaluated.add(new TaskSystemListener.ChainPriority(chain.getName(), false, Float.NEGATIVE_INFINITY));
                continue;
            }
            float priority = chain.getPriority();
            if (evaluated != null) evaluated.add(new TaskSystemListener.ChainPriority(chain.getName(), true, priority));
            if (priority > maxPriority) {
                maxPriority = priority;
                maxChain = chain;
            }
        }
        TaskChain previous = cachedCurrentTaskChain;
        if (cachedCurrentTaskChain != null && maxChain != cachedCurrentTaskChain) {
            cachedCurrentTaskChain.onInterrupt(maxChain);
        }
        cachedCurrentTaskChain = maxChain;
        if (maxChain != null) {
            statusReport = "Chain: "+maxChain.getName() + ", priority: "+maxPriority;
            maxChain.tick();
        } else {
            statusReport = " (no chain running) ";
        }
        if (evaluated != null) {
            listener.runnerTicked(evaluated, previous, maxChain);
        }
    }

    public void addTaskChain(TaskChain chain) {
        chains.add(chain);
    }

    public void enable() {
        if (!active) {
            mod.getBehaviour().push();
            mod.getBehaviour().setPauseOnLostFocus(false);
        }
        active = true;
    }

    public void disable() {
        if (active) {
            mod.getBehaviour().pop();
            Debug.logMessage("Stopped");
        }
        for (TaskChain chain : chains) {
            chain.stop();
        }
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public TaskChain getCurrentTaskChain() {
        return cachedCurrentTaskChain;
    }

    // Kinda jank ngl
    public AltoClef getMod() {
        return mod;
    }
}
