"""Stream Ps assets: powder snow, its cauldron and bucket, the frost overlay, sounds, the advancement.

    python tools/powdersnow/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
lang = []
for n in ("powder_snow", "powder_snow_cauldron"):
    assets.port_blockstate(n)
    lang.append("tile.%s.%s.name=%s" % (M, n, assets.name("block.minecraft." + n)))
assets.port_item_model("powder_snow_bucket")
lang.append("item.%s.powder_snow_bucket.name=%s" % (M, assets.name("item.minecraft.powder_snow_bucket")))
assets.texture("misc/powder_snow_outline")

# Death messages are looked up by the damage type's name alone, so these keys are vanilla's own.
for key in ("death.attack.freeze", "death.attack.freeze.player"):
    lang.append("%s=%s" % (key, assets.name(key)))

assets.port_advancement("adventure/walk_on_powder_snow_with_leather_boots", "minecraft:adventure/sleep_in_bed",
                        "minecraft:leather_boots", lang, requires=M + ":powder_snow_bucket")

events = {}
assets.copy_sound_group("block.powder_snow", events)
for e in ("item.bucket.fill_powder_snow", "item.bucket.empty_powder_snow", "entity.player.hurt_freeze",
          "entity.skeleton.converted_to_stray"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("powdersnow", events)
assets.merge_lang("powdersnow", sorted(lang) + assets.subtitle_lines(events))
print("powdersnow: %d lang lines, %d sound events" % (len(lang), len(events)))
