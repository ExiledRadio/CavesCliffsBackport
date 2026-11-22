package com.exiledradio.cavesandcliffsbackport.music.client;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;

import com.google.common.collect.ImmutableSet;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Adds the tracks 1.18 put in the game music to 1.12.2's own {@code music.game}, only while the switch is on.
 * The same kind of pack ({@link #FINGERSPIT}) adds 26.2's tracks to this mod's biome lists while Game Drops
 * Backport, which ships them, is installed.
 *
 * <p>It serves one file, {@code minecraft:sounds.json}, which the game merges with every other pack's: its
 * {@code music.game} lists four more sounds and replaces nothing, so 1.12.2's twelve tracks, another mod's and a
 * resource pack's all stay. The creative music includes the game music by reference, so it gains them too. The file
 * sits outside {@code assets/} (Forge serves all of a mod's {@code assets/}, all the time); the tracks themselves
 * are ordinary sound files of this mod.
 */
final class GameMusicPack implements IResourcePack {

    static final String FILE = "/" + CavesAndCliffsBackport.MOD_ID + "_packs/game_music/assets/minecraft/sounds.json";
    /** 26.2's additions to the eight biome lists (fingerspit's tracks, Game Drops Backport's files). */
    static final String FINGERSPIT = "/" + CavesAndCliffsBackport.MOD_ID + "_packs/fingerspit/assets/"
            + CavesAndCliffsBackport.MOD_ID + "/sounds.json";

    private final String file;
    private final String namespace;
    private final String name;

    /** 1.18's game music pack. */
    GameMusicPack() {
        this(FILE, "minecraft", "game_music");
    }

    GameMusicPack(String file, String namespace, String name) {
        this.file = file;
        this.namespace = namespace;
        this.name = name;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        InputStream in = resourceExists(location) ? GameMusicPack.class.getResourceAsStream(this.file) : null;
        if (in == null) {
            throw new FileNotFoundException(location.toString());
        }
        return in;
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return this.namespace.equals(location.getNamespace()) && "sounds.json".equals(location.getPath());
    }

    /** @return true if the file is in the jar */
    boolean complete() {
        return GameMusicPack.class.getResource(this.file) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return ImmutableSet.of(this.namespace);
    }

    /** A default pack is never listed or described, so there is no pack.mcmeta to read. */
    @Nullable
    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getPackName() {
        return CavesAndCliffsBackport.MOD_ID + ":" + this.name;
    }
}
