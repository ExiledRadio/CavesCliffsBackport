package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;
import com.exiledradio.cavesandcliffsbackport.height.compat.BuildCraftHooks;
import com.exiledradio.cavesandcliffsbackport.height.compat.KagicHooks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;

/**
 * Stream O3 ({@code docs/streams/moddedores.md}): BuildCraft's quarry and water springs and KAGIC's injector in a world
 * with 1.18's height, and the same code in a world of 0-255 (the Nether) as each mod wrote it.
 *
 * <pre>
 *   sh tools/height/selftest.sh modcompat   (level-type cavesandcliffs on a fresh world; BuildCraft 8.0.0 and KAGIC
 *                                            X11p13, dev-named with tools/height/run.sh DeobfJar, in eclipse/mods)
 * </pre>
 * Without either mod (the whole suite) it checks the hooks alone. Everything about the mods goes through reflection:
 * their jars are not on the compile class path.
 */
public final class ModCompatSelfTest implements SelfTest {

    /** Far from every other test's areas. */
    private static final int X = 52000;
    private static final int Z = 52000;

    @Override
    public String name() {
        return "modcompat";
    }

    @Override
    public void run(Check check) throws Exception {
        WorldServer over = check.world;
        WorldServer nether = over.getMinecraftServer().getWorld(-1);
        String asked = System.getProperty(CavesAndCliffsBackport.MOD_ID + ".selftest", "");
        boolean demanded = Arrays.asList(asked.split(",")).contains(name());
        boolean extended = Heights.isExtended(over);
        check.ok(extended || !demanded, "the overworld has no extended height (run tools/height/selftest.sh modcompat)");
        check.ok(nether != null && !Heights.isTall(nether), "the Nether is a 0-255 world");

        hooks(check, nether, false);
        if (extended) {
            hooks(check, over, true);
        }
        boolean bc = Loader.isModLoaded("buildcraftbuilders");
        boolean kagic = Loader.isModLoaded("kagic");
        CavesAndCliffsBackport.LOGGER.info("SELFTEST modcompat: BuildCraft {}, KAGIC {}, overworld {}.",
                bc ? "present" : "absent", kagic ? "present" : "absent", extended ? "extended" : "0-255");
        if (bc) {
            buildCraft(check, over, nether, extended);
        }
        if (kagic) {
            kagic(check, over, nether, extended);
        }
        for (String line : PatchReport.compatLines()) {
            CavesAndCliffsBackport.LOGGER.info("SELFTEST modcompat: {}", line);
        }
        if (bc) {
            for (String label : new String[] {"BuildCraftQuarry", "BuildCraftSpringPopulate"}) {
                check.ok(PatchReport.compatPatched(label), label + " patched");
            }
        }
        if (kagic) {
            for (String label : new String[] {"KagicInjector", "KagicInjectorResult", "KagicMeteorRubyTick",
                    "KagicMeteorRubyCommand"}) {
                check.ok(PatchReport.compatPatched(label), label + " patched");
            }
        }
        if (!bc && !kagic) {
            check.eq(Arrays.asList(), PatchReport.compatLines(), "mod-compat sites without the mods");
        }
    }

    // ------------------------------------------------------------------------------------------
    // The hooks: each mod's own value in a 0-255 world, 1.18's in an extended one.
    // ------------------------------------------------------------------------------------------

    private static void hooks(Check check, World world, boolean tall) {
        String w = tall ? " (extended)" : " (0-255)";
        TileEntity tile = new TileEntity() {
        };
        tile.setWorld(world);
        int min = tall ? -64 : 0;
        check.eq(min, BuildCraftHooks.quarryFloor(tile), "quarry floor" + w);
        check.eq(tall ? new BlockPos(1, -64, 1) : null,
                BuildCraftHooks.savedQuarryFloor(tile, new BlockPos(11, 74, 11), new BlockPos(1, 0, 1), 512),
                "saved quarry box at 0, frame top 74, depth 512" + w);
        check.eq(tall ? new BlockPos(1, -21, 1) : null,
                BuildCraftHooks.savedQuarryFloor(tile, new BlockPos(11, 40, 11), new BlockPos(1, 0, 1), 60),
                "saved quarry box at 0, frame top 40, depth 60" + w);
        check.eq(null, BuildCraftHooks.savedQuarryFloor(tile, new BlockPos(11, 74, 11), new BlockPos(1, 0, 1), 32),
                "saved quarry box whose depth ends at 41" + w);
        check.eq(null, BuildCraftHooks.savedQuarryFloor(tile, new BlockPos(11, 74, 11), new BlockPos(1, 5, 1), 512),
                "saved quarry box at 5" + w);
        check.eq(null, BuildCraftHooks.savedQuarryFloor(tile, null, null, 512), "quarry with no box" + w);
        Object iterator = new Object();
        check.eq(iterator, BuildCraftHooks.quarryIterator(iterator, null), "quarry iterator kept" + w);
        check.eq(null, BuildCraftHooks.quarryIterator(iterator, new BlockPos(0, -64, 0)), "quarry iterator dropped" + w);
        for (int layer = 0; layer < 5; layer++) {
            check.eq(min + layer, BuildCraftHooks.springY(layer, world), "spring layer " + layer + w);
        }

        check.eq(min + 5, KagicHooks.injectorFloor(world), "injector floor" + w);
        check.eq(tall ? 1024 : 256, KagicHooks.seedTaken(world), "injector seed-taken offset" + w);
        for (int y : new int[] {6, 7, 40, 255}) {
            check.eq(y, KagicHooks.depthY(y, world), "depth y " + y + w);
        }
        if (tall) {
            for (int y : new int[] {5, 0, -1, -30, -59}) {
                check.eq(6, KagicHooks.depthY(y, world), "depth y " + y + w);
            }
        } else {
            check.eq(3, KagicHooks.depthY(3, world), "depth y 3" + w);
        }
        EntityArmorStand ruby = new EntityArmorStand(world);
        check.eq(tall ? 320.0 : 256.0, KagicHooks.meteorY(ruby), "meteor ruby y" + w);
    }

    // ------------------------------------------------------------------------------------------
    // BuildCraft 8.0.0
    // ------------------------------------------------------------------------------------------

    private static void buildCraft(Check check, WorldServer over, WorldServer nether, boolean extended) throws Exception {
        Block quarry = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("buildcraftbuilders", "quarry"));
        if (!check.ok(quarry != null && quarry != Blocks.AIR, "BuildCraft's quarry block")) {
            return;
        }
        int depth = Class.forName("buildcraft.core.BCCoreConfig").getField("miningMaxDepth").getInt(null);
        if (extended) {
            quarry(check, over, quarry, depth, X, true);
            springs(check, over);
        }
        quarry(check, nether, quarry, depth, X, false);
    }

    /** A quarry placed at y 70: its box bottom; then a saved box at 0, read back; in the extended world a dig. */
    private static void quarry(Check check, WorldServer world, Block quarry, int depth, int x, boolean tall)
            throws Exception {
        String w = tall ? " (extended)" : " (0-255)";
        prepare(world, x, Z, 2);
        BlockPos pos = new BlockPos(x, 70, Z);
        world.setBlockState(pos, quarry.getDefaultState(), 3);
        TileEntity tile = world.getTileEntity(pos);
        if (!check.ok(tile != null && tile.getClass().getName().equals("buildcraft.builders.tile.TileQuarry"),
                "a TileQuarry at the quarry" + w + ", got " + tile)) {
            return;
        }
        EntityLivingBase placer = FakePlayerFactory.getMinecraft(world);
        tile.getClass().getMethod("onPlacedBy", EntityLivingBase.class, ItemStack.class).invoke(tile, placer,
                ItemStack.EMPTY);
        Object miningBox = get(tile, "miningBox");
        Object frameBox = get(tile, "frameBox");
        BlockPos frameMax = (BlockPos) call(frameBox, "max");
        int wanted = frameMax.getY() - 1 - depth;
        int expected = wanted < Heights.minY(world) ? Heights.minY(world) : wanted;
        BlockPos min = (BlockPos) call(miningBox, "min");
        check.eq(expected, min.getY(), "box bottom of a quarry at y 70 (frame top " + frameMax.getY() + ", depth "
                + depth + ")" + w);
        if (tall && depth >= 256) {
            check.eq(-64, min.getY(), "box bottom of a quarry at y 70" + w);
        }

        if (tall) {
            dig(check, world, tile, miningBox, frameBox);
        }

        // A quarry saved before the patch: its box ends at 0. Read back, it reaches the bottom (extended) or stays.
        Object box = get(tile, "miningBox");
        box.getClass().getMethod("setMin", BlockPos.class).invoke(box, new BlockPos(min.getX(), 0, min.getZ()));
        boolean hadIterator = get(tile, "boxIterator") != null;
        NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
        TileEntity loaded = TileEntity.create(world, nbt);
        if (check.ok(loaded != null, "the saved quarry loads" + w)) {
            BlockPos loadedMin = (BlockPos) call(get(loaded, "miningBox"), "min");
            check.eq(tall ? expected : 0, loadedMin.getY(), "box bottom of a quarry saved at 0, loaded" + w);
            check.eq(min.getX(), loadedMin.getX(), "box x of the loaded quarry" + w);
            check.ok(get(loaded, "frameBox") != null && call(get(loaded, "frameBox"), "min") != null,
                    "the loaded quarry keeps its frame (BuildCraft's own check)" + w);
            if (tall) {
                check.ok(hadIterator, "the dug quarry has a box iterator before saving");
                check.eq(null, get(loaded, "boxIterator"), "the lowered quarry starts a new box iterator" + w);
            }
        }
        // A box whose bottom is a real 0 (a quarry whose depth ends there) stays.
        world.setBlockToAir(pos);
    }

    /**
     * Lets the quarry dig with a full battery (its work is all power-driven): the column under its frame is cleared to
     * y -50, so the first blocks it reaches are below 0.
     */
    private static void dig(Check check, WorldServer world, TileEntity tile, Object miningBox, Object frameBox)
            throws Exception {
        BlockPos fMin = (BlockPos) call(frameBox, "min");
        BlockPos fMax = (BlockPos) call(frameBox, "max");
        for (int x = fMin.getX(); x <= fMax.getX(); x++) {
            for (int z = fMin.getZ(); z <= fMax.getZ(); z++) {
                for (int y = -50; y <= fMax.getY() + 1; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!p.equals(tile.getPos())) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2 | 16);
                    }
                }
            }
        }
        BlockPos mMin = (BlockPos) call(miningBox, "min");
        BlockPos mMax = (BlockPos) call(miningBox, "max");
        int before = solid(world, mMin, mMax, -63, -51);
        java.util.Set<BlockPos> rock = new java.util.HashSet<BlockPos>();
        for (int x = mMin.getX(); x <= mMax.getX(); x++) {
            for (int z = mMin.getZ(); z <= mMax.getZ(); z++) {
                for (int y = -51; y >= -64; y--) {
                    if (!world.isAirBlock(new BlockPos(x, y, z))) {
                        rock.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        Object battery = get(tile, "battery");
        Method add = battery.getClass().getMethod("addPower", long.class, boolean.class);
        long capacity = (Long) battery.getClass().getMethod("getCapacity").invoke(battery);
        int lowest = Integer.MAX_VALUE;
        int ticks = 0;
        for (; ticks < 20000 && lowest > -53; ticks++) {
            add.invoke(battery, capacity, false);
            ((ITickable) tile).update();
            if (ticks % 50 == 0) {
                lowest = Math.min(lowest, lowestMined(world, rock));
            }
        }
        int after = solid(world, mMin, mMax, -63, -51);
        CavesAndCliffsBackport.LOGGER.info("SELFTEST modcompat: quarry dug {} block(s) at y -63..-51 in {} updates; "
                + "lowest mined y {}; drill {}.", before - after, ticks, lowest, get(tile, "drillPos"));
        check.ok(after < before, "the quarry digs below y 0 (" + (before - after) + " blocks at y -63..-51)");
        check.ok(lowest <= -51, "the quarry's lowest mined y is below 0 (" + lowest + ")");
    }

    private static int solid(World world, BlockPos min, BlockPos max, int y0, int y1) {
        int n = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = y0; y <= y1; y++) {
                    if (!world.isAirBlock(new BlockPos(x, y, z))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** The lowest y of the rock below the cleared column (it ends at -50) that is gone. */
    private static int lowestMined(World world, java.util.Set<BlockPos> rock) {
        int lowest = Integer.MAX_VALUE;
        for (BlockPos p : rock) {
            if (world.isAirBlock(p)) {
                lowest = Math.min(lowest, p.getY());
            }
        }
        return lowest;
    }

    /**
     * Water springs: BuildCraft looks for bedrock in the world's bottom five layers. A column whose third layer is the
     * first bedrock gets its spring there (-62), with water above it up to the first air, as at y 2 in a 0-255 world.
     */
    private static void springs(Check check, WorldServer world) throws Exception {
        Block spring = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("buildcraftcore", "spring"));
        if (!check.ok(spring != null && spring != Blocks.AIR, "BuildCraft's spring block")) {
            return;
        }
        Method populate = Class.forName("buildcraft.core.gen.SpringPopulate").getDeclaredMethod("doPopulate",
                World.class, Random.class, int.class, int.class);
        populate.setAccessible(true);
        int sx = X + 64;
        prepare(world, sx, Z, 1);
        int min = Heights.minY(world);
        for (int y = min; y <= min + 12; y++) {
            IBlockState state = y == min + 2 ? Blocks.BEDROCK.getDefaultState()
                    : y >= min + 11 ? Blocks.AIR.getDefaultState() : Blocks.STONE.getDefaultState();
            world.setBlockState(new BlockPos(sx, y, Z), state, 2 | 16);
        }
        // nextFloat 0 (the 1-in-40 chance), nextInt 0 (the chunk's corner).
        Random always = new Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        populate.invoke(null, world, always, sx, Z);
        check.eq(spring, world.getBlockState(new BlockPos(sx, min + 2, Z)).getBlock(), "spring on bedrock at layer 2");
        for (int y = min + 4; y <= min + 10; y++) {
            check.eq(Material.WATER, world.getBlockState(new BlockPos(sx, y, Z)).getMaterial(), "spring water at " + y);
        }
        check.ok(world.isAirBlock(new BlockPos(sx, min + 11, Z)), "spring water stops at air");

        // Generated terrain: 1.18's floor is bedrock at -64, BuildCraft's layer 0, so (as at y 0 in a 0-255 world, where
        // bedrock always lies) the spring goes one below the world and only its water column is made, from -63 up.
        int gx = X + 96;
        prepare(world, gx, Z, 1);
        check.eq(Blocks.BEDROCK, world.getBlockState(new BlockPos(gx, min, Z)).getBlock(), "generated bedrock at " + min);
        boolean solidAbove = !world.isAirBlock(new BlockPos(gx, min + 1, Z));
        populate.invoke(null, world, always, gx, Z);
        if (solidAbove) {
            check.eq(Material.WATER, world.getBlockState(new BlockPos(gx, min + 1, Z)).getMaterial(),
                    "spring water above generated bedrock");
        }
    }

    // ------------------------------------------------------------------------------------------
    // KAGIC X11p13
    // ------------------------------------------------------------------------------------------

    private static void kagic(Check check, WorldServer over, WorldServer nether, boolean extended) throws Exception {
        Block injector = byClass("mod.akrivus.kagic.blocks.BlockInjector");
        Block seed = byClass("mod.akrivus.kagic.blocks.BlockGemSeed");
        if (!check.ok(injector != null && seed != null, "KAGIC's injector and gem seed blocks")) {
            return;
        }
        Method red = injector.getClass().getMethod("calcDistanceUsingRedstoneSignals", World.class, BlockPos.class);
        Method auto = injector.getClass().getMethod("calcDistanceAutomatically", World.class, BlockPos.class);
        int kx = X + 160;
        if (extended) {
            String w = " (extended)";
            prepare(over, kx, Z, 2);
            fill(over, kx - 10, kx + 10, -58, 10, Z - 10, Z + 10, Blocks.STONE.getDefaultState());
            check.eq(-6, red.invoke(injector, over, new BlockPos(kx, 0, Z)), "redstone injector at y 0" + w);
            check.eq(-36, red.invoke(injector, over, new BlockPos(kx, -30, Z)), "redstone injector at y -30" + w);
            check.eq(-59, red.invoke(injector, over, new BlockPos(kx, -55, Z)), "redstone injector at y -55 (floor)" + w);
            over.setBlockState(new BlockPos(kx, -36, Z), seed.getDefaultState(), 2 | 16);
            check.eq(-36 - 1024, red.invoke(injector, over, new BlockPos(kx, -30, Z)),
                    "redstone injector at y -30 over a seed" + w);
            over.setBlockState(new BlockPos(kx, -36, Z), Blocks.STONE.getDefaultState(), 2 | 16);
            check.eq(-59, auto.invoke(injector, over, new BlockPos(kx, 0, Z)), "automatic injector at y 0, solid rock" + w);
            // A shaft to the sky 5 east of the column from -30 up, and air beside it (z + 1) from -29 to -6: the scan
            // takes the first y with sky in reach and rock beside it, -30.
            int top = Math.max(over.getHeight(kx + 5, Z), 80);
            fill(over, kx + 5, kx + 5, -30, top + 1, Z, Z, Blocks.AIR.getDefaultState());
            fill(over, kx, kx, -29, -6, Z + 1, Z + 1, Blocks.AIR.getDefaultState());
            check.ok(over.canSeeSky(new BlockPos(kx + 5, -30, Z)), "the shaft sees the sky");
            check.eq(-30, auto.invoke(injector, over, new BlockPos(kx, 0, Z)), "automatic injector at y 0, shaft at -30" + w);

            // The depth bonus: the same rock at y 6 and y -30 (same x and z, so the same biome), the same random.
            Method create = Class.forName("mod.akrivus.kagic.util.injector.InjectorResult").getMethod("create",
                    World.class, BlockPos.class, boolean.class);
            int dx = kx + 40;
            String[] at6 = null;
            for (int y : new int[] {6, -30}) {
                fill(over, dx - 6, dx + 6, y - 6, y + 6, Z - 6, Z + 6, Blocks.STONE.getDefaultState());
                over.setBlockState(new BlockPos(dx + 2, y, Z), Blocks.IRON_ORE.getDefaultState(), 2 | 16);
                over.setBlockState(new BlockPos(dx, y + 2, Z - 1), Blocks.COAL_ORE.getDefaultState(), 2 | 16);
                over.setBlockState(new BlockPos(dx - 1, y - 2, Z + 3), Blocks.DIAMOND_ORE.getDefaultState(), 2 | 16);
                over.setBlockState(new BlockPos(dx + 3, y + 1, Z + 2), Blocks.GRAVEL.getDefaultState(), 2 | 16);
                over.rand.setSeed(650L);
                Object result = create.invoke(null, over, new BlockPos(dx, y, Z), true);
                String[] summary = summary(result);
                CavesAndCliffsBackport.LOGGER.info("SELFTEST modcompat: injector result at y {}: {}", y,
                        Arrays.toString(summary));
                if (at6 == null) {
                    at6 = summary;
                } else {
                    check.eq(Arrays.asList(at6), Arrays.asList(summary), "injector result at y -30 as at y 6" + w);
                }
            }
        }
        String w = " (0-255)";
        prepare(nether, kx, Z, 2);
        fill(nether, kx - 10, kx + 10, 1, 60, Z - 10, Z + 10, Blocks.STONE.getDefaultState());
        check.eq(34, red.invoke(injector, nether, new BlockPos(kx, 40, Z)), "redstone injector at y 40" + w);
        check.eq(5, red.invoke(injector, nether, new BlockPos(kx, 8, Z)), "redstone injector at y 8 (floor)" + w);
        nether.setBlockState(new BlockPos(kx, 34, Z), seed.getDefaultState(), 2 | 16);
        check.eq(34 - 256, red.invoke(injector, nether, new BlockPos(kx, 40, Z)), "redstone injector over a seed" + w);
        nether.setBlockState(new BlockPos(kx, 34, Z), Blocks.STONE.getDefaultState(), 2 | 16);
        check.eq(5, auto.invoke(injector, nether, new BlockPos(kx, 40, Z)), "automatic injector, solid rock" + w);
    }

    /** The gem chosen (its class), the defective rate and the flags of an injector result. */
    private static String[] summary(Object result) throws Exception {
        if (result == null) {
            return new String[] {"null"};
        }
        Object gem = get(result, "gem");
        return new String[] {gem == null ? "no gem" : gem.getClass().getSimpleName(),
                String.valueOf(get(result, "defectiveRate")), String.valueOf(get(result, "createSlags")),
                String.valueOf(get(result, "isPrimary"))};
    }

    // ------------------------------------------------------------------------------------------

    private static Block byClass(String name) {
        for (Block b : ForgeRegistries.BLOCKS) {
            if (b.getClass().getName().equals(name)) {
                return b;
            }
        }
        return null;
    }

    private static void fill(World world, int x0, int x1, int y0, int y1, int z0, int z1, IBlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    world.setBlockState(new BlockPos(x, y, z), state, 2 | 16);
                }
            }
        }
    }

    private static void prepare(WorldServer world, int x, int z, int radius) {
        int cx = x >> 4;
        int cz = z >> 4;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                world.getChunk(cx + dx, cz + dz);
            }
        }
    }

    private static Object get(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // the superclass, then
            }
        }
        throw new NoSuchFieldException(o.getClass().getName() + "." + name);
    }

    private static Object call(Object o, String name) throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }
}
