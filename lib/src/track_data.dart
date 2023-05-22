import 'package:mdu1_player/mdu1_player.dart';

class TrackData {
  final TrackType type;
  final String name;
  final int id;
  final bool selected;
  final int? trackGroupIndex;

  TrackData({
    required this.type,
    required this.name,
    required this.id,
    required this.selected,
    this.trackGroupIndex,
  });
}
