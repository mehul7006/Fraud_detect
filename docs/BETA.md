# FraudShield closed beta (roadmap 9.7)

**Goal:** 20 real users for **2 weeks**, **crash-free users ≥ 99 %**, every reported problem triaged.
**Needs from the owner:** the production API live (deploy/README.md), a Google Play developer account, the signed
release AAB (docs/RELEASE.md), the privacy-policy URL (9.4) and the Play declarations (9.3).

## 1. Set up the test track (Play Console)

1. Play Console → the app → **Testing → Closed testing** → create track "beta" (or **Internal testing** for the first
   1–2 days with up to 100 testers and no review delay).
2. Testers: create an e-mail list (Google accounts of the 20 testers). Mix on purpose:
   - phones: Xiaomi/Redmi/POCO, Samsung, Realme/Oppo/Vivo, one Pixel or Motorola; Android 10 → 15;
   - people: at least 5 who read Hindi first, 5 aged 50+, 5 who use UPI daily.
3. Upload the AAB built with `--dart-define=API_BASE=https://<domain>/api/v1 --dart-define=FS_FLAVOUR=play`.
4. Share the opt-in link with the tester instructions below. Testers must opt in, then install from Play.

## 2. What is collected

| Source | What | Where |
|---|---|---|
| **Firebase Crashlytics** (in the app: `main.dart`, collection on in **release builds only**) | crashes + fatal Flutter errors, device model, Android version | Firebase console → Crashlytics; **crash-free users** is the 9.7 target |
| Play Console → Android vitals | ANRs, crashes outside Flutter, battery warnings | Play Console |
| In-app "This verdict is wrong" (4.7 / 10.11) | wrong link / app verdicts | `GET /api/v1/admin/feedback` and `/admin/feedback/labels.tsv` |
| Blocking "This app broke" (18.4) | stays on the phone – ask testers to tell you the app name | tester message |
| Tester messages | everything else | one WhatsApp/Telegram group or a Google Form |

Nothing else is collected: messages, call numbers and browsing stay on the phone (see the privacy policy).
Upload `build/app/outputs/mapping/release/mapping.txt` and the Dart symbols (`build/symbols`) for every version so
Crashlytics shows readable stack traces.

## 3. Triage (daily, 15 minutes)

Every report becomes one GitHub issue with **one type** and **one priority** label:

| Type label | Meaning |
|---|---|
| `crash` | app closed / froze (Crashlytics or tester) |
| `false-alarm` | safe link / app / message / call marked as dangerous |
| `missed-scam` | a real scam not caught |
| `broke-app` | another app (bank, UPI, OTT…) stopped working with FraudShield on |
| `battery` | noticeable battery drain |
| `ux` / `hindi` | confusing screen / wrong or unclear Hindi |

| Priority | Rule | Fix by |
|---|---|---|
| `P0` | crash on start, payment/bank app broken, data/privacy issue | same day, new build |
| `P1` | crash in one feature, false alarm on a popular site/app, missed common scam | next build (≤ 3 days) |
| `P2` | everything else | after the beta |

- `false-alarm` on a site/app → add to the allow-lists (popular-domains / official apps) and a golden-verdict test.
- `broke-app` → find the blocked domain in the tester's blocking stats; add it to the never-block set (18.7).
- `missed-scam` → add the sample to the message corpus / URL corpus; retrain only after the beta.
- Weekly: crash-free users %, open P0/P1, top 5 false alarms → short note to the owner.

**Exit criteria (end of week 2):** crash-free users ≥ 99 %, no open P0, every P1 fixed or explained, at least 15 of
20 testers still have the app installed.

## 4. Tester instructions

**English**

> Thank you for testing FraudShield! For two weeks, please use your phone as usual with FraudShield on.
> 1. Install from the Play link, open the app, create an account and allow the permissions it asks for
>    (each one says why).
> 2. Turn on **Fake-website protection** (VPN) and **Call guard**. In Xiaomi/Realme/Vivo phones also allow
>    **Autostart** and set battery to **No restrictions** for FraudShield.
> 3. If a safe website, app, message or call is marked dangerous, tap **"This verdict is wrong"** or send us a
>    screenshot. If any app (bank, UPI, shopping, video) stops working, tell us its name.
> 4. Never share real OTPs or passwords with us. We never ask for them.
> Questions or problems: write in the tester group.

**हिन्दी**

> FraudShield की जाँच में मदद के लिए धन्यवाद! दो हफ़्ते तक FraudShield चालू रखकर फ़ोन सामान्य रूप से इस्तेमाल करें।
> 1. Play लिंक से इंस्टॉल करें, ऐप खोलें, खाता बनाएँ और जो अनुमतियाँ माँगी जाएँ वे दें (हर अनुमति का कारण लिखा है)।
> 2. **नकली-वेबसाइट सुरक्षा** (VPN) और **कॉल गार्ड** चालू करें। Xiaomi/Realme/Vivo फ़ोन में FraudShield के लिए
>    **Autostart** भी चालू करें और बैटरी को **No restrictions** पर रखें।
> 3. अगर कोई सुरक्षित वेबसाइट, ऐप, संदेश या कॉल ख़तरनाक बताई जाए, तो **"यह फ़ैसला गलत है"** दबाएँ या हमें
>    स्क्रीनशॉट भेजें। अगर कोई ऐप (बैंक, UPI, शॉपिंग, वीडियो) काम करना बंद कर दे, तो उसका नाम बताएँ।
> 4. अपना असली OTP या पासवर्ड हमें कभी न भेजें। हम कभी नहीं माँगते।
> सवाल या समस्या: टेस्टर ग्रुप में लिखें।
