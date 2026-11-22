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
}
