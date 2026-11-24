# 10.12 labelled URL corpus generator.
# PHISH = link patterns seen in Indian SMS / WhatsApp scams (bank / police / CERT-In advisories), written by hand.
# LEGIT = official brand pages, well-known Indian sites, and a fixed random sample of 80 less-known real sites
#         (Tranco ranks 100k-1M, seed 20261123) - none of them is in the popular allowlist (top 100k).
# Usage: python make_corpus.py <tranco top-1m.csv> <out.csv>
import csv
import random
import sys

tranco, out = sys.argv[1], sys.argv[2]
P = [
    # bank KYC / account block
    ("http://sbi-kyc-update.top/verify", "bank-kyc"), ("https://onlinesbi-verify.co/login", "bank-kyc"),
    ("http://sbiyono-kyc.in/update.php", "bank-kyc"), ("https://sbi.co.in.kyc-verify.top/", "subdomain"),
    ("http://yono-sbi-update.xyz/", "bank-kyc"), ("https://statebank-rewards.online/redeem", "rewards"),
    ("http://hdfcbank-netbanking.info/login", "bank-kyc"), ("https://hdfc-kyc-update.site/", "bank-kyc"),
    ("http://hdfcbank.com.secure-login.xyz/", "subdomain"), ("https://icicibank-reward.xyz/points", "rewards"),
    ("http://icici-kyc.top/", "bank-kyc"), ("https://icicl-bank.com/login", "typo"),
    ("http://axisbank-update.online/kyc", "bank-kyc"), ("https://axls-bank.in/", "typo"),
    ("http://kotak-kyc-verify.com/", "bank-kyc"), ("https://pnbindia-kyc.in/update", "bank-kyc"),
    ("http://bankofbaroda-kyc.xyz/", "bank-kyc"), ("https://canarabank-update.site/kyc", "bank-kyc"),
    ("http://unionbankofindia-kyc.online/", "bank-kyc"), ("https://indusind-reward-points.in/", "rewards"),
    ("http://yesbank-kyc.top/verify", "bank-kyc"), ("https://idfcfirstbank-update.xyz/", "bank-kyc"),
    ("http://federalbank-kyc.co/", "bank-kyc"), ("https://bandhanbank-loan.online/apply", "loan"),
    ("http://sbicard-rewards.top/redeem", "rewards"), ("https://americanexpress-india-verify.com/", "bank-kyc"),
    # UPI / wallets
    ("http://paytm-kyc-update.in/", "wallet"), ("https://paytrn.com/login", "homoglyph"),
    ("http://phonepe-kyc.in/verify", "wallet"), ("https://phonepe-cashback.xyz/claim", "wallet"),
    ("http://gpay-reward.online/scratch", "wallet"), ("https://googlepay-cashback.site/", "wallet"),
    ("http://bhimupi-refund.in/", "wallet"), ("https://mobikwik-kyc.top/", "wallet"),
    ("http://cred-rewards.xyz/", "wallet"), ("https://paytm.com-kyc.info/", "subdomain"),
    # government
    ("http://incometax-refund.in/claim", "gov-refund"), ("https://incometax.gov.in.refund-status.xyz/", "subdomain"),
    ("http://itrefund-status.online/", "gov-refund"), ("https://uidai-aadhaar-update.com/", "gov-id"),
    ("http://myaadhaar-update.in/biometric", "gov-id"), ("https://aadhaar-pan-link.xyz/", "gov-id"),
    ("http://epfo-claim.online/", "gov"), ("https://epfindia-kyc.in/", "gov"),
    ("http://pmkisan-registration.in/", "gov-scheme"), ("https://echallan-parivahan.in/pay", "challan"),
    ("http://parivahan-echallan.xyz/", "challan"), ("https://echallan.parivahan.gov.in.pay-fine.top/", "subdomain"),
    ("http://fastag-kyc.in/update", "gov"), ("https://nhai-fastag-recharge.online/", "gov"),
    ("http://gst-refund.top/", "gov-refund"), ("https://passportseva-apply.in/", "gov"),
    ("http://digilocker-verify.xyz/", "gov"), ("https://umang-update.site/", "gov"),
    ("http://pmjay-ayushman-card.online/", "gov-scheme"),
    # delivery
    ("http://indiapost-delivery.top/track", "delivery"), ("https://indiapost.gov.in.parcel-hold.xyz/", "subdomain"),
    ("http://indiapost-redelivery.in/", "delivery"), ("https://bluedart-tracking.xyz/", "delivery"),
    ("http://delhivery-reschedule.top/", "delivery"), ("https://dtdc-parcel-hold.online/", "delivery"),
    ("http://fedex-india-customs.xyz/", "delivery"),
    # utilities
    ("http://bescom-bill-update.in/", "utility"), ("https://tneb-bill-pay.online/", "utility"),
    ("http://mahadiscom-bill.top/", "utility"), ("https://bses-bill-update.xyz/", "utility"),
    ("http://indane-gas-kyc.in/", "utility"), ("https://hpgas-subsidy.online/", "utility"),
    ("http://bharatgas-kyc.xyz/", "utility"), ("https://tatapower-bill.site/", "utility"),
    ("http://kseb-bill-disconnect.in/", "utility"), ("https://uppcl-bill-pay.online/", "utility"),
    # telecom
    ("http://jio-recharge-offer.xyz/", "telecom"), ("https://myjio-free-recharge.online/", "telecom"),
    ("http://airtel-recharge.online/", "telecom"), ("https://airtel-kyc.top/", "telecom"),
    ("http://myvi-free-data.xyz/", "telecom"), ("https://bsnl-kyc-update.in/", "telecom"),
    ("http://jio.com.free-5g.top/", "subdomain"),
    # shopping
    ("http://fleepkart.com/big-billion-days", "typo"), ("https://flipkarrt.com/", "typo"),
    ("http://f1ipkart.com/offer", "homoglyph"), ("https://flipkart-sale.xyz/", "combo"),
    ("http://flipkart-big-billion-days.in/", "combo"), ("https://amazon-prize.xyz/", "combo"),
    ("http://amazon-great-indian-sale.top/", "combo"), ("https://xn--mazon-3ve.com/", "idn"),
    ("http://meesho-bonus.online/", "combo"), ("https://myntra-sale.top/", "combo"),
    ("http://nykaa-free-gift.xyz/", "combo"), ("https://ajio-lucky-draw.in/", "combo"),
    ("http://snapdeal-lucky-draw.com/", "combo"), ("https://tatacliq-offer.site/", "combo"),
    ("http://jiomart-offer.top/", "combo"), ("https://bigbasket-cashback.xyz/", "combo"),
    ("http://swiggy-refund.in/", "combo"), ("https://zomato-refund.online/", "combo"),
    ("http://dmart-anniversary-gift.xyz/", "combo"),
    # social / messaging / streaming
    ("https://whatsapp-web.cc/", "combo"), ("http://whatsapp-gold.download/", "combo"),
    ("https://whatsap-verify.com/", "typo"), ("http://telegram-premium-free.xyz/", "combo"),
    ("https://instagrarn.com/accounts/login", "homoglyph"), ("http://instagram-verify-badge.com/", "combo"),
    ("https://faceboook.com/login", "typo"), ("http://facebook-security-check.xyz/", "combo"),
    ("https://netflix-billing.site/update", "combo"), ("http://hotstar-free-subscription.top/", "combo"),
    # travel / jobs / investing
    ("https://irctc-refund.in/", "combo"), ("http://irctc-tatkal-booking.xyz/", "combo"),
    ("https://naukri-jobs-hiring.top/", "combo"), ("http://zerodha-ipo-allotment.online/", "combo"),
    ("https://groww-investment-plan.xyz/", "combo"), ("http://binance-india-bonus.top/", "combo"),
    ("https://makemytrip-refund.in/", "combo"), ("http://indigo-refund.online/", "combo"),
    # raw IP, APK, free hosting, shorteners, credentials trick, brand only in the path
    ("http://192.168.4.20/sbi-kyc-update/verify.apk", "apk-ip"), ("http://103.155.92.17/hdfc/login", "ip"),
    ("http://45.88.66.10/yono.apk", "apk-ip"), ("https://sbi-yono.netlify.app/", "free-host"),
    ("https://hdfc-netbanking.web.app/", "free-host"), ("https://paytm-kyc.firebaseapp.com/", "free-host"),
    ("https://icici-rewards.github.io/", "free-host"), ("https://flipkart-offer.vercel.app/", "free-host"),
    ("https://sbi-kyc-update.weebly.com/", "free-host"), ("https://bit.ly/3sbikyc", "shortener"),
    ("https://tinyurl.com/indiapost-hold", "shortener"), ("https://cutt.ly/aadhaar-update", "shortener"),
    ("https://www.sbi.co.in@kyc-update.xyz/", "credentials"), ("https://paytm.com@verify-kyc.top/login", "credentials"),
    ("http://customer-care-kyc-update.xyz/sbi/login", "path"), ("https://secure-verify-account.top/hdfc/netbanking", "path"),
    ("https://download-apk-update.xyz/sbi_yono.apk", "apk"), ("http://pm-kisan-yojana.top/apply.apk", "apk"),
]
L = [
    "https://www.flipkart.com/", "https://www.flipkart.com/offers-store", "https://seller.flipkart.com/",
    "https://www.amazon.in/gp/goldbox", "https://www.amazon.in/", "https://www.myntra.com/men", "https://www.meesho.com/",
    "https://www.ajio.com/", "https://www.nykaa.com/", "https://www.snapdeal.com/", "https://www.jiomart.com/",
    "https://www.bigbasket.com/", "https://blinkit.com/", "https://www.swiggy.com/", "https://www.zomato.com/",
    "https://www.dmart.in/", "https://www.onlinesbi.sbi/", "https://retail.onlinesbi.sbi/retail/login.htm",
    "https://sbi.co.in/web/personal-banking", "https://www.hdfcbank.com/personal/pay/cards",
    "https://netbanking.hdfcbank.com/netbanking/", "https://www.icicibank.com/",
    "https://infinity.icicibank.com/corp/AuthenticationController", "https://www.axisbank.com/",
    "https://www.kotak.com/en/personal-banking.html", "https://www.pnbindia.in/", "https://www.bankofbaroda.in/",
    "https://canarabank.com/", "https://www.unionbankofindia.co.in/", "https://www.indusind.com/", "https://www.yesbank.in/",
    "https://www.idfcfirstbank.com/", "https://www.federalbank.co.in/", "https://www.sbicard.com/", "https://paytm.com/",
    "https://www.phonepe.com/", "https://pay.google.com/about/", "https://www.npci.org.in/what-we-do/upi/product-overview",
    "https://www.mobikwik.com/", "https://cred.club/", "https://www.incometax.gov.in/iec/foportal/",
    "https://eportal.incometax.gov.in/iec/foservices/", "https://uidai.gov.in/", "https://myaadhaar.uidai.gov.in/",
    "https://www.epfindia.gov.in/", "https://unifiedportal-mem.epfindia.gov.in/memberinterface/", "https://pmkisan.gov.in/",
    "https://echallan.parivahan.gov.in/", "https://parivahan.gov.in/parivahan/", "https://www.gst.gov.in/",
    "https://www.passportindia.gov.in/", "https://www.digilocker.gov.in/", "https://web.umang.gov.in/",
    "https://www.indiapost.gov.in/", "https://www.bluedart.com/tracking", "https://www.delhivery.com/", "https://www.dtdc.in/",
    "https://www.fedex.com/en-in/home.html", "https://bescom.karnataka.gov.in/", "https://www.tnebltd.gov.in/",
    "https://wss.mahadiscom.in/wss/wss", "https://www.bsesdelhi.com/", "https://cx.indianoil.in/", "https://www.tatapower.com/",
    "https://www.jio.com/", "https://www.airtel.in/", "https://www.myvi.in/", "https://www.bsnl.co.in/",
    "https://web.whatsapp.com/", "https://www.whatsapp.com/", "https://telegram.org/", "https://www.instagram.com/accounts/login/",
    "https://www.facebook.com/", "https://www.netflix.com/in/", "https://www.hotstar.com/in",
    "https://www.irctc.co.in/nget/train-search", "https://www.naukri.com/", "https://zerodha.com/", "https://groww.in/",
    "https://www.binance.com/en-IN", "https://www.makemytrip.com/", "https://www.goindigo.in/",
    "https://www.google.com/", "https://mail.google.com/", "https://www.youtube.com/watch?v=abc", "https://en.wikipedia.org/wiki/India",
    "https://github.com/", "https://stackoverflow.com/", "https://www.ndtv.com/", "https://timesofindia.indiatimes.com/",
    "https://www.thehindu.com/", "https://www.hindustantimes.com/", "https://indianexpress.com/", "https://www.moneycontrol.com/",
    "https://economictimes.indiatimes.com/", "https://www.cricbuzz.com/", "https://www.justdial.com/", "https://www.olx.in/",
    "https://www.flickr.com/", "https://flipboard.com/", "https://www.outlookindia.com/", "https://www.bbc.com/news",
    "https://www.reddit.com/", "https://www.iitb.ac.in/", "https://www.du.ac.in/", "https://www.rbi.org.in/",
    "https://www.sebi.gov.in/", "https://www.india.gov.in/",
]
rank = []
for line in open(tranco, encoding='utf-8'):
    r, d = line.strip().split(',', 1)
    if 100000 < int(r) <= 1000000:
        rank.append(d)
random.seed(20261123)
sample = ["https://" + d + "/" for d in random.sample(rank, 80)]
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write("# 10.12 labelled URL corpus. PHISH: patterns from Indian SMS/WhatsApp scams (advisories). LEGIT: official /\n")
    f.write("# well-known sites + 80 random less-known real sites (Tranco ranks 100k-1M, seed 20261123).\n")
    f.write("# Regenerate: python tools/lookalike/make_corpus.py <tranco top-1m.csv> <this file>\n")
    w = csv.writer(f, lineterminator='\n')
    w.writerow(["url", "label", "category"])
    for u, c in P:
        w.writerow([u, "PHISH", c])
    for u in L:
        w.writerow([u, "LEGIT", "known"])
    for u in sample:
        w.writerow([u, "LEGIT", "tranco-sample"])
print(len(P), "phish", len(L) + len(sample), "legit")
