"""Build reproducible face-image sets from LFW for testing the recognition pipeline.

Source: Hugging Face mirror `logasja/lfw`, pinned to a dataset commit and verified by SHA-256.
(The canonical LFW host, vis-www.cs.umass.edu, is unreachable from the development machine.)

Outputs (both gitignored - LFW images are never committed to this public repository):

  .cache/lfw/eval_small/   small set used by the JVM tests
      enroll/<Name>/NN.jpg   5 images per enrolled identity
      probe/<Name>/NN.jpg    1 held-out image per enrolled identity
      unknown/<Name>/00.jpg  identities that are NEVER enrolled
      manifest.json

  tools/dataset_out/bulk/  ~100-person gallery for on-device bulk enrolment (Phase 1 M5)
      same layout + manifest.json

Selection is deterministic (fixed seed, sorted names). Re-running reproduces the same files.

LFW is celebrity web photography skewed toward white male adults. These sets exercise the
software; they say nothing about performance on the intended population.

Usage:
    .venv/Scripts/python.exe tools/prepare_dataset.py
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import shutil
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

import pyarrow.parquet as pq

REPO_ROOT = Path(__file__).resolve().parent.parent

DATASET = "logasja/lfw"
REVISION = "0ee47979927a48dadf11083cb53b51439fa92dc9"
PARQUET_URL = (
    f"https://huggingface.co/datasets/{DATASET}/resolve/{REVISION}/data/train-00000-of-00001.parquet"
)
PARQUET_SHA256 = "40a011f060ab5f67b9363c5bcf228a7bcde1d871e313717d7967fe592684f91b"
PARQUET_SIZE = 188_443_388

SEED = 20260924
MIN_IMAGES = 6  # 5 enrolment images + at least 1 held-out probe


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_parquet(cache_dir: Path) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    target = cache_dir / f"lfw-{REVISION[:7]}.parquet"
    if target.is_file() and target.stat().st_size == PARQUET_SIZE and sha256_file(target) == PARQUET_SHA256:
        print(f"parquet: cached, checksum OK ({target})")
        return target
    part = target.with_suffix(".part")
    print(f"parquet: downloading {PARQUET_SIZE / 1e6:.0f} MB from {DATASET}@{REVISION[:7]} ...")
    with urllib.request.urlopen(PARQUET_URL, timeout=120) as resp, part.open("wb") as out:
        shutil.copyfileobj(resp, out, length=1 << 20)
    actual = sha256_file(part)
    if actual != PARQUET_SHA256:
        part.unlink()
        sys.exit(f"parquet checksum mismatch: expected {PARQUET_SHA256}, got {actual}")
    part.replace(target)
    print("parquet: downloaded, checksum OK")
    return target


def load_identities(parquet_path: Path) -> dict[str, list[bytes]]:
    table = pq.read_table(parquet_path)
    meta = json.loads(table.schema.metadata[b"huggingface"])
    names = meta["info"]["features"]["label"]["names"]
    labels = table.column("label").to_pylist()
    images = table.column("image").to_pylist()
    by_name: dict[str, list[bytes]] = defaultdict(list)
    for label, image in zip(labels, images):
        data = image["bytes"]
        if data[:2] != b"\xff\xd8":
            sys.exit(f"unexpected non-JPEG image for {names[label]}")
        by_name[names[label]].append(data)  # row order is preserved -> deterministic
    return by_name


def write_set(
    out_dir: Path,
    by_name: dict[str, list[bytes]],
    enrolled: list[str],
    unknown: list[str],
    enroll_count: int,
    max_probes: int,
    description: str,
) -> dict:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    files = []

    def put(role: str, name: str, idx: int, data: bytes) -> None:
        path = out_dir / role / name / f"{idx:02d}.jpg"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        files.append(
            {"path": path.relative_to(out_dir).as_posix(), "identity": name, "role": role,
             "sha256": sha256_bytes(data)}
        )

    for name in enrolled:
        imgs = by_name[name]
        for i, data in enumerate(imgs[:enroll_count]):
            put("enroll", name, i, data)
        for i, data in enumerate(imgs[enroll_count:enroll_count + max_probes]):
            put("probe", name, i, data)
    for name in unknown:
        put("unknown", name, 0, by_name[name][0])

    manifest = {
        "description": description,
        "source": {"dataset": DATASET, "revision": REVISION, "parquet_sha256": PARQUET_SHA256},
        "seed": SEED,
        "enroll_images_per_identity": enroll_count,
        "counts": {
            "enrolled_identities": len(enrolled),
            "enroll_images": sum(f["role"] == "enroll" for f in files),
            "probe_images": sum(f["role"] == "probe" for f in files),
            "unknown_identities": len(unknown),
        },
        "files": files,
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=1), encoding="utf-8")
    return manifest["counts"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--enroll", type=int, default=5, help="images per enrolled identity (default 5)")
    parser.add_argument("--bulk-identities", type=int, default=100)
    parser.add_argument("--bulk-unknown", type=int, default=50)
    parser.add_argument("--eval-identities", type=int, default=10)
    parser.add_argument("--eval-unknown", type=int, default=10)
    args = parser.parse_args()

    by_name = load_identities(fetch_parquet(REPO_ROOT / ".cache" / "lfw"))
    multi = sorted(n for n, imgs in by_name.items() if len(imgs) >= max(MIN_IMAGES, args.enroll + 1))
    single = sorted(n for n, imgs in by_name.items() if len(imgs) == 1)
    print(f"LFW: {sum(map(len, by_name.values()))} images, {len(by_name)} identities; "
          f"{len(multi)} with >= {args.enroll + 1} images; {len(single)} with exactly 1")

    rng = random.Random(SEED)
    eval_enrolled = sorted(rng.sample(multi, args.eval_identities))
    eval_unknown = sorted(rng.sample(single, args.eval_unknown))
    remaining_multi = [n for n in multi if n not in eval_enrolled]
    remaining_single = [n for n in single if n not in eval_unknown]
    bulk_enrolled = sorted(rng.sample(remaining_multi, min(args.bulk_identities, len(remaining_multi))))
    bulk_unknown = sorted(rng.sample(remaining_single, args.bulk_unknown))

    eval_counts = write_set(
        REPO_ROOT / ".cache" / "lfw" / "eval_small", by_name, eval_enrolled, eval_unknown,
        args.enroll, max_probes=1,
        description="Small reproducible set for JVM tests: known-person, unknown-person, and derived "
                    "poor-quality / multi-face / no-face cases.",
    )
    bulk_counts = write_set(
        REPO_ROOT / "tools" / "dataset_out" / "bulk", by_name, bulk_enrolled, bulk_unknown,
        args.enroll, max_probes=3,
        description="Phase 1 bulk-enrolment gallery (pushed to the device with adb).",
    )
    print("eval_small:", eval_counts)
    print("bulk:      ", bulk_counts)


if __name__ == "__main__":
    main()
