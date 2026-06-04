import 'package:flutter/material.dart';

class RainbowTransition extends StatefulWidget {
  final Widget child;
  final Animation<double> animation;

  const RainbowTransition({
    Key? key,
    required this.child,
    required this.animation,
  }) : super(key: key);

  @override
  State<RainbowTransition> createState() => _RainbowTransitionState();
}

class _RainbowTransitionState extends State<RainbowTransition> {
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.animation,
      builder: (context, child) {
        return Stack(
          children: [
            widget.child,
            if (widget.animation.value > 0.0 && widget.animation.value < 1.0)
              Positioned.fill(
                child: CustomPaint(
                  painter: RainbowPainter(progress: widget.animation.value),
                ),
              ),
          ],
        );
      },
      child: widget.child,
    );
  }
}

class RainbowPainter extends CustomPainter {
  final double progress;

  RainbowPainter({required this.progress});

  @override
  void paint(Canvas canvas, Size size) {
    // Replicating the Native logic:
    // 0f to 1.5f progress in Native. Here we map 0.0 to 1.0.
    // However, page transitions in Flutter happen where 'progress' goes 0 -> 1 for the new page.
    // It's better to implement this as an Overlay that plays a full sweep.

    // Colors exactly as Native
    final colors = [
      const Color(0xFFFF0000), // Red
      const Color(0xFFFF7F00), // Orange
      const Color(0xFFFFFF00), // Yellow
      const Color(0xFF00FF00), // Green
      const Color(0xFF0000FF), // Blue
      const Color(0xFF4B0082), // Indigo
      const Color(0xFF9400D3), // Violet
      Colors.transparent
    ];

    // Map progress 0 -> 1 to sweep across screen.
    // To make it cover then reveal, we actually need a custom navigation approach or a two-step animation.
    // For a transition builder, 0.0 -> 0.5 is covering, 0.5 -> 1.0 is revealing.

    final width = size.width;
    final gradientWidth = width * 0.8;

    if (progress <= 0.5) {
      // Sweeping in (Covering)
      // Map 0 -> 0.5 to 0 -> 1.5
      double p = progress * 3.0; // 0.0 to 1.5
      double currentX = width * p;
      double startX = currentX;
      double endX = currentX - gradientWidth;

      final paint = Paint()
        ..shader = LinearGradient(
          colors: colors,
          begin: Alignment.centerRight,
          end: Alignment.centerLeft,
        ).createShader(Rect.fromLTRB(endX, 0, startX, size.height));

      // Solid rect behind
      canvas.drawRect(Rect.fromLTRB(0, 0, startX, size.height), Paint()..color = colors[0]);
      // Gradient edge
      canvas.drawRect(Rect.fromLTRB(endX, 0, startX, size.height), paint);

    } else {
      // Sweeping out (Revealing)
      // Map 0.5 -> 1.0 to 0 -> 1.5
      double p = (progress - 0.5) * 3.0;
      double currentX = width * p;
      double startX = currentX;
      double endX = currentX + gradientWidth;

      final reverseColors = colors.reversed.toList();

      final paint = Paint()
        ..shader = LinearGradient(
          colors: reverseColors,
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
        ).createShader(Rect.fromLTRB(startX, 0, endX, size.height));

      canvas.drawRect(Rect.fromLTRB(currentX, 0, width, size.height), Paint()..color = reverseColors.last);
      canvas.drawRect(Rect.fromLTRB(startX, 0, endX, size.height), paint);
    }
  }

  @override
  bool shouldRepaint(covariant RainbowPainter oldDelegate) {
    return oldDelegate.progress != progress;
  }
}

class RainbowPageRoute<T> extends PageRouteBuilder<T> {
  final Widget page;

  RainbowPageRoute({required this.page})
      : super(
          pageBuilder: (context, animation, secondaryAnimation) => page,
          transitionDuration: const Duration(milliseconds: 1200), // Native uses 600ms per sweep, total 1200
          transitionsBuilder: (context, animation, secondaryAnimation, child) {
            return RainbowTransition(
              animation: animation,
              child: child,
            );
          },
        );
}
