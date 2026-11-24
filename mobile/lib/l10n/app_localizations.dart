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

  /// No description provided for @contactSection.
  ///
  /// In en, this message translates to:
  /// **'Emergency contact (optional)'**
  String get contactSection;

  /// No description provided for @contactBody.
  ///
  /// In en, this message translates to:
  /// **'Someone you trust. On a critical alert you can tell them with one tap (your SMS app opens with the message ready). Saved only on this phone.'**
  String get contactBody;

  /// No description provided for @contactName.
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get contactName;

  /// No description provided for @contactPhone.
  ///
  /// In en, this message translates to:
  /// **'Mobile number'**
  String get contactPhone;

  /// No description provided for @contactSave.
  ///
  /// In en, this message translates to:
  /// **'Save contact'**
  String get contactSave;

  /// No description provided for @contactRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove contact'**
  String get contactRemove;

  /// No description provided for @contactInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter a name and a 10-digit mobile number'**
  String get contactInvalid;

  /// No description provided for @contactSaved.
  ///
  /// In en, this message translates to:
  /// **'Emergency contact: {name}'**
  String contactSaved(String name);

  /// No description provided for @tellContact.
  ///
  /// In en, this message translates to:
  /// **'Tell {name}'**
  String tellContact(String name);

  /// No description provided for @contactMessage.
  ///
  /// In en, this message translates to:
  /// **'FraudShield alert on my phone: {title}. If anyone (even \"me\") asks you for an OTP, money or a code now, call me first on my number.'**
  String contactMessage(String title);

  /// No description provided for @permFsiTitle.
  ///
  /// In en, this message translates to:
  /// **'Allow full-screen security alerts'**
  String get permFsiTitle;

  /// No description provided for @permFsiBody.
  ///
  /// In en, this message translates to:
  /// **'Android 14 and newer only lets alarm and calling apps fill the screen. Allow it so a critical warning (call forwarding ON, account takeover) appears even on the lock screen. Without it you still get a notification.'**
  String get permFsiBody;

  /// No description provided for @fakeSiteTitle.
  ///
  /// In en, this message translates to:
  /// **'This looks like {brand} but is NOT {brand}'**
  String fakeSiteTitle(String brand);

  /// No description provided for @fakeSiteThisLink.
  ///
  /// In en, this message translates to:
  /// **'This link'**
  String get fakeSiteThisLink;

  /// No description provided for @fakeSiteReal.
  ///
  /// In en, this message translates to:
  /// **'Real {brand}'**
  String fakeSiteReal(String brand);

  /// No description provided for @fakeSiteWhy.
  ///
  /// In en, this message translates to:
  /// **'Why we think so'**
  String get fakeSiteWhy;

  /// No description provided for @fakeSiteAdvice.
  ///
  /// In en, this message translates to:
  /// **'Do not enter your password, OTP, PIN or card details on this site. If you already did, call your bank and the cyber-crime helpline 1930 now.'**
  String get fakeSiteAdvice;

  /// No description provided for @fakeSiteGoReal.
  ///
  /// In en, this message translates to:
  /// **'Go to the real {brand} site'**
  String fakeSiteGoReal(String brand);

  /// No description provided for @fakeSiteReport.
  ///
  /// In en, this message translates to:
  /// **'Report'**
  String get fakeSiteReport;

  /// No description provided for @fakeSiteReportCyber.
  ///
  /// In en, this message translates to:
  /// **'Report to Cyber Crime (1930)'**
  String get fakeSiteReportCyber;

  /// No description provided for @fakeSiteWrong.
  ///
  /// In en, this message translates to:
  /// **'This warning is wrong'**
  String get fakeSiteWrong;

  /// No description provided for @fakeSiteThanks.
  ///
  /// In en, this message translates to:
  /// **'Thank you - we will check it.'**
  String get fakeSiteThanks;

  /// No description provided for @fakeSiteDiffLabel.
  ///
  /// In en, this message translates to:
  /// **'This link {seen} is different from the real address {real}'**
  String fakeSiteDiffLabel(String seen, String real);

  /// No description provided for @notifFakeSite.
  ///
  /// In en, this message translates to:
  /// **'Fake {brand} site!'**
  String notifFakeSite(String brand);

  /// No description provided for @whyRisky.
  ///
  /// In en, this message translates to:
  /// **'Why is this risky?'**
  String get whyRisky;

  /// No description provided for @vpnTitle.
  ///
  /// In en, this message translates to:
  /// **'Fake-website protection'**
  String get vpnTitle;

  /// No description provided for @vpnTileOn.
  ///
  /// In en, this message translates to:
  /// **'On - checks the sites every app opens'**
  String get vpnTileOn;

  /// No description provided for @vpnTileOff.
  ///
  /// In en, this message translates to:
  /// **'Off - tap to block fake sites in every app'**
  String get vpnTileOff;

  /// No description provided for @vpnConsentTitle.
  ///
  /// In en, this message translates to:
  /// **'Block fake websites in every app'**
  String get vpnConsentTitle;

  /// No description provided for @vpnConsentIntro.
  ///
  /// In en, this message translates to:
  /// **'FraudShield sets up a small VPN that stays on your phone. When any app or browser looks up a website name, FraudShield checks the name first and blocks known fake sites.'**
  String get vpnConsentIntro;

  /// No description provided for @vpnSeesTitle.
  ///
  /// In en, this message translates to:
  /// **'What FraudShield sees'**
  String get vpnSeesTitle;

  /// No description provided for @vpnSees.
  ///
  /// In en, this message translates to:
  /// **'Only website names (like flipkart.com) and which app asked for them. Your internet traffic does not go through FraudShield.'**
  String get vpnSees;

  /// No description provided for @vpnNotSeesTitle.
  ///
  /// In en, this message translates to:
  /// **'What it never sees'**
  String get vpnNotSeesTitle;

  /// No description provided for @vpnNotSees.
  ///
  /// In en, this message translates to:
  /// **'Pages, messages, passwords, payments or full links. Nothing is recorded on our server.'**
  String get vpnNotSees;

  /// No description provided for @vpnSentTitle.
  ///
  /// In en, this message translates to:
  /// **'What leaves your phone'**
  String get vpnSentTitle;

  /// No description provided for @vpnSent.
  ///
  /// In en, this message translates to:
  /// **'Only an unknown website name, to check if it is fake - never linked to you. In local-only mode nothing is sent.'**
  String get vpnSent;

  /// No description provided for @vpnConsentNote.
  ///
  /// In en, this message translates to:
  /// **'Android will ask you to allow a VPN connection. A key icon appears at the top while protection is on.'**
  String get vpnConsentNote;

  /// No description provided for @vpnTurnOn.
  ///
  /// In en, this message translates to:
  /// **'Turn on protection'**
  String get vpnTurnOn;

  /// No description provided for @vpnTurnOff.
  ///
  /// In en, this message translates to:
  /// **'Turn off'**
  String get vpnTurnOff;

  /// No description provided for @vpnNotNow.
  ///
  /// In en, this message translates to:
  /// **'Not now'**
  String get vpnNotNow;

  /// No description provided for @vpnStateOn.
  ///
  /// In en, this message translates to:
  /// **'Protection is on'**
  String get vpnStateOn;

  /// No description provided for @vpnStateOff.
  ///
  /// In en, this message translates to:
  /// **'Protection is off'**
  String get vpnStateOff;

  /// No description provided for @vpnStateOtherVpn.
  ///
  /// In en, this message translates to:
  /// **'Another VPN is connected'**
  String get vpnStateOtherVpn;

  /// No description provided for @vpnStateOtherVpnBody.
  ///
  /// In en, this message translates to:
  /// **'Android allows only one VPN at a time. Turn the other VPN off in Android settings, then turn FraudShield on again.'**
  String get vpnStateOtherVpnBody;

  /// No description provided for @vpnStatePrivateDns.
  ///
  /// In en, this message translates to:
  /// **'Private DNS is on'**
  String get vpnStatePrivateDns;

  /// No description provided for @vpnStatePrivateDnsBody.
  ///
  /// In en, this message translates to:
  /// **'Your phone sends website names to {name} directly, so FraudShield cannot check them. Set Private DNS to \"Automatic\" or \"Off\".'**
  String vpnStatePrivateDnsBody(String name);

  /// No description provided for @vpnStateRevoked.
  ///
  /// In en, this message translates to:
  /// **'Protection was switched off'**
  String get vpnStateRevoked;

  /// No description provided for @vpnStateRevokedBody.
  ///
  /// In en, this message translates to:
  /// **'It was turned off in Android settings or replaced by another VPN. Turn it on again to stay protected.'**
  String get vpnStateRevokedBody;

  /// No description provided for @vpnStateNoConsent.
  ///
  /// In en, this message translates to:
  /// **'Allow the VPN prompt to turn protection on.'**
  String get vpnStateNoConsent;

  /// No description provided for @vpnOpenVpnSettings.
  ///
  /// In en, this message translates to:
  /// **'Open VPN settings'**
  String get vpnOpenVpnSettings;

  /// No description provided for @vpnOpenDnsSettings.
  ///
  /// In en, this message translates to:
  /// **'Open network settings'**
  String get vpnOpenDnsSettings;

  /// No description provided for @vpnStats.
  ///
  /// In en, this message translates to:
  /// **'{n} site names checked'**
  String vpnStats(int n);

  /// No description provided for @vpnBlockedCount.
  ///
  /// In en, this message translates to:
  /// **'{n} fake sites blocked'**
  String vpnBlockedCount(int n);

  /// No description provided for @vpnListSize.
  ///
  /// In en, this message translates to:
  /// **'{n} known fake sites on your phone'**
  String vpnListSize(int n);

  /// No description provided for @vpnRecent.
  ///
  /// In en, this message translates to:
  /// **'Recently blocked'**
  String get vpnRecent;

  /// No description provided for @vpnNoneBlocked.
  ///
  /// In en, this message translates to:
  /// **'Nothing blocked yet.'**
  String get vpnNoneBlocked;

  /// No description provided for @vpnLocalOnly.
  ///
  /// In en, this message translates to:
  /// **'Local-only mode'**
  String get vpnLocalOnly;

  /// No description provided for @vpnLocalOnlyBody.
  ///
  /// In en, this message translates to:
  /// **'Use only the list on the phone; never ask the server about a website.'**
  String get vpnLocalOnlyBody;

  /// No description provided for @vpnBlockDoh.
  ///
  /// In en, this message translates to:
  /// **'Block hidden DNS services'**
  String get vpnBlockDoh;

  /// No description provided for @vpnBlockDohBody.
  ///
  /// In en, this message translates to:
  /// **'Some browsers look up sites through their own encrypted DNS and skip the check. Blocking it makes them use the checked one.'**
  String get vpnBlockDohBody;

  /// No description provided for @vpnUpstream.
  ///
  /// In en, this message translates to:
  /// **'DNS provider'**
  String get vpnUpstream;

  /// No description provided for @vpnUpstreamSystem.
  ///
  /// In en, this message translates to:
  /// **'Your network\'s (default)'**
  String get vpnUpstreamSystem;

  /// No description provided for @vpnTopApps.
  ///
  /// In en, this message translates to:
  /// **'Which apps contact which sites'**
  String get vpnTopApps;

  /// No description provided for @vpnTopAppsBody.
  ///
  /// In en, this message translates to:
  /// **'Last 30 days. Data use comes from Android\'s own counters (needs Usage Access).'**
  String get vpnTopAppsBody;

  /// No description provided for @vpnSitesCount.
  ///
  /// In en, this message translates to:
  /// **'{n} sites'**
  String vpnSitesCount(int n);

  /// No description provided for @vpnNoTraffic.
  ///
  /// In en, this message translates to:
  /// **'No data yet - turn protection on and use your phone for a while.'**
  String get vpnNoTraffic;

  /// No description provided for @vpnKeepOnTitle.
  ///
  /// In en, this message translates to:
  /// **'Keep protection on after a restart'**
  String get vpnKeepOnTitle;

  /// No description provided for @vpnKeepOnBody.
  ///
  /// In en, this message translates to:
  /// **'Your phone stops apps from starting by themselves. So that protection comes back after a restart or update: 1) in VPN settings tap FraudShield and turn on \"Always-on VPN\"; 2) allow Autostart for FraudShield.'**
  String get vpnKeepOnBody;

  /// No description provided for @vpnAlwaysOn.
  ///
  /// In en, this message translates to:
  /// **'Always-on VPN'**
  String get vpnAlwaysOn;

  /// No description provided for @vpnAutostart.
  ///
  /// In en, this message translates to:
  /// **'Allow Autostart'**
  String get vpnAutostart;

  /// No description provided for @appDetailAccess.
  ///
  /// In en, this message translates to:
  /// **'What it can access'**
  String get appDetailAccess;

  /// No description provided for @appDetailNothing.
  ///
  /// In en, this message translates to:
  /// **'No sensitive access granted.'**
  String get appDetailNothing;

  /// No description provided for @appDetailTrackers.
  ///
  /// In en, this message translates to:
  /// **'Tracking code inside'**
  String get appDetailTrackers;

  /// No description provided for @appDetailNoTrackers.
  ///
  /// In en, this message translates to:
  /// **'No known tracking code found.'**
  String get appDetailNoTrackers;

  /// No description provided for @appDetailTalksTo.
  ///
  /// In en, this message translates to:
  /// **'Who it talked to (last 7 days)'**
  String get appDetailTalksTo;

  /// No description provided for @appDetailTalksToBody.
  ///
  /// In en, this message translates to:
  /// **'From the fake-website protection on this phone. Site names never leave your phone.'**
  String get appDetailTalksToBody;

  /// No description provided for @appDetailNoNetwork.
  ///
  /// In en, this message translates to:
  /// **'No data - turn on fake-website protection to see this.'**
  String get appDetailNoNetwork;

  /// No description provided for @appDetailAdServers.
  ///
  /// In en, this message translates to:
  /// **'{n} advertising / tracking servers'**
  String appDetailAdServers(int n);

  /// No description provided for @appDetailMalware.
  ///
  /// In en, this message translates to:
  /// **'Talks to a known malware server!'**
  String get appDetailMalware;

  /// No description provided for @appBlockInternet.
  ///
  /// In en, this message translates to:
  /// **'Block internet'**
  String get appBlockInternet;

  /// No description provided for @appUnblockInternet.
  ///
  /// In en, this message translates to:
  /// **'Allow internet again'**
  String get appUnblockInternet;

  /// No description provided for @appBlockedNote.
  ///
  /// In en, this message translates to:
  /// **'Internet blocked for this app (while fake-website protection is on). Apps with fixed server addresses may still connect.'**
  String get appBlockedNote;

  /// No description provided for @appRevoke.
  ///
  /// In en, this message translates to:
  /// **'Remove permissions'**
  String get appRevoke;

  /// No description provided for @appFakeTitle.
  ///
  /// In en, this message translates to:
  /// **'This is NOT the real {name} app'**
  String appFakeTitle(String name);

  /// No description provided for @appIgnored.
  ///
  /// In en, this message translates to:
  /// **'You chose to ignore this app. FraudShield will not remind you.'**
  String get appIgnored;

  /// No description provided for @msgTitle.
  ///
  /// In en, this message translates to:
  /// **'Check a message'**
  String get msgTitle;

  /// No description provided for @msgIntro.
  ///
  /// In en, this message translates to:
  /// **'Paste an SMS or WhatsApp message. FraudShield checks it on your phone - the text does not leave your phone.'**
  String get msgIntro;

  /// No description provided for @msgHint.
  ///
  /// In en, this message translates to:
  /// **'Paste the message here'**
  String get msgHint;

  /// No description provided for @msgCheck.
  ///
  /// In en, this message translates to:
  /// **'Check'**
  String get msgCheck;

  /// No description provided for @msgSafe.
  ///
  /// In en, this message translates to:
  /// **'No scam signs found'**
  String get msgSafe;

  /// No description provided for @msgScam.
  ///
  /// In en, this message translates to:
  /// **'Looks like a {name} - do not reply, click or pay'**
  String msgScam(String name);

  /// No description provided for @msgSuspicious.
  ///
  /// In en, this message translates to:
  /// **'Be careful: this may be a {name}'**
  String msgSuspicious(String name);

  /// No description provided for @msgCatKyc.
  ///
  /// In en, this message translates to:
  /// **'fake bank KYC / account-block scam'**
  String get msgCatKyc;

  /// No description provided for @msgCatPrize.
  ///
  /// In en, this message translates to:
  /// **'fake prize / lottery scam'**
  String get msgCatPrize;

  /// No description provided for @msgCatParcel.
  ///
  /// In en, this message translates to:
  /// **'fake parcel / courier scam'**
  String get msgCatParcel;

  /// No description provided for @msgCatPower.
  ///
  /// In en, this message translates to:
  /// **'fake electricity-disconnection scam'**
  String get msgCatPower;

  /// No description provided for @msgCatJob.
  ///
  /// In en, this message translates to:
  /// **'fake job / task scam'**
  String get msgCatJob;

  /// No description provided for @msgCatLoan.
  ///
  /// In en, this message translates to:
  /// **'fake loan scam'**
  String get msgCatLoan;

  /// No description provided for @msgCatArrest.
  ///
  /// In en, this message translates to:
  /// **'\"digital arrest\" scam'**
  String get msgCatArrest;

  /// No description provided for @msgCatOtp.
  ///
  /// In en, this message translates to:
  /// **'OTP theft scam'**
  String get msgCatOtp;

  /// No description provided for @msgCatUpi.
  ///
  /// In en, this message translates to:
  /// **'UPI refund / collect scam'**
  String get msgCatUpi;

  /// No description provided for @msgCatInvest.
  ///
  /// In en, this message translates to:
  /// **'fake investment scam'**
  String get msgCatInvest;

  /// No description provided for @msgCatGeneric.
  ///
  /// In en, this message translates to:
  /// **'scam'**
  String get msgCatGeneric;

  /// No description provided for @msgLinkFake.
  ///
  /// In en, this message translates to:
  /// **'Fake {name} website - do not open'**
  String msgLinkFake(String name);

  /// No description provided for @msgLinkBad.
  ///
  /// In en, this message translates to:
  /// **'Dangerous link - do not open'**
  String get msgLinkBad;

  /// No description provided for @msgLinkOk.
  ///
  /// In en, this message translates to:
  /// **'No danger found in this link'**
  String get msgLinkOk;

  /// No description provided for @msgLinkUnknown.
  ///
  /// In en, this message translates to:
  /// **'Could not check this link'**
  String get msgLinkUnknown;

  /// No description provided for @msgAdvice.
  ///
  /// In en, this message translates to:
  /// **'Never share an OTP, PIN or password. If you already paid or shared, call your bank and the cyber-crime helpline 1930 now.'**
  String get msgAdvice;

  /// No description provided for @msgCloudButton.
  ///
  /// In en, this message translates to:
  /// **'Check with cloud AI'**
  String get msgCloudButton;

  /// No description provided for @msgCloudTitle.
  ///
  /// In en, this message translates to:
  /// **'Send this message for a deeper check?'**
  String get msgCloudTitle;

  /// No description provided for @msgCloudFirst.
  ///
  /// In en, this message translates to:
  /// **'The message text is sent securely (HTTPS) to the FraudShield server for a deeper check. It is used only for this check: it is not stored, not logged and not linked to you. Links in it are checked too. You decide every time - nothing is ever sent without this tap.'**
  String get msgCloudFirst;

  /// No description provided for @msgCloudAgain.
  ///
  /// In en, this message translates to:
  /// **'This message will be sent to the FraudShield server for a deeper check (not stored, not logged).'**
  String get msgCloudAgain;

  /// No description provided for @msgCloudSend.
  ///
  /// In en, this message translates to:
  /// **'Send for check'**
  String get msgCloudSend;

  /// No description provided for @msgCloudDone.
  ///
  /// In en, this message translates to:
  /// **'Includes the cloud check (message not stored).'**
  String get msgCloudDone;

  /// No description provided for @msgCloudFailed.
  ///
  /// In en, this message translates to:
  /// **'Cloud check not available right now - the phone\'s own check above still applies.'**
  String get msgCloudFailed;

  /// No description provided for @msgDashTile.
  ///
  /// In en, this message translates to:
  /// **'Check a message'**
  String get msgDashTile;

  /// No description provided for @msgDashTileSub.
  ///
  /// In en, this message translates to:
  /// **'Paste an SMS / WhatsApp message to see if it is a scam'**
  String get msgDashTileSub;

  /// No description provided for @leftTile.
  ///
  /// In en, this message translates to:
  /// **'Leftover files'**
  String get leftTile;

  /// No description provided for @leftTileSub.
  ///
  /// In en, this message translates to:
  /// **'Find files that removed apps left on your phone'**
  String get leftTileSub;

  /// No description provided for @leftTilePending.
  ///
  /// In en, this message translates to:
  /// **'{count} removed app(s) left files - tap to review'**
  String leftTilePending(int count);

  /// No description provided for @leftTitle.
  ///
  /// In en, this message translates to:
  /// **'Leftover files'**
  String get leftTitle;

  /// No description provided for @leftTitleApp.
  ///
  /// In en, this message translates to:
  /// **'Leftovers of {name}'**
  String leftTitleApp(String name);

  /// No description provided for @leftIntro.
  ///
  /// In en, this message translates to:
  /// **'Android removes each app\'s private data by itself. Apps can also save files in shared folders (Pictures, Download, their own folders) - those stay after uninstall. Nothing is removed until you choose and confirm.'**
  String get leftIntro;

  /// No description provided for @leftPrivateNote.
  ///
  /// In en, this message translates to:
  /// **'FraudShield cannot see other apps\' Android/data folders (Android does not allow it) - Android cleans those itself when an app is removed.'**
  String get leftPrivateNote;

  /// No description provided for @leftGroupInstaller.
  ///
  /// In en, this message translates to:
  /// **'App installers (.apk)'**
  String get leftGroupInstaller;

  /// No description provided for @leftGroupCache.
  ///
  /// In en, this message translates to:
  /// **'Cache and temporary files'**
  String get leftGroupCache;

  /// No description provided for @leftGroupMedia.
  ///
  /// In en, this message translates to:
  /// **'Photos, videos and audio'**
  String get leftGroupMedia;

  /// No description provided for @leftGroupDocument.
  ///
  /// In en, this message translates to:
  /// **'Documents'**
  String get leftGroupDocument;

  /// No description provided for @leftGroupOther.
  ///
  /// In en, this message translates to:
  /// **'Other files'**
  String get leftGroupOther;

  /// No description provided for @leftWhatInstaller.
  ///
  /// In en, this message translates to:
  /// **'Installer file - not needed once the app is installed or removed.'**
  String get leftWhatInstaller;

  /// No description provided for @leftWhatCache.
  ///
  /// In en, this message translates to:
  /// **'Temporary data the app made for itself - safe to remove.'**
  String get leftWhatCache;

  /// No description provided for @leftWhatMedia.
  ///
  /// In en, this message translates to:
  /// **'Your photos, videos or audio saved by the app - check before removing.'**
  String get leftWhatMedia;

  /// No description provided for @leftWhatDocument.
  ///
  /// In en, this message translates to:
  /// **'Documents saved by the app - check before removing.'**
  String get leftWhatDocument;

  /// No description provided for @leftWhatOther.
  ///
  /// In en, this message translates to:
  /// **'Files the app saved - check before removing.'**
  String get leftWhatOther;

  /// No description provided for @leftFolderInfo.
  ///
  /// In en, this message translates to:
  /// **'Folder, {files} files, {size}'**
  String leftFolderInfo(int files, String size);

  /// No description provided for @leftNothing.
  ///
  /// In en, this message translates to:
  /// **'Nothing left behind - all clean.'**
  String get leftNothing;

  /// No description provided for @leftRemovedApps.
  ///
  /// In en, this message translates to:
  /// **'Removed apps'**
  String get leftRemovedApps;

  /// No description provided for @leftRemovedInfo.
  ///
  /// In en, this message translates to:
  /// **'{files} files, {size}'**
  String leftRemovedInfo(int files, String size);

  /// No description provided for @leftOrphans.
  ///
  /// In en, this message translates to:
  /// **'Folders of apps that are no longer installed'**
  String get leftOrphans;

  /// No description provided for @leftOrphanFrom.
  ///
  /// In en, this message translates to:
  /// **'From {name}'**
  String leftOrphanFrom(String name);

  /// No description provided for @leftApks.
  ///
  /// In en, this message translates to:
  /// **'Old app installers in Download'**
  String get leftApks;

  /// No description provided for @leftApkSame.
  ///
  /// In en, this message translates to:
  /// **'The same version is installed - not needed'**
  String get leftApkSame;

  /// No description provided for @leftApkOlder.
  ///
  /// In en, this message translates to:
  /// **'Older than the installed app - not needed'**
  String get leftApkOlder;

  /// No description provided for @leftApkNewer.
  ///
  /// In en, this message translates to:
  /// **'Newer than the installed app - an update you have not installed'**
  String get leftApkNewer;

  /// No description provided for @leftApkNotInstalled.
  ///
  /// In en, this message translates to:
  /// **'This app is not installed'**
  String get leftApkNotInstalled;

  /// No description provided for @leftApkFlagged.
  ///
  /// In en, this message translates to:
  /// **'Flagged by file protection - remove it'**
  String get leftApkFlagged;

  /// No description provided for @leftApkUnreadable.
  ///
  /// In en, this message translates to:
  /// **'Could not read this installer'**
  String get leftApkUnreadable;

  /// No description provided for @leftDelete.
  ///
  /// In en, this message translates to:
  /// **'Remove selected ({count}, {size})'**
  String leftDelete(int count, String size);

  /// No description provided for @leftConfirmTitle.
  ///
  /// In en, this message translates to:
  /// **'Remove {count} item(s)?'**
  String leftConfirmTitle(int count);

  /// No description provided for @leftConfirmBody.
  ///
  /// In en, this message translates to:
  /// **'Photos, videos and audio go to the phone\'s trash - you can restore them for 30 days. Other files (installers, documents, cache) are deleted. Files of apps that are still installed are always left alone.'**
  String get leftConfirmBody;

  /// No description provided for @leftConfirmYes.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get leftConfirmYes;

  /// No description provided for @leftCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get leftCancel;

  /// No description provided for @leftKeep.
  ///
  /// In en, this message translates to:
  /// **'Keep all'**
  String get leftKeep;

  /// No description provided for @leftDone.
  ///
  /// In en, this message translates to:
  /// **'Moved {trashed} to trash, deleted {deleted}.'**
  String leftDone(int trashed, int deleted);

  /// No description provided for @leftRefused.
  ///
  /// In en, this message translates to:
  /// **'{count} item(s) left alone - they belong to an installed app or a protected folder.'**
  String leftRefused(int count);

  /// No description provided for @leftCancelled.
  ///
  /// In en, this message translates to:
  /// **'Nothing was removed.'**
  String get leftCancelled;

  /// No description provided for @leftSettingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Nothing left in Settings?'**
  String get leftSettingsTitle;

  /// No description provided for @leftSetClear.
  ///
  /// In en, this message translates to:
  /// **'No longer listed'**
  String get leftSetClear;

  /// No description provided for @leftSetLeftover.
  ///
  /// In en, this message translates to:
  /// **'Still points to the removed app - open Settings and switch it off'**
  String get leftSetLeftover;

  /// No description provided for @leftSetUnknown.
  ///
  /// In en, this message translates to:
  /// **'Android does not let apps check this'**
  String get leftSetUnknown;

  /// No description provided for @leftKindAccessibility.
  ///
  /// In en, this message translates to:
  /// **'Accessibility'**
  String get leftKindAccessibility;

  /// No description provided for @leftKindNotif.
  ///
  /// In en, this message translates to:
  /// **'Notification access'**
  String get leftKindNotif;

  /// No description provided for @leftKindAdmin.
  ///
  /// In en, this message translates to:
  /// **'Device admin'**
  String get leftKindAdmin;

  /// No description provided for @leftKindVpn.
  ///
  /// In en, this message translates to:
  /// **'Always-on VPN'**
  String get leftKindVpn;

  /// No description provided for @leftKindSms.
  ///
  /// In en, this message translates to:
  /// **'Default SMS app'**
  String get leftKindSms;

  /// No description provided for @leftKindDialer.
  ///
  /// In en, this message translates to:
  /// **'Default phone app'**
  String get leftKindDialer;

  /// No description provided for @leftKindBrowser.
  ///
  /// In en, this message translates to:
  /// **'Default browser'**
  String get leftKindBrowser;

  /// No description provided for @leftKindHome.
  ///
  /// In en, this message translates to:
  /// **'Default home screen'**
  String get leftKindHome;

  /// No description provided for @leftNeedAccess.
  ///
  /// In en, this message translates to:
  /// **'Allow file access so FraudShield can find leftover files.'**
  String get leftNeedAccess;

  /// No description provided for @callTile.
  ///
  /// In en, this message translates to:
  /// **'Call guard'**
  String get callTile;

  /// No description provided for @callTileOff.
  ///
  /// In en, this message translates to:
  /// **'Off - tap to block scam calls'**
  String get callTileOff;

  /// No description provided for @callTileOn.
  ///
  /// In en, this message translates to:
  /// **'On - {blocked} calls stopped in 30 days'**
  String callTileOn(int blocked);

  /// No description provided for @callTileUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Needs Android 10 or newer'**
  String get callTileUnsupported;

  /// No description provided for @callTitle.
  ///
  /// In en, this message translates to:
  /// **'Call guard'**
  String get callTitle;

  /// No description provided for @callIntro.
  ///
  /// In en, this message translates to:
  /// **'FraudShield checks incoming calls from numbers that are not in your contacts - on your phone, without sending the number anywhere.'**
  String get callIntro;

  /// No description provided for @callUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Call guard needs Android 10 or newer. You can still report a scam number below.'**
  String get callUnsupported;

  /// No description provided for @callRoleTitle.
  ///
  /// In en, this message translates to:
  /// **'Let FraudShield screen calls'**
  String get callRoleTitle;

  /// No description provided for @callRoleBody.
  ///
  /// In en, this message translates to:
  /// **'Android will ask you to choose FraudShield as the \"Caller ID & spam\" app. Your phone app stays the same.'**
  String get callRoleBody;

  /// No description provided for @callRoleButton.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get callRoleButton;

  /// No description provided for @callRoleOn.
  ///
  /// In en, this message translates to:
  /// **'FraudShield is screening calls'**
  String get callRoleOn;

  /// No description provided for @callModeTitle.
  ///
  /// In en, this message translates to:
  /// **'What should happen to risky calls?'**
  String get callModeTitle;

  /// No description provided for @callMode1.
  ///
  /// In en, this message translates to:
  /// **'Only label calls'**
  String get callMode1;

  /// No description provided for @callMode1Sub.
  ///
  /// In en, this message translates to:
  /// **'Never cut a call - just tell me who it might be'**
  String get callMode1Sub;

  /// No description provided for @callMode2.
  ///
  /// In en, this message translates to:
  /// **'Block known scam numbers (recommended)'**
  String get callMode2;

  /// No description provided for @callMode2Sub.
  ///
  /// In en, this message translates to:
  /// **'Numbers many people reported as fraud are rejected'**
  String get callMode2Sub;

  /// No description provided for @callMode3.
  ///
  /// In en, this message translates to:
  /// **'Block scams, silence risky calls'**
  String get callMode3;

  /// No description provided for @callMode3Sub.
  ///
  /// In en, this message translates to:
  /// **'Foreign and unusual numbers ring silently; you still see them'**
  String get callMode3Sub;

  /// No description provided for @callMode4.
  ///
  /// In en, this message translates to:
  /// **'Reject all unknown numbers'**
  String get callMode4;

  /// No description provided for @callMode4Sub.
  ///
  /// In en, this message translates to:
  /// **'Only contacts, numbers you called and your allow list can ring'**
  String get callMode4Sub;

  /// No description provided for @callMode4WarnTitle.
  ///
  /// In en, this message translates to:
  /// **'Reject every unknown caller?'**
  String get callMode4WarnTitle;

  /// No description provided for @callMode4WarnBody.
  ///
  /// In en, this message translates to:
  /// **'Delivery agents, doctors, a new school number or a new bank office will NOT ring. They appear in Blocked calls, where you can allow them. Emergency numbers and official 1600 bank calls always ring.'**
  String get callMode4WarnBody;

  /// No description provided for @callMode4Confirm.
  ///
  /// In en, this message translates to:
  /// **'Yes, reject unknown'**
  String get callMode4Confirm;

  /// No description provided for @callCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get callCancel;

  /// No description provided for @callContactsTitle.
  ///
  /// In en, this message translates to:
  /// **'Never block people you know'**
  String get callContactsTitle;

  /// No description provided for @callContactsBody.
  ///
  /// In en, this message translates to:
  /// **'Let FraudShield check your contacts and the numbers you called, on this phone only. Nothing is uploaded.'**
  String get callContactsBody;

  /// No description provided for @callContactsButton.
  ///
  /// In en, this message translates to:
  /// **'Allow contacts and call history'**
  String get callContactsButton;

  /// No description provided for @callNotifTitle.
  ///
  /// In en, this message translates to:
  /// **'Allow notifications'**
  String get callNotifTitle;

  /// No description provided for @callNotifBody.
  ///
  /// In en, this message translates to:
  /// **'So FraudShield can tell you who called and why a call was stopped.'**
  String get callNotifBody;

  /// No description provided for @callAllowTitle.
  ///
  /// In en, this message translates to:
  /// **'Always allow'**
  String get callAllowTitle;

  /// No description provided for @callAllowHint.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get callAllowHint;

  /// No description provided for @callAllowAdd.
  ///
  /// In en, this message translates to:
  /// **'Add'**
  String get callAllowAdd;

  /// No description provided for @callAllowInvalid.
  ///
  /// In en, this message translates to:
  /// **'Not a valid phone number'**
  String get callAllowInvalid;

  /// No description provided for @callAllowEmpty.
  ///
  /// In en, this message translates to:
  /// **'No numbers yet'**
  String get callAllowEmpty;

  /// No description provided for @callRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get callRemove;

  /// No description provided for @callBlockTitle.
  ///
  /// In en, this message translates to:
  /// **'Numbers you blocked'**
  String get callBlockTitle;

  /// No description provided for @callListInfo.
  ///
  /// In en, this message translates to:
  /// **'Scam list on this phone: {count} numbers'**
  String callListInfo(int count);

  /// No description provided for @callSync.
  ///
  /// In en, this message translates to:
  /// **'Update list'**
  String get callSync;

  /// No description provided for @callSyncDone.
  ///
  /// In en, this message translates to:
  /// **'List updated'**
  String get callSyncDone;

  /// No description provided for @callSyncFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not update now - the list on the phone still works'**
  String get callSyncFailed;

  /// No description provided for @callBlockedTitle.
  ///
  /// In en, this message translates to:
  /// **'Blocked calls'**
  String get callBlockedTitle;

  /// No description provided for @callBlockedSub.
  ///
  /// In en, this message translates to:
  /// **'Last 30 days - kept only on this phone'**
  String get callBlockedSub;

  /// No description provided for @callBlockedEmpty.
  ///
  /// In en, this message translates to:
  /// **'No screened calls in the last 30 days'**
  String get callBlockedEmpty;

  /// No description provided for @callActionReject.
  ///
  /// In en, this message translates to:
  /// **'Rejected'**
  String get callActionReject;

  /// No description provided for @callActionSilence.
  ///
  /// In en, this message translates to:
  /// **'Silenced'**
  String get callActionSilence;

  /// No description provided for @callActionAllow.
  ///
  /// In en, this message translates to:
  /// **'Rang, with a warning'**
  String get callActionAllow;

  /// No description provided for @callUndone.
  ///
  /// In en, this message translates to:
  /// **'Allowed by you'**
  String get callUndone;

  /// No description provided for @callNotSpam.
  ///
  /// In en, this message translates to:
  /// **'Not spam'**
  String get callNotSpam;

  /// No description provided for @callNotSpamDone.
  ///
  /// In en, this message translates to:
  /// **'OK - {number} is always allowed now'**
  String callNotSpamDone(String number);

  /// No description provided for @callReport.
  ///
  /// In en, this message translates to:
  /// **'Report'**
  String get callReport;

  /// No description provided for @callReportTitle.
  ///
  /// In en, this message translates to:
  /// **'Report a number'**
  String get callReportTitle;

  /// No description provided for @callReportIntro.
  ///
  /// In en, this message translates to:
  /// **'When many people report the same number it goes on the scam list for everyone. One report per number per person.'**
  String get callReportIntro;

  /// No description provided for @callReportNumber.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get callReportNumber;

  /// No description provided for @callReportCategory.
  ///
  /// In en, this message translates to:
  /// **'What was it?'**
  String get callReportCategory;

  /// No description provided for @callCatScam.
  ///
  /// In en, this message translates to:
  /// **'Fraud / scam'**
  String get callCatScam;

  /// No description provided for @callCatSpam.
  ///
  /// In en, this message translates to:
  /// **'Spam'**
  String get callCatSpam;

  /// No description provided for @callCatTele.
  ///
  /// In en, this message translates to:
  /// **'Telemarketing'**
  String get callCatTele;

  /// No description provided for @callReportSend.
  ///
  /// In en, this message translates to:
  /// **'Report to FraudShield'**
  String get callReportSend;

  /// No description provided for @callReportDone.
  ///
  /// In en, this message translates to:
  /// **'Reported - thank you'**
  String get callReportDone;

  /// No description provided for @callReportProtected.
  ///
  /// In en, this message translates to:
  /// **'This is an official 1600 / 140 service number. TRAI does not allow these to be reported or blocked.'**
  String get callReportProtected;

  /// No description provided for @callReportLimit.
  ///
  /// In en, this message translates to:
  /// **'You have reported many numbers today - please try again tomorrow'**
  String get callReportLimit;

  /// No description provided for @callReportFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not report now - check your internet and try again'**
  String get callReportFailed;

  /// No description provided for @callGovTitle.
  ///
  /// In en, this message translates to:
  /// **'Report to the government (Chakshu)'**
  String get callGovTitle;

  /// No description provided for @callGovBody.
  ///
  /// In en, this message translates to:
  /// **'Sanchar Saathi \"Chakshu\" is the Department of Telecommunications\' free portal for reporting fraud calls and SMS. Copy the number, then paste it in the form.'**
  String get callGovBody;

  /// No description provided for @callGovCopy.
  ///
  /// In en, this message translates to:
  /// **'Copy number'**
  String get callGovCopy;

  /// No description provided for @callGovCopied.
  ///
  /// In en, this message translates to:
  /// **'Number copied'**
  String get callGovCopied;

  /// No description provided for @callGovOpen.
  ///
  /// In en, this message translates to:
  /// **'Open Chakshu'**
  String get callGovOpen;

  /// No description provided for @callGovMoney.
  ///
  /// In en, this message translates to:
  /// **'Lost money? Call 1930 now or report at cybercrime.gov.in'**
  String get callGovMoney;

  /// No description provided for @blockTitleWeb.
  ///
  /// In en, this message translates to:
  /// **'Ad & tracker blocking'**
  String get blockTitleWeb;

  /// No description provided for @blockTitlePlay.
  ///
  /// In en, this message translates to:
  /// **'Tracker & malware blocking'**
  String get blockTitlePlay;

  /// No description provided for @blockIntroWeb.
  ///
  /// In en, this message translates to:
  /// **'Blocks ads, tracking and malware servers in every app and browser - on your phone, nothing is sent anywhere.'**
  String get blockIntroWeb;

  /// No description provided for @blockIntroPlay.
  ///
  /// In en, this message translates to:
  /// **'Blocks known tracking and malware servers in every app and browser - on your phone, nothing is sent anywhere.'**
  String get blockIntroPlay;

  /// No description provided for @blockAdsNotInPlay.
  ///
  /// In en, this message translates to:
  /// **'Ads are not blocked in this version: Google Play rules do not allow blocking other apps\' ads. The version from our website can block ads.'**
  String get blockAdsNotInPlay;

  /// No description provided for @blockNeedsVpn.
  ///
  /// In en, this message translates to:
  /// **'Works while Fake-website protection (the on-phone VPN) is on.'**
  String get blockNeedsVpn;

  /// No description provided for @blockCatAds.
  ///
  /// In en, this message translates to:
  /// **'Ads'**
  String get blockCatAds;

  /// No description provided for @blockCatAdsSub.
  ///
  /// In en, this message translates to:
  /// **'Ad servers in apps and websites'**
  String get blockCatAdsSub;

  /// No description provided for @blockCatTrackers.
  ///
  /// In en, this message translates to:
  /// **'Trackers'**
  String get blockCatTrackers;

  /// No description provided for @blockCatTrackersSub.
  ///
  /// In en, this message translates to:
  /// **'Analytics and tracking servers that follow what you do'**
  String get blockCatTrackersSub;

  /// No description provided for @blockCatMalware.
  ///
  /// In en, this message translates to:
  /// **'Malware & scam sites'**
  String get blockCatMalware;

  /// No description provided for @blockCatMalwareSub.
  ///
  /// In en, this message translates to:
  /// **'Known malware, phishing and scam servers'**
  String get blockCatMalwareSub;

  /// No description provided for @blockList.
  ///
  /// In en, this message translates to:
  /// **'Block list: {count} sites'**
  String blockList(int count);

  /// No description provided for @blockListNone.
  ///
  /// In en, this message translates to:
  /// **'Block list not downloaded yet'**
  String get blockListNone;

  /// No description provided for @blockUpdate.
  ///
  /// In en, this message translates to:
  /// **'Update now'**
  String get blockUpdate;

  /// No description provided for @blockUpdated.
  ///
  /// In en, this message translates to:
  /// **'Block list is up to date'**
  String get blockUpdated;

  /// No description provided for @blockUpdateFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not update the block list right now'**
  String get blockUpdateFailed;

  /// No description provided for @blockPause5.
  ///
  /// In en, this message translates to:
  /// **'Pause blocking for 5 minutes'**
  String get blockPause5;

  /// No description provided for @blockPausedUntil.
  ///
  /// In en, this message translates to:
  /// **'Paused until {time}'**
  String blockPausedUntil(String time);

  /// No description provided for @blockResume.
  ///
  /// In en, this message translates to:
  /// **'Resume now'**
  String get blockResume;

  /// No description provided for @blockAllowedApps.
  ///
  /// In en, this message translates to:
  /// **'Apps not blocked'**
  String get blockAllowedApps;

  /// No description provided for @blockAllowedAppsNone.
  ///
  /// In en, this message translates to:
  /// **'None - blocking applies to every app'**
  String get blockAllowedAppsNone;

  /// No description provided for @blockAllowApp.
  ///
  /// In en, this message translates to:
  /// **'Allow an app…'**
  String get blockAllowApp;

  /// No description provided for @blockPickApp.
  ///
  /// In en, this message translates to:
  /// **'Choose the app'**
  String get blockPickApp;

  /// No description provided for @blockPickAppBody.
  ///
  /// In en, this message translates to:
  /// **'Apps seen by the VPN. \"Allow\" stops blocking for that app. \"This app broke\" does the same and keeps a note on this phone (nothing is sent).'**
  String get blockPickAppBody;

  /// No description provided for @blockAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get blockAllow;

  /// No description provided for @blockAppBroke.
  ///
  /// In en, this message translates to:
  /// **'This app broke'**
  String get blockAppBroke;

  /// No description provided for @blockAppBrokeDone.
  ///
  /// In en, this message translates to:
  /// **'{app} is no longer blocked. The note stays on this phone.'**
  String blockAppBrokeDone(String app);

  /// No description provided for @blockRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get blockRemove;

  /// No description provided for @blockAllowedSites.
  ///
  /// In en, this message translates to:
  /// **'Sites not blocked'**
  String get blockAllowedSites;

  /// No description provided for @blockAddSite.
  ///
  /// In en, this message translates to:
  /// **'Allow a site'**
  String get blockAddSite;

  /// No description provided for @blockSiteHint.
  ///
  /// In en, this message translates to:
  /// **'example.com'**
  String get blockSiteHint;

  /// No description provided for @blockAdd.
  ///
  /// In en, this message translates to:
  /// **'Add'**
  String get blockAdd;

  /// No description provided for @blockStats.
  ///
  /// In en, this message translates to:
  /// **'Blocking statistics'**
  String get blockStats;

  /// No description provided for @blockStatsTotal.
  ///
  /// In en, this message translates to:
  /// **'{count} requests blocked'**
  String blockStatsTotal(int count);

  /// No description provided for @blockStatsSaved.
  ///
  /// In en, this message translates to:
  /// **'About {size} of data saved (estimate)'**
  String blockStatsSaved(String size);

  /// No description provided for @blockStatsByCat.
  ///
  /// In en, this message translates to:
  /// **'By type'**
  String get blockStatsByCat;

  /// No description provided for @blockStatsByApp.
  ///
  /// In en, this message translates to:
  /// **'By app'**
  String get blockStatsByApp;

  /// No description provided for @blockStatsTop.
  ///
  /// In en, this message translates to:
  /// **'Most blocked sites'**
  String get blockStatsTop;

  /// No description provided for @blockStatsDays.
  ///
  /// In en, this message translates to:
  /// **'Last {count} days'**
  String blockStatsDays(int count);

  /// No description provided for @blockStatsLocal.
  ///
  /// In en, this message translates to:
  /// **'Counted on your phone; these numbers never leave it.'**
  String get blockStatsLocal;

  /// No description provided for @blockStatsEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing blocked yet.'**
  String get blockStatsEmpty;

  /// No description provided for @blockVpnCount.
  ///
  /// In en, this message translates to:
  /// **'{count} requests blocked since the VPN started'**
  String blockVpnCount(int count);

  /// No description provided for @safeTileTitle.
  ///
  /// In en, this message translates to:
  /// **'Scam help'**
  String get safeTileTitle;

  /// No description provided for @safeTileSub.
  ///
  /// In en, this message translates to:
  /// **'Live scam guard, check before you pay, recovery kit, drills'**
  String get safeTileSub;

  /// No description provided for @safeHubTitle.
  ///
  /// In en, this message translates to:
  /// **'Scam help'**
  String get safeHubTitle;

  /// No description provided for @safeGuardTitle.
  ///
  /// In en, this message translates to:
  /// **'Live scam guard'**
  String get safeGuardTitle;

  /// No description provided for @safeGuardSub.
  ///
  /// In en, this message translates to:
  /// **'STOP warning when a scam call, a screen-sharing app and your bank app come together'**
  String get safeGuardSub;

  /// No description provided for @safeUpiTitle.
  ///
  /// In en, this message translates to:
  /// **'Check before you pay'**
  String get safeUpiTitle;

  /// No description provided for @safeUpiSub.
  ///
  /// In en, this message translates to:
  /// **'UPI ID or the text of a UPI QR code'**
  String get safeUpiSub;

  /// No description provided for @safeCareTitle.
  ///
  /// In en, this message translates to:
  /// **'Check a customer-care number'**
  String get safeCareTitle;

  /// No description provided for @safeCareSub.
  ///
  /// In en, this message translates to:
  /// **'Fake helpline numbers are a common trap'**
  String get safeCareSub;

  /// No description provided for @safeRecoveryTitle.
  ///
  /// In en, this message translates to:
  /// **'I was scammed - what now?'**
  String get safeRecoveryTitle;

  /// No description provided for @safeRecoverySub.
  ///
  /// In en, this message translates to:
  /// **'First-hour steps, 1930, block your cards, evidence'**
  String get safeRecoverySub;

  /// No description provided for @safeGuardianTitle.
  ///
  /// In en, this message translates to:
  /// **'Guardian'**
  String get safeGuardianTitle;

  /// No description provided for @safeGuardianSub.
  ///
  /// In en, this message translates to:
  /// **'A family member you can reach in one tap'**
  String get safeGuardianSub;

  /// No description provided for @safeDrillsTitle.
  ///
  /// In en, this message translates to:
  /// **'Scam drills'**
  String get safeDrillsTitle;

  /// No description provided for @safeDrillsSub.
  ///
  /// In en, this message translates to:
  /// **'Practise spotting 5 common scams'**
  String get safeDrillsSub;

  /// No description provided for @safeGuardIntro.
  ///
  /// In en, this message translates to:
  /// **'Most bank scams follow one pattern: an unknown caller keeps you on the phone, asks you to open a screen-sharing app (AnyDesk, TeamViewer...), then to open your bank app or read out an OTP. When FraudShield sees these together it shows a STOP warning. Only the kind of signal is sent to FraudShield - never the number, the app name or the code.'**
  String get safeGuardIntro;

  /// No description provided for @safeGuardSwitch.
  ///
  /// In en, this message translates to:
  /// **'Live scam guard'**
  String get safeGuardSwitch;

  /// No description provided for @safeOtpSwitch.
  ///
  /// In en, this message translates to:
  /// **'Warn me when an OTP arrives during a call'**
  String get safeOtpSwitch;

  /// No description provided for @safeOtpSwitchSub.
  ///
  /// In en, this message translates to:
  /// **'Works on the phone only; the code is never stored'**
  String get safeOtpSwitchSub;

  /// No description provided for @safePermsTitle.
  ///
  /// In en, this message translates to:
  /// **'What the guard needs'**
  String get safePermsTitle;

  /// No description provided for @safePermPhone.
  ///
  /// In en, this message translates to:
  /// **'Phone: is a call going on'**
  String get safePermPhone;

  /// No description provided for @safePermCallLog.
  ///
  /// In en, this message translates to:
  /// **'Caller\'s number - checked against your contacts on the phone'**
  String get safePermCallLog;

  /// No description provided for @safePermContacts.
  ///
  /// In en, this message translates to:
  /// **'Contacts'**
  String get safePermContacts;

  /// No description provided for @safePermUsage.
  ///
  /// In en, this message translates to:
  /// **'Usage access: which app is open'**
  String get safePermUsage;

  /// No description provided for @safePermNotif.
  ///
  /// In en, this message translates to:
  /// **'Notification access: an OTP arrived'**
  String get safePermNotif;

  /// No description provided for @safeAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get safeAllow;

  /// No description provided for @safeAllowed.
  ///
  /// In en, this message translates to:
  /// **'Allowed'**
  String get safeAllowed;

  /// No description provided for @safeTestStop.
  ///
  /// In en, this message translates to:
  /// **'Test the STOP warning'**
  String get safeTestStop;

  /// No description provided for @safeStopTitle.
  ///
  /// In en, this message translates to:
  /// **'STOP!'**
  String get safeStopTitle;

  /// No description provided for @safeStopHeadline.
  ///
  /// In en, this message translates to:
  /// **'This looks like a scam happening right now'**
  String get safeStopHeadline;

  /// No description provided for @safeStopWarnHeadline.
  ///
  /// In en, this message translates to:
  /// **'Careful: this may be a scam'**
  String get safeStopWarnHeadline;

  /// No description provided for @safeStopBody.
  ///
  /// In en, this message translates to:
  /// **'An unknown caller, a screen-sharing app and your bank app at the same time - this is how bank scams work.'**
  String get safeStopBody;

  /// No description provided for @safeStopStep1.
  ///
  /// In en, this message translates to:
  /// **'Hang up the call now.'**
  String get safeStopStep1;

  /// No description provided for @safeStopStep2.
  ///
  /// In en, this message translates to:
  /// **'Do not share any OTP, PIN or password.'**
  String get safeStopStep2;

  /// No description provided for @safeStopStep3.
  ///
  /// In en, this message translates to:
  /// **'Close and uninstall the screen-sharing app (AnyDesk, TeamViewer...).'**
  String get safeStopStep3;

  /// No description provided for @safeStopStep4.
  ///
  /// In en, this message translates to:
  /// **'If you already paid or shared a code: call 1930 now.'**
  String get safeStopStep4;

  /// No description provided for @safeStopSpeak.
  ///
  /// In en, this message translates to:
  /// **'Read aloud'**
  String get safeStopSpeak;

  /// No description provided for @safeNoVoice.
  ///
  /// In en, this message translates to:
  /// **'No voice for this language on this phone - please read the steps above.'**
  String get safeNoVoice;

  /// No description provided for @safeCall1930.
  ///
  /// In en, this message translates to:
  /// **'Call 1930'**
  String get safeCall1930;

  /// No description provided for @safeOpenRecovery.
  ///
  /// In en, this message translates to:
  /// **'Open recovery kit'**
  String get safeOpenRecovery;

  /// No description provided for @safeImSafe.
  ///
  /// In en, this message translates to:
  /// **'I am safe - close'**
  String get safeImSafe;

  /// No description provided for @safeCallGuardian.
  ///
  /// In en, this message translates to:
  /// **'Call {name}'**
  String safeCallGuardian(String name);

  /// No description provided for @safeTellGuardian.
  ///
  /// In en, this message translates to:
  /// **'Message {name}'**
  String safeTellGuardian(String name);

  /// No description provided for @safeGuardianSms.
  ///
  /// In en, this message translates to:
  /// **'FraudShield alert: I may be talking to a scammer right now. Please call me.'**
  String get safeGuardianSms;

  /// No description provided for @safeRecTitle.
  ///
  /// In en, this message translates to:
  /// **'Recovery kit'**
  String get safeRecTitle;

  /// No description provided for @safeRecIntro.
  ///
  /// In en, this message translates to:
  /// **'Act within the first hour (the \"golden hour\"): the faster you report, the better the chance to freeze the money.'**
  String get safeRecIntro;

  /// No description provided for @safeRecStart.
  ///
  /// In en, this message translates to:
  /// **'I was just scammed - start the clock'**
  String get safeRecStart;

  /// No description provided for @safeRecElapsed.
  ///
  /// In en, this message translates to:
  /// **'{time} since you started'**
  String safeRecElapsed(String time);

  /// No description provided for @safeRecReset.
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get safeRecReset;

  /// No description provided for @safeRec1930.
  ///
  /// In en, this message translates to:
  /// **'Call 1930 - National Cyber Crime Helpline'**
  String get safeRec1930;

  /// No description provided for @safeRecPortal.
  ///
  /// In en, this message translates to:
  /// **'Report at cybercrime.gov.in'**
  String get safeRecPortal;

  /// No description provided for @safeRecBlockTitle.
  ///
  /// In en, this message translates to:
  /// **'Block your card / account'**
  String get safeRecBlockTitle;

  /// No description provided for @safeRecBlockSub.
  ///
  /// In en, this message translates to:
  /// **'Numbers read on each bank\'s own website (Nov 2026). Double-check on your card.'**
  String get safeRecBlockSub;

  /// No description provided for @safeRecChecklist.
  ///
  /// In en, this message translates to:
  /// **'Checklist'**
  String get safeRecChecklist;

  /// No description provided for @safeRecStepCall1930.
  ///
  /// In en, this message translates to:
  /// **'Called 1930'**
  String get safeRecStepCall1930;

  /// No description provided for @safeRecStepBank.
  ///
  /// In en, this message translates to:
  /// **'Called my bank; blocked card / UPI'**
  String get safeRecStepBank;

  /// No description provided for @safeRecStepPasswords.
  ///
  /// In en, this message translates to:
  /// **'Changed UPI PIN, net-banking and email passwords'**
  String get safeRecStepPasswords;

  /// No description provided for @safeRecStepRemote.
  ///
  /// In en, this message translates to:
  /// **'Uninstalled screen-sharing apps'**
  String get safeRecStepRemote;

  /// No description provided for @safeRecStepPortal.
  ///
  /// In en, this message translates to:
  /// **'Filed a complaint at cybercrime.gov.in'**
  String get safeRecStepPortal;

  /// No description provided for @safeRecStepKeep.
  ///
  /// In en, this message translates to:
  /// **'Saved the complaint / acknowledgement number'**
  String get safeRecStepKeep;

  /// No description provided for @safeRecEvidence.
  ///
  /// In en, this message translates to:
  /// **'Evidence pack'**
  String get safeRecEvidence;

  /// No description provided for @safeRecEvidenceSub.
  ///
  /// In en, this message translates to:
  /// **'Write down what happened. It stays on your phone until you share it.'**
  String get safeRecEvidenceSub;

  /// No description provided for @safeRecEvWho.
  ///
  /// In en, this message translates to:
  /// **'Scammer\'s number / UPI ID / link'**
  String get safeRecEvWho;

  /// No description provided for @safeRecEvMoney.
  ///
  /// In en, this message translates to:
  /// **'Amount, date and time of each payment'**
  String get safeRecEvMoney;

  /// No description provided for @safeRecEvApps.
  ///
  /// In en, this message translates to:
  /// **'Apps they asked you to install'**
  String get safeRecEvApps;

  /// No description provided for @safeRecEvNotes.
  ///
  /// In en, this message translates to:
  /// **'What happened'**
  String get safeRecEvNotes;

  /// No description provided for @safeRecShare.
  ///
  /// In en, this message translates to:
  /// **'Share evidence'**
  String get safeRecShare;

  /// No description provided for @safeRecScreens.
  ///
  /// In en, this message translates to:
  /// **'Screenshots: open Gallery, select them and share them to your complaint or bank.'**
  String get safeRecScreens;

  /// No description provided for @safeGuardianIntro.
  ///
  /// In en, this message translates to:
  /// **'Choose a family member. When FraudShield sees a scam happening, the STOP screen offers one tap to call or message them. Nothing is sent automatically and no message content is shared.'**
  String get safeGuardianIntro;

  /// No description provided for @safeGuardianName.
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get safeGuardianName;

  /// No description provided for @safeGuardianNumber.
  ///
  /// In en, this message translates to:
  /// **'Mobile number'**
  String get safeGuardianNumber;

  /// No description provided for @safeGuardianSave.
  ///
  /// In en, this message translates to:
  /// **'Save guardian'**
  String get safeGuardianSave;

  /// No description provided for @safeGuardianRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove guardian'**
  String get safeGuardianRemove;

  /// No description provided for @safeGuardianSaved.
  ///
  /// In en, this message translates to:
  /// **'{name} is your guardian'**
  String safeGuardianSaved(String name);

  /// No description provided for @safeGuardianInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter a name and a valid Indian mobile number'**
  String get safeGuardianInvalid;

  /// No description provided for @safeGuardianTest.
  ///
  /// In en, this message translates to:
  /// **'Send a test message'**
  String get safeGuardianTest;

  /// No description provided for @safeGuardianTestText.
  ///
  /// In en, this message translates to:
  /// **'FraudShield test: you are my guardian. If I am ever in a scam call, FraudShield will help me call or message you.'**
  String get safeGuardianTestText;

  /// No description provided for @safeUpiHint.
  ///
  /// In en, this message translates to:
  /// **'UPI ID (name@bank) or QR text (upi://pay?...)'**
  String get safeUpiHint;

  /// No description provided for @safeUpiQrNote.
  ///
  /// In en, this message translates to:
  /// **'To check a QR code: scan it with Google Lens or your camera, copy the text and paste it here.'**
  String get safeUpiQrNote;

  /// No description provided for @safeCheck.
  ///
  /// In en, this message translates to:
  /// **'Check'**
  String get safeCheck;

  /// No description provided for @safeVerdictSafe.
  ///
  /// In en, this message translates to:
  /// **'No warning signs found'**
  String get safeVerdictSafe;

  /// No description provided for @safeVerdictSusp.
  ///
  /// In en, this message translates to:
  /// **'Be careful'**
  String get safeVerdictSusp;

  /// No description provided for @safeVerdictBad.
  ///
  /// In en, this message translates to:
  /// **'Do not pay'**
  String get safeVerdictBad;

  /// No description provided for @safeUpiPayee.
  ///
  /// In en, this message translates to:
  /// **'Pays to: {vpa}'**
  String safeUpiPayee(String vpa);

  /// No description provided for @safeReport.
  ///
  /// In en, this message translates to:
  /// **'Report as scam'**
  String get safeReport;

  /// No description provided for @safeReported.
  ///
  /// In en, this message translates to:
  /// **'Reported - thank you'**
  String get safeReported;

  /// No description provided for @safeReportFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not report right now'**
  String get safeReportFailed;

  /// No description provided for @safeCheckFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not check right now - try again'**
  String get safeCheckFailed;

  /// No description provided for @safeCareHint.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get safeCareHint;

  /// No description provided for @safeCareBrand.
  ///
  /// In en, this message translates to:
  /// **'Company (optional, e.g. SBI)'**
  String get safeCareBrand;

  /// No description provided for @safeCareOfficial.
  ///
  /// In en, this message translates to:
  /// **'Official number'**
  String get safeCareOfficial;

  /// No description provided for @safeCareFake.
  ///
  /// In en, this message translates to:
  /// **'Fake customer-care number'**
  String get safeCareFake;

  /// No description provided for @safeCareSusp.
  ///
  /// In en, this message translates to:
  /// **'Not an official number'**
  String get safeCareSusp;

  /// No description provided for @safeCareUnknown.
  ///
  /// In en, this message translates to:
  /// **'Not in our list - check on the company\'s own website'**
  String get safeCareUnknown;

  /// No description provided for @safeCareReal.
  ///
  /// In en, this message translates to:
  /// **'Real numbers'**
  String get safeCareReal;

  /// No description provided for @safeDrillsIntro.
  ///
  /// In en, this message translates to:
  /// **'Short practice rounds. Pick what you would do - there is no right or wrong in front of anyone, just learning.'**
  String get safeDrillsIntro;

  /// No description provided for @safeDrillBest.
  ///
  /// In en, this message translates to:
  /// **'Best: {score}%'**
  String safeDrillBest(int score);

  /// No description provided for @safeDrillNotPlayed.
  ///
  /// In en, this message translates to:
  /// **'Not played yet'**
  String get safeDrillNotPlayed;

  /// No description provided for @safeDrillNext.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get safeDrillNext;

  /// No description provided for @safeDrillFinish.
  ///
  /// In en, this message translates to:
  /// **'Finish'**
  String get safeDrillFinish;

  /// No description provided for @safeDrillResult.
  ///
  /// In en, this message translates to:
  /// **'You scored {score}%'**
  String safeDrillResult(int score);

  /// No description provided for @safeDrillSafe.
  ///
  /// In en, this message translates to:
  /// **'Safe choice'**
  String get safeDrillSafe;

  /// No description provided for @safeDrillRisky.
  ///
  /// In en, this message translates to:
  /// **'Risky choice'**
  String get safeDrillRisky;

  /// No description provided for @homeProtected.
  ///
  /// In en, this message translates to:
  /// **'You\'re protected'**
  String get homeProtected;

  /// No description provided for @homeNeedsAttention.
  ///
  /// In en, this message translates to:
  /// **'{n} protections need attention'**
  String homeNeedsAttention(int n);

  /// No description provided for @homeProtectedDetail.
  ///
  /// In en, this message translates to:
  /// **'All shields are up. FraudShield keeps watching in the background.'**
  String get homeProtectedDetail;

  /// No description provided for @homeAttentionDetail.
  ///
  /// In en, this message translates to:
  /// **'Turn them on below to stay fully safe.'**
  String get homeAttentionDetail;

  /// No description provided for @homeQuick.
  ///
  /// In en, this message translates to:
  /// **'Quick actions'**
  String get homeQuick;

  /// No description provided for @homeStatus.
  ///
  /// In en, this message translates to:
  /// **'Protection status'**
  String get homeStatus;

  /// No description provided for @homeStatusSub.
  ///
  /// In en, this message translates to:
  /// **'Tap a card to check again or turn it on'**
  String get homeStatusSub;

  /// No description provided for @homeTools.
  ///
  /// In en, this message translates to:
  /// **'Tools & help'**
  String get homeTools;

  /// No description provided for @homeAccountSub.
  ///
  /// In en, this message translates to:
  /// **'Stop account takeover and SMS theft'**
  String get homeAccountSub;

  /// No description provided for @homeScamHelp.
  ///
  /// In en, this message translates to:
  /// **'Scam help'**
  String get homeScamHelp;

  /// No description provided for @homeCallGuard.
  ///
  /// In en, this message translates to:
  /// **'Call guard'**
  String get homeCallGuard;

  /// No description provided for @homeCleaner.
  ///
  /// In en, this message translates to:
  /// **'Clean leftovers'**
  String get homeCleaner;

  /// No description provided for @homeScammed.
  ///
  /// In en, this message translates to:
  /// **'I was scammed'**
  String get homeScammed;

  /// No description provided for @ui2LoginPromise.
  ///
  /// In en, this message translates to:
  /// **'Stops scams, fake sites and account theft - before they reach you.'**
  String get ui2LoginPromise;

  /// No description provided for @ui1VpnOptions.
  ///
  /// In en, this message translates to:
  /// **'Privacy & DNS options'**
  String get ui1VpnOptions;

  /// No description provided for @homeGroupPhone.
  ///
  /// In en, this message translates to:
  /// **'Your phone & apps'**
  String get homeGroupPhone;

  /// No description provided for @homeGroupPhoneSub.
  ///
  /// In en, this message translates to:
  /// **'Risky apps, cloning and leftover files'**
  String get homeGroupPhoneSub;

  /// No description provided for @homeGroupCalls.
  ///
  /// In en, this message translates to:
  /// **'Calls & messages'**
  String get homeGroupCalls;

  /// No description provided for @homeGroupCallsSub.
  ///
  /// In en, this message translates to:
  /// **'Block scam calls, stop SMS theft'**
  String get homeGroupCallsSub;

  /// No description provided for @permTitle.
  ///
  /// In en, this message translates to:
  /// **'Permissions'**
  String get permTitle;

  /// No description provided for @permSummary.
  ///
  /// In en, this message translates to:
  /// **'{allowed} of {total} allowed'**
  String permSummary(int allowed, int total);

  /// No description provided for @permPending.
  ///
  /// In en, this message translates to:
  /// **'{n} need your OK - tap to allow'**
  String permPending(int n);

  /// No description provided for @permAllDone.
  ///
  /// In en, this message translates to:
  /// **'Everything FraudShield needs is allowed'**
  String get permAllDone;

  /// No description provided for @permIntro.
  ///
  /// In en, this message translates to:
  /// **'FraudShield asks only for what a protection needs. Tap an item: the exact Android page opens - switch it on and come back.'**
  String get permIntro;

  /// No description provided for @permNeedsOk.
  ///
  /// In en, this message translates to:
  /// **'Needs your OK'**
  String get permNeedsOk;

  /// No description provided for @permAllowed.
  ///
  /// In en, this message translates to:
  /// **'Allowed'**
  String get permAllowed;

  /// No description provided for @permByHand.
  ///
  /// In en, this message translates to:
  /// **'Check in phone settings'**
  String get permByHand;

  /// No description provided for @permOptional.
  ///
  /// In en, this message translates to:
  /// **'Optional'**
  String get permOptional;

  /// No description provided for @permAllow.
  ///
  /// In en, this message translates to:
  /// **'Allow'**
  String get permAllow;

  /// No description provided for @permOpen.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get permOpen;

  /// No description provided for @permOn.
  ///
  /// In en, this message translates to:
  /// **'On'**
  String get permOn;

  /// No description provided for @permNameNotifications.
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get permNameNotifications;

  /// No description provided for @permWhyNotifications.
  ///
  /// In en, this message translates to:
  /// **'So scam warnings can reach you.'**
  String get permWhyNotifications;

  /// No description provided for @permNameFullScreen.
  ///
  /// In en, this message translates to:
  /// **'Full-screen alerts'**
  String get permNameFullScreen;

  /// No description provided for @permWhyFullScreen.
  ///
  /// In en, this message translates to:
  /// **'Critical warnings show even on the lock screen.'**
  String get permWhyFullScreen;

  /// No description provided for @permNameNotifAccess.
  ///
  /// In en, this message translates to:
  /// **'Notification access'**
  String get permNameNotifAccess;

  /// No description provided for @permWhyNotifAccess.
  ///
  /// In en, this message translates to:
  /// **'Checks links in your messages and notifications. Message text is never uploaded.'**
  String get permWhyNotifAccess;

  /// No description provided for @permNameFiles.
  ///
  /// In en, this message translates to:
  /// **'All files access'**
  String get permNameFiles;

  /// No description provided for @permWhyFiles.
  ///
  /// In en, this message translates to:
  /// **'Scans downloaded APK and PDF files for danger.'**
  String get permWhyFiles;

  /// No description provided for @permNamePhone.
  ///
  /// In en, this message translates to:
  /// **'Phone'**
  String get permNamePhone;

  /// No description provided for @permWhyPhone.
  ///
  /// In en, this message translates to:
  /// **'Checks call forwarding and powers the scam alarm.'**
  String get permWhyPhone;

  /// No description provided for @permNameVpn.
  ///
  /// In en, this message translates to:
  /// **'Fake-website protection (VPN)'**
  String get permNameVpn;

  /// No description provided for @permWhyVpn.
  ///
  /// In en, this message translates to:
  /// **'Blocks fake and dangerous sites in every app.'**
  String get permWhyVpn;

  /// No description provided for @permNameCallScreening.
  ///
  /// In en, this message translates to:
  /// **'Caller ID & spam'**
  String get permNameCallScreening;

  /// No description provided for @permWhyCallScreening.
  ///
  /// In en, this message translates to:
  /// **'Lets Call guard stop scam calls.'**
  String get permWhyCallScreening;

  /// No description provided for @permNameContacts.
  ///
  /// In en, this message translates to:
  /// **'Contacts'**
  String get permNameContacts;

  /// No description provided for @permWhyContacts.
  ///
  /// In en, this message translates to:
  /// **'Your saved contacts are never blocked.'**
  String get permWhyContacts;

  /// No description provided for @permNameCallLog.
  ///
  /// In en, this message translates to:
  /// **'Call history'**
  String get permNameCallLog;

  /// No description provided for @permWhyCallLog.
  ///
  /// In en, this message translates to:
  /// **'People you called are never blocked.'**
  String get permWhyCallLog;

  /// No description provided for @permNameUsage.
  ///
  /// In en, this message translates to:
  /// **'Usage access'**
  String get permNameUsage;

  /// No description provided for @permWhyUsage.
  ///
  /// In en, this message translates to:
  /// **'Notices apps that open by themselves.'**
  String get permWhyUsage;

  /// No description provided for @permNameBattery.
  ///
  /// In en, this message translates to:
  /// **'Battery: no restrictions'**
  String get permNameBattery;

  /// No description provided for @permWhyBattery.
  ///
  /// In en, this message translates to:
  /// **'Keeps protection running in the background.'**
  String get permWhyBattery;

  /// No description provided for @permNameAutostart.
  ///
  /// In en, this message translates to:
  /// **'Autostart (phone maker setting)'**
  String get permNameAutostart;

  /// No description provided for @permWhyAutostart.
  ///
  /// In en, this message translates to:
  /// **'Lets protection start again after a restart. Android cannot show us this one - please check it.'**
  String get permWhyAutostart;
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
