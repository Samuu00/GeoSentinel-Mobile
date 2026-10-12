"""Icona di GeoSentinel per Android e iOS, generata da una sola geometria.

Una vetta color lichene con un abete ritagliato in negativo, una seconda cima più scura dietro e due curve di livello
(la firma della testata ForestHeader) su un fondo verde bosco. Colori del tema (core/ui/theme/Color.kt).

Uscite:
- Android: drawable/ic_launcher_{background,foreground,monochrome}.xml (icona adattiva vettoriale, 108 x 108 dp,
  zona sicura il cerchio di raggio 33 al centro) e i PNG mipmap-*dpi per i launcher senza icone adattive.
- iOS: AppIcon.appiconset con le varianti chiara, scura e a tinta (1024 x 1024).

Uso: python tools/app-icon/build_app_icon.py   (dalla radice del progetto; serve Pillow)
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "androidApp/src/main/res"
APPICON = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"

# ---- palette (dal tema) ----
SKY_TOP = "#2B5A45"      # abete schiarito
SKY_BOTTOM = "#0D1F16"   # bosco di notte
LICHEN = "#A9D3B6"       # la vetta
MOSS = "#5E8A71"         # la cima dietro
CONTOUR = "#A9D3B6"      # curve di livello, molto trasparenti
CONTOUR_ALPHA = 0.16

# ---- geometria, in unità della tela adattiva (108) ----
CENTER = (54.0, 56.0)
RINGS = (27.0, 36.0)
RING_WIDTH = 1.3
BACK_PEAK = [(52, 76), (71, 47), (90, 76)]
MAIN_PEAK = [(20, 76), (47, 33), (78, 76)]
# abete ritagliato nella vetta: tre palchi e il tronco che tocca la base, piccolo rispetto alla montagna (più grande la
# vetta si leggeva come una "A")
FIR = [
    (45, 53), (50.5, 61), (48.5, 61), (54, 68), (51.5, 68), (57, 74.5),
    (46.3, 74.5), (46.3, 76), (43.7, 76), (43.7, 74.5),
    (33, 74.5), (38.5, 68), (36, 68), (41.5, 61), (39.5, 61),
]


def path_d(points):
    return "M" + " L".join(f"{x:g},{y:g}" for x, y in points) + " Z"


def circle_d(r):
    cx, cy = CENTER
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0"


def vector(body):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generato da tools/app-icon/build_app_icon.py: non modificare a mano -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{body}"
        "</vector>\n"
    )


def android_vectors():
    background = vector(
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"\n'
        f'                android:startColor="{SKY_TOP}" android:endColor="{SKY_BOTTOM}"/>\n'
        "        </aapt:attr>\n"
        "    </path>\n"
    )
    rings = "".join(
        f'    <path android:pathData="{circle_d(r)}" android:strokeColor="{CONTOUR}"\n'
        f'        android:strokeAlpha="{CONTOUR_ALPHA}" android:strokeWidth="{RING_WIDTH}" android:fillColor="#00000000"/>\n'
        for r in RINGS
    )
    # la vetta e l'abete in un solo path pari-dispari: l'abete è un buco, si vede il fondo
    peaks = (
        f'    <path android:pathData="{path_d(BACK_PEAK)}" android:fillColor="{MOSS}"/>\n'
        f'    <path android:pathData="{path_d(MAIN_PEAK)} {path_d(FIR)}" android:fillType="evenOdd" android:fillColor="{LICHEN}"/>\n'
    )
    foreground = vector(rings + peaks)
    # icone a tema (Android 13+): solo la sagoma, il sistema la ricolora
    monochrome = vector(
        f'    <path android:pathData="{path_d(BACK_PEAK)}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.6"/>\n'
        f'    <path android:pathData="{path_d(MAIN_PEAK)} {path_d(FIR)}" android:fillType="evenOdd" android:fillColor="#FFFFFFFF"/>\n'
    )
    drawable = RES / "drawable"
    (drawable / "ic_launcher_background.xml").write_text(background, encoding="utf-8")
    (drawable / "ic_launcher_foreground.xml").write_text(foreground, encoding="utf-8")
    (drawable / "ic_launcher_monochrome.xml").write_text(monochrome, encoding="utf-8")
    # il primo piano del template stava in drawable-v24 e avrebbe la precedenza
    old = RES / "drawable-v24/ic_launcher_foreground.xml"
    if old.exists():
        old.unlink()
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
        "</adaptive-icon>\n"
    )
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (RES / "mipmap-anydpi-v26" / name).write_text(adaptive, encoding="utf-8")


# ---- raster (Pillow), disegnato 4 volte più grande e poi ridotto: bordi lisci ----

def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def render(size, window, background=True, mark=LICHEN, back=MOSS, rings=True):
    """[window] = (x0, larghezza) della tela 108 da inquadrare. Senza fondo l'abete è trasparente."""
    ss = 4
    s = size * ss
    x0, w = window
    k = s / w

    def pt(p):
        return ((p[0] - x0) * k, (p[1] - x0) * k)

    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    if background:
        top, bottom = hex_rgb(SKY_TOP), hex_rgb(SKY_BOTTOM)
        draw = ImageDraw.Draw(img)
        for y in range(s):
            # il gradiente copre tutta la tela 108, qui se ne vede la finestra
            t = (x0 + y / k) / 108
            draw.line([(0, y), (s, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    if rings:
        layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        cx, cy = pt(CENTER)
        for r in RINGS:
            rr = r * k
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=hex_rgb(CONTOUR) + (round(255 * CONTOUR_ALPHA),), width=round(RING_WIDTH * k))
        img = Image.alpha_composite(img, layer)
    peaks = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(peaks)
    d.polygon([pt(p) for p in BACK_PEAK], fill=hex_rgb(back) + (255,))
    d.polygon([pt(p) for p in MAIN_PEAK], fill=hex_rgb(mark) + (255,))
    # l'abete: un buco nella vetta (anche nella cima dietro, dove si sovrappone)
    cut = Image.new("L", (s, s), 0)
    ImageDraw.Draw(cut).polygon([pt(p) for p in FIR], fill=255)
    peaks.putalpha(Image.composite(Image.new("L", (s, s), 0), peaks.getchannel("A"), cut))
    img = Image.alpha_composite(img, peaks)
    return img.resize((size, size), Image.LANCZOS)


def android_pngs():
    # launcher senza icone adattive (Android < 8): quadrata arrotondata e tonda, dalla zona sicura
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, px in sizes.items():
        full = render(px, (14, 80))
        for name, shape in (("ic_launcher", "square"), ("ic_launcher_round", "round")):
            mask = Image.new("L", (px * 4, px * 4), 0)
            draw = ImageDraw.Draw(mask)
            if shape == "square":
                draw.rounded_rectangle([0, 0, px * 4 - 1, px * 4 - 1], radius=px * 4 * 0.22, fill=255)
            else:
                draw.ellipse([0, 0, px * 4 - 1, px * 4 - 1], fill=255)
            icon = full.copy()
            icon.putalpha(mask.resize((px, px), Image.LANCZOS))
            icon.save(RES / f"mipmap-{density}/{name}.png")


def ios_icons():
    # iOS ritaglia da solo gli angoli: la chiara è piena e senza trasparenza (lo chiede l'App Store)
    render(1024, (14, 80)).convert("RGB").save(APPICON / "app-icon-1024.png")
    # scura: il fondo lo mette il sistema, qui solo il segno
    render(1024, (14, 80), background=False).save(APPICON / "app-icon-dark-1024.png")
    # a tinta: in scala di grigi, il sistema la colora
    render(1024, (14, 80), background=False, mark="#FFFFFF", back="#9A9A9A", rings=False).convert("LA").save(APPICON / "app-icon-tinted-1024.png")
    contents = """{
  "images" : [
    {
      "filename" : "app-icon-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "filename" : "app-icon-dark-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "tinted"
        }
      ],
      "filename" : "app-icon-tinted-1024.png",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
"""
    (APPICON / "Contents.json").write_text(contents, encoding="utf-8")


if __name__ == "__main__":
    android_vectors()
    android_pngs()
    ios_icons()
    print("icona generata")
