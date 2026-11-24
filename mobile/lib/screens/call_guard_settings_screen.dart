import 'package:flutter/material.dart';

import '../core/api_client.dart';
import '../core/call_guard.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';
import 'blocked_calls_screen.dart';
import 'report_number_screen.dart';

/// Dashboard tile (16.x). Also opens the blocked-calls log when the user tapped a call notification.
class CallGuardTile extends StatefulWidget {
  final ApiClient? api;
  const CallGuardTile({super.key, this.api});
  @override
  State<CallGuardTile> createState() => _CallGuardTileState();
}

class _CallGuardTileState extends State<CallGuardTile> {
  CallGuardStatus? _s;

  @override
  void initState() {
    super.initState();
    _load();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        if (await CallGuardNative.takeOpenLog() && mounted) {
          await Navigator.push(context, MaterialPageRoute(builder: (_) => BlockedCallsScreen(api: widget.api)));
          _load();
        }
      } catch (_) {}
    });
  }

  Future<void> _load() async {
    try {
      final s = await CallGuardNative.status();
      if (mounted) setState(() => _s = s);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final s = _s;
    final on = s != null && s.supported && s.roleHeld;
    final sub = s == null
        ? l.checking
        : !s.supported
            ? l.callTileUnsupported
            : on ? l.callTileOn(s.count('REJECT') + s.count('SILENCE')) : l.callTileOff;
    return Card(
      child: ListTile(
        key: const Key('call-guard-tile'),
        leading: Icon(on ? Icons.phone_locked : Icons.phone_locked_outlined, color: on ? Colors.green : Colors.red),
        title: Text(l.callTile),
        subtitle: Text(sub),
        trailing: const Icon(Icons.chevron_right),
        onTap: () async {
          await Navigator.push(context, MaterialPageRoute(builder: (_) => CallGuardSettingsScreen(api: widget.api)));
          _load();
        },
      ),
    );
  }
}

/// 16.6 role request, 16.8 the 4 modes (strong warning before mode 4), allow list, contacts opt-in, list update.
class CallGuardSettingsScreen extends StatefulWidget {
  final ApiClient? api;
  const CallGuardSettingsScreen({super.key, this.api});
  @override
  State<CallGuardSettingsScreen> createState() => _CallGuardSettingsScreenState();
}

class _CallGuardSettingsScreenState extends State<CallGuardSettingsScreen> with WidgetsBindingObserver {
  CallGuardStatus? _s;
  bool _notif = true;
  final _add = TextEditingController();
  String? _addError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _add.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load(); // back from a permission dialog
  }

  Future<void> _load() async {
    final s = await CallGuardNative.status();
    final n = await NativeBridge.hasNotificationPermission();
    if (mounted) setState(() { _s = s; _notif = n; });
  }

  Future<void> _setMode(int m) async {
    final l = context.l10n;
    if (m == 4) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          icon: Icon(Icons.warning_amber_rounded, color: Colors.orange.shade800, size: 36),
          title: Text(l.callMode4WarnTitle),
          content: SingleChildScrollView(child: Text(l.callMode4WarnBody, key: const Key('call-mode4-warning'))),
          actions: [
            TextButton(key: const Key('call-mode4-cancel'), onPressed: () => Navigator.pop(c, false), child: Text(l.callCancel)),
            FilledButton(
              key: const Key('call-mode4-confirm'),
              style: FilledButton.styleFrom(backgroundColor: Colors.red.shade800),
              onPressed: () => Navigator.pop(c, true),
              child: Text(l.callMode4Confirm),
            ),
          ],
        ),
      );
      if (ok != true) return;
    }
    await CallGuardNative.setMode(m);
    await _load();
  }

  Future<void> _addAllowed() async {
    final c = await CallGuardNative.allow(_add.text.trim());
    if (!mounted) return;
    if (c == null) {
      setState(() => _addError = context.l10n.callAllowInvalid);
    } else {
      _add.clear();
      setState(() => _addError = null);
      await _load();
    }
  }

  Future<void> _sync() async {
    final l = context.l10n;
    final r = await CallGuardNative.sync();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(r['numbers'] == true ? l.callSyncDone : l.callSyncFailed)));
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final s = _s;
    return Scaffold(
      appBar: AppBar(title: Text(l.callTitle)),
      body: s == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(padding: const EdgeInsets.all(16), children: [
              Text(l.callIntro),
              const SizedBox(height: 12),
              if (!s.supported)
                Card(
                  key: const Key('call-unsupported'),
                  color: Colors.orange.shade50,
                  child: ListTile(leading: const Icon(Icons.info_outline), title: Text(l.callUnsupported)),
                )
              else if (!s.roleHeld)
                Card(
                  key: const Key('call-role-card'),
                  color: Colors.orange.shade50,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(l.callRoleTitle, style: const TextStyle(fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      Text(l.callRoleBody),
                      Align(
                        alignment: Alignment.centerRight,
                        child: FilledButton(
                          key: const Key('call-role-allow'),
                          onPressed: () async { await CallGuardNative.requestRole(); await _load(); },
                          child: Text(l.callRoleButton),
                        ),
                      ),
                    ]),
                  ),
                )
              else
                ListTile(
                  key: const Key('call-role-on'),
                  leading: const Icon(Icons.verified_user, color: Colors.green),
                  title: Text(l.callRoleOn),
                  subtitle: Text(l.callListInfo(s.scamListSize)),
                  trailing: TextButton(onPressed: _sync, child: Text(l.callSync)),
                ),
              if (s.supported) ...[
                if (!_notif)
                  _permCard(l.callNotifTitle, l.callNotifBody, () async { await NativeBridge.requestNotificationPermission(); await _load(); }),
                if (!s.contactsPermission)
                  _permCard(l.callContactsTitle, l.callContactsBody, () async { await CallGuardNative.requestContacts(); }, button: l.callContactsButton),
                const SizedBox(height: 8),
                Text(l.callModeTitle, style: Theme.of(context).textTheme.titleMedium),
                RadioGroup<int>(
                  groupValue: s.mode,
                  onChanged: (v) { if (v != null) _setMode(v); },
                  child: Column(children: [
                    for (final (m, t, sub) in [
                      (1, l.callMode1, l.callMode1Sub), (2, l.callMode2, l.callMode2Sub),
                      (3, l.callMode3, l.callMode3Sub), (4, l.callMode4, l.callMode4Sub),
                    ])
                      RadioListTile<int>(key: Key('call-mode-$m'), value: m, title: Text(t), subtitle: Text(sub),
                          contentPadding: EdgeInsets.zero),
                  ]),
                ),
                const Divider(),
                ListTile(
                  key: const Key('call-open-log'),
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.history),
                  title: Text(l.callBlockedTitle),
                  subtitle: Text(l.callBlockedSub),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () async {
                    await Navigator.push(context, MaterialPageRoute(builder: (_) => BlockedCallsScreen(api: widget.api)));
                    _load();
                  },
                ),
                const Divider(),
                Text(l.callAllowTitle, style: Theme.of(context).textTheme.titleMedium),
                Row(children: [
                  Expanded(
                    child: TextField(
                      key: const Key('call-allow-input'),
                      controller: _add,
                      keyboardType: TextInputType.phone,
                      decoration: InputDecoration(hintText: l.callAllowHint, errorText: _addError),
                      onSubmitted: (_) => _addAllowed(),
                    ),
                  ),
                  TextButton(key: const Key('call-allow-add'), onPressed: _addAllowed, child: Text(l.callAllowAdd)),
                ]),
                if (s.allow.isEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(l.callAllowEmpty)),
                for (final n in s.allow)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.check_circle_outline, color: Colors.green),
                    title: Text(n),
                    trailing: IconButton(
                      tooltip: l.callRemove,
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () async { await CallGuardNative.allow(n, allow: false); await _load(); },
                    ),
                  ),
                if (s.block.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(l.callBlockTitle, style: Theme.of(context).textTheme.titleMedium),
                  for (final n in s.block)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.block, color: Colors.red),
                      title: Text(n),
                      trailing: IconButton(
                        tooltip: l.callRemove,
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () async { await CallGuardNative.block(n, block: false); await _load(); },
                      ),
                    ),
                ],
              ],
              const Divider(),
              ListTile(
                key: const Key('call-open-report'),
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.flag_outlined),
                title: Text(l.callReportTitle),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ReportNumberScreen(api: widget.api))),
              ),
            ]),
    );
  }

  Widget _permCard(String title, String body, VoidCallback onTap, {String? button}) => Card(
        color: Colors.blue.shade50,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 4),
            Text(body),
            Align(alignment: Alignment.centerRight, child: TextButton(onPressed: onTap, child: Text(button ?? context.l10n.allow))),
          ]),
        ),
      );
}
