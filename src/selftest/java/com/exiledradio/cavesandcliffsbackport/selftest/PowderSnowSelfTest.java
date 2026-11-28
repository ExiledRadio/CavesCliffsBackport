package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.Advancements;
import com.exiledradio.cavesandcliffsbackport.powdersnow.BlockPowderSnow;
import com.exiledradio.cavesandcliffsbackport.powdersnow.BlockPowderSnowCauldron;
import com.exiledradio.cavesandcliffsbackport.powdersnow.Freezing;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PacketFreeze;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowContent;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowEvents;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowGenerator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityStray;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Stream Ps: the block's per-entity collision, sinking, fall damage, boots, freezing, strays, the bucket,
 * the cauldron, dispensers, spawning, patches, the advancement.
 *
 * <p>Entities are ticked by hand ({@code onUpdate}); the world's clock stands still meanwhile, so "powder
 * snow touched it this tick" stays true until a test moves the mark back itself.
 */
public final class PowderSnowSelfTest implements SelfTest {

    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private static final IBlockState STONE = Blocks.STONE.getDefaultState();

    private final List<Entity> spawned = new ArrayList<Entity>();

    @Override
    public String name() {
        return "powdersnow";
    }

    @Override
    public void run(Check c) {
        try {
            registration(c);
            collision(c);
            sinking(c);
            falling(c);
            freezing(c);
            players(c);
            strays(c);
            bucket(c);
            cauldron(c);
            dispenser(c);
            fireAndSpawning(c);
            patches(c);
            packet(c);
        } finally {
            for (Entity e : this.spawned) {
                e.setDead();
            }
        }
    }

    private IBlockState snow() {
        return PowderSnowContent.powderSnow.getDefaultState();
    }

    private <E extends Entity> E spawn(Check c, E entity, double x, double y, double z) {
        entity.setPosition(x, y, z);
        c.world.spawnEntity(entity);
        this.spawned.add(entity);
        return entity;
    }

    private static void tick(Entity e, int times) {
        for (int i = 0; i < times && !e.isDead; i++) {
            // The world counts an entity's age, not the entity; freeze damage goes by it.
            e.ticksExisted++;
            e.onUpdate();
        }
    }

    /** A tick of a player standing in powder snow. */
    private static void snowTick(EntityPlayerMP p, int times) {
        for (int i = 0; i < times; i++) {
            p.ticksExisted++;
            Freezing.markInSnow(p);
            p.onUpdateEntity();
        }
    }

    // ---- registration ------------------------------------------------------------------------

    private void registration(Check c) {
        BlockPowderSnow snow = PowderSnowContent.powderSnow;
        c.ok(snow != null && PowderSnowContent.cauldron != null && PowderSnowContent.bucket != null, "registered");
        c.eq(Items.AIR, Item.getItemFromBlock(snow), "powder snow has no item of its own");
        c.eq(Items.AIR, Item.getItemFromBlock(PowderSnowContent.cauldron), "the cauldron has no item of its own");
        c.eq(1, new ItemStack(PowderSnowContent.bucket).getMaxStackSize(), "the bucket stacks to 1");
        c.eq("item.cavesandcliffsbackport.powder_snow_bucket", PowderSnowContent.bucket.getTranslationKey(), "bucket name key");
        IBlockState s = snow.getDefaultState();
        c.ok(!s.isOpaqueCube() && !s.isFullCube() && !s.getMaterial().blocksMovement() && !s.getMaterial().isSolid()
                && !s.getMaterial().isReplaceable(), "powder snow is not solid, not opaque, not replaceable");
        c.eq(1, s.getLightOpacity(), "light opacity");
        c.eq(0.25F, s.getBlockHardness(c.world, BlockPos.ORIGIN), "hardness");
        c.ok(!s.isSideSolid(c.world, BlockPos.ORIGIN, EnumFacing.UP), "nothing rests on it");
        c.eq(new java.util.TreeSet<String>(Arrays.asList(PowderSnowContent.BLOCK_NAMES)),
                new java.util.TreeSet<String>(Arrays.asList("powder_snow", "powder_snow_cauldron")), "names for the ownership table");
        for (IBlockState st : PowderSnowContent.cauldron.getBlockState().getValidStates()) {
            c.ok(PowderSnowContent.cauldron.getStateFromMeta(PowderSnowContent.cauldron.getMetaFromState(st)) == st, "cauldron meta " + st);
        }
        c.eq(3, PowderSnowContent.cauldron.getBlockState().getValidStates().size(), "cauldron levels");
        c.ok(Freezing.FREEZE.isUnblockable(), "freeze damage ignores armor");
        c.eq("freeze", Freezing.FREEZE.getDamageType(), "freeze damage name");
        c.ok(c.world.getGameRules().hasRule(Freezing.GAME_RULE), "the freezeDamage game rule exists");
    }

    // ---- collision ---------------------------------------------------------------------------

    private List<AxisAlignedBB> boxes(Check c, BlockPos pos, Entity e) {
        List<AxisAlignedBB> list = new ArrayList<AxisAlignedBB>();
        c.world.getBlockState(pos).addCollisionBoxToList(c.world, pos, new AxisAlignedBB(pos).grow(2), list, e, false);
        return list;
    }

    private void collision(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        c.world.setBlockState(o, snow(), 2);
        c.eq(Block.NULL_AABB, snow().getCollisionBoundingBox(c.world, o), "no collision box of its own");
        c.eq(0, boxes(c, o, null).size(), "no entity: nothing");

        EntityZombie zombie = new EntityZombie(c.world);
        zombie.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
        c.eq(0, boxes(c, o, zombie).size(), "a zombie standing over it: nothing");
        zombie.fallDistance = 3.0F;
        List<AxisAlignedBB> falling = boxes(c, o, zombie);
        c.ok(falling.size() == 1 && Math.abs(falling.get(0).maxY - (o.getY() + 0.9)) < 1.0E-6, "fallen more than 2.5 blocks: a box 0.9 high");
        zombie.fallDistance = 0.0F;

        zombie.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        List<AxisAlignedBB> booted = boxes(c, o, zombie);
        c.ok(booted.size() == 1 && booted.get(0).maxY == o.getY() + 1, "leather boots, above: the full cube");
        zombie.setSneaking(true);
        c.eq(0, boxes(c, o, zombie).size(), "leather boots, sneaking: nothing");
        zombie.setSneaking(false);
        zombie.setPosition(o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5);
        c.eq(0, boxes(c, o, zombie).size(), "leather boots, inside: nothing");
        zombie.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
        zombie.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        c.eq(0, boxes(c, o, zombie).size(), "iron boots: nothing");
        zombie.setItemStackToSlot(EntityEquipmentSlot.FEET, ItemStack.EMPTY);
        zombie.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        c.eq(0, boxes(c, o, zombie).size(), "a leather helmet does not hold anyone up");

        EntityRabbit rabbit = new EntityRabbit(c.world);
        rabbit.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
        c.eq(1, boxes(c, o, rabbit).size(), "a rabbit walks on it");
        c.eq(1, boxes(c, o, new EntityFallingBlock(c.world, o.getX() + 0.5, o.getY() + 3, o.getZ() + 0.5, STONE)).size(), "a falling block lands on it");
        EntityItem item = new EntityItem(c.world, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, new ItemStack(Items.STICK));
        c.eq(0, boxes(c, o, item).size(), "an item falls in");

        c.eq(PathNodeType.DAMAGE_OTHER, snow().getBlock().getAiPathNodeType(snow(), c.world, o, zombie), "mobs do not path into it");
        c.eq(PathNodeType.BLOCKED, snow().getBlock().getAiPathNodeType(snow(), c.world, o, rabbit), "a rabbit paths over it");
        c.ok(Freezing.walkableMob(rabbit) && !Freezing.walkableMob(zombie), "walkable mobs");
        // 26.4's #powder_snow_walkable_mobs, other backports' mobs by id: Village & Pillage's fox, Game Drops' frostbite.
        c.ok(Freezing.walkableId(new net.minecraft.util.ResourceLocation("villagerbackport", "fox")), "a fox walks on it");
        c.ok(Freezing.walkableId(new net.minecraft.util.ResourceLocation("gamedropsbackport", "frostbite")), "a frostbite walks on it (26.4)");
        c.ok(!Freezing.walkableId(new net.minecraft.util.ResourceLocation("gamedropsbackport", "ice_ball"))
                && !Freezing.walkableId(new net.minecraft.util.ResourceLocation("minecraft", "zombie")), "other ids do not");
        c.world.setBlockState(o, AIR, 2);
    }

    // ---- sinking and boots -------------------------------------------------------------------

    private void sinking(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        // A column of powder snow five deep, on the stone floor.
        c.fill(o, o.up(4), snow());
        double top = o.getY() + 5;

        EntityArmorStand bare = spawn(c, new EntityArmorStand(c.world), o.getX() + 0.5, top, o.getZ() + 0.5);
        tick(bare, 12);
        double before = bare.posY;
        tick(bare, 1);
        c.ok(Math.abs((before - bare.posY) - 0.1176) < 0.001, "sinks 0.1176 a tick (1.5 times a tick of gravity): " + (before - bare.posY));
        c.ok(bare.posY < top - 0.5, "it is inside the snow");
        c.eq(0.0F, bare.fallDistance, "no fall distance inside");
        c.ok(Freezing.inSnow(bare), "marked as in powder snow");
        tick(bare, 100);
        c.ok(Math.abs(bare.posY - o.getY()) < 0.01, "it comes to rest on the floor under the snow: " + bare.posY);
        bare.setDead();

        EntityArmorStand booted = new EntityArmorStand(c.world);
        booted.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        spawn(c, booted, o.getX() + 0.5, top, o.getZ() + 0.5);
        tick(booted, 40);
        c.ok(Math.abs(booted.posY - top) < 0.01 && booted.onGround, "leather boots stand on top: " + booted.posY);
        c.ok(!Freezing.inSnow(booted), "and are not in the snow");
        booted.setDead();

        // An item sinks too, at 1.5 times its own gravity.
        EntityItem item = spawn(c, new EntityItem(c.world, 0, 0, 0, new ItemStack(Items.STICK)), o.getX() + 0.5, top - 0.5, o.getZ() + 0.5);
        item.motionX = item.motionY = item.motionZ = 0;
        tick(item, 5);
        before = item.posY;
        tick(item, 1);
        c.ok(Math.abs((before - item.posY) - 0.06) < 0.001, "an item sinks 0.06 a tick: " + (before - item.posY));
        item.setDead();
        c.fill(o, o.up(4), AIR);
    }

    private void falling(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        // Control: a pig dropped 20 blocks onto stone is hurt.
        EntityPig control = spawn(c, new EntityPig(c.world), o.getX() + 8.5, o.getY() + 20, o.getZ() + 0.5);
        control.setNoAI(false);
        tick(control, 80);
        c.ok(control.getHealth() < control.getMaxHealth() || control.isDead, "control: 20 blocks onto stone hurts");
        control.setDead();

        c.fill(o, o.up(1), snow());
        EntityPig pig = spawn(c, new EntityPig(c.world), o.getX() + 0.5, o.getY() + 22, o.getZ() + 0.5);
        tick(pig, 120);
        c.eq(pig.getMaxHealth(), pig.getHealth(), "20 blocks onto powder snow: no damage");
        c.ok(pig.posY < o.getY() + 1.5, "and it sank in: " + pig.posY);
        pig.setDead();
        c.fill(o, o.up(1), AIR);
    }

    // ---- freezing ----------------------------------------------------------------------------

    /** A one-block hole in stone, two deep, filled with powder snow. */
    private BlockPos hole(Check c, BlockPos o) {
        c.fill(o.add(-1, 0, -1), o.add(1, 1, 1), STONE);
        c.fill(o, o.up(), snow());
        return o;
    }

    private void freezing(Check c) {
        BlockPos o = hole(c, c.area().add(4, 0, 4));
        EntityPig pig = spawn(c, new EntityPig(c.world), o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        tick(pig, 100);
        int at100 = Freezing.ticks(pig);
        c.ok(at100 >= 97 && at100 <= 100, "one freeze tick a tick: " + at100 + " after 100");
        c.ok(!Freezing.fullyFrozen(pig), "not yet frozen");
        AttributeModifier slow = pig.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(Freezing.SLOW);
        c.ok(slow != null && slow.getAmount() < -0.03 && slow.getAmount() > -0.05, "slowed as it freezes: " + (slow == null ? null : slow.getAmount()));
        c.eq(pig.getMaxHealth(), pig.getHealth(), "no damage before 140");
        tick(pig, 45);
        c.eq(Freezing.FREEZE_TICKS, Freezing.ticks(pig), "stops at 140");
        c.ok(Freezing.fullyFrozen(pig) && Freezing.shaking(pig), "fully frozen, shaking");
        tick(pig, 80);
        float lost = pig.getMaxHealth() - pig.getHealth();
        c.ok(lost >= 2.0F && lost <= 3.0F, "1 damage every 40 ticks: lost " + lost + " in 80-125");
        slow = pig.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(Freezing.SLOW);
        c.ok(slow != null && Math.abs(slow.getAmount() + 0.05) < 1.0E-6, "slowed by 0.05 when frozen");

        // Out of the snow: two ticks back a tick, then no trace is left on the entity.
        c.fill(o, o.up(), AIR);
        pig.getEntityData().getCompoundTag(Freezing.TAG).setLong("Seen", c.world.getTotalWorldTime() - 10);
        // The test world's time stands still: Core's "held this tick" is put in the past the same way.
        pig.getEntityData().getCompoundTag(com.exiledradio.backportcore.freeze.FreezeTicks.TAG).setLong("Held", c.world.getTotalWorldTime() - 10);
        tick(pig, 10);
        c.eq(Freezing.FREEZE_TICKS - 20, Freezing.ticks(pig), "thaws two ticks a tick");
        tick(pig, 70);
        c.eq(0, Freezing.ticks(pig), "thawed");
        c.ok(!pig.getEntityData().hasKey(Freezing.TAG) && !pig.getEntityData().hasKey(com.exiledradio.backportcore.freeze.FreezeTicks.TAG), "no freeze data left");
        c.ok(pig.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(Freezing.SLOW) == null, "no longer slowed");
        pig.setDead();

        // Heat-loving mobs take 5.
        EntityBlaze blaze = spawn(c, new EntityBlaze(c.world), o.getX() + 0.5, o.getY() + 6, o.getZ() + 0.5);
        blaze.setNoAI(true);
        Freezing.setTicks(blaze, Freezing.FREEZE_TICKS);
        for (int i = 0; i < 40; i++) {
            Freezing.markInSnow(blaze);
            blaze.ticksExisted++;
            blaze.onUpdate();
        }
        c.eq(blaze.getMaxHealth() - 5.0F, blaze.getHealth(), "a blaze takes 5");
        c.ok(Freezing.hurtsExtra(blaze), "blazes are hurt more");
        blaze.setDead();

        // Who cannot freeze.
        c.ok(!Freezing.canFreeze(new EntityStray(c.world)) && !Freezing.canFreeze(new EntityPolarBear(c.world))
                && !Freezing.canFreeze(new EntitySnowman(c.world)), "strays, polar bears and snow golems never freeze");
        // 1.17's freeze_immune_entity_types, with this mod as the newest backport (Core gates the 26.4 undead rule).
        c.ok(Freezing.canFreeze(new EntitySkeleton(c.world)), "skeletons freeze (and turn into strays)");
        c.ok(Freezing.canFreeze(new EntityWitherSkeleton(c.world)), "wither skeletons do");
        EntityZombie zombie = new EntityZombie(c.world);
        c.ok(Freezing.canFreeze(zombie), "a zombie freezes");
        for (EntityEquipmentSlot slot : new EntityEquipmentSlot[] {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}) {
            Item[] leather = {Items.LEATHER_BOOTS, Items.LEATHER_LEGGINGS, Items.LEATHER_CHESTPLATE, Items.LEATHER_HELMET};
            zombie.setItemStackToSlot(slot, new ItemStack(leather[slot.getIndex()]));
            c.ok(!Freezing.canFreeze(zombie), "leather in the " + slot.getName() + " slot prevents freezing");
            zombie.setItemStackToSlot(slot, ItemStack.EMPTY);
        }
        zombie.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        c.ok(Freezing.canFreeze(zombie), "iron armor does not");

        // The config's extra lists.
        // Core's Freezing settings (they were this mod's until 1.2.0).
        com.exiledradio.backportcore.CoreConfig.Freezing cfg = com.exiledradio.backportcore.CoreConfig.freezing;
        String[] wear = cfg.freezeImmuneWearables, immune = cfg.freezeImmuneMobs;
        cfg.freezeImmuneWearables = new String[] {"minecraft:iron_chestplate"};
        cfg.freezeImmuneMobs = new String[] {"minecraft:cow"};
        c.ok(!Freezing.canFreeze(zombie), "config: a listed item prevents freezing");
        c.ok(!Freezing.canFreeze(new net.minecraft.entity.passive.EntityCow(c.world)), "config: a listed mob never freezes");
        cfg.freezeImmuneWearables = wear;
        cfg.freezeImmuneMobs = immune;
        c.ok(Freezing.canFreeze(zombie) && Freezing.canFreeze(new net.minecraft.entity.passive.EntityCow(c.world)), "config lists restored");
        // Mobs that may spawn in powder snow keep vanilla's immuneTo list, whichever freeze rules apply.
        c.ok(Freezing.spawnsInSnow(new EntityStray(c.world)) && !Freezing.spawnsInSnow(new EntityZombie(c.world)),
                "strays spawn in powder snow, zombies do not");
        c.fill(o.add(-1, 0, -1), o.add(1, 1, 1), AIR);
    }

    private void players(Check c) {
        BlockPos o = c.area().add(4, 0, 4);
        EntityPlayerMP p = Players.real(c.world, "powdersnow");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.setPosition(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        p.setHealth(p.getMaxHealth());
        p.inventory.clear();
        // A new player cannot be hurt for its first 60 ticks.
        for (int i = 0; i < 61; i++) {
            p.onUpdate();
        }
        snowTick(p, 150);
        c.eq(Freezing.FREEZE_TICKS, Freezing.ticks(p), "a player freezes");
        // A fed player heals between the hits; that is not what is measured here.
        c.world.getGameRules().setOrCreateGameRule("naturalRegeneration", "false");
        float health = p.getHealth();
        snowTick(p, 80);
        c.ok(p.getHealth() <= health - 1.0F, "and is hurt: " + health + " -> " + p.getHealth());

        c.world.getGameRules().setOrCreateGameRule(Freezing.GAME_RULE, "false");
        health = p.getHealth();
        snowTick(p, 80);
        c.eq(health, p.getHealth(), "freezeDamage off: no damage");
        c.world.getGameRules().setOrCreateGameRule(Freezing.GAME_RULE, "true");
        c.world.getGameRules().setOrCreateGameRule("naturalRegeneration", "true");

        p.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        snowTick(p, 80);
        c.eq(0, Freezing.ticks(p), "a leather helmet thaws them in the snow");
        p.setItemStackToSlot(EntityEquipmentSlot.HEAD, ItemStack.EMPTY);

        p.interactionManager.setGameType(GameType.SPECTATOR);
        snowTick(p, 20);
        c.eq(0, Freezing.ticks(p), "spectators do not freeze");
        p.interactionManager.setGameType(GameType.SURVIVAL);

        // Light as a Rabbit: on top of powder snow in leather boots.
        c.world.setBlockState(o, snow(), 2);
        p.setPosition(o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5);
        p.onGround = true;
        c.ok(!PowderSnowEvents.onPowderSnowInBoots(p), "no boots: not walking on it");
        c.ok(!Advancements.has(p, PowderSnowEvents.ADVANCEMENT), "no advancement yet");
        p.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        c.ok(PowderSnowEvents.onPowderSnowInBoots(p), "leather boots on powder snow");
        for (int i = 0; i < 6; i++) {
            p.ticksExisted++;
            p.onUpdateEntity();
        }
        c.ok(Math.abs(p.posY - (o.getY() + 1)) < 0.01, "the player stays on top: " + p.posY);
        c.ok(Advancements.has(p, PowderSnowEvents.ADVANCEMENT), "Light as a Rabbit");
        c.world.setBlockState(o, AIR, 2);
    }

    // ---- strays ------------------------------------------------------------------------------

    private void strays(Check c) {
        BlockPos o = hole(c, c.area().add(4, 0, 4));
        // A roof, so the skeleton does not burn in the sun.
        c.fill(o.add(-1, 3, -1), o.add(1, 3, 1), STONE);
        EntitySkeleton skeleton = spawn(c, new EntitySkeleton(c.world), o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        skeleton.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        skeleton.setCustomNameTag("Frosty");
        skeleton.enablePersistence();
        skeleton.setHealth(15.0F);
        tick(skeleton, 100);
        c.ok(!Freezing.shaking(skeleton) && !skeleton.isDead, "100 ticks in: still a skeleton, not shaking");
        int frozen = Freezing.ticks(skeleton);
        c.ok(frozen >= 97 && frozen <= 100, "a skeleton freezes as any mob, once a tick in two blocks of snow (1.18.2 Skeleton has no canFreeze): " + frozen);
        tick(skeleton, 45);
        c.ok(Freezing.shaking(skeleton), "after 140 it shakes");
        // It finishes even out of the snow.
        c.fill(o, o.up(), AIR);
        skeleton.getEntityData().getCompoundTag(Freezing.TAG).setLong("Seen", c.world.getTotalWorldTime() - 10);
        tick(skeleton, 290);
        c.ok(!skeleton.isDead, "still converting just before 300 more");
        tick(skeleton, 20);
        c.ok(skeleton.isDead, "the skeleton is gone");
        List<EntityStray> strays = c.world.getEntitiesWithinAABB(EntityStray.class, new AxisAlignedBB(o).grow(3));
        if (c.eq(1, strays.size(), "one stray in its place")) {
            EntityStray stray = strays.get(0);
            this.spawned.add(stray);
            c.eq("Frosty", stray.getCustomNameTag(), "it keeps its name");
            c.eq(Items.BOW, stray.getHeldItemMainhand().getItem(), "its bow");
            c.eq(Items.IRON_HELMET, stray.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem(), "its helmet");
            c.eq(stray.getMaxHealth(), stray.getHealth(), "and is at full health");
            c.ok(!stray.getEntityData().hasKey(Freezing.TAG), "with no freeze data");
        }

        // Off in the config: nothing happens.
        hole(c, o);
        ModConfig.powderSnow.strayConversion = false;
        EntitySkeleton second = spawn(c, new EntitySkeleton(c.world), o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        tick(second, 200);
        c.ok(!Freezing.converting(second) && !second.isDead, "config off: no conversion");
        ModConfig.powderSnow.strayConversion = true;
        second.setDead();
        c.fill(o.add(-1, 0, -1), o.add(1, 3, 1), AIR);
    }

    // ---- bucket, cauldron, dispenser ---------------------------------------------------------

    private EntityPlayerMP survivor(Check c, BlockPos at, ItemStack held) {
        EntityPlayerMP p = Players.real(c.world, "powdersnow");
        p.interactionManager.setGameType(GameType.SURVIVAL);
        p.inventory.clear();
        p.setPositionAndRotation(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 90.0F);
        p.setHeldItem(EnumHand.MAIN_HAND, held);
        return p;
    }

    private EnumActionResult click(EntityPlayerMP p, BlockPos pos, EnumFacing face) {
        return p.interactionManager.processRightClickBlock(p, p.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND, pos, face, 0.5F, 0.5F, 0.5F);
    }

    private void bucket(Check c) {
        BlockPos floor = c.area().add(4, -1, 4);
        BlockPos at = floor.up();
        EntityPlayerMP p = survivor(c, at.up(2), new ItemStack(PowderSnowContent.bucket));
        c.eq(EnumActionResult.SUCCESS, click(p, floor, EnumFacing.UP), "the bucket is used on the floor");
        c.eq(PowderSnowContent.powderSnow, c.world.getBlockState(at).getBlock(), "powder snow is placed on the clicked face");
        c.eq(Items.BUCKET, p.getHeldItemMainhand().getItem(), "an empty bucket is left");

        // Looking straight down at it with the empty bucket.
        p.setPositionAndRotation(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0.0F, 90.0F);
        p.interactionManager.processRightClick(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND);
        c.eq(Blocks.AIR, c.world.getBlockState(at).getBlock(), "an empty bucket takes it");
        c.eq(PowderSnowContent.bucket, p.getHeldItemMainhand().getItem(), "and becomes a powder snow bucket");

        // A stack of empty buckets: one is filled, the rest stay.
        c.world.setBlockState(at, snow(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET, 3));
        p.interactionManager.processRightClick(p, c.world, p.getHeldItemMainhand(), EnumHand.MAIN_HAND);
        c.ok(p.getHeldItemMainhand().getItem() == Items.BUCKET && p.getHeldItemMainhand().getCount() == 2
                && p.inventory.hasItemStack(new ItemStack(PowderSnowContent.bucket)), "from a stack: one filled bucket goes to the inventory");

        // Creative keeps the bucket.
        p.inventory.clear();
        p.interactionManager.setGameType(GameType.CREATIVE);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(PowderSnowContent.bucket));
        click(p, floor, EnumFacing.UP);
        c.ok(c.world.getBlockState(at).getBlock() == PowderSnowContent.powderSnow && p.getHeldItemMainhand().getItem() == PowderSnowContent.bucket,
                "creative: placed, bucket kept");
        p.interactionManager.setGameType(GameType.SURVIVAL);

        // Breaking it gives nothing, Silk Touch or not; pick block gives the bucket.
        c.eq(PowderSnowContent.bucket, snow().getBlock().getPickBlock(snow(), null, c.world, at, p).getItem(), "pick block is the bucket");
        ItemStack silk = new ItemStack(Items.DIAMOND_SHOVEL);
        silk.addEnchantment(net.minecraft.init.Enchantments.SILK_TOUCH, 1);
        p.setHeldItem(EnumHand.MAIN_HAND, silk);
        p.interactionManager.tryHarvestBlock(at);
        c.eq(Blocks.AIR, c.world.getBlockState(at).getBlock(), "it breaks");
        c.eq(0, c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(at).grow(2)).size(), "and drops nothing, even with Silk Touch");
    }

    private void cauldron(Check c) {
        BlockPos at = c.area().add(4, 0, 4);
        IBlockState empty = Blocks.CAULDRON.getDefaultState();
        BlockPowderSnowCauldron ours = PowderSnowContent.cauldron;

        // Snowfall.
        c.world.setBlockState(at, empty, 2);
        c.ok(BlockPowderSnowCauldron.snowInto(c.world, at), "snow falls into an empty cauldron");
        c.eq(ours.getDefaultState(), c.world.getBlockState(at), "level 1");
        c.eq(1, c.world.getBlockState(at).getComparatorInputOverride(c.world, at), "comparator 1");
        c.ok(BlockPowderSnowCauldron.snowInto(c.world, at) && BlockPowderSnowCauldron.snowInto(c.world, at), "then levels 2 and 3");
        c.eq(3, c.world.getBlockState(at).getValue(BlockPowderSnowCauldron.LEVEL), "full");
        c.ok(!BlockPowderSnowCauldron.snowInto(c.world, at), "a full one takes no more");
        c.eq(3, c.world.getBlockState(at).getComparatorInputOverride(c.world, at), "comparator 3");
        c.world.setBlockState(at, empty.withProperty(BlockCauldron.LEVEL, 1), 2);
        c.ok(!BlockPowderSnowCauldron.snowInto(c.world, at), "snow does not fall into water");

        // Buckets.
        c.world.setBlockState(at, ours.getDefaultState().withProperty(BlockPowderSnowCauldron.LEVEL, 2), 2);
        EntityPlayerMP p = survivor(c, at.up(2), new ItemStack(Items.BUCKET));
        click(p, at, EnumFacing.UP);
        c.ok(c.world.getBlockState(at).getBlock() == ours && p.getHeldItemMainhand().getItem() == Items.BUCKET, "a bucket takes nothing from level 2");
        c.world.setBlockState(at, ours.getDefaultState().withProperty(BlockPowderSnowCauldron.LEVEL, 3), 2);
        click(p, at, EnumFacing.UP);
        c.eq(empty, c.world.getBlockState(at), "a bucket empties a full one: the vanilla cauldron again");
        c.eq(PowderSnowContent.bucket, p.getHeldItemMainhand().getItem(), "and holds powder snow");
        click(p, at, EnumFacing.UP);
        c.eq(3, c.world.getBlockState(at).getBlock() == ours ? c.world.getBlockState(at).getValue(BlockPowderSnowCauldron.LEVEL) : -1,
                "a powder snow bucket fills an empty cauldron");
        c.eq(Items.BUCKET, p.getHeldItemMainhand().getItem(), "leaving the bucket");
        c.eq(Blocks.AIR, c.world.getBlockState(at.up()).getBlock(), "and places no block");
        c.world.setBlockState(at, empty.withProperty(BlockCauldron.LEVEL, 3), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(PowderSnowContent.bucket));
        click(p, at, EnumFacing.UP);
        c.eq(ours, c.world.getBlockState(at).getBlock(), "it replaces water too");
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        click(p, at, EnumFacing.UP);
        c.eq(empty.withProperty(BlockCauldron.LEVEL, 3), c.world.getBlockState(at), "a water bucket replaces powder snow with water");
        c.eq(Items.BUCKET, p.getHeldItemMainhand().getItem(), "leaving the bucket");

        // An entity on fire melts a level into water.
        c.world.setBlockState(at, ours.getDefaultState().withProperty(BlockPowderSnowCauldron.LEVEL, 3), 2);
        EntityPig pig = new EntityPig(c.world);
        pig.setPosition(at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5);
        pig.setFire(10);
        ours.onEntityCollision(c.world, at, c.world.getBlockState(at), pig);
        c.ok(!pig.isBurning(), "a burning entity is put out");
        c.eq(empty.withProperty(BlockCauldron.LEVEL, 2), c.world.getBlockState(at), "and leaves water, one level lower");

        // Mined: the vanilla cauldron.
        c.world.setBlockState(at, ours.getDefaultState(), 2);
        p.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        p.interactionManager.tryHarvestBlock(at);
        c.eq(1, Drops.count(c.world, at, Items.CAULDRON), "mined, it drops a cauldron");

        // Where it snows.
        WorldServer w = c.world;
        BlockPos cold = w.getBiomeProvider().findBiomePosition(0, 0, 6000, Arrays.<Biome>asList(Biomes.ICE_PLAINS, Biomes.COLD_TAIGA), new Random(1));
        BlockPos hot = w.getBiomeProvider().findBiomePosition(0, 0, 6000, Arrays.<Biome>asList(Biomes.DESERT), new Random(1));
        BlockPos warm = w.getBiomeProvider().findBiomePosition(0, 0, 6000, Arrays.<Biome>asList(Biomes.EXTREME_HILLS), new Random(1));
        if (cold != null) {
            c.ok(PowderSnowEvents.snowsAt(w, cold.up(70)), "it snows in a snowy biome");
        }
        if (hot != null) {
            c.ok(!PowderSnowEvents.snowsAt(w, hot.up(250)), "never in a desert, however high");
        }
        if (warm != null) {
            c.ok(!PowderSnowEvents.snowsAt(w, warm.up(66)), "not at the foot of extreme hills");
            c.ok(PowderSnowEvents.snowsAt(w, warm.up(140)), "but it does high up them");
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST powdersnow: climate checked at cold={} hot={} warm={}", cold, hot, warm);
    }

    private void dispenser(Check c) {
        BlockPos at = c.area().add(4, 0, 4);
        IBlockState state = Blocks.DISPENSER.getDefaultState().withProperty(BlockDispenser.FACING, EnumFacing.EAST);
        c.world.setBlockState(at, state, 2);
        TileEntityDispenser te = (TileEntityDispenser) c.world.getTileEntity(at);
        te.setInventorySlotContents(0, new ItemStack(PowderSnowContent.bucket));
        Blocks.DISPENSER.updateTick(c.world, at, state, c.world.rand);
        c.eq(PowderSnowContent.powderSnow, c.world.getBlockState(at.east()).getBlock(), "a dispenser places powder snow");
        c.eq(Items.BUCKET, te.getStackInSlot(0).getItem(), "and keeps the empty bucket");
        Blocks.DISPENSER.updateTick(c.world, at, state, c.world.rand);
        c.eq(Blocks.AIR, c.world.getBlockState(at.east()).getBlock(), "with the empty bucket it takes it back");
        c.eq(PowderSnowContent.bucket, te.getStackInSlot(0).getItem(), "into the bucket");
        // Blocked: the bucket is thrown out instead, nothing is placed.
        c.world.setBlockState(at.east(), STONE, 2);
        Blocks.DISPENSER.updateTick(c.world, at, state, c.world.rand);
        c.eq(Blocks.STONE, c.world.getBlockState(at.east()).getBlock(), "a blocked dispenser places nothing");
        // The empty bucket still takes water.
        te.clear();
        te.setInventorySlotContents(0, new ItemStack(Items.BUCKET));
        c.world.setBlockState(at.east(), Blocks.WATER.getDefaultState(), 2);
        Blocks.DISPENSER.updateTick(c.world, at, state, c.world.rand);
        c.eq(Items.WATER_BUCKET, te.getStackInSlot(0).getItem(), "the empty bucket still takes water");
        te.clear();
        c.world.setBlockState(at, AIR, 2);
        c.world.setBlockState(at.east(), AIR, 2);
        Drops.count(c.world, at, Items.BUCKET);
    }

    // ---- fire, spawning ----------------------------------------------------------------------

    private void fireAndSpawning(Check c) {
        BlockPos at = c.area().add(4, 0, 4);
        c.world.setBlockState(at, snow(), 2);
        EntityPig pig = new EntityPig(c.world);
        pig.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        pig.setFire(10);
        snow().getBlock().onEntityCollision(c.world, at, snow(), pig);
        c.ok(!pig.isBurning(), "powder snow puts a burning entity out");
        c.eq(Blocks.AIR, c.world.getBlockState(at).getBlock(), "and melts");

        c.world.setBlockState(at, snow(), 2);
        // Only powder snow's own rule: 1.18's spawn light rule would deny these daylight spawns under the open sky.
        boolean spawnLight = ModConfig.changes.monsterSpawnLight;
        ModConfig.changes.monsterSpawnLight = false;
        EntityLiving zombie = new EntityZombie(c.world);
        c.eq(Event.Result.DENY, ForgeEventFactory.canEntitySpawn(zombie, c.world, at.getX() + 0.5F, at.getY(), at.getZ() + 0.5F, false),
                "nothing spawns inside powder snow");
        c.ok(Event.Result.DENY != ForgeEventFactory.canEntitySpawn(new EntityStray(c.world), c.world, at.getX() + 0.5F, at.getY(), at.getZ() + 0.5F, false),
                "except what it cannot hurt");
        c.ok(Event.Result.DENY != ForgeEventFactory.canEntitySpawn(zombie, c.world, at.getX() + 0.5F, at.getY() + 3, at.getZ() + 0.5F, false),
                "and it denies nothing elsewhere");
        ModConfig.changes.monsterSpawnLight = spawnLight;
        c.world.setBlockState(at, AIR, 2);
    }

    // ---- generation --------------------------------------------------------------------------

    private void patches(Check c) {
        BlockPos o = c.area();
        // Snowy ground: dirt under grass under a snow layer, on the stone floor.
        c.fill(o.add(2, 0, 2), o.add(20, 0, 20), Blocks.DIRT.getDefaultState());
        c.fill(o.add(2, 1, 2), o.add(20, 1, 20), Blocks.GRASS.getDefaultState());
        c.fill(o.add(2, 2, 2), o.add(20, 2, 20), Blocks.SNOW_LAYER.getDefaultState());
        int placed = PowderSnowGenerator.patch(c.world, new Random(7), o.getX() + 11, o.getZ() + 11, 0, false);
        int found = 0, deepest = 0, above = 0, layersLeft = 0;
        for (int dx = 0; dx <= 22; dx++) {
            for (int dz = 0; dz <= 22; dz++) {
                int depth = 0;
                for (int dy = -1; dy <= 3; dy++) {
                    BlockPos p = o.add(dx, dy, dz);
                    if (c.world.getBlockState(p).getBlock() == PowderSnowContent.powderSnow) {
                        found++;
                        depth++;
                        if (dy > 1) {
                            above++;
                        }
                    }
                }
                deepest = Math.max(deepest, depth);
                if (depth > 0 && c.world.getBlockState(o.add(dx, 2, dz)).getBlock() == Blocks.SNOW_LAYER) {
                    layersLeft++;
                }
                if (depth > 0) {
                    c.ok(c.world.getBlockState(o.add(dx, 1, dz)).getBlock() == PowderSnowContent.powderSnow, "a patch column starts at the surface");
                }
            }
        }
        c.ok(placed >= 8 && placed == found, "a patch is placed: " + placed + " blocks, " + found + " found");
        c.ok(deepest >= 2 && deepest <= 3, "up to three deep: " + deepest);
        c.eq(0, above, "nothing above the ground");
        c.eq(0, layersLeft, "the snow layer over it is gone");
        c.eq(Blocks.GRASS, c.world.getBlockState(o.add(2, 1, 2)).getBlock(), "the ground around it is untouched");
        c.eq(0, PowderSnowGenerator.patch(c.world, new Random(7), o.getX() + 11, o.getZ() + 11, 200, true), "below the lowest height: nothing");
        c.fill(o.add(0, 0, 0), o.add(22, 3, 22), AIR);
        c.fill(o.add(0, -1, 0), o.add(22, -1, 22), STONE);

        // Real terrain: snowy hills near the origin, if the seed has any.
        WorldServer w = c.world;
        BlockPos hills = w.getBiomeProvider().findBiomePosition(0, 0, 8000,
                Arrays.<Biome>asList(Biomes.ICE_MOUNTAINS, Biomes.COLD_TAIGA_HILLS), new Random(3));
        if (hills == null) {
            hills = w.getBiomeProvider().findBiomePosition(0, 0, 8000,
                    Arrays.<Biome>asList(Biomes.EXTREME_HILLS, Biomes.EXTREME_HILLS_WITH_TREES), new Random(3));
        }
        if (hills == null) {
            CavesAndCliffsBackport.LOGGER.info("SELFTEST powdersnow: no hills within 8000 blocks, real terrain not checked");
            return;
        }
        int cx = hills.getX() >> 4, cz = hills.getZ() >> 4, reach = 5;
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                w.getChunk(x, z);
            }
        }
        int minY = ModConfig.powderSnow.patchMinY;
        int generated = 0, low = 0, snowyHigh = 0, highest = 0;
        for (int x = (cx - reach + 1) << 4; x < (cx + reach) << 4; x++) {
            for (int z = (cz - reach + 1) << 4; z < (cz + reach) << 4; z++) {
                int h = w.getHeight(x, z);
                highest = Math.max(highest, h);
                if (w.getBlockState(new BlockPos(x, h, z)).getBlock() == Blocks.SNOW_LAYER && h - 1 >= minY) {
                    snowyHigh++;
                }
                for (int y = 60; y <= h; y++) {
                    if (w.getBlockState(new BlockPos(x, y, z)).getBlock() == PowderSnowContent.powderSnow) {
                        generated++;
                        if (y < minY - 3) {
                            low++;
                        }
                    }
                }
            }
        }
        // Then by hand over the same ground, so the rule is checked even where the generator's dice missed.
        int byHand = 0;
        Random r = new Random(11);
        for (int x = (cx - reach + 1) << 4; x < (cx + reach) << 4; x += 7) {
            for (int z = (cz - reach + 1) << 4; z < (cz + reach) << 4; z += 7) {
                byHand += PowderSnowGenerator.patch(w, r, x, z, minY, true);
            }
        }
        CavesAndCliffsBackport.LOGGER.info("SELFTEST powdersnow: real terrain at {} ({}), highest {}: {} snowy columns at or above {}, "
                + "{} powder snow generated, {} more placed by hand", hills, w.getBiome(hills).getRegistryName(), highest, snowyHigh, minY, generated, byHand);
        c.eq(0, low, "no powder snow under the lowest height in real terrain");
        if (snowyHigh > 200) {
            c.ok(generated > 0, "patches generate in snowy high ground: " + generated + " blocks over " + snowyHigh + " snowy columns");
            c.ok(byHand > 0, "and the rule accepts that ground");
        }
    }

    // ---- packet ------------------------------------------------------------------------------

    private void packet(Check c) {
        ByteBuf a = Unpooled.buffer();
        new PacketFreeze(123456, 140, 3).toBytes(a);
        PacketFreeze read = new PacketFreeze();
        read.fromBytes(a.copy());
        ByteBuf b = Unpooled.buffer();
        read.toBytes(b);
        c.ok(a.equals(b) && a.readableBytes() == 5, "the freeze packet survives a round trip in 5 bytes");
    }
}
