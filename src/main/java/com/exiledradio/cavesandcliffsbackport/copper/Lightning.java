package com.exiledradio.cavesandcliffsbackport.copper;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lightning and copper, at the moment a bolt joins the world (1.12 posts {@code EntityJoinWorldEvent}
 * from {@code addWeatherEffect}; no patch needed).
 *
 * <ul>
 *   <li><b>Rods</b>: natural lightning (from a thunderstorm, not commands or tridents, as in Java) goes to
 *       the nearest lightning rod within 128 blocks that is the top block of its column and has rain on it.
 *       The bolt is cancelled and a new one made above the rod. The fire the cancelled bolt already lit
 *       (1.12 lights it in the bolt's constructor) is put out.</li>
 *   <li><b>Cleaning</b>: any real bolt striking unwaxed copper (a lightning rod is copper since 1.21.9) turns
 *       that block back to unaffected; on unwaxed or waxed copper it then walks 3-5 random paths of 1-8
 *       steps through touching copper, scraping one stage off each block it reaches.</li>
 *   <li><b>Surge Protector</b>: a rod struck with a villager within 30 blocks and no fire lit grants it to
 *       players within 30 blocks.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class Lightning {

    public static final int ROD_RANGE = 128;

    /** Set while we add our own redirected bolt, so it is not redirected again. */
    private static boolean redirecting;
    /** For tests: treat the next bolts as natural ones. */
    static boolean forceNatural;

    private Lightning() {
    }

    private static boolean effectOnly(EntityLightningBolt bolt) {
        try {
            Boolean b = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, bolt, "field_184529_d");
            return b != null && b;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return whether the bolt being added comes from a thunderstorm's random strike */
    private static boolean natural() {
        if (forceNatural) {
            return true;
        }
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String m = e.getMethodName();
            if ((m.equals("updateBlocks") || m.equals("func_147456_g")) && e.getClassName().endsWith("WorldServer")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void join(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        World world = event.getWorld();
        if (world.isRemote || !(e instanceof EntityLightningBolt) || !CopperContent.enabled()) {
            return;
        }
        EntityLightningBolt bolt = (EntityLightningBolt) e;
        if (effectOnly(bolt)) {
            return;
        }
        BlockPos at = new BlockPos(bolt.posX, bolt.posY - 1.0E-6, bolt.posZ);
        if (ModConfig.copper.lightningRods && !redirecting && natural()) {
            BlockPos rod = findRod(world, at);
            if (rod != null && !rod.equals(at)) {
                event.setCanceled(true);
                putOutFire(world, new BlockPos(bolt));
                redirecting = true;
                try {
                    world.addWeatherEffect(new EntityLightningBolt(world, rod.getX() + 0.5, rod.getY() + 1, rod.getZ() + 0.5, false));
                } finally {
                    redirecting = false;
                }
                return;
            }
        }
        strike(world, at, bolt);
    }

    /**
     * 1.17's {@code LightningRodBlock.onProjectileHit}: in a thunderstorm, a Channeling trident (Update Aquatic
     * Backport's) that hits a lightning rod under open sky calls lightning down onto the rod, with the trident's thunder.
     */
    @SubscribeEvent
    public static void tridentHit(net.minecraftforge.event.entity.ProjectileImpactEvent.Arrow event) {
        net.minecraft.entity.projectile.EntityArrow arrow = event.getArrow();
        net.minecraft.util.math.RayTraceResult hit = event.getRayTraceResult();
        World world = arrow.world;
        if (world.isRemote || hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK
                || !CopperContent.enabled() || !world.isThundering()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(world.getBlockState(pos).getBlock() instanceof BlockLightningRod) || !world.canSeeSky(pos) || !channeling(arrow)) {
            return;
        }
        EntityLightningBolt bolt = new EntityLightningBolt(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, false);
        Entity owner = arrow.shootingEntity;
        rememberCaster(bolt, owner);
        world.addWeatherEffect(bolt);
        net.minecraft.util.SoundEvent thunder = net.minecraftforge.fml.common.registry.ForgeRegistries.SOUND_EVENTS.getValue(
                new net.minecraft.util.ResourceLocation("updateaquaticbackport", "item.trident.thunder"));
        if (thunder != null) {
            world.playSound(null, pos, thunder, net.minecraft.util.SoundCategory.WEATHER, 5.0F, 1.0F);
        }
    }

    /** @return whether this is Update Aquatic Backport's trident with Channeling */
    static boolean channeling(Entity projectile) {
        net.minecraft.enchantment.Enchantment channeling = net.minecraftforge.fml.common.registry.ForgeRegistries.ENCHANTMENTS.getValue(
                new net.minecraft.util.ResourceLocation("updateaquaticbackport", "channeling"));
        if (channeling == null || !"updateaquaticbackport".equals(net.minecraft.entity.EntityList.getKey(projectile) == null ? null
                : net.minecraft.entity.EntityList.getKey(projectile).getNamespace())) {
            return false;
        }
        try {
            Object stack = projectile.getClass().getMethod("thrownStack").invoke(projectile);
            return stack instanceof net.minecraft.item.ItemStack
                    && net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(channeling, (net.minecraft.item.ItemStack) stack) > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Update Aquatic's record of who called a bolt (for Very Very Frightening), when it is there. */
    private static void rememberCaster(EntityLightningBolt bolt, @Nullable Entity owner) {
        if (!(owner instanceof net.minecraft.entity.player.EntityPlayerMP)) {
            return;
        }
        try {
            java.lang.reflect.Method remember = Class.forName("com.exiledradio.updateaquaticbackport.trident.Channeling")
                    .getDeclaredMethod("remember", EntityLightningBolt.class, net.minecraft.entity.player.EntityPlayerMP.class);
            remember.setAccessible(true);
            remember.invoke(null, bolt, owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not there: the bolt has no caster, as in 1.12.2.
        }
    }

    /** What a bolt does to the block it hits; {@code at} is that block. */
    static void strike(World world, BlockPos at, @Nullable Entity bolt) {
        IBlockState hit = world.getBlockState(at);
        if (hit.getBlock() instanceof BlockLightningRod) {
            ((BlockLightningRod) hit.getBlock()).onStruck(world, at, hit);
            surgeProtector(world, at.up());
        }
        // 1.21.9: the struck block itself, a rod included (rods are copper now); before, a rod cleaned what it stood on.
        cleanCopper(world, at, world.rand);
    }

    /**
     * Turns a struck unwaxed copper block back to unaffected and scrapes a stage off a random walk around it; struck
     * waxed copper stays as it is but still starts the walks (1.21.11 {@code LightningBolt.clearCopperOnLightningStrike}).
     */
    public static void cleanCopper(World world, BlockPos pos, Random rand) {
        IBlockState state = world.getBlockState(pos);
        boolean unwaxed = Weathering.stage(state.getBlock()) >= 0;
        if (!unwaxed && Weathering.unwaxed(state.getBlock()) == null) {
            return;
        }
        if (unwaxed) {
            Block first = Weathering.first(state.getBlock());
            if (first != state.getBlock()) {
                world.setBlockState(pos, Weathering.copy(state, first), 3);
            }
        }
        int walks = rand.nextInt(3) + 3;
        for (int w = 0; w < walks; w++) {
            int steps = rand.nextInt(8) + 1;
            BlockPos cur = pos;
            for (int s = 0; s < steps && cur != null; s++) {
                cur = step(world, cur, rand);
            }
        }
    }

    /** One step: up to 10 tries at a random touching block; the first copper one loses a stage. */
    @Nullable
    private static BlockPos step(World world, BlockPos from, Random rand) {
        for (int i = 0; i < 10; i++) {
            BlockPos p = from.add(rand.nextInt(3) - 1, rand.nextInt(3) - 1, rand.nextInt(3) - 1);
            IBlockState s = world.getBlockState(p);
            if (Weathering.stage(s.getBlock()) >= 0) {
                net.minecraft.block.Block prev = Weathering.previous(s.getBlock());
                if (prev != null) {
                    world.setBlockState(p, Weathering.copy(s, prev), 3);
                }
                return p;
            }
        }
        return null;
    }

    /** @return the nearest rod within range that tops its column and has rain falling on it, or null */
    @Nullable
    public static BlockPos findRod(World world, BlockPos strike) {
        BlockPos best = null;
        double bestDist = (double) ROD_RANGE * ROD_RANGE;
        List<TileEntity> tiles = new ArrayList<TileEntity>(world.loadedTileEntityList);
        for (TileEntity te : tiles) {
            if (!(te instanceof BlockLightningRod.TileEntityLightningRod) || te.isInvalid()) {
                continue;
            }
            BlockPos p = te.getPos();
            double d = p.distanceSq(strike);
            if (d > bestDist || !(world.getBlockState(p).getBlock() instanceof BlockLightningRod)) {
                continue;
            }
            if (world.getPrecipitationHeight(p).getY() != p.getY() + 1 || !world.isRainingAt(p.up())) {
                continue;
            }
            best = p;
            bestDist = d;
        }
        return best;
    }

    private static void putOutFire(World world, BlockPos center) {
        for (BlockPos p : BlockPos.getAllInBox(center.add(-1, -1, -1), center.add(1, 1, 1))) {
            if (world.getBlockState(p).getBlock() == Blocks.FIRE) {
                world.setBlockToAir(p);
            }
        }
    }

    private static void surgeProtector(World world, BlockPos boltPos) {
        AxisAlignedBB near = new AxisAlignedBB(boltPos).grow(30);
        if (world.getEntitiesWithinAABB(EntityVillager.class, near).isEmpty()) {
            return;
        }
        for (BlockPos p : BlockPos.getAllInBox(boltPos.add(-1, -1, -1), boltPos.add(1, 1, 1))) {
            if (world.getBlockState(p).getBlock() == Blocks.FIRE) {
                return;
            }
        }
        for (EntityPlayer player : world.getEntitiesWithinAABB(EntityPlayer.class, near)) {
            Advancements.grant(player, "adventure/lightning_rod_with_villager_no_fire");
        }
    }
}
