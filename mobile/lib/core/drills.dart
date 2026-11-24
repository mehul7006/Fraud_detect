/// 15.10: short scam drills. Each step shows what the scammer says and three things the user could do; one is safe.
/// Text is bilingual here (like message_rules.dart) because it is content, not UI chrome.
class DrillChoice {
  final String en, hi;
  final bool safe;
  final String whyEn, whyHi;
  const DrillChoice(this.en, this.hi, this.safe, this.whyEn, this.whyHi);
}

class DrillStep {
  final String en, hi;
  final List<DrillChoice> choices;
  const DrillStep(this.en, this.hi, this.choices);
}

class Drill {
  final String id, titleEn, titleHi;
  final List<DrillStep> steps;
  const Drill(this.id, this.titleEn, this.titleHi, this.steps);
}

const drills = <Drill>[
  Drill('arrest', '"Digital arrest" police call', '"डिजिटल अरेस्ट" पुलिस कॉल', [
    DrillStep(
      'A video call: a man in police uniform says a parcel in your name had drugs. "You are under digital arrest. Do not disconnect."',
      'वीडियो कॉल: पुलिस वर्दी में एक आदमी कहता है कि आपके नाम के पार्सल में ड्रग्स मिले हैं। "आप डिजिटल अरेस्ट में हैं। कॉल न काटें।"',
      [
        DrillChoice('Stay on the call and answer his questions', 'कॉल पर रहें और उसके सवालों के जवाब दें', false,
            'There is no "digital arrest" in Indian law. Staying on the call is exactly what the scammer wants.',
            'भारतीय कानून में "डिजिटल अरेस्ट" जैसा कुछ नहीं है। कॉल पर बने रहना ही ठग चाहता है।'),
        DrillChoice('Hang up and call 1930 or your local police station yourself', 'कॉल काटें और खुद 1930 या अपने थाने पर कॉल करें', true,
            'Right. Real police never arrest or question anyone over a video call.',
            'सही। असली पुलिस कभी वीडियो कॉल पर गिरफ़्तार या पूछताछ नहीं करती।'),
        DrillChoice('Ask him to show his ID card on camera', 'उससे कैमरे पर ID कार्ड दिखाने को कहें', false,
            'Fake ID cards and fake police stations are part of the act. Do not keep talking - hang up.',
            'नकली ID कार्ड और नकली थाना इस नाटक का हिस्सा हैं। बात न करते रहें - कॉल काटें।'),
      ]),
    DrillStep(
      'He says: "To clear your name, transfer your savings to an RBI safe account. It will be returned after checking."',
      'वह कहता है: "अपना नाम साफ़ करने के लिए अपनी बचत RBI के सुरक्षित खाते में डालें। जाँच के बाद लौटा दी जाएगी।"',
      [
        DrillChoice('Transfer a small amount first to test', 'पहले थोड़ी रकम भेजकर देखें', false,
            'Any amount you send is gone. No agency ever asks you to move money to "verify" it.',
            'भेजी गई कोई भी रकम चली जाती है। कोई भी एजेंसी "जाँच" के लिए पैसा नहीं मँगवाती।'),
        DrillChoice('Refuse, hang up and tell your family', 'मना करें, कॉल काटें और परिवार को बताएँ', true,
            'Right. There is no "RBI safe account". Talking to family breaks the fear the scammer builds.',
            'सही। "RBI सुरक्षित खाता" जैसा कुछ नहीं है। परिवार से बात करने से ठग का डर टूटता है।'),
        DrillChoice('Send your Aadhaar and bank details so he can check', 'जाँच के लिए आधार और बैंक विवरण भेजें', false,
            'Never send ID or bank details to a caller - they are used for more fraud.',
            'कॉल करने वाले को कभी ID या बैंक विवरण न भेजें - इनसे और ठगी होती है।'),
      ]),
  ]),
  Drill('kyc', 'Bank KYC message', 'बैंक KYC संदेश', [
    DrillStep(
      'SMS: "Dear customer, your SBI account will be blocked today. Update KYC now: http://sbi-kyc-update.top"',
      'SMS: "प्रिय ग्राहक, आपका SBI खाता आज बंद हो जाएगा। अभी KYC अपडेट करें: http://sbi-kyc-update.top"',
      [
        DrillChoice('Open the link and update KYC', 'लिंक खोलकर KYC अपडेट करें', false,
            'The link is not sbi.bank.in - it is a fake page that steals your login and OTP.',
            'यह लिंक sbi.bank.in नहीं है - यह नकली पेज है जो आपका लॉगिन और OTP चुराता है।'),
        DrillChoice('Ignore the link; check in the official bank app or at the branch', 'लिंक छोड़ें; बैंक के आधिकारिक ऐप या शाखा में जाँचें', true,
            'Right. Banks never ask for KYC through a link in an SMS.',
            'सही। बैंक कभी SMS के लिंक से KYC नहीं करवाते।'),
        DrillChoice('Call the number in the SMS', 'SMS में दिए नंबर पर कॉल करें', false,
            'Numbers in scam messages lead to the scammer. Use only the number on the bank\'s own website or card.',
            'ठगी वाले संदेश के नंबर ठग तक ले जाते हैं। केवल बैंक की वेबसाइट या कार्ड पर लिखा नंबर इस्तेमाल करें।'),
      ]),
    DrillStep(
      'A caller says he is from the bank and asks for the OTP "to stop the block".',
      'एक कॉलर खुद को बैंक का बताता है और "खाता बंद होने से रोकने" के लिए OTP माँगता है।',
      [
        DrillChoice('Read out the OTP', 'OTP पढ़कर बताएँ', false,
            'The OTP approves a payment or a login. Whoever has it can empty your account.',
            'OTP किसी भुगतान या लॉगिन को मंज़ूरी देता है। जिसके पास यह है, वह आपका खाता खाली कर सकता है।'),
        DrillChoice('Never share it; hang up', 'कभी न बताएँ; कॉल काटें', true,
            'Right. No bank employee ever asks for an OTP.', 'सही। कोई भी बैंक कर्मचारी कभी OTP नहीं माँगता।'),
        DrillChoice('Share only the first 3 digits', 'केवल पहले 3 अंक बताएँ', false,
            'Any part of the code helps the scammer. Share none of it.', 'कोड का कोई भी हिस्सा ठग की मदद करता है। कुछ भी न बताएँ।'),
      ]),
  ]),
  Drill('lottery', 'Lottery / prize', 'लॉटरी / इनाम', [
    DrillStep(
      'WhatsApp: "Congratulations! You won Rs 25 lakh in the KBC lucky draw. Pay Rs 4,999 processing fee to claim."',
      'WhatsApp: "बधाई हो! आपने KBC लकी ड्रॉ में 25 लाख रुपये जीते। इनाम पाने के लिए 4,999 रुपये प्रोसेसिंग फ़ीस भरें।"',
      [
        DrillChoice('Pay the fee - it is small compared to the prize', 'फ़ीस भर दें - इनाम के सामने यह छोटी है', false,
            'There is no prize. After the first fee come "tax", "GST" and more fees.',
            'कोई इनाम नहीं है। पहली फ़ीस के बाद "टैक्स", "GST" और फ़ीस आती रहती हैं।'),
        DrillChoice('Ignore and block the sender', 'अनदेखा करें और भेजने वाले को ब्लॉक करें', true,
            'Right. You cannot win a draw you never entered, and real prizes never need a fee.',
            'सही। जिस ड्रॉ में आपने भाग नहीं लिया उसे आप नहीं जीत सकते, और असली इनाम के लिए फ़ीस नहीं लगती।'),
        DrillChoice('Reply asking for proof', 'जवाब देकर सबूत माँगें', false,
            'Replying marks your number as active; they will send fake certificates.',
            'जवाब देने से आपका नंबर सक्रिय माना जाता है; वे नकली प्रमाणपत्र भेजेंगे।'),
      ]),
  ]),
  Drill('upi', 'UPI refund / collect request', 'UPI रिफंड / कलेक्ट रिक्वेस्ट', [
    DrillStep(
      'A buyer for your old sofa says: "I sent a request to receive Rs 8,000. Just approve it and enter your UPI PIN."',
      'आपके पुराने सोफ़े का खरीदार कहता है: "मैंने 8,000 रुपये पाने की रिक्वेस्ट भेजी है। बस उसे स्वीकार करके UPI PIN डालें।"',
      [
        DrillChoice('Approve and enter the PIN to get the money', 'पैसे पाने के लिए स्वीकार करके PIN डालें', false,
            'Entering your UPI PIN always SENDS money. You never need a PIN to receive.',
            'UPI PIN डालने से पैसा हमेशा जाता है। पैसा पाने के लिए PIN की कभी ज़रूरत नहीं होती।'),
        DrillChoice('Decline the request - receiving money needs no PIN', 'रिक्वेस्ट अस्वीकार करें - पैसा पाने के लिए PIN नहीं लगता', true,
            'Right. A "collect request" takes money from you.', 'सही। "कलेक्ट रिक्वेस्ट" आपसे पैसा लेती है।'),
        DrillChoice('Scan the QR code he sends to receive the money', 'पैसा पाने के लिए उसका भेजा QR कोड स्कैन करें', false,
            'Scanning a QR code is for paying, never for receiving.', 'QR कोड स्कैन करना भुगतान के लिए होता है, पैसा पाने के लिए कभी नहीं।'),
      ]),
  ]),
  Drill('job', 'Part-time job / task', 'पार्ट-टाइम नौकरी / टास्क', [
    DrillStep(
      'Telegram: "Earn Rs 3,000 daily by liking YouTube videos. First 3 tasks paid instantly!" You receive Rs 150.',
      'Telegram: "YouTube वीडियो लाइक करके रोज़ 3,000 रुपये कमाएँ। पहले 3 टास्क का तुरंत भुगतान!" आपको 150 रुपये मिलते हैं।',
      [
        DrillChoice('Continue - they really paid', 'जारी रखें - उन्होंने सच में भुगतान किया', false,
            'The small payment builds trust. Next comes a "prepaid task" where you must deposit money.',
            'छोटा भुगतान भरोसा बनाने के लिए है। आगे "प्रीपेड टास्क" आता है जिसमें आपको पैसा जमा करना पड़ता है।'),
        DrillChoice('Stop and leave the group', 'रुकें और ग्रुप छोड़ दें', true,
            'Right. Real jobs never ask you to pay to earn.', 'सही। असली नौकरी में कमाने के लिए पैसा नहीं भरना पड़ता।'),
        DrillChoice('Invite friends to earn too', 'दोस्तों को भी कमाने के लिए बुलाएँ', false,
            'You would pull friends into the same scam.', 'इससे आपके दोस्त भी उसी धोखे में फँसेंगे।'),
      ]),
    DrillStep(
      '"VIP task: deposit Rs 10,000 to unlock Rs 14,000 commission. Your earnings are frozen until you complete it."',
      '"VIP टास्क: 14,000 रुपये कमीशन पाने के लिए 10,000 रुपये जमा करें। टास्क पूरा होने तक आपकी कमाई रोकी गई है।"',
      [
        DrillChoice('Deposit to unfreeze your earnings', 'कमाई छुड़ाने के लिए जमा करें', false,
            'The "earnings" are only numbers on their website. Every deposit is lost.',
            '"कमाई" केवल उनकी वेबसाइट पर लिखे अंक हैं। हर जमा रकम चली जाती है।'),
        DrillChoice('Stop paying and report at cybercrime.gov.in or 1930', 'भुगतान बंद करें और cybercrime.gov.in या 1930 पर शिकायत करें', true,
            'Right. Report quickly - the sooner, the better the chance to freeze the money.',
            'सही। जल्दी शिकायत करें - जितनी जल्दी, पैसा रोकने की उतनी अच्छी संभावना।'),
        DrillChoice('Borrow money to finish the last task', 'आख़िरी टास्क पूरा करने के लिए उधार लें', false,
            'There is never a "last" task. Stop now.', 'कोई "आख़िरी" टास्क कभी नहीं होता। अभी रुकें।'),
      ]),
  ]),
];

/// Score in percent for [answers] (index of the chosen option per step).
int drillScore(Drill d, List<int> answers) {
  var ok = 0;
  for (var i = 0; i < d.steps.length && i < answers.length; i++) {
    if (d.steps[i].choices[answers[i]].safe) ok++;
  }
  return (ok * 100 / d.steps.length).round();
}
