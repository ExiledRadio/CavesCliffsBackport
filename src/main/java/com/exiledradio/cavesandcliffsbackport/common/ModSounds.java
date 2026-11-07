package com.exiledradio.cavesandcliffsbackport.common;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import net.minecraft.block.SoundType;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sound events and block sound types, made on first use and registered together.
 *
 * <p>Blocks take their sound type in their constructors, during the block registry event, and 1.12
 * fires the sound event registry event after it. So events are created here when a stream first asks
 * for them and only registered later; a stream that is switched off never asks, and its sounds are
 * never registered. Names are 1.18's ({@code block.deepslate.break}) under this mod's namespace, and
 * {@code sounds.json} (written by each stream's generator) maps them to the files.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class ModSounds {

    private static final Map<String, SoundEvent> EVENTS = new HashMap<String, SoundEvent>();
    private static final List<SoundEvent> ORDER = new ArrayList<SoundEvent>();
    private static final Map<String, SoundType> TYPES = new HashMap<String, SoundType>();
    private static boolean registered;

    private ModSounds() {
    }

    /** @return the event {@code cavesandcliffsbackport:<name>}, made once */
    public static synchronized SoundEvent event(String name) {
        SoundEvent e = EVENTS.get(name);
        if (e == null) {
            if (registered) {
                throw new IllegalStateException("sound " + name + " asked for after sounds were registered");
            }
            e = new SoundEvent(Content.id(name));
            e.setRegistryName(Content.id(name));
            EVENTS.put(name, e);
            ORDER.add(e);
        }
        return e;
    }

    /**
     * @param group 1.18's sound group, such as {@code block.deepslate}: its break, step, place, hit and
     *              fall events
     */
    public static synchronized SoundType type(String group, float volume, float pitch) {
        String key = group + "@" + volume + "@" + pitch;
        SoundType t = TYPES.get(key);
        if (t == null) {
            t = new SoundType(volume, pitch, event(group + ".break"), event(group + ".step"), event(group + ".place"),
                    event(group + ".hit"), event(group + ".fall"));
            TYPES.put(key, t);
        }
        return t;
    }

    public static SoundType type(String group) {
        return type(group, 1.0F, 1.0F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void register(RegistryEvent.Register<SoundEvent> event) {
        registered = true;
        for (SoundEvent e : ORDER) {
            event.getRegistry().register(e);
        }
    }
}
