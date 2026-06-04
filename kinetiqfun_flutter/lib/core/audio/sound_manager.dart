import 'package:audioplayers/audioplayers.dart';

class SoundManager {
  static final SoundManager _instance = SoundManager._internal();
  factory SoundManager() => _instance;
  SoundManager._internal();

  final AudioPlayer _bgmPlayer = AudioPlayer();
  // Pool of SFX players to allow overlapping sounds
  final List<AudioPlayer> _sfxPlayers = List.generate(5, (_) => AudioPlayer());
  int _currentSfxIndex = 0;

  Future<void> init() async {
    _bgmPlayer.setReleaseMode(ReleaseMode.loop);
    // Preload common sounds if necessary, though audioplayers handles it well on the fly
  }

  Future<void> playBgm(String assetPath) async {
    await _bgmPlayer.play(AssetSource(assetPath));
  }

  Future<void> stopBgm() async {
    await _bgmPlayer.stop();
  }

  Future<void> playSfx(String assetPath, {double pitch = 1.0}) async {
    final player = _sfxPlayers[_currentSfxIndex];
    _currentSfxIndex = (_currentSfxIndex + 1) % _sfxPlayers.length;
    
    await player.setPlaybackRate(pitch);
    await player.play(AssetSource(assetPath), mode: PlayerMode.lowLatency);
  }

  Future<void> playClick() async {
    await playSfx('audio/button_click_sfx.wav');
  }

  Future<void> playTransition() async {
    await playSfx('audio/transition_sfx.wav');
  }

  Future<void> playAction({double pitch = 1.0}) async {
    await playSfx('audio/box_crack.wav', pitch: pitch);
  }

  Future<void> playVictory() async {
    await playSfx('audio/win_sfx.wav');
  }

  Future<void> playCapture() async {
    await playSfx('audio/capture.wav');
  }

  void dispose() {
    _bgmPlayer.dispose();
    for (var player in _sfxPlayers) {
      player.dispose();
    }
  }
}
