#!/usr/bin/env python3
"""
UFO Hackathon - Install Demo APK on Phone / Emulator
Installs dist/UFO-demo.apk via ADB and prints setup instructions.
"""

import os
import sys
import subprocess
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = Path(__file__).resolve().parent.parent
DIST_APK = ROOT_DIR / "dist" / "UFO-demo.apk"

def install():
    print("=" * 60)
    print("🛸 UFO Hackathon APK Installer")
    print("=" * 60)

    if not DIST_APK.exists():
        print(f"[!] {DIST_APK} not found. Building it first...")
        build_script = ROOT_DIR / "scripts" / "build_demo_apk.py"
        subprocess.run([sys.executable, str(build_script)], check=True)

    print("[+] Checking ADB devices...")
    try:
        devices_out = subprocess.check_output(["adb", "devices"], text=True)
        lines = [l.strip() for l in devices_out.strip().splitlines()[1:] if l.strip() and not l.startswith("*")]
        if not lines:
            print("[!] No ADB devices or emulators found.")
            print("    Please connect your phone via USB with USB Debugging enabled,")
            print("    or start an Android Virtual Device (AVD).")
            sys.exit(1)

        print(f"[✓] Detected {len(lines)} device(s):")
        for line in lines:
            print(f"    - {line}")

        print(f"\n[+] Installing {DIST_APK.name}...")
        install_res = subprocess.run(["adb", "install", "-r", "-d", str(DIST_APK)])
        if install_res.returncode == 0:
            print("\n[✓] APK Installed successfully!")
            print("\n📱 Next Steps on Your Phone / Emulator:")
            print("1. Open 'UFO Demo' from the app drawer.")
            print("2. On first launch, tap 'Enable Accessibility' -> UFO Screen Observer.")
            print("   (On Android 13+, if greyed out: App Info -> 3-dots -> Allow Restricted Settings).")
            print("3. Grant Notification permissions for high-priority Truecaller scam alerts.")
            print("4. Switch to Account tab -> tap 'Continue as Jury Demo'.")
            print("5. Launch simulated UPI screen from Demo Tools to see live detection!\n")
        else:
            print("[✗] ADB install failed.")
            sys.exit(1)

    except FileNotFoundError:
        print("[✗] 'adb' command not found. Ensure Android SDK platform-tools is in your PATH.")
        sys.exit(1)

if __name__ == "__main__":
    install()
