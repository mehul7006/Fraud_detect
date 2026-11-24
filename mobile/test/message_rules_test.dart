import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/message_rules.dart';

/// 14.5 rules (on ≥40 samples, each explains itself) and 14.10 false-positive guard on all genuine messages.
void main() {
  final scams = <String, String>{
    // English
    'Dear customer, your SBI account will be blocked today due to incomplete KYC. Update now: http://sbi-kyc.top/': 'ACCOUNT_BLOCK_THREAT',
    'Hi, I sent you an OTP by mistake. Please forward the 6 digit code to me, it is urgent.': 'ASKS_FOR_SECRET',
    'HDFC customer care: to stop the unauthorised transaction please share the OTP you just received with our executive.': 'ASKS_FOR_SECRET',
    'This is CBI cyber cell. A parcel with drugs in your name was seized. You are under digital arrest.': 'AUTHORITY_THREAT',
    'TRAI: your mobile number will be disconnected in 2 hours due to illegal activity. Press 9 to speak to the officer.': 'AUTHORITY_THREAT',
    'Congratulations! Your number has won Rs 25,00,000 in the Flipkart Lucky Draw. Claim now: flipkart-prize.top': 'PRIZE_CLAIM',
    'You won an iPhone in our contest! Pay delivery charge Rs 499 at gift-claim.xyz': 'PAY_TO_RECEIVE',
    'India Post: your parcel is on hold. Pay Rs 25 redelivery fee at indiapost-track.top or it will be returned.': 'PAY_TO_RECEIVE',
    'Dear consumer, your electricity power will be disconnected tonight at 9:30 PM. Call officer 98XXXXX410': 'BILL_DISCONNECT',
    'Part-time job! Earn Rs 3000-8000 daily by liking YouTube videos. Contact on WhatsApp': 'EASY_MONEY_JOB',
    'Pre-approved instant loan of Rs 2,00,000 without documents! No CIBIL check. Download app now': 'NO_DOCUMENT_LOAN',
    'Your personal loan is approved. Pay file charge Rs 1,999 to release amount': 'NO_DOCUMENT_LOAN',
    'Rs 4,500 refund is pending. Approve the collect request in your UPI app and enter PIN to receive the money.': 'REFUND_COLLECT',
    'Sorry, I sent Rs 5,000 to your number by mistake. Please return it to rahul12@okaxis': 'REFUND_COLLECT',
    'Join our stock market VIP group. Guaranteed 300% returns in 30 days.': 'GUARANTEED_RETURNS',
    'Your PAN is not linked. Netbanking will be suspended in 24 hrs. Click http://pan-update.site/ to update.': 'ACCOUNT_BLOCK_THREAT',
    'Notice from Mumbai Police: an FIR is registered against your Aadhaar for money laundering. Call immediately.': 'AUTHORITY_THREAT',
    'Your reward points will expire. Share the OTP sent to your phone to redeem Rs 7,500 cashback.': 'ASKS_FOR_SECRET',
    // Hindi
    'प्रिय ग्राहक, आपका SBI खाता आज KYC अधूरा होने के कारण बंद कर दिया जाएगा। तुरंत अपडेट करें: sbi-kyc.top': 'ACCOUNT_BLOCK_THREAT',
    'गलती से आपको OTP चला गया है, कृपया 6 अंकों का कोड मुझे भेज दें।': 'ASKS_FOR_SECRET',
    'यह CBI साइबर सेल है। आप डिजिटल अरेस्ट में हैं, वीडियो कॉल पर रहें।': 'AUTHORITY_THREAT',
    'बधाई हो! आपके नंबर ने लकी ड्रॉ में ₹25 लाख जीते हैं। इनाम पाने के लिए क्लिक करें': 'PRIZE_CLAIM',
    'प्रिय उपभोक्ता, आपकी बिजली आज रात 9:30 बजे काट दी जाएगी। अधिकारी से संपर्क करें 98XXXXX410': 'BILL_DISCONNECT',
    'घर बैठे काम, रोज़ भुगतान ₹2000। रजिस्ट्रेशन शुल्क केवल ₹999।': 'EASY_MONEY_JOB',
    'बिना दस्तावेज़ ₹2,00,000 का तुरंत लोन! ऐप डाउनलोड करें': 'NO_DOCUMENT_LOAN',
    'आपका ₹4500 रिफंड लंबित है। पैसे पाने के लिए UPI ऐप में रिक्वेस्ट स्वीकार करें और PIN डालें।': 'REFUND_COLLECT',
    'मुंबई पुलिस: आपके आधार पर FIR दर्ज है। गिरफ़्तारी से बचने के लिए कॉल करें।': 'AUTHORITY_THREAT',
    // Hinglish
    'Dear customer aapka SBI account aaj block ho jayega, KYC pending hai. Turant update karein': 'ACCOUNT_BLOCK_THREAT',
    'Bhai galti se tumhare number pe OTP chala gaya, please code bhej do urgent hai': 'ASKS_FOR_SECRET',
    'CBI cyber cell se bol raha hoon, aapke naam ka drugs parcel pakda gaya hai, aap digital arrest me ho': 'AUTHORITY_THREAT',
    'KBC se lottery lagi hai aapki 25 lakh ki, pehle processing fee bharni hogi': 'PRIZE_CLAIM',
    'Dear consumer aapki bijli aaj raat 9:30 kat jayegi, pichla bill update nahi hai': 'BILL_DISCONNECT',
    'Part time job hai, YouTube video like karo aur roz 3000-8000 kamao': 'EASY_MONEY_JOB',
    'Bina documents ke Rs 2 lakh ka instant loan, CIBIL check nahi': 'NO_DOCUMENT_LOAN',
    'Galti se aapko 5000 rs chale gaye, please wapas bhej do': 'REFUND_COLLECT',
    'Stock market VIP group join karo, 30 din me 300% guaranteed return': 'GUARANTEED_RETURNS',
  };
  final genuine = <String>[
    '482913 is your OTP for transaction of Rs 1,250 at Amazon. Valid for 5 mins. Do not share this OTP with anyone. - HDFC Bank',
    'Your Flipkart verification code is 553201. Never share this code with anyone, not even Flipkart staff.',
    'Use 902114 to log in to your SBI netbanking. SBI will never ask for your OTP by call or SMS.',
    'Rs.1,200 debited from A/c XX4521 on 12-Nov-26 to VPA shop@okaxis (UPI Ref No 6243117890). Not you? Call 1800-111-109',
    'Your Myntra order is out for delivery today. Track it in the Myntra app.',
    'BSES: Your electricity bill of Rs 1,840 is generated. Due date 20-Nov-26. Pay via the official app.',
    'Hi Priya, reached home safely. Will call you after dinner.',
    'Ghar pahunch gaya, dinner ke baad call karta hoon',
    'Amazon पर ₹1250 के लेनदेन के लिए आपका OTP 482913 है। यह OTP किसी से साझा न करें। - HDFC Bank',
    'Aapka Flipkart login code 553201 hai. Ise kisi ke saath share na karein.',
  ];

  test('every scam sample is explained by the right rule and scores at least suspicious (${'≥'}40 samples with the genuine ones)', () {
    expect(scams.length + genuine.length, greaterThanOrEqualTo(40));
    for (final e in scams.entries) {
      final r = MessageRules.check(e.key);
      // rules explain; the final verdict also uses the on-device model (MessageClassifier.kt)
      expect(r.score, greaterThanOrEqualTo(35), reason: '${e.key} -> ${r.score} ${r.hits.map((h) => h.code)}');
      expect(r.hits.map((h) => h.code), contains(e.value), reason: e.key);
      expect(r.hits.every((h) => h.en.isNotEmpty && h.hi.isNotEmpty), isTrue);
    }
  });

  test('real OTP, bank, delivery and personal messages stay safe', () {
    for (final g in genuine) {
      final r = MessageRules.check(g);
      expect(r.scam, isFalse, reason: '$g -> ${r.score} ${r.hits.map((h) => h.code)}');
    }
  });

  test('bank message from a personal mobile number is suspicious', () {
    final r = MessageRules.check('SBI: your account is active. Update details on the link sent.', sender: '+91 98765 43210');
    expect(r.hits.map((h) => h.code), contains('PERSONAL_NUMBER_SENDER'));
  });

  test('14.10: false-positive rate < 2 % on all genuine messages of the corpus; catch rate printed', () {
    final f = File('../ml/messages/corpus.csv');
    if (!f.existsSync()) return; // corpus lives in the repo; skip in trimmed checkouts
    final lines = f.readAsLinesSync().skip(1);
    var gen = 0, fp = 0, sc = 0, tp = 0;
    for (final l in lines) {
      final m = RegExp(r'^"?(.*?)"?,(SCAM|GENUINE),').firstMatch(l);
      if (m == null) continue;
      final text = m.group(1)!.replaceAll('""', '"');
      final scam = MessageRules.check(text).scam;
      if (m.group(2) == 'GENUINE') { gen++; if (scam) fp++; } else { sc++; if (scam) tp++; }
    }
    // ignore: avoid_print
    print('rules alone: genuine $gen, false positives $fp (${(fp * 100 / gen).toStringAsFixed(2)} %); scams $sc, caught $tp (${(tp * 100 / sc).toStringAsFixed(1)} %)');
    expect(gen, greaterThanOrEqualTo(500));
    expect(fp / gen, lessThan(0.02));
  });
}
