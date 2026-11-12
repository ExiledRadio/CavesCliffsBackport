package com.exiledradio.cavesandcliffsbackport.blocks;

import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The <i>Caves &amp; Cliffs</i> advancement: free fall from the top of the world to its bottom and survive.
 *
 * <h2>The rule (current Java)</h2>
 * A player's fall starts on the first tick their fall distance is above zero and ends when it is back at
 * zero, however that happens: landing, water, a ladder. If they are alive then, the fall counts when it
 * started no more than one block under the build limit, ended no more than five blocks above the world's
 * floor, and covered all of the height between but five blocks. In 1.18 those are y 319, y -59 and 379.
 *
 * <p>The limits are read from the world each time ({@link WorldHeights}), so the same code asks for y 255
 * down to y 5 in a world of 1.12.2's height and for 1.18's numbers in a world with extended height.
 */
public final class WorldFall {

    public static final String ADVANCEMENT = "adventure/fall_from_world_height";

    /** Where each falling player's fall began (y). Weak, so a player who leaves is forgotten. */
    private final Map<EntityPlayer, Double> starts = new WeakHashMap<EntityPlayer, Double>();

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP) {
            update((EntityPlayerMP) event.player);
        }
    }

    /** One tick of one player; public for the self-test. */
    public void update(EntityPlayerMP player) {
        Double start = this.starts.get(player);
        if (player.fallDistance > 0.0F) {
            if (start == null) {
                this.starts.put(player, player.posY);
            }
        } else if (start != null) {
            this.starts.remove(player);
            if (player.getHealth() > 0.0F && qualifies(player.world, start, player.posY)) {
                Advancements.grant(player, ADVANCEMENT);
            }
        }
    }

    /** @return whether a fall from one height to another spans this world from top to bottom */
    public static boolean qualifies(World world, double startY, double endY) {
        int min = WorldHeights.minY(world);
        int max = WorldHeights.maxYExclusive(world);
        return startY >= max - 1 && endY <= min + 5 && startY - endY >= (max - min) - 5;
    }
}
