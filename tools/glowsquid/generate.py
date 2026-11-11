"""Stream Gs assets: glow squid, glow ink sac, glow item frame, their sounds, loot table and advancement.

    python tools/glowsquid/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []
for n in ("glow_ink_sac", "glow_item_frame"):
    assets.port_item_model(n)
    lang.append("item.%s.%s.name=%s" % (M, n, assets.name("item.minecraft." + n)))
for n in ("glow_squid", "glow_item_frame"):
    lang.append("entity.%s.%s.name=%s" % (M, n, assets.name("entity.minecraft." + n)))

assets.texture("entity/squid/glow_squid")
assets.texture("particle/glow")

# The frame as the entity draws it: 1.12.2's own item frame models (birch planks from the game or its resource
# pack) with the glow frame's back. Item models only so that the game loads, stitches and bakes them; the
# renderer asks for them by name (GlowSquidClient.FRAME, FRAME_MAP).
back = assets.texture("block/glow_item_frame")
for ours, parent in (("glow_item_frame_entity", "block/item_frame"), ("glow_item_frame_entity_map", "block/item_frame_map")):
    assets.write_json("models/item/%s.json" % ours, {"parent": parent, "textures": {"back": back}})

# 1.18's loot table in 1.12's format: 1-3 glow ink sacs, up to one more per level of Looting. Forge requires
# a name on every pool of a mod's table.
assets.write_json("loot_tables/entities/glow_squid.json", {"pools": [{"name": "main", "rolls": 1, "entries": [{
    "type": "item", "name": M + ":glow_ink_sac", "weight": 1,
    "functions": [{"function": "set_count", "count": {"min": 1, "max": 3}},
                  {"function": "looting_enchant", "count": {"min": 0, "max": 1}}]}]}]})

assets.port_advancement("husbandry/make_a_sign_glow", "minecraft:husbandry/root", M + ":glow_ink_sac", lang)

events = {}
for e in ("entity.glow_squid.ambient", "entity.glow_squid.death", "entity.glow_squid.hurt", "entity.glow_squid.squirt",
          "entity.glow_item_frame.add_item", "entity.glow_item_frame.break", "entity.glow_item_frame.place",
          "entity.glow_item_frame.remove_item", "entity.glow_item_frame.rotate_item",
          "item.glow_ink_sac.use", "item.ink_sac.use"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("glowsquid", events)
assets.merge_lang("glowsquid", sorted(lang) + assets.subtitle_lines(events))
print("glowsquid: %d lang lines, %d sound events" % (len(lang), len(events)))
