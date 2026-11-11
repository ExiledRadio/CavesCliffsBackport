package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * What axolotls attack: 1.17's two entity tags as two config lists of registry names.
 *
 * <p>Names only, so fish, drowned and glow squid from other mods (or other streams of this one) are found when
 * they are installed and simply never match when they are not.
 */
final class AxolotlTargets {

    private static String[] huntSource, hostileSource;
    private static Set<ResourceLocation> hunt = new HashSet<ResourceLocation>(), hostile = new HashSet<ResourceLocation>();

    private AxolotlTargets() {
    }

    /** Re-reads a list when the config screen replaced it. */
    private static void refresh() {
        if (huntSource != ModConfig.axolotls.huntTargets) {
            huntSource = ModConfig.axolotls.huntTargets;
            hunt = parse(huntSource);
        }
        if (hostileSource != ModConfig.axolotls.hostileTargets) {
            hostileSource = ModConfig.axolotls.hostileTargets;
            hostile = parse(hostileSource);
        }
    }

    private static Set<ResourceLocation> parse(String[] names) {
        Set<ResourceLocation> out = new HashSet<ResourceLocation>();
        if (names != null) {
            for (String n : names) {
                if (n != null && !n.trim().isEmpty()) {
                    out.add(new ResourceLocation(n.trim()));
                }
            }
        }
        return out;
    }

    /** @return false when both lists are empty, so nothing needs looking for */
    static boolean any() {
        refresh();
        return !hunt.isEmpty() || !hostile.isEmpty();
    }

    /** @return true for prey: attacked, then left alone for two minutes */
    static boolean isHunted(Entity entity) {
        refresh();
        if (hunt.isEmpty()) {
            return false;
        }
        ResourceLocation id = EntityList.getKey(entity);
        return id != null && hunt.contains(id);
    }

    /** @return true for the mobs axolotls always attack, and that attack them */
    static boolean isHostile(Entity entity) {
        refresh();
        if (hostile.isEmpty()) {
            return false;
        }
        ResourceLocation id = EntityList.getKey(entity);
        return id != null && hostile.contains(id);
    }
}
