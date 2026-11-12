package com.exiledradio.cavesandcliffsbackport.terrain.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.cavesandcliffsbackport.terrain.core.BlockCodes.*;
import static com.exiledradio.cavesandcliffsbackport.terrain.core.Decorator.*;

/**
 * VEGETAL_DECORATION's plants as 1.18.2 (with Forge 40.2.21's canSustainPlant, which the reference runs) places
 * them: VegetationPlacements / VegetationFeatures and AquaticPlacements / AquaticFeatures without the trees (random
 * patches with simple blocks and block columns, the noise state providers, bamboo, vines, seagrass, kelp, sea
 * pickles and coral). Plus every plant's canSurvive and updateShape, which trees need when they cut a plant
 * (StructureTemplate.updateShapeAtEdge in TreeFeature).
 *
 * <p>During FEATURES nothing is lit (Region.rawBrightness is 15), so MushroomBlock.canSurvive holds only on its
 * mushroom_grow_block tag (mycelium, podzol).
 */
final class Vegetation {
    private Vegetation() {
    }

    static final int DANDELION = register("dandelion");
    static final int POPPY = register("poppy");
    static final int BLUE_ORCHID = register("blue_orchid");
    static final int ALLIUM = register("allium");
    static final int AZURE_BLUET = register("azure_bluet");
    static final int RED_TULIP = register("red_tulip");
    static final int ORANGE_TULIP = register("orange_tulip");
    static final int WHITE_TULIP = register("white_tulip");
    static final int PINK_TULIP = register("pink_tulip");
    static final int OXEYE_DAISY = register("oxeye_daisy");
    static final int CORNFLOWER = register("cornflower");
    static final int LILY_OF_THE_VALLEY = register("lily_of_the_valley");
    static final int BROWN_MUSHROOM = register("brown_mushroom");
    static final int RED_MUSHROOM = register("red_mushroom");
    static final int GRASS = LushCaves.GRASS_PLANT;
    static final int FERN = register("fern");
    static final int DEAD_BUSH = register("dead_bush");
    static final int PUMPKIN = register("pumpkin");
    static final int MELON = register("melon");
    static final int SWEET_BERRY_BUSH = register("sweet_berry_bush[age=3]");
    static final int LILY_PAD = register("lily_pad");
    static final int CACTUS = register("cactus[age=0]");
    static final int SUGAR_CANE = register("sugar_cane[age=0]");
    static final int SEAGRASS = register("seagrass");
    static final int TALL_SEAGRASS_LOWER = register("tall_seagrass[half=lower]");
    static final int TALL_SEAGRASS_UPPER = register("tall_seagrass[half=upper]");
    static final int KELP_PLANT = register("kelp_plant");
    /** kelp[age], age 20..23 (KelpFeature's nextInt(4) + 20). */
    static final int[] KELP = new int[24];
    /** sea_pickle[pickles=n,waterlogged=true], n 1..4. */
    static final int[] SEA_PICKLE = new int[5];
    static final int BAMBOO_TRUNK = register("bamboo[age=1,leaves=none,stage=0]");
    static final int BAMBOO_FINAL_LARGE = register("bamboo[age=1,leaves=large,stage=1]");
    static final int BAMBOO_TOP_LARGE = register("bamboo[age=1,leaves=large,stage=0]");
    static final int BAMBOO_TOP_SMALL = register("bamboo[age=1,leaves=small,stage=0]");
    /** The tall plants: [lower, upper]. */
    static final int[] TALL_GRASS = {LushCaves.TALL_GRASS_LOWER, LushCaves.TALL_GRASS_UPPER};
    static final int[] LARGE_FERN = tall("large_fern");
    static final int[] SUNFLOWER = tall("sunflower");
    static final int[] LILAC = tall("lilac");
    static final int[] ROSE_BUSH = tall("rose_bush");
    static final int[] PEONY = tall("peony");

    /** Coral types in the tags' order: tube, brain, bubble, fire, horn. */
    private static final String[] CORAL_TYPES = {"tube", "brain", "bubble", "fire", "horn"};
    static final int[] CORAL_BLOCK = new int[5];
    /** BlockTags.CORALS in the tag's order: the five coral plants, then the five fans (waterlogged, the default). */
    static final int[] CORALS = new int[10];
    /** Wall fans [type][facing as Direction ordinal 2..5] (waterlogged). */
    static final int[][] WALL_CORALS = new int[5][6];

    private static int[] tall(String name) {
        return new int[]{register(name + "[half=lower]"), register(name + "[half=upper]")};
    }

    static {
        for (int a = 20; a <= 23; a++) {
            KELP[a] = register("kelp[age=" + a + "]");
        }
        for (int n = 1; n <= 4; n++) {
            SEA_PICKLE[n] = register("sea_pickle[pickles=" + n + ",waterlogged=true]");
        }
        String[] facings = {null, null, "north", "south", "west", "east"};
        for (int t = 0; t < 5; t++) {
            CORAL_BLOCK[t] = register(CORAL_TYPES[t] + "_coral_block");
            CORALS[t] = register(CORAL_TYPES[t] + "_coral[waterlogged=true]");
            CORALS[5 + t] = register(CORAL_TYPES[t] + "_coral_fan[waterlogged=true]");
            for (int d = 2; d < 6; d++) {
                WALL_CORALS[t][d] = register(CORAL_TYPES[t] + "_coral_wall_fan[facing=" + facings[d] + ",waterlogged=true]");
            }
        }
    }

    // ---- what each plant needs to survive ----

    private static final int K_NONE = 0;
    /** BushBlock with the default mayPlaceOn (dirt tag or farmland): flowers, grass, ferns, berries, saplings. */
    private static final int K_BUSH = 1;
    private static final int K_DEAD_BUSH = 2;
    private static final int K_MUSHROOM = 3;
    private static final int K_LILY_PAD = 4;
    private static final int K_SUGAR_CANE = 5;
    private static final int K_CACTUS = 6;
    private static final int K_DOUBLE_LOWER = 7;
    private static final int K_DOUBLE_UPPER = 8;
    private static final int K_SEAGRASS = 9;
    private static final int K_TALL_SEAGRASS_LOWER = 10;
    private static final int K_TALL_SEAGRASS_UPPER = 11;
    private static final int K_KELP = 12;
    private static final int K_KELP_PLANT = 13;
    private static final int K_SEA_PICKLE = 14;
    private static final int K_CORAL_PLANT = 15;
    private static final int K_CORAL_WALL = 16;
    private static final int K_BAMBOO = 17;
    /** AzaleaBlock: clay or the bush's dirt / farmland. */
    private static final int K_AZALEA = 18;
    /** CarpetBlock: anything but air below. */
    private static final int K_CARPET = 19;

    private static int[] kinds = new int[0];
    /** Per double plant half: the code of the other half. */
    private static int[] otherHalf = new int[0];

    private static synchronized void buildKinds() {
        int n = count();
        int[] k = new int[n];
        int[] o = new int[n];
        for (int c = 0; c < n; c++) {
            String name = name(c);
            String s = state(c);
            switch (name) {
                case "dandelion": case "poppy": case "blue_orchid": case "allium": case "azure_bluet": case "red_tulip":
                case "orange_tulip": case "white_tulip": case "pink_tulip": case "oxeye_daisy": case "cornflower":
                case "lily_of_the_valley": case "grass": case "fern": case "sweet_berry_bush": case "oak_sapling":
                case "spruce_sapling": case "birch_sapling": case "jungle_sapling": case "acacia_sapling":
                case "dark_oak_sapling":
                    k[c] = K_BUSH;
                    break;
                case "dead_bush":
                    k[c] = K_DEAD_BUSH;
                    break;
                case "brown_mushroom": case "red_mushroom":
                    k[c] = K_MUSHROOM;
                    break;
                case "lily_pad":
                    k[c] = K_LILY_PAD;
                    break;
                case "sugar_cane":
                    k[c] = K_SUGAR_CANE;
                    break;
                case "cactus":
                    k[c] = K_CACTUS;
                    break;
                case "tall_grass": case "large_fern": case "sunflower": case "lilac": case "rose_bush": case "peony":
                    k[c] = s.contains("half=upper") ? K_DOUBLE_UPPER : K_DOUBLE_LOWER;
                    o[c] = byState(s.contains("half=upper") ? s.replace("half=upper", "half=lower") : s.replace("half=lower", "half=upper"));
                    break;
                case "seagrass":
                    k[c] = K_SEAGRASS;
                    break;
                case "tall_seagrass":
                    k[c] = s.contains("half=upper") ? K_TALL_SEAGRASS_UPPER : K_TALL_SEAGRASS_LOWER;
                    o[c] = s.contains("half=upper") ? TALL_SEAGRASS_LOWER : TALL_SEAGRASS_UPPER;
                    break;
                case "kelp":
                    k[c] = K_KELP;
                    break;
                case "kelp_plant":
                    k[c] = K_KELP_PLANT;
                    break;
                case "sea_pickle":
                    k[c] = K_SEA_PICKLE;
                    break;
                case "bamboo":
                    k[c] = K_BAMBOO;
                    break;
                case "azalea": case "flowering_azalea":
                    k[c] = K_AZALEA;
                    break;
                case "moss_carpet":
                    k[c] = K_CARPET;
                    break;
                default:
                    if (name.endsWith("_coral_wall_fan")) {
                        k[c] = K_CORAL_WALL;
                    } else if (name.endsWith("_coral") || name.endsWith("_coral_fan")) {
                        k[c] = K_CORAL_PLANT;
                    }
            }
        }
        otherHalf = o;
        kinds = k;
    }

    private static int kind(int code) {
        int[] k = kinds;
        if (code >= k.length) {
            buildKinds();
            k = kinds;
        }
        return k[code];
    }

    static boolean isWaterFluid(int code) {
        return (flags(code) & F_WATER) != 0;
    }

    /** A water source fluid state (Fluids.WATER, not flowing): water level 0, waterlogged blocks, water plants. */
    private static boolean sourceWater(int code) {
        return code == WATER || (code != WATER && isWaterFluid(code) && !name(code).equals("water"));
    }

    static boolean terracotta(int code) {
        return name(code).endsWith("terracotta") && !name(code).endsWith("glazed_terracotta");
    }

    /** BlockState.canSurvive at x, y, z for the plants (Forge's canSustainPlant); true for anything else. */
    static boolean canSurvive(Region level, int code, int x, int y, int z) {
        switch (kind(code)) {
            case K_BUSH:
            case K_DOUBLE_LOWER: {
                int below = level.get(x, y - 1, z);
                return LushCaves.dirt(below) || name(below).equals("farmland");
            }
            case K_DOUBLE_UPPER: {
                // Forge: the state is this block (not the pre-check), so the lower half must be there.
                int below = level.get(x, y - 1, z);
                return below == otherHalf[code];
            }
            case K_AZALEA: {
                int below = level.get(x, y - 1, z);
                return below == CLAY || LushCaves.dirt(below) || name(below).equals("farmland");
            }
            case K_CARPET:
                return !isAir(level.get(x, y - 1, z));
            case K_DEAD_BUSH: {
                int below = level.get(x, y - 1, z);
                return below == SAND || below == RED_SAND || terracotta(below) || LushCaves.dirt(below);
            }
            case K_MUSHROOM: {
                // mushroom_grow_block; otherwise raw brightness (15 while nothing is lit) must be below 13.
                int below = level.get(x, y - 1, z);
                return sameBlock(below, MYCELIUM) || sameBlock(below, PODZOL);
            }
            case K_LILY_PAD: {
                int below = level.get(x, y - 1, z);
                // canSustainPlant: BushBlock.mayPlaceOn (source water fluid or ice below, no fluid here), else WATER type
                // (Material.WATER below: water at any level, bubble column).
                String n = name(below);
                if (n.equals("water") || n.equals("bubble_column")) {
                    return true;
                }
                return (sourceWater(below) || n.equals("ice") || n.equals("frosted_ice")) && (flags(level.get(x, y, z)) & F_FLUID) == 0;
            }
            case K_SUGAR_CANE: {
                int below = level.get(x, y - 1, z);
                if (sameBlock(below, SUGAR_CANE)) {
                    return true;
                }
                if (sameBlock(below, GRASS_BLOCK) || LushCaves.dirt(below) || below == SAND || below == RED_SAND) {
                    for (int d = 2; d < 6; d++) {
                        int s = level.get(x + LushCaves.DX[d], y - 1, z + LushCaves.DZ[d]);
                        if (isWaterFluid(s) || name(s).equals("frosted_ice")) {
                            return true;
                        }
                    }
                }
                return false;
            }
            case K_CACTUS: {
                for (int d = 2; d < 6; d++) {
                    int s = level.get(x + LushCaves.DX[d], y, z + LushCaves.DZ[d]);
                    if (solid(s) || (flags(s) & F_FLUID) != 0 && !isWaterFluid(s)) {
                        return false;
                    }
                }
                int below = level.get(x, y - 1, z);
                return (sameBlock(below, CACTUS) || below == SAND || below == RED_SAND) && !liquid(level.get(x, y + 1, z));
            }
            case K_SEAGRASS:
            case K_TALL_SEAGRASS_LOWER: {
                int below = level.get(x, y - 1, z);
                boolean ok = sturdyFace(below, 1) && below != MAGMA_BLOCK
                    || sameBlock(below, GRASS_BLOCK) || LushCaves.dirt(below) || name(below).equals("farmland");
                if (kind(code) == K_SEAGRASS) {
                    return ok;
                }
                return ok && sourceWater(level.get(x, y, z));
            }
            case K_TALL_SEAGRASS_UPPER:
                return level.get(x, y - 1, z) == TALL_SEAGRASS_LOWER;
            case K_KELP:
            case K_KELP_PLANT: {
                int below = level.get(x, y - 1, z);
                if (below == MAGMA_BLOCK) {
                    return false;
                }
                int kb = kind(below);
                return kb == K_KELP || kb == K_KELP_PLANT || sturdyFace(below, 1);
            }
            case K_SEA_PICKLE:
                return faceFull(level.get(x, y - 1, z), 1);
            case K_CORAL_PLANT:
                return sturdyFace(level.get(x, y - 1, z), 1);
            case K_CORAL_WALL: {
                int f = wallFacing(code);
                int bx = x - LushCaves.DX[f];
                int bz = z - LushCaves.DZ[f];
                return sturdyFace(level.get(bx, y, bz), f);
            }
            case K_BAMBOO: {
                int below = level.get(x, y - 1, z);
                String n = name(below);
                return below == SAND || below == RED_SAND || LushCaves.dirt(below) || n.equals("bamboo") || n.equals("bamboo_sapling")
                    || below == GRAVEL;
            }
            default:
                return true;
        }
    }

    private static int wallFacing(int code) {
        String s = state(code);
        return s.contains("facing=north") ? 2 : s.contains("facing=south") ? 3 : s.contains("facing=west") ? 4 : 5;
    }

    /**
     * Block.updateShape(state, direction, neighbor) for the plants (the new state, or the same code). Ticks they
     * schedule are not run during worldgen and do not matter here.
     */
    static int updateShape(Region level, int state, int dir, int neighbor, int x, int y, int z) {
        int k = kind(state);
        switch (k) {
            case K_NONE:
            case K_SUGAR_CANE:
            case K_CACTUS:
            case K_BAMBOO:
                return state;
            case K_DOUBLE_LOWER:
            case K_DOUBLE_UPPER:
            case K_TALL_SEAGRASS_LOWER:
            case K_TALL_SEAGRASS_UPPER: {
                // DoublePlantBlock.updateShape: toward the other half, anything but that half makes air; otherwise
                // BushBlock's survival test (the lower half's DOWN case is the same test).
                boolean lower = k == K_DOUBLE_LOWER || k == K_TALL_SEAGRASS_LOWER;
                if (dir > 1 || lower != (dir == 1) || neighbor == otherHalf[state]) {
                    return canSurvive(level, state, x, y, z) ? state : AIR;
                }
                return AIR;
            }
            case K_KELP:
                if (dir == 1 && (kind(neighbor) == K_KELP || kind(neighbor) == K_KELP_PLANT)) {
                    return KELP_PLANT;
                }
                return state;
            case K_KELP_PLANT:
                if (dir == 1 && kind(neighbor) != K_KELP && kind(neighbor) != K_KELP_PLANT) {
                    // GrowingPlantHeadBlock.getStateForPlacement draws the age from the level's own random.
                    throw new IllegalStateException("kelp plant lost its top at " + x + "," + y + "," + z);
                }
                return state;
            case K_CORAL_PLANT:
                return dir == 0 && !canSurvive(level, state, x, y, z) ? AIR : state;
            case K_CORAL_WALL:
                return (dir ^ 1) == wallFacing(state) && !canSurvive(level, state, x, y, z) ? AIR : state;
            case K_SEAGRASS:
                // BushBlock.updateShape: air when it cannot survive (the water comes back by a fluid tick only).
                return canSurvive(level, state, x, y, z) ? state : AIR;
            default:
                return canSurvive(level, state, x, y, z) ? state : AIR;
        }
    }

    // ---- placements ----

    static List<Placed> placed() {
        int mb = Region.MOTION_BLOCKING;
        int ws = WG_WORLD_SURFACE;
        int top = WG_OCEAN_FLOOR;
        List<Placed> l = new ArrayList<>();
        Feature brownMushroom = patch(96, 7, 3, onlyInAir(simple(BROWN_MUSHROOM)));
        Feature redMushroom = patch(96, 7, 3, onlyInAir(simple(RED_MUSHROOM)));
        Feature grass = patch(32, 7, 3, onlyInAir(simple(GRASS)));
        Feature taigaGrass = patch(32, 7, 3, onlyInAir(weighted(new int[]{GRASS, 1, FERN, 4})));
        Feature deadBush = patch(4, 7, 3, onlyInAir(simple(DEAD_BUSH)));
        Feature flowerDefault = patch(64, 7, 3, onlyInAir(weighted(new int[]{POPPY, 2, DANDELION, 1})));
        Feature sugarCane = patch(20, 4, 0, new Filtered(new SugarCaneSpot(), new BlockColumn(2, 4, SUGAR_CANE)));
        Feature cactus = patch(10, 7, 3, new Filtered(new CactusSpot(), new BlockColumn(1, 3, CACTUS)));
        Feature melon = patch(64, 7, 3, new Filtered((level, x, y, z) -> replaceable(level.get(x, y, z))
            && sameBlock(level.get(x, y - 1, z), GRASS_BLOCK), simple(MELON)));
        Feature berries = patch(96, 7, 3, new Filtered(onGrassInAir(), simple(SWEET_BERRY_BUSH)));
        Feature pumpkin = patch(96, 7, 3, new Filtered(onGrassInAir(), simple(PUMPKIN)));
        Feature grassJungle = grassJungle();
        Feature seagrassShort = new Seagrass(0.3F);

        l.add(new Placed("bamboo_light", new Bamboo(0.0F), RARITY, 4, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("bamboo", new Bamboo(0.2F), CUSTOM, 0, IN_SQUARE, HEIGHTMAP, ws, BIOME).mods(noiseBased(160, 80.0D, 0.3D)));
        l.add(new Placed("vines", new LushCaves.Vines(), COUNT, 127, IN_SQUARE, HEIGHT_UNIFORM, 64, 100, BIOME));
        l.add(new Placed("patch_sunflower", patch(96, 7, 3, onlyInAir(simple(SUNFLOWER[0]))), RARITY, 3, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_pumpkin", pumpkin, RARITY, 300, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_grass_plain", grass, CUSTOM, 0, IN_SQUARE, HEIGHTMAP, ws, BIOME).mods(noiseThreshold(-0.8D, 5, 10)));
        l.add(new Placed("patch_grass_forest", grass, COUNT, 2, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_badlands", grass, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_savanna", grass, COUNT, 20, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_normal", grass, COUNT, 5, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_taiga_2", taigaGrass, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_taiga", taigaGrass, COUNT, 7, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_grass_jungle", grassJungle, COUNT, 25, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_dead_bush_2", deadBush, COUNT, 2, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_dead_bush", deadBush, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_dead_bush_badlands", deadBush, COUNT, 20, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_melon", melon, RARITY, 6, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_melon_sparse", melon, RARITY, 64, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_berry_common", berries, RARITY, 32, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_berry_rare", berries, RARITY, 384, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        l.add(new Placed("patch_waterlily", patch(10, 7, 3, onlyInAir(simple(LILY_PAD))), COUNT, 4, IN_SQUARE, HEIGHTMAP, ws, BIOME));
        Feature tallGrass = patch(96, 7, 3, onlyInAir(simple(TALL_GRASS[0])));
        l.add(new Placed("patch_tall_grass_2", tallGrass, CUSTOM, 0, RARITY, 32, IN_SQUARE, HEIGHTMAP, mb, BIOME)
            .mods(noiseThreshold(-0.8D, 0, 7)));
        l.add(new Placed("patch_tall_grass", tallGrass, RARITY, 5, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_large_fern", patch(96, 7, 3, onlyInAir(simple(LARGE_FERN[0]))), RARITY, 5, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_cactus_desert", cactus, RARITY, 6, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_cactus_decorated", cactus, RARITY, 13, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_sugar_cane_swamp", sugarCane, RARITY, 3, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_sugar_cane_desert", sugarCane, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_sugar_cane_badlands", sugarCane, RARITY, 5, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("patch_sugar_cane", sugarCane, RARITY, 6, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(mushroom("brown_mushroom_normal", brownMushroom, 0, 256));
        l.add(mushroom("red_mushroom_normal", redMushroom, 0, 512));
        l.add(mushroom("brown_mushroom_taiga", brownMushroom, 0, 4));
        l.add(mushroom("red_mushroom_taiga", redMushroom, 0, 256));
        l.add(mushroom("brown_mushroom_old_growth", brownMushroom, 3, 4));
        l.add(mushroom("red_mushroom_old_growth", redMushroom, 0, 171));
        l.add(mushroom("brown_mushroom_swamp", brownMushroom, 2, 0));
        l.add(mushroom("red_mushroom_swamp", redMushroom, 0, 64));
        l.add(new Placed("flower_warm", flowerDefault, RARITY, 16, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("flower_default", flowerDefault, RARITY, 32, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("flower_flower_forest", patch(96, 6, 2, onlyInAir(new SimpleBlock(new ForestFlowers()))),
            COUNT, 3, RARITY, 2, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("flower_swamp", patch(64, 6, 2, onlyInAir(simple(BLUE_ORCHID))), RARITY, 32, IN_SQUARE, HEIGHTMAP, mb, BIOME));
        l.add(new Placed("flower_plains", patch(64, 6, 2, onlyInAir(new SimpleBlock(new PlainFlowers()))),
            CUSTOM, 0, RARITY, 32, IN_SQUARE, HEIGHTMAP, mb, BIOME).mods(noiseThreshold(-0.8D, 15, 4)));
        l.add(new Placed("flower_meadow", patch(96, 6, 2, onlyInAir(new SimpleBlock(new MeadowFlowers()))), IN_SQUARE, HEIGHTMAP, mb, BIOME));
        Feature forestFlowers = new SimpleRandom(
            patch(96, 7, 3, onlyInAir(simple(LILAC[0]))),
            patch(96, 7, 3, onlyInAir(simple(ROSE_BUSH[0]))),
            patch(96, 7, 3, onlyInAir(simple(PEONY[0]))),
            patch(96, 7, 3, onlyInAir(simple(LILY_OF_THE_VALLEY))));
        l.add(new Placed("flower_forest_flowers", forestFlowers, RARITY, 7, IN_SQUARE, HEIGHTMAP, mb, CUSTOM, 0, BIOME)
            .mods(clampedCount(-1, 3, 0, 3)));
        l.add(new Placed("forest_flowers", forestFlowers, RARITY, 7, IN_SQUARE, HEIGHTMAP, mb, CUSTOM, 0, BIOME)
            .mods(clampedCount(-3, 1, 0, 1)));

        // AquaticPlacements
        l.add(seagrass("seagrass_warm", seagrassShort, 80));
        l.add(seagrass("seagrass_normal", seagrassShort, 48));
        l.add(seagrass("seagrass_cold", seagrassShort, 32));
        l.add(seagrass("seagrass_river", new Seagrass(0.4F), 48));
        l.add(seagrass("seagrass_swamp", new Seagrass(0.6F), 64));
        Feature seagrassTall = new Seagrass(0.8F);
        l.add(seagrass("seagrass_deep_warm", seagrassTall, 80));
        l.add(seagrass("seagrass_deep", seagrassTall, 48));
        l.add(seagrass("seagrass_deep_cold", seagrassTall, 40));
        // CarvingMaskPlacement(LIQUID): no overworld biome has a liquid carver, so the mask is empty.
        l.add(new Placed("seagrass_simple", (level, random, x, y, z) -> false, CUSTOM, 0).mods((level, random, next, x, y, z) -> {
        }));
        l.add(new Placed("sea_pickle", new SeaPickle(20), RARITY, 16, IN_SQUARE, HEIGHTMAP, top, BIOME));
        l.add(new Placed("kelp_cold", new Kelp(), CUSTOM, 0, IN_SQUARE, HEIGHTMAP, top, BIOME).mods(noiseBased(120, 80.0D, 0.0D)));
        l.add(new Placed("kelp_warm", new Kelp(), CUSTOM, 0, IN_SQUARE, HEIGHTMAP, top, BIOME).mods(noiseBased(80, 80.0D, 0.0D)));
        l.add(new Placed("warm_ocean_vegetation", new SimpleRandom(new CoralTree(), new CoralClaw(), new CoralMushroom()),
            CUSTOM, 0, IN_SQUARE, HEIGHTMAP, top, BIOME).mods(noiseBased(20, 400.0D, 0.0D)));
        return l;
    }

    /** patch_grass_jungle (also bamboo_vegetation's default). */
    static Feature grassJungle() {
        return patch(32, 7, 3, new Filtered((level, x, y, z) -> level.get(x, y, z) == AIR
            && !sameBlock(level.get(x, y - 1, z), PODZOL), weighted(new int[]{GRASS, 3, FERN, 1})));
    }

    private static Placed mushroom(String name, Feature f, int count, int rarity) {
        List<Integer> ops = new ArrayList<>();
        if (count != 0) {
            ops.add(COUNT);
            ops.add(count);
        }
        if (rarity != 0) {
            ops.add(RARITY);
            ops.add(rarity);
        }
        ops.addAll(Arrays.asList(IN_SQUARE, HEIGHTMAP, Region.MOTION_BLOCKING, BIOME));
        int[] a = new int[ops.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = ops.get(i);
        }
        return new Placed(name, f, a);
    }

    private static Placed seagrass(String name, Feature f, int count) {
        return new Placed(name, f, IN_SQUARE, HEIGHTMAP, WG_OCEAN_FLOOR, COUNT, count, BIOME);
    }

    // ---- placement modifiers ----

    /** NoiseThresholdCountPlacement: Biome.BIOME_INFO_NOISE at x / 200, z / 200. */
    static Modifier noiseThreshold(double level, int below, int above) {
        return (region, random, next, x, y, z) -> {
            double d0 = Biomes.BIOME_INFO_NOISE.getValue((double) x / 200.0D, (double) z / 200.0D);
            int n = d0 < level ? below : above;
            for (int i = 0; i < n; i++) {
                next.at(x, y, z);
            }
        };
    }

    /** NoiseBasedCountPlacement. */
    static Modifier noiseBased(int ratio, double factor, double offset) {
        return (region, random, next, x, y, z) -> {
            double d0 = Biomes.BIOME_INFO_NOISE.getValue((double) x / factor, (double) z / factor);
            int n = (int) Math.ceil((d0 + offset) * (double) ratio);
            for (int i = 0; i < n; i++) {
                next.at(x, y, z);
            }
        };
    }

    /** CountPlacement over ClampedInt(UniformInt(min, max), lo, hi). */
    static Modifier clampedCount(int min, int max, int lo, int hi) {
        return (region, random, next, x, y, z) -> {
            int n = McMath.clamp(random.nextInt(max - min + 1) + min, lo, hi);
            for (int i = 0; i < n; i++) {
                next.at(x, y, z);
            }
        };
    }

    // ---- block predicates and features ----

    interface Predicate {
        boolean test(Region level, int x, int y, int z);
    }

    static Predicate onGrassInAir() {
        return (level, x, y, z) -> level.get(x, y, z) == AIR && sameBlock(level.get(x, y - 1, z), GRASS_BLOCK);
    }

    /** PlacementUtils.filtered / inlinePlaced with a BlockPredicateFilter: the test, then the feature (ensureCanWrite). */
    static final class Filtered implements Feature {
        final Predicate predicate;
        final Feature feature;

        Filtered(Predicate predicate, Feature feature) {
            this.predicate = predicate;
            this.feature = feature;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            return predicate.test(level, x, y, z) && LushCaves.placeInline(feature, level, random, x, y, z);
        }
    }

    /** PlacementUtils.onlyWhenEmpty: BlockPredicate.matchesBlock(AIR) (plain air only). */
    static Feature onlyInAir(Feature f) {
        return new Filtered((level, x, y, z) -> level.get(x, y, z) == AIR, f);
    }

    /** RandomPatchFeature (also Feature.FLOWER and NO_BONEMEAL_FLOWER). */
    static Feature patch(int tries, int xzSpread, int ySpread, Feature inner) {
        int j = xzSpread + 1;
        int k = ySpread + 1;
        return (level, random, x, y, z) -> {
            int placed = 0;
            for (int l = 0; l < tries; ++l) {
                int dx = random.nextInt(j) - random.nextInt(j);
                int dy = random.nextInt(k) - random.nextInt(k);
                int dz = random.nextInt(j) - random.nextInt(j);
                if (inner.place(level, random, x + dx, y + dy, z + dz)) {
                    ++placed;
                }
            }
            return placed > 0;
        };
    }

    /** SimpleRandomSelectorFeature: nextInt(size), then that inline placed feature. */
    static final class SimpleRandom implements Feature {
        final Feature[] features;

        SimpleRandom(Feature... features) {
            this.features = features;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int i = random.nextInt(features.length);
            return LushCaves.placeInline(features[i], level, random, x, y, z);
        }
    }

    interface StateProvider {
        int state(XoroRandom random, int x, int y, int z);
    }

    static Feature simple(int code) {
        return new SimpleBlock((random, x, y, z) -> code);
    }

    /** WeightedStateProvider: (code, weight) pairs, nextInt(total). */
    static Feature weighted(int[] entries) {
        int total = 0;
        for (int i = 1; i < entries.length; i += 2) {
            total += entries[i];
        }
        int t = total;
        return new SimpleBlock((random, x, y, z) -> {
            int i = random.nextInt(t);
            for (int e = 0; e < entries.length; e += 2) {
                i -= entries[e + 1];
                if (i < 0) {
                    return entries[e];
                }
            }
            throw new IllegalStateException();
        });
    }

    /** SimpleBlockFeature: canSurvive, then a double plant (both halves, the upper in air only) or the block. */
    static final class SimpleBlock implements Feature {
        final StateProvider provider;

        SimpleBlock(StateProvider provider) {
            this.provider = provider;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int state = provider.state(random, x, y, z);
            if (!canSurvive(level, state, x, y, z)) {
                return false;
            }
            int k = kind(state);
            if (k == K_DOUBLE_LOWER) {
                if (!isAir(level.get(x, y + 1, z))) {
                    return false;
                }
                LushCaves.placeDouble(level, x, y, z, state, otherHalf[state]);
            } else {
                level.set(x, y, z, state);
            }
            return true;
        }
    }

    // ---- noise state providers (seed 2345, NormalNoise over a legacy WorldgenRandom) ----

    private static final DoubleNoise FLOWER_NOISE = new DoubleNoise(new LegacyRandom(2345L), 0, 1.0D);
    private static final DoubleNoise MEADOW_NOISE = new DoubleNoise(new LegacyRandom(2345L), -3, 1.0D);
    private static final DoubleNoise MEADOW_SLOW_NOISE = new DoubleNoise(new LegacyRandom(2345L), -10, 1.0D);

    /** NoiseProvider.getRandomState(list, noise). */
    private static int noiseState(int[] states, double noise) {
        double d0 = McMath.clamp((1.0D + noise) / 2.0D, 0.0D, 0.9999D);
        return states[(int) (d0 * (double) states.length)];
    }

    /** flower_flower_forest: NoiseProvider(2345, (0, [1]), 0.020833334, 11 flowers). */
    static final class ForestFlowers implements StateProvider {
        private static final int[] STATES = {DANDELION, POPPY, ALLIUM, AZURE_BLUET, RED_TULIP, ORANGE_TULIP, WHITE_TULIP,
            PINK_TULIP, OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY};

        @Override
        public int state(XoroRandom random, int x, int y, int z) {
            double s = (double) 0.020833334F;
            return noiseState(STATES, FLOWER_NOISE.getValue((double) x * s, (double) y * s, (double) z * s));
        }
    }

    /** flower_plain: NoiseThresholdProvider(2345, (0, [1]), 0.005, -0.8, 1/3, dandelion, tulips, others). */
    static final class PlainFlowers implements StateProvider {
        private static final int[] LOW = {ORANGE_TULIP, RED_TULIP, PINK_TULIP, WHITE_TULIP};
        private static final int[] HIGH = {POPPY, AZURE_BLUET, OXEYE_DAISY, CORNFLOWER};

        @Override
        public int state(XoroRandom random, int x, int y, int z) {
            double s = (double) 0.005F;
            double d0 = FLOWER_NOISE.getValue((double) x * s, (double) y * s, (double) z * s);
            if (d0 < (double) -0.8F) {
                return LOW[random.nextInt(LOW.length)];
            }
            return random.nextFloat() < 0.33333334F ? HIGH[random.nextInt(HIGH.length)] : DANDELION;
        }
    }

    /** flower_meadow: DualNoiseProvider(variety 1..3, slow (-10, [1]) at 1.0, 2345, (-3, [1]) at 1.0, 8 plants). */
    static final class MeadowFlowers implements StateProvider {
        private static final int[] STATES = {TALL_GRASS[0], ALLIUM, POPPY, AZURE_BLUET, DANDELION, CORNFLOWER, OXEYE_DAISY, GRASS};

        private static double slow(int x, int y, int z) {
            return MEADOW_SLOW_NOISE.getValue((double) ((float) x * 1.0F), (double) ((float) y * 1.0F), (double) ((float) z * 1.0F));
        }

        @Override
        public int state(XoroRandom random, int x, int y, int z) {
            double d0 = slow(x, y, z);
            int i = (int) McMath.clampedMap(d0, -1.0D, 1.0D, 1.0D, (double) (3 + 1));
            int[] list = new int[i];
            for (int j = 0; j < i; ++j) {
                list[j] = noiseState(STATES, slow(x + j * 54545, y, z + j * 34234));
            }
            double s = (double) 1.0F;
            return noiseState(list, MEADOW_NOISE.getValue((double) x * s, (double) y * s, (double) z * s));
        }
    }

    // ---- block columns ----

    /** BlockColumnConfiguration.simple(BiasedToBottomInt(min, max), state): up, through plain air only. */
    static final class BlockColumn implements Feature {
        final int min;
        final int max;
        final int state;

        BlockColumn(int min, int max, int state) {
            this.min = min;
            this.max = max;
            this.state = state;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int h = min + random.nextInt(random.nextInt(max - min + 1) + 1);
            int[] aint = {h};
            return LushCaves.placeColumn(level, random, x, y, z, aint, 1, false, c -> c == AIR, (r, layer) -> state);
        }
    }

    /** patch_cactus's filter: air and the cactus would survive. */
    static final class CactusSpot implements Predicate {
        @Override
        public boolean test(Region level, int x, int y, int z) {
            return level.get(x, y, z) == AIR && canSurvive(level, CACTUS, x, y, z);
        }
    }

    /** patch_sugar_cane's filter: air, the cane would survive, and water (or flowing water) next to the block below. */
    static final class SugarCaneSpot implements Predicate {
        @Override
        public boolean test(Region level, int x, int y, int z) {
            if (level.get(x, y, z) != AIR || !canSurvive(level, SUGAR_CANE, x, y, z)) {
                return false;
            }
            return waterFluid(level.get(x + 1, y - 1, z)) || waterFluid(level.get(x - 1, y - 1, z))
                || waterFluid(level.get(x, y - 1, z + 1)) || waterFluid(level.get(x, y - 1, z - 1));
        }

        /** matchesFluids(WATER, FLOWING_WATER): the fluid state's type. */
        private static boolean waterFluid(int code) {
            return isWaterFluid(code);
        }
    }

    // ---- BambooFeature ----

    static final class Bamboo implements Feature {
        final float probability;

        Bamboo(float probability) {
            this.probability = probability;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            if (!isAir(level.get(x, y, z))) {
                return false;
            }
            if (canSurvive(level, BAMBOO_TRUNK, x, y, z)) {
                int j = random.nextInt(12) + 5;
                if (random.nextFloat() < probability) {
                    int k = random.nextInt(4) + 1;
                    for (int l = x - k; l <= x + k; ++l) {
                        for (int i1 = z - k; i1 <= z + k; ++i1) {
                            int j1 = l - x;
                            int k1 = i1 - z;
                            if (j1 * j1 + k1 * k1 <= k * k) {
                                int h = level.height(Region.WORLD_SURFACE, l, i1) - 1;
                                if (LushCaves.dirt(level.get(l, h, i1))) {
                                    level.set(l, h, i1, PODZOL);
                                }
                            }
                        }
                    }
                }
                int py = y;
                for (int l1 = 0; l1 < j && isAir(level.get(x, py, z)); ++l1) {
                    level.set(x, py, z, BAMBOO_TRUNK);
                    ++py;
                }
                if (py - y >= 3) {
                    level.set(x, py, z, BAMBOO_FINAL_LARGE);
                    level.set(x, py - 1, z, BAMBOO_TOP_LARGE);
                    level.set(x, py - 2, z, BAMBOO_TOP_SMALL);
                }
            }
            return true;
        }
    }

    // ---- aquatic ----

    static final class Seagrass implements Feature {
        final float probability;

        Seagrass(float probability) {
            this.probability = probability;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            boolean flag = false;
            int i = random.nextInt(8) - random.nextInt(8);
            int j = random.nextInt(8) - random.nextInt(8);
            int bx = x + i;
            int bz = z + j;
            int k = level.height(Region.OCEAN_FLOOR, bx, bz);
            if (level.get(bx, k, bz) == WATER) {
                boolean tall = random.nextDouble() < (double) probability;
                int state = tall ? TALL_SEAGRASS_LOWER : SEAGRASS;
                if (canSurvive(level, state, bx, k, bz)) {
                    if (tall) {
                        if (level.get(bx, k + 1, bz) == WATER) {
                            level.set(bx, k, bz, state);
                            level.set(bx, k + 1, bz, TALL_SEAGRASS_UPPER);
                        }
                    } else {
                        level.set(bx, k, bz, state);
                    }
                    flag = true;
                }
            }
            return flag;
        }
    }

    static final class Kelp implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int i = 0;
            int j = level.height(Region.OCEAN_FLOOR, x, z);
            int py = j;
            if (level.get(x, py, z) == WATER) {
                int k = 1 + random.nextInt(10);
                for (int l = 0; l <= k; ++l) {
                    if (level.get(x, py, z) == WATER && level.get(x, py + 1, z) == WATER && canSurvive(level, KELP_PLANT, x, py, z)) {
                        if (l == k) {
                            level.set(x, py, z, KELP[random.nextInt(4) + 20]);
                            ++i;
                        } else {
                            level.set(x, py, z, KELP_PLANT);
                        }
                    } else if (l > 0) {
                        int by = py - 1;
                        if (canSurvive(level, KELP[20], x, by, z) && !sameBlock(level.get(x, by - 1, z), KELP[20])) {
                            level.set(x, by, z, KELP[random.nextInt(4) + 20]);
                            ++i;
                        }
                        break;
                    }
                    ++py;
                }
            }
            return i > 0;
        }
    }

    static final class SeaPickle implements Feature {
        final int count;

        SeaPickle(int count) {
            this.count = count;
        }

        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int i = 0;
            for (int k = 0; k < count; ++k) {
                int l = random.nextInt(8) - random.nextInt(8);
                int i1 = random.nextInt(8) - random.nextInt(8);
                int bx = x + l;
                int bz = z + i1;
                int j1 = level.height(Region.OCEAN_FLOOR, bx, bz);
                int state = SEA_PICKLE[random.nextInt(4) + 1];
                if (level.get(bx, j1, bz) == WATER && canSurvive(level, state, bx, j1, bz)) {
                    level.set(bx, j1, bz, state);
                    ++i;
                }
            }
            return i > 0;
        }
    }

    // ---- coral (CoralFeature and its three shapes) ----

    /** Direction.Plane.HORIZONTAL: north, east, south, west, as Direction ordinals. */
    private static final int[] HORIZONTAL = {2, 5, 3, 4};

    private static int clockWise(int d) {
        switch (d) {
            case 2: return 5;
            case 5: return 3;
            case 3: return 4;
            default: return 2;
        }
    }

    private static int counterClockWise(int d) {
        switch (d) {
            case 2: return 4;
            case 4: return 3;
            case 3: return 5;
            default: return 2;
        }
    }

    private static boolean corals(int code) {
        for (int c : CORALS) {
            if (c == code) {
                return true;
            }
        }
        return false;
    }

    abstract static class Coral implements Feature {
        @Override
        public boolean place(Region level, XoroRandom random, int x, int y, int z) {
            int block = CORAL_BLOCK[random.nextInt(5)];
            return placeFeature(level, random, x, y, z, block);
        }

        abstract boolean placeFeature(Region level, XoroRandom random, int x, int y, int z, int block);

        static boolean placeCoralBlock(Region level, XoroRandom random, int x, int y, int z, int block) {
            int state = level.get(x, y, z);
            if ((state == WATER || corals(state)) && level.get(x, y + 1, z) == WATER) {
                level.set(x, y, z, block);
                if (random.nextFloat() < 0.25F) {
                    level.set(x, y + 1, z, CORALS[random.nextInt(CORALS.length)]);
                } else if (random.nextFloat() < 0.05F) {
                    level.set(x, y + 1, z, SEA_PICKLE[random.nextInt(4) + 1]);
                }
                for (int d : HORIZONTAL) {
                    if (random.nextFloat() < 0.2F) {
                        int nx = x + LushCaves.DX[d];
                        int nz = z + LushCaves.DZ[d];
                        if (level.get(nx, y, nz) == WATER) {
                            level.set(nx, y, nz, WALL_CORALS[random.nextInt(5)][d]);
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    /** Collections.shuffle(list, random) over the first n of an int array. */
    private static void shuffle(int[] a, XoroRandom random) {
        for (int i = a.length; i > 1; i--) {
            int j = random.nextInt(i);
            int t = a[i - 1];
            a[i - 1] = a[j];
            a[j] = t;
        }
    }

    static final class CoralTree extends Coral {
        @Override
        boolean placeFeature(Region level, XoroRandom random, int x, int y, int z, int block) {
            int py = y;
            int i = random.nextInt(3) + 1;
            for (int j = 0; j < i; ++j) {
                if (!placeCoralBlock(level, random, x, py, z, block)) {
                    return true;
                }
                ++py;
            }
            int k = random.nextInt(3) + 2;
            int[] list = HORIZONTAL.clone();
            shuffle(list, random);
            for (int n = 0; n < k; n++) {
                int d = list[n];
                int mx = x + LushCaves.DX[d];
                int my = py;
                int mz = z + LushCaves.DZ[d];
                int l = random.nextInt(5) + 2;
                int i1 = 0;
                for (int j1 = 0; j1 < l && placeCoralBlock(level, random, mx, my, mz, block); ++j1) {
                    ++i1;
                    ++my;
                    if (j1 == 0 || i1 >= 2 && random.nextFloat() < 0.25F) {
                        mx += LushCaves.DX[d];
                        mz += LushCaves.DZ[d];
                        i1 = 0;
                    }
                }
            }
            return true;
        }
    }

    static final class CoralClaw extends Coral {
        @Override
        boolean placeFeature(Region level, XoroRandom random, int x, int y, int z, int block) {
            if (!placeCoralBlock(level, random, x, y, z, block)) {
                return false;
            }
            int direction = HORIZONTAL[random.nextInt(4)];
            int i = random.nextInt(2) + 2;
            int[] list = {direction, clockWise(direction), counterClockWise(direction)};
            shuffle(list, random);
            for (int n = 0; n < i; n++) {
                int d1 = list[n];
                int mx = x;
                int my = y;
                int mz = z;
                int j = random.nextInt(2) + 1;
                mx += LushCaves.DX[d1];
                mz += LushCaves.DZ[d1];
                int k;
                int d2;
                if (d1 == direction) {
                    d2 = direction;
                    k = random.nextInt(3) + 2;
                } else {
                    ++my;
                    d2 = random.nextInt(2) == 0 ? d1 : 1;
                    k = random.nextInt(3) + 3;
                }
                for (int l = 0; l < j && placeCoralBlock(level, random, mx, my, mz, block); ++l) {
                    mx += LushCaves.DX[d2];
                    my += LushCaves.DY[d2];
                    mz += LushCaves.DZ[d2];
                }
                mx -= LushCaves.DX[d2];
                my -= LushCaves.DY[d2];
                mz -= LushCaves.DZ[d2];
                ++my;
                for (int i1 = 0; i1 < k; ++i1) {
                    mx += LushCaves.DX[direction];
                    mz += LushCaves.DZ[direction];
                    if (!placeCoralBlock(level, random, mx, my, mz, block)) {
                        break;
                    }
                    if (random.nextFloat() < 0.25F) {
                        ++my;
                    }
                }
            }
            return true;
        }
    }

    static final class CoralMushroom extends Coral {
        @Override
        boolean placeFeature(Region level, XoroRandom random, int x, int y, int z, int block) {
            int i = random.nextInt(3) + 3;
            int j = random.nextInt(3) + 3;
            int k = random.nextInt(3) + 3;
            int l = random.nextInt(3) + 1;
            for (int i1 = 0; i1 <= j; ++i1) {
                for (int j1 = 0; j1 <= i; ++j1) {
                    for (int k1 = 0; k1 <= k; ++k1) {
                        int px = i1 + x;
                        int py = j1 + y - l;
                        int pz = k1 + z;
                        if ((i1 != 0 && i1 != j || j1 != 0 && j1 != i) && (k1 != 0 && k1 != k || j1 != 0 && j1 != i)
                                && (i1 != 0 && i1 != j || k1 != 0 && k1 != k) && (i1 == 0 || i1 == j || j1 == 0 || j1 == i || k1 == 0 || k1 == k)
                                && !(random.nextFloat() < 0.1F)) {
                            placeCoralBlock(level, random, px, py, pz, block);
                        }
                    }
                }
            }
            return true;
        }
    }
}
