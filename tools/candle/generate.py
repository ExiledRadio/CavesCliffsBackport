"""Stream Ca assets: the candle, the 16 dyed candles, the 17 candle cakes, their sounds.

    python tools/candle/generate.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

M = assets.MODID
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
lang = []
for n in ["candle"] + [c + "_candle" for c in COLORS]:
    assets.port_blockstate(n)
    assets.port_item_model(n)
    lang.append("tile.%s.%s.name=%s" % (M, n, assets.name("block.minecraft." + n)))
    # Candle cakes have no item; the name shows in tools that name blocks.
    assets.port_blockstate(n + "_cake")
    lang.append("tile.%s.%s_cake.name=%s" % (M, n, assets.name("block.minecraft." + n + "_cake")))

# The cake under the candle is 1.12.2's own cake, so it looks the same before and after the candle goes on:
# the ported models point at 1.12.2's cake textures and the 1.18 copies are dropped.
models = os.path.join(assets.ASSETS, "models", "block")
for f in os.listdir(models):
    if f.endswith(".json") and ("candle_cake" in f or f == "template_cake_with_candle.json"):
        path = os.path.join(models, f)
        text = open(path, encoding="utf-8").read()
        with open(path, "w", encoding="utf-8", newline="\n") as out:
            out.write(text.replace("%s:block/cake_" % M, "blocks/cake_"))
for t in ("bottom", "side", "top"):
    copied = os.path.join(assets.ASSETS, "textures", "block", "cake_%s.png" % t)
    if os.path.exists(copied):
        os.remove(copied)

events = {}
assets.copy_sound_group("block.candle", events)
for e in ("block.candle.ambient", "block.candle.extinguish", "block.cake.add_candle"):
    events[e] = assets.copy_sound(e)
assets.merge_sounds("candle", events)
assets.merge_lang("candle", sorted(lang) + assets.subtitle_lines(events))
print("candle: %d lang lines, %d sound events" % (len(lang), len(events)))
