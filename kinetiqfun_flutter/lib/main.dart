import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'features/splash/splash_page.dart';
import 'core/audio/sound_manager.dart';
import 'core/assets/asset_manager.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Kunci orientasi ke landscape dan sembunyikan System UI (immersive mode)
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);

  // Inisialisasi Audio dan Assets
  await SoundManager().init();
  await AssetManager().init();

  runApp(const KinetiqFunApp());
}

class KinetiqFunApp extends StatelessWidget {
  const KinetiqFunApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'KinetiQFun',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.deepPurple,
          brightness: Brightness.dark, // Sesuai tema game biasanya
        ),
        useMaterial3: true,
        fontFamily: 'Inter', // Bisa diganti sesuai kebutuhan
      ),
      debugShowCheckedModeBanner: false,
      home: const SplashPage(),
    );
  }
}
