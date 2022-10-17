import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:mdu1_player/src/track_type_enum.dart';

class Mdu1Controller {
  final String initialUrl;
  Mdu1Controller(this.initialUrl);

  final _controller = StreamController<Map<String, dynamic>>();
  Stream<Map<String, dynamic>> get stream =>
      _controller.stream.asBroadcastStream();

  MethodChannel? _channel;
  EventChannel? _eventChannel;

  void initialize(int textureId) {
    _channel = MethodChannel('mdu1_player_$textureId');

    _channel?.setMethodCallHandler(_handleMethodCalls);

    _controller.sink.add({
      'event': 'initialized',
      'status': 'success',
    });

    _channel?.invokeMethod<void>('init', {
      'url': initialUrl,
    });

    _eventChannel = const EventChannel('mdu1_player/video_events');
    _eventChannel?.receiveBroadcastStream().listen((event) {
      _controller.sink.add(Map<String, dynamic>.from(event));
    });
  }

  void changeChannel(String url) {
    _channel?.invokeMethod<void>('updateChannel', {
      'url': url,
    });
  }

  Future<dynamic> _handleMethodCalls(MethodCall call) async {
    _controller.sink.add({
      'event': call.method,
      'data': call.arguments.toString(),
      'status': 'success'
    });

    switch (call.method) {
      case 'updateTime':
        break;
      case 'updateStream':
        break;
      default:
        debugPrint('Unknown method ${call.method} ');
        throw UnsupportedError('Unsupported method unknown! ${call.method}');
    }

    return Future.value();
  }

  Future<dynamic> getTracks(TrackType type) async {
    switch (type) {
      case TrackType.video:
        return _channel?.invokeMethod<void>('getVideoTracks');
      case TrackType.audio:
        return _channel?.invokeMethod<void>('getAudioTracks');
      case TrackType.captions:
        return _channel?.invokeMethod<void>('getCaptions');
    }
  }

  Future<dynamic> selectTrack(
    TrackType type,
    int trackGroupIndex,
    int trackIndex,
  ) async {
    var method = '';

    switch (type) {
      case TrackType.video:
        method = 'setVideoTrack';
        break;
      case TrackType.audio:
        method = 'setAudioTrack';
        break;
      case TrackType.captions:
        method = 'setCaptionTrack';
        break;
    }

    return _channel?.invokeMethod<void>(method, {
      'trackGroupIndex': trackGroupIndex,
      'trackIndex': trackIndex,
    });
  }

  Future<void> dispose() async {
    try {
      await _channel?.invokeMethod<void>('dispose');
      await _controller.close();
    } on PlatformException catch (e) {
      debugPrint('${e.code}: ${e.message}');
    }
  }
}
