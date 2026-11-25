# Object Counter (Android, Kotlin)

Detects objects with the phone camera (live) or from a gallery photo, draws a box
around each one and counts them — a total plus a count per object type
(e.g. `person: 3   cup: 2   bottle: 5`).

Everything runs **on the phone, offline**, using MediaPipe Object Detector with the
pretrained EfficientDet-Lite0 model (80 COCO classes: person, car, bottle, cup,
apple, banana, chair, dog, cat, phone, book, ...).

## Run it

1. Install **Android Studio** (Koala or newer) and open this folder.
2. Let Gradle sync. The first build **downloads the model automatically** to
   `app/src/main/assets/efficientdet_lite0.tflite` (needs internet once).
   If it fails, download it manually from
   https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite
   and put it in that folder.
3. Plug in an Android phone (USB debugging on) or start an emulator, press **Run**.

Requires Android 9+ (API 28).

## Using the app

- **Live camera**: point at objects; boxes and counts update in real time.
- **Gallery**: pick a photo to count objects in it.
- **Confidence slider**: raise it to remove uncertain detections, lower it to
  catch more objects. Counts update instantly.

## Notes / limits

- Only the 80 COCO classes are recognised. To count something special
  (screws, pills, eggs on a tray...) train a custom model with MediaPipe Model
  Maker and replace `efficientdet_lite0.tflite` (same code works).
- Heavily overlapping or very small objects can be missed; get closer or use a
  higher-resolution photo.

## Code map

- `MainActivity.kt` – camera (CameraX), gallery picker, counting + UI
- `ObjectDetectorHelper.kt` – model loading and detection
- `OverlayView.kt` – draws the boxes and labels
