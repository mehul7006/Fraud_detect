// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Hindi (`hi`).
class AppLocalizationsHi extends AppLocalizations {
  AppLocalizationsHi([String locale = 'hi']) : super(locale);

  @override
  String get appTitle => 'FraudShield';

  @override
  String get threatHistory => 'खतरों का इतिहास';

  @override
  String get linkSettings => 'लिंक सेटिंग';

  @override
  String get logOut => 'लॉग आउट';

  @override
  String get allow => 'अनुमति दें';

  @override
  String get grantAccess => 'एक्सेस दें';

  @override
  String get permNotifTitle => 'सूचनाओं की अनुमति दें';

  @override
  String get permNotifBody =>
      'Android 13 और नए में, सूचनाओं की अनुमति के बिना कोई भी चेतावनी नहीं दिखती। इसके बिना FraudShield आपको सावधान नहीं कर सकता।';

  @override
  String get permLinkTitle => 'लिंक और विज्ञापन निगरानी चालू करें';

  @override
  String get permLinkBody =>
      'Notification Access दें ताकि FraudShield WhatsApp, Telegram और SMS में आए लिंक जाँच सके और विज्ञापन स्पैम गिन सके। संदेश का टेक्स्ट कभी अपलोड नहीं होता।';

  @override
  String get permFilesTitle => 'फ़ाइल सुरक्षा चालू करें';

  @override
  String get permFilesBody =>
      'स्टोरेज की अनुमति दें (Android 11 और नए में \"All files access\") ताकि FraudShield डाउनलोड की गई APK/PDF फ़ाइलें जाँच सके। केवल फ़ाइल का हैश और नाम भेजा जाता है।';

  @override
  String get permPhoneTitle => 'फ़ोन की अनुमति';

  @override
  String get permPhoneBody =>
      'आपके ऑपरेटर से कॉल फ़ॉरवर्डिंग जाँचने के लिए ज़रूरी है।';

  @override
  String get permUsageTitle => 'अपने-आप खुलने वाले ऐप पहचानें (वैकल्पिक)';

  @override
  String get permUsageBody =>
      'Usage Access दें ताकि FraudShield उन ऐप्स को पहचान सके जो आपके छुए बिना खुल जाते हैं। हर ऐप की केवल एक गिनती रखी जाती है; उस गिनती के अलावा कुछ भी आपके फ़ोन से बाहर नहीं जाता।';

  @override
  String get offlineTitle => 'FraudShield सर्वर से संपर्क नहीं हो पा रहा';

  @override
  String get offlineBody =>
      'लिंक और फ़ाइल की जाँच सहेज ली जाती है और अपने-आप दोबारा की जाएगी। दोबारा कोशिश के लिए किसी टाइल पर टैप करें।';

  @override
  String get couldntCheck => 'अभी जाँच नहीं हो सकी - दोबारा के लिए टैप करें';

  @override
  String get checking => 'जाँच हो रही है...';

  @override
  String get callForwarding => 'कॉल फ़ॉरवर्डिंग';

  @override
  String get cloneRemote => 'क्लोन / रिमोट एक्सेस';

  @override
  String get riskyApps => 'जोखिम भरे / विज्ञापन-स्पैम ऐप';

  @override
  String get linkProtection => 'लिंक सुरक्षा';

  @override
  String get fileProtection => 'फ़ाइल सुरक्षा';

  @override
  String get statusOn => 'चालू';

  @override
  String get statusOff => 'बंद';

  @override
  String get statusActive => 'चालू है - आपकी कॉल आगे भेजी जा रही हैं!';

  @override
  String get statusOk => 'ठीक है';

  @override
  String statusRisk(String score) {
    return 'जोखिम $score';
  }

  @override
  String get fwdUnavailable =>
      'उपलब्ध नहीं - ऐप से आपके ऑपरेटर से जाँच नहीं हो सकती';

  @override
  String get fwdUnknown => 'अज्ञात - ऑपरेटर का जवाब समझ नहीं आया';

  @override
  String fwdCarrierSaid(String text) {
    return 'अज्ञात - ऑपरेटर का जवाब समझ नहीं आया\nऑपरेटर ने कहा: \"$text\"';
  }

  @override
  String get checkManually => 'खुद जाँचें';

  @override
  String get carrierReplies => 'ऑपरेटर के जवाब';

  @override
  String carrierRepliesBody(String json) {
    return 'फ़ोन नंबर छिपा दिए गए हैं। कुछ भी भेजा नहीं गया।\nइसे कॉपी करें और अपने ऑपरेटर का नाम और फ़ॉरवर्डिंग चालू थी या नहीं, यह जोड़ें।\n\n$json';
  }

  @override
  String get copy => 'कॉपी करें';

  @override
  String get close => 'बंद करें';

  @override
  String get accountSecurity => 'खाते की सुरक्षा';

  @override
  String get secureWhatsApp => 'WhatsApp सुरक्षित करें';

  @override
  String get secureWhatsAppSub => 'बाकी सभी डिवाइस से लॉग आउट करें';

  @override
  String get secureTelegram => 'Telegram सुरक्षित करें';

  @override
  String get secureTelegramSub => 'बाकी सभी सेशन बंद करें';

  @override
  String get smsForwarding => 'SMS फ़ॉरवर्डिंग';

  @override
  String get smsForwardingSub => 'ऐप इसे पढ़ नहीं सकते - जाँचने का तरीका देखें';

  @override
  String get smsReaders => 'मेरे SMS पढ़ सकने वाले ऐप';

  @override
  String get smsReadersSub => 'OTP चुराने वाले ऐप खोजें';

  @override
  String get secureAccount => 'खाता सुरक्षित करें';

  @override
  String get iUnderstand => 'मैं समझ गया/गई';

  @override
  String get ignore => 'अनदेखा करें';

  @override
  String get email => 'ईमेल';

  @override
  String get passwordHint => 'पासवर्ड (कम से कम 8)';

  @override
  String get login => 'लॉग इन';

  @override
  String get createAccount => 'खाता बनाएँ';

  @override
  String get haveAccount => 'पहले से खाता है? लॉग इन करें';

  @override
  String get newHere => 'नए हैं? रजिस्टर करें';

  @override
  String get signingIn => 'लॉग इन हो रहा है';

  @override
  String get wrongPassword => 'ईमेल या पासवर्ड गलत है';

  @override
  String get emailTaken => 'यह ईमेल पहले से रजिस्टर है';

  @override
  String get errOffline =>
      'इंटरनेट कनेक्शन नहीं है। FraudShield निगरानी करता रहेगा; सर्वर वाली जाँच अपने-आप दोबारा होगी।';

  @override
  String get errTooMany =>
      'बहुत ज़्यादा कोशिशें। कृपया एक मिनट रुककर दोबारा कोशिश करें।';

  @override
  String get errSession =>
      'आपका सेशन समाप्त हो गया है। कृपया दोबारा लॉग इन करें।';

  @override
  String get errServer =>
      'FraudShield सर्वर में समस्या है। कृपया कुछ मिनट बाद कोशिश करें।';

  @override
  String get errGeneric => 'कुछ गड़बड़ हो गई। कृपया दोबारा कोशिश करें।';

  @override
  String get tryAgain => 'दोबारा कोशिश करें';

  @override
  String get noThreats => 'कोई खतरा नहीं मिला। आप सुरक्षित हैं।';

  @override
  String get resolve => 'हल हो गया';

  @override
  String get language => 'भाषा';

  @override
  String get languageSystem => 'फ़ोन की भाषा';

  @override
  String get languageEnglish => 'English';

  @override
  String get languageHindi => 'हिन्दी (Hindi)';

  @override
  String wizTitle(String app) {
    return '$app सुरक्षित करें';
  }

  @override
  String get wizWarning =>
      'कोई भी ऐप आपके लिए दूसरे डिवाइस से लॉग आउट नहीं कर सकता - यह केवल आप ही उस ऐप के अंदर कर सकते हैं। अभी ये कदम उठाएँ।';

  @override
  String wizOpenNow(String app) {
    return '$app अभी खोलें';
  }

  @override
  String get wizNotInstalled => 'ऐप इंस्टॉल नहीं है';

  @override
  String get waStep1 =>
      'WhatsApp खोलें > 3 बिंदुओं पर टैप करें > Linked devices (लिंक किए गए डिवाइस)।';

  @override
  String get waStep2 =>
      'हर उस डिवाइस पर टैप करें जिसे आप नहीं पहचानते > Log out। (या: सभी डिवाइस से लॉग आउट करें।)';

  @override
  String get waStep3 =>
      'Settings > Account > Two-step verification > चालू करें और PIN + रिकवरी ईमेल सेट करें।';

  @override
  String get waStep4 =>
      'Settings > Account > Passkeys / Email address: ईमेल जोड़ें ताकि केवल आप ही खाता वापस पा सकें।';

  @override
  String get waStep5 =>
      'SMS से आया 6 अंकों का कोड कभी किसी को न बताएँ। WhatsApp सपोर्ट यह कभी नहीं माँगता।';

  @override
  String get waStep6 =>
      'अगर आप खाते से बाहर हो गए हैं: अनइंस्टॉल करें > फिर से इंस्टॉल करें > अपना नंबर दोबारा रजिस्टर करें। इससे हमलावर लॉग आउट हो जाता है।';

  @override
  String get tgStep1 => 'Telegram खोलें > Settings > Devices (डिवाइस)।';

  @override
  String get tgStep2 =>
      '\"Terminate all other sessions\" (बाकी सभी सेशन बंद करें) पर टैप करें।';

  @override
  String get tgStep3 =>
      'Settings > Privacy and Security > Two-Step Verification > पासवर्ड + रिकवरी ईमेल सेट करें।';

  @override
  String get tgStep4 =>
      'Settings > Privacy and Security > Active Websites: अनजान वेबसाइटों को डिस्कनेक्ट करें।';

  @override
  String get tgStep5 =>
      'Settings > Privacy and Security > Passcode Lock: चालू करें।';

  @override
  String get tgStep6 => 'Telegram के लॉग इन कोड कभी किसी को न बताएँ।';

  @override
  String get smsFwdTitle => 'SMS फ़ॉरवर्डिंग जाँचें';

  @override
  String get smsFwdIntro =>
      'कोई भी ऐप यह नहीं देख सकता कि आपका ऑपरेटर आपके SMS आगे भेज रहा है या नहीं। ये तीन जाँचें करें।';

  @override
  String get smsFwd1Title => 'आपके SMS पढ़ सकने वाले ऐप हटाएँ';

  @override
  String get smsFwd1Body =>
      'धोखेबाज़ \"SMS forwarder\" ऐप आपके OTP दूसरे फ़ोन पर कॉपी कर देते हैं।';

  @override
  String get smsFwd1Button => 'मेरे SMS पढ़ने वाले ऐप दिखाएँ';

  @override
  String get smsFwd2Title => 'अपने ऑपरेटर से पूछें';

  @override
  String get smsFwd2Body =>
      'अपने ही नंबर से कस्टमर केयर को कॉल करें और पूछें: \"क्या मेरे नंबर पर SMS फ़ॉरवर्डिंग या SMS रीडायरेक्ट चालू है?\" उनसे इसे बंद करने को कहें और यह भी पूछें कि क्या आपकी SIM की डुप्लिकेट बनी या बदली गई।';

  @override
  String get smsFwd3Title => 'SIM स्वैप से सावधान रहें';

  @override
  String get smsFwd3Body =>
      'अगर आपका फ़ोन अचानक बिना वजह \"No service\" दिखाए, तो तुरंत दूसरे फ़ोन से ऑपरेटर को कॉल करें। बदली गई SIM पर आपके सारे SMS और कॉल पहुँचते हैं।';

  @override
  String get smsAuditTitle => 'आपके SMS पढ़ सकने वाले ऐप';

  @override
  String get smsAuditError => 'इंस्टॉल किए गए ऐप पढ़े नहीं जा सके';

  @override
  String get smsAuditIntro =>
      'अगर आप नीचे किसी ऐप को नहीं पहचानते, तो उसे अनइंस्टॉल करें। SMS पढ़ सकने वाला अनजान ऐप आपके OTP आगे भेज सकता है।';

  @override
  String smsAuditUser(String count) {
    return 'आपके द्वारा इंस्टॉल किए गए ($count)';
  }

  @override
  String get smsAuditNoneUser =>
      'आपके द्वारा इंस्टॉल किया कोई भी ऐप आपके SMS नहीं पढ़ सकता';

  @override
  String smsAuditSystem(String count) {
    return 'सिस्टम ऐप - आमतौर पर सुरक्षित ($count)';
  }

  @override
  String get readsSms => 'SMS पढ़ता है';

  @override
  String get readsNotifications => 'सूचनाएँ पढ़ता है';

  @override
  String get uninstall => 'अनइंस्टॉल करें';

  @override
  String get riskyTitle => 'जोखिम भरे ऐप';

  @override
  String get riskyFailedTitle => 'आपके ऐप जाँचे नहीं जा सके';

  @override
  String get riskyFailedBody =>
      'इंटरनेट कनेक्शन जाँचें और दोबारा कोशिश के लिए नीचे खींचें।';

  @override
  String get riskyCheckingTitle => 'आपके ऐप जाँचे जा रहे हैं...';

  @override
  String get riskyCheckingBody => 'इसमें कुछ सेकंड लगते हैं।';

  @override
  String get riskyNoneTitle => 'कोई जोखिम भरा ऐप नहीं मिला';

  @override
  String get riskyNoneBody => 'दोबारा जाँचने के लिए नीचे खींचें।';

  @override
  String get riskyShareTitle =>
      'जोखिम भरे ऐप की पहचान बेहतर बनाने में मदद करें';

  @override
  String get riskyShareBody =>
      'जब आप किसी चिह्नित ऐप को अनइंस्टॉल या अनदेखा करें, तो उसके जोखिम संकेत (सूचनाओं की गिनती, उसकी अनुमतियाँ) और आपका फ़ैसला भेजें। कोई संदेश, कोई संपर्क, आपके दूसरे ऐप की कोई सूची नहीं।';

  @override
  String get newBadge => 'नया';

  @override
  String get trustedSites => 'भरोसेमंद वेबसाइटें';

  @override
  String get trustedSitesBody =>
      'इन साइटों (और इनके सब-डोमेन) के लिंक कभी जाँचे या चिह्नित नहीं किए जाते।';

  @override
  String get domain => 'डोमेन';

  @override
  String get domainInvalid => 'example.com जैसा डोमेन लिखें';

  @override
  String get trustDomain => 'इस डोमेन पर भरोसा करें';

  @override
  String removeDomain(String domain) {
    return '$domain हटाएँ';
  }

  @override
  String get appsToWatch => 'निगरानी वाले ऐप';

  @override
  String get appsToWatchBody =>
      'किसी ऐप को बंद करने पर उसकी सूचनाओं में आए लिंक अनदेखे कर दिए जाते हैं।';

  @override
  String get noAppsFound => 'कोई ऐप नहीं मिला।';

  @override
  String get alertNoDanger => 'कोई ज्ञात खतरा नहीं मिला';

  @override
  String alertNoDangerBody(String url) {
    return '$url\n\nफिर भी, अनचाहे लिंक से सावधान रहें।';
  }

  @override
  String get notifDangerous => 'खतरनाक लिंक!';

  @override
  String get alertSuspiciousLink => 'संदिग्ध लिंक';

  @override
  String get alertDangerous => 'खतरनाक लिंक - इसे न खोलें';

  @override
  String get alertCheckFailed => 'लिंक की जाँच नहीं हो सकी';

  @override
  String alertCheckFailedBody(String url) {
    return '$url\n\nFraudShield से संपर्क नहीं हुआ। थोड़ी देर में दोबारा कोशिश करें।';
  }

  @override
  String get alertNoLink => 'कोई लिंक नहीं मिला';

  @override
  String get alertNoLinkBody =>
      'आपके शेयर किए गए टेक्स्ट में कोई वेब लिंक नहीं है।';

  @override
  String get notifSuspiciousFile => 'संदिग्ध फ़ाइल मिली';

  @override
  String get alertMaliciousFile => 'खतरनाक फ़ाइल';

  @override
  String get alertSuspiciousFile => 'संदिग्ध फ़ाइल';

  @override
  String get actionDeleteFile => 'फ़ाइल हटाएँ';

  @override
  String alertDeleteQ(String name) {
    return '\"$name\" हटाएँ?';
  }

  @override
  String get alertDeleteQBody =>
      'इससे फ़ाइल आपके फ़ोन से हट जाएगी। इसे वापस नहीं लाया जा सकता।';

  @override
  String get actionYesDelete => 'हाँ, हटा दें';

  @override
  String get alertDeleted => 'फ़ाइल हटा दी गई';

  @override
  String alertDeletedBody(String name) {
    return '\"$name\" हटा दी गई।';
  }

  @override
  String get alertDeleteFailed => 'फ़ाइल हटाई नहीं जा सकी';

  @override
  String get alertDeleteFailedBody =>
      'FraudShield केवल Downloads, WhatsApp और Telegram फ़ोल्डर की फ़ाइलें ही हटाता है।';

  @override
  String alertLoginCode(String app) {
    return '$app का लॉग इन कोड आया';
  }

  @override
  String alertLoginCodeBody(String app) {
    return 'अभी $app का लॉग इन कोड आया है। अगर आपने अभी लॉग इन नहीं किया, तो कोई आपका खाता हथियाने की कोशिश कर रहा है। यह कोड कभी किसी को न बताएँ।';
  }

  @override
  String get actionNotMe => 'मैंने यह नहीं माँगा';

  @override
  String notifNewDevice(String app) {
    return '$app: नए डिवाइस पर गतिविधि';
  }

  @override
  String get notifNewDeviceBody =>
      'अपना खाता सुरक्षित करने के लिए FraudShield खोलें';

  @override
  String alertNewDevice(String app) {
    return '$app किसी दूसरे डिवाइस से इस्तेमाल हो रहा हो सकता है';
  }

  @override
  String get alertNewDeviceBody =>
      'नए या लिंक किए गए डिवाइस की सूचना आई है। अभी बाकी सभी डिवाइस से लॉग आउट करें।';

  @override
  String get alertClone => 'संभावित क्लोन / रिमोट एक्सेस';

  @override
  String get notifFwdOn => 'कॉल फ़ॉरवर्डिंग चालू है';

  @override
  String notifFwdOnBody(String condition) {
    return 'फ़ॉरवर्डिंग ($condition) चालू है। धोखेबाज़ इससे OTP वाली कॉल चुराते हैं।';
  }

  @override
  String alertFwdOnBody(String condition, String carrier) {
    return 'फ़ॉरवर्डिंग ($condition) चालू है।\n\nऑपरेटर का जवाब:\n$carrier\n\nधोखेबाज़ इससे OTP वाली कॉल चुराते हैं।';
  }

  @override
  String get actionFwdOff => 'फ़ॉरवर्डिंग बंद करें';

  @override
  String get fwdIconNote =>
      '(Android भी कॉल फ़ॉरवर्डिंग का निशान दिखा रहा है।)';

  @override
  String get fwdAll => 'सभी कॉल';

  @override
  String get fwdBusy => 'व्यस्त होने पर';

  @override
  String get fwdNoAnswer => 'जवाब न देने पर';

  @override
  String get fwdUnreachable => 'पहुँच से बाहर होने पर';

  @override
  String alertRiskyApp(String label) {
    return 'जोखिम भरा ऐप: $label';
  }

  @override
  String alertNewRiskyApp(String label) {
    return 'नया जोखिम भरा ऐप: $label';
  }

  @override
  String get actionIgnoreApp => 'इस ऐप को अनदेखा करें';

  @override
  String appsRisky(String count) {
    return '$count जोखिम भरे';
  }

  @override
  String get consentTitle => 'शुरू करने से पहले';

  @override
  String get consentIntro =>
      'FraudShield आपके फ़ोन को धोखाधड़ी से बचाता है। आपके डेटा के साथ यह क्या करता है, यह रहा पूरा ब्यौरा।';

  @override
  String get consentChecks => 'हमारे सर्वर से क्या जाँचा जाता है';

  @override
  String get consentChecksBody =>
      'आपकी सूचनाओं में आए वेब लिंक, नई डाउनलोड हुई फ़ाइलों का फ़िंगरप्रिंट (सामग्री नहीं), जोखिम भरे दिखने वाले ऐप, और क्लोनिंग व SIM स्वैप पहचानने के लिए आपके फ़ोन और SIM की गुप्त (स्क्रैम्बल) ID।';

  @override
  String get consentNever => 'क्या कभी आपके फ़ोन से बाहर नहीं जाता';

  @override
  String get consentNeverBody =>
      'आपके संदेशों और सूचनाओं का टेक्स्ट, आपके संपर्क, फ़ोटो, फ़ाइलों की सामग्री, कॉल लॉग, लोकेशन और आपके सभी ऐप की सूची।';

  @override
  String get consentShared => 'और कौन डेटा देखता है';

  @override
  String get consentSharedBody =>
      'लिंक Google Safe Browsing से और फ़ाइल फ़िंगरप्रिंट VirusTotal से जाँचे जाते हैं। कोई विज्ञापन नहीं, कोई एनालिटिक्स नहीं, डेटा की कोई बिक्री नहीं।';

  @override
  String get consentDelete => 'नियंत्रण आपके हाथ में';

  @override
  String get consentDeleteBody =>
      'हर अनुमति अलग से माँगी जाती है और बंद की जा सकती है। Settings > खाता हटाएँ से आपका सारा डेटा एक साथ हट जाता है।';

  @override
  String get privacyPolicy => 'गोपनीयता नीति';

  @override
  String get readPrivacyPolicy => 'पूरी गोपनीयता नीति पढ़ें';

  @override
  String get agreeContinue => 'सहमत हूँ, आगे बढ़ें';

  @override
  String get deleteAccount => 'मेरा खाता हटाएँ';

  @override
  String get deleteAccountBody =>
      'आपका खाता और उससे जुड़ा सारा डेटा (खतरों का इतिहास, डिवाइस, ऐप की जाँच) हमारे सर्वर से हट जाएगा। इसे वापस नहीं लाया जा सकता।';

  @override
  String get deleteAccountConfirm => 'सब कुछ हटाएँ';

  @override
  String get cancel => 'रद्द करें';

  @override
  String get accountSection => 'खाता और गोपनीयता';

  @override
  String get contactSection => 'आपातकालीन संपर्क (वैकल्पिक)';

  @override
  String get contactBody =>
      'कोई भरोसेमंद व्यक्ति। गंभीर चेतावनी पर आप एक टैप में उन्हें बता सकते हैं (आपका SMS ऐप संदेश तैयार करके खुलता है)। केवल इसी फ़ोन पर सहेजा जाता है।';

  @override
  String get contactName => 'नाम';

  @override
  String get contactPhone => 'मोबाइल नंबर';

  @override
  String get contactSave => 'संपर्क सहेजें';

  @override
  String get contactRemove => 'संपर्क हटाएँ';

  @override
  String get contactInvalid => 'नाम और 10 अंकों का मोबाइल नंबर लिखें';

  @override
  String contactSaved(String name) {
    return 'आपातकालीन संपर्क: $name';
  }

  @override
  String tellContact(String name) {
    return '$name को बताएँ';
  }

  @override
  String contactMessage(String title) {
    return 'मेरे फ़ोन पर FraudShield चेतावनी: $title। अगर अभी कोई (मेरे नाम से भी) आपसे OTP, पैसे या कोई कोड माँगे, तो पहले मुझे मेरे नंबर पर कॉल करें।';
  }

  @override
  String get permFsiTitle => 'फ़ुल-स्क्रीन सुरक्षा चेतावनी की अनुमति दें';

  @override
  String get permFsiBody =>
      'Android 14 और नए में केवल अलार्म और कॉलिंग ऐप पूरी स्क्रीन पर आ सकते हैं। इसकी अनुमति दें ताकि गंभीर चेतावनी (कॉल फ़ॉरवर्डिंग चालू, खाता हथियाना) लॉक स्क्रीन पर भी दिखे। इसके बिना भी आपको सूचना मिलती रहेगी।';

  @override
  String fakeSiteTitle(String brand) {
    return 'यह $brand जैसा दिखता है, पर $brand नहीं है';
  }

  @override
  String get fakeSiteThisLink => 'यह लिंक';

  @override
  String fakeSiteReal(String brand) {
    return 'असली $brand';
  }

  @override
  String get fakeSiteWhy => 'हमें ऐसा क्यों लगता है';

  @override
  String get fakeSiteAdvice =>
      'इस साइट पर अपना पासवर्ड, OTP, PIN या कार्ड की जानकारी न डालें। अगर डाल दी है, तो अभी अपने बैंक और साइबर क्राइम हेल्पलाइन 1930 पर कॉल करें।';

  @override
  String fakeSiteGoReal(String brand) {
    return 'असली $brand साइट पर जाएँ';
  }

  @override
  String get fakeSiteReport => 'रिपोर्ट करें';

  @override
  String get fakeSiteReportCyber => 'साइबर क्राइम को रिपोर्ट करें (1930)';

  @override
  String get fakeSiteWrong => 'यह चेतावनी गलत है';

  @override
  String get fakeSiteThanks => 'धन्यवाद - हम इसकी जाँच करेंगे।';

  @override
  String fakeSiteDiffLabel(String seen, String real) {
    return 'यह लिंक $seen असली पते $real से अलग है';
  }

  @override
  String notifFakeSite(String brand) {
    return 'नकली $brand साइट!';
  }

  @override
  String get whyRisky => 'यह खतरनाक क्यों है?';

  @override
  String get vpnTitle => 'नकली वेबसाइट से सुरक्षा';

  @override
  String get vpnTileOn => 'चालू - हर ऐप जो साइट खोलता है उसकी जाँच';

  @override
  String get vpnTileOff => 'बंद - हर ऐप में नकली साइट रोकने के लिए टैप करें';

  @override
  String get vpnConsentTitle => 'हर ऐप में नकली वेबसाइट रोकें';

  @override
  String get vpnConsentIntro =>
      'FraudShield आपके फ़ोन पर ही एक छोटा VPN चालू करता है। जब कोई भी ऐप या ब्राउज़र किसी वेबसाइट का नाम खोजता है, FraudShield पहले उस नाम की जाँच करता है और जानी-मानी नकली साइटों को रोक देता है।';

  @override
  String get vpnSeesTitle => 'FraudShield क्या देखता है';

  @override
  String get vpnSees =>
      'केवल वेबसाइट के नाम (जैसे flipkart.com) और कौन-सा ऐप उन्हें खोल रहा है। आपका इंटरनेट डेटा FraudShield से होकर नहीं जाता।';

  @override
  String get vpnNotSeesTitle => 'यह कभी क्या नहीं देखता';

  @override
  String get vpnNotSees =>
      'पेज, संदेश, पासवर्ड, भुगतान या पूरे लिंक। हमारे सर्वर पर कुछ भी रिकॉर्ड नहीं होता।';

  @override
  String get vpnSentTitle => 'आपके फ़ोन से क्या बाहर जाता है';

  @override
  String get vpnSent =>
      'केवल किसी अनजान वेबसाइट का नाम, यह जाँचने के लिए कि वह नकली है या नहीं - आपसे कभी जोड़ा नहीं जाता। केवल-फ़ोन मोड में कुछ नहीं भेजा जाता।';

  @override
  String get vpnConsentNote =>
      'Android आपसे VPN कनेक्शन की अनुमति माँगेगा। सुरक्षा चालू रहने पर ऊपर एक चाबी का निशान दिखता है।';

  @override
  String get vpnTurnOn => 'सुरक्षा चालू करें';

  @override
  String get vpnTurnOff => 'बंद करें';

  @override
  String get vpnNotNow => 'अभी नहीं';

  @override
  String get vpnStateOn => 'सुरक्षा चालू है';

  @override
  String get vpnStateOff => 'सुरक्षा बंद है';

  @override
  String get vpnStateOtherVpn => 'कोई दूसरा VPN जुड़ा है';

  @override
  String get vpnStateOtherVpnBody =>
      'Android एक समय में केवल एक VPN चलाने देता है। Android सेटिंग में दूसरा VPN बंद करें, फिर FraudShield दोबारा चालू करें।';

  @override
  String get vpnStatePrivateDns => 'प्राइवेट DNS चालू है';

  @override
  String vpnStatePrivateDnsBody(String name) {
    return 'आपका फ़ोन वेबसाइट के नाम सीधे $name को भेजता है, इसलिए FraudShield उनकी जाँच नहीं कर सकता। प्राइवेट DNS को \"Automatic\" या \"Off\" करें।';
  }

  @override
  String get vpnStateRevoked => 'सुरक्षा बंद कर दी गई';

  @override
  String get vpnStateRevokedBody =>
      'इसे Android सेटिंग में बंद किया गया या किसी दूसरे VPN ने इसकी जगह ले ली। सुरक्षित रहने के लिए इसे फिर चालू करें।';

  @override
  String get vpnStateNoConsent => 'सुरक्षा चालू करने के लिए VPN अनुमति दें।';

  @override
  String get vpnOpenVpnSettings => 'VPN सेटिंग खोलें';

  @override
  String get vpnOpenDnsSettings => 'नेटवर्क सेटिंग खोलें';

  @override
  String vpnStats(int n) {
    return '$n साइट नाम जाँचे गए';
  }

  @override
  String vpnBlockedCount(int n) {
    return '$n नकली साइटें रोकी गईं';
  }

  @override
  String vpnListSize(int n) {
    return 'आपके फ़ोन पर $n जानी-मानी नकली साइटें';
  }

  @override
  String get vpnRecent => 'हाल में रोकी गई';

  @override
  String get vpnNoneBlocked => 'अभी तक कुछ नहीं रोका गया।';

  @override
  String get vpnLocalOnly => 'केवल-फ़ोन मोड';

  @override
  String get vpnLocalOnlyBody =>
      'केवल फ़ोन की सूची इस्तेमाल करें; सर्वर से किसी वेबसाइट के बारे में न पूछें।';

  @override
  String get vpnBlockDoh => 'छिपी DNS सेवाएँ रोकें';

  @override
  String get vpnBlockDohBody =>
      'कुछ ब्राउज़र अपनी अलग एन्क्रिप्टेड DNS से साइट खोजते हैं और जाँच से बच जाते हैं। इसे रोकने से वे जाँची गई DNS इस्तेमाल करते हैं।';

  @override
  String get vpnUpstream => 'DNS प्रदाता';

  @override
  String get vpnUpstreamSystem => 'आपके नेटवर्क का (डिफ़ॉल्ट)';

  @override
  String get vpnTopApps => 'कौन-सा ऐप किन साइटों से जुड़ता है';

  @override
  String get vpnTopAppsBody =>
      'पिछले 30 दिन। डेटा उपयोग Android के अपने काउंटर से है (उपयोग एक्सेस चाहिए)।';

  @override
  String vpnSitesCount(int n) {
    return '$n साइटें';
  }

  @override
  String get vpnNoTraffic =>
      'अभी कोई डेटा नहीं - सुरक्षा चालू करें और कुछ देर फ़ोन इस्तेमाल करें।';

  @override
  String get vpnKeepOnTitle => 'रीस्टार्ट के बाद भी सुरक्षा चालू रखें';

  @override
  String get vpnKeepOnBody =>
      'आपका फ़ोन ऐप्स को अपने-आप शुरू होने से रोकता है। रीस्टार्ट या अपडेट के बाद सुरक्षा वापस आए, इसके लिए: 1) VPN सेटिंग में FraudShield पर टैप करके \"Always-on VPN\" चालू करें; 2) FraudShield के लिए Autostart की अनुमति दें।';

  @override
  String get vpnAlwaysOn => 'Always-on VPN';

  @override
  String get vpnAutostart => 'Autostart की अनुमति दें';

  @override
  String get appDetailAccess => 'यह क्या एक्सेस कर सकता है';

  @override
  String get appDetailNothing => 'कोई संवेदनशील एक्सेस नहीं दिया गया।';

  @override
  String get appDetailTrackers => 'अंदर ट्रैकिंग कोड';

  @override
  String get appDetailNoTrackers => 'कोई जाना-पहचाना ट्रैकिंग कोड नहीं मिला।';

  @override
  String get appDetailTalksTo => 'यह किनसे जुड़ा (पिछले 7 दिन)';

  @override
  String get appDetailTalksToBody =>
      'इस फ़ोन की नकली-वेबसाइट सुरक्षा से। साइट के नाम आपके फ़ोन से बाहर नहीं जाते।';

  @override
  String get appDetailNoNetwork =>
      'कोई डेटा नहीं - इसे देखने के लिए नकली-वेबसाइट सुरक्षा चालू करें।';

  @override
  String appDetailAdServers(int n) {
    return '$n विज्ञापन / ट्रैकिंग सर्वर';
  }

  @override
  String get appDetailMalware => 'जाने-माने मैलवेयर सर्वर से जुड़ता है!';

  @override
  String get appBlockInternet => 'इंटरनेट बंद करें';

  @override
  String get appUnblockInternet => 'इंटरनेट फिर चालू करें';

  @override
  String get appBlockedNote =>
      'इस ऐप का इंटरनेट बंद है (जब तक नकली-वेबसाइट सुरक्षा चालू है)। तय सर्वर पते वाले ऐप फिर भी जुड़ सकते हैं।';

  @override
  String get appRevoke => 'अनुमतियाँ हटाएँ';

  @override
  String appFakeTitle(String name) {
    return 'यह असली $name ऐप नहीं है';
  }

  @override
  String get appIgnored =>
      'आपने इस ऐप को अनदेखा चुना। FraudShield याद नहीं दिलाएगा।';

  @override
  String get msgTitle => 'संदेश जाँचें';

  @override
  String get msgIntro =>
      'कोई SMS या WhatsApp संदेश यहाँ चिपकाएँ। FraudShield आपके फ़ोन पर ही जाँच करता है - संदेश फ़ोन से बाहर नहीं जाता।';

  @override
  String get msgHint => 'संदेश यहाँ चिपकाएँ';

  @override
  String get msgCheck => 'जाँचें';

  @override
  String get msgSafe => 'धोखाधड़ी के संकेत नहीं मिले';

  @override
  String msgScam(String name) {
    return 'यह $name लगता है - जवाब, क्लिक या भुगतान न करें';
  }

  @override
  String msgSuspicious(String name) {
    return 'सावधान: यह $name हो सकता है';
  }

  @override
  String get msgCatKyc => 'नकली बैंक KYC / खाता-बंद धोखा';

  @override
  String get msgCatPrize => 'नकली इनाम / लॉटरी धोखा';

  @override
  String get msgCatParcel => 'नकली पार्सल / कूरियर धोखा';

  @override
  String get msgCatPower => 'नकली बिजली-कटौती धोखा';

  @override
  String get msgCatJob => 'नकली नौकरी / टास्क धोखा';

  @override
  String get msgCatLoan => 'नकली लोन धोखा';

  @override
  String get msgCatArrest => '\"डिजिटल अरेस्ट\" धोखा';

  @override
  String get msgCatOtp => 'OTP चोरी धोखा';

  @override
  String get msgCatUpi => 'UPI रिफंड / कलेक्ट धोखा';

  @override
  String get msgCatInvest => 'नकली निवेश धोखा';

  @override
  String get msgCatGeneric => 'धोखा';

  @override
  String msgLinkFake(String name) {
    return 'नकली $name वेबसाइट - न खोलें';
  }

  @override
  String get msgLinkBad => 'खतरनाक लिंक - न खोलें';

  @override
  String get msgLinkOk => 'इस लिंक में खतरा नहीं मिला';

  @override
  String get msgLinkUnknown => 'इस लिंक की जाँच नहीं हो सकी';

  @override
  String get msgAdvice =>
      'OTP, PIN या पासवर्ड कभी साझा न करें। अगर भुगतान या जानकारी दे चुके हैं, तो अभी बैंक और साइबर क्राइम हेल्पलाइन 1930 पर कॉल करें।';

  @override
  String get msgCloudButton => 'क्लाउड AI से जाँचें';

  @override
  String get msgCloudTitle => 'गहरी जाँच के लिए संदेश भेजें?';

  @override
  String get msgCloudFirst =>
      'गहरी जाँच के लिए संदेश सुरक्षित रूप से (HTTPS) FraudShield सर्वर को भेजा जाता है। इसका उपयोग केवल इसी जाँच के लिए होता है: यह न सेव होता है, न लॉग होता है, न आपसे जोड़ा जाता है। इसके लिंक भी जाँचे जाते हैं। हर बार आप तय करते हैं - इस टैप के बिना कभी कुछ नहीं भेजा जाता।';

  @override
  String get msgCloudAgain =>
      'यह संदेश गहरी जाँच के लिए FraudShield सर्वर को भेजा जाएगा (न सेव होगा, न लॉग होगा)।';

  @override
  String get msgCloudSend => 'जाँच के लिए भेजें';

  @override
  String get msgCloudDone => 'क्लाउड जाँच शामिल (संदेश सेव नहीं हुआ)।';

  @override
  String get msgCloudFailed =>
      'क्लाउड जाँच अभी उपलब्ध नहीं - ऊपर फ़ोन की अपनी जाँच मान्य है।';

  @override
  String get msgDashTile => 'संदेश जाँचें';

  @override
  String get msgDashTileSub =>
      'SMS / WhatsApp संदेश चिपकाकर देखें कि यह धोखा है या नहीं';

  @override
  String get leftTile => 'बची हुई फ़ाइलें';

  @override
  String get leftTileSub =>
      'हटाए गए ऐप्स ने फ़ोन पर जो फ़ाइलें छोड़ीं, उन्हें खोजें';

  @override
  String leftTilePending(int count) {
    return '$count हटाए गए ऐप ने फ़ाइलें छोड़ीं - देखने के लिए टैप करें';
  }

  @override
  String get leftTitle => 'बची हुई फ़ाइलें';

  @override
  String leftTitleApp(String name) {
    return '$name की बची हुई फ़ाइलें';
  }

  @override
  String get leftIntro =>
      'हर ऐप का निजी डेटा Android खुद हटा देता है। लेकिन ऐप साझा फ़ोल्डरों (Pictures, Download, अपने फ़ोल्डर) में भी फ़ाइलें सेव कर सकते हैं - वे अनइंस्टॉल के बाद भी रह जाती हैं। जब तक आप चुनकर पुष्टि न करें, कुछ भी नहीं हटाया जाता।';

  @override
  String get leftPrivateNote =>
      'FraudShield दूसरे ऐप्स के Android/data फ़ोल्डर नहीं देख सकता (Android इसकी अनुमति नहीं देता) - ऐप हटने पर Android उन्हें खुद साफ़ करता है।';

  @override
  String get leftGroupInstaller => 'ऐप इंस्टॉलर (.apk)';

  @override
  String get leftGroupCache => 'कैश और अस्थायी फ़ाइलें';

  @override
  String get leftGroupMedia => 'फ़ोटो, वीडियो और ऑडियो';

  @override
  String get leftGroupDocument => 'दस्तावेज़';

  @override
  String get leftGroupOther => 'अन्य फ़ाइलें';

  @override
  String get leftWhatInstaller =>
      'इंस्टॉलर फ़ाइल - ऐप इंस्टॉल या हटने के बाद इसकी ज़रूरत नहीं।';

  @override
  String get leftWhatCache => 'ऐप का अपना अस्थायी डेटा - हटाना सुरक्षित है।';

  @override
  String get leftWhatMedia =>
      'ऐप द्वारा सेव की गई आपकी फ़ोटो, वीडियो या ऑडियो - हटाने से पहले जाँच लें।';

  @override
  String get leftWhatDocument =>
      'ऐप द्वारा सेव किए गए दस्तावेज़ - हटाने से पहले जाँच लें।';

  @override
  String get leftWhatOther =>
      'ऐप द्वारा सेव की गई फ़ाइलें - हटाने से पहले जाँच लें।';

  @override
  String leftFolderInfo(int files, String size) {
    return 'फ़ोल्डर, $files फ़ाइलें, $size';
  }

  @override
  String get leftNothing => 'कुछ नहीं बचा - सब साफ़ है।';

  @override
  String get leftRemovedApps => 'हटाए गए ऐप';

  @override
  String leftRemovedInfo(int files, String size) {
    return '$files फ़ाइलें, $size';
  }

  @override
  String get leftOrphans => 'उन ऐप्स के फ़ोल्डर जो अब इंस्टॉल नहीं हैं';

  @override
  String leftOrphanFrom(String name) {
    return '$name से';
  }

  @override
  String get leftApks => 'Download में पुराने ऐप इंस्टॉलर';

  @override
  String get leftApkSame => 'यही वर्ज़न इंस्टॉल है - ज़रूरत नहीं';

  @override
  String get leftApkOlder => 'इंस्टॉल ऐप से पुराना - ज़रूरत नहीं';

  @override
  String get leftApkNewer =>
      'इंस्टॉल ऐप से नया - एक अपडेट जो आपने इंस्टॉल नहीं किया';

  @override
  String get leftApkNotInstalled => 'यह ऐप इंस्टॉल नहीं है';

  @override
  String get leftApkFlagged => 'फ़ाइल सुरक्षा ने इसे खतरनाक बताया - इसे हटाएँ';

  @override
  String get leftApkUnreadable => 'यह इंस्टॉलर पढ़ा नहीं जा सका';

  @override
  String leftDelete(int count, String size) {
    return 'चुने हुए हटाएँ ($count, $size)';
  }

  @override
  String leftConfirmTitle(int count) {
    return '$count चीज़ें हटाएँ?';
  }

  @override
  String get leftConfirmBody =>
      'फ़ोटो, वीडियो और ऑडियो फ़ोन के ट्रैश में जाते हैं - 30 दिन तक वापस ला सकते हैं। बाकी फ़ाइलें (इंस्टॉलर, दस्तावेज़, कैश) हटा दी जाती हैं। जो ऐप अभी इंस्टॉल हैं उनकी फ़ाइलें कभी नहीं छुई जातीं।';

  @override
  String get leftConfirmYes => 'हटाएँ';

  @override
  String get leftCancel => 'रद्द करें';

  @override
  String get leftKeep => 'सब रखें';

  @override
  String leftDone(int trashed, int deleted) {
    return '$trashed ट्रैश में भेजी गईं, $deleted हटाई गईं।';
  }

  @override
  String leftRefused(int count) {
    return '$count चीज़ें नहीं छुई गईं - वे किसी इंस्टॉल ऐप या सुरक्षित फ़ोल्डर की हैं।';
  }

  @override
  String get leftCancelled => 'कुछ नहीं हटाया गया।';

  @override
  String get leftSettingsTitle => 'सेटिंग्स में कुछ बचा तो नहीं?';

  @override
  String get leftSetClear => 'अब सूची में नहीं';

  @override
  String get leftSetLeftover =>
      'अभी भी हटाए गए ऐप की ओर इशारा करता है - सेटिंग्स खोलकर बंद करें';

  @override
  String get leftSetUnknown => 'Android ऐप्स को यह जाँचने नहीं देता';

  @override
  String get leftKindAccessibility => 'सुलभता (Accessibility)';

  @override
  String get leftKindNotif => 'नोटिफ़िकेशन एक्सेस';

  @override
  String get leftKindAdmin => 'डिवाइस एडमिन';

  @override
  String get leftKindVpn => 'हमेशा-चालू VPN';

  @override
  String get leftKindSms => 'डिफ़ॉल्ट SMS ऐप';

  @override
  String get leftKindDialer => 'डिफ़ॉल्ट फ़ोन ऐप';

  @override
  String get leftKindBrowser => 'डिफ़ॉल्ट ब्राउज़र';

  @override
  String get leftKindHome => 'डिफ़ॉल्ट होम स्क्रीन';

  @override
  String get leftNeedAccess =>
      'फ़ाइल एक्सेस दें ताकि FraudShield बची हुई फ़ाइलें खोज सके।';

  @override
  String get callTile => 'कॉल गार्ड';

  @override
  String get callTileOff => 'बंद - धोखे वाली कॉल रोकने के लिए टैप करें';

  @override
  String callTileOn(int blocked) {
    return 'चालू - 30 दिनों में $blocked कॉल रोकी गईं';
  }

  @override
  String get callTileUnsupported => 'Android 10 या नया चाहिए';

  @override
  String get callTitle => 'कॉल गार्ड';

  @override
  String get callIntro =>
      'FraudShield उन नंबरों की कॉल जाँचता है जो आपके कॉन्टैक्ट में नहीं हैं - आपके फ़ोन पर ही, नंबर कहीं भेजे बिना।';

  @override
  String get callUnsupported =>
      'कॉल गार्ड के लिए Android 10 या नया चाहिए। आप नीचे किसी धोखेबाज़ नंबर की रिपोर्ट फिर भी कर सकते हैं।';

  @override
  String get callRoleTitle => 'FraudShield को कॉल जाँचने दें';

  @override
  String get callRoleBody =>
      'Android आपसे FraudShield को \"कॉलर ID और स्पैम\" ऐप चुनने को कहेगा। आपका फ़ोन ऐप वही रहेगा।';

  @override
  String get callRoleButton => 'अनुमति दें';

  @override
  String get callRoleOn => 'FraudShield कॉल जाँच रहा है';

  @override
  String get callModeTitle => 'जोखिम वाली कॉल के साथ क्या हो?';

  @override
  String get callMode1 => 'सिर्फ़ जानकारी दिखाएँ';

  @override
  String get callMode1Sub => 'कोई कॉल न काटें - बस बताएँ कि कौन हो सकता है';

  @override
  String get callMode2 => 'ज्ञात धोखेबाज़ नंबर रोकें (सुझाया गया)';

  @override
  String get callMode2Sub =>
      'जिन नंबरों को कई लोगों ने धोखा बताया है, उनकी कॉल काट दी जाती है';

  @override
  String get callMode3 => 'धोखा रोकें, जोखिम वाली कॉल बिना आवाज़';

  @override
  String get callMode3Sub =>
      'विदेशी और असामान्य नंबर बिना आवाज़ के बजते हैं; आप उन्हें फिर भी देखते हैं';

  @override
  String get callMode4 => 'सभी अनजान नंबर काटें';

  @override
  String get callMode4Sub =>
      'सिर्फ़ कॉन्टैक्ट, जिन्हें आपने कॉल किया, और आपकी अनुमति सूची ही बज सकती है';

  @override
  String get callMode4WarnTitle => 'हर अनजान कॉल काटें?';

  @override
  String get callMode4WarnBody =>
      'डिलीवरी वाले, डॉक्टर, स्कूल का नया नंबर या बैंक का नया दफ़्तर नहीं बजेगा। ये रोकी गई कॉल में दिखेंगे, जहाँ आप इन्हें अनुमति दे सकते हैं। आपातकालीन नंबर और बैंकों की आधिकारिक 1600 कॉल हमेशा बजेंगी।';

  @override
  String get callMode4Confirm => 'हाँ, अनजान कॉल काटें';

  @override
  String get callCancel => 'रद्द करें';

  @override
  String get callContactsTitle => 'जान-पहचान वालों को कभी न रोकें';

  @override
  String get callContactsBody =>
      'FraudShield को आपके कॉन्टैक्ट और आपके किए गए नंबर इसी फ़ोन पर जाँचने दें। कुछ भी अपलोड नहीं होता।';

  @override
  String get callContactsButton => 'कॉन्टैक्ट और कॉल इतिहास की अनुमति दें';

  @override
  String get callNotifTitle => 'सूचनाओं की अनुमति दें';

  @override
  String get callNotifBody =>
      'ताकि FraudShield बता सके कि किसने कॉल की और कॉल क्यों रोकी गई।';

  @override
  String get callAllowTitle => 'हमेशा अनुमति';

  @override
  String get callAllowHint => 'फ़ोन नंबर';

  @override
  String get callAllowAdd => 'जोड़ें';

  @override
  String get callAllowInvalid => 'यह सही फ़ोन नंबर नहीं है';

  @override
  String get callAllowEmpty => 'अभी कोई नंबर नहीं';

  @override
  String get callRemove => 'हटाएँ';

  @override
  String get callBlockTitle => 'आपके ब्लॉक किए नंबर';

  @override
  String callListInfo(int count) {
    return 'इस फ़ोन पर धोखा सूची: $count नंबर';
  }

  @override
  String get callSync => 'सूची अपडेट करें';

  @override
  String get callSyncDone => 'सूची अपडेट हुई';

  @override
  String get callSyncFailed =>
      'अभी अपडेट नहीं हो सका - फ़ोन पर मौजूद सूची काम करती रहेगी';

  @override
  String get callBlockedTitle => 'रोकी गई कॉल';

  @override
  String get callBlockedSub => 'पिछले 30 दिन - सिर्फ़ इसी फ़ोन पर';

  @override
  String get callBlockedEmpty => 'पिछले 30 दिनों में कोई कॉल नहीं जाँची गई';

  @override
  String get callActionReject => 'काटी गई';

  @override
  String get callActionSilence => 'बिना आवाज़';

  @override
  String get callActionAllow => 'चेतावनी के साथ बजी';

  @override
  String get callUndone => 'आपने अनुमति दी';

  @override
  String get callNotSpam => 'स्पैम नहीं';

  @override
  String callNotSpamDone(String number) {
    return 'ठीक है - $number को अब हमेशा अनुमति है';
  }

  @override
  String get callReport => 'रिपोर्ट करें';

  @override
  String get callReportTitle => 'नंबर की रिपोर्ट करें';

  @override
  String get callReportIntro =>
      'जब कई लोग एक ही नंबर की रिपोर्ट करते हैं तो वह सबके लिए धोखा सूची में जुड़ जाता है। हर व्यक्ति एक नंबर की एक ही रिपोर्ट।';

  @override
  String get callReportNumber => 'फ़ोन नंबर';

  @override
  String get callReportCategory => 'यह क्या था?';

  @override
  String get callCatScam => 'धोखा / फ्रॉड';

  @override
  String get callCatSpam => 'स्पैम';

  @override
  String get callCatTele => 'टेलीमार्केटिंग';

  @override
  String get callReportSend => 'FraudShield को रिपोर्ट करें';

  @override
  String get callReportDone => 'रिपोर्ट भेजी गई - धन्यवाद';

  @override
  String get callReportProtected =>
      'यह आधिकारिक 1600 / 140 सेवा नंबर है। TRAI इनकी रिपोर्ट या ब्लॉक करने की अनुमति नहीं देता।';

  @override
  String get callReportLimit =>
      'आज आपने कई नंबरों की रिपोर्ट की है - कृपया कल फिर कोशिश करें';

  @override
  String get callReportFailed =>
      'अभी रिपोर्ट नहीं हो सकी - इंटरनेट जाँचकर फिर कोशिश करें';

  @override
  String get callGovTitle => 'सरकार को रिपोर्ट करें (चक्षु)';

  @override
  String get callGovBody =>
      'संचार साथी \"चक्षु\" दूरसंचार विभाग का मुफ़्त पोर्टल है जहाँ धोखे वाली कॉल और SMS की रिपोर्ट होती है। नंबर कॉपी करें, फिर फ़ॉर्म में पेस्ट करें।';

  @override
  String get callGovCopy => 'नंबर कॉपी करें';

  @override
  String get callGovCopied => 'नंबर कॉपी हुआ';

  @override
  String get callGovOpen => 'चक्षु खोलें';

  @override
  String get callGovMoney =>
      'पैसे गए? अभी 1930 पर कॉल करें या cybercrime.gov.in पर रिपोर्ट करें';

  @override
  String get blockTitleWeb => 'विज्ञापन और ट्रैकर ब्लॉकिंग';

  @override
  String get blockTitlePlay => 'ट्रैकर और मैलवेयर ब्लॉकिंग';

  @override
  String get blockIntroWeb =>
      'हर ऐप और ब्राउज़र में विज्ञापन, ट्रैकिंग और मैलवेयर सर्वर ब्लॉक करता है - आपके फ़ोन पर ही, कुछ भी कहीं नहीं भेजा जाता।';

  @override
  String get blockIntroPlay =>
      'हर ऐप और ब्राउज़र में जाने-माने ट्रैकिंग और मैलवेयर सर्वर ब्लॉक करता है - आपके फ़ोन पर ही, कुछ भी कहीं नहीं भेजा जाता।';

  @override
  String get blockAdsNotInPlay =>
      'इस संस्करण में विज्ञापन ब्लॉक नहीं होते: Google Play के नियम दूसरे ऐप के विज्ञापन ब्लॉक करने की अनुमति नहीं देते। हमारी वेबसाइट वाला संस्करण विज्ञापन ब्लॉक कर सकता है।';

  @override
  String get blockNeedsVpn =>
      'यह तब काम करता है जब नकली-वेबसाइट सुरक्षा (फ़ोन पर VPN) चालू हो।';

  @override
  String get blockCatAds => 'विज्ञापन';

  @override
  String get blockCatAdsSub => 'ऐप और वेबसाइट के विज्ञापन सर्वर';

  @override
  String get blockCatTrackers => 'ट्रैकर';

  @override
  String get blockCatTrackersSub =>
      'एनालिटिक्स और ट्रैकिंग सर्वर जो आपकी गतिविधि पर नज़र रखते हैं';

  @override
  String get blockCatMalware => 'मैलवेयर और धोखाधड़ी साइटें';

  @override
  String get blockCatMalwareSub =>
      'जाने-माने मैलवेयर, फ़िशिंग और धोखाधड़ी सर्वर';

  @override
  String blockList(int count) {
    return 'ब्लॉक सूची: $count साइटें';
  }

  @override
  String get blockListNone => 'ब्लॉक सूची अभी डाउनलोड नहीं हुई';

  @override
  String get blockUpdate => 'अभी अपडेट करें';

  @override
  String get blockUpdated => 'ब्लॉक सूची अप-टू-डेट है';

  @override
  String get blockUpdateFailed => 'ब्लॉक सूची अभी अपडेट नहीं हो सकी';

  @override
  String get blockPause5 => 'ब्लॉकिंग 5 मिनट के लिए रोकें';

  @override
  String blockPausedUntil(String time) {
    return '$time तक रुका हुआ';
  }

  @override
  String get blockResume => 'अभी फिर शुरू करें';

  @override
  String get blockAllowedApps => 'जिन ऐप पर ब्लॉकिंग नहीं';

  @override
  String get blockAllowedAppsNone => 'कोई नहीं - ब्लॉकिंग हर ऐप पर लागू है';

  @override
  String get blockAllowApp => 'किसी ऐप को अनुमति दें…';

  @override
  String get blockPickApp => 'ऐप चुनें';

  @override
  String get blockPickAppBody =>
      'VPN द्वारा देखे गए ऐप। \"अनुमति दें\" उस ऐप के लिए ब्लॉकिंग बंद करता है। \"यह ऐप बिगड़ गया\" भी यही करता है और इस फ़ोन पर एक नोट रखता है (कुछ नहीं भेजा जाता)।';

  @override
  String get blockAllow => 'अनुमति दें';

  @override
  String get blockAppBroke => 'यह ऐप बिगड़ गया';

  @override
  String blockAppBrokeDone(String app) {
    return '$app पर अब ब्लॉकिंग नहीं। नोट इसी फ़ोन पर रहेगा।';
  }

  @override
  String get blockRemove => 'हटाएँ';

  @override
  String get blockAllowedSites => 'जिन साइटों पर ब्लॉकिंग नहीं';

  @override
  String get blockAddSite => 'किसी साइट को अनुमति दें';

  @override
  String get blockSiteHint => 'example.com';

  @override
  String get blockAdd => 'जोड़ें';

  @override
  String get blockStats => 'ब्लॉकिंग आँकड़े';

  @override
  String blockStatsTotal(int count) {
    return '$count अनुरोध ब्लॉक हुए';
  }

  @override
  String blockStatsSaved(String size) {
    return 'लगभग $size डेटा बचा (अनुमान)';
  }

  @override
  String get blockStatsByCat => 'प्रकार के अनुसार';

  @override
  String get blockStatsByApp => 'ऐप के अनुसार';

  @override
  String get blockStatsTop => 'सबसे ज़्यादा ब्लॉक हुई साइटें';

  @override
  String blockStatsDays(int count) {
    return 'पिछले $count दिन';
  }

  @override
  String get blockStatsLocal =>
      'आपके फ़ोन पर गिना गया; ये आँकड़े फ़ोन से बाहर नहीं जाते।';

  @override
  String get blockStatsEmpty => 'अभी तक कुछ ब्लॉक नहीं हुआ।';

  @override
  String blockVpnCount(int count) {
    return 'VPN शुरू होने के बाद $count अनुरोध ब्लॉक हुए';
  }

  @override
  String get safeTileTitle => 'धोखाधड़ी से मदद';

  @override
  String get safeTileSub =>
      'लाइव स्कैम गार्ड, भुगतान से पहले जाँच, रिकवरी किट, अभ्यास';

  @override
  String get safeHubTitle => 'धोखाधड़ी से मदद';

  @override
  String get safeGuardTitle => 'लाइव स्कैम गार्ड';

  @override
  String get safeGuardSub =>
      'जब ठगी वाली कॉल, स्क्रीन-शेयरिंग ऐप और बैंक ऐप एक साथ हों तो STOP चेतावनी';

  @override
  String get safeUpiTitle => 'भुगतान से पहले जाँचें';

  @override
  String get safeUpiSub => 'UPI ID या UPI QR कोड का टेक्स्ट';

  @override
  String get safeCareTitle => 'कस्टमर-केयर नंबर जाँचें';

  @override
  String get safeCareSub => 'नकली हेल्पलाइन नंबर एक आम जाल हैं';

  @override
  String get safeRecoveryTitle => 'मेरे साथ ठगी हुई - अब क्या करूँ?';

  @override
  String get safeRecoverySub => 'पहले घंटे के कदम, 1930, कार्ड ब्लॉक, सबूत';

  @override
  String get safeGuardianTitle => 'अभिभावक (गार्डियन)';

  @override
  String get safeGuardianSub => 'परिवार का कोई सदस्य जिसे एक टैप में बुला सकें';

  @override
  String get safeDrillsTitle => 'धोखाधड़ी अभ्यास';

  @override
  String get safeDrillsSub => '5 आम धोखों को पहचानने का अभ्यास';

  @override
  String get safeGuardIntro =>
      'ज़्यादातर बैंक ठगी एक ही तरीके से होती है: अनजान कॉलर आपको फ़ोन पर रोके रखता है, स्क्रीन-शेयरिंग ऐप (AnyDesk, TeamViewer...) खुलवाता है, फिर बैंक ऐप खुलवाता है या OTP पूछता है। जब FraudShield ये सब एक साथ देखता है तो STOP चेतावनी दिखाता है। FraudShield को केवल संकेत का प्रकार भेजा जाता है - नंबर, ऐप का नाम या कोड कभी नहीं।';

  @override
  String get safeGuardSwitch => 'लाइव स्कैम गार्ड';

  @override
  String get safeOtpSwitch => 'कॉल के दौरान OTP आए तो चेतावनी दें';

  @override
  String get safeOtpSwitchSub =>
      'केवल फ़ोन पर काम करता है; कोड कभी सेव नहीं होता';

  @override
  String get safePermsTitle => 'गार्ड को क्या चाहिए';

  @override
  String get safePermPhone => 'फ़ोन: क्या कॉल चल रही है';

  @override
  String get safePermCallLog =>
      'कॉलर का नंबर - फ़ोन पर ही आपके कॉन्टैक्ट से मिलाया जाता है';

  @override
  String get safePermContacts => 'कॉन्टैक्ट';

  @override
  String get safePermUsage => 'उपयोग एक्सेस: कौन-सा ऐप खुला है';

  @override
  String get safePermNotif => 'नोटिफ़िकेशन एक्सेस: OTP आया';

  @override
  String get safeAllow => 'अनुमति दें';

  @override
  String get safeAllowed => 'अनुमति है';

  @override
  String get safeTestStop => 'STOP चेतावनी जाँचें';

  @override
  String get safeStopTitle => 'रुकिए!';

  @override
  String get safeStopHeadline => 'यह अभी चल रही ठगी लगती है';

  @override
  String get safeStopWarnHeadline => 'सावधान: यह ठगी हो सकती है';

  @override
  String get safeStopBody =>
      'अनजान कॉलर, स्क्रीन-शेयरिंग ऐप और बैंक ऐप एक साथ - बैंक ठगी ठीक ऐसे ही होती है।';

  @override
  String get safeStopStep1 => 'अभी कॉल काटें।';

  @override
  String get safeStopStep2 => 'कोई OTP, PIN या पासवर्ड न बताएँ।';

  @override
  String get safeStopStep3 =>
      'स्क्रीन-शेयरिंग ऐप (AnyDesk, TeamViewer...) बंद करें और हटा दें।';

  @override
  String get safeStopStep4 =>
      'अगर भुगतान कर दिया या कोड बता दिया है: अभी 1930 पर कॉल करें।';

  @override
  String get safeStopSpeak => 'पढ़कर सुनाएँ';

  @override
  String get safeNoVoice =>
      'इस फ़ोन पर इस भाषा की आवाज़ नहीं है - कृपया ऊपर के कदम पढ़ें।';

  @override
  String get safeCall1930 => '1930 पर कॉल करें';

  @override
  String get safeOpenRecovery => 'रिकवरी किट खोलें';

  @override
  String get safeImSafe => 'मैं सुरक्षित हूँ - बंद करें';

  @override
  String safeCallGuardian(String name) {
    return '$name को कॉल करें';
  }

  @override
  String safeTellGuardian(String name) {
    return '$name को संदेश भेजें';
  }

  @override
  String get safeGuardianSms =>
      'FraudShield चेतावनी: मैं शायद अभी किसी ठग से बात कर रहा/रही हूँ। कृपया मुझे कॉल करें।';

  @override
  String get safeRecTitle => 'रिकवरी किट';

  @override
  String get safeRecIntro =>
      'पहले घंटे (\"गोल्डन आवर\") में कदम उठाएँ: जितनी जल्दी शिकायत करेंगे, पैसा रुकवाने की संभावना उतनी अच्छी होगी।';

  @override
  String get safeRecStart => 'मेरे साथ अभी ठगी हुई - समय शुरू करें';

  @override
  String safeRecElapsed(String time) {
    return 'शुरू किए $time हुए';
  }

  @override
  String get safeRecReset => 'रीसेट';

  @override
  String get safeRec1930 =>
      '1930 पर कॉल करें - राष्ट्रीय साइबर क्राइम हेल्पलाइन';

  @override
  String get safeRecPortal => 'cybercrime.gov.in पर शिकायत करें';

  @override
  String get safeRecBlockTitle => 'अपना कार्ड / खाता ब्लॉक करें';

  @override
  String get safeRecBlockSub =>
      'हर बैंक की अपनी वेबसाइट से लिए नंबर (नवंबर 2026)। अपने कार्ड पर भी जाँच लें।';

  @override
  String get safeRecChecklist => 'चेकलिस्ट';

  @override
  String get safeRecStepCall1930 => '1930 पर कॉल किया';

  @override
  String get safeRecStepBank => 'बैंक को कॉल किया; कार्ड / UPI ब्लॉक किया';

  @override
  String get safeRecStepPasswords =>
      'UPI PIN, नेट-बैंकिंग और ईमेल पासवर्ड बदले';

  @override
  String get safeRecStepRemote => 'स्क्रीन-शेयरिंग ऐप हटाए';

  @override
  String get safeRecStepPortal => 'cybercrime.gov.in पर शिकायत दर्ज की';

  @override
  String get safeRecStepKeep => 'शिकायत / पावती नंबर सेव किया';

  @override
  String get safeRecEvidence => 'सबूत';

  @override
  String get safeRecEvidenceSub =>
      'जो हुआ वह लिखें। जब तक आप साझा न करें, यह आपके फ़ोन पर ही रहता है।';

  @override
  String get safeRecEvWho => 'ठग का नंबर / UPI ID / लिंक';

  @override
  String get safeRecEvMoney => 'हर भुगतान की रकम, तारीख और समय';

  @override
  String get safeRecEvApps => 'जो ऐप उन्होंने इंस्टॉल करवाए';

  @override
  String get safeRecEvNotes => 'क्या हुआ';

  @override
  String get safeRecShare => 'सबूत साझा करें';

  @override
  String get safeRecScreens =>
      'स्क्रीनशॉट: गैलरी खोलें, उन्हें चुनें और अपनी शिकायत या बैंक को भेजें।';

  @override
  String get safeGuardianIntro =>
      'परिवार का कोई सदस्य चुनें। जब FraudShield ठगी होते देखता है, तो STOP स्क्रीन पर उन्हें एक टैप में कॉल या संदेश करने का विकल्प आता है। अपने-आप कुछ नहीं भेजा जाता और किसी संदेश की सामग्री साझा नहीं होती।';

  @override
  String get safeGuardianName => 'नाम';

  @override
  String get safeGuardianNumber => 'मोबाइल नंबर';

  @override
  String get safeGuardianSave => 'गार्डियन सेव करें';

  @override
  String get safeGuardianRemove => 'गार्डियन हटाएँ';

  @override
  String safeGuardianSaved(String name) {
    return '$name आपके गार्डियन हैं';
  }

  @override
  String get safeGuardianInvalid => 'नाम और सही भारतीय मोबाइल नंबर डालें';

  @override
  String get safeGuardianTest => 'टेस्ट संदेश भेजें';

  @override
  String get safeGuardianTestText =>
      'FraudShield टेस्ट: आप मेरे गार्डियन हैं। अगर मैं कभी ठगी वाली कॉल में हुआ/हुई, तो FraudShield मुझे आपको कॉल या संदेश करने में मदद करेगा।';

  @override
  String get safeUpiHint => 'UPI ID (name@bank) या QR टेक्स्ट (upi://pay?...)';

  @override
  String get safeUpiQrNote =>
      'QR कोड जाँचने के लिए: उसे Google Lens या कैमरे से स्कैन करें, टेक्स्ट कॉपी करके यहाँ चिपकाएँ।';

  @override
  String get safeCheck => 'जाँचें';

  @override
  String get safeVerdictSafe => 'चेतावनी के संकेत नहीं मिले';

  @override
  String get safeVerdictSusp => 'सावधान रहें';

  @override
  String get safeVerdictBad => 'भुगतान न करें';

  @override
  String safeUpiPayee(String vpa) {
    return 'भुगतान जाएगा: $vpa';
  }

  @override
  String get safeReport => 'धोखे के रूप में रिपोर्ट करें';

  @override
  String get safeReported => 'रिपोर्ट हो गई - धन्यवाद';

  @override
  String get safeReportFailed => 'अभी रिपोर्ट नहीं हो सकी';

  @override
  String get safeCheckFailed => 'अभी जाँच नहीं हो सकी - फिर कोशिश करें';

  @override
  String get safeCareHint => 'फ़ोन नंबर';

  @override
  String get safeCareBrand => 'कंपनी (वैकल्पिक, जैसे SBI)';

  @override
  String get safeCareOfficial => 'आधिकारिक नंबर';

  @override
  String get safeCareFake => 'नकली कस्टमर-केयर नंबर';

  @override
  String get safeCareSusp => 'आधिकारिक नंबर नहीं';

  @override
  String get safeCareUnknown =>
      'हमारी सूची में नहीं - कंपनी की अपनी वेबसाइट पर जाँचें';

  @override
  String get safeCareReal => 'असली नंबर';

  @override
  String get safeDrillsIntro =>
      'छोटे अभ्यास। चुनें कि आप क्या करेंगे - यह केवल सीखने के लिए है।';

  @override
  String safeDrillBest(int score) {
    return 'सर्वश्रेष्ठ: $score%';
  }

  @override
  String get safeDrillNotPlayed => 'अभी नहीं खेला';

  @override
  String get safeDrillNext => 'आगे';

  @override
  String get safeDrillFinish => 'समाप्त';

  @override
  String safeDrillResult(int score) {
    return 'आपका स्कोर $score%';
  }

  @override
  String get safeDrillSafe => 'सुरक्षित विकल्प';

  @override
  String get safeDrillRisky => 'जोखिम वाला विकल्प';

  @override
  String get homeProtected => 'आप सुरक्षित हैं';

  @override
  String homeNeedsAttention(int n) {
    return '$n सुरक्षा पर ध्यान दें';
  }

  @override
  String get homeProtectedDetail =>
      'सभी सुरक्षा चालू हैं। FraudShield पीछे से नज़र रखता है।';

  @override
  String get homeAttentionDetail =>
      'पूरी तरह सुरक्षित रहने के लिए नीचे इन्हें चालू करें।';

  @override
  String get homeQuick => 'तुरंत करें';

  @override
  String get homeStatus => 'सुरक्षा की स्थिति';

  @override
  String get homeStatusSub => 'दोबारा जाँचने या चालू करने के लिए कार्ड दबाएँ';

  @override
  String get homeTools => 'टूल और मदद';

  @override
  String get homeAccountSub => 'खाता चोरी और SMS चोरी रोकें';

  @override
  String get homeScamHelp => 'धोखे में मदद';

  @override
  String get homeCallGuard => 'कॉल गार्ड';

  @override
  String get homeCleaner => 'बचा डेटा साफ़ करें';

  @override
  String get homeScammed => 'मेरे साथ धोखा हुआ';

  @override
  String get ui2LoginPromise =>
      'धोखे, नकली साइट और खाता चोरी को आप तक पहुँचने से पहले रोकता है।';

  @override
  String get ui1VpnOptions => 'निजता और DNS विकल्प';

  @override
  String get homeGroupPhone => 'आपका फ़ोन और ऐप';

  @override
  String get homeGroupPhoneSub => 'जोखिम वाले ऐप, क्लोनिंग और बची फ़ाइलें';

  @override
  String get homeGroupCalls => 'कॉल और संदेश';

  @override
  String get homeGroupCallsSub => 'धोखे वाली कॉल रोकें, SMS चोरी रोकें';

  @override
  String get permTitle => 'अनुमतियाँ';

  @override
  String permSummary(int allowed, int total) {
    return '$total में से $allowed चालू';
  }

  @override
  String permPending(int n) {
    return '$n पर आपकी मंज़ूरी चाहिए - चालू करने के लिए दबाएँ';
  }

  @override
  String get permAllDone => 'FraudShield की ज़रूरी सब अनुमतियाँ चालू हैं';

  @override
  String get permIntro =>
      'FraudShield सिर्फ़ वही माँगता है जिसकी सुरक्षा को ज़रूरत है। किसी भी चीज़ को दबाएँ: Android का सही पेज खुलेगा - चालू करें और वापस आएँ।';

  @override
  String get permNeedsOk => 'आपकी मंज़ूरी चाहिए';

  @override
  String get permAllowed => 'चालू';

  @override
  String get permByHand => 'फ़ोन की सेटिंग में जाँचें';

  @override
  String get permOptional => 'वैकल्पिक';

  @override
  String get permAllow => 'चालू करें';

  @override
  String get permOpen => 'खोलें';

  @override
  String get permOn => 'चालू';

  @override
  String get permNameNotifications => 'सूचनाएँ';

  @override
  String get permWhyNotifications => 'ताकि धोखे की चेतावनी आप तक पहुँचे।';

  @override
  String get permNameFullScreen => 'पूरी स्क्रीन वाली चेतावनी';

  @override
  String get permWhyFullScreen => 'ज़रूरी चेतावनी लॉक स्क्रीन पर भी दिखे।';

  @override
  String get permNameNotifAccess => 'सूचना पढ़ने की अनुमति';

  @override
  String get permWhyNotifAccess =>
      'संदेशों और सूचनाओं के लिंक जाँचता है। संदेश का टेक्स्ट कभी अपलोड नहीं होता।';

  @override
  String get permNameFiles => 'सभी फ़ाइलों की अनुमति';

  @override
  String get permWhyFiles =>
      'डाउनलोड हुई APK और PDF फ़ाइलों में खतरा जाँचता है।';

  @override
  String get permNamePhone => 'फ़ोन';

  @override
  String get permWhyPhone =>
      'कॉल फ़ॉरवर्डिंग जाँचता है और धोखा अलार्म चलाता है।';

  @override
  String get permNameVpn => 'नकली वेबसाइट सुरक्षा (VPN)';

  @override
  String get permWhyVpn => 'हर ऐप में नकली और खतरनाक साइट रोकता है।';

  @override
  String get permNameCallScreening => 'कॉलर ID और स्पैम';

  @override
  String get permWhyCallScreening =>
      'कॉल गार्ड को धोखे वाली कॉल रोकने देता है।';

  @override
  String get permNameContacts => 'संपर्क';

  @override
  String get permWhyContacts => 'आपके सेव किए संपर्क कभी नहीं रोके जाते।';

  @override
  String get permNameCallLog => 'कॉल इतिहास';

  @override
  String get permWhyCallLog => 'जिन्हें आपने कॉल किया वे कभी नहीं रोके जाते।';

  @override
  String get permNameUsage => 'उपयोग की जानकारी';

  @override
  String get permWhyUsage => 'अपने आप खुलने वाले ऐप पकड़ता है।';

  @override
  String get permNameBattery => 'बैटरी: कोई रोक नहीं';

  @override
  String get permWhyBattery => 'सुरक्षा को पीछे से चालू रखता है।';

  @override
  String get permNameAutostart => 'ऑटोस्टार्ट (फ़ोन कंपनी की सेटिंग)';

  @override
  String get permWhyAutostart =>
      'रीस्टार्ट के बाद सुरक्षा फिर चालू हो। Android यह हमें नहीं दिखाता - कृपया खुद जाँचें।';
}
