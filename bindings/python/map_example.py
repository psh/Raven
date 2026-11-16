"""
Raven's Map widget, driven from Python: the vector tiles of compose-raven's
maps spike, drawn as OpenFreeMap's Liberty style says.

    RAVEN_LIBRARY=/path/to/libraven.dylib python3 map_example.py [data folder]

The data folder is what compose/spikes/maps/fetch-data downloads (MAPS_DATA,
else the spike's own data/ folder): the style, its sprite sheet, and the tiles
of central Chicago. The map asks for each tile as the view needs it, and this
program reads it from the folder and hands it back. Drag to move the map,
scroll to zoom. `--capture file.ppm` saves the window once its tiles are drawn,
and quits.

`python3 map_example.py --test` runs without a window system and without a GPU,
on a style and a tile it writes itself: it is how the build of Raven checks the
map from this binding.
"""

import math
import os
import sys

import raven as r

HERE = os.path.dirname(os.path.abspath(__file__))
ATTRIBUTION = "© OpenFreeMap © OpenMapTiles Data from OpenStreetMap"


def read(path):
    try:
        with open(path, "rb") as f:
            return f.read()
    except OSError:
        return None


class FolderTiles:
    """Tiles from fetch-data's folder: tiles/z-x-y.pbf; a tile not there is no tile."""

    def __init__(self, folder):
        self.folder = folder
        self.asked = 0

    def request(self, map_, z, x, y, user_data):
        self.asked += 1
        data = read(os.path.join(self.folder, "tiles", "%d-%d-%d.pbf" % (z, x, y)))
        r.Rmap_tile_loaded(map_, z, x, y, data, len(data) if data else 0)


def wait_for_tiles(map_, seconds=5.0):
    """Draws frames until no tile asked for or handed in is still being prepared."""
    stats = r.RMapStats()
    start = r.Rget_time()
    while r.Rget_time() - start < seconds:
        r.Rdraw_frame()
        r.Rmap_get_stats(map_, stats)
        if stats.requested_tiles and not stats.pending_tiles:
            return stats
        r.Rwait_frame(0.01)
    return stats


def capture(window, path):
    pixels, width, height = r.Rwindow_capture(window)
    if not pixels:
        return False
    import ctypes
    data = ctypes.string_at(pixels, width * height * 4)
    r.Rpixels_free(pixels)
    with open(path, "wb") as f:
        f.write(b"P6\n%d %d\n255\n" % (width, height))
        f.write(b"".join(data[i:i + 3] for i in range(0, len(data), 4)))
    return True


# ---------------------------------------------------------------------------
#  --test: a style and a tile written here
# ---------------------------------------------------------------------------

def varint(n):
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        out.append(byte | (0x80 if n else 0))
        if not n:
            return bytes(out)


def message(field, body):
    return varint((field << 3) | 2) + varint(len(body)) + body


def field(number, value):
    return varint(number << 3) + varint(value)


def packed(values):
    return b"".join(varint(v & 0xFFFFFFFF) for v in values)


def zz(n):
    return (n << 1) ^ (n >> 31)


def test_tile():
    """A water square and a place named Chicago at the middle of the tile."""
    square = [9, zz(1024), zz(1024), 26, zz(2048), zz(0), zz(0), zz(2048), zz(-2048), zz(0), 15]
    water = message(1, b"water") + message(2, message(2, packed([0, 0])) + field(3, 3) + message(4, packed(square))) + \
        message(3, b"class") + message(4, message(1, b"lake")) + field(5, 4096) + field(15, 2)
    place = message(1, b"place") + message(2, message(2, packed([0, 0])) + field(3, 1) + message(4, packed([9, zz(2048), zz(2048)]))) + \
        message(3, b"name") + message(4, message(1, b"Chicago")) + field(5, 4096) + field(15, 2)
    return message(3, water) + message(3, place)


TEST_STYLE = """{"version": 8, "sources": {"openmaptiles": {"type": "vector"}}, "layers": [
    {"id": "background", "type": "background", "paint": {"background-color": "#f8f4f0"}},
    {"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water", "paint": {"fill-color": "#9ebdff"}},
    {"id": "place", "type": "symbol", "source": "openmaptiles", "source-layer": "place",
     "layout": {"text-field": ["get", "name"], "text-size": 14}}
]}"""


def run_test():
    desc = r.RInitDesc()
    desc.backend = r.R_BACKEND_NULL
    desc.headless = 1
    if not r.Rinit_ex(desc):
        print("cannot start Raven")
        return 1
    errors = []
    r.Rerror_set_callback(lambda code, severity, detail: errors.append((code, detail)))
    window = r.Rwindow_create("map", 500, 400, r.R_WINDOW_DEFAULT)
    map_ = r.Rwidget_create(r.R_WIDGET_MAP)
    r.Rwindow_set_content(window, map_)
    tile = test_tile()
    asked = []

    def request(m, z, x, y, user_data):
        asked.append((z, x, y))
        r.Rmap_tile_loaded(m, z, x, y, tile, len(tile))

    checks = [r.Rmap_set_style_json(map_, TEST_STYLE, -1, None) == 1]
    r.Rmap_set_tile_source(map_, 14, 14, request, None)
    # the middle of tile 14/4203/6089, central Chicago
    n = 2.0 ** 14
    r.Rmap_set_center(map_, math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * 6089.5 / n)))), 4203.5 / n * 360.0 - 180.0)
    r.Rmap_set_zoom(map_, 14.0)
    stats = wait_for_tiles(map_)
    r.Rwidget_invalidate(map_)
    r.Rdraw_frame()
    r.Rmap_get_stats(map_, stats)
    checks += [
        asked == [(14, 4203, 6089)],
        stats.prepared_tiles == 1,
        stats.triangles == 2,
        stats.labels_placed == 1,
        not errors,
    ]
    r.Rshutdown()
    if all(checks):
        print("map from Python: OK")
        return 0
    print("map from Python: FAILED", checks, errors, asked)
    return 1


# ---------------------------------------------------------------------------
#  The map of Chicago
# ---------------------------------------------------------------------------

def main():
    if "--test" in sys.argv:
        return run_test()
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    capture_path = sys.argv[sys.argv.index("--capture") + 1] if "--capture" in sys.argv else None
    if capture_path in args:
        args.remove(capture_path)
    folder = args[0] if args else os.environ.get("MAPS_DATA", os.path.join(HERE, "..", "..", "compose", "spikes", "maps", "data"))
    style = read(os.path.join(folder, "liberty.json"))
    if not style:
        print("no liberty.json in %s: run compose/spikes/maps/fetch-data" % folder)
        return 1
    zooms = [int(line.split()[0]) for line in (read(os.path.join(folder, "tiles.txt")) or b"").decode().splitlines() if line.strip()]

    if not r.Rinit(r.R_BACKEND_VULKAN):
        print("cannot start Raven")
        return 1
    window = r.Rwindow_create("Map from Python", 1200, 800, r.R_WINDOW_DEFAULT)
    map_ = r.Rwidget_create(r.R_WIDGET_MAP)
    r.Rwindow_set_content(window, map_)
    r.Rmap_set_style_json(map_, style, len(style), None)
    index = read(os.path.join(folder, "sprite@2x.json"))
    png = read(os.path.join(folder, "sprite@2x.png"))
    if index and png:
        r.Rmap_set_sprite(map_, index, len(index), png, len(png))
    r.Rmap_set_attribution(map_, ATTRIBUTION)
    tiles = FolderTiles(folder)
    r.Rmap_set_tile_source(map_, min(zooms or [0]), max(zooms or [14]), tiles.request, None)
    r.Rmap_set_center(map_, float(os.environ.get("MAPS_LAT", 41.882)), float(os.environ.get("MAPS_LON", -87.630)))
    r.Rmap_set_zoom(map_, float(os.environ.get("MAPS_ZOOM", 15.0)))

    if capture_path:
        stats = wait_for_tiles(map_)
        r.Rwidget_invalidate(map_)
        r.Rdraw_frame()
        r.Rmap_get_stats(map_, stats)
        print("%d tiles asked for, %d prepared, %d labels placed of %d, %d icons" % (
            tiles.asked, stats.prepared_tiles, stats.labels_placed, stats.label_candidates, stats.icons))
        capture(window, capture_path)
        r.Rshutdown()
        return 0
    while r.Rwait_frame(-1.0):
        pass
    r.Rshutdown()
    return 0


if __name__ == "__main__":
    sys.exit(main())
