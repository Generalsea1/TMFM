#!/usr/bin/env python3
from __future__ import annotations
import json, os, re, subprocess, sys, time, urllib.parse, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "docs" / "evidence" / "m2-runs"
REPORT_DIR.mkdir(parents=True, exist_ok=True)

SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "").strip()
if not SUPABASE_URL or not SERVICE_KEY:
    print("M2_BLOCKED_MISSING_SUPABASE_SECRETS", file=sys.stderr)
    sys.exit(2)

HEADERS = {
    "apikey": SERVICE_KEY,
    "Authorization": "Bearer " + SERVICE_KEY,
    "User-Agent": "TMFM-M2-Verifier/1.0",
}

def request_json(method, url, payload=None):
    data = None
    headers = dict(HEADERS)
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
        headers["Prefer"] = "return=minimal"
    req = urllib.request.Request(url, method=method, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        raw = response.read()
        return response.status, json.loads(raw.decode("utf-8")) if raw else None

def fetch_station_rows():
    params = urllib.parse.urlencode({
        "select": "id,name,name_ar,country_code,frequency_mhz,stream_url,stream_type,is_islamic,is_online,stream_consecutive_failures",
        "stream_url": "not.is.null",
        "is_islamic": "eq.false",
    })
    status, payload = request_json("GET", SUPABASE_URL + "/rest/v1/radio_stations?" + params)
    if status // 100 != 2:
        raise RuntimeError("Supabase HTTP " + str(status))
    return payload or []

def http_probe(url):
    if not url or not url.startswith("https://"):
        return False, "STREAM_URL_NOT_HTTPS", None, None, b""
    started = time.monotonic()
    request = urllib.request.Request(
        url,
        method="GET",
        headers={
            "User-Agent": "TMFM-M2-Verifier/1.0",
            "Icy-MetaData": "0",
            "Range": "bytes=0-65535",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=12) as response:
            sample = response.read(65536)
            elapsed = int((time.monotonic() - started) * 1000)
            content_type = (response.headers.get("Content-Type") or "").lower()
            return True, "HTTP_OK", content_type, elapsed, sample[:64]
    except Exception as exc:
        return False, "HTTP_FAILED: " + str(exc), None, int((time.monotonic() - started) * 1000), b""

def run_process(args):
    return subprocess.run(
        args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, timeout=25
    )

def decode_audio(url):
    probe = run_process([
        "ffprobe", "-v", "error",
        "-rw_timeout", "15000000",
        "-read_intervals", "%+5",
        "-show_entries", "stream=codec_name,bit_rate",
        "-of", "json", url,
    ])
    if probe.returncode != 0:
        return False, "FFPROBE_FAILED: " + probe.stderr[-800:], None, None, None

    try:
        streams = json.loads(probe.stdout).get("streams", [])
        audio = next(item for item in streams if item.get("codec_name"))
    except (ValueError, StopIteration) as exc:
        return False, "NO_AUDIO_STREAM: " + str(exc), None, None, None

    codec = str(audio.get("codec_name") or "unknown")
    try:
        bitrate = int(audio.get("bit_rate")) // 1000
    except (TypeError, ValueError):
        bitrate = None

    ffmpeg = run_process([
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-rw_timeout", "15000000", "-t", "8",
        "-i", url, "-af", "volumedetect", "-f", "null", "-"
    ])
    if ffmpeg.returncode != 0:
        return False, "FFMPEG_FAILED: " + ffmpeg.stderr[-800:], codec, bitrate, None

    match = re.search(r"mean_volume:\s*(-?\d+(?:\.\d+)?)\s*dB", ffmpeg.stderr)
    mean_volume = float(match.group(1)) if match else None
    if mean_volume is None:
        return False, "NO_VOLUME_MEASUREMENT", codec, bitrate, None
    if mean_volume <= -60.0:
        return False, "DECODED_AUDIO_IS_SILENT", codec, bitrate, mean_volume
    return True, "PLAYABLE", codec, bitrate, mean_volume

def verify_one(station):
    url = station.get("stream_url")
    ok, reason, content_type, connect_ms, sample = http_probe(url)
    if not ok:
        return {
            "id": station["id"], "name": station.get("name"),
            "playable": False, "reason": reason,
            "codec": None, "bitrate_kbps": None, "connect_ms": connect_ms,
            "content_type": content_type,
        }
    if b"<html" in sample.lower() or b"<!doctype" in sample.lower():
        return {
            "id": station["id"], "name": station.get("name"),
            "playable": False, "reason": "HTTP_RETURNED_HTML",
            "codec": None, "bitrate_kbps": None, "connect_ms": connect_ms,
            "content_type": content_type,
        }

    playable, reason, codec, bitrate, mean_volume = decode_audio(url)
    return {
        "id": station["id"], "name": station.get("name"),
        "playable": playable, "reason": reason, "codec": codec,
        "bitrate_kbps": bitrate, "connect_ms": connect_ms,
        "content_type": content_type, "mean_volume_db": mean_volume,
    }

def update_station(station, result):
    station_id = urllib.parse.quote(station["id"], safe="")
    url = SUPABASE_URL + "/rest/v1/radio_stations?id=eq." + station_id
    failures = int(station.get("stream_consecutive_failures") or 0)

    if result["playable"]:
        payload = {
            "stream_verified": True,
            "stream_verified_at": datetime.now(timezone.utc).isoformat(),
            "stream_codec": result["codec"],
            "stream_bitrate_kbps": result["bitrate_kbps"],
            "stream_connect_ms": result["connect_ms"],
            "stream_verification_reason": "PLAYABLE",
            "stream_consecutive_failures": 0,
            "is_online": True,
            "verification_status": "PLAYABLE",
            "is_verified": True,
        }
    else:
        failures += 1
        offline = failures >= 2
        payload = {
            "stream_verified": False,
            "stream_verified_at": None,
            "stream_codec": result.get("codec"),
            "stream_bitrate_kbps": result.get("bitrate_kbps"),
            "stream_connect_ms": result.get("connect_ms"),
            "stream_verification_reason": result["reason"],
            "stream_consecutive_failures": failures,
            "is_online": False if offline else station.get("is_online", True),
            "verification_status": "OFFLINE" if offline else "STREAM_FAILED",
            "is_verified": bool(station.get("is_verified")) and not offline,
        }
    request_json("PATCH", url, payload)

def main():
    rows = fetch_station_rows()
    results = []
    for station in rows:
        try:
            result = verify_one(station)
            update_station(station, result)
        except Exception as exc:
            result = {
                "id": station["id"], "name": station.get("name"),
                "playable": False, "reason": "VERIFIER_EXCEPTION: " + str(exc),
            }
        results.append(result)
        print(json.dumps(result, ensure_ascii=False))

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "station_count": len(results),
        "playable": sum(1 for item in results if item.get("playable")),
        "failed": sum(1 for item in results if not item.get("playable")),
        "results": results,
    }
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    (REPORT_DIR / (stamp + ".json")).write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (ROOT / "docs" / "evidence" / "m2-latest.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("M2_PLAYABLE=" + str(report["playable"]))
    print("M2_FAILED=" + str(report["failed"]))

if __name__ == "__main__":
    main()
