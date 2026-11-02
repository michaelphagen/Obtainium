import 'package:easy_localization/easy_localization.dart';
import 'package:obtainium/core/logging/app_logger.dart';
import 'package:obtainium/custom_errors.dart';
import 'package:obtainium/installers/installer.dart';
import 'package:obtainium/providers/source_provider.dart';
import 'package:obtainium/services/watch_service.dart';
import 'package:wear_link/wear_link.dart';

const int _statusSuccess = 0;
const int _statusAborted = 3;

class WatchInstaller extends Installer {
  WatchInstaller(super.settingsProvider);

  @override
  String get modeKey => 'watch';

  @override
  Future<bool> canInstallSilently(App app) async {
    final status = await WatchService.instance.status();
    if (status == null || !status.canInstall) {
      AppLogger.info(
        'App will not be installed silently: the watch helper is not reachable or cannot install: ${app.id}',
      );
      return false;
    }
    final info = await WatchService.instance.installedInfo(app.id);
    final silent =
        info != null &&
        (info.installer == status.packageName ||
            info.packageName == status.packageName);
    if (!silent) {
      AppLogger.info(
        'App will not be installed silently: the watch helper did not install it: ${app.id}',
      );
    }
    return silent;
  }

  @override
  Future<bool> checkPermission() async {
    final status = await WatchService.instance.status(force: true);
    return status?.canInstall ?? false;
  }

  @override
  Future<void> ensurePermission() async {
    final status = await WatchService.instance.status(force: true);
    if (status == null) {
      throw ObtainiumError(tr('watchNotConnected'));
    }
    if (!status.canInstall) {
      throw ObtainiumError(tr('watchCannotInstall'));
    }
  }

  @override
  Future<InstallResult> installApk(
    List<String> apkFilePaths, {
    required String appId,
    Map<String, dynamic> installOptions = const {},
  }) async {
    await ensurePermission();
    final packageName = watchBaseId(appId);
    AppLogger.info(
      'Sending ${apkFilePaths.length} APK(s) for $packageName to the watch',
    );
    final WearInstallResult result;
    try {
      result = await WearLink.install(apkFilePaths, packageName);
    } catch (e) {
      throw ObtainiumError('${tr('watchInstallFailed')}: $e');
    } finally {
      await WatchService.instance.refresh(force: true);
    }
    if (result.status == _statusSuccess) return InstallResult.success();
    if (result.status == _statusAborted) return InstallResult.cancelled();
    AppLogger.warn(
      'Watch install of $packageName failed (${result.status}): ${result.message}',
    );
    throw ObtainiumError(
      '${tr('watchInstallFailed')}: ${result.message.isNotEmpty ? result.message : result.status}',
    );
  }
}
