# Stream L: languages (Rule 4)

Caves & Cliffs ships `en_us.lang` and the family's 18 other languages: es_es, es_mx, es_ar, es_uy, es_ve, pt_br,
fr_fr, de_de, ru_ru, zh_cn, ja_jp, ko_kr, it_it, pl_pl, tr_tr, uk_ua, zh_tw and nl_nl. Every file has every
`en_us` key (418).

## How

*   `tools/lang/build_langs.py` is the family tool, copied unchanged from Backport Core's `main` (the newest copy,
    the same one Update Aquatic and Nether use). Settings are in `tools/lang/langs.json`.
*   **Vanilla names (245 keys)** come from Mojang's 1.18.2 language files (`mojang_versions: ["1.18.2"]`, cached
    in `G:/MojangLang/1.18.2`): blocks, items, mobs, advancements, subtitles and death messages. They are never
    typed by hand. Matching is by key structure (224), by English text (20, mostly double slabs, glow lichen
    variants and the dripstone death messages) and by `tools/lang/vanilla_map.txt` (1: the falling stalactite
    entity takes the pointed dripstone block's name).
*   **Our own text (173 keys)**: the settings screen's names and tooltips for every stream and the look-and-feel
    options, plus the world type name. They live in `tools/lang/translations/<code>.lang`, 15 files (es_ar, es_uy
    and es_ve copy es_mx). `translations/en_us.source.lang` records the English they were made from. Each vanilla
    thing a tooltip mentions uses Mojang's name in that language. Field names, registry names, numbers, version
    numbers, music track titles and other mods' names stay as they are.
*   **The world type** (`generator.cavesandcliffs`) is "Caves & Cliffs" in every language, as the update's name.
    Mojang's only translated "Caves & Cliffs" is the advancement title, which many languages turn into a pun
    (pt_br "Estrela cadente", pl_pl "Z górki na pazurki!"). The advancement keeps that title; tooltips that
    mention the advancement use it, and tooltips about the world type say "Caves & Cliffs".
*   **Banned words**: the family list, except "Caves Not Cliffs" and "Farmer's Future Delight". The extended
    height tooltip names them on purpose, as mods it refuses to run with.
*   1.19 and 1.20 biome names in the newer-biomes tooltips (mangrove swamp, deep dark, cherry grove) are not in
    1.18.2's files. They are taken from Mojang's 1.20.6 files where the translator could find them.
*   `pack.mcmeta` (`pack_format` 3) is in the jar, so 1.12.2 reads the lowercase file names.

## Checks

*   `python -I tools/lang/build_langs.py check`: 18/18 files, 418 keys each, 0 errors, 0 warnings.
*   `python tools/check_assets.py` now also checks the languages: every one of the 18 files exists and has
    every `en_us` key, no key `en_us` lacks, no empty value and no duplicate key, and `pack.mcmeta` exists with
    `pack_format` 3.

## After an en_us change

```
python -I tools/lang/build_langs.py todo       # what each language now lacks, in English
# translate tools/lang/todo/<code>.lang into tools/lang/translations/<code>.lang
python -I tools/lang/build_langs.py build
python -I tools/lang/build_langs.py snapshot
```

`check` warns when a key's English changed after its snapshot. Those keys need translating again.
