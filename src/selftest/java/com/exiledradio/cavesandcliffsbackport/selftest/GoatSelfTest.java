package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.Content;
import com.exiledradio.cavesandcliffsbackport.goat.EntityAIGoatRam;
import com.exiledradio.cavesandcliffsbackport.goat.EntityGoat;
import com.exiledradio.cavesandcliffsbackport.goat.GoatContent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream Gt: the goat's registration, saved data, chances, fall damage, milk and wheat, breeding, a ram at a
 * mob and at a player, a long jump, spawning and the boat advancement.
 *
 * <p>The whole test runs inside one server tick, so mobs are ticked by hand ({@link #tick}); timers in the
 * goat's goals count the goat's own age for that reason.
 */
public final class GoatSelfTest implements SelfTest {

    /** Everything this test spawned, removed at the end. */
    private final List<Entity> spawned = new ArrayList<Entity>();

    @Override
    public String name() {
        return "goat";
    }

    @Override
    public void run(Check c) throws Exception {
        if (!c.ok(GoatContent.on(), "goats are registered")) {
            return;
        }
        // Lets a hand-ticked mob update although the chunks 32 blocks around it are not all loaded.
        MinecraftForge.EVENT_BUS.register(this);
        try {
            registration(c);
            savedData(c);
            chances(c);
            fallDamage(c);
            milkAndWheat(c);
            breeding(c);
            ramRules(c);
            ramMob(c);
            ramPlayer(c);
            longJump(c);
            spawning(c);
            boat(c);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(this);
            for (Entity e : this.spawned) {
                c.world.removeEntity(e);
            }
            this.spawned.clear();
        }
    }

    @SubscribeEvent
    public void canUpdate(EntityEvent.CanUpdate event) {
        if (this.spawned.contains(event.getEntity())) {
            event.setCanUpdate(true);
        }
    }

    private <E extends Entity> E spawn(Check c, E e, double x, double y, double z) {
        e.setPosition(x, y, z);
        c.world.spawnEntity(e);
        this.spawned.add(e);
        return e;
    }

    private EntityGoat goat(Check c, double x, double y, double z) {
        EntityGoat goat = new EntityGoat(c.world);
        // Nothing happens on its own unless a test asks for it.
        goat.setRamCooldown(1000000);
        goat.setJumpCooldown(1000000);
        return spawn(c, goat, x, y, z);
    }

    private static void tick(Check c, Entity... entities) {
        for (Entity e : entities) {
            if (!e.isDead) {
                c.world.updateEntity(e);
            }
        }
    }

    private void remove(Check c, Entity... entities) {
        for (Entity e : entities) {
            c.world.removeEntity(e);
        }
    }

    // ------------------------------------------------------------------------------------------

    private void registration(Check c) {
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(Content.id("goat"));
        if (!c.ok(entry != null && entry.getEntityClass() == EntityGoat.class, "entity cavesandcliffsbackport:goat")) {
            return;
        }
        c.eq(15, EntityRegistry.instance().lookupModSpawn(EntityGoat.class, false).getModEntityId(), "entity id");
        c.ok(entry.getEgg() != null, "spawn egg");
        if (entry.getEgg() != null) {
            c.eq(0xA59B8E, entry.getEgg().primaryColor, "egg color");
            c.eq(0x55493E, entry.getEgg().secondaryColor, "egg spots");
        }
        int sounds = 0;
        for (String e : new String[] {"ambient", "death", "eat", "hurt", "long_jump", "milk", "prepare_ram", "ram_impact"}) {
            sounds += ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("entity.goat." + e)) ? 1 : 0;
            sounds += ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("entity.goat.screaming." + e)) ? 1 : 0;
        }
        sounds += ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("entity.goat.step")) ? 1 : 0;
        c.eq(17, sounds, "sound events");

        EntityGoat goat = new EntityGoat(c.world);
        c.eq(10.0F, goat.getMaxHealth(), "health");
        c.eq(0.2D, goat.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue(), "speed");
        c.eq(2.0D, goat.ramDamage(), "adult ram damage");
        c.eq(0.9F, goat.width, "width");
        c.eq(1.3F, goat.height, "height");
        c.ok(goat.hasLeftHorn() && goat.hasRightHorn() && !goat.isScreamingGoat(), "a new goat has both horns and does not scream");
        c.ok(goat.isBreedingItem(new ItemStack(Items.WHEAT)) && !goat.isBreedingItem(new ItemStack(Items.CARROT)), "wheat is its food");
        goat.setGrowingAge(-24000);
        c.eq(1.0D, goat.ramDamage(), "kid ram damage");
        c.eq(0.45F, goat.width, "kid width");
        c.eq(0.65F, goat.height, "kid height");
    }

    private void savedData(Check c) {
        EntityGoat goat = new EntityGoat(c.world);
        goat.setScreamingGoat(true);
        goat.setRamCooldown(1234);
        goat.setJumpCooldown(777);
        NBTTagCompound tag = new NBTTagCompound();
        goat.writeEntityToNBT(tag);
        c.ok(tag.hasKey("HasLeftHorn") && tag.getBoolean("HasLeftHorn"), "HasLeftHorn is written true");
        c.ok(tag.hasKey("HasRightHorn") && tag.getBoolean("HasRightHorn"), "HasRightHorn is written true");
        c.ok(tag.getBoolean("IsScreamingGoat"), "IsScreamingGoat is written");
        NBTTagCompound memories = tag.getCompoundTag("Brain").getCompoundTag("memories");
        c.eq(1234, memories.getCompoundTag("minecraft:ram_cooldown_ticks").getInteger("value"), "ram cooldown saved as the brain memory");
        c.eq(777, memories.getCompoundTag("minecraft:long_jump_cooling_down").getInteger("value"), "jump cooldown saved as the brain memory");

        EntityGoat read = new EntityGoat(c.world);
        read.readEntityFromNBT(tag);
        c.ok(read.isScreamingGoat() && read.hasLeftHorn() && read.hasRightHorn(), "screaming and horns read back");
        c.eq(1234, read.getRamCooldown(), "ram cooldown read back");
        c.eq(777, read.getJumpCooldown(), "jump cooldown read back");

        // A later update takes a horn by writing the tag; it must stick.
        tag.setBoolean("HasLeftHorn", false);
        read.readEntityFromNBT(tag);
        c.ok(!read.hasLeftHorn() && read.hasRightHorn(), "a horn taken through the tag stays taken");
        NBTTagCompound again = new NBTTagCompound();
        read.writeEntityToNBT(again);
        c.ok(again.hasKey("HasLeftHorn") && !again.getBoolean("HasLeftHorn"), "and is written back false");

        // No tags at all (a bare summon): both horns, no cooldowns.
        EntityGoat bare = new EntityGoat(c.world);
        bare.readEntityFromNBT(new NBTTagCompound());
        c.ok(bare.hasLeftHorn() && bare.hasRightHorn() && !bare.isScreamingGoat() && bare.getRamCooldown() == 0, "a goat without tags");
    }

    private void chances(Check c) {
        DifficultyInstance difficulty = c.world.getDifficultyForLocation(new BlockPos(20000, 100, 20000));
        int screaming = 0, firstKids = 0, laterKids = 0, badCooldown = 0;
        int groups = 3000;
        for (int i = 0; i < groups; i++) {
            IEntityLivingData data = null;
            for (int n = 0; n < 2; n++) {
                EntityGoat goat = new EntityGoat(c.world);
                data = goat.onInitialSpawn(difficulty, data);
                screaming += goat.isScreamingGoat() ? 1 : 0;
                if (goat.isChild()) {
                    if (n == 0) {
                        firstKids++;
                    } else {
                        laterKids++;
                    }
                }
                if (goat.getRamCooldown() < 600 || goat.getRamCooldown() > 6000 || goat.getJumpCooldown() < 600 || goat.getJumpCooldown() > 1200) {
                    badCooldown++;
                }
            }
        }
        // 2% of 6000 is 120 (standard deviation 11); 5% of 3000 is 150 (12).
        c.ok(screaming >= 75 && screaming <= 170, "about 2% of spawned goats scream: " + screaming + " of " + (groups * 2));
        c.eq(0, firstKids, "the first goat of a group is never a kid");
        c.ok(laterKids >= 100 && laterKids <= 205, "about 5% of the others are kids: " + laterKids + " of " + groups);
        c.eq(0, badCooldown, "spawned goats start with a ram cooldown of 600-6000 and a jump cooldown of 600-1200");

        EntityGoat normal = new EntityGoat(c.world);
        EntityGoat screamer = new EntityGoat(c.world);
        screamer.setScreamingGoat(true);
        int fromScreamer = 0, fromNormal = 0;
        for (int i = 0; i < 2000; i++) {
            fromScreamer += normal.createChild(screamer).isScreamingGoat() ? 1 : 0;
            fromNormal += normal.createChild(normal).isScreamingGoat() ? 1 : 0;
        }
        c.eq(2000, fromScreamer, "a kid whose other parent screams, screams");
        c.ok(fromNormal >= 15 && fromNormal <= 75, "about 2% of kids of normal goats scream: " + fromNormal + " of 2000");
        EntityGoat kid = normal.createChild(normal);
        c.ok(kid.getRamCooldown() >= 600 && kid.getJumpCooldown() >= 600, "a kid starts with cooldowns");
    }

    private void fallDamage(Check c) {
        EntityGoat goat = new EntityGoat(c.world);
        goat.fall(13.0F, 1.0F);
        c.eq(10.0F, goat.getHealth(), "a 13-block fall does no damage");
        goat.fall(20.0F, 1.0F);
        c.eq(3.0F, goat.getHealth(), "a 20-block fall does 7 (17 less 10)");
        EntityPig pig = new EntityPig(c.world);
        pig.fall(13.0F, 1.0F);
        c.eq(0.0F, pig.getHealth(), "the same 13-block fall kills a pig");
    }

    private void milkAndWheat(Check c) {
        BlockPos a = c.area();
        EntityGoat goat = goat(c, a.getX() + 5.5, 100, a.getZ() + 5.5);
        EntityPlayerMP p = Players.real(c.world, "goat");
        p.setPosition(a.getX() + 6.5, 100, a.getZ() + 5.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);

        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        c.ok(goat.processInteract(p, EnumHand.MAIN_HAND), "a bucket on a goat is used");
        c.eq(Items.MILK_BUCKET, p.getHeldItemMainhand().getItem(), "and becomes a milk bucket");

        p.inventory.clear();
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
        goat.processInteract(p, EnumHand.MAIN_HAND);
        c.eq(2, p.getHeldItemMainhand().getCount(), "one bucket of a stack is used");
        c.ok(p.inventory.hasItemStack(new ItemStack(Items.MILK_BUCKET)), "the milk goes to the inventory");

        p.inventory.clear();
        p.interactionManager.setGameType(GameType.CREATIVE);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        goat.processInteract(p, EnumHand.MAIN_HAND);
        c.ok(p.getHeldItemMainhand().getItem() == Items.BUCKET && p.inventory.hasItemStack(new ItemStack(Items.MILK_BUCKET)),
                "creative keeps the bucket and gets milk");
        p.interactionManager.setGameType(GameType.SURVIVAL);

        p.inventory.clear();
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WHEAT, 2));
        c.ok(goat.processInteract(p, EnumHand.MAIN_HAND) && goat.isInLove(), "wheat puts a goat in love");
        c.eq(1, p.getHeldItemMainhand().getCount(), "and one wheat is eaten");

        EntityGoat kid = goat(c, a.getX() + 8.5, 100, a.getZ() + 5.5);
        kid.setGrowingAge(-24000);
        p.inventory.clear();
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        kid.processInteract(p, EnumHand.MAIN_HAND);
        c.eq(Items.BUCKET, p.getHeldItemMainhand().getItem(), "a kid gives no milk");
        remove(c, goat, kid);
    }

    private void breeding(Check c) {
        BlockPos a = c.area();
        EntityGoat one = goat(c, a.getX() + 10.5, 100, a.getZ() + 10.5);
        EntityGoat two = goat(c, a.getX() + 13.5, 100, a.getZ() + 10.5);
        one.setInLove(null);
        two.setInLove(null);
        EntityGoat kid = null;
        for (int t = 0; t < 400 && kid == null; t++) {
            tick(c, one, two);
            for (EntityGoat g : c.world.getEntitiesWithinAABB(EntityGoat.class, new AxisAlignedBB(a).grow(40))) {
                if (g.isChild() && !g.isDead && g.getDistance(one) < 16.0F) {
                    kid = g;
                }
            }
        }
        c.ok(kid != null, "two goats in love have a kid");
        if (kid != null) {
            this.spawned.add(kid);
        }
        c.ok(!one.isInLove() && one.getGrowingAge() > 0, "and then wait before breeding again");
        remove(c, one, two);
        if (kid != null) {
            remove(c, kid);
        }
    }

    private void ramRules(Check c) {
        BlockPos a = c.area();
        EntityGoat goat = goat(c, a.getX() + 10.5, 100, a.getZ() + 10.5);
        double x = a.getX() + 13.5, z = a.getZ() + 10.5;
        EntityPig pig = spawn(c, new EntityPig(c.world), x, 100, z);
        c.ok(EntityAIGoatRam.canRam(goat, pig), "a pig can be rammed");
        EntityArmorStand stand = spawn(c, new EntityArmorStand(c.world), x, 100, z + 2);
        c.ok(EntityAIGoatRam.canRam(goat, stand), "an armor stand can be rammed");
        c.ok(!EntityAIGoatRam.canRam(goat, goat(c, x, 100, z - 2)), "another goat cannot");
        c.ok(!EntityAIGoatRam.canRam(goat, spawn(c, new EntityGhast(c.world), x + 6, 104, z)), "a ghast cannot");
        c.ok(!EntityAIGoatRam.canRam(goat, goat), "nor the goat itself");

        EntityPlayerMP p = Players.real(c.world, "goatrules");
        p.setPosition(x, 100, z + 4);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        c.ok(EntityAIGoatRam.canRam(goat, p), "a survival player can be rammed");
        p.interactionManager.setGameType(GameType.CREATIVE);
        c.ok(!EntityAIGoatRam.canRam(goat, p), "a creative player cannot");
        p.interactionManager.setGameType(GameType.SPECTATOR);
        c.ok(!EntityAIGoatRam.canRam(goat, p), "a spectator cannot");

        pig.setEntityInvulnerable(true);
        c.ok(!EntityAIGoatRam.canRam(goat, pig), "an invulnerable mob cannot");
        pig.setEntityInvulnerable(false);

        // Behind a wall: out of sight.
        c.fill(a.add(12, 0, 8), a.add(12, 3, 16), Blocks.STONE.getDefaultState());
        goat.getEntitySenses().clearSensingCache();
        c.ok(!EntityAIGoatRam.canRam(goat, pig), "a mob behind a wall cannot");
        c.fill(a.add(12, 0, 8), a.add(12, 3, 16), Blocks.AIR.getDefaultState());
        goat.getEntitySenses().clearSensingCache();

        EnumDifficulty was = c.world.getDifficulty();
        c.world.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
        c.ok(!EntityAIGoatRam.canRam(goat, pig), "nothing is rammed in Peaceful");
        c.world.getWorldInfo().setDifficulty(was);
        c.ok(was != EnumDifficulty.PEACEFUL, "the test world is not Peaceful");
        for (Entity e : new ArrayList<Entity>(this.spawned)) {
            remove(c, e);
        }
    }

    /** Goats that start at different places around a pig that stands still: each backs off, lowers its head and charges. */
    private void ramMob(Check c) {
        int[][] starts = {{-3, 0}, {-6, 2}, {2, -9}, {-1, 0}, {5, 5}};
        for (int[] start : starts) {
            ramOnce(c, start[0], start[1]);
        }

        BlockPos a = c.area();
        double z = a.getZ() + 16.5;
        EntityGoat goat = goat(c, a.getX() + 13.5, 100, z);
        EntityPig pig = spawn(c, new EntityPig(c.world), a.getX() + 16.5, 100, z);
        pig.tasks.taskEntries.clear();
        for (int t = 0; t < 5; t++) {
            tick(c, goat, pig);
        }
        ModConfig.goats.ramming = false;
        goat.setRamCooldown(0);
        boolean lowered = false;
        for (int t = 0; t < 150; t++) {
            tick(c, goat, pig);
            lowered |= goat.isLoweringHead();
        }
        ModConfig.goats.ramming = true;
        c.ok(pig.getHealth() == 10.0F && !lowered, "with ramming off nothing is rammed");
        c.eq(0, goat.getRamCooldown(), "and the cooldown stays run out");
        // Switched back on, the goat - wherever it wandered meanwhile - rams.
        int t = 0;
        for (; t < 400 && pig.getHealth() == 10.0F; t++) {
            tick(c, goat, pig);
        }
        c.eq(8.0F, pig.getHealth(), "switched back on, a goat that wandered off rams (after " + t + " ticks, goat at " + goat.getPositionVector()
                + ", pig at " + pig.getPositionVector() + ", cooldown " + goat.getRamCooldown() + ")");

        // A screaming goat with nobody to ram waits its shortest cooldown and tries again.
        remove(c, pig);
        goat.setScreamingGoat(true);
        goat.setRamCooldown(0);
        int highest = 0;
        for (int i = 0; i < 20; i++) {
            tick(c, goat);
            highest = Math.max(highest, goat.getRamCooldown());
        }
        StringBuilder near = new StringBuilder();
        for (net.minecraft.entity.EntityLivingBase e : c.world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class,
                goat.getEntityBoundingBox().grow(20))) {
            if (e != goat) {
                near.append(' ').append(e.getClass().getSimpleName()).append('@').append(e.getPosition());
            }
        }
        c.ok(highest >= 90 && highest <= 100, "with nobody to ram a screaming goat waits 100 ticks: " + highest + "; near:" + near);
        remove(c, goat);
    }

    private void ramOnce(Check c, int dx, int dz) {
        BlockPos a = c.area();
        double px = a.getX() + 16.5, pz = a.getZ() + 16.5;
        EntityGoat goat = goat(c, px + dx, 100, pz + dz);
        EntityPig pig = spawn(c, new EntityPig(c.world), px, 100, pz);
        pig.tasks.taskEntries.clear();
        // Lets both settle on the ground first: a mob in the air finds no path.
        for (int t = 0; t < 5; t++) {
            tick(c, goat, pig);
        }
        String what = "goat starting " + dx + "," + dz + " from a pig: ";
        goat.setRamCooldown(0);
        boolean lowered = false;
        double chargedFrom = 0;
        int t = 0;
        for (; t < 400 && pig.getHealth() == 10.0F; t++) {
            tick(c, goat, pig);
            if (goat.isLoweringHead()) {
                lowered = true;
                chargedFrom = Math.max(chargedFrom, Math.abs(goat.posX - pig.posX) + Math.abs(goat.posZ - pig.posZ));
            }
        }
        String state = " (after " + t + " ticks, goat at " + goat.getPositionVector() + ", pig at " + pig.getPositionVector()
                + ", cooldown " + goat.getRamCooldown() + ")";
        c.eq(8.0F, pig.getHealth(), what + "the ram does 2 damage" + state);
        c.ok(lowered, what + "the goat lowered its head first");
        c.ok(chargedFrom >= 3.5D && chargedFrom <= 8.2D, what + "it charged from 4 to 7 blocks away in a straight line: " + chargedFrom);
        c.ok(!goat.isLoweringHead(), what + "and raised its head after");
        c.ok(goat.getRamCooldown() >= 590 && goat.getRamCooldown() <= 6000, what + "the cooldown starts again: " + goat.getRamCooldown());
        c.ok(pig.getRevengeTarget() == null, what + "the pig does not turn on the goat");
        double gx = goat.posX, gz = goat.posZ;
        double before = pig.getDistance(gx, pig.posY, gz);
        for (int i = 0; i < 30; i++) {
            tick(c, pig);
        }
        double thrown = pig.getDistance(gx, pig.posY, gz) - before;
        c.ok(thrown > 4.0D, what + "the pig is thrown away from the goat: " + thrown + " blocks");
        c.eq(10.0F, goat.getHealth(), what + "the goat is unhurt");
        remove(c, goat, pig);
    }

    private void ramPlayer(Check c) throws Exception {
        BlockPos a = c.area();
        double z = a.getZ() + 16.5;
        EntityGoat goat = goat(c, a.getX() + 12.5, 100, z);
        for (int t = 0; t < 5; t++) {
            tick(c, goat);
        }
        EntityPlayerMP p = Players.real(c.world, "goatram");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        // A player who just joined cannot be hurt for three seconds.
        java.lang.reflect.Field invulnerable = EntityPlayerMP.class.getDeclaredField("respawnInvulnerabilityTicks");
        invulnerable.setAccessible(true);
        invulnerable.setInt(p, 0);
        spawn(c, p, a.getX() + 16.5, 100, z);
        p.onGround = true;
        try {
            goat.setRamCooldown(0);
            boolean lowered = false;
            int t = 0;
            for (; t < 400 && p.getHealth() == 20.0F; t++) {
                tick(c, goat);
                lowered |= goat.isLoweringHead();
            }
            c.ok(lowered, "the goat prepared to ram the player (after " + t + " ticks, goat at " + goat.getPositionVector() + ", cooldown "
                    + goat.getRamCooldown() + ")");
            c.eq(18.0F, p.getHealth(), "a rammed player takes 2 damage on Easy");
            c.ok(Math.abs(p.motionX) > 1.0D, "and is thrown back: " + p.motionX + " blocks per tick");
            c.ok(p.velocityChanged, "which is sent to the player");
        } finally {
            remove(c, p, goat);
        }
    }

    /** A goat on a pillar five blocks high cannot walk anywhere, so every place it can reach is a jump. */
    private void longJump(Check c) {
        // 1.17 checks the arc at full speed and flies it at 0.95 of that, so now and then a goat clips the ledge and
        // drops back: vanilla does that too. Up to three goats; the checks are made on the first that lands clear.
        for (int attempt = 0; attempt < 3 && !pillarJump(c, attempt == 2); attempt++) {
            com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport.LOGGER.info("SELFTEST goat: jump {} clipped the ledge, again", attempt + 1);
        }

        // On open ground everything is a walk: no jump, half a cooldown.
        BlockPos b = c.area();
        EntityGoat flat = goat(c, b.getX() + 16.5, 100, b.getZ() + 16.5);
        for (int i = 0; i < 5; i++) {
            tick(c, flat);
        }
        flat.setJumpCooldown(0);
        boolean jumped = false;
        int cooldown = 0;
        for (int i = 0; i < 60; i++) {
            tick(c, flat);
            jumped |= flat.isMidJump();
            cooldown = Math.max(cooldown, flat.getJumpCooldown());
        }
        c.ok(!jumped, "a goat on flat ground does not long jump");
        c.ok(cooldown >= 300 && cooldown <= 600, "and waits half a cooldown: " + cooldown);
        remove(c, flat);
    }

    /** @return false if the jump clipped something and fell short (only then, and never on the last try, unchecked) */
    private boolean pillarJump(Check c, boolean last) {
        BlockPos a = c.area();
        BlockPos top = a.add(16, 4, 16);
        c.fill(a.add(16, 0, 16), top, Blocks.STONE.getDefaultState());
        // A ledge two blocks higher, three blocks away.
        c.fill(a.add(19, 0, 15), a.add(21, 6, 17), Blocks.STONE.getDefaultState());
        EntityGoat goat = goat(c, top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
        for (int t = 0; t < 5; t++) {
            tick(c, goat);
        }
        c.ok(goat.onGround, "the goat stands on its pillar");
        goat.setJumpCooldown(0);
        boolean flew = false, small = false;
        double highest = goat.posY;
        int t = 0;
        for (; t < 400; t++) {
            tick(c, goat);
            if (goat.isMidJump()) {
                flew = true;
                small |= goat.width < 0.7F;
                highest = Math.max(highest, goat.posY);
            } else if (flew) {
                break;
            }
        }
        c.ok(flew, "the goat long jumped");
        c.ok(small, "at 0.7 of its size");
        BlockPos target = goat.getJumpTarget();
        if (!last && flew && target != null && !(goat.onGround && goat.posY == target.getY())) {
            remove(c, goat);
            return false;
        }
        if (c.ok(target != null, "toward a block it chose") && flew) {
            double off = Math.sqrt(Math.pow(goat.posX - (target.getX() + 0.5), 2) + Math.pow(goat.posZ - (target.getZ() + 0.5), 2));
            c.ok(goat.onGround && !goat.isMidJump(), "it landed (after " + t + " ticks)");
            c.ok(off < 1.5D, "about where it aimed: " + off + " from " + target + ", at " + goat.getPositionVector());
            c.eq((double) target.getY(), goat.posY, "at the height it aimed for");
            c.ok(highest > top.getY() + 1.01D, "in an arc: up to " + highest);
        }
        c.eq(0.9F, goat.width, "back at full size");
        c.eq(10.0F, goat.getHealth(), "unhurt");
        c.ok(goat.getJumpCooldown() >= 590 && goat.getJumpCooldown() <= 1200, "and waits 30-60 seconds for the next: " + goat.getJumpCooldown());
        remove(c, goat);
        return true;
    }

    private void spawning(Check c) {
        Biome[] hills = {Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_EDGE, Biomes.EXTREME_HILLS_WITH_TREES, Biomes.MUTATED_EXTREME_HILLS,
                Biomes.MUTATED_EXTREME_HILLS_WITH_TREES};
        for (Biome biome : hills) {
            Biome.SpawnListEntry found = entry(biome);
            c.ok(found != null && found.itemWeight == ModConfig.goats.spawnWeight && found.minGroupCount == 1 && found.maxGroupCount == 3,
                    "goats spawn in " + biome.getRegistryName() + " in groups of 1-3");
        }
        c.ok(entry(Biomes.PLAINS) == null && entry(Biomes.ICE_MOUNTAINS) == null, "and not in plains or ice mountains");

        BlockPos a = c.area();
        BlockPos floor = a.add(6, -1, 6);
        // Light for the spawn check, whatever the time of day.
        c.world.setBlockState(floor.east(), Blocks.GLOWSTONE.getDefaultState(), 3);
        EntityGoat goat = new EntityGoat(c.world);
        goat.setPosition(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        c.ok(c.world.getLight(floor.up()) > 8, "the spot is lit");
        c.ok(goat.getCanSpawnHere(), "a goat may spawn on stone");
        for (net.minecraft.block.Block ok : new net.minecraft.block.Block[] {Blocks.GRAVEL, Blocks.PACKED_ICE, Blocks.SNOW}) {
            c.world.setBlockState(floor, ok.getDefaultState(), 2);
            c.ok(goat.getCanSpawnHere(), "and on " + ok.getRegistryName());
        }
        for (net.minecraft.block.Block no : new net.minecraft.block.Block[] {Blocks.GRASS, Blocks.DIRT, Blocks.SAND, Blocks.ICE}) {
            c.world.setBlockState(floor, no.getDefaultState(), 2);
            c.ok(!goat.getCanSpawnHere(), "but not on " + no.getRegistryName());
        }
        // In the dark: a stone box around a goat.
        BlockPos dark = a.add(20, 0, 20);
        c.fill(dark.add(-2, 0, -2), dark.add(2, 4, 2), Blocks.STONE.getDefaultState());
        c.world.setBlockState(dark.up(), Blocks.AIR.getDefaultState(), 3);
        c.world.setBlockState(dark.up(2), Blocks.AIR.getDefaultState(), 3);
        goat.setPosition(dark.getX() + 0.5, dark.getY() + 1, dark.getZ() + 0.5);
        c.ok(c.world.getLight(dark.up()) <= 8 && !goat.getCanSpawnHere(), "nor on stone in the dark (light " + c.world.getLight(dark.up()) + ")");
    }

    private static Biome.SpawnListEntry entry(Biome biome) {
        for (Biome.SpawnListEntry e : biome.getSpawnableList(EnumCreatureType.CREATURE)) {
            if (e.entityClass == EntityGoat.class) {
                return e;
            }
        }
        return null;
    }

    private void boat(Check c) {
        BlockPos a = c.area();
        EntityPlayerMP p = Players.real(c.world, "goatboat");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        c.ok(c.world.getMinecraftServer().getAdvancementManager().getAdvancement(Content.id(GoatContent.BOAT_ADVANCEMENT)) != null,
                "the boat advancement is loaded");
        c.ok(!Advancements.has(p, GoatContent.BOAT_ADVANCEMENT) || revoke(p), "the player starts without it");
        EntityBoat boat = spawn(c, new EntityBoat(c.world, a.getX() + 8.5, 100, a.getZ() + 8.5), a.getX() + 8.5, 100, a.getZ() + 8.5);
        EntityGoat goat = goat(c, a.getX() + 8.5, 100, a.getZ() + 8.5);
        spawn(c, p, a.getX() + 8.5, 100, a.getZ() + 8.5);
        try {
            goat.startRiding(boat, true);
            goat.ticksExisted = 20;
            goat.onLivingUpdate();
            c.ok(!Advancements.has(p, GoatContent.BOAT_ADVANCEMENT), "a goat alone in a boat grants nothing");
            p.startRiding(boat, true);
            c.ok(boat.getPassengers().contains(goat) && boat.getPassengers().contains(p), "a goat and a player share a boat");
            goat.ticksExisted = 40;
            goat.onLivingUpdate();
            c.ok(Advancements.has(p, GoatContent.BOAT_ADVANCEMENT), "which grants Whatever Floats Your Goat!");
        } finally {
            p.dismountRidingEntity();
            goat.dismountRidingEntity();
            remove(c, p, goat, boat);
        }
    }

    private static boolean revoke(EntityPlayerMP p) {
        net.minecraft.advancements.Advancement adv = p.getServer().getAdvancementManager().getAdvancement(Content.id(GoatContent.BOAT_ADVANCEMENT));
        p.getAdvancements().revokeCriterion(adv, "code");
        return !Advancements.has(p, GoatContent.BOAT_ADVANCEMENT);
    }
}
