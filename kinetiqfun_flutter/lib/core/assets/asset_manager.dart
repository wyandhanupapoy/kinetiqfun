import 'dart:ui' as ui;
import 'package:flutter/services.dart';

class AssetManager {
  static final AssetManager _instance = AssetManager._internal();
  factory AssetManager() => _instance;
  AssetManager._internal();

  final Map<String, ui.Image> _images = {};

  Future<void> init() async {
    final futures = <Future<void>>[
      _loadImage('head1', 'assets/images/head1.png'),
      _loadImage('head2', 'assets/images/head2.png'),
      _loadImage('body1', 'assets/images/body1.png'),
      _loadImage('body2', 'assets/images/body2.png'),
      _loadImage('hand_left', 'assets/images/hand_left.png'),
      _loadImage('hand_right', 'assets/images/hand_right.png'),
      _loadImage('shoulder1_left', 'assets/images/shoulder1_left.png'),
      _loadImage('shoulder1_right', 'assets/images/shoulder1_right.png'),
      _loadImage('shoulder2_left', 'assets/images/shoulder2_left.png'),
      _loadImage('shoulder2_right', 'assets/images/shoulder2_right.png'),
      _loadImage('box', 'assets/images/box.png'),
    ];
    await Future.wait(futures);
  }

  Future<void> _loadImage(String key, String assetPath) async {
    try {
      final ByteData data = await rootBundle.load(assetPath);
      final Uint8List bytes = data.buffer.asUint8List();
      final ui.Codec codec = await ui.instantiateImageCodec(bytes);
      final ui.FrameInfo frameInfo = await codec.getNextFrame();
      _images[key] = frameInfo.image;
    } catch (e) {
      print('Failed to load image $assetPath: $e');
    }
  }

  ui.Image? getImage(String key) {
    return _images[key];
  }
}
