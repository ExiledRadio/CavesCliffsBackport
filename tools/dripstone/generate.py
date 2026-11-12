"""Stream Dr assets: dripstone block, pointed dripstone, the lava cauldron, their sounds and death messages.

    python tools/dripstone/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []
for n in ("dripstone_block", "pointed_dripstone"):
    assets.port_blockstate(n)
    assets.port_item_model(n)
    lang.append("tile.%s.%s.name=%s" % (M, n, assets.name("block.minecraft." + n)))

# The lava cauldron: 1.18's full-cauldron shape with 1.12.2's own cauldron and lava textures, so it matches the
# vanilla cauldron beside it under any resource pack. It has no item.
template = assets.jar_json("assets/minecraft/models/block/template_cauldron_full.json")
assets.write_json("models/block/lava_cauldron.json", {
    "ambientocclusion": False,
    "textures": {"particle": "blocks/cauldron_side", "top": "blocks/cauldron_top", "bottom": "blocks/cauldron_bottom",
                 "side": "blocks/cauldron_side", "inside": "blocks/cauldron_inner", "content": "blocks/lava_still"},
    "elements": template["elements"],
})
assets.write_json("blockstates/lava_cauldron.json", {"variants": {"normal": {"model": "%s:lava_cauldron" % M}}})
lang.append("tile.%s.lava_cauldron.name=%s" % (M, assets.name("block.minecraft.lava_cauldron")))

lang.append("entity.%s.falling_stalactite.name=%s" % (M, assets.name("block.minecraft.pointed_dripstone")))
for ours, vanilla in (("stalagmite", "stalagmite"), ("fallingStalactite", "fallingStalactite")):
    for suffix in ("", ".player"):
        lang.append("death.attack.%s.%s%s=%s" % (M, ours, suffix, assets.name("death.attack.%s%s" % (vanilla, suffix))))

events = {}
assets.copy_sound_group("block.dripstone_block", events)
assets.copy_sound_group("block.pointed_dripstone", events)
for e in ("land", "drip_water", "drip_lava", "drip_water_into_cauldron", "drip_lava_into_cauldron"):
    events["block.pointed_dripstone." + e] = assets.copy_sound("block.pointed_dripstone." + e)
# 1.18.2 names the landing subtitle in its language file but not yet in sounds.json; current Java shows it.
events["block.pointed_dripstone.land"]["subtitle"] = "subtitles.%s.block.pointed_dripstone.land" % M
assets.merge_sounds("dripstone", events)
assets.merge_lang("dripstone", sorted(lang) + assets.subtitle_lines(events))
print("dripstone: %d lang lines, %d sound events" % (len(lang), len(events)))
