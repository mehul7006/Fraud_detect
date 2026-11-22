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
}
