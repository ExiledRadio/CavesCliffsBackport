package com.exiledradio.cavesandcliffsbackport.dripstone;

import net.minecraft.util.IStringSerializable;

/** A pointed dripstone segment's thickness, 1.17's names and order. */
public enum Thickness implements IStringSerializable {
    TIP_MERGE("tip_merge"),
    TIP("tip"),
    FRUSTUM("frustum"),
    MIDDLE("middle"),
    BASE("base");

    private final String name;

    Thickness(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
