"""Stream Am assets: amethyst blocks, buds, cluster, tinted glass, shard, spyglass, its scope, sounds, advancements.

    python tools/amethyst/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []
for n in ("amethyst_block", "budding_amethyst", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
          "amethyst_cluster", "tinted_glass"):
    assets.port_blockstate(n)
    assets.port_item_model(n)
    lang.append("tile.%s.%s.name=%s" % (M, n, assets.name("block.minecraft." + n)))
for n in ("amethyst_shard", "spyglass"):
    assets.port_item_model(n)
    lang.append("item.%s.%s.name=%s" % (M, n, assets.name("item.minecraft." + n)))
assets.texture("misc/spyglass_scope")

assets.port_advancement("adventure/spyglass_at_parrot", "minecraft:adventure/root", M + ":spyglass", lang)
assets.port_advancement("adventure/spyglass_at_ghast", M + ":adventure/spyglass_at_parrot", M + ":spyglass", lang)
assets.port_advancement("adventure/spyglass_at_dragon", M + ":adventure/spyglass_at_ghast", M + ":spyglass", lang)

events = {}
assets.copy_sound_group("block.amethyst_block", events)
assets.copy_sound_group("block.amethyst_cluster", events)
for e in ("block.amethyst_block.chime", "item.spyglass.use", "item.spyglass.stop_using"):
    events[e] = assets.copy_sound(e)
for size in ("small", "medium", "large"):
    for e in ("break", "place"):
        events["block.%s_amethyst_bud.%s" % (size, e)] = assets.copy_sound("block.%s_amethyst_bud.%s" % (size, e))
assets.merge_sounds("amethyst", events)
assets.merge_lang("amethyst", sorted(lang) + assets.subtitle_lines(events))
print("amethyst: %d lang lines, %d sound events" % (len(lang), len(events)))
