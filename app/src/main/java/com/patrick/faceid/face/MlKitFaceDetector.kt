package com.patrick.faceid.face

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Face detector backed by Google ML Kit (bundled model, runs on-device).
 *
 * Landmarks are returned in the order the aligner expects, left-to-right AS SEEN IN THE IMAGE:
 * eye, eye, nose, mouth corner, mouth corner. ML Kit labels eyes/mouth by the subject's own
 * left/right, so each pair is ordered by x rather than trusting the labels. That stays correct
 * for mirrored (front-camera) images too.
 *
 * Differences from the SCRFD convention the embedder was trained on (see report D2 revised):
 * the nose point is ML Kit's NOSE_BASE rather than the nose tip, and there is no confidence score.
 *
 * [detect] blocks until ML Kit finishes. Never call it on the main thread.
 */
class MlKitFaceDetector(minFaceSize: Float = 0.05f) : FaceDetector {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(minFaceSize)
            .build()
    )

    override fun detect(image: RgbImage): List<DetectedFace> {
        val bitmap = Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        return faces.map { face ->
            fun point(type: Int): Pair<Float, Float>? =
                face.getLandmark(type)?.position?.let { it.x to it.y }

            val eyes = listOfNotNull(point(FaceLandmark.LEFT_EYE), point(FaceLandmark.RIGHT_EYE))
                .sortedBy { it.first }
            val mouth = listOfNotNull(point(FaceLandmark.MOUTH_LEFT), point(FaceLandmark.MOUTH_RIGHT))
                .sortedBy { it.first }
            val nose = point(FaceLandmark.NOSE_BASE)

            val landmarks = if (eyes.size == 2 && mouth.size == 2 && nose != null) {
                listOf(eyes[0], eyes[1], nose, mouth[0], mouth[1])
                    .flatMap { listOf(it.first, it.second) }.toFloatArray()
            } else {
                FloatArray(10) { Float.NaN }
            }

            val b = face.boundingBox
            DetectedFace(
                box = Box(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
                landmarks = landmarks,
                score = Float.NaN,
                yaw = face.headEulerAngleY,
                pitch = face.headEulerAngleX,
                roll = face.headEulerAngleZ,
            )
        }.sortedByDescending { it.box.area }
    }

    override fun close() = detector.close()
}
