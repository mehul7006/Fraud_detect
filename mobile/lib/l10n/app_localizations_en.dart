// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'FraudShield';

  @override
  String get threatHistory => 'Threat history';

  @override
  String get linkSettings => 'Link settings';

  @override
  String get logOut => 'Log out';

  @override
  String get allow => 'Allow';

  @override
  String get grantAccess => 'Grant access';

  @override
  String get permNotifTitle => 'Allow notifications';

  @override
  String get permNotifBody =>
      'Android 13+ blocks all alerts until you allow notifications. Without it FraudShield cannot warn you.';

  @override
  String get permLinkTitle => 'Enable link & ad monitoring';

  @override
  String get permLinkBody =>
      'Grant Notification Access so FraudShield can scan links sent via WhatsApp, Telegram, SMS and count ad spam. Message text is never uploaded.';

  @override
  String get permFilesTitle => 'Enable file protection';

  @override
  String get permFilesBody =>
      'Allow storage access (\"All files access\" on Android 11 and newer) so FraudShield can scan downloaded APK/PDF files. Only hash and file name are sent.';

  @override
  String get permPhoneTitle => 'Phone permission';

  @override
  String get permPhoneBody =>
      'Needed to check call forwarding with your carrier.';

  @override
  String get permUsageTitle => 'Find apps that open by themselves (optional)';

  @override
  String get permUsageBody =>
      'Allow Usage Access so FraudShield can notice apps that pop up without you tapping them. Only a count per app is kept; nothing leaves your phone except that count.';

  @override
  String get offlineTitle => 'Can\'t reach the FraudShield server';

  @override
  String get offlineBody =>
      'Link and file checks are kept and retried automatically. Tap a tile to try again.';

  @override
  String get couldntCheck => 'Couldn\'t check right now - tap to retry';

  @override
  String get checking => 'checking...';

  @override
  String get callForwarding => 'Call forwarding';

  @override
  String get cloneRemote => 'Clone / remote access';

  @override
  String get riskyApps => 'Risky / ad-spam apps';

  @override
  String get linkProtection => 'Link protection';

  @override
  String get fileProtection => 'File protection';

  @override
  String get statusOn => 'ON';

  @override
  String get statusOff => 'OFF';

  @override
  String get statusActive => 'ACTIVE';

  @override
  String get statusOk => 'OK';

  @override
  String statusRisk(String score) {
    return 'RISK $score';
  }

  @override
  String get fwdUnavailable =>
      'UNAVAILABLE - can\'t query your carrier from the app';

  @override
  String get fwdUnknown => 'UNKNOWN - carrier answer not understood';

  @override
  String fwdCarrierSaid(String text) {
    return 'UNKNOWN - carrier answer not understood\nCarrier said: \"$text\"';
  }

  @override
  String get checkManually => 'Check manually';

  @override
  String get carrierReplies => 'Carrier replies';

  @override
  String carrierRepliesBody(String json) {
    return 'Phone numbers are masked. Nothing was sent.\nCopy this and add your carrier name and whether forwarding was ON.\n\n$json';
  }

  @override
  String get copy => 'Copy';

  @override
  String get close => 'Close';

  @override
  String get accountSecurity => 'Account security';

  @override
  String get secureWhatsApp => 'Secure WhatsApp';

  @override
  String get secureWhatsAppSub => 'Log out all other devices';

  @override
  String get secureTelegram => 'Secure Telegram';

  @override
  String get secureTelegramSub => 'Terminate other sessions';

  @override
  String get smsForwarding => 'SMS forwarding';

  @override
  String get smsForwardingSub => 'Cannot be read by apps - see how to check';

  @override
  String get smsReaders => 'Apps that can read my SMS';

  @override
  String get smsReadersSub => 'Find OTP-stealing apps';

  @override
  String get secureAccount => 'Secure account';

  @override
  String get iUnderstand => 'I understand';

  @override
  String get ignore => 'Ignore';

  @override
  String get email => 'Email';

  @override
  String get passwordHint => 'Password (min 8)';

  @override
  String get login => 'Login';

  @override
  String get createAccount => 'Create account';

  @override
  String get haveAccount => 'Have an account? Login';

  @override
  String get newHere => 'New here? Register';

  @override
  String get signingIn => 'Signing in';

  @override
  String get wrongPassword => 'Wrong email or password';

  @override
  String get emailTaken => 'Email already registered';

  @override
  String get errOffline =>
      'No internet connection. FraudShield keeps watching; checks that need the server will retry automatically.';

  @override
  String get errTooMany =>
      'Too many attempts. Please wait a minute and try again.';

  @override
  String get errSession => 'Your session has ended. Please log in again.';

  @override
  String get errServer =>
      'FraudShield server problem. Please try again in a few minutes.';

  @override
  String get errGeneric => 'Something went wrong. Please try again.';

  @override
  String get tryAgain => 'Try again';

  @override
  String get noThreats => 'No threats found. You are safe.';

  @override
  String get resolve => 'Resolve';

  @override
  String get language => 'Language';

  @override
  String get languageSystem => 'Phone default';

  @override
  String get languageEnglish => 'English';

  @override
  String get languageHindi => 'हिन्दी (Hindi)';

  @override
  String wizTitle(String app) {
    return 'Secure $app';
  }

  @override
  String get wizWarning =>
      'Apps cannot log out other devices for you - only you can do it inside the app. Follow these steps now.';

  @override
  String wizOpenNow(String app) {
    return 'Open $app now';
  }

  @override
  String get wizNotInstalled => 'App not installed';

  @override
  String get waStep1 => 'Open WhatsApp > tap the 3 dots > Linked devices.';

  @override
  String get waStep2 =>
      'Tap every device you do not recognise > Log out. (Or: Log out from all devices.)';

  @override
  String get waStep3 =>
      'Settings > Account > Two-step verification > Turn on and set a PIN + recovery email.';

  @override
  String get waStep4 =>
      'Settings > Account > Passkeys / Email address: add an email so only you can recover the account.';

  @override
  String get waStep5 =>
      'Never share the 6-digit code received by SMS. WhatsApp support never asks for it.';

  @override
  String get waStep6 =>
      'If you are locked out: Uninstall > reinstall > register your number again. This logs the attacker out.';

  @override
  String get tgStep1 => 'Open Telegram > Settings > Devices.';

  @override
  String get tgStep2 => 'Tap \"Terminate all other sessions\".';

  @override
  String get tgStep3 =>
      'Settings > Privacy and Security > Two-Step Verification > set a password + recovery email.';

  @override
  String get tgStep4 =>
      'Settings > Privacy and Security > Active Websites: disconnect unknown ones.';

  @override
  String get tgStep5 =>
      'Settings > Privacy and Security > Passcode Lock: enable.';

  @override
  String get tgStep6 => 'Never share Telegram login codes.';

  @override
  String get smsFwdTitle => 'Check SMS forwarding';

  @override
  String get smsFwdIntro =>
      'Apps cannot see whether your carrier forwards your SMS. Follow these three checks.';

  @override
  String get smsFwd1Title => 'Remove apps that can read your SMS';

  @override
  String get smsFwd1Body =>
      'Scam \"SMS forwarder\" apps copy your OTPs to another phone.';

  @override
  String get smsFwd1Button => 'Show apps that read my SMS';

  @override
  String get smsFwd2Title => 'Ask your carrier';

  @override
  String get smsFwd2Body =>
      'Call customer care from your own number and ask: \"Is SMS forwarding or SMS redirect active on my number?\" Ask them to switch it off and to tell you if the SIM was duplicated or replaced.';

  @override
  String get smsFwd3Title => 'Watch for a SIM swap';

  @override
  String get smsFwd3Body =>
      'If your phone suddenly shows \"No service\" for no reason, call the carrier at once from another phone. A swapped SIM receives all your SMS and calls.';

  @override
  String get smsAuditTitle => 'Apps that can read your SMS';

  @override
  String get smsAuditError => 'Could not read installed apps';

  @override
  String get smsAuditIntro =>
      'If you do not recognise an app below, uninstall it. An unknown app that can read SMS can forward your OTPs.';

  @override
  String smsAuditUser(String count) {
    return 'Installed by you ($count)';
  }

  @override
  String get smsAuditNoneUser => 'No user-installed app can read your SMS';

  @override
  String smsAuditSystem(String count) {
    return 'System apps - usually safe ($count)';
  }

  @override
  String get readsSms => 'Reads SMS';

  @override
  String get readsNotifications => 'Reads notifications';

  @override
  String get uninstall => 'Uninstall';

  @override
  String get riskyTitle => 'Risky apps';

  @override
  String get riskyFailedTitle => 'Could not check your apps';

  @override
  String get riskyFailedBody =>
      'Check your internet connection and pull down to try again.';

  @override
  String get riskyCheckingTitle => 'Checking your apps...';

  @override
  String get riskyCheckingBody => 'This takes a few seconds.';

  @override
  String get riskyNoneTitle => 'No risky apps found';

  @override
  String get riskyNoneBody => 'Pull down to check again.';

  @override
  String get riskyShareTitle => 'Help improve risky-app detection';

  @override
  String get riskyShareBody =>
      'When you Uninstall or Ignore a flagged app, send its risk signals (notification count, permissions it can use) and your choice. No messages, no contacts, no list of your other apps.';

  @override
  String get newBadge => 'NEW';

  @override
  String get trustedSites => 'Trusted websites';

  @override
  String get trustedSitesBody =>
      'Links to these sites (and their sub-domains) are never scanned or flagged.';

  @override
  String get domain => 'Domain';

  @override
  String get domainInvalid => 'Enter a domain like example.com';

  @override
  String get trustDomain => 'Trust this domain';

  @override
  String removeDomain(String domain) {
    return 'Remove $domain';
  }

  @override
  String get appsToWatch => 'Apps to watch';

  @override
  String get appsToWatchBody =>
      'Turn an app off and links shown in its notifications are ignored.';

  @override
  String get noAppsFound => 'No apps found.';

  @override
  String get alertNoDanger => 'No known danger found';

  @override
  String alertNoDangerBody(String url) {
    return '$url\n\nStill be careful with links you did not expect.';
  }

  @override
  String get notifDangerous => 'Dangerous link!';

  @override
  String get alertSuspiciousLink => 'Suspicious link';

  @override
  String get alertDangerous => 'DANGEROUS LINK - do not open';

  @override
  String get alertCheckFailed => 'Could not check the link';

  @override
  String alertCheckFailedBody(String url) {
    return '$url\n\nNo connection to FraudShield. Try again in a moment.';
  }

  @override
  String get alertNoLink => 'No link found';

  @override
  String get alertNoLinkBody =>
      'The text you shared does not contain a web link.';

  @override
  String get notifSuspiciousFile => 'Suspicious file received';

  @override
  String get alertMaliciousFile => 'MALICIOUS FILE';

  @override
  String get alertSuspiciousFile => 'Suspicious file';

  @override
  String get actionDeleteFile => 'Delete file';

  @override
  String alertDeleteQ(String name) {
    return 'Delete \"$name\"?';
  }

  @override
  String get alertDeleteQBody =>
      'This removes the file from your phone. It cannot be undone.';

  @override
  String get actionYesDelete => 'Yes, delete it';

  @override
  String get alertDeleted => 'File deleted';

  @override
  String alertDeletedBody(String name) {
    return '\"$name\" was removed.';
  }

  @override
  String get alertDeleteFailed => 'Could not delete the file';

  @override
  String get alertDeleteFailedBody =>
      'FraudShield only deletes files in Downloads, WhatsApp and Telegram folders.';

  @override
  String alertLoginCode(String app) {
    return '$app login code received';
  }

  @override
  String alertLoginCodeBody(String app) {
    return 'A $app login code just arrived. If YOU did not just log in, someone is trying to take over your account. NEVER share this code.';
  }

  @override
  String get actionNotMe => 'I did NOT request it';

  @override
  String notifNewDevice(String app) {
    return '$app: new device activity';
  }

  @override
  String get notifNewDeviceBody => 'Open FraudShield to secure your account';

  @override
  String alertNewDevice(String app) {
    return '$app may be accessed from another device';
  }

  @override
  String get alertNewDeviceBody =>
      'A new-device or linked-device notice appeared. Log out all other devices now.';

  @override
  String get alertClone => 'Possible clone / remote access';

  @override
  String get notifFwdOn => 'Call forwarding is ON';

  @override
  String notifFwdOnBody(String condition) {
    return 'Forwarding $condition is active. Scammers use this to steal OTP calls.';
  }

  @override
  String alertFwdOnBody(String condition, String carrier) {
    return 'Forwarding $condition is active.\n\nCarrier says:\n$carrier\n\nScammers use this to steal OTP calls.';
  }

  @override
  String get actionFwdOff => 'Turn OFF forwarding';

  @override
  String get fwdIconNote => '(Android also shows its call forwarding icon.)';

  @override
  String get fwdAll => 'all calls';

  @override
  String get fwdBusy => 'when busy';

  @override
  String get fwdNoAnswer => 'when not answered';

  @override
  String get fwdUnreachable => 'when unreachable';

  @override
  String alertRiskyApp(String label) {
    return 'Risky app: $label';
  }

  @override
  String alertNewRiskyApp(String label) {
    return 'New risky app: $label';
  }

  @override
  String get actionIgnoreApp => 'Ignore this app';

  @override
  String appsRisky(String count) {
    return '$count risky';
  }

  @override
  String get consentTitle => 'Before you start';

  @override
  String get consentIntro =>
      'FraudShield checks your phone for scams. Here is exactly what it does with your data.';

  @override
  String get consentChecks => 'What is checked with our server';

  @override
  String get consentChecksBody =>
      'Web links in your notifications, the fingerprint (not the content) of new downloaded files, apps that show a risky sign, and a scrambled ID of your phone and SIM to detect cloning and SIM swap.';

  @override
  String get consentNever => 'What never leaves your phone';

  @override
  String get consentNeverBody =>
      'The text of your messages and notifications, your contacts, photos, file contents, call log, location and the list of all your apps.';

  @override
  String get consentShared => 'Who else sees data';

  @override
  String get consentSharedBody =>
      'Links are checked with Google Safe Browsing and file fingerprints with VirusTotal. No ads, no analytics, no selling of data.';

  @override
  String get consentDelete => 'You stay in control';

  @override
  String get consentDeleteBody =>
      'Each permission is asked separately and can be turned off. Settings > Delete my account removes all your data at once.';

  @override
  String get privacyPolicy => 'Privacy policy';

  @override
  String get readPrivacyPolicy => 'Read the full privacy policy';

  @override
  String get agreeContinue => 'Agree and continue';

  @override
  String get deleteAccount => 'Delete my account';

  @override
  String get deleteAccountBody =>
      'Removes your account and all data linked to it (threat history, devices, app checks) from our server. This cannot be undone.';

  @override
  String get deleteAccountConfirm => 'Delete everything';

  @override
  String get cancel => 'Cancel';

  @override
  String get accountSection => 'Account and privacy';

  @override
  String get contactSection => 'Emergency contact (optional)';

  @override
  String get contactBody =>
      'Someone you trust. On a critical alert you can tell them with one tap (your SMS app opens with the message ready). Saved only on this phone.';

  @override
  String get contactName => 'Name';

  @override
  String get contactPhone => 'Mobile number';

  @override
  String get contactSave => 'Save contact';

  @override
  String get contactRemove => 'Remove contact';

  @override
  String get contactInvalid => 'Enter a name and a 10-digit mobile number';

  @override
  String contactSaved(String name) {
    return 'Emergency contact: $name';
  }

  @override
  String tellContact(String name) {
    return 'Tell $name';
  }

  @override
  String contactMessage(String title) {
    return 'FraudShield alert on my phone: $title. If anyone (even \"me\") asks you for an OTP, money or a code now, call me first on my number.';
  }

  @override
  String get permFsiTitle => 'Allow full-screen security alerts';

  @override
  String get permFsiBody =>
      'Android 14 and newer only lets alarm and calling apps fill the screen. Allow it so a critical warning (call forwarding ON, account takeover) appears even on the lock screen. Without it you still get a notification.';

  @override
  String fakeSiteTitle(String brand) {
    return 'This looks like $brand but is NOT $brand';
  }

  @override
  String get fakeSiteThisLink => 'This link';

  @override
  String fakeSiteReal(String brand) {
    return 'Real $brand';
  }

  @override
  String get fakeSiteWhy => 'Why we think so';

  @override
  String get fakeSiteAdvice =>
      'Do not enter your password, OTP, PIN or card details on this site. If you already did, call your bank and the cyber-crime helpline 1930 now.';

  @override
  String fakeSiteGoReal(String brand) {
    return 'Go to the real $brand site';
  }

  @override
  String get fakeSiteReport => 'Report';

  @override
  String get fakeSiteReportCyber => 'Report to Cyber Crime (1930)';

  @override
  String get fakeSiteWrong => 'This warning is wrong';

  @override
  String get fakeSiteThanks => 'Thank you - we will check it.';

  @override
  String fakeSiteDiffLabel(String seen, String real) {
    return 'This link $seen is different from the real address $real';
  }

  @override
  String notifFakeSite(String brand) {
    return 'Fake $brand site!';
  }

  @override
  String get whyRisky => 'Why is this risky?';

  @override
  String get vpnTitle => 'Fake-website protection';

  @override
  String get vpnTileOn => 'On - checks the sites every app opens';

  @override
  String get vpnTileOff => 'Off - tap to block fake sites in every app';

  @override
  String get vpnConsentTitle => 'Block fake websites in every app';

  @override
  String get vpnConsentIntro =>
      'FraudShield sets up a small VPN that stays on your phone. When any app or browser looks up a website name, FraudShield checks the name first and blocks known fake sites.';

  @override
  String get vpnSeesTitle => 'What FraudShield sees';

  @override
  String get vpnSees =>
      'Only website names (like flipkart.com) and which app asked for them. Your internet traffic does not go through FraudShield.';

  @override
  String get vpnNotSeesTitle => 'What it never sees';

  @override
  String get vpnNotSees =>
      'Pages, messages, passwords, payments or full links. Nothing is recorded on our server.';

  @override
  String get vpnSentTitle => 'What leaves your phone';

  @override
  String get vpnSent =>
      'Only an unknown website name, to check if it is fake - never linked to you. In local-only mode nothing is sent.';

  @override
  String get vpnConsentNote =>
      'Android will ask you to allow a VPN connection. A key icon appears at the top while protection is on.';

  @override
  String get vpnTurnOn => 'Turn on protection';

  @override
  String get vpnTurnOff => 'Turn off';

  @override
  String get vpnNotNow => 'Not now';

  @override
  String get vpnStateOn => 'Protection is on';

  @override
  String get vpnStateOff => 'Protection is off';

  @override
  String get vpnStateOtherVpn => 'Another VPN is connected';

  @override
  String get vpnStateOtherVpnBody =>
      'Android allows only one VPN at a time. Turn the other VPN off in Android settings, then turn FraudShield on again.';

  @override
  String get vpnStatePrivateDns => 'Private DNS is on';

  @override
  String vpnStatePrivateDnsBody(String name) {
    return 'Your phone sends website names to $name directly, so FraudShield cannot check them. Set Private DNS to \"Automatic\" or \"Off\".';
  }

  @override
  String get vpnStateRevoked => 'Protection was switched off';

  @override
  String get vpnStateRevokedBody =>
      'It was turned off in Android settings or replaced by another VPN. Turn it on again to stay protected.';

  @override
  String get vpnStateNoConsent => 'Allow the VPN prompt to turn protection on.';

  @override
  String get vpnOpenVpnSettings => 'Open VPN settings';

  @override
  String get vpnOpenDnsSettings => 'Open network settings';

  @override
  String vpnStats(int n) {
    return '$n site names checked';
  }

  @override
  String vpnBlockedCount(int n) {
    return '$n fake sites blocked';
  }

  @override
  String vpnListSize(int n) {
    return '$n known fake sites on your phone';
  }

  @override
  String get vpnRecent => 'Recently blocked';

  @override
  String get vpnNoneBlocked => 'Nothing blocked yet.';

  @override
  String get vpnLocalOnly => 'Local-only mode';

  @override
  String get vpnLocalOnlyBody =>
      'Use only the list on the phone; never ask the server about a website.';

  @override
  String get vpnBlockDoh => 'Block hidden DNS services';

  @override
  String get vpnBlockDohBody =>
      'Some browsers look up sites through their own encrypted DNS and skip the check. Blocking it makes them use the checked one.';

  @override
  String get vpnUpstream => 'DNS provider';

  @override
  String get vpnUpstreamSystem => 'Your network\'s (default)';

  @override
  String get vpnTopApps => 'Which apps contact which sites';

  @override
  String get vpnTopAppsBody =>
      'Last 30 days. Data use comes from Android\'s own counters (needs Usage Access).';

  @override
  String vpnSitesCount(int n) {
    return '$n sites';
  }

  @override
  String get vpnNoTraffic =>
      'No data yet - turn protection on and use your phone for a while.';

  @override
  String get vpnKeepOnTitle => 'Keep protection on after a restart';

  @override
  String get vpnKeepOnBody =>
      'Your phone stops apps from starting by themselves. So that protection comes back after a restart or update: 1) in VPN settings tap FraudShield and turn on \"Always-on VPN\"; 2) allow Autostart for FraudShield.';

  @override
  String get vpnAlwaysOn => 'Always-on VPN';

  @override
  String get vpnAutostart => 'Allow Autostart';

  @override
  String get appDetailAccess => 'What it can access';

  @override
  String get appDetailNothing => 'No sensitive access granted.';

  @override
  String get appDetailTrackers => 'Tracking code inside';

  @override
  String get appDetailNoTrackers => 'No known tracking code found.';

  @override
  String get appDetailTalksTo => 'Who it talked to (last 7 days)';

  @override
  String get appDetailTalksToBody =>
      'From the fake-website protection on this phone. Site names never leave your phone.';

  @override
  String get appDetailNoNetwork =>
      'No data - turn on fake-website protection to see this.';

  @override
  String appDetailAdServers(int n) {
    return '$n advertising / tracking servers';
  }

  @override
  String get appDetailMalware => 'Talks to a known malware server!';

  @override
  String get appBlockInternet => 'Block internet';

  @override
  String get appUnblockInternet => 'Allow internet again';

  @override
  String get appBlockedNote =>
      'Internet blocked for this app (while fake-website protection is on). Apps with fixed server addresses may still connect.';

  @override
  String get appRevoke => 'Remove permissions';

  @override
  String appFakeTitle(String name) {
    return 'This is NOT the real $name app';
  }

  @override
  String get appIgnored =>
      'You chose to ignore this app. FraudShield will not remind you.';

  @override
  String get msgTitle => 'Check a message';

  @override
  String get msgIntro =>
      'Paste an SMS or WhatsApp message. FraudShield checks it on your phone - the text does not leave your phone.';

  @override
  String get msgHint => 'Paste the message here';

  @override
  String get msgCheck => 'Check';

  @override
  String get msgSafe => 'No scam signs found';

  @override
  String msgScam(String name) {
    return 'Looks like a $name - do not reply, click or pay';
  }

  @override
  String msgSuspicious(String name) {
    return 'Be careful: this may be a $name';
  }

  @override
  String get msgCatKyc => 'fake bank KYC / account-block scam';

  @override
  String get msgCatPrize => 'fake prize / lottery scam';

  @override
  String get msgCatParcel => 'fake parcel / courier scam';

  @override
  String get msgCatPower => 'fake electricity-disconnection scam';

  @override
  String get msgCatJob => 'fake job / task scam';

  @override
  String get msgCatLoan => 'fake loan scam';

  @override
  String get msgCatArrest => '\"digital arrest\" scam';

  @override
  String get msgCatOtp => 'OTP theft scam';

  @override
  String get msgCatUpi => 'UPI refund / collect scam';

  @override
  String get msgCatInvest => 'fake investment scam';

  @override
  String get msgCatGeneric => 'scam';

  @override
  String msgLinkFake(String name) {
    return 'Fake $name website - do not open';
  }

  @override
  String get msgLinkBad => 'Dangerous link - do not open';

  @override
  String get msgLinkOk => 'No danger found in this link';

  @override
  String get msgLinkUnknown => 'Could not check this link';

  @override
  String get msgAdvice =>
      'Never share an OTP, PIN or password. If you already paid or shared, call your bank and the cyber-crime helpline 1930 now.';

  @override
  String get msgCloudButton => 'Check with cloud AI';

  @override
  String get msgCloudTitle => 'Send this message for a deeper check?';

  @override
  String get msgCloudFirst =>
      'The message text is sent securely (HTTPS) to the FraudShield server for a deeper check. It is used only for this check: it is not stored, not logged and not linked to you. Links in it are checked too. You decide every time - nothing is ever sent without this tap.';

  @override
  String get msgCloudAgain =>
      'This message will be sent to the FraudShield server for a deeper check (not stored, not logged).';

  @override
  String get msgCloudSend => 'Send for check';

  @override
  String get msgCloudDone => 'Includes the cloud check (message not stored).';

  @override
  String get msgCloudFailed =>
      'Cloud check not available right now - the phone\'s own check above still applies.';

  @override
  String get msgDashTile => 'Check a message';

  @override
  String get msgDashTileSub =>
      'Paste an SMS / WhatsApp message to see if it is a scam';

  @override
  String get leftTile => 'Leftover files';

  @override
  String get leftTileSub => 'Find files that removed apps left on your phone';

  @override
  String leftTilePending(int count) {
    return '$count removed app(s) left files - tap to review';
  }

  @override
  String get leftTitle => 'Leftover files';

  @override
  String leftTitleApp(String name) {
    return 'Leftovers of $name';
  }

  @override
  String get leftIntro =>
      'Android removes each app\'s private data by itself. Apps can also save files in shared folders (Pictures, Download, their own folders) - those stay after uninstall. Nothing is removed until you choose and confirm.';

  @override
  String get leftPrivateNote =>
      'FraudShield cannot see other apps\' Android/data folders (Android does not allow it) - Android cleans those itself when an app is removed.';

  @override
  String get leftGroupInstaller => 'App installers (.apk)';

  @override
  String get leftGroupCache => 'Cache and temporary files';

  @override
  String get leftGroupMedia => 'Photos, videos and audio';

  @override
  String get leftGroupDocument => 'Documents';

  @override
  String get leftGroupOther => 'Other files';

  @override
  String get leftWhatInstaller =>
      'Installer file - not needed once the app is installed or removed.';

  @override
  String get leftWhatCache =>
      'Temporary data the app made for itself - safe to remove.';

  @override
  String get leftWhatMedia =>
      'Your photos, videos or audio saved by the app - check before removing.';

  @override
  String get leftWhatDocument =>
      'Documents saved by the app - check before removing.';

  @override
  String get leftWhatOther => 'Files the app saved - check before removing.';

  @override
  String leftFolderInfo(int files, String size) {
    return 'Folder, $files files, $size';
  }

  @override
  String get leftNothing => 'Nothing left behind - all clean.';

  @override
  String get leftRemovedApps => 'Removed apps';

  @override
  String leftRemovedInfo(int files, String size) {
    return '$files files, $size';
  }

  @override
  String get leftOrphans => 'Folders of apps that are no longer installed';

  @override
  String leftOrphanFrom(String name) {
    return 'From $name';
  }

  @override
  String get leftApks => 'Old app installers in Download';

  @override
  String get leftApkSame => 'The same version is installed - not needed';

  @override
  String get leftApkOlder => 'Older than the installed app - not needed';

  @override
  String get leftApkNewer =>
      'Newer than the installed app - an update you have not installed';

  @override
  String get leftApkNotInstalled => 'This app is not installed';

  @override
  String get leftApkFlagged => 'Flagged by file protection - remove it';

  @override
  String get leftApkUnreadable => 'Could not read this installer';

  @override
  String leftDelete(int count, String size) {
    return 'Remove selected ($count, $size)';
  }

  @override
  String leftConfirmTitle(int count) {
    return 'Remove $count item(s)?';
  }

  @override
  String get leftConfirmBody =>
      'Photos, videos and audio go to the phone\'s trash - you can restore them for 30 days. Other files (installers, documents, cache) are deleted. Files of apps that are still installed are always left alone.';

  @override
  String get leftConfirmYes => 'Remove';

  @override
  String get leftCancel => 'Cancel';

  @override
  String get leftKeep => 'Keep all';

  @override
  String leftDone(int trashed, int deleted) {
    return 'Moved $trashed to trash, deleted $deleted.';
  }

  @override
  String leftRefused(int count) {
    return '$count item(s) left alone - they belong to an installed app or a protected folder.';
  }

  @override
  String get leftCancelled => 'Nothing was removed.';

  @override
  String get leftSettingsTitle => 'Nothing left in Settings?';

  @override
  String get leftSetClear => 'No longer listed';

  @override
  String get leftSetLeftover =>
      'Still points to the removed app - open Settings and switch it off';

  @override
  String get leftSetUnknown => 'Android does not let apps check this';

  @override
  String get leftKindAccessibility => 'Accessibility';

  @override
  String get leftKindNotif => 'Notification access';

  @override
  String get leftKindAdmin => 'Device admin';

  @override
  String get leftKindVpn => 'Always-on VPN';

  @override
  String get leftKindSms => 'Default SMS app';

  @override
  String get leftKindDialer => 'Default phone app';

  @override
  String get leftKindBrowser => 'Default browser';

  @override
  String get leftKindHome => 'Default home screen';

  @override
  String get leftNeedAccess =>
      'Allow file access so FraudShield can find leftover files.';

  @override
  String get callTile => 'Call guard';

  @override
  String get callTileOff => 'Off - tap to block scam calls';

  @override
  String callTileOn(int blocked) {
    return 'On - $blocked calls stopped in 30 days';
  }

  @override
  String get callTileUnsupported => 'Needs Android 10 or newer';

  @override
  String get callTitle => 'Call guard';

  @override
  String get callIntro =>
      'FraudShield checks incoming calls from numbers that are not in your contacts - on your phone, without sending the number anywhere.';

  @override
  String get callUnsupported =>
      'Call guard needs Android 10 or newer. You can still report a scam number below.';

  @override
  String get callRoleTitle => 'Let FraudShield screen calls';

  @override
  String get callRoleBody =>
      'Android will ask you to choose FraudShield as the \"Caller ID & spam\" app. Your phone app stays the same.';

  @override
  String get callRoleButton => 'Allow';

  @override
  String get callRoleOn => 'FraudShield is screening calls';

  @override
  String get callModeTitle => 'What should happen to risky calls?';

  @override
  String get callMode1 => 'Only label calls';

  @override
  String get callMode1Sub => 'Never cut a call - just tell me who it might be';

  @override
  String get callMode2 => 'Block known scam numbers (recommended)';

  @override
  String get callMode2Sub =>
      'Numbers many people reported as fraud are rejected';

  @override
  String get callMode3 => 'Block scams, silence risky calls';

  @override
  String get callMode3Sub =>
      'Foreign and unusual numbers ring silently; you still see them';

  @override
  String get callMode4 => 'Reject all unknown numbers';

  @override
  String get callMode4Sub =>
      'Only contacts, numbers you called and your allow list can ring';

  @override
  String get callMode4WarnTitle => 'Reject every unknown caller?';

  @override
  String get callMode4WarnBody =>
      'Delivery agents, doctors, a new school number or a new bank office will NOT ring. They appear in Blocked calls, where you can allow them. Emergency numbers and official 1600 bank calls always ring.';

  @override
  String get callMode4Confirm => 'Yes, reject unknown';

  @override
  String get callCancel => 'Cancel';

  @override
  String get callContactsTitle => 'Never block people you know';

  @override
  String get callContactsBody =>
      'Let FraudShield check your contacts and the numbers you called, on this phone only. Nothing is uploaded.';

  @override
  String get callContactsButton => 'Allow contacts and call history';

  @override
  String get callNotifTitle => 'Allow notifications';

  @override
  String get callNotifBody =>
      'So FraudShield can tell you who called and why a call was stopped.';

  @override
  String get callAllowTitle => 'Always allow';

  @override
  String get callAllowHint => 'Phone number';

  @override
  String get callAllowAdd => 'Add';

  @override
  String get callAllowInvalid => 'Not a valid phone number';

  @override
  String get callAllowEmpty => 'No numbers yet';

  @override
  String get callRemove => 'Remove';

  @override
  String get callBlockTitle => 'Numbers you blocked';

  @override
  String callListInfo(int count) {
    return 'Scam list on this phone: $count numbers';
  }

  @override
  String get callSync => 'Update list';

  @override
  String get callSyncDone => 'List updated';

  @override
  String get callSyncFailed =>
      'Could not update now - the list on the phone still works';

  @override
  String get callBlockedTitle => 'Blocked calls';

  @override
  String get callBlockedSub => 'Last 30 days - kept only on this phone';

  @override
  String get callBlockedEmpty => 'No screened calls in the last 30 days';

  @override
  String get callActionReject => 'Rejected';

  @override
  String get callActionSilence => 'Silenced';

  @override
  String get callActionAllow => 'Rang, with a warning';

  @override
  String get callUndone => 'Allowed by you';

  @override
  String get callNotSpam => 'Not spam';

  @override
  String callNotSpamDone(String number) {
    return 'OK - $number is always allowed now';
  }

  @override
  String get callReport => 'Report';

  @override
  String get callReportTitle => 'Report a number';

  @override
  String get callReportIntro =>
      'When many people report the same number it goes on the scam list for everyone. One report per number per person.';

  @override
  String get callReportNumber => 'Phone number';

  @override
  String get callReportCategory => 'What was it?';

  @override
  String get callCatScam => 'Fraud / scam';

  @override
  String get callCatSpam => 'Spam';

  @override
  String get callCatTele => 'Telemarketing';

  @override
  String get callReportSend => 'Report to FraudShield';

  @override
  String get callReportDone => 'Reported - thank you';

  @override
  String get callReportProtected =>
      'This is an official 1600 / 140 service number. TRAI does not allow these to be reported or blocked.';

  @override
  String get callReportLimit =>
      'You have reported many numbers today - please try again tomorrow';

  @override
  String get callReportFailed =>
      'Could not report now - check your internet and try again';

  @override
  String get callGovTitle => 'Report to the government (Chakshu)';

  @override
  String get callGovBody =>
      'Sanchar Saathi \"Chakshu\" is the Department of Telecommunications\' free portal for reporting fraud calls and SMS. Copy the number, then paste it in the form.';

  @override
  String get callGovCopy => 'Copy number';

  @override
  String get callGovCopied => 'Number copied';

  @override
  String get callGovOpen => 'Open Chakshu';

  @override
  String get callGovMoney =>
      'Lost money? Call 1930 now or report at cybercrime.gov.in';

  @override
  String get blockTitleWeb => 'Ad & tracker blocking';

  @override
  String get blockTitlePlay => 'Tracker & malware blocking';

  @override
  String get blockIntroWeb =>
      'Blocks ads, tracking and malware servers in every app and browser - on your phone, nothing is sent anywhere.';

  @override
  String get blockIntroPlay =>
      'Blocks known tracking and malware servers in every app and browser - on your phone, nothing is sent anywhere.';

  @override
  String get blockAdsNotInPlay =>
      'Ads are not blocked in this version: Google Play rules do not allow blocking other apps\' ads. The version from our website can block ads.';

  @override
  String get blockNeedsVpn =>
      'Works while Fake-website protection (the on-phone VPN) is on.';

  @override
  String get blockCatAds => 'Ads';

  @override
  String get blockCatAdsSub => 'Ad servers in apps and websites';

  @override
  String get blockCatTrackers => 'Trackers';

  @override
  String get blockCatTrackersSub =>
      'Analytics and tracking servers that follow what you do';

  @override
  String get blockCatMalware => 'Malware & scam sites';

  @override
  String get blockCatMalwareSub => 'Known malware, phishing and scam servers';

  @override
  String blockList(int count) {
    return 'Block list: $count sites';
  }

  @override
  String get blockListNone => 'Block list not downloaded yet';

  @override
  String get blockUpdate => 'Update now';

  @override
  String get blockUpdated => 'Block list is up to date';

  @override
  String get blockUpdateFailed => 'Could not update the block list right now';

  @override
  String get blockPause5 => 'Pause blocking for 5 minutes';

  @override
  String blockPausedUntil(String time) {
    return 'Paused until $time';
  }

  @override
  String get blockResume => 'Resume now';

  @override
  String get blockAllowedApps => 'Apps not blocked';

  @override
  String get blockAllowedAppsNone => 'None - blocking applies to every app';

  @override
  String get blockAllowApp => 'Allow an app…';

  @override
  String get blockPickApp => 'Choose the app';

  @override
  String get blockPickAppBody =>
      'Apps seen by the VPN. \"Allow\" stops blocking for that app. \"This app broke\" does the same and keeps a note on this phone (nothing is sent).';

  @override
  String get blockAllow => 'Allow';

  @override
  String get blockAppBroke => 'This app broke';

  @override
  String blockAppBrokeDone(String app) {
    return '$app is no longer blocked. The note stays on this phone.';
  }

  @override
  String get blockRemove => 'Remove';

  @override
  String get blockAllowedSites => 'Sites not blocked';

  @override
  String get blockAddSite => 'Allow a site';

  @override
  String get blockSiteHint => 'example.com';

  @override
  String get blockAdd => 'Add';

  @override
  String get blockStats => 'Blocking statistics';

  @override
  String blockStatsTotal(int count) {
    return '$count requests blocked';
  }

  @override
  String blockStatsSaved(String size) {
    return 'About $size of data saved (estimate)';
  }

  @override
  String get blockStatsByCat => 'By type';

  @override
  String get blockStatsByApp => 'By app';

  @override
  String get blockStatsTop => 'Most blocked sites';

  @override
  String blockStatsDays(int count) {
    return 'Last $count days';
  }

  @override
  String get blockStatsLocal =>
      'Counted on your phone; these numbers never leave it.';

  @override
  String get blockStatsEmpty => 'Nothing blocked yet.';

  @override
  String blockVpnCount(int count) {
    return '$count requests blocked since the VPN started';
  }

  @override
  String get safeTileTitle => 'Scam help';

  @override
  String get safeTileSub =>
      'Live scam guard, check before you pay, recovery kit, drills';

  @override
  String get safeHubTitle => 'Scam help';

  @override
  String get safeGuardTitle => 'Live scam guard';

  @override
  String get safeGuardSub =>
      'STOP warning when a scam call, a screen-sharing app and your bank app come together';

  @override
  String get safeUpiTitle => 'Check before you pay';

  @override
  String get safeUpiSub => 'UPI ID or the text of a UPI QR code';

  @override
  String get safeCareTitle => 'Check a customer-care number';

  @override
  String get safeCareSub => 'Fake helpline numbers are a common trap';

  @override
  String get safeRecoveryTitle => 'I was scammed - what now?';

  @override
  String get safeRecoverySub =>
      'First-hour steps, 1930, block your cards, evidence';

  @override
  String get safeGuardianTitle => 'Guardian';

  @override
  String get safeGuardianSub => 'A family member you can reach in one tap';

  @override
  String get safeDrillsTitle => 'Scam drills';

  @override
  String get safeDrillsSub => 'Practise spotting 5 common scams';

  @override
  String get safeGuardIntro =>
      'Most bank scams follow one pattern: an unknown caller keeps you on the phone, asks you to open a screen-sharing app (AnyDesk, TeamViewer...), then to open your bank app or read out an OTP. When FraudShield sees these together it shows a STOP warning. Only the kind of signal is sent to FraudShield - never the number, the app name or the code.';

  @override
  String get safeGuardSwitch => 'Live scam guard';

  @override
  String get safeOtpSwitch => 'Warn me when an OTP arrives during a call';

  @override
  String get safeOtpSwitchSub =>
      'Works on the phone only; the code is never stored';

  @override
  String get safePermsTitle => 'What the guard needs';

  @override
  String get safePermPhone => 'Phone: is a call going on';

  @override
  String get safePermCallLog =>
      'Caller\'s number - checked against your contacts on the phone';

  @override
  String get safePermContacts => 'Contacts';

  @override
  String get safePermUsage => 'Usage access: which app is open';

  @override
  String get safePermNotif => 'Notification access: an OTP arrived';

  @override
  String get safeAllow => 'Allow';

  @override
  String get safeAllowed => 'Allowed';

  @override
  String get safeTestStop => 'Test the STOP warning';

  @override
  String get safeStopTitle => 'STOP!';

  @override
  String get safeStopHeadline => 'This looks like a scam happening right now';

  @override
  String get safeStopWarnHeadline => 'Careful: this may be a scam';

  @override
  String get safeStopBody =>
      'An unknown caller, a screen-sharing app and your bank app at the same time - this is how bank scams work.';

  @override
  String get safeStopStep1 => 'Hang up the call now.';

  @override
  String get safeStopStep2 => 'Do not share any OTP, PIN or password.';

  @override
  String get safeStopStep3 =>
      'Close and uninstall the screen-sharing app (AnyDesk, TeamViewer...).';

  @override
  String get safeStopStep4 =>
      'If you already paid or shared a code: call 1930 now.';

  @override
  String get safeStopSpeak => 'Read aloud';

  @override
  String get safeNoVoice =>
      'No voice for this language on this phone - please read the steps above.';

  @override
  String get safeCall1930 => 'Call 1930';

  @override
  String get safeOpenRecovery => 'Open recovery kit';

  @override
  String get safeImSafe => 'I am safe - close';

  @override
  String safeCallGuardian(String name) {
    return 'Call $name';
  }

  @override
  String safeTellGuardian(String name) {
    return 'Message $name';
  }

  @override
  String get safeGuardianSms =>
      'FraudShield alert: I may be talking to a scammer right now. Please call me.';

  @override
  String get safeRecTitle => 'Recovery kit';

  @override
  String get safeRecIntro =>
      'Act within the first hour (the \"golden hour\"): the faster you report, the better the chance to freeze the money.';

  @override
  String get safeRecStart => 'I was just scammed - start the clock';

  @override
  String safeRecElapsed(String time) {
    return '$time since you started';
  }

  @override
  String get safeRecReset => 'Reset';

  @override
  String get safeRec1930 => 'Call 1930 - National Cyber Crime Helpline';

  @override
  String get safeRecPortal => 'Report at cybercrime.gov.in';

  @override
  String get safeRecBlockTitle => 'Block your card / account';

  @override
  String get safeRecBlockSub =>
      'Numbers read on each bank\'s own website (Nov 2026). Double-check on your card.';

  @override
  String get safeRecChecklist => 'Checklist';

  @override
  String get safeRecStepCall1930 => 'Called 1930';

  @override
  String get safeRecStepBank => 'Called my bank; blocked card / UPI';

  @override
  String get safeRecStepPasswords =>
      'Changed UPI PIN, net-banking and email passwords';

  @override
  String get safeRecStepRemote => 'Uninstalled screen-sharing apps';

  @override
  String get safeRecStepPortal => 'Filed a complaint at cybercrime.gov.in';

  @override
  String get safeRecStepKeep => 'Saved the complaint / acknowledgement number';

  @override
  String get safeRecEvidence => 'Evidence pack';

  @override
  String get safeRecEvidenceSub =>
      'Write down what happened. It stays on your phone until you share it.';

  @override
  String get safeRecEvWho => 'Scammer\'s number / UPI ID / link';

  @override
  String get safeRecEvMoney => 'Amount, date and time of each payment';

  @override
  String get safeRecEvApps => 'Apps they asked you to install';

  @override
  String get safeRecEvNotes => 'What happened';

  @override
  String get safeRecShare => 'Share evidence';

  @override
  String get safeRecScreens =>
      'Screenshots: open Gallery, select them and share them to your complaint or bank.';

  @override
  String get safeGuardianIntro =>
      'Choose a family member. When FraudShield sees a scam happening, the STOP screen offers one tap to call or message them. Nothing is sent automatically and no message content is shared.';

  @override
  String get safeGuardianName => 'Name';

  @override
  String get safeGuardianNumber => 'Mobile number';

  @override
  String get safeGuardianSave => 'Save guardian';

  @override
  String get safeGuardianRemove => 'Remove guardian';

  @override
  String safeGuardianSaved(String name) {
    return '$name is your guardian';
  }

  @override
  String get safeGuardianInvalid =>
      'Enter a name and a valid Indian mobile number';

  @override
  String get safeGuardianTest => 'Send a test message';

  @override
  String get safeGuardianTestText =>
      'FraudShield test: you are my guardian. If I am ever in a scam call, FraudShield will help me call or message you.';

  @override
  String get safeUpiHint => 'UPI ID (name@bank) or QR text (upi://pay?...)';

  @override
  String get safeUpiQrNote =>
      'To check a QR code: scan it with Google Lens or your camera, copy the text and paste it here.';

  @override
  String get safeCheck => 'Check';

  @override
  String get safeVerdictSafe => 'No warning signs found';

  @override
  String get safeVerdictSusp => 'Be careful';

  @override
  String get safeVerdictBad => 'Do not pay';

  @override
  String safeUpiPayee(String vpa) {
    return 'Pays to: $vpa';
  }

  @override
  String get safeReport => 'Report as scam';

  @override
  String get safeReported => 'Reported - thank you';

  @override
  String get safeReportFailed => 'Could not report right now';

  @override
  String get safeCheckFailed => 'Could not check right now - try again';

  @override
  String get safeCareHint => 'Phone number';

  @override
  String get safeCareBrand => 'Company (optional, e.g. SBI)';

  @override
  String get safeCareOfficial => 'Official number';

  @override
  String get safeCareFake => 'Fake customer-care number';

  @override
  String get safeCareSusp => 'Not an official number';

  @override
  String get safeCareUnknown =>
      'Not in our list - check on the company\'s own website';

  @override
  String get safeCareReal => 'Real numbers';

  @override
  String get safeDrillsIntro =>
      'Short practice rounds. Pick what you would do - there is no right or wrong in front of anyone, just learning.';

  @override
  String safeDrillBest(int score) {
    return 'Best: $score%';
  }

  @override
  String get safeDrillNotPlayed => 'Not played yet';

  @override
  String get safeDrillNext => 'Next';

  @override
  String get safeDrillFinish => 'Finish';

  @override
  String safeDrillResult(int score) {
    return 'You scored $score%';
  }

  @override
  String get safeDrillSafe => 'Safe choice';

  @override
  String get safeDrillRisky => 'Risky choice';

  @override
  String get homeProtected => 'You\'re protected';

  @override
  String homeNeedsAttention(int n) {
    return '$n protections need attention';
  }

  @override
  String get homeProtectedDetail =>
      'All shields are up. FraudShield keeps watching in the background.';

  @override
  String get homeAttentionDetail => 'Turn them on below to stay fully safe.';

  @override
  String get homeQuick => 'Quick actions';

  @override
  String get homeStatus => 'Protection status';

  @override
  String get homeStatusSub => 'Tap a card to check again or turn it on';

  @override
  String get homeTools => 'Tools & help';

  @override
  String get homeAccountSub => 'Stop account takeover and SMS theft';

  @override
  String get homeScamHelp => 'Scam help';

  @override
  String get homeCallGuard => 'Call guard';

  @override
  String get homeCleaner => 'Clean leftovers';

  @override
  String get homeScammed => 'I was scammed';

  @override
  String get ui2LoginPromise =>
      'Stops scams, fake sites and account theft - before they reach you.';

  @override
  String get ui1VpnOptions => 'Privacy & DNS options';

  @override
  String get homeGroupPhone => 'Your phone & apps';

  @override
  String get homeGroupPhoneSub => 'Risky apps, cloning and leftover files';

  @override
  String get homeGroupCalls => 'Calls & messages';

  @override
  String get homeGroupCallsSub => 'Block scam calls, stop SMS theft';

  @override
  String get permTitle => 'Permissions';

  @override
  String permSummary(int allowed, int total) {
    return '$allowed of $total allowed';
  }

  @override
  String permPending(int n) {
    return '$n need your OK - tap to allow';
  }

  @override
  String get permAllDone => 'Everything FraudShield needs is allowed';

  @override
  String get permIntro =>
      'FraudShield asks only for what a protection needs. Tap an item: the exact Android page opens - switch it on and come back.';

  @override
  String get permNeedsOk => 'Needs your OK';

  @override
  String get permAllowed => 'Allowed';

  @override
  String get permByHand => 'Check in phone settings';

  @override
  String get permOptional => 'Optional';

  @override
  String get permAllow => 'Allow';

  @override
  String get permOpen => 'Open';

  @override
  String get permOn => 'On';

  @override
  String get permNameNotifications => 'Notifications';

  @override
  String get permWhyNotifications => 'So scam warnings can reach you.';

  @override
  String get permNameFullScreen => 'Full-screen alerts';

  @override
  String get permWhyFullScreen =>
      'Critical warnings show even on the lock screen.';

  @override
  String get permNameNotifAccess => 'Notification access';

  @override
  String get permWhyNotifAccess =>
      'Checks links in your messages and notifications. Message text is never uploaded.';

  @override
  String get permNameFiles => 'All files access';

  @override
  String get permWhyFiles => 'Scans downloaded APK and PDF files for danger.';

  @override
  String get permNamePhone => 'Phone';

  @override
  String get permWhyPhone =>
      'Checks call forwarding and powers the scam alarm.';

  @override
  String get permNameVpn => 'Fake-website protection (VPN)';

  @override
  String get permWhyVpn => 'Blocks fake and dangerous sites in every app.';

  @override
  String get permNameCallScreening => 'Caller ID & spam';

  @override
  String get permWhyCallScreening => 'Lets Call guard stop scam calls.';

  @override
  String get permNameContacts => 'Contacts';

  @override
  String get permWhyContacts => 'Your saved contacts are never blocked.';

  @override
  String get permNameCallLog => 'Call history';

  @override
  String get permWhyCallLog => 'People you called are never blocked.';

  @override
  String get permNameUsage => 'Usage access';

  @override
  String get permWhyUsage => 'Notices apps that open by themselves.';

  @override
  String get permNameBattery => 'Battery: no restrictions';

  @override
  String get permWhyBattery => 'Keeps protection running in the background.';

  @override
  String get permNameAutostart => 'Autostart (phone maker setting)';

  @override
  String get permWhyAutostart =>
      'Lets protection start again after a restart. Android cannot show us this one - please check it.';
}
