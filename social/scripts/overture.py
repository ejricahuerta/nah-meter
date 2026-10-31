"""Read Overture Maps (public S3, no account) for a lon/lat box. Cached per box in social/data/."""

import hashlib
import json
import os
import pickle

import pyarrow.compute as pc
import pyarrow.dataset as ds
import pyarrow.fs as fs

RELEASE = "2026-10-21.0"
BASE = f"overturemaps-us-west-2/release/{RELEASE}/"
DATA = os.path.join(os.path.dirname(__file__), "..", "data")

_s3 = None


def _fs():
    global _s3
    if _s3 is None:
        _s3 = fs.S3FileSystem(anonymous=True, region="us-west-2")
    return _s3


def _box_filter(w, s, e, n):
    b = pc.field("bbox")
    return (
        (pc.field("bbox", "xmax") >= w)
        & (pc.field("bbox", "xmin") <= e)
        & (pc.field("bbox", "ymax") >= s)
        & (pc.field("bbox", "ymin") <= n)
    )


def read(theme_type, box, columns, extra=None):
    """Rows of `theme=…/type=…` touching box [w, s, e, n], as dicts. Cached on disk."""
    key = hashlib.sha1(json.dumps([RELEASE, theme_type, box, columns, str(extra)]).encode()).hexdigest()[:16]
    os.makedirs(DATA, exist_ok=True)
    path = os.path.join(DATA, f"{theme_type.replace('/', '_')}-{key}.pkl")
    if os.path.exists(path):
        with open(path, "rb") as f:
            return pickle.load(f)
    theme, typ = theme_type.split("/")
    d = ds.dataset(f"{BASE}theme={theme}/type={typ}/", filesystem=_fs(), format="parquet")
    flt = _box_filter(*box)
    if extra is not None:
        flt = flt & extra
    rows = d.to_table(filter=flt, columns=columns).to_pylist()
    with open(path, "wb") as f:
        pickle.dump(rows, f)
    return rows
