package adris.altoclef.chains;

import adris.altoclef.AltoClef;
import adris.altoclef.Settings;
import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsManager;
import adris.altoclef.settings.SettingsService;
import adris.altoclef.multiversion.FoodComponentWrapper;
import adris.altoclef.multiversion.item.ItemVer;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.speedrun.DragonBreathTracker;
import adris.altoclef.tasksystem.TaskRunner;
import adris.altoclef.util.helpers.*;
import adris.altoclef.util.slots.PlayerSlot;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class FoodChain extends SingleTaskChain {
    private static final FoodChainConfig config = new FoodChainConfig(SettingsService.manager());
    private static boolean hasFood;

    private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
    private boolean isTryingToEat = false;
    private boolean requestFillup = false;
    private boolean needsFood = false;
    private Optional<Item> cachedPerfectFood = Optional.empty();
    private boolean shouldStop = false;

    public FoodChain(TaskRunner runner) {
        super(runner);
    }

    @Override
    protected void onTaskFinish(AltoClef mod) {
        // Nothing.
    }

    private void startEat(AltoClef mod, Item food) {
        //Debug.logInternal("EATING " + toUse.getTranslationKey() + " : " + test);
        if (mod.getPlayer().isBlocking()) {
            mod.log("want to eat, trying to stop shielding...");
            mod.getInputControls().release(Input.CLICK_RIGHT);
            return;
        }

        isTryingToEat = true;
        requestFillup = true;
        mod.getSlotHandler().forceEquipItem(new Item[]{food}, true); //"true" because it's food
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        mod.getExtraBaritoneSettings().setInteractionPaused(true);
    }

    private void stopEat() {
        if (isTryingToEat) {
            AltoClef altoClef = AltoClef.getInstance();

            if (altoClef.getItemStorage().hasItem(Items.SHIELD) || altoClef.getItemStorage().hasItemInOffhand(Items.SHIELD)) {
                if (StorageHelper.getItemStackInSlot(PlayerSlot.OFFHAND_SLOT).getItem() != Items.SHIELD) {
                    altoClef.getSlotHandler().forceEquipItemToOffhand(Items.SHIELD);
                } else {
                    isTryingToEat = false;
                    requestFillup = false;
                }
            } else {
                isTryingToEat = false;
                requestFillup = false;
            }
            altoClef.getInputControls().release(Input.CLICK_RIGHT);
            altoClef.getExtraBaritoneSettings().setInteractionPaused(false);
        }
    }

    public boolean isTryingToEat() {
        return isTryingToEat;
    }

    @Override
    public float getPriority() {
        AltoClef mod = AltoClef.getInstance();

        if (WorldHelper.isInNetherPortal()) {
            stopEat();
            return Float.NEGATIVE_INFINITY;
        }
        // do not interrupt defending from mobs by eating
        if (mod.getMobDefenseChain().isPuttingOutFire()
                || mod.getMobDefenseChain().isShielding()
                || mod.getPlayer().isBlocking()
                || mod.getMobDefenseChain().isDoingAcrobatics()
        ) {
            stopEat();
            return Float.NEGATIVE_INFINITY;
        }
        dragonBreathTracker.updateBreath(mod);
        for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer()) {
            if (dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                stopEat();
                return Float.NEGATIVE_INFINITY;
            }
        }
        if (!mod.getModSettings().isAutoEat()) {
            stopEat();
            return Float.NEGATIVE_INFINITY;
        }

        // do NOT eat while in lava if we are escaping it (spaghetti code dependencies go brrrr)
        if (mod.getPlayer().isInLava()) {
            stopEat();
            return Float.NEGATIVE_INFINITY;
        }

        /*
        - Eats if:
        - We're hungry and have food that fits
            - We're low on health and maybe a little bit hungry
            - We're very low on health and are even slightly hungry
        - We're kind of hungry and have food that fits perfectly
         */
        // We're in danger, don't eat now!!
        if (!mod.getMLGBucketChain().doneMLG() || mod.getMLGBucketChain().isFalling(mod) ||
                mod.getPlayer().isBlocking() || shouldStop) {
            stopEat();
            return Float.NEGATIVE_INFINITY;
        }
        Pair<Integer, Optional<Item>> calculation = calculateFood(mod);
        int cachedFoodScore = calculation.getLeft();
        cachedPerfectFood = calculation.getRight();
        hasFood = cachedFoodScore > 0;
        // If we requested a fillup but we're full, stop.
        if (requestFillup && mod.getPlayer().getHungerManager().getFoodLevel() >= 20) {
            requestFillup = false;
        }
        // If we no longer have food, we no longer can eat.
        if (!hasFood) {
            requestFillup = false;
        }

        //FIXME should check if currently fighting
        if (hasFood && (needsToEat() || requestFillup) && cachedPerfectFood.isPresent() &&
                !mod.getMLGBucketChain().isChorusFruiting() && !mod.getPlayer().isBlocking()/* &&
                !areEnemiesNearby(mod)*/) {

            Item toUse = cachedPerfectFood.get();

            // Make sure we're not facing a container
            if (!LookHelper.tryAvoidingInteractable(mod)) {
                return Float.NEGATIVE_INFINITY;
            }
            startEat(mod, toUse);
        } else {
            stopEat();
        }

        Settings settings = mod.getModSettings();

        if (needsFood || cachedFoodScore < settings.getMinimumFoodAllowed()) {
            needsFood = cachedFoodScore < settings.getFoodUnitsToCollect();

            // Only collect if we don't have enough food.
            // If the user inputs invalid settings, the bot would get stuck here.
            if (cachedFoodScore < settings.getFoodUnitsToCollect()) {
                setTask(new CollectFoodTask(settings.getFoodUnitsToCollect()));
                return 55f;
            }
        }


        // Food eating is handled asynchronously.
        return Float.NEGATIVE_INFINITY;
    }

    private boolean areEnemiesNearby(AltoClef mod) {
        for (Entity entity : mod.getEntityTracker().getCloseEntities()) {
            if (entity instanceof HostileEntity hostile && hostile.distanceTo(mod.getPlayer()) < (isTryingToEat?14:7)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isActive() {
        // We're always checking for food.
        return true;
    }

    @Override
    public String getName() {
        return "Food";
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopEat();
    }

    public boolean needsToEat() {
        if (!hasFood() || shouldStop) {
            return false;
        }


        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;
        int foodLevel = player.getHungerManager().getFoodLevel();
        float health = player.getHealth();

        if (foodLevel >= 20) {
            // We can't eat.
            return false;
        }

        if (health <= 10) {
            return true;
        }
        //Debug.logMessage("FOOD: " + foodLevel + " -- HEALTH: " + health);

        // Eat if we're desperate/need to heal ASAP
        if (player.isOnFire() || player.hasStatusEffect(StatusEffects.WITHER) || health < config.alwaysEatWhenWitherOrFireAndHealthBelow.get()) {
            return true;
        } else if (foodLevel > config.alwaysEatWhenBelowHunger.get()) {
            if (health < config.alwaysEatWhenBelowHealth.get()) {
                return true;
            }
        } else {
            // We have half hunger
            return true;
        }


        // Eat if we're  units hungry and we have a perfect fit.
        if (foodLevel < config.alwaysEatWhenBelowHungerAndPerfectFit.get() && cachedPerfectFood.isPresent()) {
            int need = 20 - foodLevel;
            Item best = cachedPerfectFood.get();

            int fills = (ItemVer.getFoodComponent(best) != null) ? ItemVer.getFoodComponent(best).getHunger() : -1;
            return fills == need;
        }

        return false;
    }

    private Pair<Integer, Optional<Item>> calculateFood(AltoClef mod) {
        Item bestFood = null;
        double bestFoodScore = Double.NEGATIVE_INFINITY;
        int foodTotal = 0;
        ClientPlayerEntity player = mod.getPlayer();
        float health = player != null ? player.getHealth() : 20;
        //float toHeal = player != null? 20 - player.getHealth() : 0;
        float hunger = player != null ? player.getHungerManager().getFoodLevel() : 20;
        float saturation = player != null ? player.getHungerManager().getSaturationLevel() : 20;
        // Get best food item + calculate food total
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (ItemVer.isFood(stack)) {
                // Ignore protected items
                if (!ItemHelper.canThrowAwayStack(mod, stack)) continue;

                // Ignore spider eyes
                if (stack.getItem() == Items.SPIDER_EYE) {
                    continue;
                }

                FoodComponentWrapper food = ItemVer.getFoodComponent(stack.getItem());

                assert food != null;
                float hungerIfEaten = Math.min(hunger + food.getHunger(), 20);
                float saturationIfEaten = Math.min(hungerIfEaten, saturation + food.getSaturationModifier());
                float gainedSaturation = (saturationIfEaten - saturation);
                float gainedHunger = (hungerIfEaten - hunger);
                float hungerNotFilled = 20 - hungerIfEaten;

                float saturationWasted = food.getSaturationModifier() - gainedSaturation;
                float hungerWasted = food.getHunger() - gainedHunger;

                boolean prioritizeSaturation = health < config.prioritizeSaturationWhenBelowHealth.get();
                float saturationGoodScore = prioritizeSaturation ? gainedSaturation * config.foodPickPrioritizeSaturationSaturationMultiplier.get() : gainedSaturation;
                float saturationLossPenalty = prioritizeSaturation ? 0 : saturationWasted * config.foodPickSaturationWastePenaltyMultiplier.get();
                float hungerLossPenalty = hungerWasted * config.foodPickHungerWastePenaltyMultiplier.get();
                float hungerNotFilledPenalty = hungerNotFilled * config.foodPickHungerNotFilledPenaltyMultiplier.get();

                float score = saturationGoodScore - saturationLossPenalty - hungerLossPenalty - hungerNotFilledPenalty;

                if (stack.getItem() == Items.ROTTEN_FLESH) {
                    score -= config.foodPickRottenFleshPenalty.get();
                }
                if (score > bestFoodScore) {
                    bestFoodScore = score;
                    bestFood = stack.getItem();
                }

                foodTotal += Objects.requireNonNull(ItemVer.getFoodComponent(stack.getItem())).getHunger() * stack.getCount();
            }
        }

        return new Pair<>(foodTotal, Optional.ofNullable(bestFood));
    }

    // If we need to eat like, NOW.
    public boolean needsToEatCritical() {
        return false;
    }

    public boolean hasFood() {
        return hasFood;
    }

    public void shouldStop(boolean shouldStopInput) {
        shouldStop = shouldStopInput;
    }

    public boolean isShouldStop() {
        return shouldStop;
    }

    /**
     * Eating behaviour (key prefix {@code foodChain.}). Health and hunger are in half hearts / half drumsticks (0-20).
     */
    static class FoodChainConfig {
        private static final String P = "foodChain.";
        final Setting<Integer> alwaysEatWhenWitherOrFireAndHealthBelow;
        final Setting<Integer> alwaysEatWhenBelowHunger;
        final Setting<Integer> alwaysEatWhenBelowHealth;
        final Setting<Integer> alwaysEatWhenBelowHungerAndPerfectFit;
        final Setting<Integer> prioritizeSaturationWhenBelowHealth;
        final Setting<Float> foodPickPrioritizeSaturationSaturationMultiplier;
        final Setting<Float> foodPickSaturationWastePenaltyMultiplier;
        final Setting<Float> foodPickHungerWastePenaltyMultiplier;
        final Setting<Float> foodPickHungerNotFilledPenaltyMultiplier;
        final Setting<Float> foodPickRottenFleshPenalty;
        final Setting<Float> runDontEatMaxHealth;
        final Setting<Integer> runDontEatMaxHunger;
        final Setting<Integer> canTankHitsAndEatArmor;
        final Setting<Integer> canTankHitsAndEatMaxHunger;

        FoodChainConfig(SettingsManager m) {
            alwaysEatWhenWitherOrFireAndHealthBelow = Setting.integer(P + "alwaysEatWhenWitherOrFireAndHealthBelow", 6).range(0, 20)
                    .description("Always eat when burning or withering and health is below this.").register(m);
            alwaysEatWhenBelowHunger = Setting.integer(P + "alwaysEatWhenBelowHunger", 10).range(0, 20)
                    .description("Always eat when hunger is below this.").register(m);
            alwaysEatWhenBelowHealth = Setting.integer(P + "alwaysEatWhenBelowHealth", 14).range(0, 20)
                    .description("Eat (if hunger allows) when health is below this.").register(m);
            alwaysEatWhenBelowHungerAndPerfectFit = Setting.integer(P + "alwaysEatWhenBelowHungerAndPerfectFit", 20 - 5).range(0, 20)
                    .description("Eat when hunger is below this and a food fits perfectly.").register(m);
            prioritizeSaturationWhenBelowHealth = Setting.integer(P + "prioritizeSaturationWhenBelowHealth", 8).range(0, 20)
                    .description("Below this health, prefer food with high saturation.").register(m);
            foodPickPrioritizeSaturationSaturationMultiplier = Setting.floating(P + "foodPickPrioritizeSaturationSaturationMultiplier", 8f).range(0f, 1000f)
                    .description("Food choice: weight of gained saturation when saturation is prioritized.").register(m);
            foodPickSaturationWastePenaltyMultiplier = Setting.floating(P + "foodPickSaturationWastePenaltyMultiplier", 1f).range(0f, 1000f)
                    .description("Food choice: penalty per wasted saturation point.").register(m);
            foodPickHungerWastePenaltyMultiplier = Setting.floating(P + "foodPickHungerWastePenaltyMultiplier", 2f).range(0f, 1000f)
                    .description("Food choice: penalty per wasted hunger point.").register(m);
            foodPickHungerNotFilledPenaltyMultiplier = Setting.floating(P + "foodPickHungerNotFilledPenaltyMultiplier", 1f).range(0f, 1000f)
                    .description("Food choice: penalty per hunger point that stays unfilled.").register(m);
            foodPickRottenFleshPenalty = Setting.floating(P + "foodPickRottenFleshPenalty", 100f).range(0f, 10_000f)
                    .description("Food choice: penalty for rotten flesh.").register(m);
            runDontEatMaxHealth = Setting.floating(P + "runDontEatMaxHealth", 3f).range(0f, 20f)
                    .description("With enemies close and health at or below this, run instead of eating.").register(m);
            runDontEatMaxHunger = Setting.integer(P + "runDontEatMaxHunger", 3).range(0, 20)
                    .description("With enemies close and hunger at or below this, run instead of eating.").register(m);
            canTankHitsAndEatArmor = Setting.integer(P + "canTankHitsAndEatArmor", 15).range(0, 30)
                    .description("With at least this much armor the bot eats even while being hit.").register(m);
            canTankHitsAndEatMaxHunger = Setting.integer(P + "canTankHitsAndEatMaxHunger", 3).range(0, 20)
                    .description("Max hunger at which the bot eats while tanking hits.").register(m);
        }
    }
}
