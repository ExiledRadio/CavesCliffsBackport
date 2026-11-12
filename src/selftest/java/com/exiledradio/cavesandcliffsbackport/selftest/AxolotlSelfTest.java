package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlContent;
import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlEvents;
import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlSpawning;
import com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlVariant;
import com.exiledradio.cavesandcliffsbackport.axolotl.EntityAxolotl;
import com.exiledradio.cavesandcliffsbackport.axolotl.ItemAxolotlBucket;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.common.Content;

import net.minecraft.block.BlockDispenser;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Stream Ax: axolotls. Everything runs inside one server tick, so mobs are ticked by hand ({@link #tick}).
 */
public final class AxolotlSelfTest implements SelfTest {

    private final List<Entity> made = new ArrayList<Entity>();

    @Override
    public String name() {
        return "axolotl";
    }

    @Override
    public void run(Check c) {
        if (!AxolotlContent.enabled()) {
            c.ok(!ForgeRegistries.ENTITIES.containsKey(Content.id("axolotl")), "switched off: no axolotl");
            c.ok(!ForgeRegistries.ITEMS.containsKey(Content.id("axolotl_bucket")), "switched off: no bucket of axolotl");
            return;
        }
        try {
            registration(c);
            variants(c);
            drying(c);
            swimming(c);
            landToWater(c);
            playingDead(c);
            hunting(c);
            helping(c);
            breeding(c);
            bucket(c);
            spawning(c);
            hostiles(c);
        } finally {
            for (Entity e : this.made) {
                e.setDead();
            }
            this.made.clear();
        }
    }

    // --- helpers ---------------------------------------------------------------------------------------

    private static void tick(Entity... entities) {
        for (Entity e : entities) {
            if (!e.isDead) {
                // As the world does it: the tick counter is the world's to advance, not onUpdate's.
                ++e.ticksExisted;
                e.onUpdate();
            }
        }
    }

    private <E extends Entity> E spawn(Check c, E entity, double x, double y, double z) {
        entity.setPosition(x, y, z);
        c.world.spawnEntity(entity);
        this.made.add(entity);
        return entity;
    }

    private EntityAxolotl axolotl(Check c, double x, double y, double z) {
        return spawn(c, new EntityAxolotl(c.world), x, y, z);
    }

    /**
     * A closed stone box full of water: the inside runs from {@code o} for the given size. Closed, so it is
     * dark and dry of rain, and nothing flows once the test is over.
     *
     * @param floor the block under the water
     */
    private static void tank(Check c, BlockPos o, int sx, int sy, int sz, IBlockState floor) {
        c.fill(o.add(-1, -1, -1), o.add(sx, sy, sz), Blocks.STONE.getDefaultState());
        c.fill(o.add(0, -1, 0), o.add(sx - 1, -1, sz - 1), floor);
        c.fill(o, o.add(sx - 1, sy - 1, sz - 1), Blocks.WATER.getDefaultState());
    }

    private static EntityPlayerMP player(Check c, String name, BlockPos at) {
        EntityPlayerMP p = Players.real(c.world, name);
        p.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        return p;
    }

    // --- tests -----------------------------------------------------------------------------------------

    private void registration(Check c) {
        c.ok(ForgeRegistries.ENTITIES.containsKey(Content.id("axolotl")), "axolotl registered");
        c.ok(EntityList.ENTITY_EGGS.containsKey(Content.id("axolotl")), "axolotl spawn egg");
        c.ok(AxolotlContent.bucket instanceof ItemAxolotlBucket, "bucket of axolotl registered");
        c.eq(1, new ItemStack(AxolotlContent.bucket).getMaxStackSize(), "bucket stack size");
        c.ok(BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.getObject(AxolotlContent.bucket).getClass().getName().contains("ItemAxolotlBucket"),
                "dispensers empty the bucket");
        EntityAxolotl a = new EntityAxolotl(c.world);
        c.eq(14.0F, a.getMaxHealth(), "health");
        c.eq(2.0D, a.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue(), "attack damage");
        c.eq(0.75F, a.width, "width");
        c.eq(0.42F, a.height, "height");
        c.eq(EntityAxolotl.MAX_AIR, a.getAir(), "starts with five minutes of air");
        c.ok(a.canBreatheUnderwater(), "breathes under water");
        c.ok(a.canBeLeashedTo(null), "can be leashed");
        for (String s : new String[] {"attack", "death", "hurt", "idle_air", "idle_water", "splash", "swim"}) {
            c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("entity.axolotl." + s)), "sound entity.axolotl." + s);
        }
        c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("item.bucket.fill_axolotl")), "sound fill");
        c.ok(ForgeRegistries.SOUND_EVENTS.containsKey(Content.id("item.bucket.empty_axolotl")), "sound empty");

        // What they breed with: no bucket of tropical fish exists in this test game, so the config item.
        c.eq("minecraft:fish:2", ModConfig.axolotls.breedingItem, "default breeding item");
        c.ok(AxolotlContent.isBreedingItem(new ItemStack(Items.FISH, 1, 2)), "tropical fish is food");
        c.ok(!AxolotlContent.isBreedingItem(new ItemStack(Items.FISH, 1, 0)), "raw cod is not");
        c.ok(!AxolotlContent.isBreedingItem(new ItemStack(Items.WATER_BUCKET)), "a water bucket is not");
        c.ok(!AxolotlContent.isBreedingItem(ItemStack.EMPTY), "an empty hand is not");

        // Saving.
        a.setVariant(AxolotlVariant.CYAN);
        a.setFromBucket(true);
        a.setHuntingCooldown(1234);
        NBTTagCompound tag = new NBTTagCompound();
        a.writeEntityToNBT(tag);
        c.eq(3, tag.getInteger("Variant"), "saved Variant");
        EntityAxolotl b = new EntityAxolotl(c.world);
        b.readEntityFromNBT(tag);
        c.eq(AxolotlVariant.CYAN, b.getVariant(), "variant read back");
        c.ok(b.isFromBucket(), "FromBucket read back");
        c.eq(1234, b.getHuntingCooldown(), "hunting cooldown read back");
        tag.setInteger("Variant", 99);
        b.readEntityFromNBT(tag);
        c.eq(AxolotlVariant.LUCY, b.getVariant(), "a bad variant id reads as lucy");
    }

    private void variants(Check c) {
        Set<AxolotlVariant> seen = EnumSet.noneOf(AxolotlVariant.class);
        for (int i = 0; i < 400; i++) {
            EntityAxolotl a = new EntityAxolotl(c.world);
            a.onInitialSpawn(c.world.getDifficultyForLocation(BlockPos.ORIGIN), null);
            seen.add(a.getVariant());
        }
        c.eq(EnumSet.of(AxolotlVariant.LUCY, AxolotlVariant.WILD, AxolotlVariant.GOLD, AxolotlVariant.CYAN), seen,
                "400 spawns: the four common colors and never blue");

        // A group: two colors at most, the third and fourth are babies.
        boolean groupsOk = true;
        for (int g = 0; g < 50 && groupsOk; g++) {
            IEntityLivingData data = null;
            Set<AxolotlVariant> colors = EnumSet.noneOf(AxolotlVariant.class);
            for (int i = 0; i < 4; i++) {
                EntityAxolotl a = new EntityAxolotl(c.world);
                data = a.onInitialSpawn(c.world.getDifficultyForLocation(BlockPos.ORIGIN), data);
                colors.add(a.getVariant());
                groupsOk &= a.isChild() == (i >= 2);
            }
            groupsOk &= colors.size() <= 2;
        }
        c.ok(groupsOk, "50 groups of four: two adults then two babies, two colors at most");

        // Breeding: blue one time in 1200, else a parent's color; the baby never despawns.
        EntityAxolotl gold = new EntityAxolotl(c.world);
        gold.setVariant(AxolotlVariant.GOLD);
        EntityAxolotl wild = new EntityAxolotl(c.world);
        wild.setVariant(AxolotlVariant.WILD);
        int blue = 0, golds = 0, wilds = 0, other = 0;
        boolean persistent = true;
        for (int i = 0; i < 24000; i++) {
            EntityAxolotl baby = (EntityAxolotl) gold.createChild(wild);
            persistent &= baby.isNoDespawnRequired();
            switch (baby.getVariant()) {
                case BLUE:
                    blue++;
                    break;
                case GOLD:
                    golds++;
                    break;
                case WILD:
                    wilds++;
                    break;
                default:
                    other++;
            }
        }
        c.ok(blue >= 4 && blue <= 50, "24,000 babies: about 20 blue, got " + blue);
        c.ok(other == 0 && golds > 10000 && wilds > 10000, "the rest take after a parent: " + golds + " gold, " + wilds + " wild, " + other + " other");
        c.ok(persistent, "bred babies never despawn");
    }

    private void drying(Check c) {
        BlockPos o = c.area();
        // A roof, so rain (which keeps them wet) cannot reach.
        c.fill(o.add(0, 4, 0), o.add(8, 4, 8), Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 4.5, o.getY(), o.getZ() + 4.5);
        a.setNoAI(false);
        for (int i = 0; i < 100; i++) {
            tick(a);
        }
        c.eq(EntityAxolotl.MAX_AIR - 100, a.getAir(), "on land it loses one air a tick");
        a.rehydrate();
        c.eq(EntityAxolotl.MAX_AIR, a.getAir(), "a splash of water tops it up, never past the maximum");
        a.setAir(3000);
        EntityPotion potion = new EntityPotion(c.world, a.posX, a.posY + 1, a.posZ,
                PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.WATER));
        AxolotlEvents.onPotionImpact(new ProjectileImpactEvent.Throwable(potion, new RayTraceResult(a)));
        c.eq(4800, a.getAir(), "a splash water bottle gives back 1800");
        EntityPotion harming = new EntityPotion(c.world, a.posX, a.posY + 1, a.posZ,
                PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.HARMING));
        AxolotlEvents.onPotionImpact(new ProjectileImpactEvent.Throwable(harming, new RayTraceResult(a)));
        c.eq(4800, a.getAir(), "another splash potion does not");

        a.setAir(5);
        float before = a.getHealth();
        for (int i = 0; i < 26; i++) {
            tick(a);
        }
        c.eq(before - 2.0F, a.getHealth(), "out of air it loses a heart");
        c.eq(EntityAxolotl.DRY_OUT, a.getLastDamageSource(), "to drying out");
        for (int i = 0; i < 200 && a.isEntityAlive(); i++) {
            a.hurtResistantTime = 0;
            tick(a);
        }
        c.ok(!a.isEntityAlive(), "and dies of it in the end");

        // In water the air comes back.
        tank(c, o.add(12, 0, 12), 3, 2, 3, Blocks.STONE.getDefaultState());
        EntityAxolotl wet = axolotl(c, o.getX() + 13.5, o.getY() + 0.5, o.getZ() + 13.5);
        wet.setAir(100);
        tick(wet);
        c.ok(wet.isInWater(), "in the tank it is in water");
        c.eq(EntityAxolotl.MAX_AIR, wet.getAir(), "in water its air is full again");
    }

    private void swimming(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 12, 5, 5, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 1.2, o.getZ() + 2.5);
        tick(a);
        double startY = a.posY;
        for (int i = 0; i < 60; i++) {
            a.getNavigator().clearPath();
            // Its own idle goal would set off at once; hold it still by clearing the path each tick.
            a.onEntityUpdate();
        }
        c.ok(Math.abs(a.posY - startY) < 0.01, "it neither sinks nor floats while idle");
        c.ok(a.getNavigator().tryMoveToXYZ(o.getX() + 10.5, o.getY() + 3.5, o.getZ() + 2.5, 0.5D), "it finds a path through water");
        double best = Double.MAX_VALUE;
        boolean stayedIn = true;
        for (int i = 0; i < 400; i++) {
            tick(a);
            stayedIn &= a.isInWater();
            best = Math.min(best, a.getDistance(o.getX() + 10.5, o.getY() + 3.5, o.getZ() + 2.5));
            if (best < 1.2) {
                break;
            }
        }
        c.ok(best < 1.2, "it swims 9 blocks along and 2 up to its goal (closest " + best + ")");
        c.ok(stayedIn, "without leaving the water");
        // Left alone it keeps moving about its tank.
        double moved = 0;
        for (int i = 0; i < 600; i++) {
            double x = a.posX, y = a.posY, z = a.posZ;
            tick(a);
            moved += a.getDistance(x, y, z);
        }
        c.ok(moved > 8, "idling for 30 seconds it swims about (" + moved + " blocks)");
        c.ok(a.isInWater(), "and is still in the water");
    }

    private void landToWater(Check c) {
        BlockPos o = c.area();
        c.fill(o.add(0, 4, 0), o.add(12, 4, 8), Blocks.STONE.getDefaultState());
        // A pool let into the floor, 4 blocks from where the axolotl starts.
        c.fill(o.add(6, -3, 2), o.add(10, -1, 6), Blocks.STONE.getDefaultState());
        c.fill(o.add(7, -2, 3), o.add(9, -1, 5), Blocks.WATER.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 2.5, o.getY(), o.getZ() + 4.5);
        int ticks = 0;
        double startX = a.posX;
        for (; ticks < 2400 && !a.isInWater(); ticks++) {
            tick(a);
        }
        c.ok(a.isInWater(), "on land it crawls to water 4 blocks away (" + ticks + " ticks, from x " + startX + " to " + a.posX + ")");
        c.ok(ticks > 100, "slowly: it took " + ticks + " ticks");
        c.ok(a.isEntityAlive() && a.getHealth() == a.getMaxHealth(), "and arrives unharmed");
    }

    private void playingDead(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 7, 4, 7, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 3.5, o.getY() + 1.5, o.getZ() + 3.5);
        EntityZombie zombie = new EntityZombie(c.world);
        zombie.setPosition(o.getX() + 5.5, o.getY() + 1, o.getZ() + 3.5);
        tick(a);
        c.ok(a.isInWater(), "in water");

        DamageSource bite = DamageSource.causeMobDamage(zombie);
        int played = 0;
        for (int i = 0; i < 300; i++) {
            a.hurtResistantTime = 0;
            a.setHealth(14.0F);
            a.setPlayDeadTicks(0);
            a.setPlayingDead(false);
            a.attackEntityFrom(bite, 2.0F);
            if (a.getPlayDeadTicks() == 200) {
                played++;
            }
        }
        c.ok(played >= 30 && played <= 110, "300 hits of 2 in water: plays dead about 2 times in 9, got " + played);
        played = 0;
        for (int i = 0; i < 300; i++) {
            a.hurtResistantTime = 0;
            a.setHealth(14.0F);
            a.setPlayDeadTicks(0);
            a.attackEntityFrom(DamageSource.MAGIC, 2.0F);
            if (a.getPlayDeadTicks() > 0) {
                played++;
            }
        }
        c.eq(0, played, "never for damage nobody dealt");
        played = 0;
        for (int i = 0; i < 300; i++) {
            a.hurtResistantTime = 0;
            a.setHealth(3.0F);
            a.setPlayDeadTicks(0);
            a.attackEntityFrom(bite, 5.0F);
            if (a.getPlayDeadTicks() > 0) {
                played++;
            }
        }
        c.eq(0, played, "never for a hit that would kill it");

        a.hurtResistantTime = 0;
        a.setHealth(8.0F);
        // The hits above knocked it about; start from rest.
        a.motionX = a.motionY = a.motionZ = 0.0D;
        a.setPlayDeadTicks(200);
        a.getNavigator().tryMoveToXYZ(o.getX() + 1.5, o.getY() + 1.5, o.getZ() + 1.5, 0.5D);
        tick(a);
        c.ok(a.isPlayingDead(), "the counter makes it play dead");
        // Goals are only picked every third tick.
        tick(a);
        tick(a);
        tick(a);
        c.ok(a.isPotionActive(MobEffects.REGENERATION), "playing dead gives it Regeneration");
        c.ok(a.getNavigator().noPath(), "and stops it");
        double x = a.posX, z = a.posZ;
        zombie.setAttackTarget(a);
        c.ok(zombie.getAttackTarget() == null, "a mob cannot target one that plays dead");
        int ticks = 4;
        float health = a.getHealth();
        for (; ticks < 400 && a.isPlayingDead(); ticks++) {
            tick(a);
        }
        c.ok(ticks >= 198 && ticks <= 203, "for ten seconds (" + ticks + " ticks)");
        c.ok(Math.abs(a.posX - x) < 0.3 && Math.abs(a.posZ - z) < 0.3, "without swimming anywhere");
        c.ok(a.getHealth() > health, "healing meanwhile (" + health + " to " + a.getHealth() + ")");
        zombie.setAttackTarget(a);
        c.ok(zombie.getAttackTarget() == a, "once it is up again it can be targeted");

        // On land nothing makes it play dead.
        EntityAxolotl dry = axolotl(c, o.getX() + 3.5, o.getY() + 5, o.getZ() + 3.5);
        tick(dry);
        played = 0;
        for (int i = 0; i < 300; i++) {
            dry.hurtResistantTime = 0;
            dry.setHealth(14.0F);
            dry.attackEntityFrom(bite, 2.0F);
            if (dry.getPlayDeadTicks() > 0) {
                played++;
            }
        }
        c.eq(0, played, "never on land");
    }

    private void hunting(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 9, 4, 5, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 1.5, o.getZ() + 2.5);
        EntitySquid squid = spawn(c, new EntitySquid(c.world), o.getX() + 6.5, o.getY() + 1.5, o.getZ() + 2.5);
        EntityPig pig = spawn(c, new EntityPig(c.world), o.getX() + 3.5, o.getY() + 1.5, o.getZ() + 2.5);
        pig.setNoAI(true);
        tick(a, squid, pig);
        c.ok(squid.isInWater() && pig.isInWater(), "a squid and a pig in the water");
        c.eq(squid, a.findTarget(), "it picks the squid, not the nearer pig");
        int ticks = 0;
        boolean fought = false;
        for (; ticks < 3000 && squid.isEntityAlive(); ticks++) {
            tick(a, squid);
            fought |= a.isFighting();
        }
        c.ok(!squid.isEntityAlive(), "it hunts the squid down (" + ticks + " ticks)");
        c.ok(fought, "through its fight goal");
        c.ok(pig.getHealth() == pig.getMaxHealth(), "the pig is unharmed");
        tick(a);
        tick(a);
        c.ok(a.getHuntingCooldown() > 2300, "then it rests from hunting for two minutes (" + a.getHuntingCooldown() + ")");
        c.ok(!a.isFighting() && a.getAttackTarget() == null, "with no target left");

        EntitySquid second = spawn(c, new EntitySquid(c.world), o.getX() + 4.5, o.getY() + 1.5, o.getZ() + 2.5);
        tick(second);
        c.ok(a.findTarget() == null, "and ignores a second squid");
        // ... but never the mobs it always fights.
        EntityGuardian guardian = spawn(c, new EntityGuardian(c.world), o.getX() + 6.5, o.getY() + 1.5, o.getZ() + 2.5);
        guardian.setNoAI(true);
        tick(guardian);
        c.eq(guardian, a.findTarget(), "a guardian it attacks even while resting");
        guardian.setDead();
        a.setHuntingCooldown(0);
        c.eq(second, a.findTarget(), "the rest over, it hunts again");
        a.setInLove(null);
        for (int i = 0; i < 40; i++) {
            tick(a);
        }
        c.ok(a.getAttackTarget() == null && !a.isFighting(), "ready to breed, it does not hunt");
        second.setDead();
    }

    private void helping(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 7, 4, 5, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 1.5, o.getZ() + 2.5);
        EntityPlayerMP p = player(c, "axolotlhelp", o.add(3, 6, 2));
        p.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 6000, 2));

        for (int round = 1; round <= 3; round++) {
            EntitySquid squid = spawn(c, new EntitySquid(c.world), o.getX() + 4.5, o.getY() + 1.5, o.getZ() + 2.5);
            a.setHuntingCooldown(0);
            tick(a, squid);
            for (int i = 0; i < 100 && !a.isFighting(); i++) {
                tick(a, squid);
            }
            c.ok(a.isFighting(), "round " + round + ": the axolotl is fighting a squid");
            boolean byPlayer = round != 2;
            squid.hurtResistantTime = 0;
            squid.attackEntityFrom(byPlayer ? DamageSource.causePlayerDamage(p) : DamageSource.MAGIC, 1000.0F);
            c.ok(!squid.isEntityAlive(), "round " + round + ": the squid is killed");
            tick(a);
            tick(a);
            PotionEffect regeneration = p.getActivePotionEffect(MobEffects.REGENERATION);
            if (round == 1) {
                c.ok(regeneration != null && regeneration.getDuration() == 100 && regeneration.getAmplifier() == 0,
                        "the player who killed it gets Regeneration I for 5 seconds: " + regeneration);
                c.ok(!p.isPotionActive(MobEffects.MINING_FATIGUE), "and loses Mining Fatigue");
                c.ok(Advancements.has(p, "husbandry/kill_axolotl_target"), "The Healing Power of Friendship! granted");
            } else if (round == 2) {
                c.ok(regeneration != null && regeneration.getDuration() == 100, "a kill that was not the player's adds nothing");
            } else {
                c.ok(regeneration != null && regeneration.getDuration() == 200, "a second kill adds 5 seconds more: " + regeneration);
            }
            c.ok(a.getHuntingCooldown() > 2300, "round " + round + ": the fight over, it rests");
        }
        p.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 2390, 0));
        a.applySupportingEffects(p);
        c.eq(2400, p.getActivePotionEffect(MobEffects.REGENERATION).getDuration(), "Regeneration stops at two minutes");
        a.applySupportingEffects(p);
        c.eq(2400, p.getActivePotionEffect(MobEffects.REGENERATION).getDuration(), "and stays there");
    }

    private void breeding(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 5, 3, 5, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 1.2, o.getZ() + 2.5);
        EntityAxolotl b = axolotl(c, o.getX() + 3.5, o.getY() + 1.2, o.getZ() + 2.5);
        a.setVariant(AxolotlVariant.GOLD);
        b.setVariant(AxolotlVariant.CYAN);
        tick(a, b);

        // Feeding, through the animal's own right-click.
        EntityPlayerMP p = player(c, "axolotlfeed", o.add(2, 4, 2));
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FISH, 2, 2));
        c.ok(a.processInteract(p, EnumHand.MAIN_HAND), "feeding it a tropical fish");
        c.ok(a.isInLove(), "puts it in love");
        c.eq(1, p.getHeldItemMainhand().getCount(), "and uses up one fish");
        c.ok(b.processInteract(p, EnumHand.MAIN_HAND), "feeding the other");
        c.ok(p.getHeldItemMainhand().isEmpty(), "uses up the last");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.FISH, 1, 0));
        c.ok(!a.processInteract(p, EnumHand.MAIN_HAND), "raw cod does nothing");

        AxisAlignedBB box = new AxisAlignedBB(o, o.add(5, 3, 5));
        EntityAxolotl baby = null;
        int ticks = 0;
        for (; ticks < 1200 && baby == null; ticks++) {
            tick(a, b);
            for (EntityAxolotl e : c.world.getEntitiesWithinAABB(EntityAxolotl.class, box)) {
                if (e.isChild()) {
                    baby = e;
                }
            }
        }
        if (c.ok(baby != null, "two fed axolotls have a baby (" + ticks + " ticks)")) {
            this.made.add(baby);
            c.ok(baby.getVariant() == AxolotlVariant.GOLD || baby.getVariant() == AxolotlVariant.CYAN || baby.getVariant() == AxolotlVariant.BLUE,
                    "in a parent's color: " + baby.getVariant());
            c.ok(baby.isNoDespawnRequired(), "that never despawns");
            c.ok(baby.width < 0.5F, "at half size");
            c.ok(!a.isInLove() && a.getGrowingAge() > 5000 && a.getGrowingAge() <= 6000, "and the parents wait five minutes (" + a.getGrowingAge() + ")");
            // The baby keeps close to an adult.
            baby.setPosition(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
            a.setNoAI(true);
            b.setDead();
            a.setPosition(o.getX() + 4.5, o.getY() + 2.2, o.getZ() + 4.5);
            double start = baby.getDistance(a);
            double best = start;
            for (int i = 0; i < 400; i++) {
                tick(baby);
                best = Math.min(best, baby.getDistance(a));
            }
            c.ok(best < 3.2, "the baby swims after an adult (from " + start + " to " + best + ")");
        }
    }

    private void bucket(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 3, 2, 3, Blocks.STONE.getDefaultState());
        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 0.5, o.getZ() + 1.5);
        a.setVariant(AxolotlVariant.BLUE);
        a.setGrowingAge(-12000);
        a.setHealth(9.0F);
        a.setCustomNameTag("Bluey");
        a.setHuntingCooldown(777);
        EntityPlayerMP p = player(c, "axolotlbucket", o.add(1, 3, 1));

        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        a.processInteract(p, EnumHand.MAIN_HAND);
        c.ok(a.isEntityAlive() && p.getHeldItemMainhand().getItem() == Items.BUCKET, "an empty bucket does not pick it up");

        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        c.ok(a.processInteract(p, EnumHand.MAIN_HAND), "a water bucket on an axolotl");
        ItemStack held = p.getHeldItemMainhand();
        c.eq(AxolotlContent.bucket, held.getItem(), "gives a bucket of axolotl");
        c.ok(a.isDead, "and takes the axolotl");
        c.ok(Advancements.has(p, "husbandry/axolotl_in_a_bucket"), "The Cutest Predator granted");
        NBTTagCompound tag = held.getTagCompound();
        if (c.ok(tag != null, "the bucket has data")) {
            c.eq(4, tag.getInteger("Variant"), "bucket Variant");
            c.eq(-12000, tag.getInteger("Age"), "bucket Age");
            c.eq(9.0F, tag.getFloat("Health"), "bucket Health");
            c.eq(777L, tag.getLong("HuntingCooldown"), "bucket HuntingCooldown");
            c.eq("Bluey", held.getDisplayName(), "bucket name");
        }

        // Emptied somewhere dry: water, and the same axolotl.
        BlockPos dry = o.add(8, 0, 8);
        c.fill(dry.add(-1, 0, -1), dry.add(1, 0, 1), Blocks.STONE.getDefaultState());
        c.world.setBlockState(dry, Blocks.AIR.getDefaultState(), 2 | 16);
        c.ok(((ItemAxolotlBucket) AxolotlContent.bucket).empty(p, c.world, dry, held), "emptying the bucket");
        c.eq(Material.WATER, c.world.getBlockState(dry).getMaterial(), "places water");
        List<EntityAxolotl> out = c.world.getEntitiesWithinAABB(EntityAxolotl.class, new AxisAlignedBB(dry).grow(1));
        if (c.eq(1, out.size(), "and one axolotl")) {
            EntityAxolotl b = out.get(0);
            this.made.add(b);
            c.eq(AxolotlVariant.BLUE, b.getVariant(), "its color kept");
            c.eq(-12000, b.getGrowingAge(), "its age kept");
            c.ok(b.isChild(), "still a baby");
            c.eq(9.0F, b.getHealth(), "its health kept");
            c.eq("Bluey", b.getCustomNameTag(), "its name kept");
            c.eq(777, b.getHuntingCooldown(), "its hunting rest kept");
            c.ok(b.isFromBucket(), "marked as from a bucket");
        }
        c.ok(!((ItemAxolotlBucket) AxolotlContent.bucket).empty(p, c.world, dry.down(), held), "it cannot be emptied into stone");

        // A plain bucket of axolotl (creative menu, commands) holds a lucy adult.
        BlockPos second = o.add(12, 0, 8);
        c.fill(second.add(-1, 0, -1), second.add(1, 0, 1), Blocks.STONE.getDefaultState());
        c.world.setBlockState(second, Blocks.AIR.getDefaultState(), 2 | 16);
        EntityAxolotl plain = ((ItemAxolotlBucket) AxolotlContent.bucket).release(c.world, second, new ItemStack(AxolotlContent.bucket), null);
        if (c.ok(plain != null, "a plain bucket lets an axolotl out")) {
            this.made.add(plain);
            c.ok(plain.getVariant() == AxolotlVariant.LUCY && !plain.isChild() && plain.getHealth() == 14.0F, "a healthy adult lucy");
        }

        // Creative: the water bucket stays, the filled one is added.
        EntityAxolotl again = axolotl(c, o.getX() + 1.5, o.getY() + 0.5, o.getZ() + 1.5);
        EntityPlayerMP creative = player(c, "axolotlcreative", o.add(1, 3, 1));
        creative.interactionManager.setGameType(GameType.CREATIVE);
        creative.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        again.processInteract(creative, EnumHand.MAIN_HAND);
        c.ok(creative.getHeldItemMainhand().getItem() == Items.WATER_BUCKET && creative.inventory.hasItemStack(new ItemStack(AxolotlContent.bucket)),
                "in creative the water bucket stays and the bucket of axolotl is added");
        c.world.setBlockState(dry, Blocks.AIR.getDefaultState(), 2 | 16);
    }

    private void spawning(Check c) {
        // The default habitat is the lush caves of CaveRegions; the pool checks below use the stand-in, which takes
        // any dark underground water, so they do not depend on where the test area falls.
        c.ok(AxolotlSpawning.habitat instanceof com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat.LushCaves,
                "axolotls spawn in lush caves by default");
        lushHabitat(c);
        com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat installed = AxolotlSpawning.habitat;
        AxolotlSpawning.habitat = new com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat.DarkUndergroundWater();
        try {
            spawningPools(c);
        } finally {
            AxolotlSpawning.habitat = installed;
        }
    }

    private void lushHabitat(Check c) {
        com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat lushCaves =
                new com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat.LushCaves();
        long seed = c.world.getSeed();
        BlockPos lush = null, other = null;
        for (int x = 0; x < 20000 && (lush == null || other == null); x += 64) {
            com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region r =
                    com.exiledradio.cavesandcliffsbackport.common.CaveRegions.at(seed, x, 12, 0);
            if (r == com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region.LUSH && lush == null) {
                lush = new BlockPos(x, 12, 0);
            } else if (r == com.exiledradio.cavesandcliffsbackport.common.CaveRegions.Region.NONE && other == null) {
                other = new BlockPos(x, 12, 0);
            }
        }
        if (c.ok(lush != null && other != null, "a lush region and plain underground along the x axis")) {
            // Spawning only asks in loaded chunks; an unloaded one has no height map, so nothing in it is underground.
            c.world.getChunk(lush);
            c.world.getChunk(other);
            c.ok(lushCaves.canSpawnAt(c.world, lush), "the lush habitat accepts a lush region underground");
            c.ok(!lushCaves.canSpawnAt(c.world, other), "...and refuses plain underground");
            c.ok(!lushCaves.canSpawnAt(c.world, new BlockPos(lush.getX(), 255, 0)), "...and the sky over a lush region");
        }
    }

    private void spawningPools(Check c) {
        WorldServer w = c.world;
        BlockPos o = c.area().add(4, 0, 4);
        // An underground pool: 20x20, two deep, on clay, closed in stone.
        tank(c, o, 20, 2, 20, Blocks.CLAY.getDefaultState());
        BlockPos center = o.add(10, 0, 10);
        c.ok(AxolotlSpawning.habitat.canSpawnAt(w, center), "the stand-in habitat accepts dark underground water (light "
                + w.getLightFromNeighbors(center) + ")");
        int total = 0, most = 0;
        boolean placed = true;
        Set<AxolotlVariant> colors = EnumSet.noneOf(AxolotlVariant.class);
        for (int i = 0; i < 12; i++) {
            int n = AxolotlSpawning.spawnGroup(w, center);
            total += n;
            most = Math.max(most, n);
            for (EntityAxolotl e : w.getEntitiesWithinAABB(EntityAxolotl.class, new AxisAlignedBB(o, o.add(20, 2, 20)))) {
                if (e.isDead) {
                    continue;
                }
                BlockPos at = new BlockPos(e.posX, e.getEntityBoundingBox().minY, e.posZ);
                placed &= w.getBlockState(at).getMaterial() == Material.WATER && w.getBlockState(at.down()).getBlock() == Blocks.CLAY;
                colors.add(e.getVariant());
                e.setDead();
            }
        }
        c.ok(total >= 12, "12 tries in a clay pool spawn axolotls: " + total);
        c.ok(most <= 4, "never more than four at once: " + most);
        c.ok(placed, "each in water right above clay");
        c.ok(!colors.contains(AxolotlVariant.BLUE) && !colors.isEmpty(), "none of them blue: " + colors);

        // The same pool on stone: nothing.
        BlockPos stone = c.area().add(4, 0, 4);
        tank(c, stone, 20, 2, 20, Blocks.STONE.getDefaultState());
        int none = 0;
        for (int i = 0; i < 12; i++) {
            none += AxolotlSpawning.spawnGroup(w, stone.add(10, 0, 10));
        }
        c.eq(0, none, "no clay, no axolotls");

        // A clay pool open to the sky: the stand-in habitat refuses it.
        BlockPos open = c.area().add(4, 0, 4);
        tank(c, open, 20, 2, 20, Blocks.CLAY.getDefaultState());
        c.fill(open.add(-1, 2, -1), open.add(20, 2, 20), Blocks.AIR.getDefaultState());
        c.ok(!AxolotlSpawning.habitat.canSpawnAt(w, open.add(10, 0, 10)), "the stand-in habitat refuses a pool under the sky");
        none = 0;
        for (int i = 0; i < 12; i++) {
            none += AxolotlSpawning.spawnGroup(w, open.add(10, 0, 10));
        }
        c.eq(0, none, "so nothing spawns there");
        // ... and the habitat is the only thing that refuses it.
        com.exiledradio.cavesandcliffsbackport.axolotl.AxolotlHabitat standIn = AxolotlSpawning.habitat;
        AxolotlSpawning.habitat = (world, pos) -> true;
        try {
            int some = 0;
            for (int i = 0; i < 12; i++) {
                some += AxolotlSpawning.spawnGroup(w, open.add(10, 0, 10));
            }
            c.ok(some > 0, "with another habitat plugged in, the same pool spawns them: " + some);
        } finally {
            AxolotlSpawning.habitat = standIn;
        }
        for (EntityAxolotl e : w.getEntitiesWithinAABB(EntityAxolotl.class, new AxisAlignedBB(open, open.add(20, 2, 20)))) {
            e.setDead();
        }
        // Drain the open pool: left alone it would flow over the test area.
        c.fill(open.add(-1, 0, -1), open.add(20, 1, 20), Blocks.AIR.getDefaultState());
    }

    private void hostiles(Check c) {
        BlockPos o = c.area().add(2, 0, 2);
        tank(c, o, 9, 4, 5, Blocks.STONE.getDefaultState());
        EntityGuardian fresh = new EntityGuardian(c.world);
        int before = fresh.targetTasks.taskEntries.size();
        EntityGuardian guardian = spawn(c, new EntityGuardian(c.world), o.getX() + 7.5, o.getY() + 1.5, o.getZ() + 2.5);
        guardian.getRNG().setSeed(42L);  // its target picks are dice; seeded, the test repeats
        c.eq(before + 1, guardian.targetTasks.taskEntries.size(), "a guardian joining the world learns to target axolotls");
        EntitySquid squid = spawn(c, new EntitySquid(c.world), o.getX() + 7.5, o.getY() + 2.5, o.getZ() + 2.5);
        c.eq(0, countTargetTasks(squid), "a squid does not");
        squid.setDead();

        EntityAxolotl a = axolotl(c, o.getX() + 1.5, o.getY() + 1.5, o.getZ() + 2.5);
        a.setNoAI(true);
        tick(a, guardian);
        EntityLiving g = guardian;
        for (int i = 0; i < 200 && g.getAttackTarget() == null; i++) {
            tick(guardian);
        }
        c.eq(a, g.getAttackTarget(), "the guardian targets an axolotl 6 blocks away");
        a.setNoAI(false);
        a.setPlayDeadTicks(200);
        tick(a);
        tick(a);
        c.ok(a.isPlayingDead(), "the axolotl plays dead");
        g.setAttackTarget(null);
        for (int i = 0; i < 100; i++) {
            tick(guardian);
        }
        c.ok(g.getAttackTarget() == null, "and the guardian leaves it alone");
    }

    private static int countTargetTasks(EntityLiving mob) {
        int n = 0;
        for (EntityAITasks.EntityAITaskEntry e : mob.targetTasks.taskEntries) {
            n++;
        }
        return n;
    }
}
