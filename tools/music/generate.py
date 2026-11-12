"""Stream Mu assets: the Otherside disc, 1.18's biome music events, and the tracks 1.18 added to the game and menu music.

    python tools/music/generate.py

Sounds come from the 1.18 asset index (tools/assets.py). A file 1.12.2 already ships, byte for byte (calm1, hal1,
piano3, ...), is not copied: our sounds.json names it as minecraft:..., which every 1.12.2 game has in its own asset
index.

Three outputs:
  * assets/<modid>/sounds.json: music_disc.otherside and the eight music.overworld.* events (lush and dripstone
    caves, and the six mountain biomes for the terrain stream);
  * <modid>_packs/game_music/assets/minecraft/sounds.json: the tracks 1.18 added to music.game, as an addition to
    1.12.2's own event. It sits outside assets/ so it is served only while the config switch is on
    (music/client/GameMusicPack);
  * <modid>_packs/menu_music.txt: the tracks 1.18 added to music.menu, one sound name and weight per line, for
    Backport Core's menu music pool.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
INDEX_112 = json.load(open(assets.INSTALL + "/assets/indexes/1.12.json", encoding="utf-8"))["objects"]
with open("%s/assets/objects/%s/%s" % (assets.INSTALL, INDEX_112["minecraft/sounds.json"]["hash"][:2],
                                       INDEX_112["minecraft/sounds.json"]["hash"]), encoding="utf-8") as f:
    SOUNDS_112 = json.load(f)
PACKS = os.path.join(assets.ROOT, "src", "main", "resources", M + "_packs")

BIOMES = ("lush_caves", "dripstone_caves", "meadow", "grove", "snowy_slopes", "jagged_peaks", "frozen_peaks",
          "stony_peaks")

copied = {}


def entry_of(sound):
    return dict(sound) if isinstance(sound, dict) else {"name": sound}


def in_112(path):
    key = "minecraft/sounds/%s.ogg" % path
    return key in INDEX_112 and INDEX_112[key]["hash"] == assets.INDEX[key]["hash"]


def port(sound):
    """One sounds.json entry of 1.18's, for 1.12.2: a file 1.12.2 has stays vanilla's, any other is copied to ours."""
    entry = entry_of(sound)
    path = entry["name"].rpartition(":")[2]
    if in_112(path):
        entry["name"] = "minecraft:" + path
    else:
        key = "minecraft/sounds/%s.ogg" % path
        if path not in copied:
            assets.write("sounds/%s.ogg" % path, assets._object(key))
            copied[path] = assets.INDEX[key]["size"]
        entry["name"] = "%s:%s" % (M, path)
    return entry


def names(event, table):
    return [entry_of(s)["name"].rpartition(":")[2] for s in table[event]["sounds"]]


def write_pack(rel, text):
    full = os.path.join(PACKS, *rel.split("/"))
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


# ---- our own events ----
events = {"music_disc.otherside": {"sounds": [port(s) for s in assets.vanilla_sound("music_disc.otherside")["sounds"]]}}
for biome in BIOMES:
    events["music.overworld." + biome] = {
        "sounds": [port(s) for s in assets.vanilla_sound("music.overworld." + biome)["sounds"]]}
assets.merge_sounds("music", events)

# ---- the tracks 1.18 added to 1.12.2's music.game ----
old_game = set(names("music.game", SOUNDS_112))
added = [port(s) for s in assets.vanilla_sound("music.game")["sounds"]
         if entry_of(s)["name"].rpartition(":")[2] not in old_game]
write_pack("game_music/assets/minecraft/sounds.json", json.dumps({"music.game": {"sounds": added}}, indent=2) + "\n")

# ---- the tracks 1.18 added to 1.12.2's music.menu ----
old_menu = set(names("music.menu", SOUNDS_112))
menu = []
for s in assets.vanilla_sound("music.menu")["sounds"]:
    e = entry_of(s)
    if e["name"].rpartition(":")[2] in old_menu:
        continue
    menu.append("%s %d" % (port(s)["name"], e.get("weight", 1)))
write_pack("menu_music.txt", "\n".join(menu) + "\n")

# ---- the disc ----
assets.item_generated("music_disc_otherside", assets.texture("item/music_disc_otherside"))
lines = [
    "item.%s.music_disc_otherside.name=%s" % (M, assets.name("item.minecraft.music_disc_otherside")),
    # ItemRecord's own description key: item.record.<name>.desc
    "item.record.%s.otherside.desc=%s" % (M, assets.name("item.minecraft.music_disc_otherside.desc")),
]
assets.merge_lang("music", sorted(lines) + assets.subtitle_lines(events))

total = sum(copied.values())
print("music: %d sound events, %d game tracks, %d menu tracks, %d files copied, %.1f MB"
      % (len(events), len(added), len(menu), len(copied), total / 1048576.0))
for path in sorted(copied):
    print("  %-40s %6.1f MB" % (path, copied[path] / 1048576.0))
