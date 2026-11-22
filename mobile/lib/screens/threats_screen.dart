import 'package:flutter/material.dart';
import '../core/api_client.dart';
import 'states.dart';
import '../l10n/l10n.dart';

class ThreatsScreen extends StatefulWidget {
  final ApiClient? api; // injectable for tests
  const ThreatsScreen({super.key, this.api});
  @override
  State<ThreatsScreen> createState() => _ThreatsScreenState();
}

class _ThreatsScreenState extends State<ThreatsScreen> {
  ApiClient get _api => widget.api ?? ApiClient.instance;
  late Future<List<dynamic>> _f = _api.threats();

  Future<void> _resolve(int id) async {
    try {
      await _api.resolve(id);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(friendlyError(e, context.l10n))));
      return;
    }
    if (mounted) setState(() { _f = _api.threats(); });
  }

  Color _c(String s) => switch (s) { 'CRITICAL' => Colors.red, 'HIGH' => Colors.deepOrange, 'MEDIUM' => Colors.orange, _ => Colors.grey };

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(context.l10n.threatHistory)),
        body: FutureBuilder<List<dynamic>>(
          future: _f,
          builder: (c, s) {
            if (s.hasError) return ErrorState(error: s.error!, onRetry: () => setState(() { _f = _api.threats(); }));
            if (!s.hasData) return const Center(child: CircularProgressIndicator());
            if (s.data!.isEmpty) return EmptyState(icon: Icons.verified_user, text: context.l10n.noThreats);
            return RefreshIndicator(
              onRefresh: () {
                setState(() { _f = _api.threats(); });   // block body: setState must not return a Future
                return _f;                               // keeps the pull-to-refresh spinner until loaded
              },
              child: ListView(children: [
                for (final t in s.data!)
                  ListTile(
                    leading: Icon(Icons.warning, color: _c(t['severity'])),
                    title: Text(t['title']),
                    subtitle: Text('${t['detail'] ?? ''}\n${t['createdAt']}'),
                    isThreeLine: true,
                    trailing: t['status'] == 'OPEN'
                        ? TextButton(onPressed: () => _resolve(t['id'] as int), child: Text(context.l10n.resolve))
                        : const Icon(Icons.check, color: Colors.green),
                  ),
              ]),
            );
          },
        ),
      );
}
