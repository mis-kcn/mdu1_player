import 'dart:async';
import 'dart:developer';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_vlc_player/flutter_vlc_player.dart';
import 'package:mdu1_player/mdu1_player.dart';
import 'package:rxdart/subjects.dart';

class Mdu1Player extends StatefulWidget {
  final PlayerController controller;
  final bool? useAndroidViewSurface;
  final bool enableCaptions;
  final bool enableErrorDetection;

  const Mdu1Player({
    Key? key,
    required this.controller,
    required this.enableCaptions,
    this.useAndroidViewSurface = false,
    this.enableErrorDetection = false,
  }) : super(key: key);

  @override
  State<Mdu1Player> createState() => _Mdu1PlayerState();
}

class _Mdu1PlayerState extends State<Mdu1Player> {
  Widget? _vlcPlayerWidget;
  Widget? _exoPlayerWidget;

  BehaviorSubject<bool> isLoadingController = BehaviorSubject<bool>();
  BehaviorSubject<bool> currentlyRetryingController = BehaviorSubject<bool>();
  StreamSubscription<Map<String, dynamic>>? eventSubscription;

  @override
  void initState() {
    super.initState();

    if (widget.controller is Mdu1Controller) {
      log('Subscribed to player events');
      eventSubscription = (widget.controller as Mdu1Controller)
          .stream
          .listen(_handleControllerStream);
    }
  }

  void _handleControllerStream(Map<String, dynamic> event) {
    log('Received player event: $event | ${widget.controller.streamUrl}');

    switch (event['event']) {
      case 'bufferingUpdate':
        break;
      case 'bufferingEnd':
        isLoadingController.add(false);
        currentlyRetryingController.add(false);

        break;
      case 'channelUpdated':
        isLoadingController.add(true);
        currentlyRetryingController.add(false);

        final streamUrl = event['url'].toString();
        final enableCaptions = event['captions'].toString() == '1';

        Future.delayed(const Duration(seconds: 30), () {
          if (isLoadingController.value == true) {
            widget.controller.changeChannel(
              streamUrl,
              enableCaptions: enableCaptions,
            );
          }
        });
        break;
      case 'exception':
      case 'completed':
        currentlyRetryingController.add(true);

        Future.delayed(const Duration(seconds: 7), () {
          final streamUrl = widget.controller.streamUrl;
          final enableCaptions = widget.controller.enableCaptions ?? false;
          if (currentlyRetryingController.value == true && streamUrl != null) {
            widget.controller.changeChannel(
              streamUrl,
              enableCaptions: enableCaptions,
            );
          }
        });
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.controller is MduVlcController) {
      return _getVlcView();
    } else if (widget.controller is Mdu1Controller) {
      if (_exoPlayerWidget != null) {
        return _exoPlayerWidget!;
      }

      final playerWidget = widget.useAndroidViewSurface == true
          ? PlatformViewLink(
              viewType: 'mdu1_player',
              surfaceFactory: (
                BuildContext context,
                PlatformViewController controller,
              ) {
                return AndroidViewSurface(
                  controller: controller as AndroidViewController,
                  gestureRecognizers: const <Factory<
                      OneSequenceGestureRecognizer>>{},
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
                  ..addOnPlatformViewCreatedListener(
                      params.onPlatformViewCreated)
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
              gestureRecognizers: const <Factory<
                  OneSequenceGestureRecognizer>>{},
              creationParamsCodec: const StandardMessageCodec(),
            );

      if (widget.enableErrorDetection) {
        _exoPlayerWidget = Stack(
          children: [
            Positioned.fill(child: playerWidget),
            StreamBuilder<bool>(
              stream: isLoadingController.stream,
              initialData: false,
              builder: (context, snapshot) {
                log('IsLoadingController: ${snapshot.data}');
                if (snapshot.data == false) {
                  return const SizedBox();
                }

                return Positioned(
                  left: 30,
                  top: 30,
                  child: Container(
                    color: Colors.black,
                    padding: const EdgeInsets.all(6),
                    child: const Text(
                      'Loading stream',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                );
              },
            ),
            StreamBuilder<bool>(
              stream: currentlyRetryingController.stream,
              initialData: false,
              builder: (context, snapshot) {
                log('CurrentlyRetryingController: ${snapshot.data}');
                if (snapshot.data == false) {
                  return const SizedBox();
                }

                return Positioned(
                    left: 30,
                    top: 30,
                    child: Container(
                      color: Colors.black,
                      padding: const EdgeInsets.all(6),
                      child: const Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons
                                .signal_wifi_statusbar_connected_no_internet_4_rounded,
                            size: 36,
                            color: Colors.white,
                          ),
                          Text(
                            'Network problem',
                            style: TextStyle(color: Colors.white),
                          ),
                        ],
                      ),
                    ));
              },
            ),
          ],
        );
      } else {
        _exoPlayerWidget = playerWidget;
      }

      return _exoPlayerWidget!;
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

  @override
  void dispose() {
    isLoadingController.close();
    currentlyRetryingController.close();

    widget.controller.dispose();

    super.dispose();
  }
}
