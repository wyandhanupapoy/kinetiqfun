import 'dart:math';
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';
import 'dart:ui' as ui;
import '../../core/assets/asset_manager.dart';
import 'game_engine.dart';
import 'dart:io';

class OverlayPainter extends CustomPainter {
  final GameEngine engine;
  final Size imageSize;
  final InputImageRotation rotation;
  final CameraLensDirection cameraLensDirection;

  OverlayPainter(
    this.engine,
    this.imageSize,
    this.rotation,
    this.cameraLensDirection,
  ) : super(repaint: engine);

  @override
  void paint(Canvas canvas, Size size) {
    // Apply Screen Shake
    if (engine.shakeIntensity > 0) {
      final shakeX = (engine.random.nextDouble() - 0.5) * engine.shakeIntensity;
      final shakeY = (engine.random.nextDouble() - 0.5) * engine.shakeIntensity;
      canvas.translate(shakeX, shakeY);
    }

    // Center Line
    final dashedPaint = Paint()
      ..color = Colors.white54
      ..strokeWidth = 4.0
      ..style = PaintingStyle.stroke;
    _drawDashedLine(canvas, Offset(size.width / 2, 0), Offset(size.width / 2, size.height), dashedPaint);

    if (engine.currentGameMode == GameMode.kesatria) {
      _drawKesatria(canvas, size);
    } else if (engine.currentGameMode == GameMode.balapGeol) {
      _drawBalapGeol(canvas, size);
    } else if (engine.currentGameMode == GameMode.tiruGaya) {
      _drawTiruGaya(canvas, size);
    }

    _drawHUD(canvas, size);
    _drawParticles(canvas);
    _drawFloatingTexts(canvas);
  }

  void _drawKesatria(Canvas canvas, Size size) {
    for (var playerId in engine.drawnLandmarks.keys) {
      final landmarks = engine.drawnLandmarks[playerId]!;
      final ls = landmarks[PoseLandmarkType.leftShoulder];
      final rs = landmarks[PoseLandmarkType.rightShoulder];

      if (ls != null && rs != null) {
        final tx = (double x) => _translateX(x, size);
        final ty = (double y) => _translateY(y, size);
        
        final sw = sqrt(pow(tx(ls.dx) - tx(rs.dx), 2) + pow(ty(ls.dy) - ty(rs.dy), 2));
        
        _drawBody(canvas, playerId, landmarks, tx, ty, sw * 1.55);
        _drawShoulders(canvas, playerId, landmarks, tx, ty, sw);
        _drawHands(canvas, playerId, landmarks, tx, ty, sw * 0.48);
        _drawHead(canvas, playerId, landmarks, tx, ty, sw * 0.95);
      }
    }

    // Draw Rocks
    final boxImg = AssetManager().getImage('box');
    final textStyle = const TextStyle(color: Colors.yellow, fontSize: 16, fontWeight: FontWeight.bold);
    
    for (var rock in engine.rocks) {
      if (!rock.isDestroyed) {
        // Draw HP text
        final textSpan = TextSpan(text: 'HP: \${5 - rock.hits}', style: textStyle);
        final textPainter = TextPainter(text: textSpan, textDirection: TextDirection.ltr);
        textPainter.layout();
        textPainter.paint(canvas, Offset(rock.rect.center.dx - textPainter.width / 2, rock.rect.top - 20));

        // Shake rect
        Rect drawRect = rock.rect;
        if (rock.shakeAmount > 0) {
          final sx = (engine.random.nextDouble() - 0.5) * rock.shakeAmount;
          final sy = (engine.random.nextDouble() - 0.5) * rock.shakeAmount;
          drawRect = drawRect.shift(Offset(sx, sy));
        }

        if (boxImg != null) {
          paintImage(canvas: canvas, rect: drawRect, image: boxImg, fit: BoxFit.fill);
        } else {
          canvas.drawRect(drawRect, Paint()..color = Colors.brown);
        }

        // Draw cracks
        if (rock.hits > 0) {
          final crackPaint = Paint()
            ..color = Colors.black.withOpacity(0.7)
            ..strokeWidth = 3.0
            ..style = PaintingStyle.stroke;
          for (int i = 0; i < rock.hits; i++) {
            final angle = i * 1.5;
            canvas.drawLine(
              drawRect.center,
              Offset(
                drawRect.center.dx + cos(angle) * (drawRect.width / 2),
                drawRect.center.dy + sin(angle) * (drawRect.height / 2),
              ),
              crackPaint,
            );
          }
        }
      }
    }
  }

  void _drawBalapGeol(Canvas canvas, Size size) {
    if (engine.discoHue > 0) {
      final color = HSVColor.fromAHSV(0.3, engine.discoHue, 1.0, 1.0).toColor();
      canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), Paint()..color = color);
    }
    // Simple track lines
    final trackPaint = Paint()..color = Colors.white..strokeWidth = 5.0;
    canvas.drawLine(Offset(size.width * 0.25 - 100, 100), Offset(size.width * 0.25 + 100, 100), trackPaint);
    canvas.drawLine(Offset(size.width * 0.75 - 100, 100), Offset(size.width * 0.75 + 100, 100), trackPaint);
    
    // Draw bird icons or similar based on progress (progress 0.0 to 1.0)
    final p1Y = size.height - 150 - (engine.balapP1Progress * (size.height - 250));
    final p2Y = size.height - 150 - (engine.balapP2Progress * (size.height - 250));
    
    canvas.drawCircle(Offset(size.width * 0.25, p1Y), 30, Paint()..color = Colors.red);
    canvas.drawCircle(Offset(size.width * 0.75, p2Y), 30, Paint()..color = Colors.blue);
  }

  void _drawTiruGaya(Canvas canvas, Size size) {
    // No skeleton rendered for Tiru Gaya natively, just camera view.
  }

  void _drawHUD(Canvas canvas, Size size) {
    if (engine.currentGameMode != GameMode.balapGeol && engine.currentGameMode != GameMode.tiruGaya) {
      final style = const TextStyle(
        color: Color(0xFFFFEB3B), 
        fontSize: 65, 
        fontFamily: 'game_font',
        shadows: [Shadow(color: Colors.black, blurRadius: 5, offset: Offset(2, 2))]
      );
      final p1Span = TextSpan(text: 'P1: \${engine.scoreP1}', style: style);
      final p2Span = TextSpan(text: 'P2: \${engine.scoreP2}', style: style);
      
      TextPainter(text: p1Span, textDirection: TextDirection.ltr)
        ..layout()
        ..paint(canvas, const Offset(50, 100));
        
      final p2Painter = TextPainter(text: p2Span, textDirection: TextDirection.ltr)..layout();
      p2Painter.paint(canvas, Offset(size.width - p2Painter.width - 50, 100));
    }

    if (engine.winner != null) {
      canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), Paint()..color = const Color(0x99000000));
      
      final winStyle = const TextStyle(
        color: Colors.yellow, 
        fontSize: 100, 
        fontFamily: 'game_font',
        shadows: [Shadow(color: Colors.black, blurRadius: 10)]
      );
      final nameStyle = const TextStyle(
        color: Colors.white, 
        fontSize: 80, 
        fontFamily: 'game_font',
        shadows: [Shadow(color: Colors.black, blurRadius: 10)]
      );
      
      final bounce = sin(DateTime.now().millisecondsSinceEpoch * 0.01) * 20.0;
      
      final winSpan = TextSpan(text: 'WINNER!', style: winStyle);
      final winPainter = TextPainter(text: winSpan, textDirection: TextDirection.ltr)..layout();
      winPainter.paint(canvas, Offset(size.width / 2 - winPainter.width / 2, size.height / 2 - 50 + bounce - winPainter.height));
      
      final nameSpan = TextSpan(text: engine.winner!, style: nameStyle);
      final namePainter = TextPainter(text: nameSpan, textDirection: TextDirection.ltr)..layout();
      namePainter.paint(canvas, Offset(size.width / 2 - namePainter.width / 2, size.height / 2 + 80 + bounce - namePainter.height / 2));
      
      // Celebration lines
      final time = DateTime.now().millisecondsSinceEpoch * 0.005;
      final linePaint = Paint()
        ..color = Colors.yellow
        ..strokeWidth = 10.0
        ..strokeCap = StrokeCap.round;
        
      for (int i = 0; i < 8; i++) {
        final angle = i * (pi / 4) + time;
        final r1 = 200.0;
        final r2 = 300.0;
        canvas.drawLine(
          Offset(size.width / 2 + cos(angle) * r1, size.height / 2 + sin(angle) * r1),
          Offset(size.width / 2 + cos(angle) * r2, size.height / 2 + sin(angle) * r2),
          linePaint
        );
      }
    }
  }

  void _drawParticles(Canvas canvas) {
    for (var p in engine.particles) {
      final paint = Paint()..color = p.color.withAlpha(p.alpha);
      if (p.type == ParticleType.circle) {
        canvas.drawCircle(Offset(p.x, p.y), p.size, paint);
      } else if (p.type == ParticleType.square) {
        canvas.drawRect(Rect.fromCenter(center: Offset(p.x, p.y), width: p.size * 2, height: p.size * 2), paint);
      } else if (p.type == ParticleType.spark) {
        paint.strokeWidth = p.size / 3;
        canvas.drawLine(Offset(p.x, p.y), Offset(p.x - p.vx * 1.5, p.y - p.vy * 1.5), paint);
      } else if (p.type == ParticleType.star) {
        final path = Path();
        path.moveTo(p.x, p.y - p.size * 1.5);
        path.lineTo(p.x + p.size, p.y);
        path.lineTo(p.x, p.y + p.size * 1.5);
        path.lineTo(p.x - p.size, p.y);
        path.close();
        canvas.drawPath(path, paint);
      }
    }
  }

  void _drawFloatingTexts(Canvas canvas) {
    for (var ft in engine.floatingTexts) {
      final style = TextStyle(color: Colors.yellow.withOpacity(ft.alpha / 255), fontSize: 24, fontWeight: FontWeight.bold, shadows: const [Shadow(color: Colors.black, blurRadius: 4)]);
      final span = TextSpan(text: ft.text, style: style);
      final painter = TextPainter(text: span, textDirection: TextDirection.ltr)..layout();
      painter.paint(canvas, Offset(ft.x - painter.width / 2, ft.y));
    }
  }

  // Helper Translation
  double _translateX(double x, Size canvasSize) {
    switch (rotation) {
      case InputImageRotation.rotation90deg:
        return canvasSize.width - x * canvasSize.width / (Platform.isIOS ? imageSize.width : imageSize.height);
      case InputImageRotation.rotation270deg:
        return x * canvasSize.width / (Platform.isIOS ? imageSize.width : imageSize.height);
      default:
        if (cameraLensDirection == CameraLensDirection.front) {
          return canvasSize.width - x * canvasSize.width / imageSize.width; // Mirroring
        }
        return x * canvasSize.width / imageSize.width;
    }
  }

  double _translateY(double y, Size canvasSize) {
    switch (rotation) {
      case InputImageRotation.rotation90deg:
      case InputImageRotation.rotation270deg:
        return y * canvasSize.height / (Platform.isIOS ? imageSize.height : imageSize.width);
      default:
        return y * canvasSize.height / imageSize.height;
    }
  }

  // Draw Asset Helpers
  void _drawBody(Canvas canvas, int id, Map<PoseLandmarkType, Offset> marks, double Function(double) tx, double Function(double) ty, double width) {
    final ls = marks[PoseLandmarkType.leftShoulder];
    final rs = marks[PoseLandmarkType.rightShoulder];
    final lh = marks[PoseLandmarkType.leftHip];
    final rh = marks[PoseLandmarkType.rightHip];
    if (ls == null || rs == null) return;

    final imgName = id == 1 ? 'body1' : 'body2';
    final img = AssetManager().getImage(imgName);
    if (img == null) return;

    final lsX = tx(ls.dx);
    final lsY = ty(ls.dy);
    final rsX = tx(rs.dx);
    final rsY = ty(rs.dy);
    final midX = (lsX + rsX) / 2;
    final midY = (lsY + rsY) / 2;

    double bodyHeight = width * 1.1;
    if (lh != null && rh != null) {
      final midHipY = (ty(lh.dy) + ty(rh.dy)) / 2;
      bodyHeight = (midHipY - midY) * 1.3;
    }

    final scaledW = width * 1.2;
    final scaledH = bodyHeight * 1.2;

    double angle = atan2(rsY - lsY, rsX - lsX);
    if (cameraLensDirection != CameraLensDirection.front) angle = atan2(lsY - rsY, lsX - rsX);

    canvas.save();
    canvas.translate(midX, midY);
    canvas.rotate(angle);
    if (cameraLensDirection == CameraLensDirection.front) canvas.scale(-1, 1);
    
    final rect = Rect.fromCenter(center: const Offset(0, 0), width: scaledW, height: scaledH);
    paintImage(canvas: canvas, rect: rect, image: img, fit: BoxFit.fill);
    canvas.restore();
  }

  void _drawShoulders(Canvas canvas, int id, Map<PoseLandmarkType, Offset> marks, double Function(double) tx, double Function(double) ty, double width) {
    final ls = marks[PoseLandmarkType.leftShoulder];
    final rs = marks[PoseLandmarkType.rightShoulder];
    if (ls == null || rs == null) return;

    final imgLeft = AssetManager().getImage(id == 1 ? 'shoulder1_right' : 'shoulder2_right'); // Mirrored logically
    final imgRight = AssetManager().getImage(id == 1 ? 'shoulder1_left' : 'shoulder2_left');
    if (imgLeft == null || imgRight == null) return;

    final padSize = width * 0.7 * 1.2;

    final lsX = tx(ls.dx);
    final lsY = ty(ls.dy);
    final rsX = tx(rs.dx);
    final rsY = ty(rs.dy);

    double angle = atan2(rsY - lsY, rsX - lsX);
    if (cameraLensDirection != CameraLensDirection.front) angle = atan2(lsY - rsY, lsX - rsX);

    // Left Pad
    canvas.save();
    canvas.translate(rsX, rsY);
    canvas.rotate(angle);
    paintImage(canvas: canvas, rect: Rect.fromCenter(center: Offset.zero, width: padSize, height: padSize), image: imgLeft, fit: BoxFit.fill);
    canvas.restore();

    // Right Pad
    canvas.save();
    canvas.translate(lsX, lsY);
    canvas.rotate(angle);
    paintImage(canvas: canvas, rect: Rect.fromCenter(center: Offset.zero, width: padSize, height: padSize), image: imgRight, fit: BoxFit.fill);
    canvas.restore();
  }

  void _drawHands(Canvas canvas, int id, Map<PoseLandmarkType, Offset> marks, double Function(double) tx, double Function(double) ty, double size) {
    final lw = marks[PoseLandmarkType.leftWrist];
    final rw = marks[PoseLandmarkType.rightWrist];
    final le = marks[PoseLandmarkType.leftElbow];
    final re = marks[PoseLandmarkType.rightElbow];

    final handRImg = AssetManager().getImage('hand_right');
    final handLImg = AssetManager().getImage('hand_left');

    final handSize = size * 1.1;

    if (lw != null && le != null && handRImg != null) {
      _drawRotatedHand(canvas, handRImg, lw, le, tx, ty, handSize);
    }
    if (rw != null && re != null && handLImg != null) {
      _drawRotatedHand(canvas, handLImg, rw, re, tx, ty, handSize);
    }
  }

  void _drawRotatedHand(Canvas canvas, ui.Image img, Offset wrist, Offset elbow, double Function(double) tx, double Function(double) ty, double size) {
    final wx = tx(wrist.dx);
    final wy = ty(wrist.dy);
    final ex = tx(elbow.dx);
    final ey = ty(elbow.dy);

    double angle = atan2(wy - ey, wx - ex);
    if (cameraLensDirection != CameraLensDirection.front) angle = atan2(ey - wy, ex - wx);

    canvas.save();
    canvas.translate(wx, wy);
    canvas.rotate(angle - pi / 2); // -90 deg
    if (cameraLensDirection == CameraLensDirection.front) canvas.scale(-1, 1);
    
    paintImage(canvas: canvas, rect: Rect.fromCenter(center: Offset.zero, width: size, height: size), image: img, fit: BoxFit.fill);
    canvas.restore();
  }

  void _drawHead(Canvas canvas, int id, Map<PoseLandmarkType, Offset> marks, double Function(double) tx, double Function(double) ty, double size) {
    final nose = marks[PoseLandmarkType.nose];
    final le = marks[PoseLandmarkType.leftEye];
    final re = marks[PoseLandmarkType.rightEye];
    if (nose == null) return;

    final img = AssetManager().getImage(id == 1 ? 'head1' : 'head2');
    if (img == null) return;

    final nx = tx(nose.dx);
    final ny = ty(nose.dy);
    
    double angle = 0.0;
    if (le != null && re != null) {
      angle = atan2(ty(re.dy) - ty(le.dy), tx(re.dx) - tx(le.dx));
      if (cameraLensDirection != CameraLensDirection.front) {
        angle = atan2(ty(le.dy) - ty(re.dy), tx(le.dx) - tx(re.dx));
      }
    }

    final scaledSize = size * 1.3;

    canvas.save();
    canvas.translate(nx, ny);
    canvas.rotate(angle);
    if (cameraLensDirection == CameraLensDirection.front) canvas.scale(-1, 1);
    
    paintImage(canvas: canvas, rect: Rect.fromCenter(center: Offset(0, -scaledSize * 0.25), width: scaledSize, height: scaledSize), image: img, fit: BoxFit.fill);
    canvas.restore();
  }

  void _drawDashedLine(Canvas canvas, Offset p1, Offset p2, Paint paint) {
    const int dashWidth = 10;
    const int dashSpace = 10;
    double distance = (p2 - p1).distance;
    double dx = (p2.dx - p1.dx) / distance;
    double dy = (p2.dy - p1.dy) / distance;

    double i = 0;
    while (i < distance) {
      canvas.drawLine(
        Offset(p1.dx + dx * i, p1.dy + dy * i),
        Offset(p1.dx + dx * (i + dashWidth), p1.dy + dy * (i + dashWidth)),
        paint,
      );
      i += dashWidth + dashSpace;
    }
  }

  @override
  bool shouldRepaint(covariant OverlayPainter oldDelegate) => true; // Repaint driven by GameEngine
}
