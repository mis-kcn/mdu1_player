import 'package:flutter_vlc_player/flutter_vlc_player.dart';
import 'package:mdu1_player/src/player_controller.dart';
import 'package:mdu1_player/src/track_type_enum.dart';

class MduVlcController implements PlayerController {
  final String initialUrl;
  final double aspectRatio;
  late VlcPlayerController controller;

  MduVlcController(
    this.initialUrl,
    this.aspectRatio,
  ) {
    controller = VlcPlayerController.network(
      initialUrl,
      hwAcc: HwAcc.full,
      options: VlcPlayerOptions(
        subtitle: VlcSubtitleOptions([]),
        advanced: VlcAdvancedOptions([
          VlcAdvancedOptions.clockJitter(0),
          VlcAdvancedOptions.networkCaching(200),
        ]),
        http: VlcHttpOptions([
          VlcHttpOptions.httpReconnect(true),
          VlcHttpOptions.httpContinuous(true),
        ]),
        rtp: VlcRtpOptions([
          VlcRtpOptions.rtpOverRtsp(true),
        ]),
      ),
    );
  }

  @override
  void changeChannel(String url, {bool? enableCaptions = false}) {
    controller.setMediaFromNetwork(
      url,
      autoPlay: true,
      hwAcc: HwAcc.full,
    );
  }

  @override
  void changeResizeMode(String resizeMode) {
    throw UnsupportedError(
      'This feature is only supported on native ExoPlayer.',
    );
  }

  @override
  Future<void> dispose() async {
    controller.dispose();
  }

  @override
  Future getTracks(TrackType type) {
    throw UnimplementedError();
  }

  @override
  Future handleMethodCalls(dynamic call) {
    throw UnsupportedError('This method is not implemented for VLC binding.');
  }

  @override
  void initialize(_) {
    throw UnsupportedError('This method is not implemented for VLC binding.');
  }

  @override
  Future<void> pause() {
    return controller.pause();
  }

  @override
  Future<void> play() {
    return controller.pause();
  }

  @override
  Future selectTrack(TrackType type, int trackGroupIndex, int trackIndex) {
    // TODO: implement selectTrack
    throw UnimplementedError();
  }
}
