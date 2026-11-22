import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../core/native_bridge.dart';
import 'consent_screen.dart';
import 'states.dart';
import '../core/protection_engine.dart';
import '../core/settings_store.dart';
import '../l10n/l10n.dart';

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

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _domain.dispose();
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
    if (mounted) setState(() { _apps = apps; _disabled = d; _trusted = t; _language = lang; _loading = false; });
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

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.linkSettings)),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : ListView(padding: const EdgeInsets.all(16), children: [
                Text(context.l10n.language, style: Theme.of(context).textTheme.titleMedium),
                RadioGroup<String>(
                  groupValue: _language,
                  onChanged: (v) => _setLanguage(v!),
                  child: Column(children: [
                    for (final (code, label) in [
                      ('system', context.l10n.languageSystem),
                      ('en', context.l10n.languageEnglish),
                      ('hi', context.l10n.languageHindi),
                    ])
                      RadioListTile<String>(key: Key('language-$code'), dense: true, title: Text(label), value: code),
                  ]),
                ),
                const Divider(height: 32),
                // 9.4: privacy policy + in-app account deletion (Google Play requirement)
                Text(context.l10n.accountSection, style: Theme.of(context).textTheme.titleMedium),
                ListTile(
                  leading: const Icon(Icons.privacy_tip_outlined),
                  title: Text(context.l10n.privacyPolicy),
                  trailing: const Icon(Icons.open_in_new),
                  onTap: () => NativeBridge.openUrl(kPrivacyUrl),
                ),
                ListTile(
                  key: const Key('delete-account'),
                  leading: Icon(Icons.delete_forever, color: Colors.red.shade800),
                  title: Text(context.l10n.deleteAccount, style: TextStyle(color: Colors.red.shade800)), // 8.12: 4.5:1 contrast
                  onTap: _confirmDelete,
                ),
                const Divider(height: 32),
                Text(context.l10n.trustedSites, style: Theme.of(context).textTheme.titleMedium),
                Text(context.l10n.trustedSitesBody),
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
                  IconButton(key: const Key('trusted-domain-add'), icon: const Icon(Icons.add_circle), tooltip: context.l10n.trustDomain, onPressed: _add),
                ]),
                for (final d in _trusted)
                  ListTile(
                    dense: true,
                    leading: const Icon(Icons.verified_user_outlined),
                    title: Text(d),
                    trailing: IconButton(
                        icon: const Icon(Icons.close),
                        tooltip: context.l10n.removeDomain(d),
                        onPressed: () async {
                          await store.removeTrustedDomain(d);
                          await _load();
                        }),
                  ),
                const Divider(height: 32),
                Text(context.l10n.appsToWatch, style: Theme.of(context).textTheme.titleMedium),
                Text(context.l10n.appsToWatchBody),
                if (_apps.isEmpty) Padding(padding: const EdgeInsets.all(16), child: Text(context.l10n.noAppsFound)),
                for (final a in _apps)
                  SwitchListTile(
                    key: Key('app-${a['packageName']}'),
                    dense: true,
                    title: Text(a['label'] as String),
                    subtitle: Text(a['packageName'] as String),
                    value: !_disabled.contains(a['packageName']),
                    onChanged: (on) async {
                      await store.setPackageMonitored(a['packageName'] as String, on);
                      await _load();
                    },
                  ),
              ]),
      );
}
