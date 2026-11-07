"""Stream Cu assets: copper ores, raw copper, the copper blocks, the lightning rod, sounds and advancements.

    python tools/copper/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
STAGES = ["", "exposed_", "weathered_", "oxidized_"]
lang = []


def name(shape, stage, waxed):
    base = ("copper_block" if stage == "" else stage + "copper") if shape == "block" else stage + shape
    return ("waxed_" + base) if waxed else base


def block(n):
    assets.port_blockstate(n)
    assets.port_item_model(n)
    lang.append("tile.%s.%s.name=%s" % (M, n, assets.name("block.minecraft." + n)))


for n in ("copper_ore", "deepslate_copper_ore", "raw_copper_block", "lightning_rod"):
    block(n)

for waxed in (False, True):
    for stage in STAGES:
        for shape in ("block", "cut_copper", "cut_copper_stairs"):
            block(name(shape, stage, waxed))
        slab = name("cut_copper_slab", stage, waxed)
        half, double = assets.port_slab(slab)
        assets.port_item_model(slab)
        title = assets.name("block.minecraft." + slab)
        lang.append("tile.%s.%s.name=%s" % (M, half, title))
        lang.append("tile.%s.%s.name=%s" % (M, double, title))

for item in ("copper_ingot", "raw_copper"):
    assets.port_item_model(item)
    lang.append("item.%s.%s.name=%s" % (M, item, assets.name("item.minecraft." + item)))

assets.port_advancement("husbandry/wax_on", "minecraft:husbandry/root", M + ":waxed_copper_block", lang)
assets.port_advancement("husbandry/wax_off", M + ":husbandry/wax_on", "minecraft:stone_axe", lang)
assets.port_advancement("adventure/lightning_rod_with_villager_no_fire", "minecraft:adventure/root", M + ":lightning_rod", lang)

events = {}
assets.copy_sound_group("block.copper", events)
for e in ("item.honeycomb.wax_on", "item.axe.wax_off", "item.axe.scrape"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("copper", events)
assets.merge_lang("copper", sorted(lang) + assets.subtitle_lines(events))
print("copper: %d lang lines, %d sound events" % (len(lang), len(events)))
