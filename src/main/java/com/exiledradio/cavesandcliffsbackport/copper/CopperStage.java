package com.exiledradio.cavesandcliffsbackport.copper;

import net.minecraft.block.material.MapColor;

/** The four oxidation stages, in order, with their registry name prefixes and map colors. */
public enum CopperStage {
    UNAFFECTED("", MapColor.ADOBE),
    EXPOSED("exposed_", MapColor.SILVER_STAINED_HARDENED_CLAY),
    WEATHERED("weathered_", MapColor.CYAN),
    OXIDIZED("oxidized_", MapColor.CYAN);

    public final String prefix;
    public final MapColor color;

    CopperStage(String prefix, MapColor color) {
        this.prefix = prefix;
        this.color = color;
    }

    /** 1.18's name for the full block of a stage: {@code copper_block}, then {@code exposed_copper} and so on. */
    public String blockName() {
        return this == UNAFFECTED ? "copper_block" : this.prefix + "copper";
    }
}
