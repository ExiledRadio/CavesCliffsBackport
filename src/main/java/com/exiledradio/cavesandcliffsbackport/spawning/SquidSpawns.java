package com.exiledradio.cavesandcliffsbackport.spawning;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.block.material.Material;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Where squid spawn, 1.18's {@code WaterAnimal.checkSurfaceWaterAnimalSpawnRules}: from 13 below sea level up to sea
 * level (y 50 to 63), with water below and a water block above. 1.12.2's squid take y 46 to 62 and look at nothing
 * else. Glow squid have their own rule (dark water below y 30, {@code glowsquid}); Update Aquatic Backport's fish and
 * dolphins are that mod's. Natural spawning and spawners both ask, as in 1.18.2.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class SquidSpawns {

    private SquidSpawns() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getResult() != Event.Result.DEFAULT || event.getWorld().isRemote || event.getEntityLiving().getClass() != EntitySquid.class) {
            return;
        }
        BlockPos pos = new BlockPos(MathHelper.floor(event.getX()), MathHelper.floor(event.getY()), MathHelper.floor(event.getZ()));
        // ALLOW skips 1.12.2's own tests, so its collision test is asked here.
        boolean ok = canSpawnAt(event.getWorld(), pos) && ((EntitySquid) event.getEntityLiving()).isNotColliding();
        event.setResult(ok ? Event.Result.ALLOW : Event.Result.DENY);
    }

    /** 1.18.2's rule for a squid at this block. */
    public static boolean canSpawnAt(World world, BlockPos pos) {
        int sea = world.getSeaLevel();
        return pos.getY() >= sea - 13 && pos.getY() <= sea && world.getBlockState(pos.down()).getMaterial() == Material.WATER
                && (world.getBlockState(pos.up()).getBlock() == Blocks.WATER || world.getBlockState(pos.up()).getBlock() == Blocks.FLOWING_WATER);
    }
}
