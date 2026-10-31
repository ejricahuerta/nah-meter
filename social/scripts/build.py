"""
Build the data for each made-up offer in offers.json, on real Toronto streets.

For each offer: find the real restaurant (Overture places), snap the courier and the drop-off to the
real road graph (Overture transportation: OpenStreetMap roads), name the drop-off by its cross
streets, route courier → restaurant → drop-off, take km and minutes from that route, set the pay,
score it like the app (a Decimal port of core Scorer + NahCopy), and fail if the verdict isn't the
one planned. Writes data/offers.out.json for template.html: card text plus the map in local km.

Run: python3 scripts/build.py   (first run downloads from S3, then it's cached in data/)
"""

import heapq
import json
import math
import os
import re
import sys
from decimal import ROUND_HALF_UP, Decimal

from shapely import wkb
from shapely.geometry import LineString, Point, box as shp_box
from shapely.ops import substring

sys.path.insert(0, os.path.dirname(__file__))
from overture import read  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")

DRIVABLE = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "residential",
    "living_street", "unclassified", "service",
}
# City pace by road class, km/h (good enough for a made-up offer's minutes).
SPEED = {"motorway": 80, "trunk": 60, "primary": 42, "secondary": 36, "tertiary": 32}
SHOWN_LANDUSE = {"park", "golf_course", "cemetery", "nature_reserve", "recreation_ground"}

# ---------------------------------------------------------------- local projection (km)


class Proj:
    def __init__(self, lon0, lat0):
        self.lon0, self.lat0 = lon0, lat0
        self.kx = math.cos(math.radians(lat0)) * 111.320
        self.ky = 110.574

    def xy(self, lon, lat):
        return ((lon - self.lon0) * self.kx, (lat - self.lat0) * self.ky)

    def lonlat(self, x, y):
        return (self.lon0 + x / self.kx, self.lat0 + y / self.ky)

    def geom(self, g):
        import numpy as np
        import shapely

        k = np.array([self.kx, self.ky])
        o = np.array([self.lon0, self.lat0])
        return shapely.transform(g, lambda c: (c - o) * k)


# ---------------------------------------------------------------- names

ABBR = {
    "Street": "St", "Avenue": "Ave", "Road": "Rd", "Drive": "Dr", "Boulevard": "Blvd",
    "Crescent": "Cres", "Court": "Crt", "Place": "Pl", "Circle": "Cir", "Trail": "Trl",
    "Parkway": "Pkwy", "Square": "Sq", "Terrace": "Terr", "Gardens": "Gdns", "Highway": "Hwy",
    "Gate": "Gt", "Heights": "Hts", "Grove": "Grv", "Circuit": "Crct",
}
DIRS = {"West": "W", "East": "E", "North": "N", "South": "S"}


def short_street(name):
    if not name:
        return name
    words = name.split()
    if len(words) > 1 and words[-1] in DIRS:
        words[-1] = DIRS[words[-1]]
    words = [ABBR.get(w, w) if i > 0 else w for i, w in enumerate(words)]
    return " ".join(words)


# ---------------------------------------------------------------- scoring (port of core Scorer/NahCopy)

D = Decimal


def money(v):
    s = v.normalize()
    if s == s.to_integral_value():
        return f"${int(s)}"
    return f"${v.quantize(D('0.01'), ROUND_HALF_UP)}"


def score(pay, minutes, km, orders, add_on, t):
    wait = t["perOrderWaitMin"] * orders if orders > 1 else 0
    m = minutes + wait
    hourly = (pay * 60 / m).quantize(D("0.01"), ROUND_HALF_UP)
    per_km = (pay / km).quantize(D("0.01"), ROUND_HALF_UP)
    short_hr = pay * 60 < t["minHourly"] * m
    short_km = pay < t["minPerKm"] * km
    if not add_on and pay < t["minPay"]:
        verdict, reason = "RED", f"Under {money(t['minPay'])} minimum"
    elif short_hr:
        verdict, reason = "RED", f"Under your {money(t['minHourly'])}"
    elif short_km:
        verdict, reason = "YELLOW", "Short on $/km"
    else:
        verdict, reason = "GREEN", f"Clears your {money(t['minHourly'])}"
    if add_on:
        reason = "Add-on · " + reason
    return {"verdict": verdict, "reason": reason, "hourly": f"${hourly}", "perKm": f"${per_km}", "scoredMinutes": m}


WORD = {"GREEN": "bet!", "YELLOW": "meh.", "RED": "nah.", "GREY": "can't read."}


def self_test(t):
    # From core ScorerTest's PRD cases: exactly meeting a threshold passes.
    assert score(D("20.00"), 60, D("20.0"), 1, False, t)["verdict"] == "GREEN"
    assert score(D("19.99"), 60, D("10.0"), 1, False, t)["verdict"] == "RED"
    assert score(D("5.49"), 10, D("2.0"), 1, False, t)["reason"] == "Under $5.50 minimum"
    assert score(D("12.00"), 30, D("15.0"), 1, False, t)["reason"] == "Short on $/km"
    assert score(D("3.10"), 13, D("3.5"), 1, True, t)["reason"] == "Add-on · Under your $20"
    assert score(D("4.00"), 10, D("2.0"), 1, True, t)["verdict"] == "GREEN"  # add-ons skip the minimum
    # Two orders wait 4 min: $10 for Uber's 26 min scores as 30 min, $20.00/hr.
    assert score(D("10.00"), 26, D("5.0"), 2, False, t)["hourly"] == "$20.00"


# ---------------------------------------------------------------- road graph


class Graph:
    def __init__(self, segments, proj):
        self.proj = proj
        self.node_xy = {}
        self.adj = {}
        self.node_names = {}
        self.node_classes = {}
        for s in segments:
            cls = s["class"]
            if cls not in DRIVABLE or not s["connectors"]:
                continue
            line = proj.geom(wkb.loads(s["geometry"]))
            if line.geom_type != "LineString" or line.length == 0:
                continue
            name = short_street((s["names"] or {}).get("primary"))
            cons = sorted(s["connectors"], key=lambda c: c["at"])
            speed = SPEED.get(cls, 26 if cls in ("residential", "unclassified", "living_street") else 18)
            for c in cons:
                p = line.interpolate(c["at"], normalized=True)
                self.node_xy.setdefault(c["connector_id"], (p.x, p.y))
                self.node_names.setdefault(c["connector_id"], set())
                self.node_classes.setdefault(c["connector_id"], set()).add(cls)
                if name:
                    self.node_names[c["connector_id"]].add(name)
            for a, b in zip(cons, cons[1:]):
                if b["at"] <= a["at"]:
                    continue
                part = substring(line, a["at"], b["at"], normalized=True)
                km = part.length
                cost = km / speed * 60  # minutes
                coords = list(part.coords)
                self.adj.setdefault(a["connector_id"], []).append((b["connector_id"], cost, km, coords))
                self.adj.setdefault(b["connector_id"], []).append((a["connector_id"], cost, km, coords[::-1]))

    def nearest(self, x, y, ok=lambda n: True):
        best, bd = None, 1e9
        for n, (nx, ny) in self.node_xy.items():
            if n not in self.adj or not ok(n):
                continue
            d = (nx - x) ** 2 + (ny - y) ** 2
            if d < bd:
                best, bd = n, d
        return best

    def route(self, a, b):
        dist = {a: 0.0}
        prev = {}
        pq = [(0.0, a)]
        while pq:
            d, n = heapq.heappop(pq)
            if n == b:
                break
            if d > dist[n]:
                continue
            for m, cost, km, coords in self.adj.get(n, []):
                nd = d + cost
                if nd < dist.get(m, 1e18):
                    dist[m] = nd
                    prev[m] = (n, km, coords)
                    heapq.heappush(pq, (nd, m))
        if b not in dist:
            raise SystemExit(f"no route {a} → {b}")
        pts, km, n = [], 0.0, b
        while n != a:
            p, k, coords = prev[n]
            pts = coords[:-1] + pts if pts else coords
            km += k
            n = p
        return pts, km, dist[b]


# ---------------------------------------------------------------- map layers


def clip_lines(geom, rect):
    g = geom.intersection(rect)
    if g.is_empty:
        return []
    parts = getattr(g, "geoms", [g])
    out = []
    for p in parts:
        if p.geom_type == "LineString":
            out.append(p)
        elif p.geom_type == "MultiLineString":
            out.extend(p.geoms)
    return out


def rounded(coords, nd=3):
    return [[round(x, nd), round(y, nd)] for x, y in coords]


def map_layers(box_ll, proj, rect_km, detail):
    """Roads, water, parks and neighbourhood labels inside box_ll ([w, s, e, n]), in km."""
    w, s, e, n = box_ll
    seg_rows = read(
        "transportation/segment", box_ll, ["names", "class", "subtype", "geometry"]
    )
    roads = []
    for r in seg_rows:
        cls = r["class"]
        if r["subtype"] != "road" or cls not in DRIVABLE:
            continue
        if cls == "service" or (not detail and cls in ("residential", "living_street", "unclassified")):
            continue
        g = proj.geom(wkb.loads(r["geometry"]))
        for part in clip_lines(g, rect_km):
            simp = part.simplify(0.004)
            roads.append({"c": cls, "n": short_street((r["names"] or {}).get("primary")), "p": rounded(simp.coords)})

    water, rivers = [], []
    for r in read("base/water", box_ll, ["subtype", "class", "geometry"]):
        g = proj.geom(wkb.loads(r["geometry"])).intersection(rect_km)
        if g.is_empty:
            continue
        for p in getattr(g, "geoms", [g]):
            if p.geom_type == "Polygon" and p.area > 0.002:
                p = p.simplify(0.006)
                water.append([rounded(p.exterior.coords)] + [rounded(i.coords) for i in p.interiors])
            elif p.geom_type == "LineString" and r["class"] in ("river", "canal", "stream"):
                rivers.append({"w": 2 if r["class"] == "river" else 1, "p": rounded(p.simplify(0.006).coords)})

    parks = []
    for r in read("base/land_use", box_ll, ["class", "subtype", "geometry"]):
        if r["class"] not in SHOWN_LANDUSE:
            continue
        g = proj.geom(wkb.loads(r["geometry"])).intersection(rect_km)
        for p in getattr(g, "geoms", [g]):
            if p.geom_type == "Polygon" and p.area > 0.004:
                p = p.simplify(0.006)
                parks.append([rounded(p.exterior.coords)])

    labels = []
    for r in read("divisions/division", box_ll, ["names", "subtype", "class", "geometry"]):
        if r["subtype"] not in ("neighborhood", "macrohood", "microhood", "locality"):
            continue
        name = (r["names"] or {}).get("primary")
        if not name:
            continue
        p = proj.geom(wkb.loads(r["geometry"]))
        if p.geom_type != "Point" or not rect_km.contains(p):
            continue
        labels.append({"t": name, "k": r["subtype"], "x": round(p.x, 3), "y": round(p.y, 3)})
    return roads, water, rivers, parks, labels


# ---------------------------------------------------------------- per offer

# Page sizes (CSS px) and the band above the cards the route must fit in; template.html uses the same.
# 468 px wide is a phone at a slightly smaller display size, so the map shows more above the cards.
FORMATS = {"story": (468, 832, 44, 236), "post": (468, 585, 10, 200)}


def fit(route_bbox, fmt):
    """px per km and the route's centre in page px for a format (mirrors template.html)."""
    W, H, band_top, band_h = FORMATS[fmt]
    x0, y0, x1, y1 = route_bbox
    rw, rh = max(x1 - x0, 0.3), max(y1 - y0, 0.3)
    s = min((W - 96) / rw, (band_h - 56) / rh)
    return s, (W / 2, band_top + band_h / 2)


def build_offer(o, t):
    rx, ry = o["restaurant"]["near"]
    proj = Proj(rx, ry)
    pts_ll = [o["restaurant"]["near"], o["courier"], o["dropoff"]["near"]]
    lons = [p[0] for p in pts_ll]
    lats = [p[1] for p in pts_ll]
    pad = 0.012
    gbox = [min(lons) - pad, min(lats) - pad, max(lons) + pad, max(lats) + pad]

    # The restaurant: a real place with this name near the point.
    places = read("places/place", [rx - 0.01, ry - 0.01, rx + 0.01, ry + 0.01],
                  ["names", "basic_category", "confidence", "addresses", "geometry"])
    named = [p for p in places if (p["names"] or {}).get("primary") == o["restaurant"]["name"]]
    if not named:
        raise SystemExit(f"{o['id']}: no place called {o['restaurant']['name']!r} near {o['restaurant']['near']}")
    pl = min(named, key=lambda p: wkb.loads(p["geometry"]).distance(Point(rx, ry)))
    pg = wkb.loads(pl["geometry"])
    rest_xy = proj.xy(pg.x, pg.y)

    segs = read("transportation/segment", gbox, ["id", "names", "class", "subtype", "connectors", "geometry"])
    graph = Graph([s for s in segs if s["subtype"] == "road"], proj)
    street_ok = lambda n: graph.node_classes[n] & {"residential", "tertiary", "secondary", "primary", "living_street", "unclassified"}  # noqa: E731
    courier = graph.nearest(*proj.xy(*o["courier"]), ok=street_ok)
    pickup = graph.nearest(*rest_xy)

    # Drop-off: the nearest corner of two named streets, at least one of them a side street.
    def corner(n):
        return len(graph.node_names[n]) >= 2 and graph.node_classes[n] & {"residential", "living_street", "tertiary"}

    drop = graph.nearest(*proj.xy(*o["dropoff"]["near"]), ok=corner)
    names = sorted(graph.node_names[drop], key=len)
    drop_label = f"{names[0]} & {names[1]}, {o['dropoff']['city']}"

    leg1, km1, min1 = graph.route(courier, pickup)
    leg2, km2, min2 = graph.route(pickup, drop)
    km = D(str(round(km1 + km2, 1)))
    # Traffic and lights (×1.3), then the pickup (6 min) and the hand-off (2 min); close to the
    # minutes on real cards (5.0 km in 24 min, 12.9 km in 38 min).
    minutes = int(round((min1 + min2) * 1.3 + 6 + 2))
    if o.get("addOn"):
        km = D(str(round(km2, 1)))
        minutes = int(round(min2 * 1.3 + 2))

    wait = t["perOrderWaitMin"] * o["orders"] if o["orders"] > 1 else 0
    if "pay" in o:
        pay = D(o["pay"])
    elif "hourly" in o:
        pay = (D(o["hourly"]) * (minutes + wait) / 60).quantize(D("0.01"), ROUND_HALF_UP)
    else:
        pay = (D(o["perKm"]) * km).quantize(D("0.01"), ROUND_HALF_UP)
    ev = score(pay, minutes, km, o["orders"], o.get("addOn", False), t)
    if o.get("unreadable"):
        ev = {"verdict": "GREY", "reason": "Check this offer yourself", "hourly": None, "perKm": None}
    exp = o["expect"]
    if (ev["verdict"], ev["reason"]) != (exp["verdict"], exp["reason"]):
        raise SystemExit(f"{o['id']}: got {ev['verdict']} {ev['reason']!r}, planned {exp}  (pay {pay}, {minutes} min, {km} km)")

    def trip(m, k):
        tm = f"{m // 60} hr {m % 60} min" if m >= 60 else f"{m} min"
        return f"+ {tm} (+ {k} km) total" if o.get("addOn") else f"{tm} ({k} km) total"

    route_pts = leg1 + leg2[1:]
    xs = [p[0] for p in route_pts] + [rest_xy[0]]
    ys = [p[1] for p in route_pts] + [rest_xy[1]]
    rb = (min(xs), min(ys), max(xs), max(ys))

    # The map area to draw: everything either format can show.
    ext = [1e9, 1e9, -1e9, -1e9]
    detail = True
    for fmt in FORMATS:
        W, H, band_top, band_h = FORMATS[fmt]
        s, (cx_px, cy_px) = fit(rb, fmt)
        cx, cy = (rb[0] + rb[2]) / 2, (rb[1] + rb[3]) / 2
        ext = [min(ext[0], cx - cx_px / s - 0.3), min(ext[1], cy - (H - cy_px) / s - 0.3),
               max(ext[2], cx + (W - cx_px) / s + 0.3), max(ext[3], cy + cy_px / s + 0.3)]
        detail = detail and s > 16  # side streets only when zoomed in enough to read them
    rect_km = shp_box(*ext)
    w, s_ = proj.lonlat(ext[0], ext[1])
    e, n = proj.lonlat(ext[2], ext[3])
    roads, water, rivers, parks, labels = map_layers([round(w, 4), round(s_, 4), round(e, 4), round(n, 4)], proj, rect_km, detail)

    mid = len(leg1)
    return {
        "id": o["id"],
        "verdict": ev["verdict"],
        "word": WORD[ev["verdict"]],
        "big": ev["hourly"], "bigUnit": "/hr",
        "chip": f"{ev['perKm']}/km" if ev["perKm"] else None,
        "reason": ev["reason"],
        "card": {
            "kind": "Add a delivery" if o.get("addOn") else ("Delivery" + (f" ({o['orders']})" if o["orders"] > 1 else "")),
            "exclusive": o.get("exclusive", False),
            "pay": ("+ " if o.get("addOn") else "") + f"${pay}",
            "trip": trip(minutes, km),
            "place": f"{o['restaurant']['name']} ({o['restaurant']['street']})",
            "drop": drop_label,
            "timer": o["timer"],
        },
        "status": {"clock": o["clock"], "battery": o["battery"]},
        "map": {
            "routeBox": [round(v, 3) for v in rb],
            "route": rounded(route_pts),
            "split": mid,
            "courier": rounded([graph.node_xy[courier]])[0],
            "pickup": rounded([rest_xy])[0],
            "drop": rounded([graph.node_xy[drop]])[0],
            "roads": roads, "water": water, "rivers": rivers, "parks": parks, "labels": labels,
        },
        "debug": {"pay": str(pay), "minutes": minutes, "km": str(km), "scored": ev.get("scoredMinutes")},
    }


def main():
    cfg = json.load(open(os.path.join(ROOT, "offers.json")))
    t = {k: (D(v) if isinstance(v, str) else v) for k, v in cfg["thresholds"].items()}
    self_test(t)
    only = sys.argv[1:]
    out = []
    for o in cfg["offers"]:
        if only and o["id"] not in only:
            continue
        r = build_offer(o, t)
        c = r["card"]
        print(f"{r['id']}  {r['word']:<12} {r['big'] or '':>8}/hr  {r['chip'] or '':>10}  {c['pay']:>8}  {c['trip']:<28} {c['place']}  →  {c['drop']}   [{len(r['map']['roads'])} roads]")
        out.append(r)
    path = os.path.join(ROOT, "data", "offers.out.json")
    if only and os.path.exists(path):
        # Rebuilding some offers keeps the others.
        kept = [x for x in json.load(open(path))["offers"] if x["id"] not in only]
        out = sorted(kept + out, key=lambda x: x["id"])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump({"formats": FORMATS, "offers": out}, f, separators=(",", ":"))


if __name__ == "__main__":
    main()
