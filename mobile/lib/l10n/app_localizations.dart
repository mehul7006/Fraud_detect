import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_hi.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('hi')
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'FraudShield'**
  String get appTitle;

  /// No description provided for @threatHistory.
  ///
  /// In en, this message translates to:
  /// **'Threat history'**
  String get threatHistory;

  /// No description provided for @linkSettings.
  ///
  /// In en, this message translates to:
  /// **'Link settings'**
  String get linkSettings;

  /// No description provided for @logOut.
  ///
  /// In en, this message translates to:
  /// **'Log out'**
  String get logOut;

  /// No description provided for @allow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get allow;

  /// No description provided for @grantAccess.
  ///
  /// In en, this message translates to:
  /// **'Grant access'**
  String get grantAccess;

  /// No description provided for @permNotifTitle.
  ///
  /// In en, this message translates to:
  /// **'Allow notifications'**
  String get permNotifTitle;

  /// No description provided for @permNotifBody.
  ///
  /// In en, this message translates to:
  /// **'Android 13+ blocks all alerts until you allow notifications. Without it FraudShield cannot warn you.'**
  String get permNotifBody;

  /// No description provided for @permLinkTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable link & ad monitoring'**
  String get permLinkTitle;

  /// No description provided for @permLinkBody.
  ///
  /// In en, this message translates to:
  /// **'Grant Notification Access so FraudShield can scan links sent via WhatsApp, Telegram, SMS and count ad spam. Message text is never uploaded.'**
  String get permLinkBody;

  /// No description provided for @permFilesTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable file protection'**
  String get permFilesTitle;

  /// No description provided for @permFilesBody.
  ///
  /// In en, this message translates to:
  /// **'Allow storage access (\"All files access\" on Android 11 and newer) so FraudShield can scan downloaded APK/PDF files. Only hash and file name are sent.'**
  String get permFilesBody;

  /// No description provided for @permPhoneTitle.
  ///
  /// In en, this message translates to:
  /// **'Phone permission'**
  String get permPhoneTitle;

  /// No description provided for @permPhoneBody.
  ///
  /// In en, this message translates to:
  /// **'Needed to check call forwarding with your carrier.'**
  String get permPhoneBody;

  /// No description provided for @permUsageTitle.
  ///
  /// In en, this message translates to:
  /// **'Find apps that open by themselves (optional)'**
  String get permUsageTitle;

  /// No description provided for @permUsageBody.
  ///
  /// In en, this message translates to:
  /// **'Allow Usage Access so FraudShield can notice apps that pop up without you tapping them. Only a count per app is kept; nothing leaves your phone except that count.'**
  String get permUsageBody;

  /// No description provided for @offlineTitle.
  ///
  /// In en, this message translates to:
  /// **'Can\'t reach the FraudShield server'**
  String get offlineTitle;

  /// No description provided for @offlineBody.
  ///
  /// In en, this message translates to:
  /// **'Link and file checks are kept and retried automatically. Tap a tile to try again.'**
  String get offlineBody;

  /// No description provided for @couldntCheck.
  ///
  /// In en, this message translates to:
  /// **'Couldn\'t check right now - tap to retry'**
  String get couldntCheck;

  /// No description provided for @checking.
  ///
  /// In en, this message translates to:
  /// **'checking...'**
  String get checking;

  /// No description provided for @callForwarding.
  ///
  /// In en, this message translates to:
  /// **'Call forwarding'**
  String get callForwarding;

  /// No description provided for @cloneRemote.
  ///
  /// In en, this message translates to:
  /// **'Clone / remote access'**
  String get cloneRemote;

  /// No description provided for @riskyApps.
  ///
  /// In en, this message translates to:
  /// **'Risky / ad-spam apps'**
  String get riskyApps;

  /// No description provided for @linkProtection.
  ///
  /// In en, this message translates to:
  /// **'Link protection'**
  String get linkProtection;

  /// No description provided for @fileProtection.
  ///
  /// In en, this message translates to:
  /// **'File protection'**
  String get fileProtection;

  /// No description provided for @statusOn.
  ///
  /// In en, this message translates to:
  /// **'ON'**
  String get statusOn;

  /// No description provided for @statusOff.
  ///
  /// In en, this message translates to:
  /// **'OFF'**
  String get statusOff;

  /// No description provided for @statusActive.
  ///
  /// In en, this message translates to:
  /// **'ACTIVE'**
  String get statusActive;

  /// No description provided for @statusOk.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get statusOk;

  /// No description provided for @statusRisk.
  ///
  /// In en, this message translates to:
  /// **'RISK {score}'**
  String statusRisk(String score);

  /// No description provided for @fwdUnavailable.
  ///
  /// In en, this message translates to:
  /// **'UNAVAILABLE - can\'t query your carrier from the app'**
  String get fwdUnavailable;

  /// No description provided for @fwdUnknown.
  ///
  /// In en, this message translates to:
  /// **'UNKNOWN - carrier answer not understood'**
  String get fwdUnknown;

  /// No description provided for @fwdCarrierSaid.
  ///
  /// In en, this message translates to:
  /// **'UNKNOWN - carrier answer not understood\nCarrier said: \"{text}\"'**
  String fwdCarrierSaid(String text);

  /// No description provided for @checkManually.
  ///
  /// In en, this message translates to:
  /// **'Check manually'**
  String get checkManually;

  /// No description provided for @carrierReplies.
  ///
  /// In en, this message translates to:
  /// **'Carrier replies'**
  String get carrierReplies;

  /// No description provided for @carrierRepliesBody.
  ///
  /// In en, this message translates to:
  /// **'Phone numbers are masked. Nothing was sent.\nCopy this and add your carrier name and whether forwarding was ON.\n\n{json}'**
  String carrierRepliesBody(String json);

  /// No description provided for @copy.
  ///
  /// In en, this message translates to:
  /// **'Copy'**
  String get copy;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @accountSecurity.
  ///
  /// In en, this message translates to:
  /// **'Account security'**
  String get accountSecurity;

  /// No description provided for @secureWhatsApp.
  ///
  /// In en, this message translates to:
  /// **'Secure WhatsApp'**
  String get secureWhatsApp;

  /// No description provided for @secureWhatsAppSub.
  ///
  /// In en, this message translates to:
  /// **'Log out all other devices'**
  String get secureWhatsAppSub;

  /// No description provided for @secureTelegram.
  ///
  /// In en, this message translates to:
  /// **'Secure Telegram'**
  String get secureTelegram;

  /// No description provided for @secureTelegramSub.
  ///
  /// In en, this message translates to:
  /// **'Terminate other sessions'**
  String get secureTelegramSub;

  /// No description provided for @smsForwarding.
  ///
  /// In en, this message translates to:
  /// **'SMS forwarding'**
  String get smsForwarding;

  /// No description provided for @smsForwardingSub.
  ///
  /// In en, this message translates to:
  /// **'Cannot be read by apps - see how to check'**
  String get smsForwardingSub;

  /// No description provided for @smsReaders.
  ///
  /// In en, this message translates to:
  /// **'Apps that can read my SMS'**
  String get smsReaders;

  /// No description provided for @smsReadersSub.
  ///
  /// In en, this message translates to:
  /// **'Find OTP-stealing apps'**
  String get smsReadersSub;

  /// No description provided for @secureAccount.
  ///
  /// In en, this message translates to:
  /// **'Secure account'**
  String get secureAccount;

  /// No description provided for @iUnderstand.
  ///
  /// In en, this message translates to:
  /// **'I understand'**
  String get iUnderstand;

  /// No description provided for @ignore.
  ///
  /// In en, this message translates to:
  /// **'Ignore'**
  String get ignore;

  /// No description provided for @email.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get email;

  /// No description provided for @passwordHint.
  ///
  /// In en, this message translates to:
  /// **'Password (min 8)'**
  String get passwordHint;

  /// No description provided for @login.
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get login;

  /// No description provided for @createAccount.
  ///
  /// In en, this message translates to:
  /// **'Create account'**
  String get createAccount;

  /// No description provided for @haveAccount.
  ///
  /// In en, this message translates to:
  /// **'Have an account? Login'**
  String get haveAccount;

  /// No description provided for @newHere.
  ///
  /// In en, this message translates to:
  /// **'New here? Register'**
  String get newHere;

  /// No description provided for @signingIn.
  ///
  /// In en, this message translates to:
  /// **'Signing in'**
  String get signingIn;

  /// No description provided for @wrongPassword.
  ///
  /// In en, this message translates to:
  /// **'Wrong email or password'**
  String get wrongPassword;

  /// No description provided for @emailTaken.
  ///
  /// In en, this message translates to:
  /// **'Email already registered'**
  String get emailTaken;

  /// No description provided for @errOffline.
  ///
  /// In en, this message translates to:
  /// **'No internet connection. FraudShield keeps watching; checks that need the server will retry automatically.'**
  String get errOffline;

  /// No description provided for @errTooMany.
  ///
  /// In en, this message translates to:
  /// **'Too many attempts. Please wait a minute and try again.'**
  String get errTooMany;

  /// No description provided for @errSession.
  ///
  /// In en, this message translates to:
  /// **'Your session has ended. Please log in again.'**
  String get errSession;

  /// No description provided for @errServer.
  ///
  /// In en, this message translates to:
  /// **'FraudShield server problem. Please try again in a few minutes.'**
  String get errServer;

  /// No description provided for @errGeneric.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong. Please try again.'**
  String get errGeneric;

  /// No description provided for @tryAgain.
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get tryAgain;

  /// No description provided for @noThreats.
  ///
  /// In en, this message translates to:
  /// **'No threats found. You are safe.'**
  String get noThreats;

  /// No description provided for @resolve.
  ///
  /// In en, this message translates to:
  /// **'Resolve'**
  String get resolve;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @languageSystem.
  ///
  /// In en, this message translates to:
  /// **'Phone default'**
  String get languageSystem;

  /// No description provided for @languageEnglish.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get languageEnglish;

  /// No description provided for @languageHindi.
  ///
  /// In en, this message translates to:
  /// **'हिन्दी (Hindi)'**
  String get languageHindi;

  /// No description provided for @wizTitle.
  ///
  /// In en, this message translates to:
  /// **'Secure {app}'**
  String wizTitle(String app);

  /// No description provided for @wizWarning.
  ///
  /// In en, this message translates to:
  /// **'Apps cannot log out other devices for you - only you can do it inside the app. Follow these steps now.'**
  String get wizWarning;

  /// No description provided for @wizOpenNow.
  ///
  /// In en, this message translates to:
  /// **'Open {app} now'**
  String wizOpenNow(String app);

  /// No description provided for @wizNotInstalled.
  ///
  /// In en, this message translates to:
  /// **'App not installed'**
  String get wizNotInstalled;

  /// No description provided for @waStep1.
  ///
  /// In en, this message translates to:
  /// **'Open WhatsApp > tap the 3 dots > Linked devices.'**
  String get waStep1;

  /// No description provided for @waStep2.
  ///
  /// In en, this message translates to:
  /// **'Tap every device you do not recognise > Log out. (Or: Log out from all devices.)'**
  String get waStep2;

  /// No description provided for @waStep3.
  ///
  /// In en, this message translates to:
  /// **'Settings > Account > Two-step verification > Turn on and set a PIN + recovery email.'**
  String get waStep3;

  /// No description provided for @waStep4.
  ///
  /// In en, this message translates to:
  /// **'Settings > Account > Passkeys / Email address: add an email so only you can recover the account.'**
  String get waStep4;

  /// No description provided for @waStep5.
  ///
  /// In en, this message translates to:
  /// **'Never share the 6-digit code received by SMS. WhatsApp support never asks for it.'**
  String get waStep5;

  /// No description provided for @waStep6.
  ///
  /// In en, this message translates to:
  /// **'If you are locked out: Uninstall > reinstall > register your number again. This logs the attacker out.'**
  String get waStep6;

  /// No description provided for @tgStep1.
  ///
  /// In en, this message translates to:
  /// **'Open Telegram > Settings > Devices.'**
  String get tgStep1;

  /// No description provided for @tgStep2.
  ///
  /// In en, this message translates to:
  /// **'Tap \"Terminate all other sessions\".'**
  String get tgStep2;

  /// No description provided for @tgStep3.
  ///
  /// In en, this message translates to:
  /// **'Settings > Privacy and Security > Two-Step Verification > set a password + recovery email.'**
  String get tgStep3;

  /// No description provided for @tgStep4.
  ///
  /// In en, this message translates to:
  /// **'Settings > Privacy and Security > Active Websites: disconnect unknown ones.'**
  String get tgStep4;

  /// No description provided for @tgStep5.
  ///
  /// In en, this message translates to:
  /// **'Settings > Privacy and Security > Passcode Lock: enable.'**
  String get tgStep5;

  /// No description provided for @tgStep6.
  ///
  /// In en, this message translates to:
  /// **'Never share Telegram login codes.'**
  String get tgStep6;

  /// No description provided for @smsFwdTitle.
  ///
  /// In en, this message translates to:
  /// **'Check SMS forwarding'**
  String get smsFwdTitle;

  /// No description provided for @smsFwdIntro.
  ///
  /// In en, this message translates to:
  /// **'Apps cannot see whether your carrier forwards your SMS. Follow these three checks.'**
  String get smsFwdIntro;

  /// No description provided for @smsFwd1Title.
  ///
  /// In en, this message translates to:
  /// **'Remove apps that can read your SMS'**
  String get smsFwd1Title;

  /// No description provided for @smsFwd1Body.
  ///
  /// In en, this message translates to:
  /// **'Scam \"SMS forwarder\" apps copy your OTPs to another phone.'**
  String get smsFwd1Body;

  /// No description provided for @smsFwd1Button.
  ///
  /// In en, this message translates to:
  /// **'Show apps that read my SMS'**
  String get smsFwd1Button;

  /// No description provided for @smsFwd2Title.
  ///
  /// In en, this message translates to:
  /// **'Ask your carrier'**
  String get smsFwd2Title;

  /// No description provided for @smsFwd2Body.
  ///
  /// In en, this message translates to:
  /// **'Call customer care from your own number and ask: \"Is SMS forwarding or SMS redirect active on my number?\" Ask them to switch it off and to tell you if the SIM was duplicated or replaced.'**
  String get smsFwd2Body;

  /// No description provided for @smsFwd3Title.
  ///
  /// In en, this message translates to:
  /// **'Watch for a SIM swap'**
  String get smsFwd3Title;

  /// No description provided for @smsFwd3Body.
  ///
  /// In en, this message translates to:
  /// **'If your phone suddenly shows \"No service\" for no reason, call the carrier at once from another phone. A swapped SIM receives all your SMS and calls.'**
  String get smsFwd3Body;

  /// No description provided for @smsAuditTitle.
  ///
  /// In en, this message translates to:
  /// **'Apps that can read your SMS'**
  String get smsAuditTitle;

  /// No description provided for @smsAuditError.
  ///
  /// In en, this message translates to:
  /// **'Could not read installed apps'**
  String get smsAuditError;

  /// No description provided for @smsAuditIntro.
  ///
  /// In en, this message translates to:
  /// **'If you do not recognise an app below, uninstall it. An unknown app that can read SMS can forward your OTPs.'**
  String get smsAuditIntro;

  /// No description provided for @smsAuditUser.
  ///
  /// In en, this message translates to:
  /// **'Installed by you ({count})'**
  String smsAuditUser(String count);

  /// No description provided for @smsAuditNoneUser.
  ///
  /// In en, this message translates to:
  /// **'No user-installed app can read your SMS'**
  String get smsAuditNoneUser;

  /// No description provided for @smsAuditSystem.
  ///
  /// In en, this message translates to:
  /// **'System apps - usually safe ({count})'**
  String smsAuditSystem(String count);

  /// No description provided for @readsSms.
  ///
  /// In en, this message translates to:
  /// **'Reads SMS'**
  String get readsSms;

  /// No description provided for @readsNotifications.
  ///
  /// In en, this message translates to:
  /// **'Reads notifications'**
  String get readsNotifications;

  /// No description provided for @uninstall.
  ///
  /// In en, this message translates to:
  /// **'Uninstall'**
  String get uninstall;

  /// No description provided for @riskyTitle.
  ///
  /// In en, this message translates to:
  /// **'Risky apps'**
  String get riskyTitle;

  /// No description provided for @riskyFailedTitle.
  ///
  /// In en, this message translates to:
  /// **'Could not check your apps'**
  String get riskyFailedTitle;

  /// No description provided for @riskyFailedBody.
  ///
  /// In en, this message translates to:
  /// **'Check your internet connection and pull down to try again.'**
  String get riskyFailedBody;

  /// No description provided for @riskyCheckingTitle.
  ///
  /// In en, this message translates to:
  /// **'Checking your apps...'**
  String get riskyCheckingTitle;

  /// No description provided for @riskyCheckingBody.
  ///
  /// In en, this message translates to:
  /// **'This takes a few seconds.'**
  String get riskyCheckingBody;

  /// No description provided for @riskyNoneTitle.
  ///
  /// In en, this message translates to:
  /// **'No risky apps found'**
  String get riskyNoneTitle;

  /// No description provided for @riskyNoneBody.
  ///
  /// In en, this message translates to:
  /// **'Pull down to check again.'**
  String get riskyNoneBody;

  /// No description provided for @riskyShareTitle.
  ///
  /// In en, this message translates to:
  /// **'Help improve risky-app detection'**
  String get riskyShareTitle;

  /// No description provided for @riskyShareBody.
  ///
  /// In en, this message translates to:
  /// **'When you Uninstall or Ignore a flagged app, send its risk signals (notification count, permissions it can use) and your choice. No messages, no contacts, no list of your other apps.'**
  String get riskyShareBody;

  /// No description provided for @newBadge.
  ///
  /// In en, this message translates to:
  /// **'NEW'**
  String get newBadge;

  /// No description provided for @trustedSites.
  ///
  /// In en, this message translates to:
  /// **'Trusted websites'**
  String get trustedSites;

  /// No description provided for @trustedSitesBody.
  ///
  /// In en, this message translates to:
  /// **'Links to these sites (and their sub-domains) are never scanned or flagged.'**
  String get trustedSitesBody;

  /// No description provided for @domain.
  ///
  /// In en, this message translates to:
  /// **'Domain'**
  String get domain;

  /// No description provided for @domainInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter a domain like example.com'**
  String get domainInvalid;

  /// No description provided for @trustDomain.
  ///
  /// In en, this message translates to:
  /// **'Trust this domain'**
  String get trustDomain;

  /// No description provided for @removeDomain.
  ///
  /// In en, this message translates to:
  /// **'Remove {domain}'**
  String removeDomain(String domain);

  /// No description provided for @appsToWatch.
  ///
  /// In en, this message translates to:
  /// **'Apps to watch'**
  String get appsToWatch;

  /// No description provided for @appsToWatchBody.
  ///
  /// In en, this message translates to:
  /// **'Turn an app off and links shown in its notifications are ignored.'**
  String get appsToWatchBody;

  /// No description provided for @noAppsFound.
  ///
  /// In en, this message translates to:
  /// **'No apps found.'**
  String get noAppsFound;

  /// No description provided for @alertNoDanger.
  ///
  /// In en, this message translates to:
  /// **'No known danger found'**
  String get alertNoDanger;

  /// No description provided for @alertNoDangerBody.
  ///
  /// In en, this message translates to:
  /// **'{url}\n\nStill be careful with links you did not expect.'**
  String alertNoDangerBody(String url);

  /// No description provided for @notifDangerous.
  ///
  /// In en, this message translates to:
  /// **'Dangerous link!'**
  String get notifDangerous;

  /// No description provided for @alertSuspiciousLink.
  ///
  /// In en, this message translates to:
  /// **'Suspicious link'**
  String get alertSuspiciousLink;

  /// No description provided for @alertDangerous.
  ///
  /// In en, this message translates to:
  /// **'DANGEROUS LINK - do not open'**
  String get alertDangerous;

  /// No description provided for @alertCheckFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not check the link'**
  String get alertCheckFailed;

  /// No description provided for @alertCheckFailedBody.
  ///
  /// In en, this message translates to:
  /// **'{url}\n\nNo connection to FraudShield. Try again in a moment.'**
  String alertCheckFailedBody(String url);

  /// No description provided for @alertNoLink.
  ///
  /// In en, this message translates to:
  /// **'No link found'**
  String get alertNoLink;

  /// No description provided for @alertNoLinkBody.
  ///
  /// In en, this message translates to:
  /// **'The text you shared does not contain a web link.'**
  String get alertNoLinkBody;

  /// No description provided for @notifSuspiciousFile.
  ///
  /// In en, this message translates to:
  /// **'Suspicious file received'**
  String get notifSuspiciousFile;

  /// No description provided for @alertMaliciousFile.
  ///
  /// In en, this message translates to:
  /// **'MALICIOUS FILE'**
  String get alertMaliciousFile;

  /// No description provided for @alertSuspiciousFile.
  ///
  /// In en, this message translates to:
  /// **'Suspicious file'**
  String get alertSuspiciousFile;

  /// No description provided for @actionDeleteFile.
  ///
  /// In en, this message translates to:
  /// **'Delete file'**
  String get actionDeleteFile;

  /// No description provided for @alertDeleteQ.
  ///
  /// In en, this message translates to:
  /// **'Delete \"{name}\"?'**
  String alertDeleteQ(String name);

  /// No description provided for @alertDeleteQBody.
  ///
  /// In en, this message translates to:
  /// **'This removes the file from your phone. It cannot be undone.'**
  String get alertDeleteQBody;

  /// No description provided for @actionYesDelete.
  ///
  /// In en, this message translates to:
  /// **'Yes, delete it'**
  String get actionYesDelete;

  /// No description provided for @alertDeleted.
  ///
  /// In en, this message translates to:
  /// **'File deleted'**
  String get alertDeleted;

  /// No description provided for @alertDeletedBody.
  ///
  /// In en, this message translates to:
  /// **'\"{name}\" was removed.'**
  String alertDeletedBody(String name);

  /// No description provided for @alertDeleteFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not delete the file'**
  String get alertDeleteFailed;

  /// No description provided for @alertDeleteFailedBody.
  ///
  /// In en, this message translates to:
  /// **'FraudShield only deletes files in Downloads, WhatsApp and Telegram folders.'**
  String get alertDeleteFailedBody;

  /// No description provided for @alertLoginCode.
  ///
  /// In en, this message translates to:
  /// **'{app} login code received'**
  String alertLoginCode(String app);

  /// No description provided for @alertLoginCodeBody.
  ///
  /// In en, this message translates to:
  /// **'A {app} login code just arrived. If YOU did not just log in, someone is trying to take over your account. NEVER share this code.'**
  String alertLoginCodeBody(String app);

  /// No description provided for @actionNotMe.
  ///
  /// In en, this message translates to:
  /// **'I did NOT request it'**
  String get actionNotMe;

  /// No description provided for @notifNewDevice.
  ///
  /// In en, this message translates to:
  /// **'{app}: new device activity'**
  String notifNewDevice(String app);

  /// No description provided for @notifNewDeviceBody.
  ///
  /// In en, this message translates to:
  /// **'Open FraudShield to secure your account'**
  String get notifNewDeviceBody;

  /// No description provided for @alertNewDevice.
  ///
  /// In en, this message translates to:
  /// **'{app} may be accessed from another device'**
  String alertNewDevice(String app);

  /// No description provided for @alertNewDeviceBody.
  ///
  /// In en, this message translates to:
  /// **'A new-device or linked-device notice appeared. Log out all other devices now.'**
  String get alertNewDeviceBody;

  /// No description provided for @alertClone.
  ///
  /// In en, this message translates to:
  /// **'Possible clone / remote access'**
  String get alertClone;

  /// No description provided for @notifFwdOn.
  ///
  /// In en, this message translates to:
  /// **'Call forwarding is ON'**
  String get notifFwdOn;

  /// No description provided for @notifFwdOnBody.
  ///
  /// In en, this message translates to:
  /// **'Forwarding {condition} is active. Scammers use this to steal OTP calls.'**
  String notifFwdOnBody(String condition);

  /// No description provided for @alertFwdOnBody.
  ///
  /// In en, this message translates to:
  /// **'Forwarding {condition} is active.\n\nCarrier says:\n{carrier}\n\nScammers use this to steal OTP calls.'**
  String alertFwdOnBody(String condition, String carrier);

  /// No description provided for @actionFwdOff.
  ///
  /// In en, this message translates to:
  /// **'Turn OFF forwarding'**
  String get actionFwdOff;

  /// No description provided for @fwdIconNote.
  ///
  /// In en, this message translates to:
  /// **'(Android also shows its call forwarding icon.)'**
  String get fwdIconNote;

  /// No description provided for @fwdAll.
  ///
  /// In en, this message translates to:
  /// **'all calls'**
  String get fwdAll;

  /// No description provided for @fwdBusy.
  ///
  /// In en, this message translates to:
  /// **'when busy'**
  String get fwdBusy;

  /// No description provided for @fwdNoAnswer.
  ///
  /// In en, this message translates to:
  /// **'when not answered'**
  String get fwdNoAnswer;

  /// No description provided for @fwdUnreachable.
  ///
  /// In en, this message translates to:
  /// **'when unreachable'**
  String get fwdUnreachable;

  /// No description provided for @alertRiskyApp.
  ///
  /// In en, this message translates to:
  /// **'Risky app: {label}'**
  String alertRiskyApp(String label);

  /// No description provided for @alertNewRiskyApp.
  ///
  /// In en, this message translates to:
  /// **'New risky app: {label}'**
  String alertNewRiskyApp(String label);

  /// No description provided for @actionIgnoreApp.
  ///
  /// In en, this message translates to:
  /// **'Ignore this app'**
  String get actionIgnoreApp;

  /// No description provided for @appsRisky.
  ///
  /// In en, this message translates to:
  /// **'{count} risky'**
  String appsRisky(String count);

  /// No description provided for @consentTitle.
  ///
  /// In en, this message translates to:
  /// **'Before you start'**
  String get consentTitle;

  /// No description provided for @consentIntro.
  ///
  /// In en, this message translates to:
  /// **'FraudShield checks your phone for scams. Here is exactly what it does with your data.'**
  String get consentIntro;

  /// No description provided for @consentChecks.
  ///
  /// In en, this message translates to:
  /// **'What is checked with our server'**
  String get consentChecks;

  /// No description provided for @consentChecksBody.
  ///
  /// In en, this message translates to:
  /// **'Web links in your notifications, the fingerprint (not the content) of new downloaded files, apps that show a risky sign, and a scrambled ID of your phone and SIM to detect cloning and SIM swap.'**
  String get consentChecksBody;

  /// No description provided for @consentNever.
  ///
  /// In en, this message translates to:
  /// **'What never leaves your phone'**
  String get consentNever;

  /// No description provided for @consentNeverBody.
  ///
  /// In en, this message translates to:
  /// **'The text of your messages and notifications, your contacts, photos, file contents, call log, location and the list of all your apps.'**
  String get consentNeverBody;

  /// No description provided for @consentShared.
  ///
  /// In en, this message translates to:
  /// **'Who else sees data'**
  String get consentShared;

  /// No description provided for @consentSharedBody.
  ///
  /// In en, this message translates to:
  /// **'Links are checked with Google Safe Browsing and file fingerprints with VirusTotal. No ads, no analytics, no selling of data.'**
  String get consentSharedBody;

  /// No description provided for @consentDelete.
  ///
  /// In en, this message translates to:
  /// **'You stay in control'**
  String get consentDelete;

  /// No description provided for @consentDeleteBody.
  ///
  /// In en, this message translates to:
  /// **'Each permission is asked separately and can be turned off. Settings > Delete my account removes all your data at once.'**
  String get consentDeleteBody;

  /// No description provided for @privacyPolicy.
  ///
  /// In en, this message translates to:
  /// **'Privacy policy'**
  String get privacyPolicy;

  /// No description provided for @readPrivacyPolicy.
  ///
  /// In en, this message translates to:
  /// **'Read the full privacy policy'**
  String get readPrivacyPolicy;

  /// No description provided for @agreeContinue.
  ///
  /// In en, this message translates to:
  /// **'Agree and continue'**
  String get agreeContinue;

  /// No description provided for @deleteAccount.
  ///
  /// In en, this message translates to:
  /// **'Delete my account'**
  String get deleteAccount;

  /// No description provided for @deleteAccountBody.
  ///
  /// In en, this message translates to:
  /// **'Removes your account and all data linked to it (threat history, devices, app checks) from our server. This cannot be undone.'**
  String get deleteAccountBody;

  /// No description provided for @deleteAccountConfirm.
  ///
  /// In en, this message translates to:
  /// **'Delete everything'**
  String get deleteAccountConfirm;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @accountSection.
  ///
  /// In en, this message translates to:
  /// **'Account and privacy'**
  String get accountSection;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'hi'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'hi':
      return AppLocalizationsHi();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
