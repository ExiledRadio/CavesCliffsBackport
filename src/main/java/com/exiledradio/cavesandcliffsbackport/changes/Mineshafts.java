package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.lush.BlockGlowLichen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureMineshaftPieces;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.template.TemplateManager;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

/**
 * 1.17's mineshafts ({@code MineShaftPieces} of 1.18.2), in both world types:
 * <ul>
 *   <li><b>Holding up bridges.</b> Where a corridor's floor is planks under its first and last support (a bridge over a
 *       cave), a log pillar goes down to the first solid ground within 20 blocks (not through lava); with none, a
 *       fence on the bridge and a chain up to the first ceiling within 50 blocks hold it from above.</li>
 *   <li><b>Cobwebs</b> by the supports only where two of their six sides touch a full face.</li>
 *   <li><b>Floors</b> fill whatever does not have a full top (not only air).</li>
 *   <li><b>Nothing cut.</b> A piece built later does not carve through another piece's planks, logs, fences or chains
 *       ({@code MineShaftPiece.canBeReplaced}).</li>
 * </ul>
 * The layout is 1.12.2's own, made by 1.12.2's code with the same random numbers: when a mineshaft start is made, its
 * pieces are read back from their saved form as the subclasses here ({@link #modernize}), which then build them.
 * The subclasses also take 1.12.2's piece names, so mineshafts read from the save build the same way. With the
 * setting off, the subclasses build exactly as 1.12.2. The chain is Nether Update Backport's when installed (Backport
 * Core's palette resolves it), else the shaft's own fence.
 *
 * <p>The rest of 1.12.2's rules stay: what counts as inside (sky light below 8, where 1.18.2 asks the heightmap),
 * standing torches, rails.
 */
public final class Mineshafts {

    private static IBlockState chain;
    private static boolean chainResolved;

    private Mineshafts() {
    }

    /** Init: 1.12.2's piece names read as the subclasses here, and the Default world type's generator. */
    public static void init() {
        MinecraftForge.TERRAIN_GEN_BUS.register(Mineshafts.class);
        MapGenStructureIO.registerStructureComponent(Corridor.class, "MSCorridor");
        MapGenStructureIO.registerStructureComponent(Crossing.class, "MSCrossing");
        MapGenStructureIO.registerStructureComponent(Room.class, "MSRoom");
        MapGenStructureIO.registerStructureComponent(Stairs.class, "MSStairs");
    }

    /** The Default world type's mineshafts: 1.12.2's generator, starts given the subclasses. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initMapGen(InitMapGenEvent event) {
        if (event.getType() == InitMapGenEvent.EventType.MINESHAFT && event.getNewGen() != null
                && event.getNewGen().getClass() == MapGenMineshaft.class) {
            event.setNewGen(new Generator());
        }
    }

    /** 1.12.2's mineshaft generator, whose starts are given the subclasses. */
    public static final class Generator extends MapGenMineshaft {
        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            return modernize(this.world, super.getStructureStart(chunkX, chunkZ));
        }
    }

    /**
     * A new mineshaft start with its pieces read back from their saved form, so they are the subclasses here (the
     * names are theirs, see {@link #init}). Nothing else changes: the layout was made already.
     */
    public static StructureStart modernize(World world, StructureStart start) {
        if (world == null || start == null || start.getComponents().isEmpty()) {
            return start;
        }
        NBTTagCompound tag = start.writeStructureComponentsToNBT(start.getChunkPosX(), start.getChunkPosZ());
        StructureStart read = MapGenStructureIO.getStructureStart(tag, world);
        return read != null && read.getComponents().size() == start.getComponents().size() ? read : start;
    }

    /** The chain hanging a bridge: Nether Update Backport's when installed. */
    public static IBlockState chain(IBlockState fence) {
        if (!chainResolved) {
            chainResolved = true;
            try {
                ModernPalette.Resolved r = ModernPalette.resolve("minecraft:iron_chain[axis=y]");
                chain = r == null ? null : r.state;
            } catch (RuntimeException e) {
                chain = null;
            }
            if (chain != null && chain.getBlock() == Blocks.AIR) {
                chain = null;
            }
        }
        return chain != null ? chain : fence;
    }

    static boolean on() {
        return ModConfig.changes.mineshaftSupports;
    }

    // ------------------------------------------------------------------------------------------
    // What the pieces share (1.18.2's MineShaftPiece and StructurePiece)

    static MapGenMineshaft.Type type(NBTTagCompound tag) {
        return MapGenMineshaft.Type.byId(tag.getInteger("MST"));
    }

    static IBlockState planks(MapGenMineshaft.Type type) {
        return type == MapGenMineshaft.Type.MESA
                ? Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.DARK_OAK)
                : Blocks.PLANKS.getDefaultState();
    }

    static IBlockState fence(MapGenMineshaft.Type type) {
        return type == MapGenMineshaft.Type.MESA ? Blocks.DARK_OAK_FENCE.getDefaultState() : Blocks.OAK_FENCE.getDefaultState();
    }

    /** The shaft's wood ({@code MineshaftFeature.Type.getWoodState}): oak or dark oak logs, upright. */
    static IBlockState log(MapGenMineshaft.Type type) {
        return type == MapGenMineshaft.Type.MESA
                ? Blocks.LOG2.getDefaultState().withProperty(BlockNewLog.VARIANT, BlockPlanks.EnumType.DARK_OAK)
                        .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y)
                : Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.OAK)
                        .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y);
    }

    static boolean isLog(IBlockState state, MapGenMineshaft.Type type) {
        IBlockState log = log(type);
        Block b = state.getBlock();
        if (b != log.getBlock()) {
            return false;
        }
        return b == Blocks.LOG2 ? state.getValue(BlockNewLog.VARIANT) == log.getValue(BlockNewLog.VARIANT)
                : state.getValue(BlockOldLog.VARIANT) == log.getValue(BlockOldLog.VARIANT);
    }

    /** {@code MineShaftPiece.canBeReplaced}: not the shaft's planks, wood, fence or a chain. */
    static boolean canBeReplaced(IBlockState state, MapGenMineshaft.Type type) {
        IBlockState fence = fence(type);
        return state != planks(type) && !isLog(state, type) && state.getBlock() != fence.getBlock()
                && state.getBlock() != chain(fence).getBlock();
    }

    /** {@code StructurePiece.isReplaceableByStructures}: air, liquids, glow lichen (and seagrass, which 1.12.2 lacks). */
    static boolean replaceableByStructures(IBlockState state) {
        Material m = state.getMaterial();
        return m == Material.AIR || m.isLiquid() || state.getBlock() instanceof BlockGlowLichen;
    }

    static boolean sturdy(World world, BlockPos pos, EnumFacing face) {
        return world.getBlockState(pos).getBlockFaceShape(world, pos, face) == BlockFaceShape.SOLID;
    }

    /** {@code fillPillarDownOrChainUp}: a pillar down to the ground within 20 blocks, else a chain up within 50. */
    static void pillarDownOrChainUp(World world, IBlockState log, IBlockState fence, BlockPos start, StructureBoundingBox box) {
        if (!box.isVecInside(start)) {
            return;
        }
        int x = start.getX();
        int z = start.getZ();
        int i = start.getY();
        int bottom = Heights.minY(world);
        int top = Heights.maxY(world);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean down = true;
        boolean up = true;
        for (int j = 1; down || up; j++) {
            if (down) {
                p.setPos(x, i - j, z);
                IBlockState s = world.getBlockState(p);
                boolean open = replaceableByStructures(s) && s.getMaterial() != Material.LAVA;
                if (!open && sturdy(world, p, EnumFacing.UP)) {
                    fill(world, log, x, z, i - j + 1, i);
                    return;
                }
                down = j <= 20 && open && p.getY() > bottom + 1;
            }
            if (up) {
                p.setPos(x, i + j, z);
                IBlockState s = world.getBlockState(p);
                boolean open = replaceableByStructures(s);
                if (!open && sturdy(world, p, EnumFacing.DOWN) && !(s.getBlock() instanceof BlockFalling)) {
                    world.setBlockState(new BlockPos(x, i + 1, z), fence, 2);
                    fill(world, chain(fence), x, z, i + 2, i + j);
                    return;
                }
                up = j <= 50 && open && p.getY() < top - 1;
            }
        }
    }

    /** {@code fillColumnBetween}: y from (inclusive) to (exclusive). */
    private static void fill(World world, IBlockState state, int x, int z, int from, int to) {
        for (int y = from; y < to; y++) {
            world.setBlockState(new BlockPos(x, y, z), state, 2);
        }
    }

    // ------------------------------------------------------------------------------------------
    // The pieces

    /** A corridor: 1.12.2's, with 1.17's supports, cobwebs and floor. */
    public static class Corridor extends StructureMineshaftPieces.Corridor {
        private MapGenMineshaft.Type type = MapGenMineshaft.Type.NORMAL;
        private boolean rails;
        private boolean spiders;
        private boolean spawnerPlaced;
        private int sections;

        public Corridor() {
        }

        @Override
        protected void writeStructureToNBT(NBTTagCompound tag) {
            super.writeStructureToNBT(tag);
            tag.setBoolean("hps", this.spawnerPlaced);
        }

        @Override
        protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager templates) {
            super.readStructureFromNBT(tag, templates);
            this.type = type(tag);
            this.rails = tag.getBoolean("hr");
            this.spiders = tag.getBoolean("sc");
            this.spawnerPlaced = tag.getBoolean("hps");
            this.sections = tag.getInteger("Num");
        }

        @Override
        protected void setBlockState(World world, IBlockState state, int x, int y, int z, StructureBoundingBox box) {
            if (!on() || canBeReplaced(this.getBlockStateFromPos(world, x, y, z, box), this.type)) {
                super.setBlockState(world, state, x, y, z, box);
            }
        }

        @Override
        public boolean addComponentParts(World world, Random rand, StructureBoundingBox box) {
            if (!on()) {
                return super.addComponentParts(world, rand, box);
            }
            if (this.isLiquidInStructureBoundingBox(world, box)) {
                return false;
            }
            int end = this.sections * 5 - 1;
            IBlockState planks = planks(this.type);
            IBlockState air = Blocks.AIR.getDefaultState();
            this.fillWithBlocks(world, box, 0, 0, 0, 2, 1, end, air, air, false);
            this.generateMaybeBox(world, box, rand, 0.8F, 0, 2, 0, 2, 2, end, air, air, false, 0);
            if (this.spiders) {
                this.generateMaybeBox(world, box, rand, 0.6F, 0, 0, 0, 2, 1, end, Blocks.WEB.getDefaultState(), air, false, 8);
            }
            for (int s = 0; s < this.sections; s++) {
                int z = 2 + s * 5;
                this.placeSupport(world, box, 0, 0, z, 2, 2, rand);
                this.placeCobWeb(world, box, rand, 0.1F, 0, 2, z - 1);
                this.placeCobWeb(world, box, rand, 0.1F, 2, 2, z - 1);
                this.placeCobWeb(world, box, rand, 0.1F, 0, 2, z + 1);
                this.placeCobWeb(world, box, rand, 0.1F, 2, 2, z + 1);
                this.placeCobWeb(world, box, rand, 0.05F, 0, 2, z - 2);
                this.placeCobWeb(world, box, rand, 0.05F, 2, 2, z - 2);
                this.placeCobWeb(world, box, rand, 0.05F, 0, 2, z + 2);
                this.placeCobWeb(world, box, rand, 0.05F, 2, 2, z + 2);
                if (rand.nextInt(100) == 0) {
                    this.generateChest(world, box, rand, 2, 0, z - 1, LootTableList.CHESTS_ABANDONED_MINESHAFT);
                }
                if (rand.nextInt(100) == 0) {
                    this.generateChest(world, box, rand, 0, 0, z + 1, LootTableList.CHESTS_ABANDONED_MINESHAFT);
                }
                if (this.spiders && !this.spawnerPlaced) {
                    int y = this.getYWithOffset(0);
                    int along = z - 1 + rand.nextInt(3);
                    BlockPos pos = new BlockPos(this.getXWithOffset(1, along), y, this.getZWithOffset(1, along));
                    if (box.isVecInside(pos) && this.getSkyBrightness(world, 1, 0, along, box) < 8) {
                        this.spawnerPlaced = true;
                        world.setBlockState(pos, Blocks.MOB_SPAWNER.getDefaultState(), 2);
                        TileEntity te = world.getTileEntity(pos);
                        if (te instanceof TileEntityMobSpawner) {
                            ((TileEntityMobSpawner) te).getSpawnerBaseLogic().setEntityId(EntityList.getKey(EntityCaveSpider.class));
                        }
                    }
                }
            }
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= end; z++) {
                    this.setPlanks(world, box, planks, x, -1, z);
                }
            }
            this.placeDoubleLowerOrUpperSupport(world, box, 0, -1, 2);
            if (this.sections > 1) {
                this.placeDoubleLowerOrUpperSupport(world, box, 0, -1, end - 2);
            }
            if (this.rails) {
                IBlockState rail = Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, BlockRailBase.EnumRailDirection.NORTH_SOUTH);
                for (int z = 0; z <= end; z++) {
                    IBlockState under = this.getBlockStateFromPos(world, 1, -1, z, box);
                    if (under.getMaterial() != Material.AIR && under.isFullBlock()) {
                        float f = this.getSkyBrightness(world, 1, 0, z, box) > 8 ? 0.9F : 0.7F;
                        this.randomlyPlaceBlock(world, box, rand, f, 1, 0, z, rail);
                    }
                }
            }
            return true;
        }

        /** {@code setPlanksBlock}: inside, where the block has no full top. */
        private void setPlanks(World world, StructureBoundingBox box, IBlockState planks, int x, int y, int z) {
            if (this.getSkyBrightness(world, x, y, z, box) < 8) {
                BlockPos pos = new BlockPos(this.getXWithOffset(x, z), this.getYWithOffset(y), this.getZWithOffset(x, z));
                if (box.isVecInside(pos) && !sturdy(world, pos, EnumFacing.UP)) {
                    world.setBlockState(pos, planks, 2);
                }
            }
        }

        /** {@code placeDoubleLowerOrUpperSupport}: under both sides of a support that stands on planks. */
        private void placeDoubleLowerOrUpperSupport(World world, StructureBoundingBox box, int x, int y, int z) {
            IBlockState planks = planks(this.type);
            for (int side = x; side <= x + 2; side += 2) {
                if (this.getBlockStateFromPos(world, side, y, z, box).getBlock() == planks.getBlock()
                        && this.getBlockStateFromPos(world, side, y, z, box) == planks) {
                    BlockPos pos = new BlockPos(this.getXWithOffset(side, z), this.getYWithOffset(y), this.getZWithOffset(side, z));
                    pillarDownOrChainUp(world, log(this.type), fence(this.type), pos, box);
                }
            }
        }

        /** 1.12.2's support. */
        private void placeSupport(World world, StructureBoundingBox box, int x1, int y1, int z, int y2, int x2, Random rand) {
            if (this.isSupportingBox(world, box, x1, x2, y2, z)) {
                IBlockState planks = planks(this.type);
                IBlockState fence = fence(this.type);
                IBlockState air = Blocks.AIR.getDefaultState();
                this.fillWithBlocks(world, box, x1, y1, z, x1, y2 - 1, z, fence, air, false);
                this.fillWithBlocks(world, box, x2, y1, z, x2, y2 - 1, z, fence, air, false);
                if (rand.nextInt(4) == 0) {
                    this.fillWithBlocks(world, box, x1, y2, z, x1, y2, z, planks, air, false);
                    this.fillWithBlocks(world, box, x2, y2, z, x2, y2, z, planks, air, false);
                } else {
                    this.fillWithBlocks(world, box, x1, y2, z, x2, y2, z, planks, air, false);
                    this.randomlyPlaceBlock(world, box, rand, 0.05F, x1 + 1, y2, z - 1,
                            Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.NORTH));
                    this.randomlyPlaceBlock(world, box, rand, 0.05F, x1 + 1, y2, z + 1,
                            Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.SOUTH));
                }
            }
        }

        /** {@code maybePlaceCobWeb}: inside, by chance, and touching full faces on at least two sides. */
        private void placeCobWeb(World world, StructureBoundingBox box, Random rand, float chance, int x, int y, int z) {
            if (this.getSkyBrightness(world, x, y, z, box) < 8 && rand.nextFloat() < chance
                    && this.hasSturdyNeighbours(world, box, x, y, z, 2)) {
                this.setBlockState(world, Blocks.WEB.getDefaultState(), x, y, z, box);
            }
        }

        /** {@code hasSturdyNeighbours}. */
        private boolean hasSturdyNeighbours(World world, StructureBoundingBox box, int x, int y, int z, int needed) {
            BlockPos pos = new BlockPos(this.getXWithOffset(x, z), this.getYWithOffset(y), this.getZWithOffset(x, z));
            int n = 0;
            for (EnumFacing d : EnumFacing.values()) {
                BlockPos side = pos.offset(d);
                if (box.isVecInside(side) && sturdy(world, side, d.getOpposite()) && ++n >= needed) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A crossing: 1.12.2's, with 1.17's floor. */
    public static class Crossing extends StructureMineshaftPieces.Cross {
        private MapGenMineshaft.Type type = MapGenMineshaft.Type.NORMAL;
        private boolean twoFloors;

        public Crossing() {
        }

        @Override
        protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager templates) {
            super.readStructureFromNBT(tag, templates);
            this.type = type(tag);
            this.twoFloors = tag.getBoolean("tf");
        }

        @Override
        protected void setBlockState(World world, IBlockState state, int x, int y, int z, StructureBoundingBox box) {
            if (!on() || canBeReplaced(this.getBlockStateFromPos(world, x, y, z, box), this.type)) {
                super.setBlockState(world, state, x, y, z, box);
            }
        }

        @Override
        public boolean addComponentParts(World world, Random rand, StructureBoundingBox box) {
            if (!on()) {
                return super.addComponentParts(world, rand, box);
            }
            if (this.isLiquidInStructureBoundingBox(world, box)) {
                return false;
            }
            StructureBoundingBox bb = this.boundingBox;
            IBlockState air = Blocks.AIR.getDefaultState();
            if (this.twoFloors) {
                this.fillWithBlocks(world, box, bb.minX + 1, bb.minY, bb.minZ, bb.maxX - 1, bb.minY + 3 - 1, bb.maxZ, air, air, false);
                this.fillWithBlocks(world, box, bb.minX, bb.minY, bb.minZ + 1, bb.maxX, bb.minY + 3 - 1, bb.maxZ - 1, air, air, false);
                this.fillWithBlocks(world, box, bb.minX + 1, bb.maxY - 2, bb.minZ, bb.maxX - 1, bb.maxY, bb.maxZ, air, air, false);
                this.fillWithBlocks(world, box, bb.minX, bb.maxY - 2, bb.minZ + 1, bb.maxX, bb.maxY, bb.maxZ - 1, air, air, false);
                this.fillWithBlocks(world, box, bb.minX + 1, bb.minY + 3, bb.minZ + 1, bb.maxX - 1, bb.minY + 3, bb.maxZ - 1, air, air, false);
            } else {
                this.fillWithBlocks(world, box, bb.minX + 1, bb.minY, bb.minZ, bb.maxX - 1, bb.maxY, bb.maxZ, air, air, false);
                this.fillWithBlocks(world, box, bb.minX, bb.minY, bb.minZ + 1, bb.maxX, bb.maxY, bb.maxZ - 1, air, air, false);
            }
            this.placeSupportPillar(world, box, bb.minX + 1, bb.minY, bb.minZ + 1, bb.maxY);
            this.placeSupportPillar(world, box, bb.minX + 1, bb.minY, bb.maxZ - 1, bb.maxY);
            this.placeSupportPillar(world, box, bb.maxX - 1, bb.minY, bb.minZ + 1, bb.maxY);
            this.placeSupportPillar(world, box, bb.maxX - 1, bb.minY, bb.maxZ - 1, bb.maxY);
            IBlockState planks = planks(this.type);
            int y = bb.minY - 1;
            for (int x = bb.minX; x <= bb.maxX; x++) {
                for (int z = bb.minZ; z <= bb.maxZ; z++) {
                    // setPlanksBlock; a crossing has no orientation, so its coordinates are the world's.
                    if (this.getSkyBrightness(world, x, y, z, box) < 8) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (box.isVecInside(pos) && !sturdy(world, pos, EnumFacing.UP)) {
                            world.setBlockState(pos, planks, 2);
                        }
                    }
                }
            }
            return true;
        }

        private void placeSupportPillar(World world, StructureBoundingBox box, int x, int minY, int z, int maxY) {
            if (this.getBlockStateFromPos(world, x, maxY + 1, z, box).getMaterial() != Material.AIR) {
                this.fillWithBlocks(world, box, x, minY, z, x, maxY, z, planks(this.type), Blocks.AIR.getDefaultState(), false);
            }
        }
    }

    /** A room: 1.12.2's, which does not cut other pieces' supports. */
    public static class Room extends StructureMineshaftPieces.Room {
        private MapGenMineshaft.Type type = MapGenMineshaft.Type.NORMAL;

        public Room() {
        }

        @Override
        protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager templates) {
            super.readStructureFromNBT(tag, templates);
            this.type = type(tag);
        }

        @Override
        protected void setBlockState(World world, IBlockState state, int x, int y, int z, StructureBoundingBox box) {
            if (!on() || canBeReplaced(this.getBlockStateFromPos(world, x, y, z, box), this.type)) {
                super.setBlockState(world, state, x, y, z, box);
            }
        }
    }

    /** Stairs: 1.12.2's, which do not cut other pieces' supports. */
    public static class Stairs extends StructureMineshaftPieces.Stairs {
        private MapGenMineshaft.Type type = MapGenMineshaft.Type.NORMAL;

        public Stairs() {
        }

        @Override
        protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager templates) {
            super.readStructureFromNBT(tag, templates);
            this.type = type(tag);
        }

        @Override
        protected void setBlockState(World world, IBlockState state, int x, int y, int z, StructureBoundingBox box) {
            if (!on() || canBeReplaced(this.getBlockStateFromPos(world, x, y, z, box), this.type)) {
                super.setBlockState(world, state, x, y, z, box);
            }
        }
    }
}
