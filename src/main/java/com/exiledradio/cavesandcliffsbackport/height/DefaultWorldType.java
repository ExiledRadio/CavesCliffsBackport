package com.exiledradio.cavesandcliffsbackport.height;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.PropertyManager;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;

/**
 * The Caves & Cliffs world type as the one new worlds start on, as 1.18's terrain is vanilla's default
 * ({@code height.defaultWorldType}). Only where nobody chose: the Create World screen starts on it
 * ({@code height.client.CreateWorldDefault}), and a dedicated server whose server.properties has no {@code level-type}
 * yet (a new server) gets {@code level-type=cavesandcliffs}. A player who picks Default, a server that names DEFAULT,
 * and every existing world keep 1.12's terrain. Nothing changes when the world type is off.
 */
public final class DefaultWorldType {

    private DefaultWorldType() {
    }

    /** @return the world type new worlds should start on, or null to leave vanilla's Default */
    @Nullable
    public static WorldType preferred() {
        return ModConfig.height.defaultWorldType && HeightContent.allowed() ? HeightContent.worldType() : null;
    }

    /**
     * From init. A dedicated server reads server.properties before init and its {@code level-type} only after, when it
     * makes or loads the world; a key that is not there yet is a new server's.
     */
    public static void serverInit() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldType type = preferred();
        if (!(server instanceof DedicatedServer) || type == null) {
            return;
        }
        try {
            PropertyManager settings = ReflectionHelper.getPrivateValue(DedicatedServer.class, (DedicatedServer) server,
                    "settings", "field_71340_o");
            if (settings != null && !settings.hasProperty("level-type")) {
                settings.setProperty("level-type", type.getName());
                settings.saveProperties();
                CavesAndCliffsBackport.LOGGER.info("New server: level-type={} (height.defaultWorldType). Set level-type=DEFAULT "
                        + "in server.properties for the 1.12 terrain.", type.getName());
            }
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not read server.properties for the default world type ({})", e.toString());
        }
    }
}
