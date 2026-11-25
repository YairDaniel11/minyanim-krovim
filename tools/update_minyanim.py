#!/usr/bin/env python3
"""
מוריד את נתוני זמני התפילות הארצי ישירות משרתי "נדרים פלוס – פרויקט זמני התפילות הארצי"
(matara.pro/nedarimplus/zmanim, נקודות הקצה הציבוריות של Zmanim/Manage.aspx) וממיר אותו
לפורמט הקובץ של האפליקציה: data/minyanim-israel.json.

הפורמט של נקודות הקצה (כפי שמתועד גם ב-github.com/rishumit/hebrew-maps ו-yossi8765/MapsOffline):
  GetLocation                      - כל בתי הכנסת: MosadId, Loc="lat*lon"
  GetMosadMinyan&MosadId=          - לוח שבועי (T תפילה, H שעה, D ימים, L אולם, C הערה) - בלי שם
  GetNearestMinyan&lat&lng&radius  - מניינים ב-24 השעות הקרובות, עם שם (L), כתובת (A) ו-id (M);
                                     המקור היחיד לשמות, ולכן סורקים אותו סביב כל בית כנסת.
ההורדה המלאה אורכת כ-15 דקות (כ-4,000 בקשות, בקצב מנומס של בקשה כל 0.2 שניות).

שימוש:
  python tools/update_minyanim.py                       # הורדה ישירה (ברירת מחדל)
  python tools/update_minyanim.py --limit 20 --out x.json   # בדיקה קטנה (לא לפרסום)
  python tools/update_minyanim.py --src קובץ.mapsdata    # חלופה: צילום של MapsOffline
תלויות: ספריית הסטנדרט בלבד.

פורמט הפלט תואם לפורמט הרגיל של האפליקציה (shuls -> prayers -> weekday/friday/shabbat),
ומוסיף לכל בית כנסת: id, address, lat, lon. שעה עם הערה נכתבת כאובייקט {"t":"HH:mm","n":"הערה"}.
"""
import argparse
import datetime
import io
import json
import os
import re
import sys
import threading
import time
import urllib.parse
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

SRC_URL = "https://github.com/yossi8765/MapsOffline/releases/download/data-latest/minyanim.mapsdata"
SOURCE_NAME = "נדרים פלוס – פרויקט זמני תפילות הארצי"
SOURCE_URL = "https://www.matara.pro/nedarimplus/zmanim/"

PRAYERS = {"שחרית": "shacharit", "מנחה": "mincha", "ערבית": "arvit"}  # "שיעור" וכו' - מדלגים
WEEKDAY = "אבגדה"
MIN_SHULS = 800  # הגנה: אם יש פחות מזה, כנראה שהמקור נשבר - לא מחליפים את הקובץ הקיים

# ---------------------------------------------------------------- הורדה ישירה מנדרים פלוס

BASE = "https://www.matara.pro/nedarimplus/Zmanim/Manage.aspx"
DAY_LETTERS = "אבגדהוש"
WORKERS = 4
GAP = 0.2  # שניות מינימום בין שתי בקשות (בכל התהליכונים יחד) - מנומס לשרת של עמותה
NAME_SUFFIXES = [" - נ.פ", " - נ. פ", " - נפ", " - מ.ש.ג.ב", " - מ.ה", " - א.ח", " - אח לאח", "-נ.פ", "- נ.פ"]


class Fetcher:
    def __init__(self):
        self.lock = threading.Lock()
        self.next = 0.0
        self.errors = 0
        self.requests = 0

    def json(self, **params):
        url = BASE + "?" + urllib.parse.urlencode(params)
        for attempt in range(3):
            with self.lock:  # קצב גלובלי
                now = time.monotonic()
                if self.next > now:
                    time.sleep(self.next - now)
                self.next = time.monotonic() + GAP
                self.requests += 1
            try:
                req = urllib.request.Request(url, headers={"User-Agent": "minyanim-krovim-data-updater"})
                with urllib.request.urlopen(req, timeout=60) as r:
                    body = r.read().decode("utf-8-sig", "replace").strip()
                if not body.startswith("{"):
                    return None  # "" = אין נתונים, "Wrong radius" ...
                return json.loads(body)
            except Exception:
                time.sleep(2 + 3 * attempt)
        self.errors += 1
        return None


def _clean(x):
    return " ".join((x or "").replace("‏", "").replace("‎", "").split())


def clean_src_name(x):
    n = _clean(x)
    while True:
        before = n
        for suf in NAME_SUFFIXES:
            if n.endswith(suf):
                n = n[: -len(suf)].rstrip()
        if n == before:
            break
    return n.strip("- \t") or None


def norm_time(x):
    m = re.match(r"^\s*(\d{1,2})\s*[:.]\s*(\d{1,2})\s*$", x or "")
    if not m:
        return None
    h, mi = int(m.group(1)), int(m.group(2))
    if h > 24 or mi > 59:
        return None
    return "%02d:%02d" % (h % 24, mi)


def parse_loc(x):
    try:
        lat, lon = [float(v) for v in (x or "").split("*")]
    except ValueError:
        return None
    if 29.0 <= lat <= 33.6 and 34.0 <= lon <= 36.0:
        return [round(lon, 6), round(lat, 6)]
    return None


def parse_timetable(v):
    out = []
    for r in (v or {}).get("data") or []:
        t = norm_time(r.get("H"))
        days = "".join(c for c in DAY_LETTERS if c in (r.get("D") or ""))
        prayer = _clean(r.get("T"))
        if not t or not days or not prayer:
            continue
        mn = {"prayer": prayer, "time": t, "days": days}
        if _clean(r.get("L")):
            mn["hall"] = _clean(r.get("L"))
        if _clean(r.get("C")):
            mn["note"] = _clean(r.get("C"))
        out.append(mn)
    return out


def fetch_live(limit=None, previous_path=None):
    """מחזיר snapshot במבנה {"fetched","mosads":[{id,name,address,locations,minyanim}]}."""
    f = Fetcher()
    loc = f.json(Action="GetLocation")
    if not loc:
        sys.exit("ERROR: GetLocation returned nothing - is matara.pro reachable?")
    mosads, index = [], {}
    for d in loc.get("Details") or []:
        mid = (d.get("MosadId") or "").strip()
        ll = parse_loc(d.get("Loc"))
        if not ll or not mid.isdigit() or not mid.lstrip("0"):
            continue  # "0" = רשומת בדיקה
        if mid not in index:
            index[mid] = {"id": mid, "name": None, "address": None, "locations": [], "minyanim": []}
            mosads.append(index[mid])
        if ll not in index[mid]["locations"]:
            index[mid]["locations"].append(ll)
    if limit:
        mosads = mosads[:limit]
    print("mosads: %d" % len(mosads), flush=True)

    with ThreadPoolExecutor(WORKERS) as ex:
        tables = list(ex.map(lambda m: parse_timetable(f.json(Action="GetMosadMinyan", MosadId=m["id"])), mosads))
    for m, t in zip(mosads, tables):
        m["minyanim"] = t
    with_times = [m for m in mosads if m["minyanim"]]
    print("with timetable: %d (requests so far %d)" % (len(with_times), f.requests), flush=True)

    # שמות: סריקת "המניין הקרוב" סביב כל בית כנסת - קודם מעגל של 30 מ' (שולף לעיתים כמה שכנים
    # בבת אחת), ואז מעגל של 1 מ' סביב כל מיקום של השאר
    names = {}
    for radius, all_locs in ((0.03, False), (0.001, True)):
        todo = []
        for m in with_times:
            if m["id"] in names:
                continue
            todo += m["locations"] if all_locs else m["locations"][:1]

        def sweep(ll, radius=radius):
            v = f.json(Action="GetNearestMinyan", lat=ll[1], lng=ll[0], radius=radius)
            found = {}
            for r in (v or {}).get("data") or []:
                mid = (r.get("M") or "").strip()
                nm = clean_src_name(r.get("L"))
                if mid and nm:
                    found.setdefault(mid, (nm, _clean(r.get("A")) or None))
            return found

        with ThreadPoolExecutor(WORKERS) as ex:
            for found in ex.map(sweep, todo):
                for k, v in found.items():
                    names.setdefault(k, v)
        print("named after radius %s: %d (requests %d)" % (radius, len(names), f.requests), flush=True)

    # שמות שלא נראו הפעם (למשל בתי כנסת של שבת בלבד) - נשמרים מהקובץ הקודם
    prev = {}
    if previous_path and os.path.exists(previous_path):
        try:
            for s in json.load(open(previous_path, encoding="utf-8")).get("shuls", []):
                prev[s["id"]] = (s["name"], s.get("address"))
        except Exception:
            pass
    for m in mosads:
        n = names.get(m["id"]) or prev.get(m["id"])
        if n:
            m["name"], m["address"] = n
    if f.errors > max(20, len(mosads) // 10):
        sys.exit("ERROR: %d requests failed - keeping previous data" % f.errors)
    print("done: %d requests, %d errors" % (f.requests, f.errors), flush=True)
    tz = datetime.timezone(datetime.timedelta(hours=2))
    return {"fetched": datetime.datetime.now(tz).strftime("%Y-%m-%d"), "mosads": mosads}


# ---------------------------------------------------------------- חלופה: צילום של MapsOffline

def load_snapshot(src):
    raw = open(src, "rb").read()
    z = zipfile.ZipFile(io.BytesIO(raw))
    return json.loads(z.read("minyanim/minyanim.json").decode("utf-8"))


# ---------------------------------------------------------------- המרה לפורמט האפליקציה

def clean_name(name):
    return " ".join((name or "").split())


def build_shul(m):
    name = clean_name(m.get("name"))
    if not name or not m.get("minyanim") or not m.get("locations"):
        return None
    lon, lat = m["locations"][0]
    # day_type -> prayer_key -> {time: set(notes)}
    acc = {"weekday": {}, "friday": {}, "shabbat": {}}
    for mn in m["minyanim"]:
        key = PRAYERS.get(mn.get("prayer"))
        if not key:
            continue
        days = mn.get("days", "")
        extra = " ".join(x for x in [mn.get("hall"), mn.get("note")] if x)
        wd = [c for c in WEEKDAY if c in days]
        targets = []
        if wd:
            # לא כל ימי החול - מציינים באילו ימים, כדי לא להטעות
            note = extra
            if len(wd) < 5:
                note = " ".join(x for x in ["ימים " + ",".join(wd), extra] if x)
            targets.append(("weekday", note))
        if "ו" in days:
            targets.append(("friday", extra))
        if "ש" in days:
            targets.append(("shabbat", extra))
        for day_type, note in targets:
            acc[day_type].setdefault(key, {}).setdefault(mn["time"], set()).add(note)
    prayers = {}
    for day_type, per_prayer in acc.items():
        out = {}
        for key in ("shacharit", "mincha", "arvit"):
            times = per_prayer.get(key)
            if not times:
                continue
            items = []
            for t in sorted(times):
                note = " | ".join(sorted(n for n in times[t] if n))
                items.append({"t": t, "n": note} if note else t)
            out[key] = items
        if out:
            prayers[day_type] = out
    if not prayers:
        return None
    shul = {"id": m["id"], "name": name, "lat": round(lat, 5), "lon": round(lon, 5)}
    if m.get("address"):
        shul["address"] = clean_name(m["address"])
    shul["prayers"] = prayers
    return shul


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", help="קובץ .mapsdata של MapsOffline (במקום הורדה ישירה)")
    ap.add_argument("--limit", type=int, help="בדיקה: רק N בתי כנסת ראשונים (לא לפרסום)")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "data", "minyanim-israel.json"))
    args = ap.parse_args()

    out = os.path.abspath(args.out)
    snap = load_snapshot(args.src) if args.src else fetch_live(args.limit, out)
    shuls = [s for s in (build_shul(m) for m in snap.get("mosads", [])) if s]
    if len(shuls) < MIN_SHULS and not args.limit:
        sys.exit("ERROR: only %d synagogues in source - refusing to overwrite data" % len(shuls))
    shuls.sort(key=lambda s: int(s["id"]))

    # "generated" נכתב ראשון (האפליקציה קוראת אותו מתחילת הקובץ בלי לפענח את כולו)
    doc = {
        "format": 2,
        "generated": snap.get("fetched") or datetime.date.today().isoformat(),
        "source": SOURCE_NAME,
        "sourceUrl": SOURCE_URL,
        "shuls": shuls,
    }
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print("wrote %s: %d synagogues, generated %s, %.2f MB" % (out, len(shuls), doc["generated"], os.path.getsize(out) / 1e6))


if __name__ == "__main__":
    main()
