import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/protection_engine.dart';

void main() {
  test('detects forwarded response', () {
    expect(ProtectionEngine.looksForwarded('Voice call forwarding: active, forwarded to +919876543210'), true);
  });
  test('detects not forwarded response', () {
    expect(ProtectionEngine.looksForwarded('Call forwarding not forwarded'), false);
    expect(ProtectionEngine.looksForwarded('Service is inactive'), false);
  });
}
