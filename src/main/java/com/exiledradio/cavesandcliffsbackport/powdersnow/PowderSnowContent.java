package com.exiledradio.cavesandcliffsbackport.powdersnow;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;
import com.exiledradio.cavesandcliffsbackport.common.Owners;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Stream Ps: powder snow, its bucket and cauldron. Freezing is {@link Freezing}, everything event-driven
 * {@link PowderSnowEvents}, the patches {@link PowderSnowGenerator}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class PowderSnowContent {

    public static BlockPowderSnow powderSnow;
    public static BlockPowderSnowCauldron cauldron;
    public static ItemPowderSnowBucket bucket;
    public static SoundEvent bucketFill, bucketEmpty, hurtFreeze, convertedToStray;

    /** Every block name this stream registers, for Core's ownership table. */
    public static final String[] BLOCK_NAMES = {"powder_snow", "powder_snow_cauldron"};

    private PowderSnowContent() {
    }

    public static boolean enabled() {
        return ModConfig.powderSnow.enabled;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        bucketFill = ModSounds.event("item.bucket.fill_powder_snow");
        bucketEmpty = ModSounds.event("item.bucket.empty_powder_snow");
        hurtFreeze = ModSounds.event("entity.player.hurt_freeze");
        convertedToStray = ModSounds.event("entity.skeleton.converted_to_stray");
        powderSnow = Content.block(event.getRegistry(), "powder_snow", new BlockPowderSnow(ModSounds.type("block.powder_snow")), null);
        cauldron = Content.block(event.getRegistry(), "powder_snow_cauldron", new BlockPowderSnowCauldron(), "pickaxe");
        CavesAndCliffsBackport.LOGGER.info("Registered powder snow.");
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) {
            return;
        }
        // Neither block has an item of its own: powder snow is carried in the bucket, the cauldron is vanilla's.
        bucket = Content.item(event.getRegistry(), "powder_snow_bucket", new ItemPowderSnowBucket(powderSnow));
    }

    /** From the mod's pre-init: tells Core which blocks are ours (or, switched off, that nobody provides them). */
    public static void preInit() {
        Owners.declare(enabled(), BLOCK_NAMES);
    }

    /** From the mod's init: events, generation, dispensers. */
    public static void init() {
        if (!enabled()) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new PowderSnowEvents());
        GameRegistry.registerWorldGenerator(new PowderSnowGenerator(), 10);
        dispensers();
    }

    /**
     * A dispenser places powder snow from its bucket into air, and takes powder snow with an empty bucket.
     * The empty bucket keeps whatever behavior it had for everything else.
     */
    private static void dispensers() {
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(bucket, new BehaviorDefaultDispenseItem() {
            private final BehaviorDefaultDispenseItem plain = new BehaviorDefaultDispenseItem();

            @Override
            public ItemStack dispenseStack(IBlockSource source, ItemStack stack) {
                World world = source.getWorld();
                BlockPos pos = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
                if (world.isAirBlock(pos) && bucket.place(world, pos, null)) {
                    return new ItemStack(Items.BUCKET);
                }
                return this.plain.dispense(source, stack);
            }
        });
        final IBehaviorDispenseItem before = BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(Items.BUCKET);
        BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(Items.BUCKET, new IBehaviorDispenseItem() {
            private final BehaviorDefaultDispenseItem plain = new BehaviorDefaultDispenseItem();

            @Override
            public ItemStack dispense(IBlockSource source, ItemStack stack) {
                World world = source.getWorld();
                BlockPos pos = source.getBlockPos().offset(source.getBlockState().getValue(BlockDispenser.FACING));
                IBlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof BlockPowderSnow)) {
                    return before.dispense(source, stack);
                }
                BlockPowderSnow.pickUp(world, pos, state);
                world.playSound(null, pos, bucketFill, SoundCategory.BLOCKS, 1.0F, 1.0F);
                world.playEvent(1000, source.getBlockPos(), 0);
                ItemStack filled = new ItemStack(bucket);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    return filled;
                }
                if (((TileEntityDispenser) source.getBlockTileEntity()).addItemStack(filled) < 0) {
                    this.plain.dispense(source, filled);
                }
                return stack;
            }
        });
    }
}
