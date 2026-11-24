"""14.1 labelled message corpus (English, Hindi, Hinglish) - generated from hand-written templates.

    python ml/messages/build_corpus.py      -> ml/messages/corpus.csv  (text,label,category,lang,template,split)

Scam categories follow Indian police / CERT-In / bank advisories (2024-2026): bank KYC / account block, lottery /
prize, courier / India Post, electricity disconnection, fake job / task, instant loan, "digital arrest", OTP request,
UPI refund / collect, investment / trading. Genuine: bank transaction alerts, OTPs, delivery updates, bill reminders,
real promotions, personal messages.

Privacy review: no real person's data. Phone numbers are masked (98XXXXX410) as in anonymised SMS datasets, names are
common first names drawn at random, amounts / dates / reference numbers are random, links are look-alike patterns.
Split: by TEMPLATE (20 % of each category's templates are test-only), so test messages never share a template
with training messages - the test score is not inflated by memorised wording.
"""
import csv
import os
import random

rnd = random.Random(20261124)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "corpus.csv")

NAMES = ["Rahul", "Priya", "Amit", "Sneha", "Vikram", "Anjali", "Rohit", "Pooja", "Suresh", "Kavita", "Arjun", "Neha",
         "Ravi", "Meena", "Sanjay", "Deepa", "Karan", "Asha", "Manoj", "Sunita"]
BANKS = ["SBI", "HDFC Bank", "ICICI Bank", "Axis Bank", "PNB", "Bank of Baroda", "Canara Bank", "Kotak Bank", "Union Bank"]
BANK_SHORT = {"SBI": "sbi", "HDFC Bank": "hdfc", "ICICI Bank": "icici", "Axis Bank": "axis", "PNB": "pnb",
              "Bank of Baroda": "bob", "Canara Bank": "canara", "Kotak Bank": "kotak", "Union Bank": "unionbank"}
SHOPS = ["Flipkart", "Amazon", "Myntra", "Meesho", "Ajio"]
COURIERS = ["India Post", "Blue Dart", "Delhivery", "DTDC", "FedEx"]
DISCOMS = ["BSES", "MSEDCL", "TNEB", "BESCOM", "UPPCL", "Tata Power"]
TLDS = ["top", "xyz", "online", "site", "in", "co", "info", "click"]


def num():
    return rnd.choice(["98", "97", "96", "88", "70", "63"]) + "XXXXX" + f"{rnd.randint(100, 999)}"


def amt(lo=500, hi=99999):
    return f"{rnd.randint(lo, hi):,}"


def link(*words):
    w = "-".join(x.lower().replace(" ", "") for x in words if x)
    return rnd.choice(["http://", "https://", ""]) + f"{w}.{rnd.choice(TLDS)}/" + rnd.choice(["", "verify", "kyc", "claim", "pay", "update", "track"])


def acct():
    return "XX" + str(rnd.randint(1000, 9999))


def otp():
    return str(rnd.randint(100000, 999999))


def date():
    return f"{rnd.randint(1, 28):02d}-{rnd.choice(['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'])}-26"


def F():
    """Random slot values for one message."""
    b = rnd.choice(BANKS)
    return dict(name=rnd.choice(NAMES), bank=b, bk=BANK_SHORT[b], shop=rnd.choice(SHOPS), courier=rnd.choice(COURIERS),
                discom=rnd.choice(DISCOMS), num=num(), amt=amt(), big=amt(100000, 2500000), acct=acct(), otp=otp(),
                date=date(), ref=str(rnd.randint(10**9, 10**10 - 1)), hrs=rnd.choice(["2", "6", "12", "24"]),
                time=f"{rnd.randint(7, 11)}:{rnd.choice(['00', '30'])} PM", link_kyc=None, upi=f"{rnd.choice(NAMES).lower()}{rnd.randint(1, 99)}@ok{rnd.choice(['axis', 'sbi', 'hdfcbank', 'icici'])}")


# (category, lang, template). {link} is filled with a look-alike link for the category.
SCAM = {
    "BANK_KYC_BLOCK": [
        ("en", "Dear customer, your {bank} account will be blocked today due to incomplete KYC. Update now: {link}"),
        ("en", "{bank}: Your PAN is not linked. Netbanking will be suspended in {hrs} hrs. Click {link} to update."),
        ("en", "ALERT! Your {bank} YONO / netbanking access is locked. Re-activate here {link} immediately."),
        ("en", "Dear {name}, your {bank} a/c {acct} KYC has expired. Submit Aadhaar & PAN at {link} to avoid suspension."),
        ("en", "Your {bank} debit card will be deactivated tonight. Verify your details: {link} - Team {bank}"),
        ("hi", "प्रिय ग्राहक, आपका {bank} खाता आज KYC अधूरा होने के कारण बंद कर दिया जाएगा। तुरंत अपडेट करें: {link}"),
        ("hi", "{bank}: आपका PAN लिंक नहीं है। {hrs} घंटे में नेटबैंकिंग बंद हो जाएगी। अभी अपडेट करें {link}"),
        ("hi", "आपके {bank} खाते {acct} की KYC समाप्त हो गई है। आधार और PAN यहाँ जमा करें: {link}"),
        ("hinglish", "Dear customer aapka {bank} account aaj block ho jayega, KYC pending hai. Turant update karein {link}"),
        ("hinglish", "{bank} alert: aapka PAN link nahi hai, {hrs} ghante me netbanking band. Update karo {link}"),
        ("hinglish", "Aapka {bank} card kal se band ho jayega. Abhi verify karein: {link} warna account freeze"),
        ("hinglish", "Sir aapka {bank} a/c {acct} KYC expire ho gaya hai, link pe jaakar Aadhaar PAN daalo {link}"),
    ],
    "LOTTERY_PRIZE": [
        ("en", "Congratulations! Your number has won Rs {big} in the {shop} Lucky Draw. Claim now: {link}"),
        ("en", "You are selected as KBC lucky winner of Rs 25,00,000. Call {num} to claim your prize. Pay processing fee first."),
        ("en", "Dear {name}, you won an iPhone in our anniversary contest! Pay delivery charge Rs 499 at {link}"),
        ("en", "WINNER! Your mobile number won Rs {big} in Jio lucky draw. Contact manager {num} within 24 hrs."),
        ("hi", "बधाई हो! आपके नंबर ने {shop} लकी ड्रॉ में ₹{big} जीते हैं। इनाम पाने के लिए क्लिक करें {link}"),
        ("hi", "आप KBC लॉटरी के विजेता हैं, इनाम ₹25 लाख। दावा करने के लिए {num} पर कॉल करें।"),
        ("hi", "प्रिय {name}, आपने कार जीती है! रजिस्ट्रेशन शुल्क ₹{amt} जमा करें: {link}"),
        ("hinglish", "Congratulations aapne {shop} lucky draw me Rs {big} jeete hain! Claim karo abhi {link}"),
        ("hinglish", "KBC se lottery lagi hai aapki 25 lakh ki, manager ko call karo {num}, pehle processing fee bharni hogi"),
        ("hinglish", "Aapka number lucky winner bana hai, free iPhone milega bas delivery charge 499 pay karo {link}"),
    ],
    "COURIER_PARCEL": [
        ("en", "{courier}: Your parcel is on hold due to incomplete address. Update within {hrs} hrs: {link}"),
        ("en", "Your package could not be delivered. Pay Rs 25 redelivery fee at {link} or it will be returned."),
        ("en", "{courier} alert: shipment {ref} held at customs. Clear duty Rs {amt} at {link}"),
        ("en", "Dear customer, your {courier} parcel is pending. Confirm address and pay small fee: {link}"),
        ("hi", "{courier}: आपका पार्सल अधूरे पते के कारण रुका है। {hrs} घंटे में पता अपडेट करें: {link}"),
        ("hi", "आपका पैकेज डिलीवर नहीं हो सका। ₹25 शुल्क दें, वरना वापस भेज दिया जाएगा: {link}"),
        ("hinglish", "{courier}: aapka parcel address galat hone se ruka hai, {hrs} ghante me update karo {link}"),
        ("hinglish", "Aapka courier customs me atka hai, Rs {amt} duty pay karo tabhi milega {link}"),
        ("hinglish", "Parcel deliver nahi hua, 25 rs redelivery fee pay karein warna return ho jayega {link}"),
    ],
    "ELECTRICITY_DISCONNECT": [
        ("en", "Dear consumer, your electricity power will be disconnected tonight at {time} because your previous month bill was not updated. Call officer {num}"),
        ("en", "{discom}: Your electricity connection will be cut today. Bill not updated. Contact {num} immediately."),
        ("en", "Electricity bill pending. Power supply will be stopped at {time}. Pay now: {link} or call {num}"),
        ("hi", "प्रिय उपभोक्ता, आपकी बिजली आज रात {time} बजे काट दी जाएगी क्योंकि पिछला बिल अपडेट नहीं है। अधिकारी से संपर्क करें {num}"),
        ("hi", "{discom}: आपका बिजली कनेक्शन आज काट दिया जाएगा। तुरंत {num} पर कॉल करें।"),
        ("hinglish", "Dear consumer aapki bijli aaj raat {time} kat jayegi, pichla bill update nahi hai. Officer ko call karo {num}"),
        ("hinglish", "{discom}: light connection aaj cut ho jayega, bill pending hai, turant contact karo {num}"),
        ("hinglish", "Bijli bill pending hai, {time} baje supply band. Abhi pay karo {link}"),
    ],
    "JOB_TASK": [
        ("en", "Part-time job! Earn Rs 3000-8000 daily by liking YouTube videos. Contact on WhatsApp {num}"),
        ("en", "Hi {name}, we are hiring for work from home. Simple tasks, daily payment Rs {amt}. Join telegram: {link}"),
        ("en", "Amazon is hiring home-based staff. Salary 30k-50k. Registration fee Rs 999 only. Apply {link}"),
        ("en", "Earn daily by rating hotels online. First task bonus Rs 200. Message HR on WhatsApp {num}"),
        ("hi", "पार्ट टाइम जॉब! यूट्यूब वीडियो लाइक करके रोज़ ₹3000-8000 कमाएँ। व्हाट्सऐप करें {num}"),
        ("hi", "घर बैठे काम, रोज़ भुगतान ₹{amt}। रजिस्ट्रेशन शुल्क केवल ₹999। आवेदन करें {link}"),
        ("hinglish", "Part time job hai, YouTube video like karo aur roz 3000-8000 kamao. WhatsApp karo {num}"),
        ("hinglish", "Ghar baithe kaam, daily payment milega Rs {amt}. Telegram join karo {link}"),
        ("hinglish", "Hotel rating task karo aur paise kamao, pehla task bonus 200 rs. HR ko msg karo {num}"),
    ],
    "LOAN_INSTANT": [
        ("en", "Pre-approved instant loan of Rs {big} without documents! No CIBIL check. Download app: {link}"),
        ("en", "Dear {name}, your personal loan Rs {big} is approved. Pay file charge Rs 1,999 to release amount: {link}"),
        ("en", "Get loan in 5 minutes on Aadhaar only. 0% interest first month. Apply {link}"),
        ("hi", "बिना दस्तावेज़ ₹{big} का तुरंत लोन! कोई CIBIL जाँच नहीं। ऐप डाउनलोड करें: {link}"),
        ("hi", "प्रिय {name}, आपका ₹{big} का लोन स्वीकृत है। राशि पाने के लिए फ़ाइल चार्ज ₹1999 जमा करें: {link}"),
        ("hinglish", "Bina documents ke Rs {big} ka instant loan, CIBIL check nahi. App download karo {link}"),
        ("hinglish", "Aapka loan approve ho gaya hai Rs {big}, bas file charge 1999 pay karo amount release ke liye {link}"),
        ("hinglish", "Sirf Aadhaar pe 5 minute me loan milega, apply karo {link}"),
    ],
    "DIGITAL_ARREST": [
        ("en", "This is CBI cyber cell. A parcel with drugs in your name was seized. You are under digital arrest. Stay on video call and do not tell anyone."),
        ("en", "Notice from Mumbai Police: an FIR is registered against your Aadhaar for money laundering. Call {num} immediately to avoid arrest."),
        ("en", "TRAI: your mobile number will be disconnected in 2 hours due to illegal activity. Press 9 or call {num} to speak to the officer."),
        ("en", "Customs department: your Aadhaar is linked to a suspicious parcel. Transfer Rs {big} to RBI safe account for verification or face arrest."),
        ("hi", "यह CBI साइबर सेल है। आपके नाम का ड्रग्स वाला पार्सल पकड़ा गया है। आप डिजिटल अरेस्ट में हैं, वीडियो कॉल पर रहें और किसी को न बताएँ।"),
        ("hi", "मुंबई पुलिस: आपके आधार पर मनी लॉन्ड्रिंग की FIR दर्ज है। गिरफ़्तारी से बचने के लिए तुरंत {num} पर कॉल करें।"),
        ("hinglish", "CBI cyber cell se bol raha hoon, aapke naam ka drugs parcel pakda gaya hai, aap digital arrest me ho, video call pe raho kisi ko mat batao"),
        ("hinglish", "Police notice: aapke Aadhaar pe FIR hai money laundering ki, arrest se bachne ke liye {num} pe call karo"),
        ("hinglish", "TRAI: aapka number 2 ghante me band ho jayega illegal activity ki wajah se, officer se baat karne ke liye 9 dabao"),
    ],
    "OTP_REQUEST": [
        ("en", "Hi, I sent you an OTP by mistake. Please forward the 6 digit code to me, it is urgent."),
        ("en", "{bank} customer care: to stop the unauthorised transaction please share the OTP you just received with our executive."),
        ("en", "Dear {name}, your reward points will expire. Share the OTP sent to your phone to redeem Rs {amt} cashback."),
        ("hi", "गलती से आपको OTP चला गया है, कृपया 6 अंकों का कोड मुझे भेज दें, बहुत ज़रूरी है।"),
        ("hi", "{bank} कस्टमर केयर: गलत लेनदेन रोकने के लिए अभी आया OTP हमारे अधिकारी को बताएँ।"),
        ("hinglish", "Bhai galti se tumhare number pe OTP chala gaya, please code bhej do urgent hai"),
        ("hinglish", "{bank} customer care se: fraud transaction rokne ke liye jo OTP aaya hai wo batao"),
        ("hinglish", "Reward points expire ho rahe hain, jo OTP aaya hai share karo aur Rs {amt} cashback pao"),
    ],
    "UPI_REFUND": [
        ("en", "Rs {amt} refund is pending for you. Approve the collect request in your UPI app to receive the money."),
        ("en", "Sorry, I sent Rs {amt} to your number by mistake. Please return it to {upi}, screenshot attached."),
        ("en", "To receive your {shop} refund of Rs {amt}, scan the QR code and enter your UPI PIN: {link}"),
        ("hi", "आपका ₹{amt} रिफंड लंबित है। पैसे पाने के लिए अपने UPI ऐप में रिक्वेस्ट स्वीकार करें और PIN डालें।"),
        ("hi", "गलती से आपको ₹{amt} भेज दिए, कृपया {upi} पर वापस कर दें।"),
        ("hinglish", "Aapka Rs {amt} refund pending hai, UPI app me request accept karo aur PIN daalo paise aa jayenge"),
        ("hinglish", "Galti se aapko {amt} rs chale gaye, please {upi} pe wapas bhej do"),
        ("hinglish", "{shop} refund lene ke liye QR scan karo aur UPI PIN daalo {link}"),
    ],
    "INVESTMENT_TRADING": [
        ("en", "Join our stock market VIP group. Guaranteed 300% returns in 30 days. Limited seats: {link}"),
        ("en", "Invest Rs 10,000 and earn Rs 1,000 daily with crypto trading. SEBI registered experts. WhatsApp {num}"),
        ("en", "IPO allotment guaranteed through our institutional account. Deposit now to secure shares: {link}"),
        ("hi", "हमारे स्टॉक मार्केट VIP ग्रुप से जुड़ें। 30 दिनों में 300% पक्का रिटर्न। सीमित सीटें: {link}"),
        ("hi", "₹10,000 निवेश करें और क्रिप्टो ट्रेडिंग से रोज़ ₹1,000 कमाएँ। व्हाट्सऐप करें {num}"),
        ("hinglish", "Stock market VIP group join karo, 30 din me 300% guaranteed return. Link {link}"),
        ("hinglish", "10 hazar lagao aur roz 1000 kamao crypto trading se, WhatsApp karo {num}"),
    ],
}
LINKWORDS = {
    "BANK_KYC_BLOCK": lambda f: link(f["bk"], rnd.choice(["kyc", "update", "verify", "netbanking"])),
    "LOTTERY_PRIZE": lambda f: link(f["shop"], rnd.choice(["lucky-draw", "prize", "winner", "gift"])),
    "COURIER_PARCEL": lambda f: link(f["courier"].replace(" ", ""), rnd.choice(["track", "redelivery", "address"])),
    "ELECTRICITY_DISCONNECT": lambda f: link(f["discom"].replace(" ", ""), "bill", rnd.choice(["pay", "update"])),
    "JOB_TASK": lambda f: link(rnd.choice(["daily-earn", "wfh-jobs", "task-income"])),
    "LOAN_INSTANT": lambda f: link(rnd.choice(["instant", "quick", "easy"]), "loan", rnd.choice(["app", "now"])),
    "DIGITAL_ARREST": lambda f: link("cbi", "notice"),
    "OTP_REQUEST": lambda f: link("rewards", "redeem"),
    "UPI_REFUND": lambda f: link(f["shop"], "refund"),
    "INVESTMENT_TRADING": lambda f: link(rnd.choice(["vip-stock", "crypto-profit", "ipo-allot"])),
}
GENUINE = {
    "BANK_TXN": [
        ("en", "Rs.{amt} debited from A/c {acct} on {date} to VPA {upi} (UPI Ref No {ref}). Not you? Call 1800-111-109 - {bank}"),
        ("en", "Dear Customer, Rs.{amt} credited to your A/c {acct} on {date} by NEFT. Avl Bal Rs.{big}. -{bank}"),
        ("en", "Your {bank} Credit Card ending {acct} was used for Rs {amt} at {shop} on {date}. If not done by you, report immediately on the official app."),
        ("hi", "आपके खाते {acct} से {date} को ₹{amt} डेबिट हुए (UPI Ref {ref})। यह आपने नहीं किया? {bank} की आधिकारिक हेल्पलाइन पर कॉल करें।"),
        ("hinglish", "A/c {acct} me Rs {amt} credit hue {date} ko. Avl bal Rs {big} - {bank}"),
    ],
    "OTP_GENUINE": [
        ("en", "{otp} is your OTP for transaction of Rs {amt} at {shop}. Valid for 5 mins. Do not share this OTP with anyone. - {bank}"),
        ("en", "Your {shop} verification code is {otp}. Never share this code with anyone, not even {shop} staff."),
        ("en", "Use {otp} to log in to your {bank} netbanking. {bank} will never ask for your OTP by call or SMS."),
        ("hi", "{shop} पर ₹{amt} के लेनदेन के लिए आपका OTP {otp} है। यह OTP किसी से साझा न करें। - {bank}"),
        ("hinglish", "Aapka {shop} login code {otp} hai. Ise kisi ke saath share na karein."),
    ],
    "DELIVERY": [
        ("en", "Your {shop} order with {courier} is out for delivery today. Track it in the {shop} app."),
        ("en", "Delivered: your {shop} package was delivered on {date}. Rate your experience in the app."),
        ("en", "{courier}: Shipment {ref} has been dispatched and will reach you by {date}."),
        ("hi", "आपका {shop} ऑर्डर आज डिलीवरी के लिए निकल चुका है। {shop} ऐप में ट्रैक करें।"),
        ("hinglish", "Aapka {shop} order aaj deliver hoga, app me track karein."),
    ],
    "BILL_REMINDER": [
        ("en", "{discom}: Your electricity bill of Rs {amt} for {date} is generated. Due date {date}. Pay via the official app or website."),
        ("en", "Reminder: your mobile bill of Rs {amt} is due on {date}. Pay in the app to avoid late fee."),
        ("en", "Your {bank} credit card statement is ready. Total due Rs {amt}, minimum due Rs 500, due date {date}."),
        ("hi", "{discom}: आपका ₹{amt} का बिजली बिल बन गया है। अंतिम तिथि {date}। आधिकारिक ऐप से भुगतान करें।"),
        ("hinglish", "Reminder: aapka mobile bill Rs {amt} {date} tak due hai, app se pay karein."),
    ],
    "PROMO_GENUINE": [
        ("en", "{shop} Big Sale is live! Up to 70% off on fashion. Shop now in the {shop} app. T&C apply."),
        ("en", "Recharge with Rs 299 and get 2GB/day for 28 days. Recharge in the official app. -Jio"),
        ("en", "Weekend offer: flat 20% off on groceries in the {shop} app. Valid till {date}."),
        ("hi", "{shop} की बड़ी सेल शुरू! फ़ैशन पर 70% तक की छूट। {shop} ऐप में खरीदें।"),
        ("hinglish", "{shop} sale live hai, fashion pe 70% tak off. App me shop karo."),
    ],
    "PERSONAL": [
        ("en", "Hi {name}, reached home safely. Will call you after dinner."),
        ("en", "Meeting moved to {time} tomorrow. Please bring the documents."),
        ("en", "Happy birthday {name}! Have a great year ahead."),
        ("en", "Can you pick up milk and bread on the way back?"),
        ("hi", "{name}, मैं घर पहुँच गया हूँ। खाने के बाद फ़ोन करूँगा।"),
        ("hi", "कल की मीटिंग {time} बजे है, कागज़ात साथ लाना।"),
        ("hinglish", "Ghar pahunch gaya, dinner ke baad call karta hoon"),
        ("hinglish", "Kal meeting {time} baje hai, documents le aana"),
        ("hinglish", "Happy birthday {name}! Bahut saari shubhkamnayein"),
    ],
}


def render(template, cat, scam):
    f = F()
    if scam:
        f["link"] = LINKWORDS[cat](f)
    return template.format(**f)


def main():
    rows = []
    for scam, groups, per_template in ((True, SCAM, 17), (False, GENUINE, 42)):
        for cat, temps in groups.items():
            idx = list(range(len(temps)))
            rnd.shuffle(idx)
            n_test = max(1, round(len(temps) * 0.2))
            test_ids = set(idx[:n_test])
            for t, (lang, tpl) in enumerate(temps):
                seen = set()
                for _ in range(per_template * 3):
                    if len([1 for r in rows if r[4] == f"{cat}#{t}"]) >= per_template:
                        break
                    text = render(tpl, cat, scam)
                    if text in seen:
                        continue
                    seen.add(text)
                    rows.append((text, "SCAM" if scam else "GENUINE", cat, lang, f"{cat}#{t}", "test" if t in test_ids else "train"))
    rnd.shuffle(rows)
    with open(OUT, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["text", "label", "category", "lang", "template", "split"])
        w.writerows(rows)
    from collections import Counter
    print(len(rows), "messages;", Counter(r[1] for r in rows), Counter(r[3] for r in rows))
    print("scam categories:", len(SCAM), "test rows:", sum(r[5] == "test" for r in rows))


if __name__ == "__main__":
    main()
