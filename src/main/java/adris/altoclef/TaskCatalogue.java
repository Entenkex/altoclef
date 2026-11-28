package adris.altoclef;

import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.multiversion.versionedfields.Entities;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.container.UpgradeInSmithingTableTask;
import adris.altoclef.tasks.resources.*;
import adris.altoclef.tasks.resources.wood.*;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.util.*;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.item.Item;
import net.minecraft.util.DyeColor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Contains a hardcoded list of ALL obtainable resources.
 * <p>
 * Most resources correspond to a single item, but some resources (like "log" or "door") include a range of items.
 * <p>
 * Call `TaskCatalogue.getItemTask` to return a task given a resource key.
 * Call `TaskCatalogue.getSquashedItemTask` to return a task that gets multiple resources, combining their steps.
 */
@SuppressWarnings({"rawtypes"})
public class TaskCatalogue {

    private static final HashMap<String, Item[]> nameToItemMatches = new HashMap<>();
    private static final HashMap<String, CataloguedResource> nameToResourceTask = new HashMap<>();
    private static final HashMap<Item, CataloguedResource> itemToResourceTask = new HashMap<>();
    private static final HashSet<Item> resourcesObtainable = new HashSet<>();
    private static final Map<String, Item> craftedEntries = new TreeMap<>();
    private static final Map<String, Item> smeltedEntries = new TreeMap<>();

    public static void init() {
        nameToItemMatches.keySet();
    }

    static {
        /// DEFINE RESOURCE TASKS HERE
        {

            /// RAW RESOURCES
            mine("log", MiningRequirement.HAND, ItemHelper.LOG, ItemHelper.LOG).anyDimension();
            woodTasks("log", wood -> wood.log, (wood, count) -> new MineAndCollectTask(wood.log, count, new Block[]{Block.getBlockFromItem(wood.log)}, MiningRequirement.HAND), true);
            mine("dirt", MiningRequirement.HAND, new Block[]{Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH}, Items.DIRT);
            simple("cobblestone", Items.COBBLESTONE, CollectBlockByOneTask.CollectCobblestoneTask::new).dontMineIfPresent();
            simple("cobbled_deepslate", Items.COBBLED_DEEPSLATE, CollectBlockByOneTask.CollectCobbledDeepslateTask::new).dontMineIfPresent();
            mine("andesite", MiningRequirement.WOOD, Blocks.ANDESITE, Items.ANDESITE);
            mine("granite", MiningRequirement.WOOD, Blocks.GRANITE, Items.GRANITE);
            mine("diorite", MiningRequirement.WOOD, Blocks.DIORITE, Items.DIORITE);
            mine("calcite", MiningRequirement.WOOD, Blocks.CALCITE, Items.CALCITE);
            mine("tuff", MiningRequirement.WOOD, Blocks.TUFF, Items.TUFF);
            mine("netherrack", MiningRequirement.WOOD, Blocks.NETHERRACK, Items.NETHERRACK).forceDimension(Dimension.NETHER);
            mine("magma_block", MiningRequirement.WOOD, Blocks.MAGMA_BLOCK, Items.MAGMA_BLOCK).forceDimension(Dimension.NETHER);
            mine("blackstone", MiningRequirement.WOOD, Blocks.BLACKSTONE, Items.BLACKSTONE).forceDimension(Dimension.NETHER);
            mine("basalt", MiningRequirement.WOOD, Blocks.BASALT, Items.BASALT).forceDimension(Dimension.NETHER);
            mine("soul_sand", Items.SOUL_SAND).forceDimension(Dimension.NETHER);
            mine("soul_soil", Items.SOUL_SOIL).forceDimension(Dimension.NETHER);
            mine("glowstone_dust", Blocks.GLOWSTONE, Items.GLOWSTONE_DUST).forceDimension(Dimension.NETHER);
            mine("coal", MiningRequirement.WOOD, new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE}, Items.COAL);
            mine("raw_iron", MiningRequirement.STONE, new Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE}, Items.RAW_IRON);
            mine("raw_gold", MiningRequirement.IRON, new Block[]{Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE}, Items.RAW_GOLD);
            mine("raw_copper", MiningRequirement.STONE, new Block[]{Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE}, Items.RAW_COPPER);
            mine("diamond", MiningRequirement.IRON, new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE}, Items.DIAMOND);
            mine("emerald", MiningRequirement.IRON, new Block[]{Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE}, Items.EMERALD);
            mine("redstone", MiningRequirement.IRON, new Block[]{Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE}, Items.REDSTONE);
            mine("lapis_lazuli", MiningRequirement.STONE, new Block[]{Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE}, Items.LAPIS_LAZULI);
            alias("lapis", "lapis_lazuli");
            mine("amethyst_shard", MiningRequirement.WOOD, Blocks.AMETHYST_CLUSTER, Items.AMETHYST_SHARD);
            mine("pointed_dripstone", MiningRequirement.WOOD, Blocks.POINTED_DRIPSTONE, Items.POINTED_DRIPSTONE);
            mine("sand", Blocks.SAND, Items.SAND);
            mine("red_sand", Blocks.RED_SAND, Items.RED_SAND);
            mine("gravel", Blocks.GRAVEL, Items.GRAVEL);
            mine("clay_ball", Blocks.CLAY, Items.CLAY_BALL);
            mine("ancient_debris", MiningRequirement.DIAMOND, Blocks.ANCIENT_DEBRIS, Items.ANCIENT_DEBRIS).forceDimension(Dimension.NETHER);
            mine("gilded_blackstone", MiningRequirement.STONE, Blocks.GILDED_BLACKSTONE, Items.GILDED_BLACKSTONE).forceDimension(Dimension.NETHER);
            mine("oak_sapling", Blocks.OAK_LEAVES, Items.OAK_SAPLING);
            mine("spruce_sapling", Blocks.SPRUCE_LEAVES, Items.SPRUCE_SAPLING);
            mine("birch_sapling", Blocks.BIRCH_LEAVES, Items.BIRCH_SAPLING);
            mine("jungle_sapling", Blocks.JUNGLE_LEAVES, Items.JUNGLE_SAPLING);
            mine("acacia_sapling", Blocks.ACACIA_LEAVES, Items.ACACIA_SAPLING);
            mine("dark_oak_sapling", Blocks.DARK_OAK_LEAVES, Items.DARK_OAK_SAPLING);
            mine("mangrove_propagule", Blocks.MANGROVE_PROPAGULE, Items.MANGROVE_PROPAGULE);
            mine("cherry_sapling", Blocks.CHERRY_LEAVES, Items.CHERRY_SAPLING);
            simple("sapling", ItemHelper.SAPLINGS, CollectSaplingsTask::new);
            simple("sandstone", Items.SANDSTONE, CollectSandstoneTask::new).dontMineIfPresent();
            simple("red_sandstone", Items.RED_SANDSTONE, CollectRedSandstoneTask::new).dontMineIfPresent();
            simple("coarse_dirt", Items.COARSE_DIRT, CollectCoarseDirtTask::new).dontMineIfPresent();
            simple("amethyst_block", Items.AMETHYST_BLOCK, CollectAmethystBlockTask::new).dontMineIfPresent();
            simple("dripstone_block", Items.DRIPSTONE_BLOCK, CollectDripstoneBlockTask::new).dontMineIfPresent();
            simple("flint", Items.FLINT, CollectFlintTask::new);
            simple("obsidian", Items.OBSIDIAN, CollectObsidianTask::new).dontMineIfPresent();
            simple("wool", ItemHelper.WOOL, CollectWoolTask::new);
            simple("egg", Items.EGG, CollectEggsTask::new);
            mob("bone", Items.BONE, SkeletonEntity.class);
            mob("gunpowder", Items.GUNPOWDER, CreeperEntity.class);
            simple("ender_pearl", Items.ENDER_PEARL, KillEndermanTask::new);
            mob("spider_eye", Items.SPIDER_EYE, SpiderEntity.class);
            mob("leather", Items.LEATHER, CowEntity.class);
            mob("feather", Items.FEATHER, ChickenEntity.class);
            mob("rotten_flesh", Items.ROTTEN_FLESH, ZombieEntity.class);
            mob("rabbit_foot", Items.RABBIT_FOOT, RabbitEntity.class);
            mob("rabbit_hide", Items.RABBIT_HIDE, RabbitEntity.class);
            mob("slime_ball", Items.SLIME_BALL, SlimeEntity.class);
            mob("wither_skeleton_skull", Items.WITHER_SKELETON_SKULL, WitherSkeletonEntity.class).forceDimension(Dimension.NETHER);
            mob("ink_sac", Items.INK_SAC, SquidEntity.class); // Warning, this probably won't work.
            mob("glow_ink_sac", Items.GLOW_INK_SAC, Entities.GLOW_SQUID); // Warning, this probably won't work.
            mob("string", Items.STRING, SpiderEntity.class); // Warning, this probably won't work.
            mine("sugar_cane", Items.SUGAR_CANE);
            mine("brown_mushroom", MiningRequirement.HAND, new Block[]{Blocks.BROWN_MUSHROOM, Blocks.BROWN_MUSHROOM_BLOCK}, Items.BROWN_MUSHROOM);
            mine("red_mushroom", MiningRequirement.HAND, new Block[]{Blocks.RED_MUSHROOM, Blocks.RED_MUSHROOM_BLOCK}, Items.RED_MUSHROOM);
            mine("mushroom", MiningRequirement.HAND, new Block[]{Blocks.BROWN_MUSHROOM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM, Blocks.RED_MUSHROOM_BLOCK}, Items.BROWN_MUSHROOM, Items.RED_MUSHROOM);
            mine("melon_slice", MiningRequirement.HAND, Blocks.MELON, Items.MELON_SLICE);
            mine("pumpkin", MiningRequirement.HAND, Blocks.PUMPKIN, Items.PUMPKIN);
            mine("bell", MiningRequirement.WOOD, Blocks.BELL, Items.BELL);
            mine("nether_wart", MiningRequirement.HAND, Blocks.NETHER_WART, Items.NETHER_WART).forceDimension(Dimension.NETHER);
            mine("crimson_fungus", MiningRequirement.HAND, Blocks.CRIMSON_FUNGUS, Items.CRIMSON_FUNGUS).forceDimension(Dimension.NETHER);
            mine("warped_fungus", MiningRequirement.HAND, Blocks.WARPED_FUNGUS, Items.WARPED_FUNGUS).forceDimension(Dimension.NETHER);
            mine("crimson_roots", MiningRequirement.HAND, Blocks.CRIMSON_ROOTS, Items.CRIMSON_ROOTS).forceDimension(Dimension.NETHER);
            mine("warped_roots", MiningRequirement.HAND, Blocks.WARPED_ROOTS, Items.WARPED_ROOTS).forceDimension(Dimension.NETHER);
            mine("weeping_vines", MiningRequirement.HAND, Blocks.WEEPING_VINES, Items.WEEPING_VINES).forceDimension(Dimension.NETHER);
            mine("twisting_vines", MiningRequirement.HAND, Blocks.TWISTING_VINES, Items.TWISTING_VINES).forceDimension(Dimension.NETHER);
            mine("nether_wart_block", MiningRequirement.HAND, Blocks.NETHER_WART_BLOCK, Items.NETHER_WART_BLOCK).forceDimension(Dimension.NETHER);
            mine("warped_wart_block", MiningRequirement.HAND, Blocks.WARPED_WART_BLOCK, Items.WARPED_WART_BLOCK).forceDimension(Dimension.NETHER);
            mine("shroomlight", MiningRequirement.HAND, Blocks.SHROOMLIGHT, Items.SHROOMLIGHT).forceDimension(Dimension.NETHER);
            simple("blaze_rod", Items.BLAZE_ROD, CollectBlazeRodsTask::new).forceDimension(Dimension.NETHER); // Not super simple tbh lmao
            //simple("quartz", Items.QUARTZ, CollectQuartzTask::new);
            mine("quartz", MiningRequirement.WOOD, Blocks.NETHER_QUARTZ_ORE, Items.QUARTZ).forceDimension(Dimension.NETHER);
            simple("cocoa_beans", Items.COCOA_BEANS, CollectCocoaBeansTask::new);
            shear("cobweb", Blocks.COBWEB, Items.COBWEB).dontMineIfPresent();
            colorfulTasks("wool", color -> color.wool, (color, count) -> new CollectWoolTask(color.color, count));
            // Misc greenery
            shear("leaves", ItemHelper.itemsToBlocks(ItemHelper.LEAVES), ItemHelper.LEAVES).dontMineIfPresent();
            for (CataloguedResource resource : woodTasks(
                    "leaves",
                    woodItems -> woodItems.leaves,
                    (woodItems, count) -> {
                        if (woodItems.isNetherWood()) {
                            // Nether "leaves" aren't sheared, they can simply be mined.
                            return new MineAndCollectTask(woodItems.leaves, count, new Block[]{Block.getBlockFromItem(woodItems.leaves)}, MiningRequirement.HAND).forceDimension(Dimension.NETHER);
                        } else {
                            return new ShearAndCollectBlockTask(woodItems.leaves, count, Block.getBlockFromItem(woodItems.leaves));
                        }
                    })
            ) {
                resource.dontMineIfPresent();
            }
            mine("bamboo", Blocks.BAMBOO, Items.BAMBOO);
            shear("vine", Blocks.VINE, Items.VINE).dontMineIfPresent();
            shear("grass", Blocks.SHORT_GRASS, Items.SHORT_GRASS).dontMineIfPresent();
            shear("lily_pad", Blocks.LILY_PAD, Items.LILY_PAD).dontMineIfPresent();
            shear("tall_grass", Blocks.TALL_GRASS, Items.TALL_GRASS).dontMineIfPresent();
            shear("fern", Blocks.FERN, Items.FERN).dontMineIfPresent();
            shear("large_fern", Blocks.LARGE_FERN, Items.LARGE_FERN).dontMineIfPresent();
            shear("dead_bush", Blocks.DEAD_BUSH, Items.DEAD_BUSH).dontMineIfPresent();
            shear("glow_lichen", Blocks.GLOW_LICHEN, Items.GLOW_LICHEN).dontMineIfPresent();
            // Flowers
            simple("flower", ItemHelper.FLOWER, CollectFlowerTask::new);
            mine("allium", Items.ALLIUM);
            mine("azure_bluet", Items.AZURE_BLUET);
            mine("blue_orchid", Items.BLUE_ORCHID);
            mine("cactus", Items.CACTUS);
            mine("cornflower", Items.CORNFLOWER);
            mine("dandelion", Items.DANDELION);
            mine("lilac", Items.LILAC);
            mine("lily_of_the_valley", Items.LILY_OF_THE_VALLEY);
            mine("orange_tulip", Items.ORANGE_TULIP);
            mine("oxeye_daisy", Items.OXEYE_DAISY);
            mine("pink_tulip", Items.PINK_TULIP);
            mine("poppy", Items.POPPY);
            mine("peony", Items.PEONY);
            mine("red_tulip", Items.RED_TULIP);
            mine("rose_bush", Items.ROSE_BUSH);
            mine("sunflower", Items.SUNFLOWER);
            mine("white_tulip", Items.WHITE_TULIP);
            // Crops
            simple("wheat", Items.WHEAT, CollectWheatTask::new);
            crop("carrot", Items.CARROT, Blocks.CARROTS, Items.CARROT);
            crop("potato", Items.POTATO, Blocks.POTATOES, Items.POTATO);
            crop("poisonous_potato", Items.POISONOUS_POTATO, Blocks.POTATOES, Items.POTATO);
            crop("beetroot", Items.BEETROOT, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
            simple("wheat_seeds", Items.WHEAT_SEEDS, CollectWheatSeedsTask::new);
            crop("beetroot_seeds", Items.BEETROOT_SEEDS, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);


            // MATERIALS
            //mine("netherite_upgrade_smithing_template", MiningRequirement.HAND, Blocks.CHEST, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE).forceDimension(Dimension.NETHER);
            simple("netherite_upgrade_smithing_template", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, GetSmithingTemplateTask::new);
            alias("netherite_upgrade", "netherite_upgrade_smithing_template");
            simple("planks", ItemHelper.PLANKS, CollectPlanksTask::new).dontMineIfPresent();
            // Per-tree Planks. At the moment, nether planks need to be specified that their logs are in the nether.
            for (CataloguedResource woodCatalogue : woodTasks("planks", wood -> wood.planks, (wood, count) -> {
                CollectPlanksTask result = new CollectPlanksTask(wood.planks, count);
                if (wood.isNetherWood()) {
                    // Kinda jank
                    result.logsInNether();
                }
                return result;
            }, true)) {
                // Don't mine individual planks either!! Handled internally.
                woodCatalogue.dontMineIfPresent();
            }
            simple("stripped_logs", ItemHelper.STRIPPED_LOGS, CollectStrippedLogTask::new).dontMineIfPresent();
            for (CataloguedResource woodCatalogue : woodTasks("stripped_logs", wood -> wood.strippedLog,
                    (wood, count) -> new CollectStrippedLogTask(wood.strippedLog, count))) {
                woodCatalogue.dontMineIfPresent();
            }
            // crafted("stick", Items.STICK);
            simple("stick", Items.STICK, CollectSticksTask::new);
            smelted("stone", Items.STONE).dontMineIfPresent();
            smelted("deepslate", Items.DEEPSLATE).dontMineIfPresent();
            smelted("smooth_stone", Items.SMOOTH_STONE);
            smelted("smooth_quartz", Items.SMOOTH_QUARTZ);
            smelted("smooth_basalt", Items.SMOOTH_BASALT);
            smelted("glass", Items.GLASS).dontMineIfPresent();
            simple("iron_ingot", Items.IRON_INGOT, CollectIronIngotTask::new).forceDimension(Dimension.OVERWORLD);
            smelted("copper_ingot", Items.COPPER_INGOT);
            smelted("charcoal", Items.CHARCOAL);
            smelted("brick", Items.BRICK);
            smelted("nether_brick", Items.NETHER_BRICK);
            smelted("green_dye", Items.GREEN_DYE);
            simple("gold_ingot", Items.GOLD_INGOT, CollectGoldIngotTask::new).anyDimension(); // accounts for nether too
            crafted("iron_block", Items.IRON_BLOCK);
            crafted("gold_block", Items.GOLD_BLOCK);
            crafted("copper_block", Items.COPPER_BLOCK);
            crafted("raw_iron_block", Items.RAW_IRON_BLOCK);
            crafted("raw_gold_block", Items.RAW_GOLD_BLOCK);
            crafted("raw_copper_block", Items.RAW_COPPER_BLOCK);
            crafted("diamond_block", Items.DIAMOND_BLOCK);
            crafted("redstone_block", Items.REDSTONE_BLOCK);
            crafted("coal_block", Items.COAL_BLOCK);
            crafted("emerald_block", Items.EMERALD_BLOCK);
            crafted("lapis_block", Items.LAPIS_BLOCK);
            crafted("slime_block", Items.SLIME_BLOCK);
            crafted("melon", Items.MELON).dontMineIfPresent();
            crafted("glowstone", Items.GLOWSTONE).dontMineIfPresent();
            crafted("clay", Items.CLAY).dontMineIfPresent();
            smelted("netherite_scrap", Items.NETHERITE_SCRAP);
            crafted("netherite_ingot", Items.NETHERITE_INGOT);
            simple("gold_nugget", Items.GOLD_NUGGET, CollectGoldNuggetsTask::new);
            {
                String g = "gold_nugget";
                crafted("glistering_melon_slice", Items.GLISTERING_MELON_SLICE);
            }
            crafted("sugar", Items.SUGAR);
            crafted("bone_meal", Items.BONE_MEAL);
            crafted("melon_seeds", Items.MELON_SEEDS);
            crafted("bamboo_planks", Items.BAMBOO_PLANKS);
            crafted("bamboo_block", Items.BAMBOO_BLOCK);
            simple("hay_block", Items.HAY_BLOCK, CollectHayBlockTask::new).dontMineIfPresent();
            crafted("polished_andesite", Items.POLISHED_ANDESITE);
            crafted("polished_diorite", Items.POLISHED_DIORITE);
            crafted("polished_granite", Items.POLISHED_GRANITE);
            crafted("quartz_block", Items.QUARTZ_BLOCK);
            crafted("polished_blackstone", Items.POLISHED_BLACKSTONE);
            crafted("polished_blackstone_bricks", Items.POLISHED_BLACKSTONE_BRICKS);
            crafted("polished_basalt", Items.POLISHED_BASALT);
            crafted("polished_deepslate", Items.POLISHED_DEEPSLATE);
            crafted("deepslate_bricks", Items.DEEPSLATE_BRICKS);
            crafted("deepslate_tiles", Items.DEEPSLATE_TILES);
            crafted("cut_copper", Items.CUT_COPPER);
            crafted("cut_sandstone", Items.CUT_SANDSTONE);
            crafted("cut_red_sandstone", Items.CUT_RED_SANDSTONE);
            crafted("quartz_bricks", Items.QUARTZ_BRICKS);
            crafted("quartz_pillar", Items.QUARTZ_PILLAR);
            crafted("stone_bricks", Items.STONE_BRICKS);
            crafted("mossy_stone_bricks", Items.MOSSY_STONE_BRICKS);
            crafted("mossy_cobblestone", Items.MOSSY_COBBLESTONE);
            simple("nether_bricks", Items.NETHER_BRICKS, CollectNetherBricksTask::new).dontMineIfPresent();
            crafted("red_nether_bricks", Items.RED_NETHER_BRICKS);
            smelted("cracked_stone_bricks", Items.CRACKED_STONE_BRICKS);
            smelted("cracked_nether_bricks", Items.CRACKED_NETHER_BRICKS);
            smelted("cracked_polished_blackstone_bricks", Items.CRACKED_POLISHED_BLACKSTONE_BRICKS);
            smelted("cracked_deepslate_bricks", Items.CRACKED_DEEPSLATE_BRICKS);
            smelted("cracked_deepslate_tiles", Items.CRACKED_DEEPSLATE_TILES);
            smelted("smooth_sandstone", Items.SMOOTH_SANDSTONE);
            smelted("smooth_red_sandstone", Items.SMOOTH_RED_SANDSTONE);
            {
                String b = "nether_brick";
                crafted("nether_brick_fence", Items.NETHER_BRICK_FENCE);
            }
            crafted("brush", Items.BRUSH);
            crafted("paper", Items.PAPER);
            crafted("book", Items.BOOK);
            crafted("writable_book", Items.WRITABLE_BOOK);
            alias("book_and_quill", "writable_book");
            crafted("bowl", Items.BOWL);
            crafted("blaze_powder", Items.BLAZE_POWDER);
            crafted("ender_eye", Items.ENDER_EYE);
            alias("eye_of_ender", "ender_eye");
            crafted("fermented_spider_eye", Items.FERMENTED_SPIDER_EYE);
            crafted("fire_charge", Items.FIRE_CHARGE);
            crafted("flower_banner_pattern", Items.FLOWER_BANNER_PATTERN);
            simple("magma_cream", Items.MAGMA_CREAM, CollectMagmaCreamTask::new);
            // Slabs + Stairs + Walls
            crafted("cobblestone_slab", Items.COBBLESTONE_SLAB);
            crafted("cobblestone_stairs", Items.COBBLESTONE_STAIRS);
            crafted("cobblestone_wall", Items.COBBLESTONE_WALL);
            crafted("stone_slab", Items.STONE_SLAB);
            crafted("stone_stairs", Items.STONE_STAIRS);
            crafted("smooth_stone_slab", Items.SMOOTH_STONE_SLAB);
            crafted("stone_brick_slab", Items.STONE_BRICK_SLAB);
            crafted("stone_brick_stairs", Items.STONE_BRICK_STAIRS);
            crafted("stone_brick_wall", Items.STONE_BRICK_WALL);
            crafted("mossy_stone_brick_slab", Items.MOSSY_STONE_BRICK_SLAB);
            crafted("mossy_stone_brick_stairs", Items.MOSSY_STONE_BRICK_STAIRS);
            crafted("mossy_stone_brick_wall", Items.MOSSY_STONE_BRICK_WALL);
            crafted("mossy_cobblestone_slab", Items.MOSSY_COBBLESTONE_SLAB);
            crafted("mossy_cobblestone_stairs", Items.MOSSY_COBBLESTONE_STAIRS);
            crafted("mossy_cobblestone_wall", Items.MOSSY_COBBLESTONE_WALL);
            crafted("andesite_slab", Items.ANDESITE_SLAB);
            crafted("andesite_stairs", Items.ANDESITE_STAIRS);
            crafted("andesite_wall", Items.ANDESITE_WALL);
            crafted("granite_slab", Items.GRANITE_SLAB);
            crafted("granite_stairs", Items.GRANITE_STAIRS);
            crafted("granite_wall", Items.GRANITE_WALL);
            crafted("diorite_slab", Items.DIORITE_SLAB);
            crafted("diorite_stairs", Items.DIORITE_STAIRS);
            crafted("diorite_wall", Items.DIORITE_WALL);
            crafted("polished_andesite_slab", Items.POLISHED_ANDESITE_SLAB);
            crafted("polished_andesite_stairs", Items.POLISHED_ANDESITE_STAIRS);
            crafted("polished_granite_slab", Items.POLISHED_GRANITE_SLAB);
            crafted("polished_granite_stairs", Items.POLISHED_GRANITE_STAIRS);
            crafted("polished_diorite_slab", Items.POLISHED_DIORITE_SLAB);
            crafted("polished_diorite_stairs", Items.POLISHED_DIORITE_STAIRS);
            crafted("sandstone_slab", Items.SANDSTONE_SLAB);
            crafted("sandstone_stairs", Items.SANDSTONE_STAIRS);
            crafted("sandstone_wall", Items.SANDSTONE_WALL);
            crafted("cut_sandstone_slab", Items.CUT_SANDSTONE_SLAB);
            crafted("smooth_sandstone_slab", Items.SMOOTH_SANDSTONE_SLAB);
            crafted("smooth_sandstone_stairs", Items.SMOOTH_SANDSTONE_STAIRS);
            crafted("red_sandstone_slab", Items.RED_SANDSTONE_SLAB);
            crafted("red_sandstone_stairs", Items.RED_SANDSTONE_STAIRS);
            crafted("red_sandstone_wall", Items.RED_SANDSTONE_WALL);
            crafted("cut_red_sandstone_slab", Items.CUT_RED_SANDSTONE_SLAB);
            crafted("smooth_red_sandstone_slab", Items.SMOOTH_RED_SANDSTONE_SLAB);
            crafted("smooth_red_sandstone_stairs", Items.SMOOTH_RED_SANDSTONE_STAIRS);
            crafted("nether_brick_slab", Items.NETHER_BRICK_SLAB);
            crafted("nether_brick_stairs", Items.NETHER_BRICK_STAIRS);
            crafted("nether_brick_wall", Items.NETHER_BRICK_WALL);
            crafted("red_nether_brick_slab", Items.RED_NETHER_BRICK_SLAB);
            crafted("red_nether_brick_stairs", Items.RED_NETHER_BRICK_STAIRS);
            crafted("red_nether_brick_wall", Items.RED_NETHER_BRICK_WALL);
            crafted("quartz_slab", Items.QUARTZ_SLAB);
            crafted("quartz_stairs", Items.QUARTZ_STAIRS);
            crafted("smooth_quartz_slab", Items.SMOOTH_QUARTZ_SLAB);
            crafted("smooth_quartz_stairs", Items.SMOOTH_QUARTZ_STAIRS);
            crafted("blackstone_slab", Items.BLACKSTONE_SLAB);
            crafted("blackstone_stairs", Items.BLACKSTONE_STAIRS);
            crafted("blackstone_wall", Items.BLACKSTONE_WALL);
            crafted("polished_blackstone_slab", Items.POLISHED_BLACKSTONE_SLAB);
            crafted("polished_blackstone_stairs", Items.POLISHED_BLACKSTONE_STAIRS);
            crafted("polished_blackstone_wall", Items.POLISHED_BLACKSTONE_WALL);
            crafted("polished_blackstone_brick_slab", Items.POLISHED_BLACKSTONE_BRICK_SLAB);
            crafted("polished_blackstone_brick_stairs", Items.POLISHED_BLACKSTONE_BRICK_STAIRS);
            crafted("polished_blackstone_brick_wall", Items.POLISHED_BLACKSTONE_BRICK_WALL);
            crafted("cut_copper_slab", Items.CUT_COPPER_SLAB);
            crafted("cut_copper_stairs", Items.CUT_COPPER_STAIRS);
            crafted("cobbled_deepslate_slab", Items.COBBLED_DEEPSLATE_SLAB);
            crafted("cobbled_deepslate_stairs", Items.COBBLED_DEEPSLATE_STAIRS);
            crafted("cobbled_deepslate_wall", Items.COBBLED_DEEPSLATE_WALL);
            crafted("polished_deepslate_slab", Items.POLISHED_DEEPSLATE_SLAB);
            crafted("polished_deepslate_stairs", Items.POLISHED_DEEPSLATE_STAIRS);
            crafted("polished_deepslate_wall", Items.POLISHED_DEEPSLATE_WALL);
            crafted("deepslate_brick_slab", Items.DEEPSLATE_BRICK_SLAB);
            crafted("deepslate_brick_stairs", Items.DEEPSLATE_BRICK_STAIRS);
            crafted("deepslate_brick_wall", Items.DEEPSLATE_BRICK_WALL);
            crafted("deepslate_tile_slab", Items.DEEPSLATE_TILE_SLAB);
            crafted("deepslate_tile_stairs", Items.DEEPSLATE_TILE_STAIRS);
            crafted("deepslate_tile_wall", Items.DEEPSLATE_TILE_WALL);
            crafted("chiseled_sandstone", Items.CHISELED_SANDSTONE);
            crafted("chiseled_red_sandstone", Items.CHISELED_RED_SANDSTONE);
            crafted("chiseled_stone_bricks", Items.CHISELED_STONE_BRICKS);
            crafted("chiseled_nether_bricks", Items.CHISELED_NETHER_BRICKS);
            crafted("chiseled_quartz_block", Items.CHISELED_QUARTZ_BLOCK);
            crafted("chiseled_deepslate", Items.CHISELED_DEEPSLATE);
            /// TOOLS
            tools("wooden", Items.WOODEN_PICKAXE, Items.WOODEN_SHOVEL, Items.WOODEN_SWORD, Items.WOODEN_AXE, Items.WOODEN_HOE);
            tools("stone", Items.STONE_PICKAXE, Items.STONE_SHOVEL, Items.STONE_SWORD, Items.STONE_AXE, Items.STONE_HOE);
            tools("iron", Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_HOE);
            tools("golden", Items.GOLDEN_PICKAXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_SWORD, Items.GOLDEN_AXE, Items.GOLDEN_HOE);
            tools("diamond", Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HOE);
            armor("leather", Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
            armor("iron", Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            armor("golden", Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
            armor("diamond", Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            smith("netherite_helmet", Items.NETHERITE_HELMET, "netherite_ingot", "diamond_helmet");
            smith("netherite_chestplate", Items.NETHERITE_CHESTPLATE, "netherite_ingot", "diamond_chestplate");
            smith("netherite_leggings", Items.NETHERITE_LEGGINGS, "netherite_ingot", "diamond_leggings");
            smith("netherite_boots", Items.NETHERITE_BOOTS, "netherite_ingot", "diamond_boots");
            smith("netherite_pickaxe", Items.NETHERITE_PICKAXE, "netherite_ingot", "diamond_pickaxe");
            smith("netherite_axe", Items.NETHERITE_AXE, "netherite_ingot", "diamond_axe");
            smith("netherite_shovel", Items.NETHERITE_SHOVEL, "netherite_ingot", "diamond_shovel");
            smith("netherite_sword", Items.NETHERITE_SWORD, "netherite_ingot", "diamond_sword");
            smith("netherite_hoe", Items.NETHERITE_HOE, "netherite_ingot", "diamond_hoe");
            crafted("bow", Items.BOW);
            crafted("arrow", Items.ARROW);
            {
                String i = "iron_ingot";
                crafted("bucket", Items.BUCKET);
                crafted("flint_and_steel", Items.FLINT_AND_STEEL);
                crafted("shears", Items.SHEARS);
                crafted("iron_nugget", Items.IRON_NUGGET);
                crafted("compass", Items.COMPASS);
                crafted("shield", Items.SHIELD);
                String g = "gold_ingot";
                crafted("clock", Items.CLOCK);
            }
            simple("water_bucket", Items.WATER_BUCKET, CollectBucketLiquidTask.CollectWaterBucketTask::new);
            simple("lava_bucket", Items.LAVA_BUCKET, CollectBucketLiquidTask.CollectLavaBucketTask::new);
            {
                crafted("map", Items.MAP);
            }
            crafted("fishing_rod", Items.FISHING_ROD);
            crafted("carrot_on_a_stick", Items.CARROT_ON_A_STICK);
            crafted("warped_fungus_on_a_stick", Items.WARPED_FUNGUS_ON_A_STICK);
            crafted("spyglass", Items.SPYGLASS);
            crafted("glass_bottle", Items.GLASS_BOTTLE);
            {
                String l = "leather";
                crafted("leather_horse_armor", Items.LEATHER_HORSE_ARMOR);
            }
            alias("wooden_pick", "wooden_pickaxe");
            alias("stone_pick", "stone_pickaxe");
            alias("iron_pick", "iron_pickaxe");
            alias("gold_pick", "golden_pickaxe");
            alias("diamond_pick", "diamond_pickaxe");
            alias("netherite_pick", "netherite_pickaxe");
            simple("boat", ItemHelper.WOOD_BOAT, CollectBoatTask::new);
            woodTasks("boat", woodItems -> woodItems.boat, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.boat, count));
            crafted("lead", Items.LEAD);

            simple("honeycomb", Items.HONEYCOMB, CollectHoneycombTask::new);
            {
                crafted("honeycomb_block", Items.HONEYCOMB_BLOCK);
                crafted("candle", Items.CANDLE);
                crafted("beehive", Items.BEEHIVE);
            }

            // FURNITURE
            crafted("crafting_table", Items.CRAFTING_TABLE).dontMineIfPresent();
            crafted("smithing_table", Items.SMITHING_TABLE);
            crafted("grindstone", Items.GRINDSTONE);
            simple("wooden_pressure_plate", ItemHelper.WOOD_PRESSURE_PLATE, CollectWoodenPressurePlateTask::new);
            woodTasks("pressure_plate", woodItems -> woodItems.pressurePlate, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.pressurePlate, count));
            simple("wooden_button", ItemHelper.WOOD_BUTTON, CollectWoodenButtonTask::new);
            woodTasks("button", woodItems -> woodItems.button, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.button, count));
            crafted("stone_pressure_plate", Items.STONE_PRESSURE_PLATE);
            crafted("stone_button", Items.STONE_BUTTON);
            crafted("polished_blackstone_pressure_plate", Items.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            crafted("polished_blackstone_button", Items.POLISHED_BLACKSTONE_BUTTON);
            simple("sign", ItemHelper.WOOD_SIGN, CollectSignTask::new).dontMineIfPresent(); // By default, we save signs round these parts.
            woodTasks("sign", woodItems -> woodItems.sign, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.sign, count));
            {
                String c = "cobblestone";
                crafted("furnace", Items.FURNACE).dontMineIfPresent();
                crafted("dropper", Items.DROPPER);
                crafted("dispenser", Items.DISPENSER);
                crafted("brewing_stand", Items.BREWING_STAND);
                crafted("piston", Items.PISTON);
                crafted("observer", Items.OBSERVER);
                crafted("lever", Items.LEVER);
            }
            simple("hanging_sign", ItemHelper.WOOD_HANGING_SIGN, CollectHangingSignTask::new).dontMineIfPresent();
            woodTasks("hanging_sign", woodItems -> woodItems.hangingSign, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.hangingSign, count));
            crafted("chest", Items.CHEST).dontMineIfPresent();
            crafted("torch", Items.TORCH);
            simple("bed", ItemHelper.BED, CollectBedTask::new);
            colorfulTasks("bed", colors -> colors.bed, (colors, count) -> RegistryCrafting.craftTask(colors.bed, count));
            {
                String i = "iron_ingot";
                String b = "iron_block";
                crafted("anvil", Items.ANVIL);
                crafted("cauldron", Items.CAULDRON);
                crafted("minecart", Items.MINECART);
                crafted("iron_door", Items.IRON_DOOR);
                crafted("iron_bars", Items.IRON_BARS);
                crafted("blast_furnace", Items.BLAST_FURNACE);
                crafted("iron_trapdoor", Items.IRON_TRAPDOOR);
            }
            crafted("armor_stand", Items.ARMOR_STAND);
            {
                String b = "obsidian";
                crafted("enchanting_table", Items.ENCHANTING_TABLE);
                crafted("ender_chest", Items.ENDER_CHEST).dontMineIfPresent();
            }
            {
                String b = "brick";
                crafted("decorated_pot", Items.DECORATED_POT);
                crafted("flower_pot", Items.FLOWER_POT);
                crafted("bricks", Items.BRICKS);
                crafted("brick_slab", Items.BRICK_SLAB);
                crafted("brick_stairs", Items.BRICK_STAIRS);
                crafted("brick_wall", Items.BRICK_WALL);
            }
            crafted("ladder", Items.LADDER);
            crafted("jukebox", Items.JUKEBOX);
            crafted("note_block", Items.NOTE_BLOCK);
            crafted("redstone_lamp", Items.REDSTONE_LAMP);
            crafted("bookshelf", Items.BOOKSHELF);
            crafted("loom", Items.LOOM);
            {
                String g = "glass";
                crafted("glass_pane", Items.GLASS_PANE).dontMineIfPresent();
            }
            simple("carved_pumpkin", Items.CARVED_PUMPKIN, count -> new CarveThenCollectTask(Items.CARVED_PUMPKIN, count, Blocks.CARVED_PUMPKIN, Items.PUMPKIN, Blocks.PUMPKIN, Items.SHEARS));
            crafted("jack_o_lantern", Items.JACK_O_LANTERN);
            crafted("target", Items.TARGET);
            crafted("campfire", Items.CAMPFIRE);
            crafted("soul_campfire", Items.SOUL_CAMPFIRE);
            crafted("soul_torch", Items.SOUL_TORCH);
            {
                crafted("smoker", Items.SMOKER);
            }
            {
                String i = "iron_nugget";
                crafted("lantern", Items.LANTERN);
                crafted("soul_lantern", Items.SOUL_LANTERN);
                crafted("chain", Items.CHAIN);
            }
            {
                crafted("lodestone", Items.LODESTONE);
            }
            crafted("lightning_rod", Items.LIGHTNING_ROD);
            crafted("tinted_glass", Items.TINTED_GLASS);

            // A BUNCH OF WOODEN STUFF
            simple("wooden_stairs", ItemHelper.WOOD_STAIRS, CollectWoodenStairsTask::new);
            woodTasks("stairs", woodItems -> woodItems.stairs, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.stairs, count));
            simple("wooden_slab", ItemHelper.WOOD_SLAB, CollectWoodenSlabTask::new);
            woodTasks("slab", woodItems -> woodItems.slab, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.slab, count));
            simple("wooden_door", ItemHelper.WOOD_DOOR, CollectWoodenDoorTask::new);
            woodTasks("door", woodItems -> woodItems.door, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.door, count));
            simple("wooden_trapdoor", ItemHelper.WOOD_TRAPDOOR, CollectWoodenTrapDoorTask::new);
            woodTasks("trapdoor", woodItems -> woodItems.trapdoor, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.trapdoor, count));
            simple("wooden_fence", ItemHelper.WOOD_FENCE, CollectFenceTask::new);
            woodTasks("fence", woodItems -> woodItems.fence, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.fence, count));
            simple("wooden_fence_gate", ItemHelper.WOOD_FENCE_GATE, CollectFenceGateTask::new);
            woodTasks("fence_gate", woodItems -> woodItems.fenceGate, (woodItems, count) -> RegistryCrafting.craftTask(woodItems.fenceGate, count));
            {
                crafted("chiseled_bookshelf", Items.CHISELED_BOOKSHELF).dontMineIfPresent();
                crafted("barrel", Items.BARREL);
                crafted("cartography_table", Items.CARTOGRAPHY_TABLE);
                crafted("composter", Items.COMPOSTER);
                crafted("fletching_table", Items.FLETCHING_TABLE);
                crafted("lectern", Items.LECTERN);
            }            // Most people will always think "wooden door" when they say "door".
            alias("door", "wooden_door");
            alias("trapdoor", "wooden_trapdoor");
            alias("fence", "wooden_fence");
            alias("fence_gate", "wooden_fence_gate");

            crafted("heavy_weighted_pressure_plate", Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
            crafted("light_weighted_pressure_plate", Items.LIGHT_WEIGHTED_PRESSURE_PLATE);

            crafted("daylight_detector", Items.DAYLIGHT_DETECTOR);
            crafted("tripwire_hook", Items.TRIPWIRE_HOOK);
            crafted("trapped_chest", Items.TRAPPED_CHEST);
            crafted("crossbow", Items.CROSSBOW);
            {
                crafted("tnt", Items.TNT);
            }
            crafted("sticky_piston", Items.STICKY_PISTON);
            crafted("redstone_torch", Items.REDSTONE_TORCH);
            crafted("repeater", Items.REPEATER);
            crafted("comparator", Items.COMPARATOR);
            {
                // Some rails
                String i = "iron_ingot";
                String g = "gold_ingot";
                crafted("rail", Items.RAIL);
                crafted("powered_rail", Items.POWERED_RAIL);
                crafted("detector_rail", Items.DETECTOR_RAIL);
                crafted("activator_rail", Items.ACTIVATOR_RAIL);
                crafted("hopper", Items.HOPPER);
            }
            crafted("painting", Items.PAINTING);
            crafted("item_frame", Items.ITEM_FRAME);
            crafted("glow_item_frame", Items.GLOW_ITEM_FRAME);
            crafted("chest_minecart", Items.CHEST_MINECART);
            crafted("furnace_minecart", Items.FURNACE_MINECART);
            crafted("hopper_minecart", Items.HOPPER_MINECART);
            crafted("tnt_minecart", Items.TNT_MINECART);
            alias("minecart_with_chest", "chest_minecart");
            alias("minecart_with_furnace", "furnace_minecart");
            alias("minecart_with_hopper", "hopper_minecart");
            alias("minecart_with_tnt", "tnt_minecart");


            /// FOOD
            mobCook("porkchop", Items.PORKCHOP, Items.COOKED_PORKCHOP, PigEntity.class);
            mobCook("beef", Items.BEEF, Items.COOKED_BEEF, CowEntity.class);
            mobCook("chicken", Items.CHICKEN, Items.COOKED_CHICKEN, ChickenEntity.class);
            mobCook("mutton", Items.MUTTON, Items.COOKED_MUTTON, SheepEntity.class);
            mobCook("rabbit", Items.RABBIT, Items.COOKED_RABBIT, RabbitEntity.class);
            mobCook("salmon", Items.SALMON, Items.COOKED_SALMON, SalmonEntity.class);
            mobCook("cod", Items.COD, Items.COOKED_COD, CodEntity.class);
            simple("milk", Items.MILK_BUCKET, CollectMilkTask::new);
            mine("apple", Blocks.OAK_LEAVES, Items.APPLE);
            smelted("baked_potato", Items.BAKED_POTATO);
            crafted("mushroom_stew", Items.MUSHROOM_STEW);
            crafted("suspicious_stew", Items.SUSPICIOUS_STEW);
            crafted("bread", Items.BREAD);
            crafted("cookie", Items.COOKIE);
            crafted("pumpkin_pie", Items.PUMPKIN_PIE);
            crafted("cake", Items.CAKE).dontMineIfPresent();
            {
                crafted("golden_carrot", Items.GOLDEN_CARROT);
                crafted("golden_apple", Items.GOLDEN_APPLE);
            }
            crafted("rabbit_stew", Items.RABBIT_STEW);
            {
                crafted("beetroot_soup", Items.BEETROOT_SOUP);
            }
        }
    }

    private static CataloguedResource put(String name, Item[] matches, Function<Integer, ResourceTask> getTask) {
        List<Item> supportedMatches = new ArrayList<>();
        for (Item item : matches) {
            if (item != Items.UNSUPPORTED) {
                supportedMatches.add(item);
            }
        }
        matches = supportedMatches.toArray(new Item[0]);

        CataloguedResource result = new CataloguedResource(matches, getTask);
        Block[] blocks = ItemHelper.itemsToBlocks(matches);
        // DEFAULT BEHAVIOUR: Mine if present & assume overworld is required!
        if (blocks.length != 0) {
            result.mineIfPresent();
        }
        result.forceDimension(Dimension.OVERWORLD);
        if (nameToResourceTask.containsKey(name)) {
            throw new IllegalStateException("Tried cataloguing " + name + " twice!");
        }
        nameToResourceTask.put(name, result);
        nameToItemMatches.put(name, matches);
        resourcesObtainable.addAll(Arrays.asList(matches));

        // If this resource is just one item, consider it collectable.
        if (matches.length == 1) {
            if (itemToResourceTask.containsKey(matches[0])) {
                throw new IllegalStateException("Tried cataloguing " + matches[0].getTranslationKey() + " twice!");
            }
            itemToResourceTask.put(matches[0], result);
        }

        return result;
    }

    // This is here so that we can use strings for item targets (optionally) and stuff like that.
    public static Item[] getItemMatches(String name) {
        if (!nameToItemMatches.containsKey(name)) {
            return new Item[0];
        }
        return nameToItemMatches.get(name);
    }

    /**
     * Catalogue name → the items it collects (e.g. "planks" → all planks).
     */
    public static Map<String, Item[]> catalogueEntries() {
        return Collections.unmodifiableMap(nameToItemMatches);
    }

    /**
     * Items that have their own catalogue entry (can be collected one by one).
     */
    public static Set<Item> directlyObtainableItems() {
        return Collections.unmodifiableSet(itemToResourceTask.keySet());
    }

    public static boolean isObtainable(Item item) {
        return resourcesObtainable.contains(item);
    }

    public static ItemTarget getItemTarget(String name, int count) {
        return new ItemTarget(name, count);
    }

    public static CataloguedResourceTask getSquashedItemTask(ItemTarget... targets) {
        return new CataloguedResourceTask(true, targets);
    }

    public static ResourceTask getItemTask(String name, int count) {

        if (!taskExists(name)) {
            Debug.logWarning("Task " + name + " does not exist. Error possibly.");
            Debug.logStack();
            return null;
        }

        return nameToResourceTask.get(name).getResource(count);
    }

    public static ResourceTask getItemTask(Item item, int count) {
        if (!taskExists(item)) {
            Debug.logWarning("Task " + item + " does not exist. Error possibly.");
            Debug.logStack();
            return null;
        }

        return itemToResourceTask.get(item).getResource(count);
    }

    public static ResourceTask getItemTask(ItemTarget target) {
        if (target.isCatalogueItem()) {
            return getItemTask(target.getCatalogueName(), target.getTargetCount());
        } else if (target.getMatches().length == 1) {
            return getItemTask(target.getMatches()[0], target.getTargetCount());
        } else {
            return getSquashedItemTask(target);
        }
    }

    public static boolean taskExists(String name) {
        return nameToResourceTask.containsKey(name);
    }

    public static boolean taskExists(Item item) {
        return itemToResourceTask.containsKey(item);
    }

    public static Collection<String> resourceNames() {
        return nameToResourceTask.keySet();
    }

    private static CataloguedResource simple(String name, Item[] matches, Function<Integer, ResourceTask> getTask) {
        return put(name, matches, getTask);
    }

    private static CataloguedResource simple(String name, Item matches, Function<Integer, ResourceTask> getTask) {
        return simple(name, new Item[]{matches}, getTask);
    }

    // TODO: Do I really need this?
    //private static CataloguedResource task(Item matches, Function<Integer, ResourceTask> getTask){
    //
    //}

    private static CataloguedResource mine(String name, MiningRequirement requirement, Item[] toMine, Item... targets) {
        Block[] toMineBlocks = new Block[toMine.length];
        for (int i = 0; i < toMine.length; ++i) toMineBlocks[i] = Block.getBlockFromItem(toMine[i]);
        return mine(name, requirement, toMineBlocks, targets);
    }

    private static CataloguedResource mine(String name, MiningRequirement requirement, Block[] toMine, Item... targets) {
        return put(name, targets, count -> new MineAndCollectTask(new ItemTarget(targets, count), toMine, requirement)).dontMineIfPresent(); // Mining already taken care of!!
    }

    private static CataloguedResource mine(String name, MiningRequirement requirement, Block toMine, Item target) {
        return mine(name, requirement, new Block[]{toMine}, target);
    }

    private static CataloguedResource mine(String name, Block toMine, Item target) {
        return mine(name, MiningRequirement.HAND, toMine, target);
    }

    private static CataloguedResource mine(String name, Item target) {
        return mine(name, Block.getBlockFromItem(target), target);
    }

    private static CataloguedResource shear(String name, Block[] toShear, Item... targets) {
        return put(name, targets, count -> new ShearAndCollectBlockTask(new ItemTarget[]{new ItemTarget(targets, count)}, toShear)).dontMineIfPresent();
    }

    private static CataloguedResource shear(String name, Block toShear, Item... targets) {
        return shear(name, new Block[]{toShear}, targets);
    }

    /**
     * A crafted item: the recipe comes from the recipe registry (game recipes, local fallback otherwise),
     * chosen when the task is created (inventory decides between alternatives). See docs/recipe-model.md.
     */
    private static CataloguedResource crafted(String name, Item match) {
        craftedEntries.put(name, match);
        return put(name, new Item[]{match}, count -> RegistryCrafting.craftTask(match, count));
    }

    /**
     * Catalogue entries crafted via the recipe registry (name → item), for {@code @recipes check}.
     */
    public static Map<String, Item> craftedEntries() {
        return Collections.unmodifiableMap(craftedEntries);
    }

    /**
     * A smelted item: the furnace recipe comes from the recipe registry (the inventory decides between inputs,
     * e.g. raw copper or copper ore).
     */
    private static CataloguedResource smelted(String name, Item match) {
        smeltedEntries.put(name, match);
        return put(name, new Item[]{match}, count -> RegistryCrafting.smeltTask(match, count));
    }

    /**
     * Catalogue entries smelted via the recipe registry (name → item), for {@code @recipes check}.
     */
    public static Map<String, Item> smeltedEntries() {
        return Collections.unmodifiableMap(smeltedEntries);
    }

    private static CataloguedResource smith(String name, Item[] matches, String materials, String tool) {
        return put(name, matches, count -> new UpgradeInSmithingTableTask(new ItemTarget(tool, count), new ItemTarget(materials, count), new ItemTarget(matches, count)));//new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(matches, count), new ItemTarget(materials, count))));
    }

    private static CataloguedResource smith(String name, Item match, String materials, String tool) {
        return smith(name, new Item[]{match}, materials, tool);
    }

    private static CataloguedResource mob(String name, Item[] matches, Class mobClass) {
        return put(name, matches, count -> new KillAndLootTask(mobClass, new ItemTarget(matches, count)));
    }

    private static CataloguedResource mob(String name, Item match, Class mobClass) {
        return mob(name, new Item[]{match}, mobClass);
    }

    private static void mobCook(String uncookedName, String cookedName, Item uncooked, Item cooked, Class mobClass) {
        mob(uncookedName, uncooked, mobClass);
        smelted(cookedName, cooked);
    }

    private static void mobCook(String uncookedName, Item uncooked, Item cooked, Class mobClass) {
        mobCook(uncookedName, "cooked_" + uncookedName, uncooked, cooked, mobClass);
    }

    private static CataloguedResource crop(String name, Item[] matches, Block[] cropBlocks, Item[] cropSeeds) {
        return put(name, matches, count -> new CollectCropTask(new ItemTarget(matches, count), cropBlocks, cropSeeds));
    }

    public static CataloguedResource crop(String name, Item match, Block cropBlock, Item cropSeed) {
        return crop(name, new Item[]{match}, new Block[]{cropBlock}, new Item[]{cropSeed});
    }

    private static void colorfulTasks(String baseName, Function<ItemHelper.ColorfulItems, Item> getMatch, BiFunction<ItemHelper.ColorfulItems, Integer, ResourceTask> getTask) {
        for (DyeColor dCol : DyeColor.values()) {
            MapColor mCol = dCol.getMapColor();
            ItemHelper.ColorfulItems color = ItemHelper.getColorfulItems(mCol);
            String prefix = color.colorName;
            put(prefix + "_" + baseName, new Item[]{getMatch.apply(color)}, count -> getTask.apply(color, count));
        }
    }

    private static CataloguedResource[] woodTasks(Function<ItemHelper.WoodItems, String> getCatalogueName, Function<ItemHelper.WoodItems, Item> getMatch, BiFunction<ItemHelper.WoodItems, Integer, ResourceTask> getTask, boolean requireNetherForNetherStuff) {
        List<CataloguedResource> result = new ArrayList<>();
        for (WoodType woodType : WoodType.values()) {
            ItemHelper.WoodItems woodItems = ItemHelper.getWoodItems(woodType);
            Item match = getMatch.apply(woodItems);
            String cataloguedName = getCatalogueName.apply(woodItems);
            if (match == null) continue;
            boolean isNether = woodItems.isNetherWood();
            CataloguedResource t = put(cataloguedName, new Item[]{match}, count -> getTask.apply(woodItems, count));
            if (requireNetherForNetherStuff && isNether) {
                t.forceDimension(Dimension.NETHER);
            }
            result.add(t);
        }
        return result.toArray(CataloguedResource[]::new);
    }

    private static CataloguedResource[] woodTasks(String baseName, Function<ItemHelper.WoodItems, Item> getMatch, BiFunction<ItemHelper.WoodItems, Integer, ResourceTask> getTask, boolean requireNetherForNetherStuff) {
        return woodTasks(woodItem -> woodItem.prefix + "_" + baseName, getMatch, getTask, requireNetherForNetherStuff);
    }

    private static CataloguedResource[] woodTasks(String baseName, Function<ItemHelper.WoodItems, Item> getMatch, BiFunction<ItemHelper.WoodItems, Integer, ResourceTask> getTask) {
        return woodTasks(baseName, getMatch, getTask, false);
    }

    private static void tools(String toolMaterialName, Item pickaxeItem, Item shovelItem, Item swordItem, Item axeItem, Item hoeItem) {
        crafted(toolMaterialName + "_pickaxe", pickaxeItem);
        crafted(toolMaterialName + "_shovel", shovelItem);
        crafted(toolMaterialName + "_sword", swordItem);
        crafted(toolMaterialName + "_axe", axeItem);
        crafted(toolMaterialName + "_hoe", hoeItem);
    }

    private static void armor(String armorMaterialName, Item helmetItem, Item chestplateItem, Item leggingsItem, Item bootsItem) {
        crafted(armorMaterialName + "_helmet", helmetItem);
        crafted(armorMaterialName + "_chestplate", chestplateItem);
        crafted(armorMaterialName + "_leggings", leggingsItem);
        crafted(armorMaterialName + "_boots", bootsItem);
    }

    private static void alias(String newName, String original) {
        if (!nameToResourceTask.containsKey(original) || !nameToItemMatches.containsKey(original)) {
            Debug.logWarning("Invalid resource: " + original + ". Will not create alias.");
        } else {
            nameToResourceTask.put(newName, nameToResourceTask.get(original));
            nameToItemMatches.put(newName, nameToItemMatches.get(original));
        }
    }

    private static class CataloguedResource {
        private final Item[] _targets;
        private final Function<Integer, ResourceTask> _getResource;

        private boolean _mineIfPresent;
        private boolean _forceDimension = false;
        private Dimension _targetDimension;

        public CataloguedResource(Item[] targets, Function<Integer, ResourceTask> getResource) {
            _targets = targets;
            _getResource = getResource;
        }

        public CataloguedResource mineIfPresent() {
            _mineIfPresent = true;
            return this;
        }

        public CataloguedResource dontMineIfPresent() {
            _mineIfPresent = false;
            return this;
        }

        public CataloguedResource forceDimension(Dimension dimension) {
            _forceDimension = true;
            _targetDimension = dimension;
            return this;
        }

        public CataloguedResource anyDimension() {
            _forceDimension = false;
            return this;
        }

        public ResourceTask getResource(int count) {
            ResourceTask result = _getResource.apply(count);
            if (_mineIfPresent) {
                result = result.mineIfPresent(ItemHelper.itemsToBlocks(_targets));
            }
            if (_forceDimension) {
                result = result.forceDimension(_targetDimension);
            }
            return result;
        }
    }
}
