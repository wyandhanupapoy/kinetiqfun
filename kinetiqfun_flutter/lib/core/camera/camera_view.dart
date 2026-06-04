import 'dart:io';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mlkit_commons/google_mlkit_commons.dart';

class CameraView extends StatefulWidget {
  final CustomPaint? customPaint;
  final Function(InputImage inputImage) onImage;
  final CameraLensDirection initialDirection;

  const CameraView({
    Key? key,
    required this.customPaint,
    required this.onImage,
    this.initialDirection = CameraLensDirection.front,
  }) : super(key: key);

  @override
  State<CameraView> createState() => _CameraViewState();
}

class _CameraViewState extends State<CameraView> {
  CameraController? _controller;
  int _cameraIndex = -1;
  double _zoomLevel = 1.0;
  List<CameraDescription> _cameras = [];

  @override
  void initState() {
    super.initState();
    _initializeCamera();
  }

  void _initializeCamera() async {
    _cameras = await availableCameras();
    if (_cameras.isEmpty) return;

    _cameraIndex = _cameras.indexWhere(
        (camera) => camera.lensDirection == widget.initialDirection);
    
    if (_cameraIndex == -1) {
      _cameraIndex = 0; // fallback to any camera
    }

    _startLiveFeed();
  }

  @override
  void dispose() {
    _stopLiveFeed();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_cameras.isEmpty) return const SizedBox.shrink();
    if (_controller == null || !_controller!.value.isInitialized) {
      return const SizedBox();
    }

    // Kamera ditujukan untuk Fullscreen Landscape
    final size = MediaQuery.of(context).size;
    final deviceRatio = size.width / size.height;
    // _controller!.value.aspectRatio may be portrait (e.g. 0.56) even if device is landscape.
    // Ensure we are comparing correctly.
    double cameraRatio = _controller!.value.aspectRatio;
    if (cameraRatio < 1) cameraRatio = 1 / cameraRatio; // Force landscape ratio for comparison

    double scale = 1.0;
    if (deviceRatio > cameraRatio) {
      scale = deviceRatio / cameraRatio;
    } else {
      scale = cameraRatio / deviceRatio;
    }

    return Stack(
      fit: StackFit.expand,
      children: <Widget>[
        Transform.scale(
          scale: scale,
          child: Center(
            child: AspectRatio(
              aspectRatio: cameraRatio,
              child: CameraPreview(_controller!),
            ),
          ),
        ),
        if (widget.customPaint != null) widget.customPaint!,
      ],
    );
  }

  Future _startLiveFeed() async {
    final camera = _cameras[_cameraIndex];
    _controller = CameraController(
      camera,
      // Menggunakan resolusi medium/high untuk keseimbangan performa pose detection
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: Platform.isAndroid
          ? ImageFormatGroup.nv21
          : ImageFormatGroup.bgra8888,
    );

    _controller?.initialize().then((_) {
      if (!mounted) return;
      _controller?.getMinZoomLevel().then((value) {
        _zoomLevel = value;
      });
      _controller?.startImageStream(_processCameraImage);
      setState(() {});
    });
  }

  Future _stopLiveFeed() async {
    await _controller?.stopImageStream();
    await _controller?.dispose();
    _controller = null;
  }

  void _processCameraImage(CameraImage image) {
    final inputImage = _inputImageFromCameraImage(image);
    if (inputImage == null) return;
    widget.onImage(inputImage);
  }

  final _orientations = {
    DeviceOrientation.portraitUp: 0,
    DeviceOrientation.landscapeLeft: 90,
    DeviceOrientation.portraitDown: 180,
    DeviceOrientation.landscapeRight: 270,
  };

  InputImage? _inputImageFromCameraImage(CameraImage image) {
    if (_controller == null) return null;
    final camera = _cameras[_cameraIndex];
    final sensorOrientation = camera.sensorOrientation;
    
    InputImageRotation? rotation;
    if (Platform.isIOS) {
      rotation = InputImageRotationValue.fromRawValue(sensorOrientation);
    } else if (Platform.isAndroid) {
      var rotationCompensation = _orientations[_controller!.value.deviceOrientation];
      if (rotationCompensation == null) return null;
      if (camera.lensDirection == CameraLensDirection.front) {
        rotationCompensation = (sensorOrientation + rotationCompensation) % 360;
      } else {
        rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360;
      }
      rotation = InputImageRotationValue.fromRawValue(rotationCompensation);
    }
    if (rotation == null) return null;

    final format = InputImageFormatValue.fromRawValue(image.format.raw);
    if (format == null ||
        (Platform.isAndroid && format != InputImageFormat.nv21) ||
        (Platform.isIOS && format != InputImageFormat.bgra8888)) return null;

    if (image.planes.isEmpty) return null;

    return InputImage.fromBytes(
      bytes: image.planes[0].bytes,
      metadata: InputImageMetadata(
        size: Size(image.width.toDouble(), image.height.toDouble()),
        rotation: rotation,
        format: format,
        bytesPerRow: image.planes[0].bytesPerRow,
      ),
    );
  }
}
