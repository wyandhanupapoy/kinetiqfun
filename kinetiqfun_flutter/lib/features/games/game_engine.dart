import 'dart:math';
import 'package:flutter/material.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';

enum GameMode { kesatria, balapGeol, tiruGaya, none }
enum ParticleType { circle, square, star, spark }

class Particle {
  double x, y, vx, vy, size;
  int alpha;
  Color color;
  int life;
  ParticleType type;

  Particle({
    required this.x,
    required this.y,
    required this.vx,
    required this.vy,
    required this.size,
    required this.alpha,
    required this.color,
    required this.life,
    this.type = ParticleType.circle,
  });
}

class FloatingText {
  double x, y;
  String text;
  double alpha;
  int life;

  FloatingText({
    required this.x,
    required this.y,
    required this.text,
    required this.alpha,
    required this.life,
  });
}

class Rock {
  int id;
  int ownerId;
  int hits;
  Rect rect;
  bool isDestroyed;
  double velocityY;
  double shakeAmount;

  Rock({
    required this.id,
    required this.ownerId,
    this.hits = 0,
    required this.rect,
    this.isDestroyed = false,
    this.velocityY = 0.0,
    this.shakeAmount = 0.0,
  });
}

class GameEngine extends ChangeNotifier {
  GameMode currentGameMode = GameMode.kesatria;
  String? winner;
  int scoreP1 = 0;
  int scoreP2 = 0;
  String nameP1 = "Player 1";
  String nameP2 = "Player 2";

  List<Rock> rocks = [];
  List<Particle> particles = [];
  List<FloatingText> floatingTexts = [];
  
  double balapP1Progress = 0.0;
  double balapP2Progress = 0.0;
  
  double shakeIntensity = 0.0;
  double discoHue = 0.0;
  
  final Random random = Random();

  // Smoothing states
  Map<int, Map<PoseLandmarkType, Offset>> drawnLandmarks = {};
  Map<int, Map<PoseLandmarkType, Offset>> targetLandmarks = {};

  void setGameMode(GameMode mode) {
    currentGameMode = mode;
    notifyListeners();
  }

  void updatePoses(List<Pose> poses, Size imageSize) {
    // Basic assignment. Ideally we'd map poses to P1/P2 based on X position
    targetLandmarks.clear();
    
    for (int i = 0; i < poses.length; i++) {
      if (i >= 2) break; // Only 2 players max
      int playerId = i + 1; // 1 or 2
      
      // Better heuristic: determine player 1 vs player 2 by X position
      // Wait, we need to do this carefully if both are in frame.
      
      targetLandmarks[playerId] = {};
      for (var entry in poses[i].landmarks.entries) {
        targetLandmarks[playerId]![entry.key] = Offset(entry.value.x, entry.value.y);
      }
    }
  }

  void tick() {
    bool needsRepaint = false;
    
    // 60FPS Interpolation for poses
    const double interpSpeed = 0.25;
    for (var playerId in targetLandmarks.keys) {
      needsRepaint = true;
      drawnLandmarks.putIfAbsent(playerId, () => {});
      
      for (var type in targetLandmarks[playerId]!.keys) {
        final targetPos = targetLandmarks[playerId]![type]!;
        final drawnPos = drawnLandmarks[playerId]![type];
        
        if (drawnPos == null) {
          drawnLandmarks[playerId]![type] = targetPos;
        } else {
          drawnLandmarks[playerId]![type] = Offset(
            drawnPos.dx + (targetPos.dx - drawnPos.dx) * interpSpeed,
            drawnPos.dy + (targetPos.dy - drawnPos.dy) * interpSpeed,
          );
        }
      }
    }
    
    // Cleanup lost players
    drawnLandmarks.removeWhere((id, _) => !targetLandmarks.containsKey(id));

    // Update Particles
    if (particles.isNotEmpty) needsRepaint = true;
    for (int i = particles.length - 1; i >= 0; i--) {
      var p = particles[i];
      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.7; // Gravity
      p.alpha = (p.alpha * 0.94).toInt();
      p.life--;
      
      if (p.life <= 0 || p.alpha <= 10) {
        particles.removeAt(i);
      }
    }

    // Update Floating Text
    if (floatingTexts.isNotEmpty) needsRepaint = true;
    for (int i = floatingTexts.length - 1; i >= 0; i--) {
      var ft = floatingTexts[i];
      ft.y -= 3.0; // Float up
      ft.alpha *= 0.9;
      ft.life--;
      
      if (ft.life <= 0 || ft.alpha <= 10.0) {
        floatingTexts.removeAt(i);
      }
    }

    // Screen Shake
    if (shakeIntensity > 0) {
      shakeIntensity *= 0.8;
      if (shakeIntensity < 0.5) shakeIntensity = 0;
      needsRepaint = true;
    }

    if (currentGameMode == GameMode.kesatria) {
      for (var rock in rocks) {
        if (!rock.isDestroyed && rock.shakeAmount > 0) {
          rock.shakeAmount -= 2.5;
          needsRepaint = true;
        }
      }
    }
    
    if (currentGameMode == GameMode.balapGeol) {
      discoHue = (discoHue + 3.0) % 360.0;
      needsRepaint = true;
    }

    if (needsRepaint || winner != null) {
      notifyListeners();
    }
  }

  void onRockHit(Rect rect) {
    _spawnHitSparks(rect);
    floatingTexts.add(FloatingText(x: rect.center.dx, y: rect.top, text: "HIT!", alpha: 255, life: 30));
    notifyListeners();
  }

  void onRockDestroyed(Rect rect) {
    _spawnRockParticles(rect);
    shakeIntensity = 15.0;
    floatingTexts.add(FloatingText(x: rect.center.dx, y: rect.center.dy, text: "BROKEN!", alpha: 255, life: 40));
    notifyListeners();
  }

  void _spawnHitSparks(Rect rect) {
    for (int i = 0; i < 12; i++) {
      particles.add(Particle(
        x: rect.center.dx,
        y: rect.center.dy,
        vx: (random.nextDouble() - 0.5) * 25.0,
        vy: (random.nextDouble() - 0.5) * 25.0 - 10.0,
        size: random.nextDouble() * 12.0 + 6.0,
        alpha: 255,
        color: random.nextBool() ? Colors.yellow : Colors.orange,
        life: 20 + random.nextInt(15),
        type: ParticleType.spark,
      ));
    }
  }

  void _spawnRockParticles(Rect rect) {
    for (int i = 0; i < 30; i++) {
      particles.add(Particle(
        x: rect.center.dx,
        y: rect.center.dy,
        vx: (random.nextDouble() - 0.5) * 40.0,
        vy: (random.nextDouble() - 0.5) * 40.0 - 15.0,
        size: random.nextDouble() * 30.0 + 15.0,
        alpha: 255,
        color: random.nextBool() ? Colors.black54 : Colors.grey,
        life: 50 + random.nextInt(30),
        type: random.nextBool() ? ParticleType.square : ParticleType.circle,
      ));
    }
  }

  void setWinner(String winName, double width, double height) {
    winner = winName;
    _spawnVictoryParticles(width, height);
    notifyListeners();
  }

  void _spawnVictoryParticles(double width, double height) {
    for (int i = 0; i < 120; i++) {
      // Left Cannon
      particles.add(Particle(
        x: width * 0.1,
        y: height,
        vx: (random.nextDouble() - 0.1) * 50.0,
        vy: -(25.0 + random.nextDouble() * 50.0),
        size: 15.0 + random.nextDouble() * 25.0,
        alpha: 255,
        color: Color.fromARGB(255, random.nextInt(256), random.nextInt(256), random.nextInt(256)),
        life: 100 + random.nextInt(50),
        type: random.nextBool() ? ParticleType.star : ParticleType.circle,
      ));

      // Right Cannon
      particles.add(Particle(
        x: width * 0.9,
        y: height,
        vx: (random.nextDouble() - 0.9) * 50.0,
        vy: -(25.0 + random.nextDouble() * 50.0),
        size: 15.0 + random.nextDouble() * 25.0,
        alpha: 255,
        color: Color.fromARGB(255, random.nextInt(256), random.nextInt(256), random.nextInt(256)),
        life: 100 + random.nextInt(50),
        type: random.nextBool() ? ParticleType.star : ParticleType.circle,
      ));
    }
  }
}
