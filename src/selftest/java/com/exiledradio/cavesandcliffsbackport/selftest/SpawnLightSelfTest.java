package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.blocks.BlockLight;
import com.exiledradio.cavesandcliffsbackport.blocks.BlocksContent;
import com.exiledradio.cavesandcliffsbackport.spawning.SpawnLight;

import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * 1.18's monster spawn light rule ({@link SpawnLight}): block light 0 or no spawn, the sky and raw-brightness tests as
 * 1.18.2's {@code Monster.isDarkEnoughToSpawn}, in the open and underground, in thunder, from spawners, and 1.12.2's
 * rule with the setting off. Each case is tried {@value #TRIES} times, as the tests draw random numbers.
 */
public final class SpawnLightSelfTest implements SelfTest {

    private static final int TRIES = 800;

    @Override
    public String name() {
        return "spawnlight";
    }

    @Override
    public void run(Check c) throws Exception {
        WorldServer world = c.world;
        EnumDifficulty difficulty = world.getDifficulty();
        int darken = world.getSkylightSubtracted();
        boolean setting = ModConfig.changes.monsterSpawnLight;
        float[] weather = weather(world);
        try {
            world.getWorldInfo().setDifficulty(EnumDifficulty.NORMAL);
            setWeather(world, 0.0F, 0.0F);
            world.setSkylightSubtracted(0);
            ModConfig.changes.monsterSpawnLight = true;
            whichMobs(c);
            BlockPos corner = c.area();
            BlockPos cave = chamber(c, corner.add(2, 0, 2));
            BlockPos open = corner.add(24, 0, 24);
            BlockPos spawnerPos = corner.add(2, -1, 24);
            world.setBlockState(spawnerPos, Blocks.MOB_SPAWNER.getDefaultState(), 2);
            TileEntity te = world.getTileEntity(spawnerPos);
            MobSpawnerBaseLogic spawner = te instanceof TileEntityMobSpawner ? ((TileEntityMobSpawner) te).getSpawnerBaseLogic() : null;
            c.ok(spawner != null, "a spawner to test with");

            c.eq(0, world.getLightFor(EnumSkyBlock.SKY, cave), "sky light in the closed cave");
            c.eq(15, world.getLightFor(EnumSkyBlock.SKY, open), "sky light in the open");

            underground(c, world, cave, spawner);
            openSky(c, world, open);
            thunder(c, world, cave, open);
            peaceful(c, world, cave);
            settingOff(c, world, cave, spawner);
        } finally {
            ModConfig.changes.monsterSpawnLight = setting;
            world.getWorldInfo().setDifficulty(difficulty);
            world.setSkylightSubtracted(darken);
            setWeather(world, weather[0], weather[1]);
        }
    }

    /** Monsters that keep 1.12.2's light test get 1.18's; the ones with their own spawn rules keep those. */
    private static void whichMobs(Check c) {
        World w = c.world;
        EntityMob[] use = {new EntityZombie(w), new EntitySkeleton(w), new EntityCreeper(w), new EntitySpider(w),
                new EntityWitherSkeleton(w), new EntityHusk(w), new EntityStray(w)};
        for (EntityMob m : use) {
            c.ok(SpawnLight.applies(m), m.getClass().getSimpleName() + " gets 1.18's spawn light rule");
        }
        EntityMob[] own = {new EntityBlaze(w), new EntitySilverfish(w), new EntityEndermite(w), new EntityGuardian(w),
                new EntityPigZombie(w)};
        for (EntityMob m : own) {
            c.ok(!SpawnLight.applies(m), m.getClass().getSimpleName() + " keeps its own spawn rules");
        }
        c.eq(Loader.isModLoaded("wildupdatebackport") && SpawnLight.wildUpdateOwnsNether(), SpawnLight.wildUpdateOwnsNether(),
                "the Nether is Wild Update's only when it is installed with its setting on");
    }

    /** Underground: sky light 0, so the raw brightness is the block light, and only block light 0 spawns. */
    private static void underground(Check c, WorldServer world, BlockPos cave, MobSpawnerBaseLogic spawner) {
        c.eq(TRIES, natural(world, new EntityZombie(world), cave, 0), "underground, block light 0: every try spawns");
        c.eq(0, natural(world, new EntityZombie(world), cave, 1), "underground, block light 1: no spawn");
        c.eq(0, natural(world, new EntityZombie(world), cave, 7), "underground, block light 7: no spawn");
        c.eq(TRIES, natural(world, new EntityCreeper(world), cave, 0), "a creeper underground at block light 0 spawns");
        c.eq(0, natural(world, new EntityHusk(world), cave, 0), "a husk still needs the sky");
        if (spawner != null) {
            c.eq(TRIES, fromSpawner(world, new EntityZombie(world), cave, 0, spawner), "a spawner at block light 0 spawns");
            c.eq(0, fromSpawner(world, new EntityZombie(world), cave, 1, spawner), "a spawner at block light 1 does not (1.18.2's BaseSpawner)");
            c.eq(0, fromSpawner(world, new EntityZombie(world), cave, 7, spawner), "a spawner at block light 7 does not");
            // Blazes keep 1.12.2's rules, which ignore light.
            c.ok(fromSpawner(world, new EntityBlaze(world), cave, 7, spawner) > 0, "a blaze spawner in light still spawns");
        }
    }

    /** Under the open sky in daytime nothing spawns; at night (darkened by 11) a quarter of tries do, none in block light. */
    private static void openSky(Check c, WorldServer world, BlockPos open) {
        world.setSkylightSubtracted(0);
        c.eq(0, natural(world, new EntityZombie(world), open, 0), "open sky, day, block light 0: no spawn");
        world.setSkylightSubtracted(11);
        try {
            // Sky light 15 <= rand(32) half the time, raw brightness 4 <= rand(8) half the time.
            int n = natural(world, new EntityZombie(world), open, 0);
            c.ok(n > TRIES * 0.17 && n < TRIES * 0.33, "open sky, night, block light 0: about a quarter of tries spawn, got " + n + "/" + TRIES);
            c.eq(0, natural(world, new EntityZombie(world), open, 1), "open sky, night, block light 1: no spawn");
            c.eq(0, natural(world, new EntityZombie(world), open, 7), "open sky, night, block light 7: no spawn");
            c.ok(natural(world, new EntityHusk(world), open, 0) > 0, "a husk under the night sky spawns");
        } finally {
            world.setSkylightSubtracted(0);
        }
    }

    /**
     * A thunderstorm darkens the sky by 10 for the test: at noon, raw brightness 5 lets some through. The world's own
     * darkening at a thundery noon is 5 (World.calculateSkylightSubtracted), which alone would let none through.
     */
    private static void thunder(Check c, WorldServer world, BlockPos cave, BlockPos open) {
        world.setSkylightSubtracted(5);
        c.eq(0, natural(world, new EntityZombie(world), open, 0), "no thunder, sky darkened by 5: no spawn");
        setWeather(world, 1.0F, 1.0F);
        try {
            c.ok(world.isThundering(), "the test's thunderstorm");
            // Sky 15 <= rand(32) half the time, 5 <= rand(8) three times in eight: 3/16.
            int n = natural(world, new EntityZombie(world), open, 0);
            c.ok(n > TRIES * 0.11 && n < TRIES * 0.27, "thunder, open sky, day, block light 0: about 3/16 of tries spawn, got " + n + "/" + TRIES);
            c.eq(0, natural(world, new EntityZombie(world), open, 1), "thunder, open sky, block light 1: no spawn");
            c.eq(TRIES, natural(world, new EntityZombie(world), cave, 0), "thunder, underground, block light 0: every try spawns");
            c.eq(0, natural(world, new EntityZombie(world), cave, 1), "thunder, underground, block light 1: no spawn");
        } finally {
            setWeather(world, 0.0F, 0.0F);
            world.setSkylightSubtracted(0);
        }
        c.ok(!world.isThundering(), "the thunderstorm is over");
    }

    private static void peaceful(Check c, WorldServer world, BlockPos cave) {
        world.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
        try {
            c.eq(0, natural(world, new EntityZombie(world), cave, 0), "Peaceful: no spawn");
        } finally {
            world.getWorldInfo().setDifficulty(EnumDifficulty.NORMAL);
        }
    }

    /** Off: 1.12.2's light test, combined light <= rand(8), spawners included. */
    private static void settingOff(Check c, WorldServer world, BlockPos cave, MobSpawnerBaseLogic spawner) {
        ModConfig.changes.monsterSpawnLight = false;
        try {
            EntityZombie zombie = new EntityZombie(world);
            place(zombie, cave);
            c.eq(Event.Result.DEFAULT, ForgeEventFactory.canEntitySpawn(zombie, world, (float) zombie.posX, (float) zombie.posY,
                    (float) zombie.posZ, (MobSpawnerBaseLogic) null), "off: the spawn check is left to vanilla");
            int one = natural(world, new EntityZombie(world), cave, 1);
            c.ok(one > TRIES * 0.8, "off: block light 1 spawns 7 tries in 8, got " + one + "/" + TRIES);
            int seven = natural(world, new EntityZombie(world), cave, 7);
            c.ok(seven > TRIES * 0.06 && seven < TRIES * 0.2, "off: block light 7 spawns 1 try in 8, got " + seven + "/" + TRIES);
            c.eq(0, natural(world, new EntityZombie(world), cave, 8), "off: block light 8 never spawns");
            if (spawner != null) {
                int s = fromSpawner(world, new EntityZombie(world), cave, 1, spawner);
                c.ok(s > TRIES * 0.8, "off: a spawner at block light 1 spawns 7 tries in 8, got " + s + "/" + TRIES);
            }
        } finally {
            ModConfig.changes.monsterSpawnLight = true;
        }
    }

    // ------------------------------------------------------------------------------------------

    /** A closed stone room, 5x5 inside and 3 high, with its stone floor at the corner's height: returns its middle. */
    private static BlockPos chamber(Check c, BlockPos corner) {
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = 0; dz <= 6; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    boolean shell = dx == 0 || dx == 6 || dz == 0 || dz == 6 || dy == 0 || dy == 4;
                    c.world.setBlockState(corner.add(dx, dy, dz), shell ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
        return corner.add(3, 1, 3);
    }

    /** Spawns tried at {@code pos} with block light {@code light} there (a light block next to it), out of {@link #TRIES}. */
    private static int natural(WorldServer world, EntityMob mob, BlockPos pos, int light) {
        return tries(world, mob, pos, light, null);
    }

    private static int fromSpawner(WorldServer world, EntityMob mob, BlockPos pos, int light, MobSpawnerBaseLogic spawner) {
        return tries(world, mob, pos, light, spawner);
    }

    private static int tries(WorldServer world, EntityMob mob, BlockPos pos, int light, MobSpawnerBaseLogic spawner) {
        BlockPos lamp = pos.east();
        BlockLight block = BlocksContent.light;
        if (light > 0) {
            world.setBlockState(lamp, block.getDefaultState().withProperty(BlockLight.LEVEL, light + 1), 2);
        }
        try {
            if (world.getLightFor(EnumSkyBlock.BLOCK, pos) != light) {
                return -1000 - world.getLightFor(EnumSkyBlock.BLOCK, pos);
            }
            place(mob, pos);
            float x = (float) mob.posX;
            float y = (float) mob.posY;
            float z = (float) mob.posZ;
            int n = 0;
            for (int i = 0; i < TRIES; i++) {
                boolean ok;
                if (spawner != null) {
                    // TileEntityMobSpawner's own call (MobSpawnerBaseLogic.updateSpawner).
                    ok = ForgeEventFactory.canEntitySpawnSpawner(mob, world, x, y, z, spawner);
                } else {
                    // WorldEntitySpawner.findChunksForSpawning's.
                    Event.Result r = ForgeEventFactory.canEntitySpawn(mob, world, x, y, z, false);
                    ok = r == Event.Result.ALLOW || (r == Event.Result.DEFAULT && mob.getCanSpawnHere() && mob.isNotColliding());
                }
                if (ok) {
                    n++;
                }
            }
            return n;
        } finally {
            if (light > 0) {
                world.setBlockState(lamp, Blocks.AIR.getDefaultState(), 2);
            }
        }
    }

    private static void place(EntityMob mob, BlockPos pos) {
        mob.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
    }

    // ---- weather: World's rain and thunder strengths, which only the client may set through methods ----

    private static final String[] RAIN = {"rainingStrength", "field_73004_o"};
    private static final String[] PREV_RAIN = {"prevRainingStrength", "field_73003_n"};
    private static final String[] THUNDER = {"thunderingStrength", "field_73017_q"};
    private static final String[] PREV_THUNDER = {"prevThunderingStrength", "field_73018_p"};

    private static float[] weather(World world) {
        return new float[] {get(world, RAIN), get(world, THUNDER)};
    }

    private static void setWeather(World world, float rain, float thunder) {
        set(world, RAIN, rain);
        set(world, PREV_RAIN, rain);
        set(world, THUNDER, thunder);
        set(world, PREV_THUNDER, thunder);
    }

    private static float get(World world, String[] names) {
        Float v = ObfuscationReflectionHelper.getPrivateValue(World.class, world, names);
        return v;
    }

    private static void set(World world, String[] names, float value) {
        ObfuscationReflectionHelper.setPrivateValue(World.class, world, value, names);
    }
}
