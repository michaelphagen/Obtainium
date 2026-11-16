import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:android_package_manager/android_package_manager.dart';
import 'package:obtainium/core/logging/app_logger.dart';
import 'package:path_provider/path_provider.dart';
import 'package:wear_link/wear_link.dart';

const String watchIdSuffix = '@watch';

bool isWatchId(String? id) => id != null && id.endsWith(watchIdSuffix);

String watchBaseId(String id) =>
    isWatchId(id) ? id.substring(0, id.length - watchIdSuffix.length) : id;

String toWatchId(String packageName) =>
    '${watchBaseId(packageName)}$watchIdSuffix';

class WatchPackageInfo extends PackageInfo {
  final String? installer;

  const WatchPackageInfo({
    required super.packageName,
    super.versionName,
    super.versionCode,
    super.firstInstallTime,
    super.lastUpdateTime,
    this.installer,
  }) : super(installLocation: AndroidInstallLocation.unspecified);
}

class WatchService {
  WatchService._();

  static final WatchService instance = WatchService._();

  static const Duration _maxAge = Duration(seconds: 30);

  Map<String, WearPackage>? _packages;
  WearStatus? _status;
  DateTime? _fetchedAt;
  Future<void>? _refreshing;

  Future<File> _cacheFile() async {
    final dir = await getApplicationSupportDirectory();
    return File('${dir.path}/watch_packages.json');
  }

  Future<void> _loadCache() async {
    if (_packages != null) return;
    try {
      final file = await _cacheFile();
      if (file.existsSync()) {
        final list = jsonDecode(await file.readAsString()) as List<dynamic>;
        _packages = {
          for (final p in list.map(
            (e) => WearPackage.fromJson(e as Map<String, dynamic>),
          ))
            p.packageName: p,
        };
      }
    } catch (e) {
      AppLogger.warn('Could not read the cached watch app list', error: e);
    }
    _packages ??= {};
  }

  Future<void> refresh({bool force = false}) {
    final fetchedAt = _fetchedAt;
    if (!force &&
        fetchedAt != null &&
        DateTime.now().difference(fetchedAt) < _maxAge) {
      return Future.value();
    }
    return _refreshing ??= _refresh().whenComplete(() => _refreshing = null);
  }

  Future<void> _refresh() async {
    await _loadCache();
    try {
      _status = await WearLink.status();
      final list = _status == null ? null : await WearLink.packages();
      if (list != null) {
        _packages = {for (final p in list) p.packageName: p};
        await (await _cacheFile()).writeAsString(
          jsonEncode(list.map((p) => p.toJson()).toList()),
        );
      }
    } catch (e) {
      _status = null;
      AppLogger.warn('Could not reach the watch helper', error: e);
    } finally {
      _fetchedAt = DateTime.now();
    }
  }

  Future<WearStatus?> status({bool force = false}) async {
    await refresh(force: force);
    return _status;
  }

  Future<WatchPackageInfo?> installedInfo(String id) async {
    await refresh();
    await _loadCache();
    final p = _packages![watchBaseId(id)];
    if (p == null) return null;
    return WatchPackageInfo(
      packageName: p.packageName,
      versionName: p.versionName,
      versionCode: p.versionCode,
      firstInstallTime: p.firstInstallTime,
      lastUpdateTime: p.lastUpdateTime,
      installer: p.installer,
    );
  }
}
