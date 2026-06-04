import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import '../menu/menu_page.dart';
import '../../core/audio/sound_manager.dart';
import '../../core/ui/snow_background.dart';
import '../../core/ui/rainbow_transition.dart';
import 'tutorial_page.dart';

class LoadingPage extends StatefulWidget {
  const LoadingPage({super.key});

  @override
  State<LoadingPage> createState() => _LoadingPageState();
}

class _LoadingPageState extends State<LoadingPage> with TickerProviderStateMixin {
  int progressStatus = 0;
  bool isReady = false;
  late AnimationController _logoAnimController;
  late Animation<double> _logoScaleAnim;
  late Timer _textTimer;
  int _textIndex = 0;
  final List<String> _loadingTexts = ["Loading", "Loading.", "Loading..", "Loading..."];
  late String _currentTip;

  final List<String> _gameTips = [
    "Tip: Posisikan tubuh Anda 2 meter dari kamera.",
    "Tip: Pastikan pencahayaan ruangan cukup terang.",
    "Tip: Gunakan pakaian yang kontras dengan latar belakang.",
    "Tip: Ikuti gerakan di layar seakurat mungkin!",
    "Tip: Ajak teman untuk melihat skormu!"
  ];

  @override
  void initState() {
    super.initState();
    SoundManager().playBgm('audio/background_music.mp3'); // We'll update SoundManager later

    _currentTip = _gameTips[Random().nextInt(_gameTips.length)];

    _logoAnimController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);

    _logoScaleAnim = Tween<double>(begin: 1.0, end: 1.05).animate(
      CurvedAnimation(parent: _logoAnimController, curve: Curves.easeInOut),
    );

    _textTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
      if (mounted && !isReady) {
        setState(() {
          _textIndex = (_textIndex + 1) % _loadingTexts.length;
        });
      }
    });

    _startLoading();
  }

  void _startLoading() async {
    while (progressStatus < 100) {
      await Future.delayed(const Duration(milliseconds: 35)); // 100 loops * 35ms = ~3.5s
      if (mounted) {
        setState(() {
          progressStatus += 1;
        });
      }
    }
    if (mounted) {
      setState(() {
        isReady = true;
      });
    }
  }

  void _goToMenu() {
    Navigator.of(context).pushReplacement(
      RainbowPageRoute(page: const TutorialPage()),
    );
  }

  @override
  void dispose() {
    _logoAnimController.dispose();
    _textTimer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1E1E1E), // colorBackgroundDark approximation
      body: Stack(
        fit: StackFit.expand,
        children: [
          // SnowBackground with Alpha
          Opacity(
            opacity: 0.3,
            child: const SnowBackground(child: SizedBox.expand()),
          ),
          
          // Elements matching ConstraintLayout
          SafeArea(
            child: LayoutBuilder(builder: (context, constraints) {
              return Stack(
                children: [
                  // Logo Game
                  Align(
                    alignment: const Alignment(0, -0.4),
                    child: ScaleTransition(
                      scale: _logoScaleAnim,
                      child: Image.asset(
                        'assets/images/game_logo.png',
                        width: 320,
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),

                  // Teks Loading
                  if (!isReady)
                    Align(
                      alignment: const Alignment(0, 0.45),
                      child: Text(
                        _loadingTexts[_textIndex].toUpperCase(),
                        style: const TextStyle(
                          color: Color(0xFFE91E63), // colorPrimary approximate
                          fontFamily: 'game_font',
                          fontSize: 22,
                          letterSpacing: 1.0,
                          fontWeight: FontWeight.bold,
                          shadows: [Shadow(color: Color(0xFFE91E63), blurRadius: 10)],
                        ),
                      ),
                    ),

                  // Progress Bar & Angka Persentase
                  if (!isReady)
                    Positioned(
                      bottom: 60,
                      left: 80,
                      right: 80,
                      child: SizedBox(
                        height: 16,
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            // Background Progress Bar (Dashed/Empty)
                            Container(
                              decoration: BoxDecoration(
                                color: Colors.white24,
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            // Progress Bar Active
                            ClipRRect(
                              borderRadius: BorderRadius.circular(8),
                              child: LinearProgressIndicator(
                                value: progressStatus / 100.0,
                                minHeight: 16,
                                backgroundColor: Colors.transparent,
                                valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFFE91E63)),
                              ),
                            ),
                            // Angka Persentase Overlay
                            Text(
                              '$progressStatus%',
                              style: const TextStyle(
                                color: Colors.white,
                                fontFamily: 'game_font',
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                shadows: [Shadow(color: Colors.black, blurRadius: 4)],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),

                  // Game Tips (Under Progress Bar in Native it was above or below?)
                  // In XML: layout_marginTop="24dp" app:layout_constraintTop_toBottomOf="@+id/progressBar"
                  // So it is 24dp below the progress bar? Wait, if progress bar is bottom 60dp, then tips is bottom 36dp approximately.
                  if (!isReady)
                    Positioned(
                      bottom: 24, // 24dp from bottom
                      left: 80,
                      right: 80,
                      child: Text(
                        _currentTip,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.grey, // textColorSecondary
                          fontSize: 16,
                          fontStyle: FontStyle.italic,
                        ),
                      ),
                    ),

                  // Start Button
                  if (isReady)
                    Positioned(
                      bottom: 50,
                      left: 0,
                      right: 0,
                      child: Center(
                        child: AnimatedOpacity(
                          opacity: 1.0,
                          duration: const Duration(milliseconds: 500),
                          child: OutlinedButton(
                            onPressed: _goToMenu,
                            style: OutlinedButton.styleFrom(
                              fixedSize: const Size(240, 64),
                              side: const BorderSide(color: Color(0xFFE91E63), width: 3),
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(32)),
                            ),
                            child: const Text(
                              'START',
                              style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Color(0xFFE91E63)),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              );
            }),
          ),
        ],
      ),
    );
  }
}
