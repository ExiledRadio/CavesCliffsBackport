"""Stream X assets: the light block and item, and the Caves & Cliffs advancement.

    python tools/blocks/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []

# The block draws nothing; its 16 models only carry the picture its marker and break particles use.
assets.port_blockstate("light")

# The item: 1.18's model, whose overrides pick the picture with the level's number from the "level" property.
item = assets.jar_json("assets/minecraft/models/item/light.json")
item["parent"] = "item/generated"
item["textures"] = {"layer0": assets.texture("item/light")}
for override in item["overrides"]:
    override["model"] = assets.port_model(override["model"])
assets.write_json("models/item/light.json", item)
lang.append("tile.%s.light.name=%s" % (M, assets.name("block.minecraft.light")))

# Needs the light item to exist, so switching the stream off removes the advancement with it.
assets.port_advancement("adventure/fall_from_world_height", "minecraft:adventure/root", "minecraft:water_bucket", lang,
                        requires=M + ":light")

assets.merge_lang("blocks", sorted(lang))
print("blocks: %d lang lines" % len(lang))
