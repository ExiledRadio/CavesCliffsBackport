"""Stream L assets: moss, azaleas, rooted dirt, hanging roots, spore blossom, cave vines, glow berries, dripleaf,
glow lichen, their sounds.

    python tools/lush/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []


def block_name(ours, vanilla=None):
    lang.append("tile.%s.%s.name=%s" % (M, ours, assets.name("block.minecraft." + (vanilla or ours))))


# Blocks whose 1.18 blockstate file fits as it is.
for n in ("moss_block", "moss_carpet", "azalea", "flowering_azalea", "rooted_dirt", "hanging_roots", "spore_blossom",
          "potted_azalea_bush", "potted_flowering_azalea_bush", "cave_vines_plant", "big_dripleaf", "big_dripleaf_stem"):
    assets.port_blockstate(n)
    block_name(n)

# Leaves: 1.12's two decay properties, all four drawn the same.
for n in ("azalea_leaves", "flowering_azalea_leaves"):
    model = {"model": assets.port_model("block/" + n).replace(":block/", ":")}
    assets.write_json("blockstates/%s.json" % n, {"variants": {
        "check_decay=%s,decayable=%s" % (c, d): model for c in ("false", "true") for d in ("false", "true")}})
    block_name(n)

# The cave vine tip: its age (0-7 here) does not show.
tip = assets.jar_json("assets/minecraft/blockstates/cave_vines.json")["variants"]
assets.write_json("blockstates/cave_vines.json", {"variants": {
    "age=%d,berries=%s" % (age, b): {"model": assets.port_model(tip["berries=" + b]["model"].split(":")[-1]).replace(":block/", ":")}
    for age in range(8) for b in ("false", "true")}})
block_name("cave_vines")

# Small dripleaf: 1.18's half=lower|upper is our upper=false|true.
small = {}
for f in ("north", "east", "south", "west"):
    small["facing=%s,half=lower" % f] = "facing=%s,upper=false" % f
    small["facing=%s,half=upper" % f] = "facing=%s,upper=true" % f
assets.port_blockstate("small_dripleaf", variants=small)
block_name("small_dripleaf")

# Glow lichen: four blocks (none, down, up, up and down), each with its four side faces as properties.
lichen = assets.port_model("block/glow_lichen").replace(":block/", ":")
SIDES = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}
UP, DOWN = {"x": 270}, {"x": 90}


def part(turn, when=None):
    apply = dict(model=lichen, **turn)
    if turn:
        apply["uvlock"] = True
    p = {"apply": apply}
    if when:
        p["when"] = when
    return p


for n, extra in (("glow_lichen", []), ("glow_lichen_down", [DOWN]), ("glow_lichen_up", [UP]),
                 ("glow_lichen_up_down", [UP, DOWN])):
    parts = [part(turn, {side: "true"}) for side, turn in SIDES.items()] + [part(t) for t in extra]
    if not extra:
        # The state with no face at all is never placed, but every state must draw something.
        parts.append(part({}, {side: "false" for side in SIDES}))
    assets.write_json("blockstates/%s.json" % n, {"multipart": parts})
    block_name(n, "glow_lichen")

for n in ("moss_block", "moss_carpet", "azalea", "flowering_azalea", "azalea_leaves", "flowering_azalea_leaves",
          "rooted_dirt", "hanging_roots", "spore_blossom", "big_dripleaf", "small_dripleaf", "glow_lichen"):
    assets.port_item_model(n)
assets.port_item_model("glow_berries")
lang.append("item.%s.glow_berries.name=%s" % (M, assets.name("item.minecraft.glow_berries")))

events = {}
for group in ("block.moss", "block.moss_carpet", "block.azalea", "block.flowering_azalea", "block.azalea_leaves",
              "block.rooted_dirt", "block.hanging_roots", "block.spore_blossom", "block.cave_vines", "block.big_dripleaf",
              "block.small_dripleaf"):
    assets.copy_sound_group(group, events)
for e in ("block.cave_vines.pick_berries", "block.big_dripleaf.tilt_down", "block.big_dripleaf.tilt_up",
          "block.growing_plant.crop"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("lush", events)
assets.merge_lang("lush", sorted(lang) + assets.subtitle_lines(events))
print("lush: %d lang lines, %d sound events" % (len(lang), len(events)))
