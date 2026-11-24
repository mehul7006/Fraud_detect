/// 14.5: on-device scam rules for message text (English, Hindi, Hinglish). Each rule explains itself in plain words.
/// Runs before / beside the ML model (MessageClassifier.kt): rules give the "why", the model catches new wording.
class RuleHit {
  final String code;
  final int points;
  final String en, hi;
  const RuleHit(this.code, this.points, this.en, this.hi);
}

class RuleResult {
  final int score; // 0..100
  final List<RuleHit> hits;
  const RuleResult(this.score, this.hits);
  bool get scam => score >= 50;
}

class _Rule {
  final String code;
  final int points;
  final RegExp re;
  final String en, hi;
  _Rule(this.code, this.points, String pattern, this.en, this.hi) : re = RegExp(pattern, caseSensitive: false, unicode: true);
}

class MessageRules {
  static final _rules = <_Rule>[
    _Rule('ASKS_FOR_SECRET', 55,
        // Dart's \b only knows Latin letters: Hindi words are matched without it (कोड, भेज, बताएँ)
        r'((share|send|bata\w*|bhej\w*|forward|tell|batao|बताएँ|बताएं|भेज|दें).{0,40}(\b(otp|pin|cvv|password|mpin|code)\b|कोड))|((\b(otp|code)\b|कोड).{0,30}(share|bhej|bata|send|forward|बताएँ|भेज))|(enter|daal\w*|डालें).{0,20}(upi )?pin',
        'Asks you to share an OTP, PIN or password - no bank or company ever does this.',
        'आपसे OTP, PIN या पासवर्ड माँगता है - कोई बैंक या कंपनी ऐसा कभी नहीं करती।'),
    _Rule('ACCOUNT_BLOCK_THREAT', 35,
        r'(account|a/c|card|netbanking|yono|khata|खाता|खाते|कार्ड|नेटबैंकिंग).{0,50}(block|suspend|band|बंद|freeze|deactivat|lock|expire|समाप्त)',
        'Threatens that your account or card will be blocked.', 'धमकी देता है कि आपका खाता या कार्ड बंद हो जाएगा।'),
    _Rule('URGENCY', 15,
        r'\b(immediately|urgent\w*|today|tonight|within \d+ ?(hrs?|hours?)|turant|abhi|jaldi|warna|otherwise)\b|तुरंत|अभी|आज रात|घंटे में|वरना',
        'Pushes you to act immediately.', 'तुरंत कुछ करने का दबाव बनाता है।'),
    _Rule('AUTHORITY_THREAT', 55, // never genuine by message: enough on its own
        r'\b(cbi|police|customs|trai|fir|arrest\w*|court|narcotics|money laundering|digital arrest|cyber cell)\b|गिरफ़्तारी|पुलिस|डिजिटल अरेस्ट|साइबर सेल',
        'Uses police / CBI / customs threats - real officers never arrest anyone by phone or message.',
        'पुलिस / CBI / कस्टम की धमकी - असली अधिकारी फ़ोन या मैसेज पर कभी गिरफ़्तार नहीं करते।'),
    _Rule('PRIZE_CLAIM', 40,
        r'\b(won|winner|lucky draw|lottery|claim|jeet\w*|kbc|inaam|prize|free iphone)\b|जीत\w*|इनाम|लॉटरी|लकी ड्रॉ|विजेता',
        'Promises a prize or lottery you never entered.', 'ऐसे इनाम या लॉटरी का वादा जिसमें आपने कभी भाग नहीं लिया।'),
    _Rule('PAY_TO_RECEIVE', 40,
        r'(processing|registration|file|delivery|redelivery|customs|clear(ance)?)\s*(fee|charge|duty)|(fee|charge|शुल्क).{0,30}(pay|bhar\w*|jama|जमा|दें)|शुल्क',
        'Asks you to pay a fee to receive money, a parcel or a prize.', 'पैसा, पार्सल या इनाम पाने के लिए शुल्क माँगता है।'),
    _Rule('EASY_MONEY_JOB', 35,
        r'(earn|kamao|kamaye\w*|कमाएँ|कमाई).{0,40}(daily|roz|per day|रोज़|रोज)|(like|rating|task).{0,30}(video|hotel|youtube|paise|payment)|part[ -]?time job|ghar baithe|घर बैठे',
        'Offers easy money for simple tasks - a common job scam.', 'आसान काम के बदले आसान पैसे - एक आम नौकरी धोखा।'),
    _Rule('NO_DOCUMENT_LOAN', 35,
        r'(without documents|bina documents?|no cibil|cibil check nahi|बिना दस्तावेज़|बिना डॉक्यूमेंट)|loan.{0,40}(file charge|processing fee)',
        'Promises a loan without checks or asks a fee first - typical fake loan app.', 'बिना जाँच लोन या पहले शुल्क - नकली लोन ऐप का तरीका।'),
    _Rule('REFUND_COLLECT', 40,
        r'(refund|cashback|paise|रिफंड).{0,60}(approve|accept|scan|qr|pin|स्वीकार)|(sent|bhej\w*|chale gaye|भेज दिए).{0,40}(by mistake|galti|गलती)|(by mistake|galti|गलती).{0,40}(sent|chale gaye|bhej\w*|transfer|भेज दिए|चले गए)',
        'A "refund" or "sent by mistake" trick: approving a request or entering your PIN SENDS money.',
        '"रिफंड" या "गलती से भेजा" की चाल: रिक्वेस्ट स्वीकार करने या PIN डालने से पैसा जाता है।'),
    _Rule('BILL_DISCONNECT', 35,
        r'(electricity|bijli|light|power|बिजली).{0,60}(disconnect\w*|cut|kat\w*|band|काट|कट)',
        'Says your electricity will be cut tonight - electricity boards do not send such messages with a phone number.',
        'कहता है कि आज रात बिजली कट जाएगी - बिजली विभाग ऐसे मैसेज फ़ोन नंबर के साथ नहीं भेजता।'),
    _Rule('GUARANTEED_RETURNS', 35,
        r'(guarantee\w*|पक्का|pakka).{0,30}(return|profit|रिटर्न|मुनाफ़ा)|\d+ ?% (returns?|रिटर्न)|vip (group|stock)|ipo allotment guaranteed',
        'Promises guaranteed investment returns - no real investment can.', 'पक्के निवेश रिटर्न का वादा - असली निवेश ऐसा नहीं कर सकता।'),
    _Rule('HAS_LINK', 10, r'(https?://|www\.)\S+|\b[\w-]+\.(top|xyz|online|site|click|info)\b',
        'Contains a link - FraudShield checks it separately.', 'इसमें लिंक है - FraudShield उसकी अलग से जाँच करता है।'),
  ];

  /// "Do not share this OTP" / "never ask for your OTP" - the wording of REAL OTP / bank messages.
  static final _genuineOtp = RegExp(
      r"(do not|don'?t|never|na karein|mat|न करें|किसी से साझा न|share na).{0,40}(share|disclose|ask|batay|साझा|बताएँ)|will never ask|kisi ke saath share na",
      caseSensitive: false, unicode: true);

  /// A plain mobile number as sender of a message that claims to be from a bank / company (sender optional).
  static final _mobileSender = RegExp(r'^\+?(91)?[6-9]\d{9}$');
  static final _claimsCompany = RegExp(r'\b(bank|sbi|hdfc|icici|axis|kotak|pnb|paytm|phonepe|amazon|flipkart|india post|electricity)\b',
      caseSensitive: false);

  static RuleResult check(String text, {String? sender}) {
    final hits = <RuleHit>[];
    final genuineOtp = _genuineOtp.hasMatch(text);
    for (final r in _rules) {
      if (!r.re.hasMatch(text)) continue;
      if (genuineOtp && (r.code == 'ASKS_FOR_SECRET' || r.code == 'URGENCY')) continue; // "do not share" is the opposite
      hits.add(RuleHit(r.code, r.points, r.en, r.hi));
    }
    if (sender != null && _mobileSender.hasMatch(sender.replaceAll(' ', '')) && _claimsCompany.hasMatch(text)) {
      hits.add(const RuleHit('PERSONAL_NUMBER_SENDER', 25, 'Claims to be a bank or company but comes from a personal mobile number.',
          'बैंक या कंपनी होने का दावा, पर मैसेज एक निजी मोबाइल नंबर से आया है।'));
    }
    final score = hits.fold<int>(0, (s, h) => s + h.points).clamp(0, 100);
    hits.sort((a, b) => b.points.compareTo(a.points));
    return RuleResult(score, hits);
  }
}
