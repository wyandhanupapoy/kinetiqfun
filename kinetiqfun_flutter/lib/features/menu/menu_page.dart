import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import '../games/kesatria/kesatria_page.dart';
import '../games/geol/geol_page.dart';
import '../games/tiru_gaya/tiru_gaya_page.dart';
import '../../core/audio/sound_manager.dart';
import '../../core/ui/rainbow_transition.dart';
import '../../core/ui/snow_background.dart';

class MenuPage extends StatefulWidget {
  const MenuPage({super.key});

  @override
  State<MenuPage> createState() => _MenuPageState();
}

class _MenuPageState extends State<MenuPage> {
  late PageController _pageController;
  double _currentPageValue = 1.0; // Start at middle item

  final List<Map<String, dynamic>> games = [
    {
      'title': 'Kesatria PCD',
      'icon': 'assets/images/icongame.png', // Replace with correct later
      'route': '/kesatria'
    },
    {
      'title': 'Geol Kicau Mania',
      'icon': 'assets/images/icongame.png',
      'route': '/geol'
    },
    {
      'title': 'Tiru Gaya',
      'icon': 'assets/images/icongame.png',
      'route': '/tiru_gaya'
    },
  ];

  @override
  void initState() {
    super.initState();
    _pageController = PageController(viewportFraction: 0.5, initialPage: 1);
    _pageController.addListener(() {
      setState(() {
        _currentPageValue = _pageController.page!;
      });
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _showDevInfo() {
    SoundManager().playClick();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.indigo[900],
        title: const Text('Developer Info', style: TextStyle(color: Colors.white)),
        content: const Text(
          'Dibuat oleh Tim KinetiQFun\nPoliteknik Negeri Bandung',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () {
              SoundManager().playClick();
              Navigator.of(context).pop();
            },
            child: const Text('Tutup', style: TextStyle(color: Colors.pinkAccent)),
          ),
        ],
      ),
    );
  }

  void _navigateToGame(String route) {
    SoundManager().playTransition();
    Widget targetPage;
    if (route == '/kesatria') {
      targetPage = const KesatriaPage();
    } else if (route == '/geol') {
      targetPage = const GeolKicauManiaPage();
    } else {
      targetPage = const TiruGayaPage();
    }

    Navigator.of(context).push(RainbowPageRoute(page: targetPage));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black, // Dark background like Native
      body: Stack(
        children: [
          // Background (SnowBackground)
          Positioned.fill(
            child: SnowBackground(
              child: const SizedBox.expand(),
            ),
          ),
          
          SafeArea(
            child: Stack(
              children: [
                // Top Left Logo
                Positioned(
                  top: 16,
                  left: 16,
                  child: Image.asset(
                    'assets/images/game_logo.png',
                    width: 120,
                    fit: BoxFit.contain,
                  ),
                ),

                // Top Right Info Button
                Positioned(
                  top: 16,
                  right: 16,
                  child: Material(
                    color: const Color(0xFF03A9F4),
                    borderRadius: BorderRadius.circular(4),
                    elevation: 8,
                    child: InkWell(
                      onTap: _showDevInfo,
                      child: Container(
                        width: 48,
                        height: 48,
                        alignment: Alignment.center,
                        child: const Text(
                          "!",
                          style: TextStyle(
                            fontFamily: 'game_font',
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),

                // Title: Select Game
                const Align(
                  alignment: Alignment.topCenter,
                  child: Padding(
                    padding: EdgeInsets.only(top: 24),
                    child: Text(
                      "Select Game",
                      style: TextStyle(
                        fontFamily: 'game_font',
                        fontSize: 32,
                        color: Colors.white,
                        shadows: [Shadow(color: Colors.black, blurRadius: 4, offset: Offset(3, 3))],
                      ),
                    ),
                  ),
                ),

                // Carousel
                Column(
                  children: [
                    const SizedBox(height: 100), // Space for top elements
                    Expanded(
                      child: PageView.builder(
                        controller: _pageController,
                        itemCount: games.length,
                        itemBuilder: (context, index) {
                          final isSelected = (_currentPageValue.round() == index);
                          
                          // Math for tilt and blur
                          double difference = index - _currentPageValue;
                          
                          // Scale: middle is 1.0, edges are 0.8
                          double scale = 1.0 - (difference.abs() * 0.2).clamp(0.0, 0.2);
                          
                          // Tilt: Y-axis rotation based on distance from center
                          double tilt = difference * 0.5; // radians (approx 30 deg at edges)
                          
                          // Blur: 0 at center, max 10 at edges
                          double blurAmount = (difference.abs() * 5.0).clamp(0.0, 10.0);

                          Widget card = GestureDetector(
                            onTap: () {
                              if (isSelected) {
                                _navigateToGame(games[index]['route']);
                              } else {
                                _pageController.animateToPage(index, duration: const Duration(milliseconds: 300), curve: Curves.easeInOut);
                              }
                            },
                            child: SizedBox(
                              width: 320,
                              child: Stack(
                                alignment: Alignment.center,
                                children: [
                                  // Cardbox Container
                                  Image.asset('assets/images/cardbox.png', fit: BoxFit.contain, width: 320),
                                  
                                  // Screenshot Placeholder (Ratio 16:9, Width 84%, Vert Bias 0.42)
                                  Align(
                                    alignment: const Alignment(0, -0.16), // Bias 0.42 approx (0 = center, -1 = top, +1 = bot)
                                    child: FractionallySizedBox(
                                      widthFactor: 0.84,
                                      child: AspectRatio(
                                        aspectRatio: 16 / 9,
                                        child: Container(
                                          color: Colors.black,
                                          alignment: Alignment.center,
                                          child: const Text(
                                            "Tangkap Layar Game",
                                            style: TextStyle(
                                              color: Colors.white,
                                              fontFamily: 'game_font',
                                              fontSize: 14,
                                            ),
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                  
                                  // Judul Game (Bottom margin 24)
                                  Positioned(
                                    bottom: 24,
                                    child: Text(
                                      games[index]['title'],
                                      style: const TextStyle(
                                        fontFamily: 'game_font',
                                        fontSize: 22,
                                        fontWeight: FontWeight.bold,
                                        color: Colors.white,
                                        shadows: [Shadow(color: Colors.black, blurRadius: 4, offset: Offset(2, 2))],
                                      ),
                                      textAlign: TextAlign.center,
                                    ),
                                  ),
                                  
                                  // Overlay Efek Gelap (Alpha based on distance)
                                  if (blurAmount > 0.1)
                                    Positioned.fill(
                                      child: Container(
                                        color: Colors.black.withOpacity((difference.abs() * 0.6).clamp(0.0, 0.6)),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                          );

                          // Apply ImageFilter for Blur (Only applies effectively to non-selected items)
                          if (blurAmount > 0.1) {
                            card = ImageFiltered(
                              imageFilter: ImageFilter.blur(sigmaX: blurAmount, sigmaY: blurAmount),
                              child: card,
                            );
                          }

                          // Apply Transform
                          return Transform(
                            alignment: Alignment.center,
                            transform: Matrix4.identity()
                              ..setEntry(3, 2, 0.001) // perspective
                              ..rotateY(-tilt)
                              ..scale(scale),
                            child: Opacity(
                              opacity: 1.0 - (difference.abs() * 0.3).clamp(0.0, 0.5),
                              child: card,
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
