import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:pick_or_save/src/pick_or_save_method_channel.dart';

abstract class PickOrSavePlatform extends PlatformInterface {
  /// Constructs a PickOrSavePlatform.
  PickOrSavePlatform() : super(token: _token);

  static final Object _token = Object();

  static PickOrSavePlatform _instance = MethodChannelPickOrSave();

  /// The default instance of [PickOrSavePlatform] to use.
  ///
  /// Defaults to [MethodChannelPickOrSave].
  static PickOrSavePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PickOrSavePlatform] when
  /// they register themselves.
  static set instance(PickOrSavePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> directoryPicker({DirectoryPickerParams? params}) {
    throw UnimplementedError('directoryPicker() has not been implemented.');
  }

  Future<List<DocumentFile>?> directoryDocumentsPicker(
      {DirectoryDocumentsPickerParams? params}) {
    throw UnimplementedError(
        'directoryFilesPicker() has not been implemented.');
  }

  Future<List<String>?> filePicker({FilePickerParams? params}) {
    throw UnimplementedError('filePicker() has not been implemented.');
  }

  Future<List<String>?> fileSaver({FileSaverParams? params}) {
    throw UnimplementedError('fileSaver() has not been implemented.');
  }

  Future<List<String>?> fileDelete({FileDeleteParams? params}) {
    throw UnimplementedError('fileDelete() has not been implemented.');
  }

  Future<FileMetadata> fileMetaData({FileMetadataParams? params}) {
    throw UnimplementedError('fileMetaData() has not been implemented.');
  }

  Future<String?> cacheFilePathFromPath({CacheFilePathFromPathParams? params}) {
    throw UnimplementedError(
        'cacheFilePathFromUri() has not been implemented.');
  }

  Future<bool?> uriPermissionStatus({UriPermissionStatusParams? params}) {
    throw UnimplementedError('uriPermissionStatus() has not been implemented.');
  }

  Future<List<String>?> urisWithPersistedPermission() {
    throw UnimplementedError(
        'urisWithPersistedPermission() has not been implemented.');
  }

  Future<String?> cancelActions({CancelActionsParams? params}) {
    throw UnimplementedError('cancelActions() has not been implemented.');
  }
}
