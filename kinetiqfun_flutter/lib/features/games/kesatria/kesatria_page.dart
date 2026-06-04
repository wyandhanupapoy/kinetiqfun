import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';
import '../base_pose_screen.dart';
import '../game_engine.dart';
import '../../../core/audio/sound_manager.dart';
import '../../../core/utils/tooltip_helper.dart';

class KesatriaPage extends BasePoseScreen {
  const KesatriaPage({super.key});

  @override
  State<KesatriaPage> createState() => _KesatriaPageState();
}

class _KesatriaPageState extends BasePoseScreenState<KesatriaPage> {
  bool isGameStarted = false;
  int _countdown = 5;
  Timer? _countdownTimer;

  final Map<int, double> _lastHandYL = {};
  final Map<int, double> _lastHandYR = {};

  int _p1DestroyedCount = 0;
  int _p2DestroyedCount = 0;
  final int _totalRocksGoal = 3;

  @override
  void initState() {
    super.initState();
    engine.setGameMode(GameMode.kesatria);
    SoundManager().playBgm('audio/kesatria_pcd_background_music.mp3');

    // Wait a bit for layout to get sizes, then init
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initRocks();
      _startCountdown();
      _showTooltips();
    });
  }

  void _initRocks() {
    final size = MediaQuery.of(context).size;
    final width = size.width;
    final height = size.height;

    engine.rocks.clear();
    int nextId = 0;

    for (int i = 0; i < 3; i++) {
      _spawnRock(1, width, height, i, nextId++);
      _spawnRock(2, width, height, i, nextId++);
    }
  }

  void _spawnRock(int playerId, double screenWidth, double screenHeight, int index, int id) {
    final rockWidth = screenWidth / 10.0;
    final rockHeight = rockWidth;
    
    final areaWidth = screenWidth / 2.0;
    final startX = playerId == 1 ? 0.0 : areaWidth;
    
    final slotMultipliers = [0.2, 0.5, 0.8];
    final slot = slotMultipliers[index % 3];
    
    double x = startX + (areaWidth * slot) - (rockWidth / 2.0);
    double startY = screenHeight - rockHeight - 50.0;
    
    engine.rocks.add(Rock(
      id: id,
      ownerId: playerId,
      rect: Rect.fromLTWH(x, startY, rockWidth, rockHeight),
    ));
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
      SoundManager().playClick(); // Beep
    });
  }

  @override
  void onPoseDetected(List<Pose> poses, Size imageSize) {
    if (!isGameStarted || engine.winner != null) return;

    final size = MediaQuery.of(context).size;

    for (var playerId in engine.targetLandmarks.keys) {
      final marks = engine.targetLandmarks[playerId]!;
      final wl = marks[PoseLandmarkType.leftWrist];
      final wr = marks[PoseLandmarkType.rightWrist];

      final ty = (double y) => y * size.height / imageSize.height;

      _checkHit(playerId, wl, imageSize, true);
      _checkHit(playerId, wr, imageSize, false);
    }
  }

  void _checkHit(int playerId, Offset? wrist, Size imageSize, bool isLeft) {
    if (wrist == null) return;
    
    final size = MediaQuery.of(context).size;
    final screenX = size.width - (wrist.dx * size.width / imageSize.width);
    final screenY = wrist.dy * size.height / imageSize.height;
    
    final lastYMap = isLeft ? _lastHandYL : _lastHandYR;
    final lastY = lastYMap[playerId] ?? screenY;

    for (var rock in engine.rocks) {
      if (!rock.isDestroyed && rock.ownerId == playerId) {
        final hitZone = rock.rect.inflate(40.0);
        
        final velocityY = screenY - lastY;
        final isSwingingDown = velocityY > 20.0;
        final startedFromAbove = lastY < rock.rect.top + (rock.rect.height / 2.0);
        
        if (hitZone.contains(Offset(screenX, screenY)) && isSwingingDown && startedFromAbove) {
          rock.hits++;
          engine.onRockHit(rock.rect);
          SoundManager().playAction(pitch: 1.0 + (rock.hits * 0.1));
          rock.shakeAmount = 25.0;
          
          if (rock.hits >= 5) {
            rock.isDestroyed = true;
            engine.onRockDestroyed(rock.rect);
            SoundManager().playAction(pitch: 0.8);
            
            if (playerId == 1) _p1DestroyedCount++; else _p2DestroyedCount++;
            engine.scoreP1 = _p1DestroyedCount;
            engine.scoreP2 = _p2DestroyedCount;
            
            if (_p1DestroyedCount >= _totalRocksGoal) {
              engine.setWinner(engine.nameP1, size.width, size.height);
              SoundManager().playVictory();
            } else if (_p2DestroyedCount >= _totalRocksGoal) {
              engine.setWinner(engine.nameP2, size.width, size.height);
              SoundManager().playVictory();
            }
          }
          break; // One rock per frame
        }
      }
    }
    
    lastYMap[playerId] = screenY;
  }

  void _showTooltips() {
    if (!mounted) return;
    final size = MediaQuery.of(context).size;
    
    // P1 Area
    TooltipHelper.showAtPoint(context, "Area Pemain 1", size.width * 0.25, size.height * 0.5);
    // P2 Area
    TooltipHelper.showAtPoint(context, "Area Pemain 2", size.width * 0.75, size.height * 0.5);
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
