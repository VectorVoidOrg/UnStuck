"""
UFO Fraudster UPI VPA Dataset Generator & Importer (`backend/scripts/seed_fraudster_vpa_dataset.py`).

Generates a realistic dataset of 250+ reported fraudulent UPI IDs (`backend/data/public_fraud_vpa_dataset.csv`)
modeled after official Indian Cybercrime (MHA I4C `cybercrime.gov.in` Suspect Repository & Police Advisories)
scam categories:
  1. Digital Arrest / Law Enforcement Impersonation (CBI, ED, Customs, Narcotics, Police)
  2. Bank KYC / PAN / Account Block Impersonation (SBI, HDFC, ICICI, Axis fake support)
  3. Electricity / Utility Disconnection Scams (BESCOM, MSEB, TataPower, BSES fake officers)
  4. OLX / Marketplace QR Code & Collect Request Traps (Fake Army/CISF buyer handles)
  5. Remote Screen-Share Customer Care Scams (AnyDesk/TeamViewer helpline VPAs)
  6. Part-Time Job / Telegram Task / YouTube Like Money Mule Accounts
  7. Fake Government Scheme / PM-Kisan / Income Tax Refund Handles
  8. Instant Loan App Extortion Handles

Also supports importing any external CSV file of fraudulent UPI IDs into `hall_of_shame`:
    python backend/scripts/seed_fraudster_vpa_dataset.py --csv path/to/your_file.csv
"""
import argparse
import csv
import sys
from pathlib import Path
from typing import Any, Dict, List

BACKEND_DIR = Path(__file__).resolve().parent.parent
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from app.db.mongo import DATA_DIR, hall_collection, receiver_collection

HANDLES = ["okaxis", "ybl", "paytm", "okicici", "oksbi", "axl", "ibl", "apl", "upi", "barodampay"]

SCAM_VPA_PREFIX_TEMPLATES = [
    # 1. Digital Arrest / Police / Customs Impersonation
    ("cbi.digitalarrest", "DIGITAL_ARREST_EXTORTION", 0.99, 28, 32),
    ("ed.investigation.cell", "DIGITAL_ARREST_EXTORTION", 0.98, 19, 21),
    ("mumbai.cyber.police", "DIGITAL_ARREST_EXTORTION", 0.97, 15, 17),
    ("customs.parcel.clearance", "DIGITAL_ARREST_EXTORTION", 0.96, 22, 25),
    ("ncb.narcotics.verify", "DIGITAL_ARREST_EXTORTION", 0.99, 31, 35),
    ("supremecourt.escrow", "DIGITAL_ARREST_EXTORTION", 0.98, 18, 20),
    # 2. Fake Bank KYC / PAN Suspension
    ("scammer.kyc", "FAKE_KYC_SUSPENSION", 0.96, 14, 18),
    ("sbi.kyc.update24", "FAKE_KYC_SUSPENSION", 0.95, 12, 15),
    ("hdfc.pan.link", "FAKE_KYC_SUSPENSION", 0.94, 11, 14),
    ("icici.account.unblock", "FAKE_KYC_SUSPENSION", 0.95, 16, 19),
    ("rbi.kyc.verification", "FAKE_KYC_SUSPENSION", 0.98, 24, 27),
    # 3. Electricity / Gas / Utility Bill Disconnection
    ("ebill.disconnect", "ELECTRICITY_BILL_SCAM", 0.93, 12, 15),
    ("bescom.power.officer", "ELECTRICITY_BILL_SCAM", 0.94, 10, 13),
    ("mseb.bill.desk99", "ELECTRICITY_BILL_SCAM", 0.92, 9, 12),
    ("tatapower.urgent.pay", "ELECTRICITY_BILL_SCAM", 0.91, 8, 11),
    ("bses.tonight.cutoff", "ELECTRICITY_BILL_SCAM", 0.95, 14, 16),
    # 4. OLX / Marketplace Collect Request & Fake Army Buyer
    ("lottery.cashback", "COLLECT_REQUEST_TRAP", 0.89, 9, 11),
    ("indianarmy.canteen.pay", "COLLECT_REQUEST_TRAP", 0.96, 21, 24),
    ("cisf.officer.transfer", "COLLECT_REQUEST_TRAP", 0.95, 17, 20),
    ("olx.verified.advance", "COLLECT_REQUEST_TRAP", 0.93, 13, 16),
    ("flipkart.prize.claim", "COLLECT_REQUEST_TRAP", 0.91, 11, 14),
    # 5. Remote Screen-Sharing / Fake Customer Care Helpline
    ("anydesk.refund.help", "SCREEN_SHARE_HELPLINE", 0.97, 19, 22),
    ("customercare.instant.refund", "SCREEN_SHARE_HELPLINE", 0.94, 15, 18),
    ("swiggy.refund.desk", "SCREEN_SHARE_HELPLINE", 0.92, 10, 12),
    ("airtel.sim.block.care", "SCREEN_SHARE_HELPLINE", 0.95, 14, 17),
    # 6. Telegram Task / Part-Time Job / Investment Mules
    ("telegram.vip.task01", "TELEGRAM_TASK_MULE", 0.93, 25, 30),
    ("youtube.like.payout", "TELEGRAM_TASK_MULE", 0.90, 18, 23),
    ("crypto.usdt.merchant99", "TELEGRAM_TASK_MULE", 0.94, 29, 34),
    # 7. Government Scheme / Tax Refund Impersonation
    ("pmkisan.yojana.claim", "GOVT_SCHEME_IMPERSONATION", 0.96, 16, 19),
    ("incometax.tds.refund", "GOVT_SCHEME_IMPERSONATION", 0.95, 13, 15),
]


def generate_fraudster_vpa_records() -> List[Dict[str, Any]]:
    records: List[Dict[str, Any]] = []
    for prefix, scam_type, base_risk, flags, txns in SCAM_VPA_PREFIX_TEMPLATES:
        for idx, handle in enumerate(HANDLES):
            vpa = f"{prefix}@{handle}"
            records.append({
                "receiver": vpa,
                "avg_risk": round(min(0.99, base_risk - (idx % 3) * 0.01), 2),
                "flag_count": flags + (idx % 5),
                "transactions": txns + (idx % 7),
                "scam_type": scam_type,
                "source": "I4C_SUSPECT_PATTERN_DATASET",
                "status": "BLACKLISTED",
            })
    return records


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed Fraudster UPI ID Dataset into UFO Database")
    parser.add_argument("--csv", type=str, default="", help="Optional custom CSV file to import")
    args = parser.parse_args()

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    default_csv_path = DATA_DIR / "public_fraud_vpa_dataset.csv"

    if args.csv and Path(args.csv).exists():
        csv_to_load = Path(args.csv)
        with csv_to_load.open("r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            records = [
                {
                    "receiver": row["receiver"].strip().lower(),
                    "avg_risk": float(row.get("avg_risk", 0.95)),
                    "flag_count": int(row.get("flag_count", 10)),
                    "transactions": int(row.get("transactions", 12)),
                    "scam_type": row.get("scam_type", "EXTERNAL_BLACKLIST"),
                    "source": row.get("source", "CUSTOM_CSV_IMPORT"),
                    "status": "BLACKLISTED",
                }
                for row in reader
                if row.get("receiver")
            ]
    else:
        records = generate_fraudster_vpa_records()
        with default_csv_path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["receiver", "avg_risk", "flag_count", "transactions", "scam_type", "source", "status"],
            )
            writer.writeheader()
            writer.writerows(records)
        print(f"📁 Generated {len(records)} Fraudster UPI VPA records -> {default_csv_path}")

    # Bulk upsert into hall_of_shame and receivers collections
    for rec in records:
        hall_collection.update_one(
            {"receiver": rec["receiver"]},
            {"$set": rec},
            upsert=True,
        )
        receiver_collection.update_one(
            {"receiver": rec["receiver"]},
            {
                "$set": {
                    "receiver": rec["receiver"],
                    "transactions": rec["transactions"],
                    "total_risk": round(rec["avg_risk"] * rec["transactions"], 2),
                    "flag_count": rec["flag_count"],
                }
            },
            upsert=True,
        )

    total = hall_collection.count_documents({})
    print(f"✅ Seeded UFO `hall_of_shame` blacklist! Total blacklisted fraudster UPI IDs: {total}")


if __name__ == "__main__":
    main()
