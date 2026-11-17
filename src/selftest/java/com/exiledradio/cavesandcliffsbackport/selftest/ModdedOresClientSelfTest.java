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
            ok(particle != null && particle.getIconName().startsWith(CavesAndCliffsBackport.MOD_ID + ":block/deepslate_ores/"),
                    when + ": " + f + "'s particle is a redrawn texture: " + (particle == null ? null : particle.getIconName()));
        }
        for (String vanilla : new String[] {"minecraft:blocks/iron_ore", "minecraft:blocks/diamond_ore", "minecraft:blocks/emerald_ore", "minecraft:blocks/redstone_ore"}) {
            ok(ModdedOreClient.redrawn().containsKey(vanilla), when + ": " + vanilla + " was redrawn on deepslate");
        }
        eq(0, ModdedOreClient.kept().size(), when + ": textures kept as they are: " + ModdedOreClient.kept());
    }

    /** Originals on top, forms below, from x+2 eastward, facing the player. */
    private void buildWall(Minecraft mc) {
        final List<IBlockState[]> pairs = new ArrayList<IBlockState[]>();
        pairs.add(new IBlockState[] {Blocks.IRON_ORE.getDefaultState(), DeepslateContent.BLOCKS.get("deepslate_iron_ore").getDefaultState()});
        for (ModdedOres.Form f : ModdedOres.forms()) {
            pairs.add(new IBlockState[] {f.original, f.block().getDefaultState()});
        }
        final BlockPos p = new BlockPos(mc.player);
        IntegratedServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            WorldServer w = server.getWorld(0);
            for (int i = 0; i < pairs.size(); i++) {
                BlockPos col = p.add(-pairs.size() + 1 + 2 * i, 0, 4);
                w.setBlockState(col.up(2), pairs.get(i)[0]);
                w.setBlockState(col.up(1), pairs.get(i)[1]);
            }
            w.setWorldTime(6000);
        });
        mc.player.setPositionAndRotation(p.getX() + 0.5, p.getY(), p.getZ() + 0.5 - 3, 0, 0);
        mc.player.rotationYaw = 0;
        mc.player.rotationPitch = 0;
        mc.player.capabilities.isFlying = true;
        mc.gameSettings.hideGUI = true;
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
