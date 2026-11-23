"""
Genera gli stili della mappa in shared/src/commonMain/composeResources/files/map/.

Parte dallo stile "liberty" di OpenFreeMap (tile vettoriali OpenMapTiles, licenza BSD dello stile, dati ODbL)
e lo ricolora con una palette tipo Apple Maps, in chiaro e in scuro, con le cime con la quota; toglie i POI generici
di OpenStreetMap. Niente rilievo ombreggiato (tolto il 23/11: zoomando, le tile delle quote facevano laggare l'iPhone).
Produce anche:
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

# Palette tipo Apple Maps (11/2026): terra calda e chiara, boschi verde salvia, acqua azzurra, strade bianche con un filo
# di bordo, statali gialle. Di notte grafite con i boschi appena verdi. Chiave = gruppo di livelli (vedi group()).
LIGHT = {
    "land": "#f6f3ec", "park": "#d3e7c1", "wood": "#c8e0b2", "grass": "#dcecc9", "residential": "#efebe3",
    "ice": "#ffffff", "sand": "#f2ead3", "pitch": "#d3e7c1", "cemetery": "#d7e6cb", "hospital": "#f6e1df",
    "school": "#f0eadb", "water": "#a2d1f2", "aeroway": "#ebe8e1", "runway": "#ffffff",
    "motorway": "#f8d37a", "motorway_casing": "#e2b250",
    "major": "#ffffff", "major_casing": "#dcd6c9",
    "minor": "#ffffff", "minor_casing": "#e6e1d6",
    "path": "#b09e85", "rail": "#cfcac0", "building": "#e9e4da", "building_outline": "#dcd6ca",
    "boundary": "#a49d93",
    "text": "#6f6a61", "text_place": "#48443e", "text_city": "#1d1c1a", "text_water": "#3f78a8",
    "text_transit": "#3a6fb0", "halo": "#ffffff",
    "peak": "#7a5a3a",
}
DARK = {
    "land": "#1c1e1d", "park": "#1e2e22", "wood": "#1b2c1f", "grass": "#203124", "residential": "#232524",
    "ice": "#2e3133", "sand": "#2c2a23", "pitch": "#1e2e22", "cemetery": "#212e24", "hospital": "#33282a",
    "school": "#2a2824", "water": "#172a3d", "aeroway": "#262826", "runway": "#3a3c3b",
    "motorway": "#8a7134", "motorway_casing": "#4d4022",
    "major": "#46494a", "major_casing": "#2a2b2b",
    "minor": "#393c3c", "minor_casing": "#282a29",
    "path": "#80776a", "rail": "#44474a", "building": "#2a2c2b", "building_outline": "#323534",
    "boundary": "#6f706d",
    "text": "#a19e97", "text_place": "#c9c6bf", "text_city": "#ecebe7", "text_water": "#7fa9d1",
    "text_transit": "#8cb0e0", "halo": "#1c1e1d",
    "peak": "#cdb08c",
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


def peaks(pal):
    """Le cime (OpenMapTiles "mountain_peak") con nome e quota, in corsivo marrone come sulle carte escursionistiche."""
    name = ["coalesce", ["get", "name:it"], ["get", "name"]]
    return {
        "id": "mountain_peak", "type": "symbol", "source": "openmaptiles", "source-layer": "mountain_peak",
        "minzoom": 10, "filter": ["==", ["geometry-type"], "Point"],
        "layout": {
            "text-field": ["case", ["has", "ele"], ["concat", name, "\n", ["to-string", ["get", "ele"]], " m"], name],
            "text-font": ["Noto Sans Italic"], "text-size": 11, "text-line-height": 1.15,
            "text-anchor": "top", "text-max-width": 8,
        },
        "paint": {"text-color": pal["peak"], "text-halo-color": pal["halo"], "text-halo-width": 1.4},
    }


# Stile leggero (22/11, lag su iPhone): solo i livelli che servono in montagna, da 89 a circa 30. Restano aree
# naturali e acqua, le strade senza i doppioni di ponti, tunnel e svincoli, e le scritte di località, cime e acqua.
# Tolti: edifici, confini, ferrovie leggere, aree sportive, nomi e cartelli delle strade, regioni e stati.
KEEP = {
    "background", "park", "landuse_residential", "landcover_wood", "landcover_grass", "landcover_wetland",
    "landcover_ice", "landcover_sand", "waterway_river", "waterway_other", "water",
    "road_service_track_casing", "road_minor_casing", "road_secondary_tertiary_casing", "road_trunk_primary_casing",
    "road_motorway_casing", "road_path_pedestrian", "road_service_track", "road_minor", "road_secondary_tertiary",
    "road_trunk_primary", "road_motorway", "road_major_rail",
    "water_name_point_label", "label_other", "label_village", "label_town", "label_city", "label_city_capital",
}


def bridges_as_roads(layer):
    """Senza i livelli bridge_*, i ponti li disegnano le strade normali: dal filtro si toglie solo "bridge". I tunnel
    restano fuori: una galleria disegnata come strada sembrerebbe un passaggio attraverso la montagna."""
    def fix(f):
        if isinstance(f, list):
            if len(f) == 5 and f[0] == "match" and f[1] == ["get", "brunnel"] and f[2] == ["bridge", "tunnel"]:
                return ["match", ["get", "brunnel"], ["tunnel"], False, True]
            return [fix(x) for x in f]
        return f
    if "filter" in layer:
        layer["filter"] = fix(layer["filter"])


def recolor(style, pal, dark):
    s = copy.deepcopy(style)
    s["name"] = "GeoSentinel " + ("scura" if dark else "chiara")
    # niente rilievo: né quello di Natural Earth né le Terrain Tiles (tolte il 23/11, lag su iPhone zoomando)
    s["sources"].pop("ne2_shaded", None)
    layers = []
    for layer in s["layers"]:
        paint = layer.setdefault("paint", {})
        if layer["type"] == "raster":
            continue
        # i punti d'interesse generici di OpenStreetMap: sulla mappa ci sono i POI del parco, gli altri erano rumore
        if layer.get("source-layer") == "poi":
            continue
        # stile leggero: tutto il resto si toglie (lag su iPhone, 22/11)
        if layer["id"] not in KEEP:
            continue
        bridges_as_roads(layer)
        # le cime prima delle località: a parità di posto vince il nome del paese
        if layer["id"] == "label_other":
            layers.append(peaks(pal))
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
