package com.exiledradio.cavesandcliffsbackport.spawning;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSand;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Where rabbits and wolves spawn, 1.18.2's {@code Rabbit.checkRabbitSpawnRules} and {@code Wolf.checkWolfSpawnRules}:
 * on a block of the {@code rabbits_spawnable_on} tag (grass block, snow, snow block, sand: not red sand) or the {@code
 * wolves_spawnable_on} tag (grass block, snow, snow block), with a light level above 8. 1.12.2 takes grass blocks
 * only, as every animal does ({@code EntityAnimal.getCanSpawnHere}).
 *
 * <p>On a grass block nothing changes: 1.12.2's own test runs. On the other blocks the spawn is allowed when 1.12.2's
 * test would allow it on grass (light above 8, the block below lets the mob spawn, nothing in the way); otherwise the
 * event is left alone, so chunk generation's animals, which skip the test, are not turned away. Natural spawning and
 * spawners both ask, as in 1.18.2.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class SnowSpawns {

    private SnowSpawns() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.changes.snowSpawns || event.getResult() != Event.Result.DEFAULT || event.getWorld().isRemote) {
            return;
        }
        EntityLiving mob = event.getEntityLiving() instanceof EntityLiving ? (EntityLiving) event.getEntityLiving() : null;
        if (mob == null || mob.getClass() != EntityRabbit.class && mob.getClass() != EntityWolf.class) {
            return;
        }
        if (canSpawnHere(mob)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    /** @return whether 1.18.2's spawn block tag has the block the mob would spawn on, other than the grass block */
    public static boolean extraBlock(EntityLiving mob, IBlockState below) {
        Block b = below.getBlock();
        if (b == Blocks.SNOW || b == Blocks.SNOW_LAYER) {
            return true;
        }
        // The tag's sand is plain sand; red sand is a block of its own since 1.13.
        return b == Blocks.SAND && mob instanceof EntityRabbit && below.getValue(BlockSand.VARIANT) == BlockSand.EnumType.SAND;
    }

    /** 1.18.2's rule for a place on one of the tags' blocks other than grass, with 1.12.2's other tests. */
    public static boolean canSpawnHere(EntityLiving mob) {
        BlockPos pos = new BlockPos(MathHelper.floor(mob.posX), MathHelper.floor(mob.getEntityBoundingBox().minY),
                MathHelper.floor(mob.posZ));
        IBlockState below = mob.world.getBlockState(pos.down());
        return extraBlock(mob, below) && mob.world.getLight(pos) > 8 && below.canEntitySpawn(mob)
                && mob.isNotColliding();
    }
}
