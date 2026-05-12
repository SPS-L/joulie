# SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
#                         Sustainable Power Systems Lab <https://sps-lab.org>
# SPDX-License-Identifier: GPL-3.0-or-later
"""Update Joulie's EV models database from the OpenEV Data dataset.

Fetches the latest release of `open-ev-data/open-ev-data-dataset`,
transforms each vehicle into Joulie's compact schema, and publishes the
result as the `ev_models.json` asset on the `ev-db-latest` GitHub release
of `SPS-L/joulie` (creating the release if it does not exist).

Usage
-----
    GITHUB_TOKEN=<pat> python3 scripts/update_ev_db.py

Suggested crontab (run on the 1st of every month at 03:00 server time):
    0 3 1 * * GITHUB_TOKEN=<token> /path/to/venv/bin/python3 \
      /path/to/scripts/update_ev_db.py >> /var/log/joulie-ev-db.log 2>&1

The preferred path is the scheduled `.github/workflows/ev-db-update.yml`
GitHub Actions workflow, which injects the auto-issued
`secrets.GITHUB_TOKEN` and runs on the first of every month.
"""

from __future__ import annotations

import datetime as _dt
import io
import json
import os
import pathlib
import sys
from typing import Any

import requests

SUPPLEMENT_PATH = pathlib.Path(__file__).parent / "ev_models_supplement.json"

UPSTREAM_LATEST_URL = (
    "https://api.github.com/repos/open-ev-data/open-ev-data-dataset/releases/latest"
)
TARGET_REPO = "SPS-L/joulie"
TARGET_TAG = "ev-db-latest"
TARGET_ASSET_NAME = "ev_models.json"

# Adapter table mapping known upstream field aliases to the canonical
# Joulie field names. Each entry is a tuple of dotted paths — the
# resolver walks each path top-down and returns the first non-null
# value. Unknown upstream rows fall through and get dropped silently.
#
# OpenEV Data v1.x ships a nested schema (`make.name`, `battery.pack_capacity_kwh_net`,
# `range.rated[cycle=wltp].range_km`); legacy flat alternatives are
# kept here so the script keeps working if the upstream schema gets
# flattened in a future release.
MAKE_KEYS = ("make.name", "make", "make_name", "brand", "manufacturer")
MODEL_KEYS = ("model.name", "model", "model_name")
VARIANT_KEYS = ("variant.name", "variant", "variant_name", "trim.name", "trim")
YEAR_KEYS = ("year", "release_year", "model_year", "availability.start_year")
BATTERY_KEYS = (
    "battery.pack_capacity_kwh_net",  # preferred (net / usable)
    "battery_capacity_usable_kwh",
    "usable_battery_size",
    "battery_kwh",
    "battery_capacity_kwh",
    "battery.pack_capacity_kwh_gross",  # last-resort fallback (gross)
    "battery_capacity_gross_kwh",
)
WLTP_CONSUMPTION_KEYS = (
    "energy_consumption_wltp_kwh_100km",
    "wltp_kwh_100km",
    "consumption_wltp",
    "consumption.wltp_kwh_100km",
)

MIN_BATTERY_KWH = 5.0  # drop e-bikes / hybrids leaking through
VALIDATION_FLOOR = 50  # consumer (TASK-91) requires this many rows


def log_info(msg: str) -> None:
    print(f"[INFO]  {msg}", flush=True)


def log_error(msg: str) -> None:
    print(f"[ERROR] {msg}", flush=True, file=sys.stderr)


def die(msg: str, code: int = 1) -> "Any":
    log_error(msg)
    sys.exit(code)


def _pick(record: dict, keys: tuple) -> Any:
    """Return the first non-null value resolved from `record`.

    Each entry in `keys` is a dotted path: `"battery.pack_capacity_kwh_net"`
    walks `record["battery"]["pack_capacity_kwh_net"]`. Missing
    intermediate keys, non-dict intermediate values, and `None`
    leaves are all skipped, falling through to the next path.
    """
    for path in keys:
        cur: Any = record
        for segment in path.split("."):
            if not isinstance(cur, dict):
                cur = None
                break
            cur = cur.get(segment)
            if cur is None:
                break
        if cur is not None:
            return cur
    return None


def _wltp_range_km(record: dict) -> float | None:
    """Walk OpenEV Data's `range.rated[]` list for the WLTP entry.

    Returns the rated WLTP range in km if present, else None. Earlier
    upstream schemas inlined `range_km_wltp` at the top level; both
    work.
    """
    rated = record.get("range", {})
    if isinstance(rated, dict):
        rated_list = rated.get("rated")
        if isinstance(rated_list, list):
            for entry in rated_list:
                if not isinstance(entry, dict):
                    continue
                cycle = str(entry.get("cycle") or "").lower()
                if cycle == "wltp":
                    return _as_float(entry.get("range_km"))
    # Legacy flat alternatives:
    for k in ("range_km_wltp", "wltp_range_km"):
        v = _as_float(record.get(k))
        if v is not None:
            return v
    return None


def _as_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _as_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _as_str(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip()


def transform(raw: list[dict]) -> list[dict]:
    """Map raw upstream rows to Joulie's compact schema, filter, sort.

    WLTP kWh/100km is taken directly from the upstream record when it
    ships a consumption figure (legacy schema). For OpenEV Data v1.x,
    which only ships the rated range, we derive consumption from
    `battery_kwh / range_km * 100`. The derived value is a slightly
    optimistic floor since the manufacturer's published WLTP test
    treats the pack's nominal SoC band; close enough for Joulie's
    autocomplete-defaults purpose.
    """
    out: list[dict] = []
    for r in raw:
        if not isinstance(r, dict):
            continue
        make = _as_str(_pick(r, MAKE_KEYS))
        model = _as_str(_pick(r, MODEL_KEYS))
        if not make or not model:
            continue
        battery = _as_float(_pick(r, BATTERY_KEYS))
        if battery is None or battery < MIN_BATTERY_KWH:
            continue
        wltp = _as_float(_pick(r, WLTP_CONSUMPTION_KEYS))
        if wltp is None:
            range_km = _wltp_range_km(r)
            if range_km is not None and range_km > 0:
                wltp = round(battery / range_km * 100.0, 2)
        out.append(
            {
                "make": make,
                "model": model,
                "variant": _as_str(_pick(r, VARIANT_KEYS)),
                "year": _as_int(_pick(r, YEAR_KEYS)),
                "battery_kwh": round(battery, 2),
                "wltp_kwh_100km": wltp,
            }
        )
    out.sort(
        key=lambda v: (
            v["make"].lower(),
            v["model"].lower(),
            v["year"] if v["year"] is not None else 99_999,
        )
    )
    return out


def load_supplement() -> list[dict]:
    if not SUPPLEMENT_PATH.exists():
        return []
    with SUPPLEMENT_PATH.open(encoding="utf-8") as f:
        data = json.load(f)
    entries = data.get("vehicles", [])
    log_info(f"Loaded {len(entries)} vehicles from supplement")
    return entries


def merge(upstream: list[dict], supplement: list[dict]) -> list[dict]:
    """Supplement entries win on exact (make, model, variant, year) collision."""
    seen = {(v["make"], v["model"], v["variant"], v["year"]) for v in supplement}
    merged = [v for v in upstream if (v["make"], v["model"], v["variant"], v["year"]) not in seen]
    merged.extend(supplement)
    merged.sort(key=lambda v: (v["make"].lower(), v["model"].lower(), v["year"] or 99_999))
    return merged


def fetch_upstream(token: str | None = None) -> tuple[str, list[dict]]:
    """Return `(upstream_tag, raw_vehicles)` from the latest OpenEV release.

    `token` (when supplied) lifts the api.github.com rate limit from 60
    req/hr per runner IP to 5000 req/hr for the caller. CI passes
    `secrets.GITHUB_TOKEN`; cron and local runs can also pass a PAT.
    Public-repo reads work with any valid token regardless of scope, so
    the `secrets.GITHUB_TOKEN` minted for `SPS-L/joulie` is sufficient
    to authenticate against `open-ev-data/open-ev-data-dataset`.
    """
    log_info("Fetching upstream release metadata")
    headers = {"Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = requests.get(
        UPSTREAM_LATEST_URL,
        headers=headers,
        timeout=30,
    )
    r.raise_for_status()
    release = r.json()
    upstream_tag = release.get("tag_name") or release.get("name") or "unknown"
    assets = release.get("assets") or []
    json_asset = None
    for a in assets:
        name = (a.get("name") or "").lower()
        if name.endswith(".json"):
            json_asset = a
            break
    if json_asset is None:
        die(f"No .json asset found on upstream release {upstream_tag}")
    url = json_asset["browser_download_url"]
    log_info(f"Downloading {json_asset['name']} from {upstream_tag}")
    rr = requests.get(url, timeout=60)
    rr.raise_for_status()
    raw = rr.json()
    # Upstream may wrap rows under a top-level key.
    if isinstance(raw, dict):
        for k in ("data", "vehicles", "items"):
            if isinstance(raw.get(k), list):
                raw = raw[k]
                break
    if not isinstance(raw, list):
        die("Upstream JSON is not a list of vehicles")
    return upstream_tag, raw


def build_payload(upstream_tag: str, vehicles: list[dict]) -> dict:
    return {
        "version": _dt.date.today().isoformat(),
        "source": upstream_tag,
        "vehicle_count": len(vehicles),
        "vehicles": vehicles,
    }


def _identity_key(vehicle: dict) -> tuple[str, str, str, int]:
    """Identity tuple for the merge: (make, model, variant, year).

    Normalised to trim + lowercase on the string fields so that
    whitespace and casing changes upstream don't fork the entry. Year
    falls back to a sentinel -1 when absent, matching the Android side
    in `EvModelRepository.identityKey()`.
    """
    return (
        _as_str(vehicle.get("make")).lower(),
        _as_str(vehicle.get("model")).lower(),
        _as_str(vehicle.get("variant")).lower(),
        vehicle.get("year") if isinstance(vehicle.get("year"), int) else -1,
    )


def merge_incremental(
    existing: list[dict],
    fresh: list[dict],
) -> list[dict]:
    """Union `existing` and `fresh` by [_identity_key]. Fresh wins.

    The published `ev_models.json` grows monotonically across runs:
    entries unique to either side are kept; entries present in both
    are replaced by the upstream-derived row from this run (so battery
    / WLTP corrections propagate). A model retired upstream stays in
    the published asset so devices that already showed it in the
    autocomplete keep finding it after the monthly refresh.

    The output is sorted with the same key as [transform] so diffs
    across publish runs are minimal.
    """
    merged: dict[tuple[str, str, str, int], dict] = {}
    for v in existing:
        if isinstance(v, dict):
            merged[_identity_key(v)] = v
    for v in fresh:
        merged[_identity_key(v)] = v
    out = list(merged.values())
    out.sort(
        key=lambda v: (
            _as_str(v.get("make")).lower(),
            _as_str(v.get("model")).lower(),
            v["year"] if isinstance(v.get("year"), int) else 99_999,
        )
    )
    return out


def fetch_published_vehicles(release: dict | None) -> list[dict]:
    """Return the vehicle list from the previously-published asset.

    Used as the merge base so this script never drops rows the
    published asset already shipped. Failure modes (no release yet, no
    asset on the release, network error, malformed JSON) all degrade
    to an empty list — a clean first publish is the expected path on
    the very first run.
    """
    if release is None:
        return []
    asset_url: str | None = None
    for asset in release.get("assets") or []:
        if asset.get("name") == TARGET_ASSET_NAME:
            asset_url = asset.get("browser_download_url")
            break
    if not asset_url:
        return []
    try:
        r = requests.get(asset_url, timeout=60)
        r.raise_for_status()
        data = r.json()
    except (requests.RequestException, ValueError):
        log_info(
            "Could not download or parse the previously-published asset; "
            "proceeding with fresh upstream only."
        )
        return []
    if isinstance(data, dict):
        vehicles = data.get("vehicles")
        if isinstance(vehicles, list):
            return [v for v in vehicles if isinstance(v, dict)]
    return []


def _gh_headers(token: str) -> dict:
    return {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def _get_release(token: str) -> dict | None:
    url = f"https://api.github.com/repos/{TARGET_REPO}/releases/tags/{TARGET_TAG}"
    r = requests.get(url, headers=_gh_headers(token), timeout=30)
    if r.status_code == 404:
        return None
    r.raise_for_status()
    return r.json()


def _create_release(token: str, upstream_tag: str) -> dict:
    body = (
        f"Auto-updated from OpenEV Data {upstream_tag}. "
        f"Generated: {_dt.date.today().isoformat()}"
    )
    payload = {
        "tag_name": TARGET_TAG,
        "name": "EV Models Database",
        "body": body,
        "prerelease": False,
        "draft": False,
    }
    r = requests.post(
        f"https://api.github.com/repos/{TARGET_REPO}/releases",
        headers=_gh_headers(token),
        json=payload,
        timeout=30,
    )
    r.raise_for_status()
    return r.json()


def _delete_existing_asset(token: str, release: dict) -> None:
    for asset in release.get("assets") or []:
        if asset.get("name") == TARGET_ASSET_NAME:
            asset_id = asset["id"]
            log_info(f"Deleting existing asset {TARGET_ASSET_NAME} (id={asset_id})")
            r = requests.delete(
                f"https://api.github.com/repos/{TARGET_REPO}/releases/assets/{asset_id}",
                headers=_gh_headers(token),
                timeout=30,
            )
            r.raise_for_status()


def _upload_asset(token: str, release: dict, payload_bytes: bytes) -> None:
    upload_url = release["upload_url"].split("{", 1)[0]
    headers = _gh_headers(token) | {"Content-Type": "application/json"}
    r = requests.post(
        f"{upload_url}?name={TARGET_ASSET_NAME}",
        headers=headers,
        data=payload_bytes,
        timeout=120,
    )
    r.raise_for_status()


def main() -> int:
    # When EV_DB_LOCAL_OUTPUT is set, the script regenerates the merged
    # payload and writes it to that path *without* uploading to the
    # `ev-db-latest` GitHub release. Used by the release-APK workflow to
    # bundle a fresh upstream+supplement merge into assets/ev_models.json
    # at build time, so a tagged APK ships with the latest data even if
    # the monthly publish hasn't run since the supplement changed.
    local_output = os.environ.get("EV_DB_LOCAL_OUTPUT")
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        die("GITHUB_TOKEN env var not set")
    try:
        upstream_tag, raw = fetch_upstream(token)
        log_info(f"Fetched {len(raw)} vehicles from OpenEV Data {upstream_tag}")
        fresh = transform(raw)
        log_info(f"After filtering: {len(fresh)} fresh vehicles retained")
        supplement = load_supplement()
        if supplement:
            before = len(fresh)
            fresh = merge(fresh, supplement)
            added = len(fresh) - before
            log_info(
                f"After supplement merge: {len(fresh)} vehicles "
                f"({added} added, {len(supplement) - added} collisions resolved in favour of supplement)"
            )
        if len(fresh) < VALIDATION_FLOOR:
            die(
                f"Refusing to publish: only {len(fresh)} fresh vehicles passed "
                f"the filter, floor is {VALIDATION_FLOOR}. Aborting before "
                f"merging into the previously-published asset, so a broken "
                f"upstream cannot poison the existing data."
            )

        # Fetch the existing release (if any) first, so we can:
        #   (a) feed its assets into the incremental merge,
        #   (b) then re-use the same release object below to delete the
        #       outdated asset and upload the new one.
        release = _get_release(token)
        previously_published = fetch_published_vehicles(release)
        log_info(
            f"Previously-published asset: {len(previously_published)} vehicles"
        )
        vehicles = merge_incremental(previously_published, fresh)
        added = len(vehicles) - len(previously_published)
        log_info(
            f"After merge: {len(vehicles)} vehicles (added/restored {added} vs "
            f"the previously-published asset; upstream contributed {len(fresh)})"
        )

        payload = build_payload(upstream_tag, vehicles)
        payload_bytes = json.dumps(payload, ensure_ascii=False, indent=2).encode(
            "utf-8"
        )
        log_info(f"Output size: {len(payload_bytes) / 1024:.1f} KB")

        if local_output:
            out = pathlib.Path(local_output)
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(payload_bytes)
            log_info(
                f"Wrote merged payload to {out} ({len(vehicles)} vehicles) "
                f"and skipped {TARGET_REPO} release upload"
            )
            return 0

        if release is None:
            log_info(f"Creating release {TARGET_TAG} on {TARGET_REPO}")
            release = _create_release(token, upstream_tag)
        else:
            _delete_existing_asset(token, release)

        _upload_asset(token, release, payload_bytes)
        log_info(
            f"Uploaded {TARGET_ASSET_NAME} to {TARGET_REPO} release {TARGET_TAG}"
        )
        return 0
    except requests.HTTPError as e:
        die(f"HTTP error: {e}")
    except requests.RequestException as e:
        die(f"Network error: {e}")
    except (KeyError, ValueError, TypeError, json.JSONDecodeError) as e:
        die(f"Data error: {e}")


if __name__ == "__main__":
    sys.exit(main())
