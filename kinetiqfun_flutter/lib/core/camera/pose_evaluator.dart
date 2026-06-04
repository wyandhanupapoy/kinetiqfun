import 'dart:math';
import 'package:google_mlkit_pose_detection/google_mlkit_pose_detection.dart';

class PoseEvaluator {
  static bool isLikelyHuman(Pose pose) {
    int validLandmarks = 0;
    final requiredLandmarks = [
      PoseLandmarkType.leftShoulder,
      PoseLandmarkType.rightShoulder,
      PoseLandmarkType.leftHip,
      PoseLandmarkType.rightHip,
    ];

    for (var type in requiredLandmarks) {
      final landmark = pose.landmarks[type];
      if (landmark != null && landmark.likelihood > 0.6) {
        validLandmarks++;
      }
    }
    return validLandmarks >= 3;
  }

  static double getAngle(PoseLandmark? first, PoseLandmark? middle, PoseLandmark? last) {
    if (first == null || middle == null || last == null) return 0.0;
    
    double angle = atan2(last.y - middle.y, last.x - middle.x) -
        atan2(first.y - middle.y, first.x - middle.x);
    
    angle = angle * (180.0 / pi);
    if (angle < 0) angle += 360.0;
    if (angle > 180.0) angle = 360.0 - angle;
    return angle;
  }

  static List<double> extractFeatures(Pose pose) {
    final marks = pose.landmarks;
    return [
      getAngle(marks[PoseLandmarkType.leftShoulder], marks[PoseLandmarkType.leftElbow], marks[PoseLandmarkType.leftWrist]),
      getAngle(marks[PoseLandmarkType.rightShoulder], marks[PoseLandmarkType.rightElbow], marks[PoseLandmarkType.rightWrist]),
      getAngle(marks[PoseLandmarkType.leftHip], marks[PoseLandmarkType.leftKnee], marks[PoseLandmarkType.leftAnkle]),
      getAngle(marks[PoseLandmarkType.rightHip], marks[PoseLandmarkType.rightKnee], marks[PoseLandmarkType.rightAnkle]),
      getAngle(marks[PoseLandmarkType.leftElbow], marks[PoseLandmarkType.leftShoulder], marks[PoseLandmarkType.leftHip]),
      getAngle(marks[PoseLandmarkType.rightElbow], marks[PoseLandmarkType.rightShoulder], marks[PoseLandmarkType.rightHip]),
      getAngle(marks[PoseLandmarkType.leftKnee], marks[PoseLandmarkType.leftHip], marks[PoseLandmarkType.leftShoulder]),
      getAngle(marks[PoseLandmarkType.rightKnee], marks[PoseLandmarkType.rightHip], marks[PoseLandmarkType.rightShoulder]),
    ];
  }

  static double compareFeatures(List<double> features1, List<double> features2) {
    if (features1.length != features2.length) return 0.0;
    double score = 0.0;
    for (int i = 0; i < features1.length; i++) {
      double diff = (features1[i] - features2[i]).abs();
      // Scoring: 100 max per angle. If diff > 90, score is 0.
      double s = (90.0 - diff) / 90.0 * 100.0;
      if (s < 0) s = 0;
      score += s;
    }
    return score / features1.length;
  }
}
