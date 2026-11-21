import 'package:flutter/material.dart';
import '../core/native_bridge.dart';
import '../core/protection_engine.dart';
import '../core/settings_store.dart';

/// Link-protection settings (4.9): which apps are watched, and domains the user trusts.
class SettingsScreen extends StatefulWidget {
  final ProtectionEngine engine;
  const SettingsScreen({super.key, required this.engine});
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
    if (mounted) setState(() { _apps = apps; _disabled = d; _trusted = t; _loading = false; });
  }

  Future<void> _add() async {
    final d = SettingsStore.normaliseDomain(_domain.text);
    if (d == null) {
      setState(() => _error = 'Enter a domain like example.com');
      return;
    }
    await store.addTrustedDomain(d);
    _domain.clear();
    setState(() => _error = null);
    await _load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Link settings')),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : ListView(padding: const EdgeInsets.all(16), children: [
                Text('Trusted websites', style: Theme.of(context).textTheme.titleMedium),
                const Text('Links to these sites (and their sub-domains) are never scanned or flagged.'),
                const SizedBox(height: 8),
                Row(children: [
                  Expanded(
                    child: TextField(
                      key: const Key('trusted-domain-field'),
                      controller: _domain,
                      keyboardType: TextInputType.url,
                      decoration: InputDecoration(labelText: 'Domain', hintText: 'example.com', errorText: _error),
                      onSubmitted: (_) => _add(),
                    ),
                  ),
                  IconButton(key: const Key('trusted-domain-add'), icon: const Icon(Icons.add_circle), tooltip: 'Trust this domain', onPressed: _add),
                ]),
                for (final d in _trusted)
                  ListTile(
                    dense: true,
                    leading: const Icon(Icons.verified_user_outlined),
                    title: Text(d),
                    trailing: IconButton(
                        icon: const Icon(Icons.close),
                        tooltip: 'Remove $d',
                        onPressed: () async {
                          await store.removeTrustedDomain(d);
                          await _load();
                        }),
                  ),
                const Divider(height: 32),
                Text('Apps to watch', style: Theme.of(context).textTheme.titleMedium),
                const Text('Turn an app off and links shown in its notifications are ignored.'),
                if (_apps.isEmpty) const Padding(padding: EdgeInsets.all(16), child: Text('No apps found.')),
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
