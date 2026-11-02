import 'package:flutter_test/flutter_test.dart';
import 'package:obtainium/models/app.dart';
import 'package:obtainium/providers/apps_provider_install.dart';
import 'package:obtainium/services/watch_service.dart';

void main() {
  group('watch ids', () {
    test('a watch id is the package name with the watch suffix', () {
      expect(toWatchId('com.example.app'), 'com.example.app@watch');
      expect(toWatchId('com.example.app@watch'), 'com.example.app@watch');
      expect(isWatchId('com.example.app@watch'), isTrue);
      expect(isWatchId('com.example.app'), isFalse);
      expect(isWatchId(null), isFalse);
    });

    test('the base id drops only the watch suffix', () {
      expect(watchBaseId('com.example.app@watch'), 'com.example.app');
      expect(watchBaseId('com.example.app'), 'com.example.app');
    });
  });

  test('an app can pick the watch as its install method', () {
    const app = App(
      id: 'com.example.app',
      url: 'https://example.com/app',
      author: 'Author',
      name: 'App',
      latestVersion: '1.0',
      preferredApkIndex: 0,
      additionalSettings: {'installerMode': 'watch'},
    );
    expect(installerModeFor(app, 'system'), 'watch');
  });

  test('watch installs report their own result instead of being polled', () {
    expect(
      shouldPollForInstallConfirmation('watch', needsBGWorkaround: false),
      isFalse,
    );
    expect(
      shouldPollForInstallConfirmation('stock', needsBGWorkaround: false),
      isTrue,
    );
  });
}
