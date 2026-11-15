package com.exiledradio.cavesandcliffsbackport.height.client;

import com.exiledradio.cavesandcliffsbackport.height.DefaultWorldType;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Create World screen starts on the Caves & Cliffs world type ({@link DefaultWorldType}). Only a new screen: one
 * re-creating a world (its seed is filled in) keeps that world's type, and coming back from a sub-screen keeps the
 * pick. Picking Default still makes a 1.12 world.
 */
@SideOnly(Side.CLIENT)
public final class CreateWorldDefault {

    /** For the client self-test: screens set. */
    public static volatile int applied;

    private final Set<GuiCreateWorld> seen = Collections.newSetFromMap(new WeakHashMap<GuiCreateWorld, Boolean>());

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiCreateWorld)) {
            return;
        }
        GuiCreateWorld gui = (GuiCreateWorld) event.getGui();
        if (!this.seen.add(gui)) {
            return;
        }
        WorldType type = DefaultWorldType.preferred();
        if (type == null) {
            return;
        }
        try {
            String seed = ReflectionHelper.getPrivateValue(GuiCreateWorld.class, gui, "worldSeed", "field_146329_I");
            int index = ReflectionHelper.getPrivateValue(GuiCreateWorld.class, gui, "selectedIndex", "field_146331_K");
            if ((seed == null || seed.isEmpty()) && index == WorldType.DEFAULT.getId()) {
                ReflectionHelper.setPrivateValue(GuiCreateWorld.class, gui, type.getId(), "selectedIndex", "field_146331_K");
                applied++;
            }
        } catch (Exception e) {
            com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.debug("Create World: could not preselect {} ({})",
                    type.getName(), e.toString());
        }
    }
}
