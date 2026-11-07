package com.exiledradio.cavesandcliffsbackport.common;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/**
 * Grants our advancements from code. Each one's file in {@code assets/<modid>/advancements/} has a single
 * criterion {@code code} with the {@code minecraft:impossible} trigger, so only these calls complete it -
 * 1.12 has none of the triggers (item used on block, lightning strike) that 1.17 uses.
 */
public final class Advancements {

    private Advancements() {
    }

    /** @param path such as {@code husbandry/wax_on}; does nothing for fake players, clients or unknown paths */
    public static void grant(EntityPlayer player, String path) {
        if (!(player instanceof EntityPlayerMP) || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        Advancement adv = server.getAdvancementManager().getAdvancement(Content.id(path));
        if (adv != null) {
            p.getAdvancements().grantCriterion(adv, "code");
        }
    }

    /** @return whether the player has it, for tests */
    public static boolean has(EntityPlayerMP p, String path) {
        Advancement adv = p.getServer().getAdvancementManager().getAdvancement(Content.id(path));
        return adv != null && p.getAdvancements().getProgress(adv).isDone();
    }
}
