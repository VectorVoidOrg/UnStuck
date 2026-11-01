#!/usr/bin/env python3
"""
UFO Hackathon - Build Demo APK Script
Assembles and packages the standalone Demo flavor APK into dist/UFO-demo.apk.
"""

import os
import sys
import shutil
import subprocess
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent
FRONTEND_DIR = ROOT_DIR / "frontend"
DIST_DIR = ROOT_DIR / "dist"
TARGET_APK = DIST_DIR / "UFO-demo.apk"

def build_apk():
    print("=" * 60)
    print("🛸 UFO Hackathon Demo APK Builder")
    print("=" * 60)

    DIST_DIR.mkdir(parents=True, exist_ok=True)

    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    print("[+] Running assembleDemoRelease...")

    result = subprocess.run(
        [gradle_cmd, "assembleDemoRelease"],
        cwd=str(FRONTEND_DIR),
        shell=(os.name == "nt")
    )

    if result.returncode != 0:
        print("[!] assembleDemoRelease failed, falling back to assembleDemoDebug...")
        dbg_result = subprocess.run(
            [gradle_cmd, "assembleDemoDebug"],
            cwd=str(FRONTEND_DIR),
            shell=(os.name == "nt")
        )
        if dbg_result.returncode != 0:
            print("[✗] Gradle build failed.")
            sys.exit(1)
        source_apk = FRONTEND_DIR / "app" / "build" / "outputs" / "apk" / "demo" / "debug" / "app-demo-debug.apk"
    else:
        source_apk = FRONTEND_DIR / "app" / "build" / "outputs" / "apk" / "demo" / "release" / "app-demo-release.apk"

    if not source_apk.exists():
        print(f"[✗] Built APK not found at {source_apk}")
        sys.exit(1)

    shutil.copy2(source_apk, TARGET_APK)
    size_mb = TARGET_APK.stat().st_size / (1024 * 1024)
    print(f"\n[✓] Successfully built: {TARGET_APK}")
    print(f"    Size: {size_mb:.2f} MB")
    print(f"    Target Sdk: 35 (Android 15), Min Sdk: 26")

if __name__ == "__main__":
    build_apk()
