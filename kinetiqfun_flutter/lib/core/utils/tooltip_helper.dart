import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class TooltipHelper {
  static const String _prefsKeyPrefix = 'tooltip_seen_';

  /// Shows a tooltip at a specific X, Y point on the screen.
  static void showAtPoint(BuildContext context, String message, double pointX, double pointY) async {
    final prefs = await SharedPreferences.getInstance();
    final key = _prefsKeyPrefix + message; // Use message as key for simplicity

    if (prefs.getBool(key) == true) {
      return; // Already seen
    }

    final overlay = Overlay.of(context);
    OverlayEntry? entry;

    entry = OverlayEntry(
      builder: (context) {
        return Positioned(
          left: pointX - 100, // Approximate centering
          top: pointY - 60, // Above the point
          child: Material(
            color: Colors.transparent,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                color: const Color(0xCC000000), // Semi-transparent black
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                message,
                style: const TextStyle(
                  color: Colors.white,
                  fontFamily: 'game_font',
                  fontSize: 16,
                ),
              ),
            ),
          ),
        );
      },
    );

    overlay.insert(entry);

    // Mark as seen
    await prefs.setBool(key, true);

    // Remove after 5 seconds
    Future.delayed(const Duration(seconds: 5), () {
      entry?.remove();
    });
  }
}
