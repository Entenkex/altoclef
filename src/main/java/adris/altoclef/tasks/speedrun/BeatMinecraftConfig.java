package adris.altoclef.tasks.speedrun;

import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsManager;
import adris.altoclef.settings.SettingsService;

/**
 * Settings of the beat-the-game task (key prefix {@code beatMinecraft.}).
 */
public class BeatMinecraftConfig {

    private static final String P = "beatMinecraft.";
    private static BeatMinecraftConfig instance;

    public final Setting<Integer> targetEyes;
    public final Setting<Integer> minimumEyes;
    public final Setting<Boolean> placeSpawnNearEndPortal;
    public final Setting<Boolean> barterPearlsInsteadOfEndermanHunt;
    public final Setting<Boolean> sleepThroughNight;
    public final Setting<Boolean> rePickupCraftingTable;
    public final Setting<Integer> foodUnits;
    public final Setting<Integer> requiredBeds;
    public final Setting<Integer> minBuildMaterialCount;
    public final Setting<Integer> buildMaterialCount;
    public final Setting<Boolean> rePickupSmoker;
    public final Setting<Boolean> rePickupFurnace;

    public BeatMinecraftConfig(SettingsManager m) {
        targetEyes = Setting.integer(P + "targetEyes", 14).range(0, 64)
                .description("How many eyes of ender to collect.").register(m);
        minimumEyes = Setting.integer(P + "minimumEyes", 12).range(0, 64)
                .description("Minimum eyes of ender to have before the stronghold portal is opened.").register(m);
        placeSpawnNearEndPortal = Setting.bool(P + "placeSpawnNearEndPortal", false)
                .description("Set the spawn point with a bed near the end portal.").register(m);
        barterPearlsInsteadOfEndermanHunt = Setting.bool(P + "barterPearlsInsteadOfEndermanHunt", false)
                .description("Get ender pearls by bartering with piglins instead of hunting endermen.").register(m);
        sleepThroughNight = Setting.bool(P + "sleepThroughNight", false)
                .description("Sleep through the night in the overworld.").register(m);
        rePickupCraftingTable = Setting.bool(P + "rePickupCraftingTable", true)
                .description("Pick placed crafting tables up again.").register(m);
        foodUnits = Setting.integer(P + "foodUnits", 220).range(0, 10_000)
                .description("Food units to collect.").register(m);
        requiredBeds = Setting.integer(P + "requiredBeds", 10).range(0, 64)
                .description("Beds to collect for killing the dragon.").register(m);
        minBuildMaterialCount = Setting.integer(P + "minBuildMaterialCount", 5).range(0, 2304)
                .description("Below this many building blocks, collect more.").register(m);
        buildMaterialCount = Setting.integer(P + "buildMaterialCount", 64).range(0, 2304)
                .description("How many building blocks to collect.").register(m);
        rePickupSmoker = Setting.bool(P + "rePickupSmoker", true)
                .description("Pick placed smokers up again.").register(m);
        rePickupFurnace = Setting.bool(P + "rePickupFurnace", true)
                .description("Pick placed furnaces up again.").register(m);
    }

    /**
     * The registered instance (created and registered on first use).
     */
    public static synchronized BeatMinecraftConfig get() {
        if (instance == null) {
            instance = new BeatMinecraftConfig(SettingsService.manager());
        }
        return instance;
    }
}
