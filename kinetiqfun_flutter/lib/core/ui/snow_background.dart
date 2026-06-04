import 'dart:math';
import 'package:flutter/material.dart';

class SnowBackground extends StatefulWidget {
  final Widget child;

  const SnowBackground({Key? key, required this.child}) : super(key: key);

  @override
  State<SnowBackground> createState() => _SnowBackgroundState();
}

class _SnowBackgroundState extends State<SnowBackground> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  final List<_Snowflake> _snowflakes = [];
  final int maxSnowflakes = 120;
  final Random _random = Random();
  bool _isInitialized = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1), // Ticker duration doesn't matter much as we repeat it
    )..addListener(() {
        _updateSnowflakes();
      });
    _controller.repeat();
  }

  void _initSnowflakes(Size size) {
    if (_isInitialized) return;
    _snowflakes.clear();
    for (int i = 0; i < maxSnowflakes; i++) {
      _snowflakes.add(_createRandomSnowflake(size, true));
    }
    _isInitialized = true;
  }

  _Snowflake _createRandomSnowflake(Size size, bool randomY) {
    double yPos = randomY ? _random.nextDouble() * size.height : -20.0;
    return _Snowflake(
      x: _random.nextDouble() * size.width,
      y: yPos,
      radius: _random.nextDouble() * 4.0 + 1.5,
      speedX: _random.nextDouble() * 2.0 - 1.0,
      speedY: _random.nextDouble() * 2.0 + 1.0,
      alpha: _random.nextInt(155) + 100,
    );
  }

  void _updateSnowflakes() {
    if (!mounted || !_isInitialized) return;
    
    // To get screen size for updates
    final RenderBox? renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.hasSize) return;
    final size = renderBox.size;

    for (int i = 0; i < _snowflakes.length; i++) {
      var flake = _snowflakes[i];
      flake.y += flake.speedY;
      flake.x += flake.speedX;
      
      // Sine wave sway
      flake.x += (sin(flake.y / 30.0) * 0.8);

      // Reset if out of bounds
      if (flake.y > size.height + flake.radius || 
          flake.x < -flake.radius - 20.0 || 
          flake.x > size.width + flake.radius + 20.0) {
        _snowflakes[i] = _createRandomSnowflake(size, false);
      }
    }
    setState(() {}); // Trigger repaint
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final size = Size(constraints.maxWidth, constraints.maxHeight);
        if (size.width > 0 && size.height > 0) {
          _initSnowflakes(size);
        }
        return Stack(
          children: [
            // Background Image
            Positioned.fill(
              child: Image.asset(
                'assets/images/loading_bg.png',
                fit: BoxFit.cover, // Replicates scale + translate matrix in Native
                errorBuilder: (context, error, stackTrace) => Container(color: Colors.deepPurple[900]),
              ),
            ),
            // Snowflakes
            Positioned.fill(
              child: CustomPaint(
                painter: _SnowPainter(snowflakes: _snowflakes),
              ),
            ),
            // Content
            Positioned.fill(
              child: widget.child,
            ),
          ],
        );
      },
    );
  }
}

class _Snowflake {
  double x;
  double y;
  double radius;
  double speedX;
  double speedY;
  int alpha;

  _Snowflake({
    required this.x,
    required this.y,
    required this.radius,
    required this.speedX,
    required this.speedY,
    required this.alpha,
  });
}

class _SnowPainter extends CustomPainter {
  final List<_Snowflake> snowflakes;
  final Paint _paint = Paint()..color = Colors.white;

  _SnowPainter({required this.snowflakes});

  @override
  void paint(Canvas canvas, Size size) {
    for (var flake in snowflakes) {
      _paint.color = Colors.white.withAlpha(flake.alpha);
      canvas.drawCircle(Offset(flake.x, flake.y), flake.radius, _paint);
    }
  }

  @override
  bool shouldRepaint(covariant _SnowPainter oldDelegate) => true; // Constant repaint driven by AnimationController
}
