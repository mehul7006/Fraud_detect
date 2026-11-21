import 'package:flutter_test/flutter_test.dart';
import 'package:fraudshield/core/url_extractor.dart';

/// Same 20 samples as android-native/test/UrlExtractorTest.kt (roadmap 4.6).
void main() {
  final cases = <String, List<String>>{
    'go to http://example.com now': ['http://example.com'],
    'https://shop.example.com/a?x=1&y=2 ok': ['https://shop.example.com/a?x=1&y=2'],
    'visit www.example.com today': ['www.example.com'],
    'open flipkart.com': ['flipkart.com'],
    'claim at fleepkart.com/offer': ['fleepkart.com/offer'],
    'click bit.ly/abc123': ['bit.ly/abc123'],
    'install http://evil.xyz/app.apk': ['http://evil.xyz/app.apk'],
    'See https://example.com/page.': ['https://example.com/page'],
    'go www.example.com, or a.example.org!': ['www.example.com', 'a.example.org'],
    '(https://example.com/x)': ['https://example.com/x'],
    'link [www.example.com/a] here': ['www.example.com/a'],
    'अपना खाता देखें http://sbi-kyc.top/login अभी': ['http://sbi-kyc.top/login'],
    'देखें:http://x-offer.click/a': ['http://x-offer.click/a'],
    'http://a.com/1 and https://b.in/2': ['http://a.com/1', 'https://b.in/2'],
    'Your OTP is 123456, do not share it': [],
    'mail me at john.smith@example.com please': [],
    'www.example.com and www.example.com': ['www.example.com'],
    'HTTPS://EXAMPLE.COM/Path': ['HTTPS://EXAMPLE.COM/Path'],
    'http://192.168.1.5:8080/login': ['http://192.168.1.5:8080/login'],
    'try evil-site.xyz:8080/x now': ['evil-site.xyz:8080/x'],
  };
  test('has 20 samples', () => expect(cases.length, 20));
  cases.forEach((text, expected) => test('extracts from "$text"', () => expect(UrlExtractor.extract(text), expected)));
  test('hostOf', () {
    expect(UrlExtractor.hostOf('HTTPS://Shop.Example.com/a'), 'shop.example.com');
    expect(UrlExtractor.hostOf('flipkart.com/x'), 'flipkart.com');
  });
}
