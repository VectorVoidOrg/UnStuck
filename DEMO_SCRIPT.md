# 🎭 UFO Hackathon Presentation Script (3-Minute Timed Pitch)

**Total Duration:** 3 minutes (180 seconds)  
**Presenters:** Android Engineer & Backend Engineer  
**Demo Hardware:** Android Phone running `dist/UFO-demo.apk` connected to projector / screen mirroring.

---

## ⏱️ Timeline & Stage Cues

### [00:00 - 00:30] The Hook: The ₹20,000 Crore Blindspot
- **Presenter:**
  > *"Every day, 500 million UPI transactions happen across India. But there are two nightmare moments every citizen dreads:*
  > 
  > *1. **Before payment:** Falling for a Digital Arrest or impersonation scam where life savings disappear in 10 seconds.*
  > *2. **After payment:** A critical payment fails, money is debited, and your bank leaves you helpless for weeks.*
  > 
  > *Introducing **UFO: Unified Fraud Observer & Unstuck Resolution**. The first on-device AI system that intercepts scams BEFORE you enter your PIN, and automates RBI compensation when payments fail."*

---

### [00:30 - 01:15] Act 1: Prevention (Live Payment Screen Interception)
- **Visual:** Switch phone to **Prevention** tab.
- **Presenter:**
  > *"Let's see live Prevention in action. UFO runs silently in the background, watching only UPI payment apps like Google Pay, PhonePe, and Paytm. It never reads passwords, PINs, or sensitive messages."*
- **Action:** Open Demo Tools -> **Launch Simulated Payment App Screen**.
  - Show simulated Google Pay screen paying `cbi.digitalarrest@ybl` (₹50,000).
- **Presenter:**
  > *"Notice what happens the moment a scam recipient appears. In less than 400 milliseconds, UFO's Gateway analyzes the receiver and triggers a Truecaller-sized Heads-Up alert right over the payment page!*
  > 
  > *It flags a 95% scam probability: impersonation and collect trap detected. The user is warned BEFORE they ever touch the UPI PIN keypad."*
- **Action:** Tap **Safe Merchant** (`swiggy@icici`) -> Point out the green badge indicating verified safe merchant.

---

### [01:15 - 02:00] Act 2: Unstuck (Gemini Extraction & Automated Ombudsman Complaint)
- **Visual:** Switch phone to **Unstuck** tab.
- **Presenter:**
  > *"Now, what if a legitimate payment fails, your money gets stuck, and the bank ignores your tickets? RBI circulars mandate a T+1 resolution deadline and ₹100 per day automatic penalty for delays. But almost nobody knows how to claim it.*
  > 
  > *With Unstuck, the user simply uploads their failed transaction screenshot."*
- **Action:** Select failed transaction screenshot -> Tap extract.
- **Presenter:**
  > *"The image is processed—metadata and EXIF data stripped on-device for privacy, downscaled, and sent to Gemini via our Gateway. Gemini extracts the 12-digit UTR, date, amount, and failure code with mathematical validation.*
  > 
  > *In one tap, UFO generates a formal, legally structured complaint under the Reserve Bank - Integrated Ombudsman Scheme 2021, calculates exact penalty interest accrued to date, and prepares it ready to email or share to the RBI portal."*

---

### [02:00 - 02:30] Act 3: Architecture & Security Governance
- **Visual:** Switch phone to **Account** tab.
- **Presenter:**
  > *"Behind the scenes, UFO is built with banking-grade security:*
  > 
  > *- **Strict Server-Side Quota Enforcement:** Quota checks are transactional and conditional—zero client-side bypass possible.*
  > *- **Hardware Keystore AEAD Encryption:** All session tokens are encrypted using Android KeyStore AES-256 GCM authenticated encryption via DataStore.*
  > *- **Zero Data Leakage:** On-device PhotoPicker strips EXIF tags and sanitizes images before upload.*
  > *- **Micro-Footprint:** The entire release APK is under 2.8 MB."*

---

### [02:30 - 03:00] Conclusion & Q&A
- **Presenter:**
  > *"UFO bridges the gap between fear and recovery in digital payments—protecting users before the transaction, and fighting for them when systems fail.*
  > 
  > *Thank you. We are ready for your questions!"*

