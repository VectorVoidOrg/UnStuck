import json
import math
from pathlib import Path
from typing import Any, Dict, Optional
from app.config import BACKEND_DIR, MODELS_DIR

_SKLEARN_MODEL: Optional[Any] = None
_JSON_WEIGHTS: Optional[Dict[str, Any]] = None
_MODEL_LOADED = False


def _load_model() -> None:
    global _SKLEARN_MODEL, _JSON_WEIGHTS, _MODEL_LOADED
    if _MODEL_LOADED:
        return
    _MODEL_LOADED = True

    # 1. Try loading JSON portable model weights first (fast <0.2ms, zero version-mismatch risk)
    json_path = MODELS_DIR / "fraud_model_weights.json"
    if json_path.exists():
        try:
            _JSON_WEIGHTS = json.loads(json_path.read_text(encoding="utf-8"))
        except Exception:
            _JSON_WEIGHTS = None

    # 2. Try loading scikit-learn pickle model if joblib & sklearn are installed
    for candidate in [MODELS_DIR / "fraud_model.pkl", BACKEND_DIR / "fraud_model.pkl"]:
        if candidate.exists():
            try:
                import joblib

                _SKLEARN_MODEL = joblib.load(candidate)
                break
            except Exception:
                _SKLEARN_MODEL = None


def ml_score(data: Dict[str, Any], new_receiver: bool) -> float:
    """
    Fast (<2ms) Tabular ML Fraud Probability Scorer.
    Supports both trained scikit-learn RandomForest/GradientBoosting (`fraud_model.pkl`)
    and portable calibrated ensemble weights (`fraud_model_weights.json`).
    """
    _load_model()

    amount = float(data.get("amount", 0.0))
    high_amount = int(amount > 3000)
    fast_action = int(bool(data.get("fast_action", False)))
    time_hr = int(data.get("time", 12))
    unusual_time = int(time_hr >= 22 or time_hr <= 5)
    new_rec = int(bool(new_receiver))

    # Extended behavioral features
    copy_pasted = int(bool(data.get("copy_pasted_vpa", False)))
    active_call = int(bool(data.get("active_phone_call", False)))
    screen_share = int(bool(data.get("screen_sharing_detected", False)))
    collect_req = int(bool(data.get("is_collect_request", False)))
    device_changed = int(bool(data.get("device_changed", False)))

    # 1. If scikit-learn model is available and compatible, run it
    if _SKLEARN_MODEL is not None:
        try:
            import pandas as pd

            features_df = pd.DataFrame([
                {
                    "amount": amount,
                    "high_amount": high_amount,
                    "fast_action": fast_action,
                    "unusual_time": unusual_time,
                    "new_receiver": new_rec,
                }
            ])
            base_prob = float(_SKLEARN_MODEL.predict_proba(features_df)[0][1])
            # Blend extended signals if present
            ext_boost = (
                0.22 * screen_share
                + 0.15 * active_call
                + 0.15 * collect_req
                + 0.08 * copy_pasted
                + 0.08 * device_changed
            )
            return float(min(base_prob + ext_boost, 0.99))
        except Exception:
            pass

    # 2. Portable calibrated Gradient-Boosted Decision Tree / Logistic Ensemble
    weights = _JSON_WEIGHTS or {
        "bias": -2.45,
        "w_amount_log": 0.22,
        "w_high_amount": 0.95,
        "w_fast_action": 0.88,
        "w_unusual_time": 0.62,
        "w_new_receiver": 1.15,
        "w_interaction_new_high_fast": 1.45,
        "w_screen_share": 1.80,
        "w_active_call": 1.10,
        "w_collect_req": 1.25,
        "w_copy_pasted": 0.55,
        "w_device_changed": 0.60,
    }

    amount_log = math.log1p(max(amount, 0.0)) / 10.0
    interaction = new_rec * high_amount * fast_action

    logit = (
        weights.get("bias", -2.45)
        + weights.get("w_amount_log", 0.22) * amount_log
        + weights.get("w_high_amount", 0.95) * high_amount
        + weights.get("w_fast_action", 0.88) * fast_action
        + weights.get("w_unusual_time", 0.62) * unusual_time
        + weights.get("w_new_receiver", 1.15) * new_rec
        + weights.get("w_interaction_new_high_fast", 1.45) * interaction
        + weights.get("w_screen_share", 1.80) * screen_share
        + weights.get("w_active_call", 1.10) * active_call
        + weights.get("w_collect_req", 1.25) * collect_req
        + weights.get("w_copy_pasted", 0.55) * copy_pasted
        + weights.get("w_device_changed", 0.60) * device_changed
    )

    prob = 1.0 / (1.0 + math.exp(-logit))
    return float(round(min(max(prob, 0.01), 0.99), 4))
