package com.exiledradio.cavesandcliffsbackport.selftest;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import com.exiledradio.cavesandcliffsbackport.lush.LushContent;
import com.exiledradio.cavesandcliffsbackport.powdersnow.PowderSnowContent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The particle textures of every block and item of the mod, on the client: for every block state, the model's
 * particle sprite ({@code BlockModelShapes.getTexture}, what break particles and FancyBlockParticles draw) and
 * every quad's sprite are stitched sprites of the block atlas, and for every item the same of its model. Then,
 * in a superflat world, breaking and hitting moss carpet and powder snow spawn particles whose sprite is not the
 * missing one. Writes {@code selftest-client-report.txt} and quits.
 *
 * <pre>    ./gradlew runClient -Pselftest=particles -Pclienttest=particles</pre>
 */
public final class ParticleClientSelfTest {

    private static final String WORLD = "cc_particles_selftest";

    private final List<String> failures = new ArrayList<String>();
    private int passed;
    private int state;
    private int wait;

    public static void start(String mode) {
        MinecraftForge.EVENT_BUS.register(new ParticleClientSelfTest());
        CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST {} requested", mode);
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

    private void step(Minecraft mc) throws Exception {
        switch (this.state) {
            case 0:
                if (!(mc.currentScreen instanceof GuiMainMenu)) {
                    return;
                }
                mc.gameSettings.pauseOnLostFocus = false;
                this.wait = 20;
                this.state = 1;
                return;
            case 1:
                models(mc);
                mc.getSaveLoader().deleteWorldDirectory(WORLD);
                mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(1718L, GameType.CREATIVE, false, false, WorldType.FLAT));
                this.state = 2;
                return;
            case 2:
                if (mc.world == null || mc.player == null || mc.getIntegratedServer() == null) {
                    return;
                }
                this.wait = 40;
                this.state = 3;
                return;
            case 3:
                breaking(mc);
                finish(mc);
                return;
            default:
        }
    }

    /** Every state's and item's model sprites are stitched sprites of the block atlas. */
    private void models(Minecraft mc) {
        TextureMap atlas = mc.getTextureMapBlocks();
        int states = 0;
        for (Block block : Block.REGISTRY) {
            if (!CavesAndCliffsBackport.MOD_ID.equals(block.getRegistryName().getNamespace())) {
                continue;
            }
            for (IBlockState s : block.getBlockState().getValidStates()) {
                states++;
                String where = s.toString();
                IBakedModel model = mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(s);
                sprite(atlas, mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(s), where + " particle");
                quads(atlas, model, s, where);
            }
        }
        ok(states > 100, "states checked: " + states);
        int items = 0;
        for (Item item : Item.REGISTRY) {
            if (!CavesAndCliffsBackport.MOD_ID.equals(item.getRegistryName().getNamespace())) {
                continue;
            }
            NonNullList<ItemStack> stacks = NonNullList.create();
            item.getSubItems(net.minecraft.creativetab.CreativeTabs.SEARCH, stacks);
            if (stacks.isEmpty()) {
                stacks.add(new ItemStack(item));
            }
            for (ItemStack stack : stacks) {
                items++;
                IBakedModel model = mc.getRenderItem().getItemModelWithOverrides(stack, null, null);
                String where = "item " + item.getRegistryName() + "@" + stack.getMetadata();
                sprite(atlas, model.getParticleTexture(), where + " particle");
                quads(atlas, model, null, where);
            }
        }
        ok(items > 50, "items checked: " + items);
    }

    private void quads(TextureMap atlas, IBakedModel model, IBlockState s, String where) {
        for (EnumFacing side : sidesAndNull()) {
            for (BakedQuad q : model.getQuads(s, side, 0L)) {
                sprite(atlas, q.getSprite(), where + " quad " + side);
            }
        }
    }

    private static EnumFacing[] sidesAndNull() {
        EnumFacing[] all = new EnumFacing[7];
        System.arraycopy(EnumFacing.values(), 0, all, 0, 6);
        return all;
    }

    private void sprite(TextureMap atlas, TextureAtlasSprite sprite, String where) {
        if (sprite == null) {
            fail(where + ": no sprite");
            return;
        }
        String name = sprite.getIconName();
        if (sprite == atlas.getMissingSprite() || "missingno".equals(name)) {
            fail(where + ": the missing sprite");
        } else if (atlas.getTextureExtry(name) != sprite) {
            fail(where + ": " + name + " is not the atlas's sprite of that name (not stitched, or from an older atlas)");
        } else if (!(sprite.getMaxU() > sprite.getMinU()) || !(sprite.getMaxV() > sprite.getMinV())) {
            fail(where + ": " + name + " has empty UVs");
        } else {
            this.passed++;
        }
    }

    /** Breaking and hitting moss carpet and powder snow spawn particles with stitched, non-missing sprites. */
    private void breaking(Minecraft mc) throws Exception {
        ParticleManager pm = mc.effectRenderer;
        Field texture = null;
        for (Field f : Particle.class.getDeclaredFields()) {
            if (f.getType() == TextureAtlasSprite.class) {
                f.setAccessible(true);
                texture = f;
            }
        }
        BlockPos base = new BlockPos(mc.player).add(3, 0, 0);
        List<IBlockState> tested = new ArrayList<IBlockState>();
        if (LushContent.enabled()) {
            tested.add(LushContent.mossCarpet.getDefaultState());
            tested.add(LushContent.moss.getDefaultState());
        }
        if (PowderSnowContent.enabled()) {
            tested.add(PowderSnowContent.powderSnow.getDefaultState());
        }
        tested.add(Blocks.CARPET.getDefaultState());
        for (IBlockState s : tested) {
            mc.world.setBlockState(base, s, 0);
            pm.clearEffects(mc.world);
            pm.addBlockDestroyEffects(base, s);
            pm.addBlockHitEffects(base, EnumFacing.UP);
            pm.addBlockHitEffects(base, EnumFacing.NORTH);
            List<Particle> all = particles(pm);
            int bad = 0, n = 0;
            String sample = null;
            for (Particle p : all) {
                TextureAtlasSprite t = (TextureAtlasSprite) texture.get(p);
                if (t == null) {
                    continue;
                }
                n++;
                if (t == mc.getTextureMapBlocks().getMissingSprite() || mc.getTextureMapBlocks().getTextureExtry(t.getIconName()) != t) {
                    bad++;
                    sample = t.getIconName() + " (" + p.getClass().getName() + ")";
                }
            }
            CavesAndCliffsBackport.LOGGER.info("CLIENT SELFTEST particles of {}: {} with a sprite, {} bad, manager {}", s, n, bad, pm.getClass().getName());
            ok(n > 0, s + " breaking spawns block particles: " + n);
            ok(bad == 0, s + " particles have stitched sprites: " + bad + " bad, e.g. " + sample);
            mc.world.setBlockState(base, Blocks.AIR.getDefaultState(), 0);
        }
    }

    /** Every particle the manager holds: its queue of new ones and its layers (found by type, any mapping). */
    private static List<Particle> particles(ParticleManager pm) throws IllegalAccessException {
        List<Particle> all = new ArrayList<Particle>();
        for (Field f : ParticleManager.class.getDeclaredFields()) {
            f.setAccessible(true);
            Object v = f.get(pm);
            if (v instanceof Collection) {
                for (Object o : (Collection<?>) v) {
                    if (o instanceof Particle) {
                        all.add((Particle) o);
                    }
                }
            } else if (v instanceof ArrayDeque[][]) {
                for (ArrayDeque<?>[] l : (ArrayDeque<?>[][]) v) {
                    for (ArrayDeque<?> d : l) {
                        for (Object o : d) {
                            if (o instanceof Particle) {
                                all.add((Particle) o);
                            }
                        }
                    }
                }
            }
        }
        return all;
    }

    private void ok(boolean c, String what) {
        if (c) {
            this.passed++;
        } else {
            fail(what);
        }
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
