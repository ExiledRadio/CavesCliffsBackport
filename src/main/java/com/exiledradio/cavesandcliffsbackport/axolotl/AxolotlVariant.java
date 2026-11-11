package com.exiledradio.cavesandcliffsbackport.axolotl;

import java.util.Random;

/** The five axolotl colors, with 1.17's ids. Blue never spawns: only breeding gives it, one time in 1200. */
public enum AxolotlVariant {
    LUCY(0, "lucy", true),
    WILD(1, "wild", true),
    GOLD(2, "gold", true),
    CYAN(3, "cyan", true),
    BLUE(4, "blue", false);

    private static final AxolotlVariant[] BY_ID = values();
    private static final AxolotlVariant[] COMMON = {LUCY, WILD, GOLD, CYAN};

    public final int id;
    /** The texture's suffix: {@code textures/entity/axolotl/axolotl_<name>.png}. */
    public final String name;
    public final boolean common;

    AxolotlVariant(int id, String name, boolean common) {
        this.id = id;
        this.name = name;
        this.common = common;
    }

    /** @return the variant with this id; an id out of range (a damaged save or bucket) gives lucy */
    public static AxolotlVariant byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : LUCY;
    }

    public static boolean valid(int id) {
        return id >= 0 && id < BY_ID.length;
    }

    public static AxolotlVariant randomCommon(Random random) {
        return COMMON[random.nextInt(COMMON.length)];
    }
}
