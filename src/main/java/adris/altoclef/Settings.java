package adris.altoclef;

import adris.altoclef.control.KillAura;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsManager;
import adris.altoclef.settings.SettingsService;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.util.BlockRange;
import adris.altoclef.util.helpers.ItemHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Streams;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The core settings of the mod (key prefix {@code core.}), used across the codebase.
 * <p>
 * Values come from the active settings profile ({@code altoclef/profiles/}), see {@link SettingsService}.
 * All settings with defaults and limits are listed in the generated {@code altoclef/settings_reference.yml}.
 */
public class Settings {

    private static final String P = "core.";
    private static final TypeReference<List<Item>> ITEM_LIST = new TypeReference<>() {
    };

    private static Settings instance;

    private final Setting<Boolean> showDebugTickMs;
    private final Setting<Boolean> showTaskChains;
    private final Setting<Boolean> hideAllWarningLogs;
    private final Setting<Boolean> showStackTracesInChat;
    private final Setting<String> commandPrefix;
    private final Setting<String> logLevel;
    private final Setting<String> chatLogPrefix;
    private final Setting<Boolean> showTimer;
    private final Setting<Float> containerItemMoveDelay;
    private final Setting<Boolean> useCraftingBookToCraft;
    private final Setting<Float> resourcePickupDropRange;
    private final Setting<Integer> minimumFoodAllowed;
    private final Setting<Integer> foodUnitsToCollect;
    private final Setting<Float> resourceChestLocateRange;
    private final Setting<Float> resourceMineRange;
    private final Setting<Boolean> avoidSearchingDungeonChests;
    private final Setting<Boolean> avoidOceanBlocks;
    private final Setting<Float> entityReachRange;
    private final Setting<Boolean> collectPickaxeFirst;
    private final Setting<Boolean> replantCrops;
    private final Setting<Boolean> mobDefense;
    private final Setting<KillAura.Strategy> forceFieldStrategy;
    private final Setting<Boolean> dodgeProjectiles;
    private final Setting<Boolean> killOrAvoidAnnoyingHostiles;
    private final Setting<Boolean> avoidDrowning;
    private final Setting<Boolean> autoCloseScreenWhenLookingOrMining;
    private final Setting<Boolean> extinguishSelfWithWater;
    private final Setting<Boolean> autoEat;
    private final Setting<Boolean> autoMLGBucket;
    private final Setting<Boolean> autoReconnect;
    private final Setting<Boolean> autoRespawn;
    private final Setting<DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR> overworldToNetherBehaviour;
    private final Setting<Integer> netherFastTravelWalkingRange;
    private final Setting<String> idleCommand;
    private final Setting<String> deathCommand;
    private final Setting<List<Item>> throwawayItems;
    private final Setting<Integer> reservedBuildingBlockCount;
    private final Setting<Boolean> dontThrowAwayCustomNameItems;
    private final Setting<Boolean> dontThrowAwayEnchantedItems;
    private final Setting<Boolean> throwAwayUnusedItems;
    private final Setting<List<Item>> importantItems;
    private final Setting<Boolean> limitFuelsToSupportedFuels;
    private final Setting<List<Item>> supportedFuels;
    private final Setting<BlockPos> homeBasePosition;
    private final Setting<List<BlockRange>> areasToProtect;

    public Settings(SettingsManager m) {
        showDebugTickMs = Setting.bool(P + "showDebugTickMs", false)
                .description("Shows a debug bar with how many milliseconds AltoClef's tick takes.").register(m);
        showTaskChains = Setting.bool(P + "showTaskChains", true)
                .description("Shows the current task chain in the top left.").register(m);
        hideAllWarningLogs = Setting.bool(P + "hideAllWarningLogs", false)
                .description("Disables all warning logs. Not recommended, makes debugging harder.").register(m);
        showStackTracesInChat = Setting.bool(P + "showStackTracesInChat", false)
                .description("Show the full stack trace of errors in chat (for debugging). The log file always contains it.").register(m);
        commandPrefix = Setting.string(P + "commandPrefix", "@")
                .description("Prefix for AltoClef commands (e.g. @gamer).")
                .check(v -> v.isBlank() ? "must not be empty" : null).register(m);
        logLevel = Setting.string(P + "logLevel", "NORMAL")
                .description("""
                        Log level: ALL (everything), NORMAL (warnings and errors), WARN (only warnings),
                        ERROR (only errors), NONE (nothing; some errors may still be printed).""")
                .check(v -> Set.of("ALL", "NORMAL", "WARN", "ERROR", "NONE").contains(v)
                        ? null : "must be one of ALL, NORMAL, WARN, ERROR, NONE").register(m);
        chatLogPrefix = Setting.string(P + "chatLogPrefix", "[Alto Clef] ")
                .description("Prepended to every log message in chat.").register(m);
        showTimer = Setting.bool(P + "showTimer", true)
                .description("Shows a timer.").register(m);
        containerItemMoveDelay = Setting.floating(P + "containerItemMoveDelay", 0.2f).range(0f, 5f)
                .description("Delay in seconds between item moves in inventories (crafting, furnace, chests).").register(m);
        useCraftingBookToCraft = Setting.bool(P + "useCraftingBookToCraft", true)
                .description("Use the recipe book to fill the crafting grid (much faster). If false, items are placed slot by slot.").register(m);
        resourcePickupDropRange = Setting.floating(P + "resourcePickupDropRange", -1f).range(-1f, 10_000f)
                .description("Dropped resource items further away than this are not picked up. -1 disables the limit.").register(m);
        minimumFoodAllowed = Setting.integer(P + "minimumFoodAllowed", 0).range(0, 10_000)
                .description("Minimum food (units) to keep in the inventory; below this the bot collects more.").register(m);
        foodUnitsToCollect = Setting.integer(P + "foodUnitsToCollect", 0).range(0, 10_000)
                .description("Food units to collect when food is below minimumFoodAllowed.").register(m);
        resourceChestLocateRange = Setting.floating(P + "resourceChestLocateRange", 500f).range(0f, 10_000f)
                .description("""
                        Cached chests within this range are used to get resources from. 0 disables chest pickups.
                        Don't set it too high, the bot then prefers chests even if the resource is close by.""").register(m);
        resourceMineRange = Setting.floating(P + "resourceMineRange", 100f).range(-1f, 10_000f)
                .description("""
                        Blocks normally obtained otherwise (e.g. crafting tables) are mined if found within this range.
                        0 disables it, -1 ALWAYS mines a catalogued block (not recommended, may travel very far).""").register(m);
        avoidSearchingDungeonChests = Setting.bool(P + "avoidSearchingDungeonChests", true)
                .description("When storing items, check the area around a chest so the bot doesn't keep digging up dungeons.").register(m);
        avoidOceanBlocks = Setting.bool(P + "avoidOceanBlocks", false)
                .description("Ignore blocks below an ocean (ocean biome, below y=64). AltoClef can't handle oceans.").register(m);
        entityReachRange = Setting.floating(P + "entityReachRange", 4f).range(0f, 10f)
                .description("How close the bot must be to attack/interact with an entity. 6 works in singleplayer, 4 on stricter servers.").register(m);
        collectPickaxeFirst = Setting.bool(P + "collectPickaxeFirst", true)
                .description("Get a pickaxe before collecting anything (helps when drops are underground).").register(m);
        replantCrops = Setting.bool(P + "replantCrops", true)
                .description("Replant crops that were broken while collecting food.").register(m);
        mobDefense = Setting.bool(P + "mobDefense", true)
                .description("Kill aura and survival moves: run from hostiles at low health, from creepers, avoid dangerous mobs, dodge projectiles.").register(m);
        forceFieldStrategy = Setting.enumeration(P + "forceFieldStrategy", KillAura.Strategy.class, KillAura.Strategy.SMART)
                .description("""
                        Force field behaviour when mobDefense is on (pushes mobs away, not meant to kill):
                        FASTEST: attack all hostiles every frame. DELAY: attack the closest when the attack is charged.
                        SMART: attack the closest at most every 0.2 s. OFF: off.""").register(m);
        dodgeProjectiles = Setting.bool(P + "dodgeProjectiles", true)
                .description("With mobDefense: try to dodge incoming projectiles.").register(m);
        killOrAvoidAnnoyingHostiles = Setting.bool(P + "killOrAvoidAnnoyingHostiles", true)
                .description("Kill or run from mobs that stay close for too long (skeletons, large groups).").register(m);
        avoidDrowning = Setting.bool(P + "avoidDrowning", true)
                .description("Avoid going underwater when baritone gives no movement instructions.").register(m);
        autoCloseScreenWhenLookingOrMining = Setting.bool(P + "autoCloseScreenWhenLookingOrMining", true)
                .description("Close open screens when the look direction changes or the bot mines (prevents getting stuck in a container).").register(m);
        extinguishSelfWithWater = Setting.bool(P + "extinguishSelfWithWater", true)
                .description("Extinguish yourself when on fire (and not fire immune).").register(m);
        autoEat = Setting.bool(P + "autoEat", true)
                .description("Eat when hungry or in danger.").register(m);
        autoMLGBucket = Setting.bool(P + "autoMLGBucket", true)
                .description("MLG / no-fall bucket when knocked off course and falling.").register(m);
        autoReconnect = Setting.bool(P + "autoReconnect", true)
                .description("Reconnect to the last server after a disconnect. If false the bot stops on disconnect.").register(m);
        autoRespawn = Setting.bool(P + "autoRespawn", true)
                .description("Respawn instantly after death. If false the bot stops when it dies.").register(m);
        overworldToNetherBehaviour = Setting.enumeration(P + "overworldToNetherBehaviour",
                        DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR.class,
                        DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR.BUILD_PORTAL_VANILLA)
                .description("""
                        What to do if the bot must go to the nether but finds no portal:
                        BUILD_PORTAL_VANILLA: build one (obsidian or water bucket + lava). GO_TO_HOME_BASE: go to homeBasePosition and assume a portal there.""").register(m);
        netherFastTravelWalkingRange = Setting.integer(P + "netherFastTravelWalkingRange", 600).range(0, 100_000)
                .description("When fast travelling through the nether, walk if closer than this in the overworld.").register(m);
        idleCommand = Setting.string(P + "idleCommand", "")
                .description("""
                        Command to run when nothing else runs, e.g. "idle", "follow <name>" or "goto <coords>". Empty = none.""").register(m);
        deathCommand = Setting.string(P + "deathCommand", "")
                .description("""
                        Command(s) to run after death; {deathmessage} is replaced with the death message.
                        Several commands can be separated with " & ", e.g. "/home & I died: {deathmessage} & @get diamond".""").register(m);
        throwawayItems = Setting.of(P + "throwawayItems", ITEM_LIST, Arrays.asList(
                        // Overworld junk
                        Items.DRIPSTONE_BLOCK,
                        Items.ROOTED_DIRT,
                        Items.GRAVEL,
                        Items.SAND,
                        Items.DIORITE,
                        Items.ANDESITE,
                        Items.GRANITE,
                        Items.TUFF,
                        Items.COBBLESTONE,
                        Items.DIRT,
                        Items.COBBLED_DEEPSLATE,
                        Items.ACACIA_LEAVES, Items.BIRCH_LEAVES, Items.DARK_OAK_LEAVES, Items.OAK_LEAVES, Items.JUNGLE_LEAVES, Items.SPRUCE_LEAVES,
                        // Nether junk, mostly tuned for the "beat game" task
                        Items.NETHERRACK,
                        Items.MAGMA_BLOCK,
                        Items.SOUL_SOIL,
                        Items.SOUL_SAND,
                        Items.NETHER_BRICKS,
                        Items.NETHER_BRICK,
                        Items.BASALT,
                        Items.BLACKSTONE,
                        Items.END_STONE,
                        Items.SANDSTONE,
                        Items.STONE_BRICKS))
                .description("Thrown away first when something has to be thrown away.").register(m);
        reservedBuildingBlockCount = Setting.integer(P + "reservedBuildingBlockCount", 64).range(0, 2304)
                .description("How many throwaway blocks to keep as building blocks.").register(m);
        dontThrowAwayCustomNameItems = Setting.bool(P + "dontThrowAwayCustomNameItems", true)
                .description("Items with a custom name count as important and are never thrown away.").register(m);
        dontThrowAwayEnchantedItems = Setting.bool(P + "dontThrowAwayEnchantedItems", true)
                .description("Enchanted items count as important and are never thrown away.").register(m);
        throwAwayUnusedItems = Setting.bool(P + "throwAwayUnusedItems", true)
                .description("""
                        If no throwaway items are left, throw away any unimportant item the current tasks don't need.
                        Careful: anything not in importantItems may be thrown away.""").register(m);
        importantItems = Setting.of(P + "importantItems", ITEM_LIST, Streams.concat(
                        Stream.of(
                                Items.TOTEM_OF_UNDYING,
                                Items.ENCHANTED_GOLDEN_APPLE,
                                Items.ENDER_EYE,
                                Items.TRIDENT,
                                Items.DIAMOND,
                                Items.DIAMOND_BLOCK,
                                Items.NETHERITE_SCRAP,
                                Items.NETHERITE_INGOT,
                                Items.NETHERITE_BLOCK
                        ),
                        Stream.of(ItemHelper.DIAMOND_ARMORS),
                        Stream.of(ItemHelper.NETHERITE_ARMORS),
                        Stream.of(ItemHelper.DIAMOND_TOOLS),
                        Stream.of(ItemHelper.NETHERITE_TOOLS),
                        // Don't throw away shulker boxes, that would be pretty bad
                        Stream.of(ItemHelper.SHULKER_BOXES)
                ).toList())
                .description("Never thrown away, even with throwAwayUnusedItems.").register(m);
        limitFuelsToSupportedFuels = Setting.bool(P + "limitFuelsToSupportedFuels", true)
                .description("""
                        Only burn items from supportedFuels when smelting. If false, ALL burnable unprotected items
                        may be burned (blaze rods, beds, wooden tools, crafting tables, ...).""").register(m);
        supportedFuels = Setting.of(P + "supportedFuels", ITEM_LIST, Arrays.asList(Items.COAL, Items.CHARCOAL))
                .description("Fuels used when limitFuelsToSupportedFuels is true.").register(m);
        homeBasePosition = Setting.of(P + "homeBasePosition", BlockPos.class, new BlockPos(0, 64, 0))
                .description("The bot's home base (\"x, y, z\"). Only used by tasks that are told to use it.").register(m);
        areasToProtect = Setting.of(P + "areasToProtect", new TypeReference<List<BlockRange>>() {
                }, Collections.<BlockRange>emptyList())
                .description("""
                        Areas that are never mined (anti grief / spawn protection). Example:
                        - {start: "-10, 0, -10", end: "10, 255, 10"}
                        - {start: "1000, 50, 2000", end: "1200, 255, 2100", dimension: NETHER}""").register(m);
    }

    /**
     * Creates the core settings once, loads the active profile and calls {@code onReload} now and after every reload.
     */
    public static void load(Consumer<Settings> onReload) {
        if (instance == null) {
            instance = new Settings(SettingsService.manager());
            Settings settings = instance;
            SettingsService.manager().addReloadListener(() -> onReload.accept(settings));
        }
        SettingsService.load();
    }

    public boolean shouldShowTaskChain() {
        return showTaskChains.get();
    }

    public boolean shouldShowDebugTickMs() {
        return showDebugTickMs.get();
    }

    public boolean shouldHideAllWarningLogs() {
        return hideAllWarningLogs.get();
    }

    public boolean shouldShowStackTracesInChat() {
        return showStackTracesInChat.get();
    }

    public String getLogLevel() {
        return logLevel.get();
    }

    public String getCommandPrefix() {
        return commandPrefix.get();
    }

    public String getChatLogPrefix() {
        return chatLogPrefix.get();
    }

    public boolean shouldShowTimer() {
        return showTimer.get();
    }

    public float getResourcePickupRange() {
        return resourcePickupDropRange.get();
    }

    public float getResourceChestLocateRange() {
        return resourceChestLocateRange.get();
    }

    public float getResourceMineRange() {
        return resourceMineRange.get();
    }

    public float getContainerItemMoveDelay() {
        return containerItemMoveDelay.get();
    }

    public boolean shouldUseCraftingBookToCraft() {
        return useCraftingBookToCraft.get();
    }

    public int getFoodUnitsToCollect() {
        return foodUnitsToCollect.get();
    }

    public int getMinimumFoodAllowed() {
        return minimumFoodAllowed.get();
    }

    public boolean isMobDefense() {
        return mobDefense.get();
    }

    public boolean isDodgeProjectiles() {
        return dodgeProjectiles.get();
    }

    public boolean isAutoEat() {
        return autoEat.get();
    }

    public boolean isAutoReconnect() {
        return autoReconnect.get();
    }

    public boolean isAutoRespawn() {
        return autoRespawn.get();
    }

    public boolean shouldReplantCrops() {
        return replantCrops.get();
    }

    public boolean shouldDealWithAnnoyingHostiles() {
        return killOrAvoidAnnoyingHostiles.get();
    }

    public KillAura.Strategy getForceFieldStrategy() {
        return forceFieldStrategy.get();
    }

    public String getIdleCommand() {
        return idleCommand.get();
    }

    public String getDeathCommand() {
        return deathCommand.get();
    }

    public boolean shouldRunIdleCommandWhenNotActive() {
        String command = idleCommand.get();
        return command != null && !command.isBlank();
    }

    public boolean shouldAutoMLGBucket() {
        return autoMLGBucket.get();
    }

    public boolean shouldCollectPickaxeFirst() {
        return collectPickaxeFirst.get();
    }

    public boolean shouldAvoidDrowning() {
        return avoidDrowning.get();
    }

    public boolean shouldCloseScreenWhenLookingOrMining() {
        return autoCloseScreenWhenLookingOrMining.get();
    }

    public boolean shouldExtinguishSelfWithWater() {
        return extinguishSelfWithWater.get();
    }

    public boolean shouldAvoidSearchingForDungeonChests() {
        return avoidSearchingDungeonChests.get();
    }

    public boolean shouldAvoidOcean() {
        return avoidOceanBlocks.get();
    }

    public boolean isThrowaway(Item item) {
        return throwawayItems.get().contains(item);
    }

    public boolean isImportant(Item item) {
        return importantItems.get().contains(item);
    }

    public boolean shouldThrowawayUnusedItems() {
        return throwAwayUnusedItems.get();
    }

    public int getReservedBuildingBlockCount() {
        return reservedBuildingBlockCount.get();
    }

    public boolean getDontThrowAwayCustomNameItems() {
        return dontThrowAwayCustomNameItems.get();
    }

    public boolean getDontThrowAwayEnchantedItems() {
        return dontThrowAwayEnchantedItems.get();
    }

    public float getEntityReachRange() {
        return entityReachRange.get();
    }

    public Item[] getThrowawayItems(boolean includeProtected) {
        return throwawayItems.get().stream().filter(item -> includeProtected || !AltoClef.getInstance().getBehaviour().isProtected(item)).toArray(Item[]::new);
    }

    public Item[] getThrowawayItems(AltoClef mod) {
        return getThrowawayItems(false);
    }

    public boolean shouldLimitFuelsToSupportedFuels() {
        return limitFuelsToSupportedFuels.get();
    }

    public boolean isSupportedFuel(Item item) {
        return !limitFuelsToSupportedFuels.get() || supportedFuels.get().contains(item);
    }

    public Item[] getSupportedFuelItems() {
        return supportedFuels.get().toArray(Item[]::new);
    }

    public boolean isPositionExplicitlyProtected(BlockPos pos) {
        for (BlockRange protection : areasToProtect.get()) {
            if (protection.contains(pos)) return true;
        }
        return false;
    }

    public DefaultGoToDimensionTask.OVERWORLD_TO_NETHER_BEHAVIOUR getOverworldToNetherBehaviour() {
        return overworldToNetherBehaviour.get();
    }

    public int getNetherFastTravelWalkingRange() {
        return netherFastTravelWalkingRange.get();
    }

    public BlockPos getHomeBasePosition() {
        return homeBasePosition.get();
    }

    /**
     * True if the last profile load had problems (missing/broken files). Values that were only clamped don't count.
     */
    public boolean failedToLoad() {
        return SettingsService.lastLoadFailed();
    }
}
