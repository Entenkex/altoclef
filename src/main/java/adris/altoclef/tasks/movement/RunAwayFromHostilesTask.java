package adris.altoclef.tasks.movement;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.baritone.GoalRunAwayFromEntities;
import adris.altoclef.util.helpers.BaritoneHelper;
import baritone.api.pathing.goals.Goal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SkeletonEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Run until no hostile we are fleeing from is closer than {@code distance}.
 * <p>
 * The hostiles to flee from are remembered while the task runs. {@code EntityTracker.getHostiles()} only lists mobs
 * that are angry at the player <i>right now</i> and within 26 blocks; for skeletons "angry" flickers (no line of
 * sight for a tick). Using that list alone made the goal count as reached the moment a skeleton dropped out of it,
 * the task finished, Mob Defense let go, the skeleton was back next tick, a new path was started and cancelled again:
 * the bot stood still while being shot (two deaths in the 2026-11-28 baseline runs, see docs/baseline-runs.md).
 * A remembered mob is only forgotten when it is dead / unloaded or at least {@code distance} away.
 */
public class RunAwayFromHostilesTask extends CustomBaritoneGoalTask {

    private final double distanceToRun;
    private final boolean includeSkeletons;
    private final Set<LivingEntity> fleeingFrom = new LinkedHashSet<>();

    public RunAwayFromHostilesTask(double distance, boolean includeSkeletons) {
        distanceToRun = distance;
        this.includeSkeletons = includeSkeletons;
    }

    public RunAwayFromHostilesTask(double distance) {
        this(distance, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            fleeingFrom.clear();
        }
    }

    @Override
    protected Goal newGoal(AltoClef mod) {
        // We want to run away NOW
        mod.getClientBaritone().getPathingBehavior().forceCancel();
        return new GoalRunAwayFromHostiles(mod, distanceToRun);
    }

    @Override
    protected boolean isEqual(Task other) {
        if (other instanceof RunAwayFromHostilesTask task) {
            return Math.abs(task.distanceToRun - distanceToRun) < 1;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        return "NIGERUNDAYOO, SUMOOKEYY! distance=" + distanceToRun + ", skeletons=" + includeSkeletons;
    }

    /**
     * Current hostiles plus the ones we already flee from that are still alive and too close.
     */
    private List<Entity> hostilesToFleeFrom(AltoClef mod) {
        synchronized (BaritoneHelper.MINECRAFT_LOCK) {
            for (LivingEntity hostile : mod.getEntityTracker().getHostiles()) {
                if (includeSkeletons || !(hostile instanceof SkeletonEntity)) fleeingFrom.add(hostile);
            }
            double maxSq = distanceToRun * distanceToRun;
            fleeingFrom.removeIf(entity -> entity == null || !entity.isAlive()
                    || entity.squaredDistanceTo(mod.getPlayer()) >= maxSq);
            return new ArrayList<>(fleeingFrom);
        }
    }

    private class GoalRunAwayFromHostiles extends GoalRunAwayFromEntities {

        public GoalRunAwayFromHostiles(AltoClef mod, double distance) {
            super(mod, distance, false, 0.8);
        }

        @Override
        protected List<Entity> getEntities(AltoClef mod) {
            return hostilesToFleeFrom(mod);
        }
    }
}
