import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_vlc_player/flutter_vlc_player.dart';
import 'package:mdu1_player/mdu1_player.dart';

class Mdu1Player extends StatefulWidget {
  final PlayerController controller;
  final bool? useAndroidViewSurface;
  final bool enableCaptions;

  const Mdu1Player({
    Key? key,
    required this.controller,
    required this.enableCaptions,
    this.useAndroidViewSurface = false,
  }) : super(key: key);

  @override
  State<Mdu1Player> createState() => _Mdu1PlayerState();
}

class _Mdu1PlayerState extends State<Mdu1Player> with WidgetsBindingObserver {
  Widget? _vlcPlayerWidget;
  Widget? _exoPlayerWidget;

  @override
  void initState() {
    WidgetsBinding.instance.addObserver(this);
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.controller is MduVlcController) {
      return _getVlcView();
    } else if (widget.controller is Mdu1Controller) {
      return _getAndroidView();
    }

    return Center(
      child: Text(
        '$defaultTargetPlatform is not supported by the Mdu1_view plugin',
      ),
    );
  }

  void _onPlatformViewCreated(int id) {
    widget.controller.initialize(id);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Widget _getVlcView() {
    if (_vlcPlayerWidget != null) {
      return _vlcPlayerWidget!;
    }

    _vlcPlayerWidget = VlcPlayer(
      controller: (widget.controller as MduVlcController).controller,
      aspectRatio: (widget.controller as MduVlcController).aspectRatio,
    );

    return _vlcPlayerWidget!;
  }

  Widget _getAndroidView() {
    if (_exoPlayerWidget != null) {
      return _exoPlayerWidget!;
    }

    _exoPlayerWidget = widget.useAndroidViewSurface == true
        ? PlatformViewLink(
            viewType: 'mdu1_player',
            surfaceFactory: (
              BuildContext context,
              PlatformViewController controller,
            ) {
              return AndroidViewSurface(
                controller: controller as AndroidViewController,
                gestureRecognizers: const <
                    Factory<OneSequenceGestureRecognizer>>{},
                hitTestBehavior: PlatformViewHitTestBehavior.transparent,
              );
            },
            onCreatePlatformView: (PlatformViewCreationParams params) {
              final ExpensiveAndroidViewController controller =
                  PlatformViewsService.initExpensiveAndroidView(
                id: params.id,
                viewType: 'mdu1_player',
                layoutDirection: TextDirection.ltr,
                creationParams: <String, dynamic>{
                  'captions': widget.enableCaptions,
                },
                creationParamsCodec: const StandardMessageCodec(),
                onFocus: () => params.onFocusChanged(true),
              );
              controller
                ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
                ..addOnPlatformViewCreatedListener(_onPlatformViewCreated)
                ..create();

              return controller;
            },
          )
        : AndroidView(
            viewType: 'mdu1_player',
            onPlatformViewCreated: _onPlatformViewCreated,
            creationParams: <String, dynamic>{
              'captions': widget.enableCaptions,
            },
            hitTestBehavior: PlatformViewHitTestBehavior.transparent,
            gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
            creationParamsCodec: const StandardMessageCodec(),
          );

    return _exoPlayerWidget!;
  }
}
