package com.exiledradio.cavesandcliffsbackport.dripstone;

import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.CaveRegions;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

/**
 * Dripstone caves: what 1.18 puts in its dripstone caves biome, in the caves of our dripstone
 * {@link com.exiledradio.cavesandcliffsbackport.common.CaveRegions}.
 *
 * <h2>Features (1.18's settings)</h2>
 * <ul>
 *   <li><b>Clusters</b>: a patch 2-8 blocks each way of dripstone block on floor and ceiling (2-4 deep) with
 *       stalagmites and stalactites up to 3-6 long, taller toward the middle, and - in wet clusters - one-block
 *       <b>pools</b> of water set in the floor.</li>
 *   <li><b>Large dripstone</b>: a column of dripstone block from floor and ceiling, each a cone whose height
 *       follows 1.18's curve, leaning with a "wind" when wide and blunt.</li>
 *   <li><b>Pointed dripstone</b>: single ones, one or two long, on a small patch of dripstone block.</li>
 *   <li><b>Extra copper</b>: {@code extraCopperVeins} more tries a chunk (16) at veins of 20, most of them
 *       around y 48.</li>
 * </ul>
 * 1.18 tries each feature at random heights of its whole 384-block world and most tries land in rock; here
 * the tries are scaled to the 64 levels they are spread over, and each must start in air, underground, inside
 * a dripstone region.
 *
 * <h2>Staying inside the population window</h2>
 * Every feature starts in the middle 16x16 of the 2x2 chunks being populated and reads and writes only
 * inside them ({@link Window}), so it never loads or generates another chunk. Large dripstone is capped at a
 * radius of 7 for that.
 */
public final class DripstoneCaveGenerator implements IWorldGenerator {

    private static final int MIN_Y = 6, MAX_Y = 70;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.owns(world)) {
            return; // the 1.18 terrain places its own
        }
        if (!ModConfig.caves.dripstoneRegions || world.provider.getDimension() != 0
                || com.exiledradio.backportcore.worldgen.FlatWorlds.isFlat(world) // a cave biome's, and a superflat world is all its preset's biome
                || DripstoneContent.dripstone == null || !regionNear(world.getSeed(), chunkX, chunkZ)) {
            return;
        }
        Window w = new Window(world, chunkX * 16, chunkZ * 16);
        for (int i = 10 + random.nextInt(10); i > 0; i--) {
            BlockPos at = w.randomStart(random);
            if (at != null) {
                cluster(w, random, at);
            }
        }
        for (int i = 2 + random.nextInt(8); i > 0; i--) {
            BlockPos at = w.randomStart(random);
            if (at != null) {
                largeDripstone(w, random, at);
            }
        }
        for (int i = 38 + random.nextInt(14); i > 0; i--) {
            BlockPos origin = w.randomPos(random);
            for (int n = 1 + random.nextInt(5); n > 0; n--) {
                BlockPos at = origin.add(spread(random), spread(random), spread(random));
                if (w.inside(at) && w.start(at)) {
                    pointed(w, random, at);
                }
            }
        }
        Block ore = Owners.ourBlock("copper_ore");
        if (ore != null && ModConfig.dripstone.extraCopperVeins > 0) {
            WorldGenMinable vein = new WorldGenMinable(ore.getDefaultState(), 20, BlockMatcher.forBlock(Blocks.STONE));
            for (int i = 0; i < ModConfig.dripstone.extraCopperVeins; i++) {
                // The vein generator itself adds 8 to x and z. Heights: a triangle over 0-96, 1.18's -16..112 in this world.
                BlockPos at = new BlockPos(chunkX * 16 + random.nextInt(16), random.nextInt(49) + random.nextInt(49), chunkZ * 16 + random.nextInt(16));
                if (at.getY() > 0 && CaveRegions.dripstone(world, at.getX() + 8, at.getY(), at.getZ() + 8)) {
                    vein.generate(world, random, at);
                }
            }
        }
    }

    /** 1.18's spread of single pointed dripstones around a try: a bell around 0, at most 10. */
    private static int spread(Random random) {
        return MathHelper.clamp((int) Math.round(random.nextGaussian() * 3), -10, 10);
    }

    /** @return whether any of a coarse grid of points in this chunk's window is in a dripstone region */
    private static boolean regionNear(long seed, int chunkX, int chunkZ) {
        for (int dx = 4; dx < 32; dx += 12) {
            for (int dz = 4; dz < 32; dz += 12) {
                for (int y = MIN_Y; y <= MAX_Y; y += 8) {
                    if (CaveRegions.at(seed, chunkX * 16 + dx, y, chunkZ * 16 + dz) == CaveRegions.Region.DRIPSTONE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The 2x2 chunks being populated: nothing outside is read (it counts as bedrock) or written. */
    public static final class Window {

        final World world;
        final int minX, minZ;
        private final Block deepslate = Owners.ourBlock("deepslate"), tuff = Owners.ourBlock("tuff");

        public Window(World world, int minX, int minZ) {
            this.world = world;
            this.minX = minX;
            this.minZ = minZ;
        }

        boolean inside(BlockPos p) {
            return p.getX() >= this.minX && p.getX() < this.minX + 32 && p.getZ() >= this.minZ && p.getZ() < this.minZ + 32
                    && p.getY() > 0 && p.getY() < 255;
        }

        IBlockState get(BlockPos p) {
            return inside(p) ? this.world.getBlockState(p) : Blocks.BEDROCK.getDefaultState();
        }

        void set(BlockPos p, IBlockState state) {
            if (inside(p)) {
                this.world.setBlockState(p, state, 2);
            }
        }

        BlockPos randomPos(Random random) {
            return new BlockPos(this.minX + 8 + random.nextInt(16), MIN_Y + random.nextInt(MAX_Y - MIN_Y), this.minZ + 8 + random.nextInt(16));
        }

        /** @return a random place in the window's middle where a feature may start, or null */
        BlockPos randomStart(Random random) {
            BlockPos p = randomPos(random);
            return start(p) ? p : null;
        }

        /** A feature starts in air or water, underground, in a dripstone region. */
        boolean start(BlockPos p) {
            return emptyOrWater(get(p)) && CaveRegions.dripstone(this.world, p.getX(), p.getY(), p.getZ()) && CaveRegions.underground(this.world, p);
        }

        boolean air(BlockPos p) {
            IBlockState s = get(p);
            return s.getBlock().isAir(s, this.world, p);
        }

        boolean emptyOrWater(IBlockState s) {
            return s.getMaterial() == Material.AIR || s.getMaterial() == Material.WATER;
        }

        boolean emptyOrWaterOrLava(BlockPos p) {
            IBlockState s = get(p);
            return emptyOrWater(s) || s.getMaterial() == Material.LAVA;
        }

        /** 1.18's base stone: what dripstone replaces and grows from. */
        boolean baseStone(IBlockState s) {
            Block b = s.getBlock();
            return b == Blocks.STONE || b == this.deepslate && b != null || b == this.tuff && b != null;
        }

        boolean dripstoneBase(IBlockState s) {
            return s.getBlock() == DripstoneContent.dripstone || baseStone(s);
        }

        boolean placeDripstoneBlock(BlockPos p) {
            if (baseStone(get(p))) {
                set(p, DripstoneContent.dripstone.getDefaultState());
                return true;
            }
            return false;
        }

        /**
         * A stalagmite or stalactite of {@code height} from its first segment at {@code p}: base, middles,
         * frustum, tip. Only from a dripstone base and only into air or water; a segment where water was is
         * waterlogged (1.18.2's {@code DripstoneUtils.growPointedDripstone}: {@code isWaterAt}, flowing water too).
         */
        void growPointed(BlockPos p, EnumFacing direction, int height, boolean mergeTip) {
            if (!canGrowPointed(p, direction, height)) {
                return;
            }
            BlockPointedDripstone block = DripstoneContent.pointed;
            for (int i = 0; i < height; i++) {
                int fromTip = height - 1 - i;
                Thickness t = fromTip == 0 ? (mergeTip ? Thickness.TIP_MERGE : Thickness.TIP)
                        : fromTip == 1 ? Thickness.FRUSTUM : i == 0 ? Thickness.BASE : Thickness.MIDDLE;
                BlockPos at = p.offset(direction, i);
                if (inside(at) && get(at).getMaterial() == Material.WATER) {
                    Waterlogging.placeWithWater(this.world, at, block.state(direction, t), 2);
                } else {
                    set(at, block.state(direction, t));
                }
            }
        }

        boolean canGrowPointed(BlockPos p, EnumFacing direction, int height) {
            if (height <= 0 || !dripstoneBase(get(p.offset(direction.getOpposite())))) {
                return false;
            }
            for (int i = 0; i < height; i++) {
                if (!emptyOrWater(get(p.offset(direction, i)))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Looks up and down an open column from {@code p}.
         *
         * @return {floor y, ceiling y}, each {@code Integer.MIN_VALUE} when no acceptable edge is within range;
         * null when {@code p} itself is not open
         */
        int[] column(BlockPos p, int range, boolean lavaEdge) {
            if (!emptyOrWater(get(p))) {
                return null;
            }
            return new int[] {edge(p, range, EnumFacing.DOWN, lavaEdge), edge(p, range, EnumFacing.UP, lavaEdge)};
        }

        private int edge(BlockPos p, int range, EnumFacing direction, boolean lavaEdge) {
            for (int i = 1; i <= range; i++) {
                BlockPos at = p.offset(direction, i);
                IBlockState s = get(at);
                if (!emptyOrWater(s)) {
                    boolean ok = lavaEdge ? dripstoneBase(s) || s.getMaterial() == Material.LAVA
                            : s.getBlock() != Blocks.BEDROCK || inside(at);
                    return ok ? at.getY() : Integer.MIN_VALUE;
                }
            }
            return Integer.MIN_VALUE;
        }
    }

    private static final int NONE = Integer.MIN_VALUE;

    // ------------------------------------------------------------------------------- clusters and pools

    /** @return whether anything could be tried (the start was open) */
    public static boolean cluster(Window w, Random random, BlockPos origin) {
        if (!w.emptyOrWater(w.get(origin))) {
            return false;
        }
        int height = 3 + random.nextInt(4);
        float wetness = MathHelper.clamp(0.1F + (float) random.nextGaussian() * 0.3F, 0.1F, 0.9F);
        float density = 0.3F + random.nextFloat() * 0.6F;
        int rx = 2 + random.nextInt(7), rz = 2 + random.nextInt(7);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                // More likely toward the middle: 10% at the rim, certain three blocks in.
                double chance = clampedMap(Math.min(rx - Math.abs(dx), rz - Math.abs(dz)), 0, 3, 0.1, 1.0);
                clusterColumn(w, random, origin.add(dx, 0, dz), dx, dz, wetness, chance, height, density);
            }
        }
        return true;
    }

    private static void clusterColumn(Window w, Random random, BlockPos pos, int dx, int dz, float wetness, double chance,
                                      int clusterHeight, float density) {
        int[] column = w.column(pos, 12, false);
        if (column == null || column[0] == NONE && column[1] == NONE) {
            return;
        }
        int floor = column[0], ceiling = column[1];
        boolean wet = random.nextFloat() < wetness;
        if (wet && floor != NONE && canPlacePool(w, atY(pos, floor))) {
            w.set(atY(pos, floor), Blocks.WATER.getDefaultState());
            floor--;
        }
        int stalactite = 0;
        if (ceiling != NONE && random.nextDouble() < chance && w.get(atY(pos, ceiling)).getMaterial() != Material.LAVA) {
            replaceWithDripstone(w, atY(pos, ceiling), 2 + random.nextInt(3), EnumFacing.UP);
            int max = floor != NONE ? Math.min(clusterHeight, ceiling - floor) : clusterHeight;
            stalactite = dripstoneHeight(random, dx, dz, density, max);
        }
        int stalagmite = 0;
        if (floor != NONE && random.nextDouble() < chance && w.get(atY(pos, floor)).getMaterial() != Material.LAVA) {
            replaceWithDripstone(w, atY(pos, floor), 2 + random.nextInt(3), EnumFacing.DOWN);
            stalagmite = ceiling != NONE ? Math.max(0, stalactite + random.nextInt(3) - 1)
                    : dripstoneHeight(random, dx, dz, density, clusterHeight);
        }
        if (ceiling != NONE && floor != NONE && ceiling - stalactite <= floor + stalagmite) {
            // They would overlap: meet somewhere between.
            int lo = Math.max(ceiling - stalactite, floor + 1);
            int hi = Math.min(floor + stalagmite, ceiling - 1);
            int meet = lo + random.nextInt(Math.max(1, hi + 1 - lo + 1));
            stalactite = ceiling - meet;
            stalagmite = meet - 1 - floor;
        }
        boolean merge = random.nextBoolean() && stalactite > 0 && stalagmite > 0 && ceiling != NONE && floor != NONE
                && stalactite + stalagmite == ceiling - floor - 1
                // Both halves must really be placed, or one would be left with a merged tip and no partner.
                && w.canGrowPointed(atY(pos, ceiling - 1), EnumFacing.DOWN, stalactite)
                && w.canGrowPointed(atY(pos, floor + 1), EnumFacing.UP, stalagmite);
        if (ceiling != NONE) {
            w.growPointed(atY(pos, ceiling - 1), EnumFacing.DOWN, stalactite, merge);
        }
        if (floor != NONE) {
            w.growPointed(atY(pos, floor + 1), EnumFacing.UP, stalagmite, merge);
        }
    }

    private static BlockPos atY(BlockPos p, int y) {
        return new BlockPos(p.getX(), y, p.getZ());
    }

    private static double clampedMap(double v, double from0, double from1, double to0, double to1) {
        double t = MathHelper.clamp((v - from0) / (from1 - from0), 0.0, 1.0);
        return to0 + t * (to1 - to0);
    }

    /** A cluster column's dripstone length: none for most columns of a sparse cluster, longer near the middle. */
    private static int dripstoneHeight(Random random, int dx, int dz, float density, int max) {
        if (random.nextFloat() > density) {
            return 0;
        }
        double mean = clampedMap(Math.abs(dx) + Math.abs(dz), 0, 8, max / 2.0, 0);
        return (int) MathHelper.clamp(mean + random.nextGaussian() * 3, 0, max);
    }

    /** A pool block needs rock or water on its four sides and below, and no water on top. */
    private static boolean canPlacePool(Window w, BlockPos pos) {
        IBlockState s = w.get(pos);
        if (s.getMaterial() == Material.WATER || s.getBlock() == DripstoneContent.dripstone || BlockPointedDripstone.isPointed(s)) {
            return false;
        }
        if (w.get(pos.up()).getMaterial() == Material.WATER) {
            return false;
        }
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (!holdsWater(w, pos.offset(side))) {
                return false;
            }
        }
        return holdsWater(w, pos.down());
    }

    private static boolean holdsWater(Window w, BlockPos pos) {
        IBlockState s = w.get(pos);
        return w.baseStone(s) || s.getMaterial() == Material.WATER;
    }

    private static void replaceWithDripstone(Window w, BlockPos from, int thickness, EnumFacing direction) {
        for (int i = 0; i < thickness; i++) {
            if (!w.placeDripstoneBlock(from.offset(direction, i))) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------------------- large dripstone

    /** @return whether a column was built */
    public static boolean largeDripstone(Window w, Random random, BlockPos origin) {
        int[] column = w.column(origin, 30, true);
        if (column == null || column[0] == NONE || column[1] == NONE) {
            return false;
        }
        int floor = column[0], ceiling = column[1], caveHeight = ceiling - floor - 1;
        if (caveHeight < 4) {
            return false;
        }
        // 1.18: a radius of 3-19, at most a third of the cave's height; 7 here keeps it inside the window.
        int maxRadius = MathHelper.clamp((int) (caveHeight * 0.33F), 3, 7);
        int radius = 3 + random.nextInt(maxRadius - 3 + 1);
        Large stalactite = new Large(atY(origin, ceiling - 1), false, radius, 0.3 + random.nextDouble() * 0.6, 0.4 + random.nextDouble() * 1.6);
        Large stalagmite = new Large(atY(origin, floor + 1), true, radius, 0.4 + random.nextDouble() * 0.6, 0.4 + random.nextDouble() * 1.6);
        double windX = 0, windZ = 0;
        if (stalactite.windy() && stalagmite.windy()) {
            double speed = random.nextDouble() * 0.3, angle = random.nextDouble() * Math.PI;
            windX = Math.cos(angle) * speed;
            windZ = Math.sin(angle) * speed;
        }
        boolean top = stalactite.settle(w, origin.getY(), windX, windZ);
        boolean bottom = stalagmite.settle(w, origin.getY(), windX, windZ);
        if (top) {
            stalactite.place(w, random, origin.getY(), windX, windZ);
        }
        if (bottom) {
            stalagmite.place(w, random, origin.getY(), windX, windZ);
        }
        return top || bottom;
    }

    /** One half of a large dripstone column: a cone from the floor or the ceiling. */
    private static final class Large {
        BlockPos root;
        final boolean up;
        int radius;
        final double bluntness, scale;

        Large(BlockPos root, boolean up, int radius, double bluntness, double scale) {
            this.root = root;
            this.up = up;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        boolean windy() {
            return this.radius >= 4 && this.bluntness >= 0.6;
        }

        /** 1.18's cone: how tall it is this far from its axis. */
        int heightAt(double r) {
            if (r < this.bluntness) {
                r = this.bluntness;
            }
            double e = r / this.radius * 0.384;
            double f = 0.75 * Math.pow(e, 4.0 / 3.0), g = Math.pow(e, 2.0 / 3.0), h = Math.log(e) / 3.0;
            return (int) (Math.max(this.scale * (f - g - h), 0) / 0.384 * this.radius);
        }

        private static BlockPos blown(BlockPos p, int originY, double windX, double windZ) {
            int dy = originY - p.getY();
            return p.add(MathHelper.floor(windX * dy), 0, MathHelper.floor(windZ * dy));
        }

        /** Pushes the root back into the rock until its whole rim is embedded, halving the radius when it never is. */
        boolean settle(Window w, int originY, double windX, double windZ) {
            while (this.radius > 1) {
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(this.root);
                int tries = Math.min(10, heightAt(0));
                for (int i = 0; i < tries; i++) {
                    if (w.get(m).getMaterial() == Material.LAVA) {
                        return false;
                    }
                    if (embedded(w, blown(m, originY, windX, windZ))) {
                        this.root = m.toImmutable();
                        return true;
                    }
                    m.move(this.up ? EnumFacing.DOWN : EnumFacing.UP);
                }
                this.radius /= 2;
            }
            return false;
        }

        private boolean embedded(Window w, BlockPos center) {
            if (w.emptyOrWaterOrLava(center)) {
                return false;
            }
            double step = 6.0 / this.radius;
            for (double a = 0; a < Math.PI * 2; a += step) {
                if (w.emptyOrWaterOrLava(center.add((int) (Math.cos(a) * this.radius), 0, (int) (Math.sin(a) * this.radius)))) {
                    return false;
                }
            }
            return true;
        }

        void place(Window w, Random random, int originY, double windX, double windZ) {
            IBlockState dripstone = DripstoneContent.dripstone.getDefaultState();
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                for (int dz = -this.radius; dz <= this.radius; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > this.radius) {
                        continue;
                    }
                    int height = heightAt(r);
                    if (height <= 0) {
                        continue;
                    }
                    if (random.nextFloat() < 0.2F) {
                        height = (int) (height * (0.8F + random.nextFloat() * 0.2F));
                    }
                    BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(this.root.add(dx, 0, dz));
                    boolean placed = false;
                    for (int i = 0; i < height; i++) {
                        BlockPos at = blown(m, originY, windX, windZ);
                        if (w.emptyOrWaterOrLava(at)) {
                            placed = true;
                            w.set(at, dripstone);
                        } else if (placed && w.baseStone(w.get(at))) {
                            break;
                        }
                        m.move(this.up ? EnumFacing.UP : EnumFacing.DOWN);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------- single pointed dripstone

    /** A single pointed dripstone on the nearest floor or ceiling, one long (two, one time in five). */
    public static boolean pointed(Window w, Random random, BlockPos origin) {
        int[] column = w.column(origin, 12, false);
        if (column == null) {
            return false;
        }
        boolean floor = column[0] != NONE, ceiling = column[1] != NONE;
        if (!floor && !ceiling) {
            return false;
        }
        EnumFacing direction = floor && ceiling ? (random.nextBoolean() ? EnumFacing.UP : EnumFacing.DOWN) : floor ? EnumFacing.UP : EnumFacing.DOWN;
        BlockPos root = atY(origin, direction == EnumFacing.UP ? column[0] : column[1]);
        BlockPos first = root.offset(direction);
        // A patch of dripstone block around the root: each side 70%, then two more steps at 50% each.
        w.placeDripstoneBlock(root);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (random.nextFloat() > 0.7F) {
                continue;
            }
            BlockPos p = root.offset(side);
            w.placeDripstoneBlock(p);
            if (random.nextFloat() > 0.5F) {
                continue;
            }
            p = p.offset(EnumFacing.random(random));
            w.placeDripstoneBlock(p);
            if (random.nextFloat() > 0.5F) {
                continue;
            }
            w.placeDripstoneBlock(p.offset(EnumFacing.random(random)));
        }
        int height = random.nextFloat() < 0.2F && w.emptyOrWater(w.get(first.offset(direction))) ? 2 : 1;
        w.growPointed(first, direction, height, false);
        return true;
    }
}
