"""Stream Ax assets: the axolotl's five textures, the bucket of axolotl, sounds, names, advancements.

    python tools/axolotl/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []
for color in ("lucy", "wild", "gold", "cyan", "blue"):
    assets.texture("entity/axolotl/axolotl_" + color)
assets.port_item_model("axolotl_bucket")
lang.append("item.%s.axolotl_bucket.name=%s" % (M, assets.name("item.minecraft.axolotl_bucket")))
lang.append("entity.%s.axolotl.name=%s" % (M, assets.name("entity.minecraft.axolotl")))
# The vanilla keys of 1.17's dry-out damage, which 1.12.2 lacks.
for key in ("death.attack.dryout", "death.attack.dryout.player"):
    lang.append("%s=%s" % (key, assets.name(key)))

assets.port_advancement("husbandry/axolotl_in_a_bucket", "minecraft:husbandry/root", M + ":axolotl_bucket", lang)
assets.port_advancement("husbandry/kill_axolotl_target", M + ":husbandry/axolotl_in_a_bucket", M + ":axolotl_bucket", lang)

events = {}
for e in ("entity.axolotl.attack", "entity.axolotl.death", "entity.axolotl.hurt", "entity.axolotl.idle_air",
          "entity.axolotl.idle_water", "entity.axolotl.splash", "entity.axolotl.swim", "item.bucket.fill_axolotl",
          "item.bucket.empty_axolotl"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("axolotl", events)
assets.merge_lang("axolotl", sorted(lang) + assets.subtitle_lines(events))
print("axolotl: %d lang lines, %d sound events" % (len(lang), len(events)))
