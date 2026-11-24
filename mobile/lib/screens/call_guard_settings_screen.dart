import 'package:flutter/material.dart';

import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

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
    return FeatureTile(
      tileKey: const Key('call-guard-tile'),
      icon: on ? Icons.phone_locked_rounded : Icons.phone_locked_outlined, feature: Feature.call,
      title: l.callTile,
      status: sub,
      tone: on ? Tone.safe : Tone.danger,
      trailingIcon: Icons.chevron_right_rounded,
      onTap: () async {
          await Navigator.push(context, MaterialPageRoute(builder: (_) => CallGuardSettingsScreen(api: widget.api)));
          _load();
      },
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
          icon: Icon(Icons.warning_amber_rounded, color: Tone.warn.fg(context), size: 36),
          title: Text(l.callMode4WarnTitle),
          content: SingleChildScrollView(child: Text(l.callMode4WarnBody, key: const Key('call-mode4-warning'))),
          actions: [
            TextButton(key: const Key('call-mode4-cancel'), onPressed: () => Navigator.pop(c, false), child: Text(l.callCancel)),
            FilledButton(
              key: const Key('call-mode4-confirm'),
              style: FilledButton.styleFrom(backgroundColor: Tone.danger.fg(context),
                  foregroundColor: Theme.of(context).brightness == Brightness.dark ? AppColors.ink : Colors.white),
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
          : ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
              IntroBanner(icon: Icons.phone_locked_rounded, feature: Feature.call, text: l.callIntro),
              if (!s.supported)
                Card(
                  key: const Key('call-unsupported'),
                  color: Tone.warn.bg(context),
                  child: ListTile(leading: Icon(Icons.info_outline_rounded, color: Tone.warn.fg(context)), title: Text(l.callUnsupported)),
                )
              else if (!s.roleHeld)
                Card(
                  key: const Key('call-role-card'),
                  color: Tone.warn.bg(context),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Row(children: [
                        Icon(Icons.phone_disabled_rounded, color: Tone.warn.fg(context)),
                        const SizedBox(width: 10),
                        Expanded(child: Text(l.callRoleTitle, style: const TextStyle(fontWeight: FontWeight.w700))),
                      ]),
                      const SizedBox(height: 6),
                      Text(l.callRoleBody),
                      const SizedBox(height: 10),
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
                Card(
                  color: Tone.safe.bg(context),
                  child: ListTile(
                    key: const Key('call-role-on'),
                    leading: Icon(Icons.verified_user_rounded, color: Tone.safe.fg(context), size: 32),
                    title: Text(l.callRoleOn),
                    subtitle: Text(l.callListInfo(s.scamListSize)),
                    trailing: TextButton(onPressed: _sync, child: Text(l.callSync)),
                  ),
                ),
              if (s.supported) ...[
                if (!_notif)
                  _permCard(Icons.notifications_active_outlined, l.callNotifTitle, l.callNotifBody,
                      () async { await NativeBridge.requestNotificationPermission(); await _load(); }),
                if (!s.contactsPermission)
                  _permCard(Icons.contacts_rounded, l.callContactsTitle, l.callContactsBody,
                      () async { await CallGuardNative.requestContacts(); }, button: l.callContactsButton),
                SectionTitle(l.callModeTitle),
                Card(
                  child: RadioGroup<int>(
                    groupValue: s.mode,
                    onChanged: (v) { if (v != null) _setMode(v); },
                    child: Column(children: [
                      for (final (m, t, sub) in [
                        (1, l.callMode1, l.callMode1Sub), (2, l.callMode2, l.callMode2Sub),
                        (3, l.callMode3, l.callMode3Sub), (4, l.callMode4, l.callMode4Sub),
                      ])
                        RadioListTile<int>(key: Key('call-mode-$m'), value: m,
                            title: Text(t, style: TextStyle(fontWeight: FontWeight.w600, color: m == 4 ? Tone.danger.fg(context) : null)),
                            subtitle: Text(sub)),
                    ]),
                  ),
                ),
                const SizedBox(height: 6),
                FeatureTile(
                  tileKey: const Key('call-open-log'),
                  icon: Icons.history_rounded, feature: Feature.history,
                  title: l.callBlockedTitle, status: l.callBlockedSub,
                  trailingIcon: Icons.chevron_right_rounded,
                  onTap: () async {
                    await Navigator.push(context, MaterialPageRoute(builder: (_) => BlockedCallsScreen(api: widget.api)));
                    _load();
                  },
                ),
                SectionTitle(l.callAllowTitle),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 12, 8, 8),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Row(children: [
                        Expanded(
                          child: TextField(
                            key: const Key('call-allow-input'),
                            controller: _add,
                            keyboardType: TextInputType.phone,
                            decoration: InputDecoration(hintText: l.callAllowHint, errorText: _addError,
                                prefixIcon: const Icon(Icons.person_add_alt_rounded)),
                            onSubmitted: (_) => _addAllowed(),
                          ),
                        ),
                        const SizedBox(width: 4),
                        TextButton(key: const Key('call-allow-add'), onPressed: _addAllowed, child: Text(l.callAllowAdd)),
                      ]),
                      if (s.allow.isEmpty)
                        Padding(padding: const EdgeInsets.fromLTRB(4, 10, 4, 6),
                            child: Text(l.callAllowEmpty, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))),
                      for (final n in s.allow)
                        ListTile(
                          contentPadding: const EdgeInsets.only(left: 4),
                          leading: Icon(Icons.check_circle_rounded, color: Tone.safe.fg(context)),
                          title: Text(n),
                          trailing: IconButton(
                            tooltip: l.callRemove,
                            icon: const Icon(Icons.delete_outline_rounded),
                            onPressed: () async { await CallGuardNative.allow(n, allow: false); await _load(); },
                          ),
                        ),
                    ]),
                  ),
                ),
                if (s.block.isNotEmpty) ...[
                  SectionTitle(l.callBlockTitle),
                  Card(
                    child: Column(children: [
                      for (final n in s.block)
                        ListTile(
                          leading: Icon(Icons.block_rounded, color: Tone.danger.fg(context)),
                          title: Text(n),
                          trailing: IconButton(
                            tooltip: l.callRemove,
                            icon: const Icon(Icons.delete_outline_rounded),
                            onPressed: () async { await CallGuardNative.block(n, block: false); await _load(); },
                          ),
                        ),
                    ]),
                  ),
                ],
              ],
              const SizedBox(height: 6),
              FeatureTile(
                tileKey: const Key('call-open-report'),
                icon: Icons.flag_rounded, feature: Feature.scam,
                title: l.callReportTitle,
                trailingIcon: Icons.chevron_right_rounded,
                onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ReportNumberScreen(api: widget.api))),
              ),
            ]),
    );
  }

  Widget _permCard(IconData icon, String title, String body, VoidCallback onTap, {String? button}) =>
      NoticeCard(icon: icon, title: title, body: body, action: button ?? context.l10n.allow, onAction: onTap, tone: Tone.info);
}
