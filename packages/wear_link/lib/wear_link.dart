import 'dart:convert';

import 'package:flutter/services.dart';

class WearPackage {
  final String packageName;
  final String? versionName;
  final int versionCode;
  final int? firstInstallTime;
  final int? lastUpdateTime;
  final String? installer;

  const WearPackage({
    required this.packageName,
    required this.versionName,
    required this.versionCode,
    this.firstInstallTime,
    this.lastUpdateTime,
    this.installer,
  });

  factory WearPackage.fromJson(Map<String, dynamic> json) => WearPackage(
    packageName: json['packageName'] as String,
    versionName: json['versionName'] as String?,
    versionCode: (json['versionCode'] as num).toInt(),
    firstInstallTime: (json['firstInstallTime'] as num?)?.toInt(),
    lastUpdateTime: (json['lastUpdateTime'] as num?)?.toInt(),
    installer: json['installer'] as String?,
  );

  Map<String, dynamic> toJson() => {
    'packageName': packageName,
    'versionName': versionName,
    'versionCode': versionCode,
    'firstInstallTime': firstInstallTime,
    'lastUpdateTime': lastUpdateTime,
    'installer': installer,
  };
}

class WearStatus {
  final String node;
  final String packageName;
  final bool canInstall;
  final int? sdk;
  final String? helperVersion;

  const WearStatus({
    required this.node,
    required this.packageName,
    required this.canInstall,
    this.sdk,
    this.helperVersion,
  });
}

class WearInstallResult {
  final int status;
  final String message;

  const WearInstallResult(this.status, this.message);
}

class WearLink {
  static const MethodChannel _channel = MethodChannel(
    'dev.imranr.obtainium/wear_link',
  );

  static Future<WearStatus?> status() async {
    final body = await _channel.invokeMethod<String>('status');
    if (body == null) return null;
    final json = jsonDecode(body) as Map<String, dynamic>;
    return WearStatus(
      node: json['node'] as String? ?? 'Watch',
      packageName: json['packageName'] as String? ?? '',
      canInstall: json['canInstall'] as bool? ?? false,
      sdk: (json['sdk'] as num?)?.toInt(),
      helperVersion: json['helperVersion'] as String?,
    );
  }

  static Future<List<WearPackage>?> packages() async {
    final body = await _channel.invokeMethod<String>('packages');
    if (body == null) return null;
    return (jsonDecode(body) as List<dynamic>)
        .map((e) => WearPackage.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  static Future<WearInstallResult> install(
    List<String> paths,
    String packageName,
  ) async {
    final body = await _channel.invokeMethod<String>('install', {
      'paths': paths,
      'packageName': packageName,
    });
    final json = jsonDecode(body!) as Map<String, dynamic>;
    return WearInstallResult(
      (json['status'] as num).toInt(),
      json['message'] as String? ?? '',
    );
  }
}
