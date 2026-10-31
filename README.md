# 🛸 UFO – Unified Fraud Observer

> AI-powered real-time fraud prevention system for UPI transactions  
> **“Stopping scams before they happen.”**

---

## 🚨 Problem

UPI fraud is rapidly increasing due to:
- Social engineering (calls, fake requests)
- User confusion (Pay vs Receive)
- Malicious apps and deceptive flows

⚠️ Existing systems detect fraud **after money is lost**

---

## 💡 Solution

**UFO (Unified Fraud Observer)** is a real-time AI layer that:

- Analyzes transactions **before execution**
- Detects suspicious behavior using contextual signals
- Warns users instantly with explainable insights
- Prevents fraud at the decision moment

---

## ⚙️ How It Works

1. User initiates a transaction  
2. Data sent to backend API  
3. Feature extraction (behavior + context)  
4. ML model predicts fraud risk  
5. Risk score + explanation generated  
6. User receives warning BEFORE payment  

---

## 🧠 Core Intelligence

We use **behavior-aware fraud detection**, not just rules.

### Signals Used:
- New receiver detection  
- Copy-paste UPI ID  
- Transaction speed (contextual)  
- Amount anomaly  
- User hesitation patterns  
- Device/IP changes  

⚠️ Key Logic:
> Speed alone ≠ fraud  
> Speed + low trust context = fraud signal  

---

## 🧮 AI Model

- Model: Random Forest  
- Library: Scikit-learn  
- Output:
  - Risk Score (0–1)
  - Classification (Safe / Warning / Fraud)
  - Explainable Reasoning  

---

## 🛠️ Tech Stack

### Frontend
- Kotlin (Android)
- Jetpack Compose

### Backend
- FastAPI (Python)

### AI/ML
- Scikit-learn (Random Forest)

### Database
- MongoDB

### Deployment
- AWS (EC2)

---

## 🔌 API Endpoints

### `/predict`
Returns fraud risk

```json
{
  "risk": 0.87,
  "status": "FRAUD",
  "reason": ["New receiver", "High amount", "Fast action"]
}
```
### /transaction

- Stores transaction data

### /behavior

- Tracks user interaction patterns