import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';
import '../base_pose_screen.dart';
import '../game_engine.dart';
import '../../../core/audio/sound_manager.dart';

class GeolKicauManiaPage extends BasePoseScreen {
  const GeolKicauManiaPage({super.key});

  @override
  State<GeolKicauManiaPage> createState() => _GeolKicauManiaPageState();
}

class _GeolKicauManiaPageState extends BasePoseScreenState<GeolKicauManiaPage> {
  bool isGameStarted = false;
  int _countdown = 5;
  Timer? _countdownTimer;

  final Map<int, double> _lastHipX = {};

  @override
  void initState() {
    super.initState();
    engine.setGameMode(GameMode.balapGeol);
    SoundManager().playBgm('audio/kicau_mania_background_music.mp3');
    _startCountdown();
  }

  void _startCountdown() {
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      setState(() {
        _countdown--;
        if (_countdown <= 0) {
          isGameStarted = true;
          timer.cancel();
        }
      });
      SoundManager().playClick();
    });
  }

  @override
  void onPoseDetected(List<Pose> poses, Size imageSize) {
    if (!isGameStarted || engine.winner != null) return;

    final size = MediaQuery.of(context).size;

    for (var playerId in engine.targetLandmarks.keys) {
      final marks = engine.targetLandmarks[playerId]!;
      final lh = marks[PoseLandmarkType.leftHip];
      final rh = marks[PoseLandmarkType.rightHip];

      if (lh != null && rh != null) {
        final midXImage = (lh.dx + rh.dx) / 2.0;
        final midXScreen = size.width - (midXImage * size.width / imageSize.width); // Mirrored

        final lastX = _lastHipX[playerId] ?? midXScreen;
        final delta = (midXScreen - lastX).abs();

        if (delta > 10.0) { // Threshold for hip shake
          if (playerId == 1) {
            engine.balapP1Progress += 0.02;
            if (engine.balapP1Progress >= 1.0) {
              engine.setWinner(engine.nameP1, size.width, size.height);
              SoundManager().playVictory();
            }
          } else {
            engine.balapP2Progress += 0.02;
            if (engine.balapP2Progress >= 1.0) {
              engine.setWinner(engine.nameP2, size.width, size.height);
              SoundManager().playVictory();
            }
          }
        }
        
        _lastHipX[playerId] = midXScreen;
      }
    }
  }

  @override
  Widget buildGameUI(BuildContext context) {
    if (!isGameStarted) {
      return Center(
        child: Text(
          _countdown > 0 ? '$_countdown' : 'GO!',
          style: const TextStyle(fontFamily: 'game_font', fontSize: 100, color: Colors.yellow, fontWeight: FontWeight.bold, shadows: [Shadow(color: Colors.red, blurRadius: 10)]),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}
