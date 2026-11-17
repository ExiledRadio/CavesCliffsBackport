package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.deepslate.DeepslateContent;
import com.exiledradio.cavesandcliffsbackport.deepslate.ModdedOres;
import com.exiledradio.cavesandcliffsbackport.deepslate.client.ModdedOreClient;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stream O1's client test: every form's textures were drawn and are in the atlas, every confirmed form is drawn from
 * its ore's own model, and a wall in a superflat world shows the originals (top row) over their deepslate forms (bottom
 * row), with vanilla's iron ore over 1.18's deepslate iron ore on the left for comparison
 * ({@code screenshots/cc_moddedores_grid.png}). Then F3 + T: everything is drawn again.
 *
 * <pre>    ./gradlew runClient -Pclienttest=moddedores</pre>
 */
public final class ModdedOresClientSelfTest {

    private static final String WORLD = "cc_moddedores_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;
    private String shot;

    public static void start(String mode) {
        MinecraftForge.EVENT_BUS.register(new ModdedOresClientSelfTest());
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {} requested", mode);
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.shot != null) {
            Minecraft mc = Minecraft.getMinecraft();
            ScreenShotHelper.saveScreenshot(mc.gameDir, this.shot, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST screenshot {}", this.shot);
            this.shot = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.state == 99) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.wait > 0) {
            this.wait--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST threw", t);
            fail("threw " + t);
            finish(mc);
        }
    }

    private void step(Minecraft mc) {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                checkAssets(mc, "at startup");
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(1718L, GameType.CREATIVE, false, false, WorldType.FLAT));
                this.state = 1;
                return;
            case 1:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                buildWall(mc);
                this.wait = 80;
                this.state = 2;
                return;
            case 2:
                this.shot = "cc_moddedores_grid.png";
                this.wait = 5;
                this.state = 3;
                return;
            case 3:
                // F3 + T.
                mc.refreshResources();
                checkAssets(mc, "after a reload");
                this.wait = 40;
                this.state = 4;
                return;
            case 4:
                this.shot = "cc_moddedores_grid_reloaded.png";
                this.wait = 5;
                this.state = 5;
                return;
            default:
                finish(mc);
        }
    }

    private void checkAssets(Minecraft mc, String when) {
        TextureMap atlas = mc.getTextureMapBlocks();
        TextureAtlasSprite missing = atlas.getMissingSprite();
        for (ModdedOres.Form f : ModdedOres.forms()) {
            TextureAtlasSprite s = atlas.getAtlasSprite(ModdedOreClient.formSprite(f).toString());
            ok(s != missing, when + ": " + f + "'s texture is in the atlas");
            IBakedModel m = mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getModel(new ModelResourceLocation(f.block().getRegistryName(), "normal"));
            ok(m != mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(), when + ": " + f + " has a model");
            ok(mc.getBlockRendererDispatcher().getModelForState(f.block().getDefaultState()) != mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),
                    when + ": " + f + "'s state has a model");
            eq("retextured", ModdedOreClient.baked().get(f.block()), when + ": " + f + " is drawn from its ore's model");
            TextureAtlasSprite particle = m.getParticleTexture();
            ok(!f.confirmed() || particle != null && particle.getIconName().startsWith(CavesAndCliffsBackport.MOD_ID + ":block/deepslate_ores/"),
                    when + ": " + f + "'s particle is a redrawn texture: " + (particle == null ? null : particle.getIconName()));
        }
        for (String vanilla : new String[] {"minecraft:blocks/iron_ore", "minecraft:blocks/diamond_ore", "minecraft:blocks/emerald_ore", "minecraft:blocks/redstone_ore"}) {
            ok(ModdedOreClient.redrawn().containsKey(vanilla), when + ": " + vanilla + " was redrawn on deepslate");
        }
        // Textures with no stone under them (a Nether or sand ore's) are kept; a confirmed form must not use one.
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {}: textures kept as they are: {}", when, ModdedOreClient.kept());
    }

    /** Pairs in rows of eight: each original over its form, vanilla's iron ore over 1.18's deepslate iron ore first. */
    private void buildWall(Minecraft mc) {
        final List<IBlockState[]> pairs = new ArrayList<IBlockState[]>();
        pairs.add(new IBlockState[] {Blocks.IRON_ORE.getDefaultState(), DeepslateContent.BLOCKS.get("deepslate_iron_ore").getDefaultState()});
        for (ModdedOres.Form f : ModdedOres.forms()) {
            pairs.add(new IBlockState[] {f.original, f.block().getDefaultState()});
        }
        final int perRow = 8, rows = (pairs.size() + perRow - 1) / perRow;
        final BlockPos p = new BlockPos(mc.player);
        IntegratedServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            WorldServer w = server.getWorld(0);
            for (int i = 0; i < pairs.size(); i++) {
                int row = i / perRow, col = i % perRow;
                // The player looks south (+z), so east (+x) is on the left: start from the left.
                BlockPos at = p.add(perRow - 1 - 2 * col, 1 + 3 * (rows - 1 - row), 12);
                w.setBlockState(at.up(), pairs.get(i)[0]);
                w.setBlockState(at, pairs.get(i)[1]);
            }
            w.setWorldTime(6000);
            // Moved by the server, which would otherwise set a client-side move back.
            net.minecraft.entity.player.EntityPlayerMP sp = server.getPlayerList().getPlayers().get(0);
            sp.capabilities.isFlying = true;
            sp.sendPlayerAbilities();
            sp.connection.setPlayerLocation(p.getX() + 0.5, p.getY() + 1.5 * rows, p.getZ() + 0.5, 0, 0);
            drops(w, p.add(0, 40, 0));
        });
        mc.player.capabilities.isFlying = true;
        mc.gameSettings.hideGUI = true;
    }

    /**
     * On the server thread: every confirmed form drops what its ore drops, with the same experience, for Fortune 0 to 3
     * and the same random numbers; the names are logged.
     */
    private void drops(WorldServer w, BlockPos at) {
        for (ModdedOres.Form f : ModdedOres.forms()) {
            if (!f.confirmed()) {
                ok(f.item().getCreativeTabs().length == 0, f + " (not an ore) has no creative tab");
                continue;
            }
            int diffs = 0, exp = 0, dropped = 0;
            for (int fortune = 0; fortune <= 3; fortune++) {
                for (long seed = 1; seed <= 20; seed++) {
                    w.rand.setSeed(seed);
                    net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> theirs = net.minecraft.util.NonNullList.create();
                    f.original.getBlock().getDrops(theirs, w, at, f.original, fortune);
                    int theirExp = f.original.getBlock().getExpDrop(f.original, w, at, fortune);
                    w.rand.setSeed(seed);
                    net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> ours = net.minecraft.util.NonNullList.create();
                    f.block().getDrops(ours, w, at, f.block().getDefaultState(), fortune);
                    int ourExp = f.block().getExpDrop(f.block().getDefaultState(), w, at, fortune);
                    diffs += ModdedOresSelfTest.sameDrops(f, theirs, ours) ? 0 : 1;
                    exp += theirExp == ourExp ? 0 : 1;
                    dropped += ours.size();
                }
            }
            eq(0, diffs, f + ": drops differ from the original's");
            eq(0, exp, f + ": experience differs from the original's");
            ok(dropped > 0, f + " drops something");
            net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> one = net.minecraft.util.NonNullList.create();
            f.block().getDrops(one, w, at, f.block().getDefaultState(), 0);
            float h = f.block().getDefaultState().getBlockHardness(w, at);
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST form {} = \"{}\" hardness {} (ore {}) tool {} {} drops {} ores {}", f,
                    f.item().getItemStackDisplayName(new net.minecraft.item.ItemStack(f.item())), h, f.originalHardness,
                    f.block().getHarvestTool(f.block().getDefaultState()), f.block().getHarvestLevel(f.block().getDefaultState()), one, f.oreNames());
            ok(h == Math.max(4.5F, f.originalHardness * 1.5F), f + ": hardness " + h);
        }
    }

    private void ok(boolean condition, String what) {
        if (condition) {
            this.passed++;
        } else {
            fail(what);
        }
    }

    private void eq(Object expected, Object actual, String what) {
        ok(expected == null ? actual == null : expected.equals(actual), what + ": expected " + expected + ", got " + actual);
    }

    private void fail(String what) {
        this.failures.add(what);
        CavesAndCliffsBackport.LOGGER.error("CLIENT SELFTEST FAIL {}", what);
    }

    private void finish(Minecraft mc) {
        this.state = 99;
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", this.passed, this.failures.size());
        try {
            PrintWriter out = new PrintWriter(new File(mc.gameDir, "selftest-client-report.txt"), "UTF-8");
            out.println("pass=" + this.passed + " fail=" + this.failures.size());
            for (String f : this.failures) {
                out.println(f);
            }
            out.close();
        } catch (Exception ignored) {
        }
        mc.shutdown();
    }
}
