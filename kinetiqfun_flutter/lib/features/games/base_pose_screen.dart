import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';
import '../../core/camera/camera_view.dart';
import 'overlay_painter.dart';
import 'game_engine.dart';

abstract class BasePoseScreen extends StatefulWidget {
  const BasePoseScreen({super.key});
}

abstract class BasePoseScreenState<T extends BasePoseScreen> extends State<T> with TickerProviderStateMixin {
  final PoseDetector _poseDetector = PoseDetector(
    options: PoseDetectorOptions(
      mode: PoseDetectionMode.stream,
      model: PoseDetectionModel.base, // Natively supports multi-pose
    ),
  );
  
  bool _canProcess = true;
  bool _isBusy = false;
  CustomPaint? _customPaint;
  
  late final GameEngine engine;
  late final AnimationController _tickerController;

  @override
  void initState() {
    super.initState();
    engine = GameEngine();
    _tickerController = AnimationController(vsync: this, duration: const Duration(days: 365))
      ..addListener(() {
        engine.tick();
      })
      ..forward();
  }

  @override
  void dispose() async {
    _canProcess = false;
    _tickerController.dispose();
    engine.dispose();
    _poseDetector.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          CameraView(
            customPaint: _customPaint,
            onImage: _processImage,
            initialDirection: CameraLensDirection.front,
          ),
          buildGameUI(context),
        ],
      ),
    );
  }

  Widget buildGameUI(BuildContext context) {
    return const SizedBox.shrink();
  }

  void onPoseDetected(List<Pose> poses, Size imageSize) {}

  Future<void> _processImage(InputImage inputImage) async {
    if (!_canProcess) return;
    if (_isBusy) return;
    _isBusy = true;
    
    final poses = await _poseDetector.processImage(inputImage);
    
    if (inputImage.metadata?.size != null && inputImage.metadata?.rotation != null) {
      engine.updatePoses(poses, inputImage.metadata!.size);

      final painter = OverlayPainter(
        engine,
        inputImage.metadata!.size,
        inputImage.metadata!.rotation,
        CameraLensDirection.front,
      );
      
      _customPaint = CustomPaint(painter: painter);
      
      onPoseDetected(poses, inputImage.metadata!.size);
    } else {
      _customPaint = null;
    }
    
    _isBusy = false;
    if (mounted) {
      setState(() {});
    }
  }
}
