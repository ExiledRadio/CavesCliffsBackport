package com.exiledradio.cavesandcliffsbackport.spawning;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.18's light rule for monster spawning, {@code Monster.isDarkEnoughToSpawn} of 1.18.2:
 * <ol>
 *   <li>no spawn if the sky light is above {@code rand(32)} (as 1.12.2);</li>
 *   <li>no spawn if the block light is above 0 (new);</li>
 *   <li>no spawn if the raw brightness, with the sky darkened by 10 in a thunderstorm, is above {@code rand(8)}
 *       (as 1.12.2, without its look at the neighbors of slabs and stairs).</li>
 * </ol>
 * 1.18.2 has no per-dimension values yet (those came in 1.19), so the rule is the same in every dimension. It applies
 * to natural spawns and to spawners alike, as in 1.18.2's {@code BaseSpawner}, which checks the mob's spawn rules.
 *
 * <p>It replaces 1.12.2's {@code EntityMob.isValidLightLevel} for every mob that keeps it: an {@link EntityMob}
 * whose class and superclasses override neither {@code isValidLightLevel} nor {@code getCanSpawnHere} (husks and
 * strays, which only add a sky check, are taken too). Blazes, silverfish, endermites, guardians, zombie pigmen and
 * modded mobs with spawn rules of their own keep theirs. With Wild Update Backport's Nether light setting on, the
 * Nether is Wild Update's (1.19.3's values) and this leaves it alone.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class SpawnLight {

    private SpawnLight() {
    }

    /** Per mob class: whether it uses 1.12.2's light test, so this rule replaces it. */
    private static final ConcurrentHashMap<Class<?>, Boolean> USES_VANILLA_LIGHT = new ConcurrentHashMap<Class<?>, Boolean>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.changes.monsterSpawnLight || event.getResult() != Event.Result.DEFAULT
                || !(event.getEntityLiving() instanceof EntityMob)) {
            return;
        }
        EntityMob mob = (EntityMob) event.getEntityLiving();
        if (!applies(mob) || mob.world.isRemote) {
            return;
        }
        if (mob.world.provider.getDimensionType() == DimensionType.NETHER && wildUpdateOwnsNether()) {
            return;
        }
        event.setResult(canSpawnHere(mob) ? Event.Result.ALLOW : Event.Result.DENY);
    }

    /** @return whether this mob uses 1.12.2's {@code EntityMob.isValidLightLevel}, so 1.18's rule replaces it */
    public static boolean applies(EntityMob mob) {
        Class<?> c = mob.getClass();
        Boolean known = USES_VANILLA_LIGHT.get(c);
        if (known == null) {
            known = usesVanillaLight(c);
            USES_VANILLA_LIGHT.put(c, known);
        }
        return known;
    }

    private static boolean usesVanillaLight(Class<?> c) {
        for (Class<?> k = c; k != EntityMob.class && k != null; k = k.getSuperclass()) {
            if (declares(k, "isValidLightLevel", "func_70814_o")) {
                return false;
            }
            if (declares(k, "getCanSpawnHere", "func_70601_bi") && k != EntityHusk.class && k != EntityStray.class) {
                return false;
            }
        }
        return true;
    }

    private static boolean declares(Class<?> k, String mcp, String srg) {
        try {
            for (Method m : k.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType() == boolean.class
                        && (mcp.equals(m.getName()) || srg.equals(m.getName()))) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // A class whose methods cannot be listed (a missing optional dependency): leave it as 1.12.2 has it.
            return true;
        }
        return false;
    }

    /**
     * 1.12.2's {@code EntityMob.getCanSpawnHere} and the {@code isNotColliding} test after it (an allowed spawn skips
     * vanilla's own), with 1.18.2's light test in place of 1.12.2's.
     */
    public static boolean canSpawnHere(EntityMob mob) {
        World world = mob.world;
        BlockPos pos = new BlockPos(mob.posX, mob.getEntityBoundingBox().minY, mob.posZ);
        if (world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return false;
        }
        if (!isDarkEnoughToSpawn(world, pos, mob.getRNG(), world.isThundering())) {
            return false;
        }
        IBlockState below = world.getBlockState(new BlockPos(mob).down());
        if (!below.canEntitySpawn(mob)) {
            return false;
        }
        if (((EntityCreature) mob).getBlockPathWeight(pos) < 0.0F) {
            return false;
        }
        if ((mob instanceof EntityHusk || mob instanceof EntityStray) && !world.canSeeSky(new BlockPos(mob))) {
            return false;
        }
        return mob.isNotColliding();
    }

    /** 1.18.2's {@code Monster.isDarkEnoughToSpawn}. */
    public static boolean isDarkEnoughToSpawn(World world, BlockPos pos, Random random, boolean thundering) {
        if (world.getLightFor(EnumSkyBlock.SKY, pos) > random.nextInt(32)) {
            return false;
        }
        if (world.getLightFor(EnumSkyBlock.BLOCK, pos) > 0) {
            return false;
        }
        return rawBrightness(world, pos, thundering) <= random.nextInt(8);
    }

    /**
     * 1.18.2's {@code getMaxLocalRawBrightness}: the greater of the darkened sky light and the block light, with the
     * sky darkened by 10 in a thunderstorm. Like 1.12.2's thunder test, it swaps the world's darkening for the call.
     */
    static int rawBrightness(World world, BlockPos pos, boolean thundering) {
        if (!thundering) {
            return world.getLight(pos, false);
        }
        int darken = world.getSkylightSubtracted();
        world.setSkylightSubtracted(10);
        try {
            return world.getLight(pos, false);
        } finally {
            world.setSkylightSubtracted(darken);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Wild Update Backport's Nether rule.

    private static Field wildChanges;
    private static Field wildEnabled;
    private static Field wildNetherLight;
    private static boolean wildLooked;

    /** @return whether Wild Update Backport is installed with its Nether spawn light change on */
    public static boolean wildUpdateOwnsNether() {
        if (!Loader.isModLoaded("wildupdatebackport")) {
            return false;
        }
        try {
            if (!wildLooked) {
                wildLooked = true;
                Class<?> config = Class.forName("com.exiledradio.wildupdatebackport.ModConfig");
                wildChanges = config.getField("changes");
                Class<?> changes = wildChanges.getType();
                wildEnabled = changes.getField("enabled");
                wildNetherLight = changes.getField("netherSpawnLight");
            }
            if (wildChanges == null) {
                return true;
            }
            Object changes = wildChanges.get(null);
            return wildEnabled.getBoolean(changes) && wildNetherLight.getBoolean(changes);
        } catch (Exception e) {
            // Its settings could not be read: the Nether stays Wild Update's, so the two never both apply.
            wildChanges = null;
            CavesAndCliffsBackport.LOGGER.warn("Wild Update Backport's Nether spawn light setting is not readable; its Nether keeps its own rule.", e);
            return true;
        }
    }
}
