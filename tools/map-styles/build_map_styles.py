"""
Genera gli stili della mappa in shared/src/commonMain/composeResources/files/map/.

Parte dallo stile "liberty" di OpenFreeMap (tile vettoriali OpenMapTiles, licenza BSD dello stile, dati ODbL)
e lo ricolora con una palette simile a Google Maps, in chiaro e in scuro. Produce anche:
- satellite.json: immagini Sentinel-2 cloudless di EOX (2016, CC BY 4.0, gratuite e senza chiave; 10 m, nitide
  fino allo zoom 15) con sopra strade, sentieri ed etichette (vista "ibrida");

Uso:  python tools/map-styles/build_map_styles.py
Da rilanciare quando si vuole aggiornare la base di OpenFreeMap; i JSON generati si committano.
"""
import copy
import json
import pathlib
import urllib.request

LIBERTY_URL = "https://tiles.openfreemap.org/styles/liberty"
OUT = pathlib.Path(__file__).resolve().parents[2] / "shared/src/commonMain/composeResources/files/map"

# Il livello 2016 ("s2cloudless_3857") è CC BY 4.0; gli anni dal 2018 sono CC BY-NC-SA (solo uso non commerciale)
EOX_TILES = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg"
EOX_ATTRIBUTION = (
    '<a href="https://s2maps.eu">Sentinel-2 cloudless</a> by EOX IT Services GmbH '
    '(contiene dati Copernicus Sentinel 2016 modificati)'
)
# Palette simile a Google Maps. Chiave = gruppo di livelli (vedi group()).
LIGHT = {
    "land": "#f2f2ef", "park": "#cdebc5", "wood": "#d3ebcb", "grass": "#d9efd2", "residential": "#ebebe8",
    "ice": "#ffffff", "sand": "#f4efdc", "pitch": "#d8edd1", "cemetery": "#d4e8cc", "hospital": "#fbe3e3",
    "school": "#f0ebdf", "water": "#aadaff", "aeroway": "#e8eaed", "runway": "#ffffff",
    "motorway": "#fde293", "motorway_casing": "#f2c45a",
    "major": "#ffffff", "major_casing": "#d4d4d4",
    "minor": "#ffffff", "minor_casing": "#e0e0e0",
    "path": "#a8a39a", "rail": "#c4c7c5", "building": "#e8e8e8", "building_outline": "#d9d9d9",
    "boundary": "#9aa0a6",
    "text": "#5f6368", "text_place": "#3c4043", "text_city": "#202124", "text_water": "#3f6ea2",
    "text_transit": "#1967d2", "halo": "#ffffff",
}
DARK = {
    "land": "#212327", "park": "#1f3a2c", "wood": "#1c3328", "grass": "#203a2c", "residential": "#26282c",
    "ice": "#2f3134", "sand": "#2e2b24", "pitch": "#1f3a2c", "cemetery": "#22332a", "hospital": "#3a2a2e",
    "school": "#2c2a26", "water": "#163049", "aeroway": "#2a2c30", "runway": "#3a3d42",
    "motorway": "#7a6a3a", "motorway_casing": "#4d4224",
    "major": "#4a4d52", "major_casing": "#2c2e31",
    "minor": "#3a3c40", "minor_casing": "#2a2c2f",
    "path": "#6b6f75", "rail": "#4d5056", "building": "#2b2d31", "building_outline": "#33363a",
    "boundary": "#6b7078",
    "text": "#9aa0a6", "text_place": "#c4c7c5", "text_city": "#e8eaed", "text_water": "#7ba7d8",
    "text_transit": "#8ab4f8", "halo": "#212327",
}


def group(layer):
    """Il gruppo di colore di un livello di liberty, dal suo id."""
    i = layer["id"]
    if i == "background": return "land"
    if i.startswith("park"): return "park"
    if i == "landcover_wood": return "wood"
    if i in ("landcover_grass", "landcover_wetland"): return "grass"
    if i == "landuse_residential": return "residential"
    if i == "landcover_ice": return "ice"
    if i == "landcover_sand": return "sand"
    if i in ("landuse_pitch", "landuse_track"): return "pitch"
    if i == "landuse_cemetery": return "cemetery"
    if i == "landuse_hospital": return "hospital"
    if i == "landuse_school": return "school"
    if i == "water" or i.startswith("waterway_") and layer["type"] == "line": return "water"
    if i == "aeroway_fill": return "aeroway"
    if i.startswith("aeroway_"): return "runway"
    if layer.get("source-layer") == "transportation" and layer["type"] == "line":
        casing = i.endswith("_casing")
        if "rail" in i: return "rail"
        if "path_pedestrian" in i: return "major_casing" if casing else "path"
        if "motorway" in i: return "motorway_casing" if casing else "motorway"
        if any(k in i for k in ("trunk_primary", "secondary_tertiary", "_link")): return "major_casing" if casing else "major"
        return "minor_casing" if casing else "minor"
    if i.startswith("building"): return "building"
    if i.startswith("boundary"): return "boundary"
    return None


def text_group(layer):
    i = layer["id"]
    if i.startswith("water"): return "text_water"
    if i == "poi_transit": return "text_transit"
    if i in ("label_city", "label_city_capital") or i.startswith("label_country"): return "text_city"
    if i.startswith("label_"): return "text_place"
    return "text"


def recolor(style, pal, dark):
    s = copy.deepcopy(style)
    s["name"] = "GeoSentinel " + ("scura" if dark else "chiara")
    layers = []
    for layer in s["layers"]:
        paint = layer.setdefault("paint", {})
        if layer["type"] == "raster":
            if dark: continue  # rilievo di Natural Earth: chiaro, stonerebbe di notte
            layers.append(layer)
            continue
        if layer["type"] == "symbol":
            if "text-color" in paint or layer.get("layout", {}).get("text-field"):
                paint["text-color"] = pal[text_group(layer)]
                paint["text-halo-color"] = pal["halo"]
            layers.append(layer)
            continue
        g = group(layer)
        if g is not None:
            color = pal[g]
            if layer["type"] == "background": paint["background-color"] = color
            elif layer["type"] == "fill":
                paint["fill-color"] = color
                if "fill-outline-color" in paint:
                    paint["fill-outline-color"] = pal["building_outline"] if g == "building" else color
            elif layer["type"] == "line": paint["line-color"] = color
            elif layer["type"] == "fill-extrusion": paint["fill-extrusion-color"] = color
        layers.append(layer)
    s["layers"] = layers
    return s


def satellite(style):
    """Immagini EOX + strade principali, sentieri ed etichette chiare con alone scuro (come la vista ibrida)."""
    base = recolor(style, LIGHT, dark=False)
    keep_lines = ("road_motorway", "road_trunk_primary", "road_secondary_tertiary", "road_path_pedestrian")
    layers = [
        {"id": "background", "type": "background", "paint": {"background-color": "#000000"}},
        {"id": "satellite", "type": "raster", "source": "eox"},
    ]
    for layer in base["layers"]:
        if layer["id"] in keep_lines:
            line = copy.deepcopy(layer)
            line["paint"]["line-color"] = "#fde293" if "motorway" in line["id"] else "#ffffff"
            line["paint"]["line-opacity"] = 0.55 if "path" not in line["id"] else 0.8
            layers.append(line)
        # niente punti di interesse né cartelli stradali: su sfondo fotografico il testo bianco sparirebbe nel cartello
        elif (layer["type"] == "symbol" and layer.get("layout", {}).get("text-field")
              and not layer["id"].startswith("poi") and "shield" not in layer["id"]):
            label = copy.deepcopy(layer)
            label["paint"]["text-color"] = "#ffffff"
            label["paint"]["text-halo-color"] = "rgba(0,0,0,0.75)"
            layers.append(label)
    return {
        "version": 8, "name": "GeoSentinel satellite",
        "glyphs": style["glyphs"], "sprite": style["sprite"],
        "sources": {
            "openmaptiles": style["sources"]["openmaptiles"],
            # oltre lo zoom 15 le immagini si ingrandiscono e basta: maxzoom 15 evita richieste inutili
            "eox": {"type": "raster", "tiles": [EOX_TILES], "tileSize": 256, "maxzoom": 15, "attribution": EOX_ATTRIBUTION},
        },
        "layers": layers,
    }


def main():
    # OpenFreeMap rifiuta lo user agent di default di Python (403)
    request = urllib.request.Request(LIBERTY_URL, headers={"User-Agent": "GeoSentinel-map-styles/1.0"})
    with urllib.request.urlopen(request) as r:
        liberty = json.load(r)
    OUT.mkdir(parents=True, exist_ok=True)
    styles = {
        "streets_light.json": recolor(liberty, LIGHT, dark=False),
        "streets_dark.json": recolor(liberty, DARK, dark=True),
        "satellite.json": satellite(liberty),
    }
    for name, style in styles.items():
        (OUT / name).write_text(json.dumps(style, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(name, len(style["layers"]), "livelli")


if __name__ == "__main__":
    main()
