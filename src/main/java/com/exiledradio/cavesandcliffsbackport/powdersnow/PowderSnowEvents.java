package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Iterator;
import java.util.Random;

/** Everything powder snow does through events. Registered from the mod's init only when the stream is on. */
public final class PowderSnowEvents {

    public static final String ADVANCEMENT = "adventure/walk_on_powder_snow_with_leather_boots";

    /** Our own, so the cauldron rolls do not shift the world's random sequence. */
    private final Random random = new Random();

    PowderSnowEvents() {
    }

    // ---- freezing ----------------------------------------------------------------------------

    @SubscribeEvent
    public void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        Freezing.tick(event.getEntityLiving());
    }

    @SubscribeEvent
    public void joinWorld(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote && event.getEntity() instanceof EntityLivingBase) {
            Freezing.joined(event.getEntity());
        }
    }

    @SubscribeEvent
    public void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP && event.getTarget() instanceof EntityLivingBase) {
            Freezing.startTracking((EntityPlayerMP) event.getEntityPlayer(), event.getTarget());
        }
    }

    /** A fully frozen player's hurt sound is Java's freezing one. */
    @SubscribeEvent
    public void hurtSound(PlaySoundAtEntityEvent event) {
        if (event.getSound() == SoundEvents.ENTITY_PLAYER_HURT && event.getEntity() instanceof EntityPlayer
                && Freezing.fullyFrozen(event.getEntity())) {
            event.setSound(PowderSnowContent.hurtFreeze);
        }
    }

    /** Java's freezeDamage game rule, for worlds that do not have it yet. */
    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote && !world.getGameRules().hasRule(Freezing.GAME_RULE)) {
            world.getGameRules().addGameRule(Freezing.GAME_RULE, "true", GameRules.ValueType.BOOLEAN_VALUE);
        }
    }

    /** Mobs do not spawn inside powder snow, except those it cannot hurt. */
    @SubscribeEvent
    public void checkSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.isSpawner() || Freezing.spawnsInPowderSnow(event.getEntity())) {
            return;
        }
        BlockPos pos = new BlockPos(event.getX(), event.getY(), event.getZ());
        if (event.getWorld().getBlockState(pos).getBlock() instanceof BlockPowderSnow) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ---- the advancement ---------------------------------------------------------------------

    /** Light as a Rabbit: standing on powder snow in boots that hold. Looked at four times a second. */
    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        EntityPlayer player = event.player;
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER || player.ticksExisted % 5 != 0) {
            return;
        }
        if (onPowderSnowInBoots(player)) {
            Advancements.grant(player, ADVANCEMENT);
        }
    }

    public static boolean onPowderSnowInBoots(EntityPlayer player) {
        if (!player.onGround) {
            return false;
        }
        BlockPos below = new BlockPos(player.posX, player.posY - 0.2D, player.posZ);
        return player.world.getBlockState(below).getBlock() instanceof BlockPowderSnow && Freezing.canWalkOn(player)
                && !Freezing.walkableMob(player);
    }

    // ---- buckets -----------------------------------------------------------------------------

    /** An empty bucket takes powder snow, through the same event that lets a bucket take a modded liquid. */
    @SubscribeEvent
    public void fillBucket(FillBucketEvent event) {
        RayTraceResult target = event.getTarget();
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK || event.getResult() != Event.Result.DEFAULT
                || event.getEmptyBucket().getItem() != Items.BUCKET) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = target.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockPowderSnow)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (!world.isBlockModifiable(player, pos) || !player.canPlayerEdit(pos.offset(target.sideHit), target.sideHit, event.getEmptyBucket())) {
            return;
        }
        BlockPowderSnow.pickUp(world, pos, state);
        player.playSound(PowderSnowContent.bucketFill, 1.0F, 1.0F);
        event.setFilledBucket(new ItemStack(PowderSnowContent.bucket));
        event.setResult(Event.Result.ALLOW);
    }

    /** A powder snow bucket fills a vanilla cauldron (whatever is in it) and a lava cauldron. Ours handles itself. */
    @SubscribeEvent
    public void bucketOnCauldron(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() != PowderSnowContent.bucket) {
            return;
        }
        World world = event.getWorld();
        BlockPos pos = event.getPos();
        Block block = world.getBlockState(pos).getBlock();
        if (block != Blocks.CAULDRON && (block != Owners.ourBlock("lava_cauldron") || block == Blocks.AIR)) {
            return;
        }
        if (!world.isRemote) {
            BlockPowderSnowCauldron.empty(event.getEntityPlayer(), event.getHand());
            world.setBlockState(pos, PowderSnowContent.cauldron.getDefaultState().withProperty(BlockPowderSnowCauldron.LEVEL, 3));
            world.playSound(null, pos, PowderSnowContent.bucketEmpty, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    // ---- snowfall into cauldrons -------------------------------------------------------------

    /**
     * 1.12 calls a block's rain hook only where it rains, never where it snows, so this is our own pass over
     * the loaded chunks while the weather is on: vanilla's one column in sixteen chunks a tick, and Java's
     * one in ten for a cauldron under snow.
     */
    @SubscribeEvent
    public void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER || !ModConfig.powderSnow.cauldronSnow
                || !(event.world instanceof WorldServer) || !event.world.isRaining()) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        for (Iterator<Chunk> it = world.getPersistentChunkIterable(world.getPlayerChunkMap().getChunkIterator()); it.hasNext(); ) {
            Chunk chunk = it.next();
            if (this.random.nextInt(16) != 0 || !world.provider.canDoRainSnowIce(chunk)) {
                continue;
            }
            BlockPos top = world.getPrecipitationHeight(new BlockPos((chunk.x << 4) + this.random.nextInt(16), 0,
                    (chunk.z << 4) + this.random.nextInt(16)));
            snowfall(world, top.down(), this.random);
        }
    }

    /** One snowfall roll on the block under the sky at a column. */
    static boolean snowfall(World world, BlockPos pos, Random random) {
        Block block = world.getBlockState(pos).getBlock();
        if (block != Blocks.CAULDRON && block != PowderSnowContent.cauldron) {
            return false;
        }
        return snowsAt(world, pos) && random.nextFloat() < 0.1F && BlockPowderSnowCauldron.snowInto(world, pos);
    }

    /** @return whether the weather at a position is snow: a biome with weather, cold enough at that height */
    public static boolean snowsAt(World world, BlockPos pos) {
        Biome biome = world.getBiome(pos);
        return (biome.canRain() || biome.getEnableSnow()) && biome.getTemperature(pos) < 0.15F;
    }
}
