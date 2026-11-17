package com.exiledradio.cavesandcliffsbackport.deepslate;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.ModConfig;
import com.exiledradio.cavesandcliffsbackport.common.ModSounds;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Stream O1: deepslate forms of every other mod's stone ores.
 *
 * <h2>Finding them</h2>
 * Candidates are picked at the last moment block registration allows - this mod's {@code Register<Block>} handler at
 * LOWEST priority, after every mod's own - by registry name (an "ore" word in it) and by state (rock, a full opaque
 * cube, breakable, no tile entity), one per metadata. Nether and End ores (by name) and this family's own ores are left
 * out, as are those in {@code deepslate.moddedOresExclude}. Every candidate gets a block and an item, since registration
 * can't wait; it becomes a <em>confirmed</em> ore once the ore dictionary lists it under an {@code ore*} name. An
 * unconfirmed candidate is never generated, has no creative tab and so is not in JEI either.
 *
 * <h2>What the form is</h2>
 * {@link BlockModdedDeepslateOre}: 1.18's deepslate ore rules (hardness 1.5 times the stone ore's, at least 4.5; same
 * harvest tool and level, drops, experience and Fortune; deepslate's sound), plus the original's ore dictionary names
 * and smelting. Its texture is made on the client ({@code deepslate.client.ModdedOreTextures}).
 *
 * <h2>Where it is placed</h2>
 * {@link DeepslateLayer} (Default worlds) swaps a confirmed ore in the deepslate layer for its form. Caves &amp; Cliffs
 * worlds ask {@link #deepslateForm(IBlockState)} where they place a modded ore in deepslate.
 *
 * <p>The list goes to the log and to {@code config/cavesandcliffsbackport-modded-ores.txt}.
 */
@Mod.EventBusSubscriber(modid = CavesAndCliffsBackport.MOD_ID)
public final class ModdedOres {

    /** Registry paths of the forms start with this: {@code deepslate/<modid>/<name>[_<meta>]}. */
    public static final String PREFIX = "deepslate/";

    /** "Deepslate %s" in the current language; the original's name goes in. */
    public static final String FORMAT_KEY = "gui." + CavesAndCliffsBackport.MOD_ID + ".deepslate_ore";
    /** "true" in languages that put the original's name after a word of their own in lower case (Dutch, Polish, ...). */
    public static final String LOWERCASE_KEY = FORMAT_KEY + ".lowercase";

    /** An "ore" word in a registry path: ore, ores, ore_copper, copper_ore, oreCopper (lowered), copperore. */
    private static final Pattern ORE_WORD = Pattern.compile("(^|[_./-])ores?($|[_./-])|^ore[a-z]|[a-z]ores?$");
    /** Words ending in "ore" that are not ores. */
    private static final Pattern NOT_ORE = Pattern.compile("(core|store|spore|shore|score|more|chore|snore|swore|bore|"
            + "lore|fore|adore|ignore|explore|restore|sycamore|hardcore|folklore|semaphore|sophomore)s?($|[_./-])");
    /** Nether and End ores, by registry path or ore dictionary name. */
    private static final Pattern NETHER_END = Pattern.compile("nether|hell|(^|[_./-])end($|[_./-])|endstone|end_stone|theend|"
            + "(^|[_./-])ender_?ore|^oreend|^oreender");

    /**
     * Nether and End ores by ore dictionary name or translation key, where words run together (oreClathrateEnder,
     * oreNetherCopper): their hosts, and glowstone, which only the Nether has.
     */
    private static final Pattern NETHER_END_NAME = Pattern.compile("nether|hell|ender|endstone|end_stone|glowstone|soulsand|soul_sand");

    /** A candidate: one metadata of another mod's stone ore, and its deepslate form. */
    public static final class Form {
        public final ResourceLocation originalName;
        public final int meta;
        public final IBlockState original;
        public final float originalHardness;
        BlockModdedDeepslateOre block;
        ItemModdedDeepslateOre item;
        /** Listed under an ore* name in the ore dictionary. */
        volatile boolean confirmed;
        /** Listed under a Nether or End ore name: never confirmed. */
        boolean netherOrEnd;
        /** Mined with something other than a pickaxe: a sand or gravel host (oil sand), which deepslate is not. */
        boolean notStone;
        final Set<String> oreNames = new TreeSet<String>();
        private final Set<String> registeredNames = new HashSet<String>();
        boolean smelting;

        Form(ResourceLocation originalName, int meta, IBlockState original, float originalHardness) {
            this.originalName = originalName;
            this.meta = meta;
            this.original = original;
            this.originalHardness = originalHardness;
        }

        public BlockModdedDeepslateOre block() {
            return this.block;
        }

        public ItemModdedDeepslateOre item() {
            return this.item;
        }

        public boolean confirmed() {
            return this.confirmed;
        }

        public Set<String> oreNames() {
            return Collections.unmodifiableSet(this.oreNames);
        }

        Item originalItem() {
            return Item.getItemFromBlock(this.original.getBlock());
        }

        /** The original's item metadata: the block's metadata when the item has subtypes. */
        int originalItemMeta() {
            Item i = originalItem();
            return i.getHasSubtypes() ? this.meta : 0;
        }

        /** @return the original ore as an item, or empty if it has none */
        public ItemStack originalStack() {
            Item i = originalItem();
            return i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i, 1, originalItemMeta());
        }

        /** A drop that is the original ore itself becomes this form, as a deepslate ore drops itself. */
        ItemStack swapSelf(ItemStack s) {
            Item i = originalItem();
            if (!s.isEmpty() && i != Items.AIR && s.getItem() == i && (!i.getHasSubtypes() || s.getMetadata() == originalItemMeta())) {
                return new ItemStack(this.item, s.getCount());
            }
            return s;
        }

        /** "Deepslate " + the original's name, in the current language (on a server, English). */
        @SuppressWarnings("deprecation")
        String displayName() {
            ItemStack o = originalStack();
            String name;
            try {
                name = o.isEmpty() ? this.original.getBlock().getLocalizedName() : o.getDisplayName();
            } catch (RuntimeException e) {
                name = this.originalName.getPath();
            }
            if (net.minecraft.util.text.translation.I18n.canTranslate(LOWERCASE_KEY)
                    && "true".equals(net.minecraft.util.text.translation.I18n.translateToLocal(LOWERCASE_KEY).trim())
                    && !name.isEmpty()) {
                int first = name.codePointAt(0);
                name = new String(Character.toChars(Character.toLowerCase(first))) + name.substring(Character.charCount(first));
            }
            if (!net.minecraft.util.text.translation.I18n.canTranslate(FORMAT_KEY)) {
                return "Deepslate " + name;
            }
            return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(FORMAT_KEY, name);
        }

        @Override
        public String toString() {
            return this.originalName + (this.meta != 0 ? ":" + this.meta : "");
        }
    }

    private static final List<Form> FORMS = new ArrayList<Form>();
    private static final Map<Block, List<Form>> BY_BLOCK = new IdentityHashMap<Block, List<Form>>();
    private static final Map<IBlockState, Form> BY_STATE = new HashMap<IBlockState, Form>();
    private static final Map<Block, Form> BY_FORM = new IdentityHashMap<Block, Form>();
    /** Left out, with why, for the report. */
    private static final Map<String, String> LEFT_OUT = new java.util.TreeMap<String, String>();

    private ModdedOres() {
    }

    public static boolean enabled() {
        return DeepslateContent.enabled() && ModConfig.deepslate.moddedOres;
    }

    /** @return every candidate, confirmed or not, in registration order */
    public static List<Form> forms() {
        return Collections.unmodifiableList(FORMS);
    }

    /** @return the form a block is, or null if it is not one */
    @Nullable
    public static Form form(Block block) {
        return BY_FORM.get(block);
    }

    public static boolean isForm(Block block) {
        return BY_FORM.containsKey(block);
    }

    /**
     * The lookup for whatever places ores: the deepslate form of a stone ore state.
     *
     * @return the deepslate form's state, or null when the state is not another mod's confirmed stone ore (or the
     *         setting is off)
     */
    @Nullable
    public static IBlockState deepslateForm(IBlockState stoneOre) {
        Form f = BY_STATE.get(stoneOre);
        return f != null && f.confirmed ? f.block.getDefaultState() : null;
    }

    /** Adds every confirmed ore's states to {@link DeepslateLayer}'s swaps. */
    static void addSwaps(Map<IBlockState, IBlockState> swaps) {
        for (Map.Entry<IBlockState, Form> e : BY_STATE.entrySet()) {
            if (e.getValue().confirmed && !swaps.containsKey(e.getKey())) {
                swaps.put(e.getKey(), e.getValue().block.getDefaultState());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Finding candidates

    static boolean familyNamespace(String ns) {
        return "minecraft".equals(ns) || CavesAndCliffsBackport.MOD_ID.equals(ns) || ns.endsWith("backport")
                || ns.startsWith("backportcore");
    }

    static boolean oreName(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return ORE_WORD.matcher(p).find() && !NOT_ORE.matcher(p).find();
    }

    static boolean netherOrEnd(String name) {
        return NETHER_END.matcher(name.toLowerCase(Locale.ROOT)).find();
    }

    private static boolean excluded(Set<String> exclude, ResourceLocation name, int meta) {
        return exclude.contains(name.getNamespace() + ":*") || exclude.contains(name.toString())
                || exclude.contains(name + ":" + meta);
    }

    private static float field(Block b, String mcp, String srg) {
        try {
            return ReflectionHelper.findField(Block.class, mcp, srg).getFloat(b);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Float.NaN;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) {
            return;
        }
        IForgeRegistry<Block> r = event.getRegistry();
        Set<String> exclude = new HashSet<String>();
        for (String s : ModConfig.deepslate.moddedOresExclude) {
            exclude.add(s.trim().toLowerCase(Locale.ROOT));
        }
        SoundType sound = ModSounds.type("block.deepslate");
        for (Block b : new ArrayList<Block>(r.getValuesCollection())) {
            ResourceLocation name = b.getRegistryName();
            if (name == null || familyNamespace(name.getNamespace()) || !oreName(name.getPath())) {
                continue;
            }
            if (netherOrEnd(name.getPath())) {
                LEFT_OUT.put(name.toString(), "Nether or End ore");
                continue;
            }
            Set<Integer> metas = new TreeSet<Integer>();
            for (IBlockState s : b.getBlockState().getValidStates()) {
                try {
                    metas.add(b.getMetaFromState(s));
                } catch (RuntimeException ignored) {
                    // A state the block can't save: never generated as an ore either.
                }
            }
            float hardness = field(b, "blockHardness", "field_149782_v");
            float resistance = field(b, "blockResistance", "field_149781_w");
            for (int meta : metas) {
                IBlockState st;
                try {
                    st = b.getStateFromMeta(meta);
                    if (st.getBlock() != b || st.getMaterial() != Material.ROCK || !st.isOpaqueCube() || !st.isFullCube()
                            || b.hasTileEntity(st) || !(hardness >= 0)) {
                        continue;
                    }
                } catch (RuntimeException e) {
                    continue;
                }
                if (excluded(exclude, name, meta)) {
                    LEFT_OUT.put(name + (meta != 0 ? ":" + meta : ""), "excluded by deepslate.moddedOresExclude");
                    continue;
                }
                Form f = new Form(name, meta, st, hardness);
                // 1.18: deepslate ores are 4.5 against stone ores' 3, so 1.5 times; never below 4.5. Blast resistance stays.
                float ours = Math.max(4.5F, hardness * 1.5F);
                float res = resistance >= 0 ? resistance / 3.0F : 3.0F;
                BlockModdedDeepslateOre block = new BlockModdedDeepslateOre(f, sound, ours, res);
                f.block = block;
                String path = PREFIX + name.getNamespace() + "/" + name.getPath() + (meta != 0 ? "_" + meta : "");
                block.setRegistryName(new ResourceLocation(CavesAndCliffsBackport.MOD_ID, path));
                block.setTranslationKey(CavesAndCliffsBackport.MOD_ID + ".deepslate_ore");
                r.register(block);
                FORMS.add(f);
                BY_FORM.put(block, f);
                List<Form> list = BY_BLOCK.get(b);
                if (list == null) {
                    list = new ArrayList<Form>();
                    BY_BLOCK.put(b, list);
                }
                list.add(f);
                for (IBlockState s : b.getBlockState().getValidStates()) {
                    try {
                        if (b.getMetaFromState(s) == meta) {
                            BY_STATE.put(s, f);
                        }
                    } catch (RuntimeException ignored) {
                        // As above.
                    }
                }
            }
        }
        CavesAndCliffsBackport.LOGGER.info("Modded ores: {} candidates for deepslate forms.", FORMS.size());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerItems(RegistryEvent.Register<Item> event) {
        if (FORMS.isEmpty()) {
            return;
        }
        IForgeRegistry<Item> r = event.getRegistry();
        for (Form f : FORMS) {
            f.item = new ItemModdedDeepslateOre(f.block);
            f.item.setRegistryName(f.block.getRegistryName());
            r.register(f.item);
        }
        sweepOreDictionary();
    }

    // ---------------------------------------------------------------------------------------------
    // Confirming them, ore dictionary and smelting

    @SubscribeEvent
    public static void oreRegistered(OreDictionary.OreRegisterEvent event) {
        if (!FORMS.isEmpty()) {
            consider(event.getName(), event.getOre());
        }
    }

    private static void sweepOreDictionary() {
        for (String name : OreDictionary.getOreNames()) {
            if (name.startsWith("ore")) {
                for (ItemStack s : new ArrayList<ItemStack>(OreDictionary.getOres(name, false))) {
                    consider(name, s);
                }
            }
        }
    }

    private static void consider(String name, ItemStack stack) {
        if (!name.startsWith("ore") || stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
            return;
        }
        List<Form> forms = BY_BLOCK.get(((ItemBlock) stack.getItem()).getBlock());
        if (forms == null) {
            return;
        }
        boolean wild = stack.getMetadata() == OreDictionary.WILDCARD_VALUE;
        int meta = wild ? -1 : stack.getItem().getMetadata(stack.getMetadata());
        boolean netherEnd = netherOrEnd(name) || NETHER_END_NAME.matcher(name.toLowerCase(Locale.ROOT)).find();
        for (Form f : forms) {
            if (!wild && f.meta != meta) {
                continue;
            }
            if (netherEnd || netherOrEndKey(f)) {
                f.netherOrEnd = true;
                f.confirmed = false;
                continue;
            }
            if (f.netherOrEnd) {
                continue;
            }
            if (!stoneHost(f)) {
                f.notStone = true;
                f.confirmed = false;
                continue;
            }
            f.confirmed = true;
            f.oreNames.add(name);
            if (f.item != null && f.registeredNames.add(name)) {
                OreDictionary.registerOre(name, new ItemStack(f.item));
            }
        }
    }

    /** The original is mined with a pickaxe (or with no tool at all), as an ore in stone is. */
    private static boolean stoneHost(Form f) {
        try {
            String tool = f.original.getBlock().getHarvestTool(f.original);
            return tool == null || "pickaxe".equals(tool);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean netherOrEndKey(Form f) {
        try {
            ItemStack o = f.originalStack();
            String key = o.isEmpty() ? f.original.getBlock().getTranslationKey() : o.getTranslationKey();
            return NETHER_END_NAME.matcher(key.toLowerCase(Locale.ROOT)).find();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** From post-init and load-complete: every late ore dictionary name, and the smelting results. */
    public static void postInit() {
        if (FORMS.isEmpty()) {
            return;
        }
        sweepOreDictionary();
        // A harvest tool set in init or later.
        for (Form f : FORMS) {
            if (f.confirmed && !stoneHost(f)) {
                f.notStone = true;
                f.confirmed = false;
            }
        }
        FurnaceRecipes furnace = FurnaceRecipes.instance();
        for (Form f : FORMS) {
            if (f.smelting || !f.confirmed) {
                continue;
            }
            ItemStack o = f.originalStack();
            ItemStack result = o.isEmpty() ? ItemStack.EMPTY : furnace.getSmeltingResult(o);
            if (!result.isEmpty() && furnace.getSmeltingResult(new ItemStack(f.item)).isEmpty()) {
                furnace.addSmeltingRecipe(new ItemStack(f.item), result.copy(), furnace.getSmeltingExperience(result));
                f.smelting = true;
            }
        }
    }

    /** From load-complete: the last ore dictionary names and smelting, then the list for the log and the report. */
    public static void loadComplete() {
        if (!enabled()) {
            return;
        }
        postInit();
        List<String> ores = new ArrayList<String>();
        for (Form f : FORMS) {
            if (f.confirmed) {
                ores.add(f.toString());
            }
        }
        CavesAndCliffsBackport.LOGGER.info("Modded ores with deepslate forms ({}): {}", ores.size(), ores);
        try {
            writeReport(new File(Loader.instance().getConfigDir(), CavesAndCliffsBackport.MOD_ID + "-modded-ores.txt"));
        } catch (Exception e) {
            CavesAndCliffsBackport.LOGGER.warn("Could not write the modded ore report.", e);
        }
    }

    private static void writeReport(File file) throws Exception {
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new java.io.FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            out.println("# Caves & Cliffs Backport: other mods' ores and their deepslate forms.");
            out.println("# Written at every game start. Leave an ore out with deepslate.moddedOresExclude (modid:name, modid:name:meta or modid:*).");
            out.println("#");
            out.println("# Deepslate forms (generated in deepslate):");
            for (Form f : FORMS) {
                if (f.confirmed) {
                    out.println(f + "\t-> " + f.block.getRegistryName() + "\thardness " + f.block.getBlockHardness(f.block.getDefaultState(), null, null)
                            + "\t" + String.join(" ", f.oreNames) + (f.smelting ? "\tsmelts" : ""));
                }
            }
            out.println();
            out.println("# Candidates that are not ores (no ore* ore dictionary name, a Nether or End one, or not mined with a pickaxe): never generated, hidden.");
            for (Form f : FORMS) {
                if (!f.confirmed) {
                    out.println(f + "\t-> " + f.block.getRegistryName() + (f.netherOrEnd ? "\tNether or End ore"
                            : f.notStone ? "\tnot mined with a pickaxe (a sand or gravel ore)" : "\tnot in the ore dictionary"));
                }
            }
            out.println();
            out.println("# Left out:");
            for (Map.Entry<String, String> e : LEFT_OUT.entrySet()) {
                out.println(e.getKey() + "\t" + e.getValue());
            }
        } finally {
            out.close();
        }
    }

    /** Every original ore block that has at least one form, for the client. */
    public static Set<Block> originals() {
        return new LinkedHashSet<Block>(BY_BLOCK.keySet());
    }

    /** For tests: the reason an ore was left out, or null. */
    @Nullable
    public static String leftOut(String name) {
        return LEFT_OUT.get(name);
    }
}
