package com.exiledradio.cavesandcliffsbackport.common;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerMerchant;
import net.minecraft.item.ItemRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.17's and 1.18's changes to vanilla advancements, the family's way (Buzzy Bees' {@code AdvancementTweaks}):
 * <ul>
 *   <li><i>Two by Two</i> ({@code husbandry/bred_all_animals}) also needs an axolotl and a goat bred (1.17's criteria
 *       {@code minecraft:axolotl} and {@code minecraft:goat}); <i>A Balanced Diet</i> ({@code husbandry/balanced_diet})
 *       also glow berries eaten ({@code glow_berries}). 1.12.2 cannot add to another namespace's advancement file, so
 *       the loaded advancements get the criteria by reflection whenever the server has loaded new ones (start,
 *       {@code /reload}), and players' progress is read again so their listeners include them. Criteria other mods
 *       add the same way are kept.</li>
 *   <li>1.18's <i>Sound of Music</i> ({@code adventure/play_jukebox_in_meadows}, under <i>Sweet Dreams</i>): a music
 *       disc put in a jukebox in a meadow. A meadow is 1.18's, where the world has 1.18 terrain.</li>
 *   <li>1.18's <i>Star Trader</i> ({@code adventure/trade_at_world_height}, under <i>What a Deal!</i>): a trade with a
 *       villager while standing at y 319 or higher, as 1.18's criterion. 1.12.2 has no trade trigger, so a trade is
 *       seen as a rise in the uses of the open trade screen's offers (Village &amp; Pillage reads trades the same way).</li>
 * </ul>
 * The two new ones are this mod's files with the {@code code} criterion ({@link Advancements}).
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class VanillaAdvancements {

    public static final ResourceLocation TWO_BY_TWO = new ResourceLocation("minecraft", "husbandry/bred_all_animals");
    public static final ResourceLocation BALANCED_DIET = new ResourceLocation("minecraft", "husbandry/balanced_diet");
    public static final String AXOLOTL_CRITERION = "minecraft:axolotl";
    public static final String GOAT_CRITERION = "minecraft:goat";
    public static final String GLOW_BERRIES_CRITERION = "glow_berries";
    public static final String SOUND_OF_MUSIC = "adventure/play_jukebox_in_meadows";
    public static final String STAR_TRADER = "adventure/trade_at_world_height";
    /** 1.18's Star Trader: the player at y 319 or above. */
    public static final double STAR_TRADER_Y = 319.0D;

    private static final Field CRITERIA = ReflectionHelper.findField(Advancement.class, "criteria", "field_192080_e");
    private static final Field REQUIREMENTS = ReflectionHelper.findField(Advancement.class, "requirements", "field_192081_f");

    private static Advancement twoByTwo;
    private static Advancement balancedDiet;
    private static boolean failed;

    /** Per player with a trade screen open: its merchant and the total uses of its offers when last looked at. */
    private static final Map<EntityPlayer, Object[]> TRADING = new WeakHashMap<EntityPlayer, Object[]>();

    private VanillaAdvancements() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            check(server);
        }
    }

    /** Changes the loaded Two by Two and A Balanced Diet if they are new; public for the self-test. */
    public static void check(MinecraftServer server) {
        if (failed || server.worlds.length == 0 || server.worlds[0] == null) {
            return;
        }
        Advancement two = server.getAdvancementManager().getAdvancement(TWO_BY_TWO);
        Advancement diet = server.getAdvancementManager().getAdvancement(BALANCED_DIET);
        if (two == twoByTwo && diet == balancedDiet) {
            return;
        }
        boolean changed = false;
        try {
            if (two != null && two != twoByTwo) {
                changed |= bred(two, AXOLOTL_CRITERION, Content.id("axolotl"));
                changed |= bred(two, GOAT_CRITERION, Content.id("goat"));
            }
            if (diet != null && diet != balancedDiet && ForgeRegistries.ITEMS.containsKey(Content.id("glow_berries"))) {
                JsonObject item = new JsonObject();
                item.addProperty("item", Content.id("glow_berries").toString());
                JsonObject conditions = new JsonObject();
                conditions.add("item", item);
                changed |= add(diet, GLOW_BERRIES_CRITERION, CriteriaTriggers.CONSUME_ITEM.deserializeInstance(conditions, null));
            }
        } catch (RuntimeException | IllegalAccessException e) {
            failed = true;
            CavesAndCliffsBackport.LOGGER.warn("Could not add axolotls, goats and glow berries to Two by Two and A Balanced Diet.", e);
            return;
        }
        twoByTwo = two;
        balancedDiet = diet;
        if (changed) {
            // Listeners and progress were built from the old criteria: read them again (from the files).
            server.getPlayerList().reloadResources();
        }
    }

    /** One more bred_animals criterion with a child of this type, when the entity is registered. */
    private static boolean bred(Advancement two, String name, ResourceLocation entity) throws IllegalAccessException {
        if (!ForgeRegistries.ENTITIES.containsKey(entity)) {
            return false;
        }
        JsonObject type = new JsonObject();
        type.addProperty("type", entity.toString());
        JsonObject conditions = new JsonObject();
        conditions.add("child", type);
        return add(two, name, CriteriaTriggers.BRED_ANIMALS.deserializeInstance(conditions, null));
    }

    /** @return true if the criterion was added (false if the advancement already had it) */
    @SuppressWarnings("unchecked")
    private static boolean add(Advancement advancement, String name, ICriterionInstance instance) throws IllegalAccessException {
        Map<String, Criterion> criteria = (Map<String, Criterion>) CRITERIA.get(advancement);
        if (criteria.containsKey(name)) {
            return false;
        }
        Map<String, Criterion> more = new LinkedHashMap<String, Criterion>(criteria);
        more.put(name, new Criterion(instance));
        String[][] requirements = (String[][]) REQUIREMENTS.get(advancement);
        String[][] longer = Arrays.copyOf(requirements, requirements.length + 1);
        longer[requirements.length] = new String[] {name};
        CRITERIA.set(advancement, ImmutableMap.copyOf(more));
        REQUIREMENTS.set(advancement, longer);
        return true;
    }

    // ---- Sound of Music ------------------------------------------------------------------------

    @SubscribeEvent
    public static void jukebox(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || !(event.getItemStack().getItem() instanceof ItemRecord)) {
            return;
        }
        IBlockState state = world.getBlockState(event.getPos());
        if (state.getBlock() == Blocks.JUKEBOX && !state.getValue(BlockJukebox.HAS_RECORD)
                && inMeadow(world, event.getPos())) {
            Advancements.grant(event.getEntityPlayer(), SOUND_OF_MUSIC);
        }
    }

    /** @return whether 1.18's biome here is the meadow (a world with 1.18 terrain only) */
    public static boolean inMeadow(World world, net.minecraft.util.math.BlockPos pos) {
        return com.exiledradio.cavesandcliffsbackport.terrain.ModernTerrain.biomeAt(world, pos.getX(), pos.getY(), pos.getZ())
                == com.exiledradio.cavesandcliffsbackport.terrain.core.Biomes.MEADOW;
    }

    // ---- Star Trader ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayer player = event.player;
        IMerchant merchant = player.openContainer instanceof ContainerMerchant ? merchant((ContainerMerchant) player.openContainer) : null;
        if (merchant == null) {
            TRADING.remove(player);
            return;
        }
        int uses = uses(merchant, player);
        Object[] last = TRADING.get(player);
        TRADING.put(player, new Object[] {merchant, uses});
        if (last != null && last[0] == merchant && uses > (Integer) last[1] && player.posY >= STAR_TRADER_Y
                && merchant instanceof net.minecraft.entity.Entity) {
            Advancements.grant(player, STAR_TRADER);
        }
    }

    private static Field merchantField;

    private static IMerchant merchant(ContainerMerchant container) {
        try {
            if (merchantField == null) {
                merchantField = ReflectionHelper.findField(ContainerMerchant.class, "merchant", "field_75178_e");
            }
            return (IMerchant) merchantField.get(container);
        } catch (RuntimeException | IllegalAccessException e) {
            return null;
        }
    }

    /** @return the total uses of everything this merchant offers the player */
    public static int uses(IMerchant merchant, EntityPlayer player) {
        MerchantRecipeList recipes = merchant.getRecipes(player);
        int total = 0;
        if (recipes != null) {
            for (MerchantRecipe r : recipes) {
                total += r.getToolUses();
            }
        }
        return total;
    }
}
