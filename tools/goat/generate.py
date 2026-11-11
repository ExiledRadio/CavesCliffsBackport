"""Stream Gt assets: the goat texture, its sounds, its name and the boat advancement.

    python tools/goat/generate.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = ["entity.%s.goat.name=%s" % (M, assets.name("entity.minecraft.goat"))]
assets.texture("entity/goat/goat")

# 1.17's "Whatever Floats Your Goat!". It has no item of ours to hang an item_exists condition on, so it
# loads only when our own condition (goat/GoatCondition, named in advancements/_factories.json) says goats are on.
factories = os.path.join(assets.ASSETS, "advancements", "_factories.json")
known = json.load(open(factories, encoding="utf-8")) if os.path.exists(factories) else {}
known.setdefault("conditions", {})["goats"] = "com.exiledradio.%s.goat.GoatCondition" % M
assets.write_json("advancements/_factories.json", known)
path = "husbandry/ride_a_boat_with_a_goat"
assets.port_advancement(path, "minecraft:husbandry/root", "minecraft:boat", lang)
full = os.path.join(assets.ASSETS, "advancements", *(path + ".json").split("/"))
data = json.load(open(full, encoding="utf-8"))
data["conditions"] = [{"type": "%s:goats" % M}]
assets.write_json("advancements/%s.json" % path, data)

events = {}
for e in ("ambient", "death", "eat", "hurt", "long_jump", "milk", "prepare_ram", "ram_impact"):
    events["entity.goat." + e] = assets.copy_sound("entity.goat." + e)
    events["entity.goat.screaming." + e] = assets.copy_sound("entity.goat.screaming." + e)
events["entity.goat.step"] = assets.copy_sound("entity.goat.step")
assets.merge_sounds("goat", events)
assets.merge_lang("goat", sorted(lang) + assets.subtitle_lines(events))
print("goat: %d lang lines, %d sound events" % (len(lang), len(events)))
