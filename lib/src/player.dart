import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:mdu1_player/src/mdu1_controller.dart';

class Mdu1Player extends StatefulWidget {
  final Mdu1Controller controller;
  final bool? useAndroidViewSurface;

  const Mdu1Player({
    Key? key,
    required this.controller,
    this.useAndroidViewSurface = false,
  }) : super(key: key);

  @override
  State<Mdu1Player> createState() => _Mdu1PlayerState();
}

class _Mdu1PlayerState extends State<Mdu1Player> with WidgetsBindingObserver {
  bool isPlatformChannel = false;

  @override
  void initState() {
    WidgetsBinding.instance.addObserver(this);
    super.initState();
    Future.delayed(const Duration(milliseconds: 250), () {
      setState(() {
        isPlatformChannel = true;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return isPlatformChannel == true ? _getAndroidView() : Container();
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      throw UnsupportedError('This platform is unsupported for playback!');
    }

    return Center(
      child: Text(
          '$defaultTargetPlatform is not supported by the Mdu1_view plugin'),
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

  Widget _getAndroidView() {
    return widget.useAndroidViewSurface == true
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
                creationParams: <String, dynamic>{},
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
            creationParams: const <String, dynamic>{},
            creationParamsCodec: const StandardMessageCodec(),
          );
  }
}
