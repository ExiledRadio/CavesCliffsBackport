package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.height.Heights;
import com.exiledradio.cavesandcliffsbackport.height.asm.PatchReport;

import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.FlyingNodeProcessor;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/**
 * Mob movement below y 0 ({@code height.asm.AiPatches}): strafing, pathfinding, bats and teleporting on a floor at
 * y -41 of an extended world, as they work at y 100. Run in a Caves &amp; Cliffs world:
 * {@code sh tools/height/selftest.sh depthai}.
 */
public final class DepthAiSelfTest implements SelfTest {

    private static final int X = 26000;
    private static final int Z = 26000;
    private static final int FLOOR = -41;

    @Override
    public String name() {
        return "depthai";
    }

    @Override
    public void run(Check c) throws Exception {
        WorldServer world = c.world;
        // Loads the classes, then asks whether each was patched.
        new WalkNodeProcessor();
        new FlyingNodeProcessor();
        new EntityBat(world);
        new EntitySkeleton(world);
        for (String label : new String[] {"WalkNodeProcessor", "FlyingNodeProcessor", "PathNavigateGround", "EntityBat",
                "EntityLivingBaseTeleport"}) {
            c.ok(PatchReport.compatPatched(label), label + " patched");
        }
        if (Heights.minY(world) >= 0) {
            return;
        }
        for (int cx = (X - 4) >> 4; cx <= (X + 30) >> 4; cx++) {
            for (int cz = (Z - 4) >> 4; cz <= (Z + 30) >> 4; cz++) {
                world.getChunk(cx, cz);
            }
        }
        c.fill(new BlockPos(X - 4, FLOOR, Z - 4), new BlockPos(X + 30, FLOOR, Z + 30), Blocks.STONE.getDefaultState());
        c.fill(new BlockPos(X - 4, FLOOR + 1, Z - 4), new BlockPos(X + 30, FLOOR + 16, Z + 30), Blocks.AIR.getDefaultState());
        c.fill(new BlockPos(X - 4, FLOOR + 17, Z - 4), new BlockPos(X + 30, FLOOR + 17, Z + 30), Blocks.STONE.getDefaultState());
        int y = FLOOR + 1;

        PathNodeType walk = new WalkNodeProcessor().getPathNodeType(world, X + 5, y, Z + 5);
        c.eq(PathNodeType.WALKABLE, walk, "air on a floor at y " + y + " is walkable");
        ChunkCache cache = new ChunkCache(world, new BlockPos(X, y - 4, Z), new BlockPos(X + 16, y + 4, Z + 16), 0);
        c.eq(PathNodeType.WALKABLE, new WalkNodeProcessor().getPathNodeType(cache, X + 5, y, Z + 5),
                "and through pathfinding's ChunkCache");
        c.eq(PathNodeType.WALKABLE, new FlyingNodeProcessor().getPathNodeType(world, X + 5, y, Z + 5),
                "and for a flying mob's node processor");

        // A skeleton's strafe (EntityAIAttackRangedBow) keeps its sideways step on walkable ground.
        EntitySkeleton skeleton = new EntitySkeleton(world);
        skeleton.setPosition(X + 12.5, y, Z + 12.5);
        world.spawnEntity(skeleton);
        skeleton.onGround = true;
        skeleton.getMoveHelper().strafe(-0.5F, 0.5F);
        skeleton.getMoveHelper().onUpdateMoveHelper();
        c.ok(skeleton.moveStrafing != 0.0F, "a skeleton at y " + y + " strafes (" + skeleton.moveStrafing + ", forward "
                + skeleton.moveForward + ")");

        // A path to a point in the air ends on the floor under it.
        skeleton.setPosition(X + 12.5, y, Z + 12.5);
        Path path = skeleton.getNavigator().getPathToPos(new BlockPos(X + 18, y + 4, Z + 12));
        c.ok(path != null && path.getFinalPathPoint() != null && path.getFinalPathPoint().y == y,
                "a path to a point in the air ends on the floor below it ("
                        + (path == null || path.getFinalPathPoint() == null ? "none" : path.getFinalPathPoint()) + ")");

        // Teleporting (an enderman's, chorus fruit's) finds the floor.
        boolean teleported = skeleton.attemptTeleport(X + 6.5, y + 6, Z + 6.5);
        c.ok(teleported && Math.abs(skeleton.posY - y) < 0.01, "a teleport to y " + (y + 6) + " lands on the floor ("
                + teleported + ", y " + skeleton.posY + ")");
        world.removeEntityDangerously(skeleton);

        // A bat keeps its target for more than a tick.
        EntityBat bat = new EntityBat(world);
        bat.setPosition(X + 12.5, y + 8, Z + 12.5);
        world.spawnEntity(bat);
        bat.setIsBatHanging(false);
        java.lang.reflect.Method tasks = ReflectionHelper.findMethod(EntityBat.class, "updateAITasks", "func_70619_bc");
        java.lang.reflect.Field target = ReflectionHelper.findField(EntityBat.class, "spawnPosition", "field_82237_a");
        Object last = null;
        int changes = 0;
        for (int i = 0; i < 20; i++) {
            tasks.invoke(bat);
            Object now = target.get(bat);
            if (now != last) {
                changes++;
            }
            last = now;
        }
        c.ok(changes <= 12, "a bat at y " + (y + 8) + " keeps its flight target (" + changes + " new targets in 20 ticks)");
        world.removeEntityDangerously(bat);
    }
}
