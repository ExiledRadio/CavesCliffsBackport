package com.exiledradio.cavesandcliffsbackport.upgrade;

import com.exiledradio.cavesandcliffsbackport.CavesAndCliffsBackport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/**
 * {@code /cavesandcliffs upgradeworld [confirm|cancel|status]}: upgrades this 0-255 world to y -64..319 on its next
 * load, the way 1.18 upgraded old worlds ({@link WorldUpgrade}). Operators of level 4 (the server console, a single
 * player with cheats on).
 */
public final class UpgradeCommand extends CommandBase {

    /** Where level.dat is copied before the world is marked. */
    public static final String BACKUP = "level.dat.before-extended-height";
    private static final long CONFIRM_MILLIS = 60_000L;

    private String askedBy;
    private long askedAt;

    @Override
    public String getName() {
        return "cavesandcliffs";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/cavesandcliffs upgradeworld [confirm|cancel|status]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "upgradeworld");
        }
        if (args.length == 2 && "upgradeworld".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "confirm", "cancel", "status");
        }
        return Collections.emptyList();
    }

    private static void say(ICommandSender sender, TextFormatting color, String text) {
        TextComponentString line = new TextComponentString(text);
        line.getStyle().setColor(color);
        sender.sendMessage(line);
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2 || !"upgradeworld".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        WorldServer overworld = server.getWorld(0);
        File dir = overworld.getSaveHandler().getWorldDirectory();
        String sub = args.length > 1 ? args[1] : "";
        switch (sub) {
            case "status":
                status(sender, overworld, dir);
                return;
            case "cancel":
                if (WorldUpgrade.cancel(dir)) {
                    say(sender, TextFormatting.GREEN, "Upgrade request canceled. The world stays y 0-255.");
                } else {
                    say(sender, TextFormatting.YELLOW, "This world has no upgrade request to cancel.");
                }
                return;
            case "confirm":
                confirm(server, sender, overworld, dir);
                return;
            case "":
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
        String why = refusal(overworld, dir);
        if (why != null) {
            say(sender, TextFormatting.RED, why);
            return;
        }
        say(sender, TextFormatting.GOLD, "Upgrade this world to y -64 to 319, as Minecraft 1.18 upgraded old worlds:");
        say(sender, TextFormatting.WHITE, "- After the next restart, every chunk explored so far keeps its blocks from y 0 "
                + "up. As each one loads it gets deepslate, caves, ores and aquifers below y 0, and its bedrock floor "
                + "moves to y -64.");
        WorldType own = overworld.getWorldInfo().getTerrainType();
        if (WorldUpgrade.hybridType(own)) {
            say(sender, TextFormatting.WHITE, "- New chunks keep " + own.getName() + "'s terrain and structures from y 0 "
                    + "up, and get the same below y 0. The world stays a " + own.getName() + " world.");
        } else {
            say(sender, TextFormatting.WHITE, "- New chunks get the 1.18 terrain, up to y 319, and blend into the old "
                    + "ones at the border. No new structure starts in or reaches into an old chunk.");
        }
        say(sender, TextFormatting.WHITE, "- It can't be undone. Back up the whole world folder first.");
        say(sender, TextFormatting.GOLD, "Type /cavesandcliffs upgradeworld confirm within 60 seconds to go ahead.");
        this.askedBy = sender.getName();
        this.askedAt = System.currentTimeMillis();
    }

    /** Why this world can't be upgraded, or null. */
    @Nullable
    private static String refusal(WorldServer overworld, File dir) {
        if (WorldUpgrade.of(overworld) != null) {
            return "This world was upgraded already: it runs from y -64 to 319.";
        }
        if (WorldUpgrade.extendedNow(overworld)) {
            return "This world has extended height already: it runs from y -64 to 319.";
        }
        if (WorldUpgrade.isPending(dir)) {
            return "This world upgrades on its next load already. /cavesandcliffs upgradeworld cancel undoes that.";
        }
        String why = WorldUpgrade.refusal();
        if (why != null) {
            return "This world can't be upgraded: " + why + ".";
        }
        WorldType type = overworld.getWorldInfo().getTerrainType();
        if (type != WorldType.DEFAULT && !WorldUpgrade.hybridType(type)) {
            return "This world can't be upgraded: its world type is " + type.getName() + ". Only Default worlds can "
                    + "be, as in 1.18, and the world types in the config's height.otherWorldTypes.";
        }
        return null;
    }

    private void confirm(MinecraftServer server, ICommandSender sender, WorldServer overworld, File dir) {
        if (this.askedBy == null || !this.askedBy.equals(sender.getName())
                || System.currentTimeMillis() - this.askedAt > CONFIRM_MILLIS) {
            say(sender, TextFormatting.YELLOW, "Type /cavesandcliffs upgradeworld first, then confirm within 60 seconds.");
            return;
        }
        this.askedBy = null;
        String why = refusal(overworld, dir);
        if (why != null) {
            say(sender, TextFormatting.RED, why);
            return;
        }
        // level.dat as it is now, then a copy of it.
        server.saveAllWorlds(false);
        File level = new File(dir, "level.dat");
        try {
            Files.copy(level.toPath(), new File(dir, BACKUP).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CavesAndCliffsBackport.LOGGER.error("World upgrade: could not back up {}.", level, e);
            say(sender, TextFormatting.RED, "Could not back up level.dat (" + e.getMessage() + "); nothing was changed.");
            return;
        }
        if (!WorldUpgrade.markPending(dir)) {
            say(sender, TextFormatting.RED, "Could not mark the world for the upgrade; nothing was changed.");
            return;
        }
        CavesAndCliffsBackport.LOGGER.info("World upgrade to y -64..319 requested by {}; it happens on the next load.",
                sender.getName());
        say(sender, TextFormatting.GREEN, "Done: the world upgrades on its next load. level.dat was backed up as "
                + BACKUP + ".");
        say(sender, TextFormatting.WHITE, "Back up the whole world folder now if you haven't: the region files change "
                + "as old chunks load after the upgrade.");
        say(sender, TextFormatting.WHITE, "Then restart the server (in single player: Save and Quit to Title, then open "
                + "the world again). Until then, /cavesandcliffs upgradeworld cancel undoes this.");
    }

    private static void status(ICommandSender sender, WorldServer overworld, File dir) {
        WorldUpgrade up = WorldUpgrade.of(overworld);
        if (up != null) {
            say(sender, TextFormatting.WHITE, "Upgraded world: " + up.doneCount() + " of " + up.oldCount()
                    + " old chunks have their part below y 0 so far (each gets it when it first loads).");
        } else if (WorldUpgrade.isPending(dir)) {
            String why = WorldUpgrade.refusal();
            say(sender, TextFormatting.WHITE, "This world upgrades on its next load." + (why == null ? ""
                    : " It can't in this game as it is: " + why + "."));
        } else if (WorldUpgrade.extendedNow(overworld)) {
            say(sender, TextFormatting.WHITE, "This world has extended height (y -64 to 319).");
        } else {
            say(sender, TextFormatting.WHITE, "This world is y 0-255. /cavesandcliffs upgradeworld upgrades it.");
        }
    }
}
