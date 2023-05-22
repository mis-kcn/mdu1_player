import 'package:flutter/services.dart';
import 'package:mdu1_player/mdu1_player.dart';

abstract class PlayerController {
  void initialize(int? textureId);

  void changeResizeMode(String resizeMode);

  void changeChannel(
    String url, {
    bool? enableCaptions = false,
  });

  Future<void> pause();

  Future<void> play();

  Future<dynamic> handleMethodCalls(MethodCall? call);

  Future<List<TrackData>> getTracks(TrackType type);

  Future<dynamic> selectTrack(
    TrackType type,
    int trackGroupIndex,
    int trackIndex,
  );

  Future<void> dispose();
}
