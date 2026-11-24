"""13.2 device check: FraudShield's app inventory vs Android's own `dumpsys package` for N apps.
Usage: python verify_inventory.py <last-audit.json> <adb serial> pkg1 pkg2 ...
Compares: requested permissions, granted permissions, targetSdk, versionName, installer, first install time."""
import json
import re
import subprocess
import sys
from datetime import datetime

audit = {a["packageName"]: a for a in json.load(open(sys.argv[1], encoding="utf-8"))}
serial, pkgs = sys.argv[2], sys.argv[3:]
ADB = "E:/Android/Sdk/platform-tools/adb"
ok_all = True
rows = []
for pkg in pkgs:
    out = subprocess.run([ADB, "-s", serial, "shell", "dumpsys", "package", pkg], capture_output=True, text=True).stdout
    a = audit.get(pkg)
    if a is None:
        rows.append((pkg, "MISSING from inventory")); ok_all = False; continue
    # the package's own section only (dumpsys also lists shared-user / other packages)
    sec = out[out.find(f"Package [{pkg}]"):]
    req_block = re.search(r"requested permissions:\n((?:\s+\S.*\n)+?)\s*(?:install permissions|runtime permissions|User 0|\n\S)", sec)
    requested = set(re.findall(r"^\s+([\w.]+)", req_block.group(1), re.M)) if req_block else set()
    granted = set(re.findall(r"^\s+([\w.]+): granted=true", sec, re.M))
    target = re.search(r"targetSdk=(\d+)", sec)
    version = re.search(r"versionName=(\S+)", sec)
    installer = re.search(r"installerPackageName=(\S+)", sec)
    first = re.search(r"firstInstallTime=([\d\- :]+)", sec)
    checks = {
        "requested": set(a["requestedPermissions"]) == requested,
        "granted": set(a["grantedPermissions"]) == granted,
        "targetSdk": target is not None and int(target.group(1)) == a["targetSdk"],
        "version": version is not None and version.group(1) == (a.get("versionName") or ""),
        "installer": (installer.group(1) if installer else None) in (a.get("installer"), None if a.get("installer") is None else "x"),
        "firstInstall": first is not None and abs(datetime.strptime(first.group(1).strip(), "%Y-%m-%d %H:%M:%S").timestamp() * 1000 - a["firstInstallTime"]) < 1000 * 86400,
    }
    bad = [k for k, v in checks.items() if not v]
    if bad:
        ok_all = False
        detail = []
        if "requested" in bad: detail.append(f"req only-app={sorted(set(a['requestedPermissions'])-requested)[:3]} only-pm={sorted(requested-set(a['requestedPermissions']))[:3]}")
        if "granted" in bad: detail.append(f"granted only-app={sorted(set(a['grantedPermissions'])-granted)[:3]} only-pm={sorted(granted-set(a['grantedPermissions']))[:3]}")
        rows.append((pkg, "DIFF " + ", ".join(bad) + " " + "; ".join(detail)))
    else:
        rows.append((pkg, f"match ({len(requested)} requested, {len(granted)} granted, target {a['targetSdk']}, installer {a.get('installer')})"))
for p, r in rows:
    print(f"{p}: {r}")
print("ALL MATCH" if ok_all else "DIFFERENCES FOUND")
