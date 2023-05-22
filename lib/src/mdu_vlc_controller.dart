import 'package:flutter_vlc_player/flutter_vlc_player.dart';
import 'package:mdu1_player/src/player_controller.dart';
import 'package:mdu1_player/src/track_type_enum.dart';

import 'track_data.dart';

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
  Future<List<TrackData>> getTracks(TrackType type) async {
    switch (type) {
      case TrackType.video:
        final selected = await controller.getVideoTrack();
        final tracks = await controller.getVideoTracks();

        if (tracks.isEmpty) {
          return [
            TrackData(
              type: TrackType.video,
              name: 'Auto',
              id: -1,
              selected: true,
            )
          ];
        }

        var sortedByValueMap = Map.fromEntries(tracks.entries.toList()
          ..sort((e1, e2) => e1.value.compareTo(e2.value)));

        var parsedTracks = sortedByValueMap.entries
            .map(
              (e) => TrackData(
                type: TrackType.video,
                name: e.value,
                id: e.key,
                selected: selected == e.key,
              ),
            )
            .toList();

        if (parsedTracks.length == 1) {
          parsedTracks = [
            TrackData(
              type: TrackType.video,
              name: 'Auto',
              id: parsedTracks.first.id,
              selected: true,
            )
          ];
        }

        return parsedTracks;

      case TrackType.audio:
        final selected = await controller.getAudioTrack();
        final tracks = await controller.getAudioTracks();

        if (tracks.isEmpty) {
          return [
            TrackData(
              type: TrackType.audio,
              name: 'Auto',
              id: -1,
              selected: true,
            )
          ];
        }

        var sortedByValueMap = Map.fromEntries(tracks.entries.toList()
          ..sort((e1, e2) => e1.value.compareTo(e2.value)));

        var parsedTracks = sortedByValueMap.entries
            .map(
              (e) => TrackData(
                type: TrackType.video,
                name: _formatAudioName(e.value),
                id: e.key,
                selected: selected == e.key,
              ),
            )
            .toList();

        if (parsedTracks.length == 1) {
          parsedTracks = [
            TrackData(
              type: TrackType.audio,
              name: 'Auto',
              id: parsedTracks.first.id,
              selected: true,
            )
          ];
        }

        return parsedTracks;

      case TrackType.captions:
        final selected = await controller.getSpuTrack();
        final fetchedTracks = (await controller.getSpuTracks());
        var sortedByValueMap = Map.fromEntries(fetchedTracks.entries.toList()
          ..sort((e1, e2) => e1.value.compareTo(e2.value)));

        final tracks = <int, String>{
          ...{
            -1: 'Off',
          },
          ...sortedByValueMap,
        };

        final parsedTracks = tracks.entries.map(
          (e) {
            final name = e.value.toLowerCase().contains('closed captions 1')
                ? 'English'
                : e.value;

            return TrackData(
              type: TrackType.captions,
              name: name,
              id: e.key,
              selected: selected == e.key,
            );
          },
        ).toList();

        return parsedTracks;
    }
  }

  String _formatAudioName(
    String data,
  ) {
    final language = data.split('[').last.split(']').first;
    final stereo = data.contains(
      'aac',
    )
        ? 'Stereo'
        : '';
    return [language, stereo].join(' ');
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
  Future<void> selectTrack(
      TrackType type, int trackGroupIndex, int trackIndex) {
    switch (type) {
      case TrackType.video:
        return controller.setVideoTrack(trackIndex);
      case TrackType.audio:
        return controller.setAudioTrack(trackIndex);
      case TrackType.captions:
        return controller.setSpuTrack(trackIndex);
    }
  }
}
