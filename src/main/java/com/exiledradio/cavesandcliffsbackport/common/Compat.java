package com.exiledradio.cavesandcliffsbackport.common;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.item.Item;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft integrations with other mods of the family, none of them required.
 *
 * <p>Every call goes through reflection or registry names, so this mod never links against another
 * mod's classes and loads the same with or without it.
 *
 * <h2>Composting</h2>
 * The composter is a 1.14 block. When a mod that has one is installed, the items 1.14 lets you compost
 * from this update (azaleas, moss, dripleaf, glow berries and the rest) are added to it. Streams call
 * {@link #compost} while registering items; the calls are kept until {@link #init} hands them over.
 *
 * <h2>Stonecutting</h2>
 * Village &amp; Pillage's stonecutter works out its cuts from the crafting recipes of blocks of rock, so the
 * deepslate sets reach it on their own; copper is metal ({@code Material.IRON}), which it does not take. Streams
 * call {@link #stonecut} with 1.17's stonecutting recipes, and {@link #init} adds them to V&amp;P's list of extra
 * cuts in memory, never in its config file (the entries V&amp;P reads after the ones it derived; one already there is
 * not added twice).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class Compat {

    private static final List<Object[]> COMPOST = new ArrayList<Object[]>();
    private static final List<String> STONECUT = new ArrayList<String>();

    private Compat() {
    }

    /** Asks for an item to be compostable with this chance, if a composter exists in this game. */
    public static void compost(float chance, Item item) {
        if (item != null) {
            COMPOST.add(new Object[] {chance, item});
        }
    }

    /**
     * Asks for a stonecutting recipe, if Village &amp; Pillage's stonecutter is in this game.
     *
     * @param input  a block of ours, by registry path
     * @param output a block of ours, by registry path
     */
    public static void stonecut(String input, String output, int count) {
        String m = CavesAndCliffsBackport.MOD_ID + ":";
        STONECUT.add(m + input + "=" + m + output + (count > 1 ? "*" + count : ""));
    }

    /** @return the stonecutting recipes asked for, in V&amp;P's {@code input=output*count} form */
    public static List<String> stonecutting() {
        return java.util.Collections.unmodifiableList(STONECUT);
    }

    /** Called from the mod's init, after every registry event. */
    public static void init() {
        if (Loader.isModLoaded(Owners.VILLAGE_AND_PILLAGE) && !STONECUT.isEmpty()) {
            addStonecutting();
        }
        if (Loader.isModLoaded(Owners.VILLAGE_AND_PILLAGE) && !COMPOST.isEmpty()) {
            try {
                Method register = Class.forName("com.exiledradio.villagerbackport.block.Compostables")
                        .getMethod("register", float.class, Item.class);
                for (Object[] entry : COMPOST) {
                    register.invoke(null, entry[0], entry[1]);
                }
                CavesAndCliffsBackport.LOGGER.info("Added {} items to the composter.", COMPOST.size());
            } catch (ReflectiveOperationException | RuntimeException e) {
                CavesAndCliffsBackport.LOGGER.warn("Could not add items to the composter; they stay uncompostable.", e);
            }
        }
    }

    /**
     * Village &amp; Pillage reads its config file back into its fields when its settings are saved, which drops the
     * cuts added here; this puts them back after its own handler, so they never reach its file either.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Owners.VILLAGE_AND_PILLAGE.equals(event.getModID()) && !STONECUT.isEmpty()) {
            addStonecutting();
        }
    }

    private static void addStonecutting() {
        try {
            Object workstations = Class.forName("com.exiledradio.villagerbackport.ModConfig").getField("workstations").get(null);
            java.lang.reflect.Field extras = workstations.getClass().getField("extraStonecutterRecipes");
            java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<String>(java.util.Arrays.asList((String[]) extras.get(workstations)));
            int before = all.size();
            all.addAll(STONECUT);
            extras.set(workstations, all.toArray(new String[0]));
            Class.forName("com.exiledradio.villagerbackport.block.StonecutterRecipes").getMethod("invalidate").invoke(null);
            CavesAndCliffsBackport.LOGGER.info("Added {} cuts to the stonecutter.", all.size() - before);
        } catch (ReflectiveOperationException | RuntimeException e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not add copper to the stonecutter; it stays uncuttable there.", e);
        }
    }
}
