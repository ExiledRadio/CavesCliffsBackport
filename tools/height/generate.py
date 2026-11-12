"""Stream H's language lines: the Caves & Cliffs world type's name on the world creation screen.

    python tools/height/generate.py

(The height config lines come from tools/common/generate.py, which reads ModConfig.)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import assets  # noqa: E402

# WorldType's translation key is "generator." + its name (height/WorldTypeCavesAndCliffs.NAME).
assets.merge_lang("height", ["generator.cavesandcliffs=Caves & Cliffs"])
print("height: 1 line")
