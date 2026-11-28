package com.exiledradio.cavesandcliffsbackport.changes;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.17's {@code playersSleepingPercentage} game rule (default 100), as 1.18.2's {@code SleepStatus} and
 * {@code ServerLevel.tick}: the night is skipped once at least {@code max(1, ceil(players * percentage / 100))} of the
 * world's players who are not spectators have slept long enough (100 ticks); above 100 it is never skipped. Skipping
 * sets the time to the next morning (with {@code doDaylightCycle}), wakes the sleepers, and clears rain and thunder
 * if it is raining.
 *
 * <p>At 100 the skip stays 1.12.2's own (the same rule: everyone asleep). At any other value 1.12.2's all-asleep flag
 * is cleared before each world tick so only this rule skips the night.
 *
 * <p>The action bar line 1.17 shows in multiplayer when the number of sleepers changes ("1/2 players sleeping",
 * "Sleeping through this night") is shown on a dedicated server, or in an open world with more than one player.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class SleepPercentage {

    public static final String GAME_RULE = "playersSleepingPercentage";

    /** Per world: the last sleeping and active counts announced, so a line is sent only when they change. */
    private static final Map<World, int[]> LAST = new WeakHashMap<World, int[]>();

    private static Field allPlayersSleeping;

    private SleepPercentage() {
    }

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote && !world.getGameRules().hasRule(GAME_RULE)) {
            world.getGameRules().addGameRule(GAME_RULE, "100", GameRules.ValueType.NUMERICAL_VALUE);
        }
    }

    /** @return the rule's value in this world (100 where it is missing) */
    public static int percentage(World world) {
        return world.getGameRules().hasRule(GAME_RULE) ? world.getGameRules().getInt(GAME_RULE) : 100;
    }

    /** @return how many sleepers the night needs: {@code max(1, ceil(active * percentage / 100))} */
    public static int sleepersNeeded(int active, int percentage) {
        return Math.max(1, MathHelper.ceil(active * percentage / 100.0F));
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        int percentage = percentage(world);
        int active = 0, sleeping = 0, deep = 0;
        for (EntityPlayer player : world.playerEntities) {
            if (player.isSpectator()) {
                continue;
            }
            active++;
            if (player.isPlayerSleeping()) {
                sleeping++;
                if (player.isPlayerFullyAsleep()) {
                    deep++;
                }
            }
        }
        announce(world, percentage, active, sleeping);
        if (percentage == 100) {
            return;
        }
        clearVanillaFlag(world);
        int needed = sleepersNeeded(active, percentage);
        if (percentage > 100 || sleeping < needed || deep < needed) {
            return;
        }
        skipNight(world);
    }

    /** What 1.18.2 does once enough have slept: morning, everyone up, the rain stopped. */
    static void skipNight(WorldServer world) {
        if (world.getGameRules().getBoolean("doDaylightCycle")) {
            long time = world.getWorldTime() + 24000L;
            world.setWorldTime(time - time % 24000L);
        }
        List<EntityPlayer> sleepers = new ArrayList<EntityPlayer>();
        for (EntityPlayer player : world.playerEntities) {
            if (player.isPlayerSleeping()) {
                sleepers.add(player);
            }
        }
        for (EntityPlayer player : sleepers) {
            player.wakeUpPlayer(false, false, true);
        }
        boolean weatherCycle = !world.getGameRules().hasRule("doWeatherCycle") || world.getGameRules().getBoolean("doWeatherCycle");
        if (weatherCycle && world.isRaining()) {
            world.provider.resetRainAndThunder();
        }
    }

    private static void announce(WorldServer world, int percentage, int active, int sleeping) {
        int[] last = LAST.get(world);
        if (last != null && last[0] == sleeping && last[1] == active) {
            return;
        }
        LAST.put(world, new int[] {sleeping, active});
        boolean multiplayer = world.getMinecraftServer() != null
                && (world.getMinecraftServer().isDedicatedServer() || world.playerEntities.size() > 1);
        if (sleeping == 0 || percentage > 100 || !multiplayer) {
            return;
        }
        int needed = sleepersNeeded(active, percentage);
        TextComponentTranslation line = sleeping >= needed ? new TextComponentTranslation("sleep.skipping_night")
                : new TextComponentTranslation("sleep.players_sleeping", sleeping, needed);
        for (EntityPlayer player : world.playerEntities) {
            player.sendStatusMessage(line, true);
        }
    }

    /** 1.12.2 skips the night by itself once everyone sleeps; at a percentage other than 100 that is this rule's job. */
    private static void clearVanillaFlag(WorldServer world) {
        try {
            if (allPlayersSleeping == null) {
                allPlayersSleeping = ObfuscationReflectionHelper.findField(WorldServer.class, "field_73068_P");
            }
            allPlayersSleeping.setBoolean(world, false);
        } catch (RuntimeException | IllegalAccessException e) {
            // Without it 1.12.2 still skips when everyone sleeps, as at 100.
        }
    }
}
