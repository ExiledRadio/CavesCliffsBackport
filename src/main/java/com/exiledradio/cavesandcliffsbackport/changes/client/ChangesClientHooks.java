package com.exiledradio.cavesandcliffsbackport.changes.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.changes.ChangesHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;

/** The client side of the "Other Changes" page: what {@code height.asm.ChangesTransformer}'s client patches call. */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID, value = Side.CLIENT)
public final class ChangesClientHooks {

    private static final Field NAME_FIELD = ObfuscationReflectionHelper.findField(GuiRepair.class, "field_147091_w");

    /** The local player's last move: what it meant to do (strafe, forward, yaw) and where it went. */
    private static float lastStrafe;
    private static float lastForward;
    private static float lastYaw;
    private static double lastDx;
    private static double lastDz;

    private ChangesClientHooks() {
    }

    // ------------------------------------------------------------------------------------------
    // Absorption hearts (1.17): ChangesHooks.absorptionU.

    /** @param u the texture x Forge's renderHealth computed for an absorption heart (full or half) */
    public static int absorptionU(int u) {
        return ModConfig.changes.absorptionHearts ? ChangesHooks.absorptionU(u) : u;
    }

    // ------------------------------------------------------------------------------------------
    // Sprinting (1.18): a horizontal collision stops sprinting only when it is not minor, that is when the way the
    // player went is more than 8 degrees off the way it meant to go (LocalPlayer.aiStep / isHorizontalCollisionMinor).

    /** Replaces {@code collidedHorizontally} in EntityPlayerSP's sprint test. */
    public static boolean sprintBlocked(EntityPlayerSP player) {
        if (!player.collidedHorizontally) {
            return false;
        }
        return !ModConfig.changes.sprintCollisions || !ChangesHooks.isMinor(lastStrafe, lastForward, lastYaw, lastDx, lastDz);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        lastStrafe = player.moveStrafing;
        lastForward = player.moveForward;
        lastYaw = player.rotationYaw;
        lastDx = player.posX - player.prevPosX;
        lastDz = player.posZ - player.prevPosZ;
    }

    // ------------------------------------------------------------------------------------------

    /** Anvil names of 50 characters (1.17): the text field (the server's limit is patched). */
    @SubscribeEvent
    public static void initGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiRepair) {
            try {
                GuiTextField field = (GuiTextField) NAME_FIELD.get(event.getGui());
                if (field != null) {
                    field.setMaxStringLength(ModConfig.changes.anvilNames ? 50 : 35);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
