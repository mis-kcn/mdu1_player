import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:mdu1_player/mdu1_player.dart';

class Mdu1Controller implements PlayerController {
  final String initialUrl;
  Mdu1Controller(this.initialUrl);

  final _controller = StreamController<Map<String, dynamic>>();
  Stream<Map<String, dynamic>> get stream =>
      _controller.stream.asBroadcastStream();

  MethodChannel? _channel;
  EventChannel? _eventChannel;

  @override
  void initialize(int? textureId) {
    _channel = MethodChannel('mdu1_player_$textureId');

    _channel?.setMethodCallHandler(handleMethodCalls);

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

  @override
  void changeResizeMode(String resizeMode) {
    _channel?.invokeMethod('updateResizeMode', {
      'resizeMode': resizeMode,
    });
  }

  @override
  void changeChannel(String url, {bool? enableCaptions = false}) {
    _channel?.invokeMethod<void>('updateChannel', {
      'url': url,
      'enableCaptions': enableCaptions,
    });
  }

  @override
  Future<void> pause() async {
    return _channel?.invokeMethod<void>('pause');
  }

  @override
  Future<void> play() async {
    return _channel?.invokeMethod<void>('play');
  }

  @override
  Future<dynamic> handleMethodCalls(MethodCall? call) async {
    assert(call != null);
    _controller.sink.add({
      'event': call!.method,
      'data': call.arguments.toString(),
      'status': 'success'
    });

    return Future.value();
  }

  @override
  Future<List<TrackData>> getTracks(TrackType type) async {
    switch (type) {
      case TrackType.video:
        final tracks =
            await _channel?.invokeMethod<List<Object?>>('getVideoTracks');

        return tracks!.map(
          (e) {
            e = e as Map<dynamic, dynamic>;
            return TrackData(
              type: TrackType.video,
              name: e['name'],
              id: e['trackIndex'],
              selected: e['isSelected'] == true,
              trackGroupIndex: e['trackGroupIndex'],
            );
          },
        ).toList();

      case TrackType.audio:
        final tracks =
            await _channel?.invokeMethod<List<Object?>>('getAudioTracks');

        return tracks!.map(
          (e) {
            e = e as Map<dynamic, dynamic>;
            return TrackData(
              type: TrackType.audio,
              name: e['name'],
              id: e['trackIndex'],
              selected: e['isSelected'] == true,
              trackGroupIndex: e['trackGroupIndex'],
            );
          },
        ).toList();
      case TrackType.captions:
        final tracks =
            await _channel?.invokeMethod<List<Object?>>('getCaptions');

        return tracks!.map(
          (e) {
            e = e as Map<dynamic, dynamic>;
            return TrackData(
              type: TrackType.captions,
              name: e['name'],
              id: e['trackIndex'],
              selected: e['isSelected'] == true,
              trackGroupIndex: e['trackGroupIndex'],
            );
          },
        ).toList();
    }
  }

  @override
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

  @override
  Future<void> dispose() async {
    try {
      await _channel?.invokeMethod<void>('dispose');
      await _controller.close();
    } on PlatformException catch (e) {
      debugPrint('${e.code}: ${e.message}');
    }
  }
}
