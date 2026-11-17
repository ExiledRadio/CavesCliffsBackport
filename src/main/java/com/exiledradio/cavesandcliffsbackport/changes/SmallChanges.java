package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITempt;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityDonkey;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntityMule;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The event-driven changes of the "Other Changes" page:
 * <ul>
 *   <li><b>Sleeping and the weather</b> (1.18, 21w44a, MC-63340): {@code ServerLevel.tick} resets the weather cycle
 *       after a night slept through only {@code if (... RULE_WEATHER_CYCLE && this.isRaining())}. 1.12.2's {@code
 *       WorldServer.wakeAllPlayers} always does; on a clear night the weather timers it reset are put back at the end
 *       of the same tick.</li>
 *   <li><b>Horses and llamas tempted</b> (1.18): {@code AbstractHorse.addBehaviourGoals}' {@code TemptGoal(1.25,
 *       golden carrot, golden apple, enchanted golden apple)} for horses, donkeys and mules (priority 3), and {@code
 *       Llama}'s {@code TemptGoal(1.25, hay block)} (priority 4); skeleton and zombie horses have none.</li>
 *   <li><b>Enchanting table light</b> (1.18, 21w37a): light level 7.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class SmallChanges {

    /** 1.18.2's AbstractHorse temptation items. */
    public static final Set<Item> HORSE_FOOD = ImmutableSet.of(Items.GOLDEN_CARROT, Items.GOLDEN_APPLE);
    public static final Set<Item> LLAMA_FOOD = ImmutableSet.of(Item.getItemFromBlock(Blocks.HAY_BLOCK));

    private static final Field LIGHT = ObfuscationReflectionHelper.findField(Block.class, "field_149784_t");
    /** 1.12.2's enchanting table light (none). */
    private static int vanillaTableLight = -1;

    /** The weather of worlds whose players woke up this tick, to put back at its end. */
    private static final Map<World, int[]> WEATHER = new IdentityHashMap<World, int[]>();

    private SmallChanges() {
    }

    public static void init() {
        StrongholdBurial.init();
        applyTableLight();
    }

    // ------------------------------------------------------------------------------------------
    // Enchanting table light.

    public static void applyTableLight() {
        try {
            if (vanillaTableLight < 0) {
                vanillaTableLight = LIGHT.getInt(Blocks.ENCHANTING_TABLE);
            }
            LIGHT.setInt(Blocks.ENCHANTING_TABLE, ModConfig.changes.enchantingTableLight ? 7 : vanillaTableLight);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (CavesAndCliffsBackport.MOD_ID.equals(event.getModID())) {
            applyTableLight();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Sleeping and the weather.

    /**
     * wakeAllPlayers wakes each player with {@code wakeUpPlayer(false, false, true)}, before it resets the weather; a
     * player leaving the bed or pushed out wakes otherwise.
     */
    @SubscribeEvent
    public static void wakeUp(PlayerWakeUpEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        World world = player.world;
        if (!ModConfig.changes.sleepWeather || world.isRemote || event.wakeImmediately() || event.updateWorld()
                || !event.shouldSetSpawn() || world.isRaining() || WEATHER.containsKey(world)) {
            return;
        }
        WorldInfo info = world.getWorldInfo();
        WEATHER.put(world, new int[] {info.getRainTime(), info.isRaining() ? 1 : 0, info.getThunderTime(), info.isThundering() ? 1 : 0});
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WEATHER.isEmpty()) {
            return;
        }
        int[] w = WEATHER.remove(event.world);
        if (w != null) {
            restore(event.world, w);
        }
    }

    /** Puts back the weather saved when the players woke, unless sleeping changed nothing. */
    static void restore(World world, int[] w) {
        WorldInfo info = world.getWorldInfo();
        if (info.getRainTime() == 0 && !info.isRaining() && info.getThunderTime() == 0 && !info.isThundering()) {
            info.setRainTime(w[0]);
            info.setRaining(w[1] == 1);
            info.setThunderTime(w[2]);
            info.setThundering(w[3] == 1);
        }
    }

    /** For the self-test: the weather as a night slept through leaves it in a world, 1.12.2's reset and all. */
    public static void sleptThrough(WorldServer world) {
        int[] saved = null;
        if (ModConfig.changes.sleepWeather && !world.isRaining()) {
            WorldInfo info = world.getWorldInfo();
            saved = new int[] {info.getRainTime(), info.isRaining() ? 1 : 0, info.getThunderTime(), info.isThundering() ? 1 : 0};
        }
        if (world.getGameRules().getBoolean("doWeatherCycle")) {
            world.provider.resetRainAndThunder();
        }
        if (saved != null) {
            restore(world, saved);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Horses and llamas.

    @SubscribeEvent
    public static void join(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !ModConfig.changes.horseTempting) {
            return;
        }
        Class<?> type = event.getEntity().getClass();
        if (type == EntityHorse.class || type == EntityDonkey.class || type == EntityMule.class) {
            addTempt((AbstractHorse) event.getEntity(), 3, HORSE_FOOD);
        } else if (type == EntityLlama.class) {
            addTempt((AbstractHorse) event.getEntity(), 4, LLAMA_FOOD);
        }
    }

    private static void addTempt(AbstractHorse horse, int priority, Set<Item> food) {
        for (EntityAITasks.EntityAITaskEntry e : horse.tasks.taskEntries) {
            if (e.action instanceof EntityAITempt) {
                return;
            }
        }
        horse.tasks.addTask(priority, new EntityAITempt(horse, 1.25D, false, food));
    }
}
