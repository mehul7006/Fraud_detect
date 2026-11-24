import 'package:flutter/material.dart';

import '../ui/components.dart';
import '../ui/screen_kit.dart';
import '../ui/theme.dart';

import '../core/leftovers.dart';
import '../core/native_bridge.dart';
import '../l10n/l10n.dart';

/// 17.3: review what removed apps left behind. With [package]: one removed app (from the "X left N files"
/// notification). Without: removed apps, orphan folders of popular apps (17.5) and old installers in Download (17.6).
/// Safe defaults: only installers and cache are pre-ticked; nothing is removed without the confirm dialog (17.4).
class LeftoverScreen extends StatefulWidget {
  final String? package;
  const LeftoverScreen({super.key, this.package});
  @override
  State<LeftoverScreen> createState() => _LeftoverScreenState();
}

class _Entry {
  final String path, title, subtitle, group;
  final int bytes;
  bool ticked;
  _Entry(this.path, this.title, this.subtitle, this.group, this.bytes, this.ticked);
}

class _LeftoverScreenState extends State<LeftoverScreen> {
  bool _loading = true, _access = true, _busy = false;
  String? _label;
  List<SettingsCheck> _settings = const [];
  List<RemovedApp> _removed = const [];
  final Map<String, List<_Entry>> _sections = {}; // section title -> entries (insertion order = display order)

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load()); // needs the localizations
  }

  String _groupName(AppLocalizations l, String g) => switch (g) {
        'INSTALLER' => l.leftGroupInstaller, 'CACHE' => l.leftGroupCache, 'MEDIA' => l.leftGroupMedia,
        'DOCUMENT' => l.leftGroupDocument, _ => l.leftGroupOther,
      };
  String _what(AppLocalizations l, String g) => switch (g) {
        'INSTALLER' => l.leftWhatInstaller, 'CACHE' => l.leftWhatCache, 'MEDIA' => l.leftWhatMedia,
        'DOCUMENT' => l.leftWhatDocument, _ => l.leftWhatOther,
      };
  String _apkText(AppLocalizations l, String s) => switch (s) {
        'SAME_INSTALLED' => l.leftApkSame, 'OLDER_THAN_INSTALLED' => l.leftApkOlder, 'NEWER_THAN_INSTALLED' => l.leftApkNewer,
        'NOT_INSTALLED' => l.leftApkNotInstalled, 'FLAGGED' => l.leftApkFlagged, _ => l.leftApkUnreadable,
      };

  _Entry _entry(AppLocalizations l, LeftoverItem i) => _Entry(i.path, i.rel,
      '${i.isDir ? l.leftFolderInfo(i.files, humanSize(i.bytes)) : humanSize(i.bytes)}'
          '${i.note.isNotEmpty ? ' - ${l.leftOrphanFrom(i.note)}' : ''}\n${_what(l, i.group)}',
      i.group, i.bytes, i.tickedByDefault);

  Future<void> _load() async {
    setState(() => _loading = true);
    final l = context.l10n;
    _access = await NativeBridge.hasAllFilesAccess().catchError((_) => false);
    _sections.clear();
    if (_access) {
      try {
        if (widget.package != null) {
          final p = await LeftoverApi.forPackage(widget.package!);
          _label = p.label;
          _settings = p.settings;
          for (final i in p.items) {
            (_sections[_groupName(l, i.group)] ??= []).add(_entry(l, i));
          }
        } else {
          _removed = await LeftoverApi.pending();
          final s = await LeftoverApi.scan();
          if (s.orphans.isNotEmpty) _sections[l.leftOrphans] = s.orphans.map((i) => _entry(l, i)).toList();
          if (s.apks.isNotEmpty) {
            _sections[l.leftApks] = s.apks
                .map((a) => _Entry(a.path, a.label.isEmpty ? a.rel : '${a.label}  (${a.rel})',
                    '${humanSize(a.bytes)}${a.version != null ? ' - v${a.version}' : ''}\n${_apkText(l, a.status)}', 'INSTALLER', a.bytes, a.tickedByDefault))
                .toList();
          }
        }
      } catch (_) {/* shown as "nothing found"; never a crash */}
    }
    if (mounted) setState(() => _loading = false);
  }

  List<_Entry> get _ticked => _sections.values.expand((e) => e).where((e) => e.ticked).toList();

  Future<void> _remove() async {
    final l = context.l10n;
    final sel = _ticked;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: Text(l.leftConfirmTitle(sel.length)),
        content: Text(l.leftConfirmBody),
        actions: [
          TextButton(key: const Key('left-confirm-no'), onPressed: () => Navigator.pop(c, false), child: Text(l.leftCancel)),
          FilledButton(key: const Key('left-confirm-yes'), onPressed: () => Navigator.pop(c, true), child: Text(l.leftConfirmYes)),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() => _busy = true);
    String msg;
    try {
      final r = await LeftoverApi.delete(sel.map((e) => e.path).toList(), package: widget.package);
      msg = r.cancelled ? l.leftCancelled : [l.leftDone(r.trashed, r.deleted), if (r.refused > 0) l.leftRefused(r.refused)].join(' ');
    } catch (_) {
      msg = l.leftCancelled;
    }
    if (!mounted) return;
    setState(() => _busy = false);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg, key: const Key('left-result'))));
    await _load();
  }

  Widget _settingsCard(AppLocalizations l) {
    String kind(String k) => switch (k) {
          'ACCESSIBILITY' => l.leftKindAccessibility, 'NOTIFICATION_ACCESS' => l.leftKindNotif, 'DEVICE_ADMIN' => l.leftKindAdmin,
          'ALWAYS_ON_VPN' => l.leftKindVpn, 'DEFAULT_SMS' => l.leftKindSms, 'DEFAULT_DIALER' => l.leftKindDialer,
          'DEFAULT_BROWSER' => l.leftKindBrowser, 'DEFAULT_HOME' => l.leftKindHome, _ => k,
        };
    return Card(
      key: const Key('left-settings'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(4, 14, 4, 6),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(children: [
              const FeatureIcon(Icons.settings_suggest_rounded, Feature.device, size: 36),
              const SizedBox(width: 10),
              Expanded(child: Text(l.leftSettingsTitle, style: Theme.of(context).textTheme.titleSmall)),
            ]),
          ),
          for (final s in _settings)
            Builder(builder: (context) {
              final tone = s.state == 'CLEAR' ? Tone.safe : s.state == 'LEFTOVER' ? Tone.danger : Tone.neutral;
              return ListTile(
                leading: Icon(s.state == 'CLEAR' ? Icons.check_circle_rounded : s.state == 'LEFTOVER' ? Icons.warning_amber_rounded : Icons.help_outline_rounded,
                    color: tone.fg(context)),
                title: Text(kind(s.kind)),
                subtitle: Text(s.state == 'CLEAR' ? l.leftSetClear : s.state == 'LEFTOVER' ? l.leftSetLeftover : l.leftSetUnknown,
                    style: TextStyle(color: tone.fg(context))),
              );
            }),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    final sel = _ticked;
    final selBytes = sel.fold<int>(0, (a, e) => a + e.bytes);
    return Scaffold(
      appBar: AppBar(title: Text(widget.package != null ? l.leftTitleApp(_label ?? widget.package!) : l.leftTitle)),
      bottomNavigationBar: sel.isEmpty
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                child: FilledButton.icon(
                  key: const Key('left-remove'),
                  onPressed: _busy ? null : _remove,
                  icon: const Icon(Icons.delete_sweep_rounded),
                  label: Text(l.leftDelete(sel.length, humanSize(selBytes))),
                ),
              ),
            ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
              IntroBanner(
                icon: Icons.cleaning_services_rounded, feature: Feature.cleaner, text: l.leftIntro,
                footer: Text(l.leftPrivateNote, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.white)),
              ),
              if (!_access)
                Card(
                  key: const Key('left-need-access'),
                  color: Tone.warn.bg(context),
                  child: ListTile(
                    leading: Icon(Icons.folder_off_rounded, color: Tone.warn.fg(context)),
                    title: Text(l.leftNeedAccess),
                    trailing: TextButton(onPressed: () async { await NativeBridge.openAllFilesAccessSettings(); }, child: Text(l.allow)),
                  ),
                ),
              if (widget.package != null && _settings.isNotEmpty) _settingsCard(l),
              if (widget.package == null && _removed.isNotEmpty) ...[
                SectionTitle(l.leftRemovedApps),
                for (final r in _removed)
                  FeatureTile(
                    tileKey: Key('left-removed-${r.package}'),
                    icon: Icons.auto_delete_rounded, feature: Feature.cleaner,
                    title: r.label,
                    status: l.leftRemovedInfo(r.files, humanSize(r.bytes)),
                    tone: Tone.warn,
                    trailingIcon: Icons.chevron_right_rounded,
                    onTap: () async {
                      await Navigator.push(context, MaterialPageRoute(builder: (_) => LeftoverScreen(package: r.package)));
                      _load();
                    },
                  ),
              ],
              if (_access && _sections.isEmpty && _removed.isEmpty)
                Card(
                  color: Tone.safe.bg(context),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(children: [
                      Icon(Icons.verified_rounded, color: Tone.safe.fg(context), size: 30),
                      const SizedBox(width: 12),
                      Expanded(child: Text(l.leftNothing, key: const Key('left-nothing'),
                          style: TextStyle(color: Tone.safe.fg(context), fontWeight: FontWeight.w600))),
                    ]),
                  ),
                ),
              for (final s in _sections.entries) ...[
                SectionTitle(s.key),
                Card(
                  child: Column(children: [
                    for (final e in s.value)
                      CheckboxListTile(
                        key: Key('left-item-${e.path}'),
                        value: e.ticked,
                        onChanged: _busy ? null : (v) => setState(() => e.ticked = v ?? false),
                        title: Text(e.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text(e.subtitle),
                        isThreeLine: true,
                        controlAffinity: ListTileControlAffinity.leading,
                      ),
                  ]),
                ),
              ],
              if (widget.package != null && _sections.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: OutlinedButton(
                    key: const Key('left-keep'),
                    onPressed: () async {
                      await LeftoverApi.dismiss(widget.package!);
                      if (context.mounted) Navigator.pop(context);
                    },
                    child: Text(l.leftKeep),
                  ),
                ),
            ]),
    );
  }
}

/// Dashboard tile: "Leftover files" with the number of removed apps that left files; opens the screen, and opens a
/// removed app directly when the user tapped its "X left N files" notification.
class LeftoverTile extends StatefulWidget {
  const LeftoverTile({super.key});
  @override
  State<LeftoverTile> createState() => _LeftoverTileState();
}

class _LeftoverTileState extends State<LeftoverTile> with WidgetsBindingObserver {
  int _pending = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final open = await LeftoverApi.takeOpen();
    final p = await LeftoverApi.pending();
    if (!mounted) return;
    setState(() => _pending = p.length);
    if (open != null) {
      await Navigator.push(context, MaterialPageRoute(builder: (_) => LeftoverScreen(package: open)));
      if (mounted) _refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = context.l10n;
    return FeatureTile(
      tileKey: const Key('left-tile'),
      icon: Icons.cleaning_services_rounded, feature: Feature.cleaner,
      title: l.leftTile,
      status: _pending > 0 ? l.leftTilePending(_pending) : l.leftTileSub,
      tone: _pending > 0 ? Tone.warn : Tone.neutral,
      trailingIcon: Icons.chevron_right_rounded,
      onTap: () async {
        await Navigator.push(context, MaterialPageRoute(builder: (_) => const LeftoverScreen()));
        _refresh();
      },
    );
  }
}
