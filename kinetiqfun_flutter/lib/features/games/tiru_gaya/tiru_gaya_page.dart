import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';
import '../base_pose_screen.dart';
import '../game_engine.dart';
import '../../../core/audio/sound_manager.dart';
import '../../../core/camera/pose_evaluator.dart';

class TiruGayaPage extends BasePoseScreen {
  const TiruGayaPage({super.key});

  @override
  State<TiruGayaPage> createState() => _TiruGayaPageState();
}

class _TiruGayaPageState extends BasePoseScreenState<TiruGayaPage> {
  int _currentRound = 1;
  int _p1Score = 0;
  int _p2Score = 0;
  final int _totalRounds = 4;
  
  bool _isWaitingForPlayers = true;
  bool _isGameStarted = false;
  bool _isFlash = false;
  
  int _roundTimer = 5;
  Timer? _timer;
  
  final List<String> _targetPoses = [
    'assets/images/posebuaya.png',
    'assets/images/poserocket.png',
    'assets/images/posekucing.png',
    'assets/images/posedino.png'
  ];
  
  List<double> _p1SimScore = [0, 0, 0, 0];
  List<double> _p2SimScore = [0, 0, 0, 0];
  
  int _currentTargetIndex = 0;
  String _roundStatus = "";
  bool _showSnapshot = false;

  Pose? _lastP1Pose;
  Pose? _lastP2Pose;

  @override
  void initState() {
    super.initState();
    engine.setGameMode(GameMode.tiruGaya);
    SoundManager().playBgm('audio/tiru_gaya.mp3');
    _targetPoses.shuffle();
    _startWaiting();
  }
  
  void _startWaiting() {
    _isWaitingForPlayers = true;
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      if (_lastP1Pose != null && _lastP2Pose != null) {
        // Both found
        timer.cancel();
        setState(() {
          _isWaitingForPlayers = false;
        });
        _startRound();
      }
    });
  }

  void _startRound() {
    _showSnapshot = false;
    _roundTimer = 5;
    _isGameStarted = true;
    _roundStatus = "Round \$_currentRound";
    
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) return;
      setState(() {
        _roundTimer--;
        if (_roundTimer <= 0) {
          timer.cancel();
          _captureAndScore();
        } else {
          SoundManager().playClick();
        }
      });
    });
  }
  
  void _captureAndScore() async {
    // Flash effect
    SoundManager().playCapture();
    setState(() { _isFlash = true; });
    
    // Calculate dummy similarity (in a real app, this compares features)
    // Here we use PoseEvaluator to extract current features and compare with random for now.
    double p1Sim = 0.0;
    double p2Sim = 0.0;
    
    if (_lastP1Pose != null) {
      final p1f = PoseEvaluator.extractFeatures(_lastP1Pose!);
      // Compare with some ideal features or just generate a random high score for demonstration if target features aren't easily extractable
      p1Sim = 60.0 + (engine.random.nextDouble() * 30.0); 
    }
    if (_lastP2Pose != null) {
      final p2f = PoseEvaluator.extractFeatures(_lastP2Pose!);
      p2Sim = 60.0 + (engine.random.nextDouble() * 30.0);
    }
    
    _p1SimScore[_currentRound - 1] = p1Sim;
    _p2SimScore[_currentRound - 1] = p2Sim;
    
    if (p1Sim > p2Sim) {
      _p1Score++;
      _roundStatus = "Player 1 Wins Round!";
    } else {
      _p2Score++;
      _roundStatus = "Player 2 Wins Round!";
    }

    await Future.delayed(const Duration(milliseconds: 100));
    if (mounted) setState(() { _isFlash = false; _showSnapshot = true; });
    
    // Wait before next round
    await Future.delayed(const Duration(seconds: 4));
    if (mounted) {
      _currentRound++;
      _currentTargetIndex = (_currentTargetIndex + 1) % _targetPoses.length;
      
      if (_currentRound > _totalRounds) {
        if (_p1Score > _p2Score) {
          engine.setWinner(engine.nameP1, MediaQuery.of(context).size.width, MediaQuery.of(context).size.height);
          SoundManager().playVictory();
        } else if (_p2Score > _p1Score) {
          engine.setWinner(engine.nameP2, MediaQuery.of(context).size.width, MediaQuery.of(context).size.height);
          SoundManager().playVictory();
        } else {
          // Tie break (Sudden Death)
          _totalRounds + 1; // Simplification
          _startRound();
        }
      } else {
        _startRound();
      }
    }
  }

  @override
  void onPoseDetected(List<Pose> poses, Size imageSize) {
    if (poses.isEmpty) return;
    
    for (var pose in poses) {
      final nose = pose.landmarks[PoseLandmarkType.nose];
      if (nose != null) {
        if (nose.x > (imageSize.width / 2)) {
          _lastP1Pose = pose; // P1 on left side of mirrored screen (so right side of image)
        } else {
          _lastP2Pose = pose;
        }
      }
    }
  }

  @override
  Widget buildGameUI(BuildContext context) {
    final size = MediaQuery.of(context).size;
    
    if (_isWaitingForPlayers) {
      return Center(
        child: Container(
          padding: const EdgeInsets.all(20),
          color: Colors.black87,
          child: const Text('Menunggu Pemain...', style: TextStyle(color: Colors.white, fontSize: 36, fontWeight: FontWeight.bold, fontFamily: 'game_font')),
        ),
      );
    }
    
    return Stack(
      children: [
        // ROUND X/Y Top Center
        if (_isGameStarted && !_showSnapshot)
          Align(
            alignment: Alignment.topCenter,
            child: Padding(
              padding: const EdgeInsets.only(top: 32),
              child: Text(
                'ROUND $_currentRound/$_totalRounds',
                style: const TextStyle(
                  fontFamily: 'game_font',
                  fontSize: 40,
                  color: Colors.white,
                  shadows: [Shadow(color: Colors.black, blurRadius: 5, offset: Offset(2, 2))],
                ),
              ),
            ),
          ),
          
        // Global Scores Top Corners
        Positioned(
          top: 32, left: 32,
          child: Text('P1 SKOR: $_p1Score', style: const TextStyle(fontFamily: 'game_font', fontSize: 36, color: Color(0xFFFFDD00), shadows: [Shadow(color: Colors.black, blurRadius: 5, offset: Offset(2, 2))])),
        ),
        Positioned(
          top: 32, right: 32,
          child: Text('SKOR P2: $_p2Score', style: const TextStyle(fontFamily: 'game_font', fontSize: 36, color: Color(0xFFFFDD00), shadows: [Shadow(color: Colors.black, blurRadius: 5, offset: Offset(2, 2))])),
        ),

        // Center Target Image
        if (_isGameStarted && !_showSnapshot)
          Align(
            alignment: const Alignment(0, 0.4), // Margin bottom 32dp equivalent
            child: Image.asset(
              _targetPoses[_currentTargetIndex],
              width: 250,
              height: 250,
              fit: BoxFit.contain,
            ),
          ),
          
        // Timer Text Center
        if (_isGameStarted && !_showSnapshot)
          Center(
            child: Text(
              '$_roundTimer',
              style: const TextStyle(
                fontFamily: 'game_font',
                fontSize: 180,
                color: Color(0xFFFFDD00),
                shadows: [Shadow(color: Colors.black, blurRadius: 15, offset: Offset(5, 5))],
              ),
            ),
          ),
          
        // Flash overlay
        if (_isFlash)
          Container(color: Colors.white),
          
        // Snapshot / Round Result (Polaroid Layout)
        if (_showSnapshot)
          Stack(
            children: [
              // Dark Background Overlay
              Container(color: const Color(0x99000000)),
              
              // P1 Polaroid (Left)
              Align(
                alignment: const Alignment(-0.6, 0.2), // Bias ~0.6, left of center
                child: Transform.rotate(
                  angle: -8 * 3.1415926535 / 180,
                  child: Card(
                    color: Colors.white,
                    elevation: 16,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    child: Container(
                      width: 350,
                      padding: const EdgeInsets.only(left: 16, right: 16, top: 16, bottom: 24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            height: 250,
                            color: Colors.black,
                            width: double.infinity,
                            // (In actual app, put camera image here)
                          ),
                          const SizedBox(height: 16),
                          Text(
                            '${_p1SimScore[_currentRound - 1].toInt()}% MIRIP',
                            style: const TextStyle(fontFamily: 'game_font', fontSize: 28, color: Color(0xFF333333)),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),

              // P2 Polaroid (Right)
              Align(
                alignment: const Alignment(0.6, 0.2), // Bias ~0.6, right of center
                child: Transform.rotate(
                  angle: 8 * 3.1415926535 / 180,
                  child: Card(
                    color: Colors.white,
                    elevation: 16,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    child: Container(
                      width: 350,
                      padding: const EdgeInsets.only(left: 16, right: 16, top: 16, bottom: 24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            height: 250,
                            color: Colors.black,
                            width: double.infinity,
                          ),
                          const SizedBox(height: 16),
                          Text(
                            '${_p2SimScore[_currentRound - 1].toInt()}% MIRIP',
                            style: const TextStyle(fontFamily: 'game_font', fontSize: 28, color: Color(0xFF333333)),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              
              // Winner Text
              Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 48),
                  child: Text(
                    _p1SimScore[_currentRound - 1] > _p2SimScore[_currentRound - 1] 
                      ? "P1 LEBIH MANTAP!" 
                      : "P2 LEBIH MANTAP!",
                    style: const TextStyle(
                      fontFamily: 'game_font',
                      fontSize: 40,
                      color: Color(0xFFFFDD00),
                      shadows: [Shadow(color: Colors.black, blurRadius: 5, offset: Offset(3, 3))],
                    ),
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
            ],
          ),
      ],
    );
  }
}
