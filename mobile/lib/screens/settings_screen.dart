import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/native_bridge.dart';
import 'consent_screen.dart';
import 'states.dart';
import '../core/protection_engine.dart';
import '../core/settings_store.dart';
import '../l10n/l10n.dart';
import '../ui/components.dart';
import '../ui/theme.dart';

/// Link-protection settings (4.9): which apps are watched, and domains the user trusts.
class SettingsScreen extends StatefulWidget {
  final ProtectionEngine engine;
  final ApiClient? api; // injectable for tests; defaults to the engine's client
  const SettingsScreen({super.key, required this.engine, this.api});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  SettingsStore get store => widget.engine.settings;
  List<Map<String, dynamic>> _apps = [];
  Set<String> _disabled = {};
  List<String> _trusted = [];
  final _domain = TextEditingController();
  String? _error;
  bool _loading = true;
  String _language = 'system'; // 8.11
  ({String name, String phone})? _contact; // 7.4
  final _cName = TextEditingController();
  final _cPhone = TextEditingController();
  String? _contactError;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _domain.dispose();
    _cName.dispose();
    _cPhone.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    List<Map<String, dynamic>> apps = [];
    try {
      apps = (await NativeBridge.auditApps()).where((a) => a['isSystem'] != true).toList()
        ..sort((a, b) => (a['label'] as String).toLowerCase().compareTo((b['label'] as String).toLowerCase()));
    } catch (_) {}
    final d = await store.disabledPackages();
    final t = await store.trustedDomains();
    final lang = await store.language();
    final contact = await store.emergencyContact();
    if (mounted) setState(() { _apps = apps; _disabled = d; _trusted = t; _language = lang; _contact = contact; _loading = false; });
  }

  Future<void> _add() async {
    final d = SettingsStore.normaliseDomain(_domain.text);
    if (d == null) {
      setState(() => _error = context.l10n.domainInvalid);
      return;
    }
    await store.addTrustedDomain(d);
    _domain.clear();
    setState(() => _error = null);
    await _load();
  }

  Future<void> _saveContact() async {
    final ok = await store.setEmergencyContact(_cName.text, _cPhone.text);
    if (!mounted) return;
    if (!ok) {
      setState(() => _contactError = context.l10n.contactInvalid);
      return;
    }
    _cName.clear();
    _cPhone.clear();
    setState(() => _contactError = null);
    await _load();
  }

  Future<void> _confirmDelete() async {
    final l = context.l10n;
    final yes = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        icon: const Icon(Icons.delete_forever, color: Colors.red, size: 40),
        title: Text(l.deleteAccount),
        content: Text(l.deleteAccountBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: Text(l.cancel)),
          FilledButton(
            key: const Key('delete-account-confirm'),
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.pop(c, true),
            child: Text(l.deleteAccountConfirm),
          ),
        ],
      ),
    );
    if (yes != true) return;
    try {
      await (widget.api ?? widget.engine.api).deleteAccount(); // fires onSessionExpired -> login screen
      await NativeBridge.clearBackgroundSession().catchError((_) {});
      if (mounted) Navigator.of(context).popUntil((r) => r.isFirst);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(friendlyError(e, l))));
    }
  }

  /// 8.11: switches the whole app at once (MaterialApp listens to [appLocale]) and remembers it.
  Future<void> _setLanguage(String code) async {
    await store.setLanguage(code);
    appLocale.value = localeFromSetting(code);
    if (mounted) setState(() => _language = code);
  }

  Widget _group(BuildContext context, IconData icon, Feature f, String title, String? body, List<Widget> children) =>
      Card(
        margin: const EdgeInsets.only(top: 14),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 10),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Row(children: [
              FeatureIcon(icon, f, size: 40),
              const SizedBox(width: 12),
              Expanded(child: Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700))),
            ]),
            if (body != null) Padding(padding: const EdgeInsets.only(top: 10), child: Text(body)),
            const SizedBox(height: 6),
            ...children,
          ]),
        ),
      );

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.linkSettings)),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 32), children: [
                _group(context, Icons.translate_rounded, Feature.message, context.l10n.language, null, [
                  RadioGroup<String>(
                    groupValue: _language,
                    onChanged: (v) => _setLanguage(v!),
                    child: Column(children: [
                      for (final (code, label) in [
                        ('system', context.l10n.languageSystem),
                        ('en', context.l10n.languageEnglish),
                        ('hi', context.l10n.languageHindi),
                      ])
                        RadioListTile<String>(key: Key('language-$code'), dense: true, contentPadding: EdgeInsets.zero, title: Text(label), value: code),
                    ]),
                  ),
                ]),
                // 7.4: optional emergency contact, kept on the phone only
                _group(context, Icons.family_restroom_rounded, Feature.account, context.l10n.contactSection, context.l10n.contactBody, [
                  if (_contact != null)
                    ListTile(
                      key: const Key('contact-saved'),
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.contact_phone_outlined),
                      title: Text(context.l10n.contactSaved(_contact!.name)),
                      subtitle: Text(_contact!.phone),
                      trailing: IconButton(
                        key: const Key('contact-remove'),
                        icon: const Icon(Icons.close),
                        tooltip: context.l10n.contactRemove,
                        onPressed: () async { await store.clearEmergencyContact(); await _load(); },
                      ),
                    )
                  else ...[
                    const SizedBox(height: 6),
                    TextField(key: const Key('contact-name'), controller: _cName,
                        decoration: InputDecoration(labelText: context.l10n.contactName, prefixIcon: const Icon(Icons.person_outline_rounded))),
                    const SizedBox(height: 10),
                    TextField(key: const Key('contact-phone'), controller: _cPhone, keyboardType: TextInputType.phone,
                        decoration: InputDecoration(labelText: context.l10n.contactPhone, errorText: _contactError, prefixIcon: const Icon(Icons.phone_outlined))),
                    Align(alignment: Alignment.centerRight,
                        child: TextButton(key: const Key('contact-save'), onPressed: _saveContact, child: Text(context.l10n.contactSave))),
                  ],
                ]),
                _group(context, Icons.verified_user_rounded, Feature.web, context.l10n.trustedSites, context.l10n.trustedSitesBody, [
                  const SizedBox(height: 8),
                  Row(children: [
                    Expanded(
                      child: TextField(
                        key: const Key('trusted-domain-field'),
                        controller: _domain,
                        keyboardType: TextInputType.url,
                        decoration: InputDecoration(labelText: context.l10n.domain, hintText: 'example.com', errorText: _error),
                        onSubmitted: (_) => _add(),
                      ),
                    ),
                    IconButton(key: const Key('trusted-domain-add'), icon: Icon(Icons.add_circle_rounded, color: Theme.of(context).colorScheme.primary, size: 32),
                        tooltip: context.l10n.trustDomain, onPressed: _add),
                  ]),
                  for (final d in _trusted)
                    ListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      leading: Icon(Icons.verified_user_outlined, color: Tone.safe.fg(context)),
                      title: Text(d),
                      trailing: IconButton(
                          icon: const Icon(Icons.close),
                          tooltip: context.l10n.removeDomain(d),
                          onPressed: () async {
                            await store.removeTrustedDomain(d);
                            await _load();
                          }),
                    ),
                ]),
                _group(context, Icons.apps_rounded, Feature.apps, context.l10n.appsToWatch, context.l10n.appsToWatchBody, [
                  if (_apps.isEmpty) Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: Text(context.l10n.noAppsFound)),
                  for (final a in _apps)
                    SwitchListTile(
                      key: Key('app-${a['packageName']}'),
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      title: Text(a['label'] as String),
                      subtitle: Text(a['packageName'] as String),
                      value: !_disabled.contains(a['packageName']),
                      onChanged: (on) async {
                        await store.setPackageMonitored(a['packageName'] as String, on);
                        await _load();
                      },
                    ),
                ]),
                // 9.4: privacy policy + in-app account deletion (Google Play requirement)
                _group(context, Icons.manage_accounts_rounded, Feature.history, context.l10n.accountSection, null, [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.privacy_tip_outlined),
                    title: Text(context.l10n.privacyPolicy),
                    trailing: const Icon(Icons.open_in_new_rounded),
                    onTap: () => NativeBridge.openUrl(kPrivacyUrl),
                  ),
                  ListTile(
                    key: const Key('delete-account'),
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.delete_forever_rounded, color: Tone.danger.fg(context)),
                    title: Text(context.l10n.deleteAccount, style: TextStyle(color: Tone.danger.fg(context))), // 8.12: 4.5:1 contrast
                    onTap: _confirmDelete,
                  ),
                ]),
              ]),
      );
}
