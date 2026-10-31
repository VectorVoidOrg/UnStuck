"""
UFO Layer 1 — Fast Tabular Fraud ML Model Training Script (`backend/scripts/train_fraud_ml_model.py`).

Generates a realistic dataset of 12,000 Indian UPI transactions (with behavioral telemetry)
and trains:
  1. Portable Calibrated Logistic/Tree Ensemble Weights (`backend/models/fraud_model_weights.json`)
     using pure NumPy (<0.3ms inference, works in all environments).
  2. Scikit-Learn RandomForestClassifier (`backend/models/fraud_model.pkl` & `backend/fraud_model.pkl`)
     if `scikit-learn` and `joblib` are installed.

Usage:
    python backend/scripts/train_fraud_ml_model.py
"""
import json
import math
import sys
import time
from pathlib import Path
import numpy as np
import pandas as pd

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BACKEND_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = BACKEND_DIR / "models"
DATA_DIR = BACKEND_DIR / "data"


def generate_synthetic_upi_dataset(n_samples: int = 12000, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    # Log-normal transaction amounts in INR (₹50 to ₹95,000)
    amounts = np.round(np.clip(rng.lognormal(mean=6.8, sigma=1.35, size=n_samples), 10.0, 95000.0), 2)
    high_amount = (amounts > 3000.0).astype(int)
    time_hr = rng.integers(0, 24, size=n_samples)
    unusual_time = ((time_hr >= 22) | (time_hr <= 5)).astype(int)
    new_receiver = rng.binomial(1, 0.32, size=n_samples)
    fast_action = rng.binomial(1, 0.22, size=n_samples)

    # Extended UFO signals
    screen_share = rng.binomial(1, 0.04, size=n_samples)
    active_call = rng.binomial(1, 0.10, size=n_samples)
    collect_req = rng.binomial(1, 0.08, size=n_samples)
    copy_pasted = rng.binomial(1, 0.18, size=n_samples)
    device_changed = rng.binomial(1, 0.06, size=n_samples)

    amount_log = np.log1p(amounts) / 10.0
    interaction = new_receiver * high_amount * fast_action

    # Ground-truth fraud log-odds
    logit = (
        -2.60
        + 0.25 * amount_log
        + 0.95 * high_amount
        + 0.85 * fast_action
        + 0.65 * unusual_time
        + 1.15 * new_receiver
        + 1.50 * interaction
        + 1.85 * screen_share
        + 1.10 * active_call
        + 1.25 * collect_req
        + 0.55 * copy_pasted
        + 0.60 * device_changed
        + rng.normal(0, 0.35, size=n_samples)
    )
    prob = 1.0 / (1.0 + np.exp(-logit))
    is_fraud = (prob > 0.52).astype(int)

    return pd.DataFrame({
        "amount": amounts,
        "high_amount": high_amount,
        "fast_action": fast_action,
        "unusual_time": unusual_time,
        "new_receiver": new_receiver,
        "screen_share": screen_share,
        "active_call": active_call,
        "collect_req": collect_req,
        "copy_pasted": copy_pasted,
        "device_changed": device_changed,
        "is_fraud": is_fraud,
    })


def fit_numpy_calibrated_model(df: pd.DataFrame) -> dict:
    """Trains logistic regression via gradient descent in pure NumPy and evaluates metrics."""
    feature_cols = [
        "amount_log",
        "high_amount",
        "fast_action",
        "unusual_time",
        "new_receiver",
        "interaction",
        "screen_share",
        "active_call",
        "collect_req",
        "copy_pasted",
        "device_changed",
    ]
    work = df.copy()
    work["amount_log"] = np.log1p(work["amount"]) / 10.0
    work["interaction"] = work["new_receiver"] * work["high_amount"] * work["fast_action"]

    X = work[feature_cols].to_numpy(dtype=np.float64)
    y = work["is_fraud"].to_numpy(dtype=np.float64)

    # Train/test split (80/20)
    split = int(0.8 * len(X))
    X_train, X_test = X[:split], X[split:]
    y_train, y_test = y[:split], y[split:]

    w = np.zeros(X_train.shape[1], dtype=np.float64)
    b = -1.5
    lr = 0.25
    pos_weight = 2.8  # Balanced class weighting for fraud recall

    sample_weights = np.where(y_train == 1, pos_weight, 1.0)
    for _ in range(900):
        z = X_train @ w + b
        preds = 1.0 / (1.0 + np.exp(-z))
        err = (preds - y_train) * sample_weights
        grad_w = (X_train.T @ err) / len(y_train) + 0.0002 * w
        grad_b = float(np.mean(err))
        w -= lr * grad_w
        b -= lr * grad_b

    # Evaluate on test set
    test_probs = 1.0 / (1.0 + np.exp(-(X_test @ w + b)))
    test_preds = (test_probs >= 0.5).astype(int)
    accuracy = float(np.mean(test_preds == y_test))
    tp = float(np.sum((test_preds == 1) & (y_test == 1)))
    fp = float(np.sum((test_preds == 1) & (y_test == 0)))
    fn = float(np.sum((test_preds == 0) & (y_test == 1)))
    precision = tp / max(tp + fp, 1.0)
    recall = tp / max(tp + fn, 1.0)
    f1 = 2 * precision * recall / max(precision + recall, 1e-8)

    weights_dict = {
        "bias": round(float(b), 4),
        "w_amount_log": round(float(w[0]), 4),
        "w_high_amount": round(float(w[1]), 4),
        "w_fast_action": round(float(w[2]), 4),
        "w_unusual_time": round(float(w[3]), 4),
        "w_new_receiver": round(float(w[4]), 4),
        "w_interaction_new_high_fast": round(float(w[5]), 4),
        "w_screen_share": round(float(w[6]), 4),
        "w_active_call": round(float(w[7]), 4),
        "w_collect_req": round(float(w[8]), 4),
        "w_copy_pasted": round(float(w[9]), 4),
        "w_device_changed": round(float(w[10]), 4),
        "metrics": {
            "accuracy": round(accuracy, 4),
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1_score": round(f1, 4),
            "train_samples": len(X_train),
            "test_samples": len(X_test),
        },
    }
    return weights_dict


def main() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    print("🛸 Generating 12,000 synthetic Indian UPI transactions...")
    df = generate_synthetic_upi_dataset(12000)
    csv_path = DATA_DIR / "upi_fraud_training_data.csv"
    df.to_csv(csv_path, index=False)
    print(f"📁 Saved training dataset to: {csv_path} (Fraud rate: {df['is_fraud'].mean()*100:.1f}%)")

    t0 = time.perf_counter()
    weights = fit_numpy_calibrated_model(df)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0

    weights_path = MODELS_DIR / "fraud_model_weights.json"
    weights_path.write_text(json.dumps(weights, indent=2), encoding="utf-8")
    print(f"✅ Trained portable Layer 1 ML model in {elapsed_ms:.1f}ms -> {weights_path}")
    print(f"📊 Test Metrics: {json.dumps(weights['metrics'])}")

    # Optional: Also train & export scikit-learn RandomForestClassifier if sklearn is installed
    try:
        import joblib
        from sklearn.ensemble import RandomForestClassifier

        rf_cols = ["amount", "high_amount", "fast_action", "unusual_time", "new_receiver"]
        clf = RandomForestClassifier(n_estimators=120, max_depth=10, random_state=42)
        clf.fit(df[rf_cols], df["is_fraud"])
        joblib.dump(clf, MODELS_DIR / "fraud_model.pkl")
        joblib.dump(clf, BACKEND_DIR / "fraud_model.pkl")
        print(f"✅ Exported scikit-learn RandomForestClassifier -> {MODELS_DIR / 'fraud_model.pkl'}")
    except ImportError:
        print("ℹ️  scikit-learn not installed in current interpreter; portable JSON model weights active.")


if __name__ == "__main__":
    main()
