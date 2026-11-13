#!/usr/bin/env python3
"""
generate.py - stream Lf's assets, from Mojang's 1.16.5, 1.17.1 and 1.18.2 releases.

  python -I tools/lookfeel/generate.py <download dir>

<download dir> is a new, empty folder for Mojang's files (client jars, asset indexes, objects); the generated files
go under src/main/resources/assets/cavesandcliffsbackport/. Nothing else is read from the network. Writes:

  textures/gui/title/background/panorama_0..5.png        1.18.2's panorama (21w41a), from the asset index
  textures/gui/title/background/1_17/panorama_0..5.png   1.17.1's amethyst geode panorama (21w15a)
  textures/gui/title/background/panorama_overlay.png     1.18.2's jar (unchanged since 1.16)
  textures/gui/title/minecraft.png, mojangstudios.png    1.18.2's jar (unchanged since 1.16)
  textures/gui/widgets.png, checkbox.png, accessibility.png   1.18.2's jar (unchanged since 1.15/1.16)
  texts/splashes_{added,removed}_1_17.txt                 1.16.5 -> 1.17.1 splashes.txt
  texts/splashes_{added,removed}_1_18.txt                 1.17.1 -> 1.18.2 splashes.txt
  vanilla_text/<code>.lang                                vanilla's own text as 1.18.2 words it (below)

Vanilla text: the union of the keys the earlier family backports reword (their vanilla_text/en_us.lang, each at its
version) plus the keys 1.17 reworded that 1.12.2 has, each with its 1.18.2 text in every language. A 1.12.2 key is
matched to Mojang's by its English at the version that reworded it (1.12.2's entity.PigZombie.name is 1.16's
entity.minecraft.zombified_piglin). Keys whose 1.12.2 use differs from the modern string are left out (1.17's
"World Type" lost its colon only because 1.17's button adds ": "; 1.12.2's button does not).
"""

import hashlib
import io
import json
import os
import sys
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'cavesandcliffsbackport')
WS = os.path.dirname(ROOT)

# The family's eighteen languages plus English (Nether Update's vanilla_text list).
CODES = ['en_us', 'de_de', 'es_ar', 'es_es', 'es_mx', 'es_uy', 'es_ve', 'fr_fr', 'it_it', 'ja_jp', 'ko_kr', 'nl_nl',
         'pl_pl', 'pt_br', 'ru_ru', 'tr_tr', 'uk_ua', 'zh_cn', 'zh_tw']

# Earlier backports' vanilla text, with the version each one's English is.
EARLIER = [
    ('UpdateAquaticBackport/src/main/resources/assets/updateaquaticbackport/vanilla_text/en_us.lang', '1.13.2'),
    ('RLCraftVillagerTrading-languages/src/main/resources/assets/villagerbackport/vanilla_text/en_us.lang', '1.14.4'),
    ('BuzzyBeesBackport/src/main/resources/assets/buzzybeesbackport/vanilla_text/en_us.lang', '1.15.2'),
    ('NetherUpdateBackport/src/main/resources/assets/netherupdatebackport/vanilla_text/en_us.lang', '1.16.5'),
]

# 1.17's rewordings of text 1.12.2 has: 1.12.2 key -> Mojang key (en_us.json, 1.17.1).
V117 = [
    ('options.vsync', 'options.vsync'),                      # "Use VSync" -> "VSync"
    ('build.tooHigh', 'build.tooHigh'),                      # no longer ends in "blocks"
    ('tile.blockLapis.name', 'block.minecraft.lapis_block'),  # "Block of Lapis Lazuli"
    ('subtitles.entity.shulker_bullet.hit', 'subtitles.entity.shulker_bullet.hit'),
    ('subtitles.entity.shulker_bullet.hurt', 'subtitles.entity.shulker_bullet.hurt'),
    ('advancements.story.shiny_gear.title', 'advancements.story.shiny_gear.title'),
    ('disconnect.loginFailedInfo.serversUnavailable', 'disconnect.loginFailedInfo.serversUnavailable'),
]

# Text 1.18 added that stream Lf shows (its options, the autosave indicator): Mojang's keys, 1.18.2's text.
NEW_118 = ['menu.savingLevel', 'options.autosaveIndicator', 'options.hideLightningFlashes',
           'options.hideLightningFlashes.tooltip']

VERSIONS = ['1.13.2', '1.14.4', '1.15.2', '1.16.5', '1.17.1', '1.18.2']


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'backport-tools'})
    with urllib.request.urlopen(req) as r:
        return r.read()


def obj(h):
    data = get('https://resources.download.minecraft.net/%s/%s' % (h[:2], h))
    assert hashlib.sha1(data).hexdigest() == h, h
    return data


class Version:
    def __init__(self, dl, vid, manifest):
        self.id = vid
        self.dir = os.path.join(dl, vid)
        os.makedirs(self.dir, exist_ok=True)
        meta = json.loads(get([v['url'] for v in manifest['versions'] if v['id'] == vid][0]))
        jar = os.path.join(self.dir, 'client.jar')
        if not os.path.exists(jar):
            io.open(jar, 'wb').write(get(meta['downloads']['client']['url']))
        self.jar = zipfile.ZipFile(jar)
        self.index = json.loads(get(meta['assetIndex']['url']))['objects']

    def asset(self, name):
        """An asset-index object (cached in the download folder)."""
        p = os.path.join(self.dir, 'objects', name)
        if not os.path.exists(p):
            os.makedirs(os.path.dirname(p), exist_ok=True)
            io.open(p, 'wb').write(obj(self.index[name]['hash']))
        return io.open(p, 'rb').read()

    def lang(self, code):
        if code == 'en_us':
            return json.loads(self.jar.read('assets/minecraft/lang/en_us.json').decode('utf-8'))
        name = 'minecraft/lang/%s.json' % code
        return json.loads(self.asset(name).decode('utf-8')) if name in self.index else {}

    def splashes(self):
        text = self.jar.read('assets/minecraft/texts/splashes.txt').decode('utf-8')
        return [l for l in text.replace('\r', '').split('\n') if l.strip()]


def write(rel, data):
    p = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    io.open(p, 'wb').write(data)


def write_lines(rel, lines):
    write(rel, ('\n'.join(lines) + '\n').encode('utf-8'))


def to_lang(value):
    return value.replace('\r', '').replace('\n', '\\n')


def read_lang(path):
    out = []
    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\r\n')
        if line and not line.startswith('#') and '=' in line:
            out.append(tuple(line.split('=', 1)))
    return out


def main():
    dl = sys.argv[1]
    manifest = json.loads(get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
    v = {vid: Version(dl, vid, manifest) for vid in VERSIONS}
    v16, v17, v18 = v['1.16.5'], v['1.17.1'], v['1.18.2']

    # Panoramas: the jars carry 1x1 placeholders; the faces are in the asset index.
    for i in range(6):
        name = 'minecraft/textures/gui/title/background/panorama_%d.png' % i
        write('textures/gui/title/background/panorama_%d.png' % i, v18.asset(name))
        write('textures/gui/title/background/1_17/panorama_%d.png' % i, v17.asset(name))
    for src, dst in [('textures/gui/title/background/panorama_overlay.png', None), ('textures/gui/title/minecraft.png', None),
                     ('textures/gui/title/mojangstudios.png', None), ('textures/gui/widgets.png', None),
                     ('textures/gui/checkbox.png', None), ('textures/gui/accessibility.png', None)]:
        write(dst or src, v18.jar.read('assets/minecraft/' + src))

    # Splashes.
    for a, b, tag in [(v16, v17, '1_17'), (v17, v18, '1_18')]:
        sa, sb = a.splashes(), b.splashes()
        write_lines('texts/splashes_added_%s.txt' % tag, [s for s in sb if s not in sa])
        write_lines('texts/splashes_removed_%s.txt' % tag, [s for s in sa if s not in sb])

    # Vanilla text: 1.12.2 key -> Mojang key.
    mapping = []
    seen = set()
    for rel, ver in EARLIER:
        en = v[ver].lang('en_us')
        by_text = {}
        for k in sorted(en):
            by_text.setdefault(to_lang(en[k]), []).append(k)
        for key, text in read_lang(os.path.join(WS, rel)):
            if key in seen:
                continue
            if key in en and to_lang(en[key]) == text:
                mk = key
            else:
                same = by_text.get(text)
                if not same:
                    raise SystemExit('%s=%s: no key with that text in %s' % (key, text, ver))
                mk = key if key in same else same[0]
            seen.add(key)
            mapping.append((key, mk))
    for key, mk in V117 + [(k, k) for k in NEW_118]:
        if key not in seen:
            seen.add(key)
            mapping.append((key, mk))
    en18 = v18.lang('en_us')
    for key, mk in mapping:
        if mk not in en18:
            raise SystemExit('%s: %s is not in 1.18.2' % (key, mk))
    fallbacks = 0
    for code in CODES:
        moj = v18.lang(code)
        lines = []
        for key, mk in mapping:
            if mk in moj:
                lines.append('%s=%s' % (key, to_lang(moj[mk])))
            else:
                fallbacks += code != 'en_us'
                lines.append('%s=%s' % (key, to_lang(en18[mk])))
        write_lines('vanilla_text/%s.lang' % code, lines)
    print('generate: %d vanilla-text keys in %d languages (%d English fallbacks)' % (len(mapping), len(CODES), fallbacks))


if __name__ == '__main__':
    main()
