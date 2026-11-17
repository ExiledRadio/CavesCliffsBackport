package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.changes.ExperienceOrbs;
import com.exiledradio.cavesandcliffsbackport.changes.Mineshafts;
import com.exiledradio.cavesandcliffsbackport.spawning.LocalMobCap;
import com.exiledradio.cavesandcliffsbackport.spawning.SnowSpawns;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSand;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureMineshaftPieces;
import net.minecraft.world.gen.structure.StructureMineshaftStart;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.TerrainGen;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The 1.17 and 1.18 changes of the "Other Changes" page: the per-player mob cap ({@link LocalMobCap}), rabbits and
 * wolves on snow ({@link SnowSpawns}), experience orbs ({@link ExperienceOrbs}) and mineshaft supports ({@link
 * Mineshafts}), each also with its setting off.
 */
public final class ChangesSelfTest implements SelfTest {

    @Override
    public String name() {
        return "changes";
    }

    @Override
    public void run(Check c) throws Exception {
        boolean cap = ModConfig.changes.localMobCap;
        boolean snow = ModConfig.changes.snowSpawns;
        boolean orbs = ModConfig.changes.experienceOrbs;
        boolean shafts = ModConfig.changes.mineshaftSupports;
        try {
            ModConfig.changes.localMobCap = true;
            ModConfig.changes.snowSpawns = true;
            ModConfig.changes.experienceOrbs = true;
            ModConfig.changes.mineshaftSupports = true;
            mobCap(c);
            snowSpawns(c);
            experienceOrbs(c);
            mineshafts(c);
        } finally {
            ModConfig.changes.localMobCap = cap;
            ModConfig.changes.snowSpawns = snow;
            ModConfig.changes.experienceOrbs = orbs;
            ModConfig.changes.mineshaftSupports = shafts;
            LocalMobCap.forget(c.world);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Per-player mob cap

    private static boolean offered(WorldServer world, EnumCreatureType type, BlockPos pos) {
        LocalMobCap.forget(world);
        return offeredSameTick(world, type, pos);
    }

    /** What WorldEntitySpawner is offered at a place: Forge's PotentialSpawns, as WorldServer fires it. */
    private static boolean offeredSameTick(WorldServer world, EnumCreatureType type, BlockPos pos) {
        List<Biome.SpawnListEntry> list = new ArrayList<Biome.SpawnListEntry>();
        list.add(new Biome.SpawnListEntry(type == EnumCreatureType.MONSTER ? EntityZombie.class : EntityPig.class, 100, 1, 1));
        List<Biome.SpawnListEntry> out = ForgeEventFactory.getPotentialSpawns(world, type, pos, list);
        return out != null && !out.isEmpty();
    }

    private static void mobCap(Check c) {
        WorldServer world = c.world;
        BlockPos corner = c.area();
        BlockPos a = corner.add(16, 0, 16);
        EntityPlayer pa = c.player("capA", a);
        EntityPlayer pb = c.player("capB", a.add(200, 0, 0));
        List<EntityZombie> zombies = new ArrayList<EntityZombie>();
        world.playerEntities.add(pa);
        world.playerEntities.add(pb);
        try {
            LocalMobCap.forget(world);
            int before = LocalMobCap.count(world, pa, EnumCreatureType.MONSTER);
            c.ok(before < 70, "room for monsters around the first player at the start, " + before + " counted");
            c.ok(offered(world, EnumCreatureType.MONSTER, a), "monsters spawn near a player with room");
            c.ok(!offered(world, EnumCreatureType.MONSTER, a.add(0, 0, 400)), "no spawns in a chunk 128 blocks or more from every player");

            for (int i = before; i < 70; i++) {
                EntityZombie z = new EntityZombie(world);
                z.setPosition(corner.getX() + 2 + i % 28, 100, corner.getZ() + 2 + i / 28);
                world.spawnEntity(z);
                zombies.add(z);
            }
            LocalMobCap.forget(world);
            c.eq(70, LocalMobCap.count(world, pa, EnumCreatureType.MONSTER), "monsters counted toward the first player's own cap");
            c.eq(0, LocalMobCap.count(world, pb, EnumCreatureType.MONSTER), "none toward the far player's");
            c.ok(!offered(world, EnumCreatureType.MONSTER, a), "the first player's cap is full: no monsters near them");
            c.ok(!offered(world, EnumCreatureType.MONSTER, a.add(-100, 0, 0)), "... nor 100 blocks away, where only they are near");
            c.ok(offered(world, EnumCreatureType.MONSTER, a.add(100, 0, 0)), "a chunk near both players: the far one has room");
            c.ok(offered(world, EnumCreatureType.MONSTER, a.add(200, 0, 0)), "monsters still spawn near the far player");
            c.ok(offered(world, EnumCreatureType.CREATURE, a), "a full monster cap leaves animals alone");

            for (int i = 0; i < 5; i++) {
                zombies.get(i).enablePersistence();
            }
            LocalMobCap.forget(world);
            c.eq(65, LocalMobCap.count(world, pa, EnumCreatureType.MONSTER), "persistent monsters do not count");
            c.ok(offered(world, EnumCreatureType.MONSTER, a), "room again with five persistent");

            // Within one tick: the counts taken once, each chunk decided once, and a mob that joins counts at once.
            for (int i = 5; i < 9; i++) {
                zombies.get(i).setDead();
            }
            LocalMobCap.forget(world);
            c.eq(61, LocalMobCap.count(world, pa, EnumCreatureType.MONSTER), "dead monsters do not count");
            c.ok(offeredSameTick(world, EnumCreatureType.MONSTER, a), "the chunk decided with 61 counted");
            int need = 70 - LocalMobCap.count(world, pa, EnumCreatureType.MONSTER);
            for (int i = 0; i < need; i++) {
                EntityZombie z = new EntityZombie(world);
                z.setPosition(corner.getX() + 3 + i, 100, corner.getZ() + 20);
                world.spawnEntity(z);
                zombies.add(z);
            }
            c.eq(70, LocalMobCap.count(world, pa, EnumCreatureType.MONSTER), "monsters that join in the tick count at once");
            c.ok(offeredSameTick(world, EnumCreatureType.MONSTER, a), "a chunk already decided this tick keeps its answer");
            c.ok(!offeredSameTick(world, EnumCreatureType.MONSTER, a.add(16, 0, 0)), "a chunk decided after them sees the full cap");

            ModConfig.changes.localMobCap = false;
            c.ok(offered(world, EnumCreatureType.MONSTER, a), "off: the full cap does not stop spawns");
            c.ok(offered(world, EnumCreatureType.MONSTER, a.add(0, 0, 400)), "off: nor does the distance");
            ModConfig.changes.localMobCap = true;
        } finally {
            ModConfig.changes.localMobCap = true;
            world.playerEntities.remove(pa);
            world.playerEntities.remove(pb);
            for (EntityZombie z : zombies) {
                world.removeEntityDangerously(z);
            }
            LocalMobCap.forget(world);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Rabbits and wolves on snow

    private static Event.Result spawnOn(WorldServer world, EntityLiving mob, BlockPos floor, IBlockState below) {
        world.setBlockState(floor, below, 2);
        mob.setLocationAndAngles(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0.0F, 0.0F);
        return ForgeEventFactory.canEntitySpawn(mob, world, (float) mob.posX, (float) mob.posY, (float) mob.posZ, false);
    }

    private static void snowSpawns(Check c) {
        WorldServer world = c.world;
        BlockPos corner = c.area();
        BlockPos floor = corner.add(8, 0, 8);
        IBlockState snowBlock = Blocks.SNOW.getDefaultState();
        IBlockState sand = Blocks.SAND.getDefaultState();
        IBlockState redSand = Blocks.SAND.getDefaultState().withProperty(BlockSand.VARIANT, BlockSand.EnumType.RED_SAND);
        c.ok(world.getLight(floor.up()) > 8, "the open floor is light");

        c.eq(Event.Result.ALLOW, spawnOn(world, new EntityRabbit(world), floor, snowBlock), "a rabbit spawns on a snow block");
        c.eq(Event.Result.ALLOW, spawnOn(world, new EntityWolf(world), floor, snowBlock), "a wolf spawns on a snow block");
        c.eq(Event.Result.ALLOW, spawnOn(world, new EntityRabbit(world), floor, sand), "a rabbit spawns on sand");
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityWolf(world), floor, sand), "a wolf does not on sand");
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityRabbit(world), floor, redSand), "a rabbit does not on red sand");
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityPig(world), floor, snowBlock), "other animals keep the grass block");
        EntityRabbit onGrass = new EntityRabbit(world);
        c.eq(Event.Result.DEFAULT, spawnOn(world, onGrass, floor, Blocks.GRASS.getDefaultState()), "on grass, 1.12.2's test runs");
        c.ok(onGrass.getCanSpawnHere(), "... and lets it spawn");
        EntityRabbit onSnow = new EntityRabbit(world);
        spawnOn(world, onSnow, floor, snowBlock);
        c.ok(!onSnow.getCanSpawnHere(), "1.12.2's own test turns a rabbit on snow away");

        // A dark cell: light 8 or less, no spawn.
        BlockPos dark = corner.add(20, 0, 20);
        c.fill(dark.add(-1, 0, -1), dark.add(1, 3, 1), Blocks.STONE.getDefaultState());
        c.fill(dark.up(), dark.up(2), Blocks.AIR.getDefaultState());
        c.ok(world.getLight(dark.up()) <= 8, "the closed cell is dark, light " + world.getLight(dark.up()));
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityWolf(world), dark, snowBlock), "no wolf on snow in the dark");

        // Something in the way.
        world.setBlockState(floor.up(), Blocks.STONE.getDefaultState(), 2);
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityRabbit(world), floor, snowBlock), "no rabbit inside a block");
        world.setBlockState(floor.up(), Blocks.AIR.getDefaultState(), 2);

        ModConfig.changes.snowSpawns = false;
        c.eq(Event.Result.DEFAULT, spawnOn(world, new EntityWolf(world), floor, snowBlock), "off: a wolf on snow is left to 1.12.2");
        ModConfig.changes.snowSpawns = true;
    }

    // ------------------------------------------------------------------------------------------
    // Experience orbs

    private static EntityXPOrb orb(WorldServer world, BlockPos at, double dx, int value) {
        EntityXPOrb o = new EntityXPOrb(world, at.getX() + 0.5 + dx, at.getY() + 0.1, at.getZ() + 0.5, value);
        o.motionX = 0;
        o.motionY = 0;
        o.motionZ = 0;
        return o;
    }

    private static List<EntityXPOrb> orbsAt(WorldServer world, BlockPos at, int value) {
        List<EntityXPOrb> out = new ArrayList<EntityXPOrb>();
        for (EntityXPOrb o : world.getEntitiesWithinAABB(EntityXPOrb.class, new AxisAlignedBB(at).grow(1.5D))) {
            if (!o.isDead && o.xpValue == value) {
                out.add(o);
            }
        }
        return out;
    }

    private static int total(List<EntityXPOrb> orbs) {
        int n = 0;
        for (EntityXPOrb o : orbs) {
            n += ExperienceOrbs.count(o);
        }
        return n;
    }

    private static void experienceOrbs(Check c) throws Exception {
        WorldServer world = c.world;
        BlockPos corner = c.area();

        // Merging when made.
        BlockPos p = corner.add(4, 0, 4);
        for (int i = 0; i < 400; i++) {
            world.spawnEntity(orb(world, p, 0, 7));
        }
        List<EntityXPOrb> made = orbsAt(world, p, 7);
        c.ok(made.size() < 120, "400 orbs made in one place merge as they are made, " + made.size() + " left");
        c.eq(400, total(made), "their counts add up to 400");
        for (EntityXPOrb o : made) {
            o.ticksExisted = 21;
        }
        ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        made = orbsAt(world, p, 7);
        c.ok(made.size() <= 40, "after a second at most 40 are left, one per id group, got " + made.size());
        c.eq(400, total(made), "... still 400 in all");
        world.spawnEntity(orb(world, p, 0, 3));
        List<EntityXPOrb> three = orbsAt(world, p, 3);
        c.ok(three.size() == 1 && ExperienceOrbs.count(three.get(0)) == 1, "an orb of another size does not join them");

        // Picking up a counted orb.
        BlockPos q = corner.add(12, 0, 4);
        EntityPlayer player = c.player("orbs", q);
        player.experienceTotal = 0;
        player.experience = 0;
        player.experienceLevel = 0;
        EntityXPOrb counted = orb(world, q, 0, 5);
        ExperienceOrbs.setCount(counted, 3);
        world.spawnEntity(counted);
        for (int i = 1; i <= 3; i++) {
            List<EntityXPOrb> here = orbsAt(world, q, 5);
            c.eq(1, here.size(), "pickup " + i + ": one orb");
            if (here.size() != 1) {
                break;
            }
            EntityXPOrb o = here.get(0);
            c.eq(4 - i, ExperienceOrbs.count(o), "pickup " + i + ": the orb's count");
            o.delayBeforeCanPickup = 0;
            player.xpCooldown = 0;
            o.onCollideWithPlayer(player);
            c.eq(5 * i, player.experienceTotal, "pickup " + i + ": experience given");
            c.ok(o.isDead, "pickup " + i + ": the orb picked up is gone");
        }
        c.eq(0, orbsAt(world, q, 5).size(), "the last pickup leaves nothing");

        // Merging while lying around: same id group (ids 40 apart), touching, same value.
        BlockPos m = corner.add(20, 0, 4);
        EntityXPOrb first = orb(world, m, 0, 17);
        for (int i = 0; i < 39; i++) {
            new EntityXPOrb(world);
        }
        EntityXPOrb grouped = orb(world, m.add(0, 0, 6), 0, 17);
        EntityXPOrb other = orb(world, m.add(0, 0, -6), 0, 17);
        c.eq(0, (grouped.getEntityId() - first.getEntityId()) % 40, "two orbs in one id group");
        world.spawnEntity(first);
        world.spawnEntity(grouped);
        world.spawnEntity(other);
        grouped.setPosition(first.posX + 0.3, first.posY, first.posZ);
        other.setPosition(first.posX - 0.3, first.posY, first.posZ);
        first.xpOrbAge = 500;
        grouped.xpOrbAge = 100;
        first.ticksExisted = 21;
        grouped.ticksExisted = 5;
        other.ticksExisted = 5;
        ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
        c.ok(grouped.isDead, "the orb of its group merged in");
        c.eq(2, ExperienceOrbs.count(first), "the merged orb stands for two");
        c.eq(100, first.xpOrbAge, "it keeps the younger age");
        c.ok(!other.isDead && ExperienceOrbs.count(other) == 1, "an orb of another id group stays apart");

        ModConfig.changes.experienceOrbs = false;
        BlockPos off = corner.add(4, 0, 20);
        for (int i = 0; i < 50; i++) {
            world.spawnEntity(orb(world, off, 0, 7));
        }
        c.eq(50, orbsAt(world, off, 7).size(), "off: orbs made in one place stay apart");
        ModConfig.changes.experienceOrbs = true;

        deadPlayers(c, corner.add(20, 0, 20));
    }

    private static void deadPlayers(Check c, BlockPos at) throws Exception {
        WorldServer world = c.world;
        Field target = ObfuscationReflectionHelper.findField(EntityXPOrb.class, "field_80002_g");
        EntityPlayer dead = c.player("orbsDead", at.add(2, 0, 0));
        EntityPlayer alive = c.player("orbsAlive", at.add(-5, 0, 0));
        dead.setHealth(0.0F);
        alive.setHealth(20.0F);
        world.playerEntities.add(dead);
        world.playerEntities.add(alive);
        try {
            EntityXPOrb o = orb(world, at, 0, 1);
            world.spawnEntity(o);
            // Ask the orb to look for a player this tick, as 1.12.2 does every so often.
            target.setInt(o, Integer.MIN_VALUE / 2);
            ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, world));
            c.ok(ExperienceOrbs.following(o) == alive, "an orb near a dead player follows the living one");
            o.onUpdate();
            c.ok(ExperienceOrbs.following(o) == alive, "... and does not turn to the dead one while it moves");
            ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));
            c.ok(target.getInt(o) != Integer.MAX_VALUE, "its search is let go after the tick");

            world.playerEntities.remove(alive);
            target.setInt(o, Integer.MIN_VALUE / 2);
            ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.START, world));
            o.onUpdate();
            c.ok(ExperienceOrbs.following(o) == null, "with only a dead player near, it follows no one");
            ExperienceOrbs.worldTick(new TickEvent.WorldTickEvent(Side.SERVER, TickEvent.Phase.END, world));

            ModConfig.changes.experienceOrbs = false;
            target.setInt(o, Integer.MIN_VALUE / 2);
            o.onUpdate();
            c.ok(ExperienceOrbs.following(o) == dead, "off: 1.12.2's orb follows the dead player");
            ModConfig.changes.experienceOrbs = true;
            o.setDead();
        } finally {
            ModConfig.changes.experienceOrbs = true;
            world.playerEntities.remove(dead);
            world.playerEntities.remove(alive);
            dead.setHealth(20.0F);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mineshafts

    /** A corridor along +z (SOUTH), three sections, without rails or spiders, read back as Caves & Cliffs' own. */
    private static StructureComponent corridor(Check c, BlockPos min) {
        StructureBoundingBox bb = new StructureBoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + 2, min.getY() + 2, min.getZ() + 14);
        StructureMineshaftPieces.Corridor vanilla = new StructureMineshaftPieces.Corridor(0, new Random(1), bb, EnumFacing.SOUTH,
                MapGenMineshaft.Type.NORMAL);
        NBTTagCompound tag = vanilla.createStructureBaseNBT();
        tag.setBoolean("hr", false);
        tag.setBoolean("sc", false);
        tag.setInteger("Num", 3);
        return MapGenStructureIO.getStructureComponent(tag, c.world);
    }

    private static int sturdySides(WorldServer world, BlockPos pos) {
        int n = 0;
        for (EnumFacing d : EnumFacing.values()) {
            BlockPos side = pos.offset(d);
            if (world.getBlockState(side).getBlockFaceShape(world, side, d.getOpposite()) == BlockFaceShape.SOLID) {
                n++;
            }
        }
        return n;
    }

    /** Cobwebs in a box, and how many of them touch fewer than two full faces. */
    private static int[] webs(WorldServer world, BlockPos from, BlockPos to, boolean clear) {
        int all = 0;
        int floating = 0;
        for (BlockPos p : BlockPos.getAllInBox(from, to)) {
            if (world.getBlockState(p).getBlock() == Blocks.WEB) {
                all++;
                if (sturdySides(world, p) < 2) {
                    floating++;
                }
                if (clear) {
                    world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        return new int[] {all, floating};
    }

    private static void mineshafts(Check c) {
        WorldServer world = c.world;

        StructureMineshaftStart start = new StructureMineshaftStart(world, new Random(7), 1000, 1000, MapGenMineshaft.Type.NORMAL);
        int pieces = start.getComponents().size();
        StructureStart read = Mineshafts.modernize(world, start);
        boolean ours = true;
        for (StructureComponent p : read.getComponents()) {
            ours &= p instanceof Mineshafts.Corridor || p instanceof Mineshafts.Crossing || p instanceof Mineshafts.Room
                    || p instanceof Mineshafts.Stairs;
        }
        c.eq(pieces, read.getComponents().size(), "a start read back keeps its pieces");
        c.ok(ours, "... each one Caves & Cliffs' own");
        c.eq(start.getBoundingBox().toString(), read.getBoundingBox().toString(), "... and its size");
        MapGenBase gen = TerrainGen.getModdedMapGen(new MapGenMineshaft(), InitMapGenEvent.EventType.MINESHAFT);
        c.ok(gen instanceof Mineshafts.Generator, "the Default world's mineshafts are Caves & Cliffs' generator");

        BlockPos o = c.area();
        int ox = o.getX();
        int oz = o.getZ();
        IBlockState stone = Blocks.STONE.getDefaultState();
        IBlockState air = Blocks.AIR.getDefaultState();
        c.fill(new BlockPos(ox, 100, oz), new BlockPos(ox + 20, 150, oz + 20), stone);
        // A: a ravine 18 deep under the last support.
        c.fill(new BlockPos(ox + 1, 101, oz + 10), new BlockPos(ox + 3, 119, oz + 16), air);
        // B: a pit 30 deep under it, a cave up to y 144 over it.
        c.fill(new BlockPos(ox + 7, 100, oz + 10), new BlockPos(ox + 9, 129, oz + 16), air);
        c.fill(new BlockPos(ox + 7, 133, oz + 10), new BlockPos(ox + 9, 144, oz + 16), air);
        // C: as A, built with the setting off.
        c.fill(new BlockPos(ox + 13, 101, oz + 10), new BlockPos(ox + 15, 119, oz + 16), air);
        c.eq(0, world.getLightFor(EnumSkyBlock.SKY, new BlockPos(ox + 2, 110, oz + 13)), "the ravine is dark");
        c.eq(0, world.getLightFor(EnumSkyBlock.SKY, new BlockPos(ox + 8, 140, oz + 13)), "the cave is dark");
        IBlockState fence = Blocks.OAK_FENCE.getDefaultState();
        world.setBlockState(new BlockPos(ox + 2, 120, oz + 5), fence, 2);
        world.setBlockState(new BlockPos(ox + 14, 120, oz + 5), fence, 2);

        StructureBoundingBox all = new StructureBoundingBox(ox - 8, 0, oz - 8, ox + 40, 255, oz + 40);
        StructureComponent a = corridor(c, new BlockPos(ox + 1, 120, oz + 1));
        StructureComponent b = corridor(c, new BlockPos(ox + 7, 130, oz + 1));
        c.ok(a instanceof Mineshafts.Corridor, "a corridor read back is Caves & Cliffs' own");
        a.addComponentParts(world, new Random(3), all);
        b.addComponentParts(world, new Random(3), all);

        IBlockState planks = Blocks.PLANKS.getDefaultState();
        for (int x : new int[] {ox + 1, ox + 3}) {
            c.eq(planks, world.getBlockState(new BlockPos(x, 119, oz + 13)), "A: a plank bridge over the ravine at x " + x);
            boolean logs = true;
            for (int y = 101; y <= 118; y++) {
                IBlockState s = world.getBlockState(new BlockPos(x, y, oz + 13));
                logs &= s.getBlock() == Blocks.LOG && s.getValue(BlockLog.LOG_AXIS) == BlockLog.EnumAxis.Y;
            }
            c.ok(logs, "A: an oak log pillar from the ravine floor up to the bridge at x " + x);
        }
        c.eq(fence, world.getBlockState(new BlockPos(ox + 2, 120, oz + 5)).getBlock().getDefaultState(),
                "A: a fence already there is not carved out");

        IBlockState chain = Mineshafts.chain(fence);
        for (int x : new int[] {ox + 7, ox + 9}) {
            c.eq(planks, world.getBlockState(new BlockPos(x, 129, oz + 13)), "B: a plank bridge over the pit at x " + x);
            c.eq(air, world.getBlockState(new BlockPos(x, 120, oz + 13)), "B: no pillar from 30 blocks down at x " + x);
            c.eq(Blocks.OAK_FENCE, world.getBlockState(new BlockPos(x, 130, oz + 13)).getBlock(), "B: a fence on the bridge at x " + x);
            // Up to the first ceiling: the cave's at y 145, or the corridor's own top at y 132 where 1.12.2's
            // corridor (as 1.18.2's) left a fifth of it standing.
            int y = 131;
            while (y < 146 && world.getBlockState(new BlockPos(x, y, oz + 13)).getBlock() == chain.getBlock()) {
                y++;
            }
            c.ok(y > 131 && world.getBlockState(new BlockPos(x, y, oz + 13)) == stone && (y == 132 || y == 145),
                    "B: a chain up to the ceiling at x " + x + " (" + chain.getBlock().getRegistryName() + "), up to y " + y);
        }

        ModConfig.changes.mineshaftSupports = false;
        StructureComponent off = corridor(c, new BlockPos(ox + 13, 120, oz + 1));
        off.addComponentParts(world, new Random(3), all);
        c.eq(planks, world.getBlockState(new BlockPos(ox + 13, 119, oz + 13)), "off: 1.12.2's plank bridge");
        c.eq(air, world.getBlockState(new BlockPos(ox + 13, 110, oz + 13)), "off: no log pillar");
        c.eq(air, world.getBlockState(new BlockPos(ox + 14, 120, oz + 5)), "off: the corridor carves through a fence");
        ModConfig.changes.mineshaftSupports = true;

        // Cobwebs: corridor B (a cave above, a pit below) built with many seeds.
        BlockPos webFrom = new BlockPos(ox + 7, 130, oz + 1);
        BlockPos webTo = new BlockPos(ox + 9, 132, oz + 15);
        webs(world, webFrom, webTo, true);
        int onAll = 0;
        int onFloating = 0;
        for (int seed = 0; seed < 40; seed++) {
            corridor(c, new BlockPos(ox + 7, 130, oz + 1)).addComponentParts(world, new Random(seed), all);
            int[] w = webs(world, webFrom, webTo, true);
            onAll += w[0];
            onFloating += w[1];
        }
        c.ok(onAll > 0, "cobwebs still generate, " + onAll + " in 40 corridors");
        c.eq(0, onFloating, "no cobweb touches fewer than two full faces");
        ModConfig.changes.mineshaftSupports = false;
        int offFloating = 0;
        for (int seed = 0; seed < 40; seed++) {
            corridor(c, new BlockPos(ox + 7, 130, oz + 1)).addComponentParts(world, new Random(seed), all);
            offFloating += webs(world, webFrom, webTo, true)[1];
        }
        c.ok(offFloating > 0, "off: 1.12.2 leaves cobwebs floating, " + offFloating + " in 40 corridors");
        ModConfig.changes.mineshaftSupports = true;
    }
}
