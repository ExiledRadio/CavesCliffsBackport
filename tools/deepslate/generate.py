"""Stream D assets: deepslate and its sets, calcite, tuff, smooth basalt, deepslate ores, raw iron and gold.

    python tools/deepslate/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
SETS = ["cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile"]
ORES = ["coal", "iron", "gold", "redstone", "lapis", "diamond", "emerald"]


def full(s):
    return s + "s" if s.endswith(("_brick", "_tile")) else s


lang = []


def block(name, item=True):
    assets.port_blockstate(name)
    if item:
        assets.port_item_model(name)
    lang.append("tile.%s.%s.name=%s" % (M, name, assets.name("block.minecraft." + name)))


plain = ["deepslate", "chiseled_deepslate", "cracked_deepslate_bricks", "cracked_deepslate_tiles", "infested_deepslate",
         "calcite", "tuff", "smooth_basalt", "raw_iron_block", "raw_gold_block"] + ["deepslate_%s_ore" % o for o in ORES]
for name in plain:
    block(name)

# One model whatever its lit state; 1.18's file has a single "" variant, ours names both.
data = assets.port_blockstate("deepslate_redstone_ore")
one = data["variants"]["normal"]
assets.write_json("blockstates/deepslate_redstone_ore.json", {"variants": {"lit=false": one, "lit=true": one}})

for s in SETS:
    block(full(s))
    block(s + "_stairs")
    block(s + "_wall")
    # The one state with no sides and no post never occurs (a lone wall has a post); give it the post anyway,
    # so every state has a model.
    wall = assets.port_blockstate(s + "_wall")
    post = [p for p in wall["multipart"] if p.get("when") == {"up": "true"}][0]["apply"]
    wall["multipart"].append({"when": {"up": "false", "north": "none", "east": "none", "south": "none", "west": "none"},
                              "apply": post})
    assets.write_json("blockstates/%s_wall.json" % s, wall)
    # Slabs: 1.18's one block with type=bottom/top/double is 1.12's half block plus a double block.
    data = assets.jar_json("assets/minecraft/blockstates/%s_slab.json" % s)["variants"]
    bottom = assets._port_apply(data["type=bottom"])
    top = assets._port_apply(data["type=top"])
    double = assets._port_apply(data["type=double"])
    assets.write_json("blockstates/%s_slab.json" % s, {"variants": {"half=bottom,variant=default": bottom,
                                                                     "half=top,variant=default": top}})
    assets.write_json("blockstates/%s_double_slab.json" % s, {"variants": {"variant=default": double}})
    assets.port_item_model(s + "_slab")
    name = assets.name("block.minecraft.%s_slab" % s)
    lang.append("tile.%s.%s_slab.name=%s" % (M, s, name))
    lang.append("tile.%s.%s_double_slab.name=%s" % (M, s, name))

for item in ("raw_iron", "raw_gold"):
    assets.port_item_model(item)
    lang.append("item.%s.%s.name=%s" % (M, item, assets.name("item.minecraft." + item)))

events = {}
for group in ("block.deepslate", "block.polished_deepslate", "block.deepslate_bricks", "block.deepslate_tiles",
              "block.calcite", "block.tuff", "block.basalt"):
    assets.copy_sound_group(group, events)
assets.merge_sounds("deepslate", events)
assets.merge_lang("deepslate", sorted(lang) + assets.subtitle_lines(events))
print("deepslate: %d names, %d sound events" % (len(lang), len(events)))
