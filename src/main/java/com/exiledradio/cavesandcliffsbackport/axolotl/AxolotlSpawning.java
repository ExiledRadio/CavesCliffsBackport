package com.exiledradio.cavesandcliffsbackport.axolotl;

import com.exiledradio.cavesandcliffsbackport.ModConfig;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.List;
import java.util.Random;

/**
 * Natural axolotl spawning: 1.17's own mob category ("axolotls", at most 5 around a player) as a small spawner
 * of our own.
 *
 * <h2>Why not the biome spawn lists</h2>
 * 1.12.2 spawns by biome, and a biome is a whole column: there is no "this cave, but not the hill above it".
 * Lush caves here are regions inside the ground, so the spawner asks {@link #habitat} about each place instead.
 * It otherwise works as the vanilla spawner does: every tick, one random block in every chunk within 7 chunks
 * of a player; if that block is water, a group is tried around it.
 *
 * <h2>The group (1.17's numbers)</h2>
 * Up to three walks of 4-6 steps from the first block, each step up to 5 blocks sideways; each step spawns an
 * axolotl if the place allows it (water, clay right below, the habitat, 24 blocks from players and the world
 * spawn, nothing in the way), up to four. A group shares two colors, and its third and fourth are babies.
 */
public final class AxolotlSpawning {

    /**
     * Where axolotls may spawn. The stand-in is dark underground water; the integrator replaces it with the
     * lush regions of {@code common/CaveRegions} (see {@code docs/streams/axolotl.md}).
     */
    public static AxolotlHabitat habitat = new AxolotlHabitat.LushCaves();

    private static final int CHUNK_RANGE = 8;
    private static final int MAX_GROUP = 4;

    private AxolotlSpawning() {
    }

    /** One spawning round for a world; called every tick. */
    static void tick(WorldServer world) {
        if (!ModConfig.axolotls.spawning || ModConfig.axolotls.spawnCap <= 0 || world.playerEntities.isEmpty()
                || !world.getGameRules().getBoolean("doMobSpawning")) {
            return;
        }
        List<EntityPlayer> players = world.playerEntities;
        int chunks = 0;
        for (int i = 0; i < players.size(); i++) {
            if (!players.get(i).isSpectator()) {
                chunks += countOwnChunks(players, i);
            }
        }
        if (chunks == 0) {
            return;
        }
        // The vanilla cap: the category's number for every 17x17 chunks around players.
        int cap = ModConfig.axolotls.spawnCap * chunks / 289;
        int count = 0;
        for (Entity e : world.loadedEntityList) {
            if (e instanceof EntityAxolotl && !((EntityAxolotl) e).isNoDespawnRequired() && !((EntityAxolotl) e).isFromBucket()) {
                ++count;
            }
        }
        if (count >= cap) {
            return;
        }
        Random rand = world.rand;
        for (int i = 0; i < players.size() && count < cap; i++) {
            EntityPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            int px = MathHelper.floor(player.posX / 16.0D);
            int pz = MathHelper.floor(player.posZ / 16.0D);
            // The outermost ring counts toward the cap but is not spawned in, as vanilla.
            for (int dx = -CHUNK_RANGE + 1; dx < CHUNK_RANGE && count < cap; dx++) {
                for (int dz = -CHUNK_RANGE + 1; dz < CHUNK_RANGE && count < cap; dz++) {
                    int cx = px + dx;
                    int cz = pz + dz;
                    if (ownedByEarlier(players, i, cx, cz)) {
                        continue;
                    }
                    Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    int x = (cx << 4) + rand.nextInt(16);
                    int z = (cz << 4) + rand.nextInt(16);
                    int top = chunk.getHeightValue(x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, rand.nextInt(Math.max(top, 1) + 1), z);
                    if (chunk.getBlockState(pos).getMaterial() == Material.WATER) {
                        count += spawnGroup(world, pos);
                    }
                }
            }
        }
    }

    /** @return how many chunks of this player's 17x17 no earlier player in the list already covers */
    private static int countOwnChunks(List<EntityPlayer> players, int index) {
        if (index == 0) {
            return 289;
        }
        EntityPlayer player = players.get(index);
        int px = MathHelper.floor(player.posX / 16.0D);
        int pz = MathHelper.floor(player.posZ / 16.0D);
        int n = 0;
        for (int dx = -CHUNK_RANGE; dx <= CHUNK_RANGE; dx++) {
            for (int dz = -CHUNK_RANGE; dz <= CHUNK_RANGE; dz++) {
                if (!ownedByEarlier(players, index, px + dx, pz + dz)) {
                    ++n;
                }
            }
        }
        return n;
    }

    private static boolean ownedByEarlier(List<EntityPlayer> players, int index, int cx, int cz) {
        for (int j = 0; j < index; j++) {
            EntityPlayer other = players.get(j);
            if (!other.isSpectator() && Math.abs(MathHelper.floor(other.posX / 16.0D) - cx) <= CHUNK_RANGE
                    && Math.abs(MathHelper.floor(other.posZ / 16.0D) - cz) <= CHUNK_RANGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries a group around a block.
     *
     * @return how many axolotls spawned
     */
    public static int spawnGroup(WorldServer world, BlockPos start) {
        Random rand = world.rand;
        BlockPos spawnPoint = world.getSpawnPoint();
        int spawned = 0;
        for (int walk = 0; walk < 3; walk++) {
            int x = start.getX();
            int z = start.getZ();
            int y = start.getY();
            IEntityLivingData group = null;
            int steps = 4 + rand.nextInt(3);
            for (int step = 0; step < steps; step++) {
                x += rand.nextInt(6) - rand.nextInt(6);
                z += rand.nextInt(6) - rand.nextInt(6);
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.isBlockLoaded(pos)) {
                    continue;
                }
                double cx = x + 0.5D;
                double cz = z + 0.5D;
                if (world.isAnyPlayerWithinRangeAt(cx, y, cz, 24.0D) || spawnPoint.distanceSq(cx, y, cz) < 576.0D) {
                    continue;
                }
                IBlockState state = world.getBlockState(pos);
                if (state.getMaterial() != Material.WATER || world.getBlockState(pos.up()).isNormalCube()) {
                    continue;
                }
                // The axolotl's own spawn rule (clay below, the habitat) before it is built: nearly every water
                // block the spawner finds is a river, ocean or lake, and building an axolotl there only to throw it
                // away cost a fifth of a server tick.
                if (world.getBlockState(pos.down()).getBlock() != Blocks.CLAY || !habitat.canSpawnAt(world, pos)) {
                    continue;
                }
                EntityAxolotl axolotl = new EntityAxolotl(world);
                axolotl.setLocationAndAngles(cx, y, cz, rand.nextFloat() * 360.0F, 0.0F);
                Event.Result result = ForgeEventFactory.canEntitySpawn(axolotl, world, (float) cx, (float) y, (float) cz, false);
                if (result == Event.Result.DENY
                        || (result == Event.Result.DEFAULT && !(axolotl.getCanSpawnHere() && axolotl.isNotColliding()))) {
                    continue;
                }
                if (!ForgeEventFactory.doSpecialSpawn(axolotl, world, (float) cx, (float) y, (float) cz)) {
                    group = axolotl.onInitialSpawn(world.getDifficultyForLocation(pos), group);
                }
                if (world.spawnEntity(axolotl) && ++spawned >= MAX_GROUP) {
                    return spawned;
                }
            }
        }
        return spawned;
    }
}
