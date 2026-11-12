package com.exiledradio.cavesandcliffsbackport.music;

import net.minecraft.item.ItemRecord;
import net.minecraft.util.SoundEvent;

/**
 * A 1.18 music disc on 1.12.2's own record item, so the jukebox, the "Now Playing" line, the tooltip, the rare name
 * color and the record stat are vanilla's. The description line is {@code item.record.<name>.desc}, as 1.12.2's
 * records.
 *
 * <p>1.18's disc also carries its comparator signal. 1.12.2's jukebox works the signal out from the item id, right
 * for vanilla's twelve discs only; the value is kept here, under the name the family's jukebox patch reads, for
 * when that patch is shared (see {@code docs/CORE_REQUESTS.md}).
 */
public final class ItemMusicDisc extends ItemRecord {

    private final SoundEvent music;
    private final int signal;

    public ItemMusicDisc(String name, SoundEvent sound, int signal) {
        super(name, sound);
        this.music = sound;
        this.signal = signal;
    }

    /** @return the comparator signal from a jukebox playing this disc (1.18's {@code analogOutput}) */
    public int comparatorSignal() {
        return this.signal;
    }

    /** @return the disc's sound event (vanilla's getter is client-only) */
    public SoundEvent music() {
        return this.music;
    }
}
