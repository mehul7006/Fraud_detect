import 'package:flutter/material.dart';
import '../core/api_client.dart';

class ThreatsScreen extends StatefulWidget {
  final ApiClient? api; // injectable for tests
  const ThreatsScreen({super.key, this.api});
  @override
  State<ThreatsScreen> createState() => _ThreatsScreenState();
}

class _ThreatsScreenState extends State<ThreatsScreen> {
  ApiClient get _api => widget.api ?? ApiClient.instance;
  late Future<List<dynamic>> _f = _api.threats();

  Color _c(String s) => switch (s) { 'CRITICAL' => Colors.red, 'HIGH' => Colors.deepOrange, 'MEDIUM' => Colors.orange, _ => Colors.grey };

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Threat history')),
        body: FutureBuilder<List<dynamic>>(
          future: _f,
          builder: (c, s) {
            if (s.hasError) return const Center(child: Text('Could not load'));
            if (!s.hasData) return const Center(child: CircularProgressIndicator());
            if (s.data!.isEmpty) return const Center(child: Text('No threats found. You are safe.'));
            return RefreshIndicator(
              onRefresh: () async => setState(() => _f = _api.threats()),
              child: ListView(children: [
                for (final t in s.data!)
                  ListTile(
                    leading: Icon(Icons.warning, color: _c(t['severity'])),
                    title: Text(t['title']),
                    subtitle: Text('${t['detail'] ?? ''}\n${t['createdAt']}'),
                    isThreeLine: true,
                    trailing: t['status'] == 'OPEN'
                        ? TextButton(onPressed: () async { await _api.resolve(t['id']); setState(() => _f = _api.threats()); }, child: const Text('Resolve'))
                        : const Icon(Icons.check, color: Colors.green),
                  ),
              ]),
            );
          },
        ),
      );
}
