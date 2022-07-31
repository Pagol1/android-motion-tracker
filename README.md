# android-motion-tracker
TODO:
- ~~Work on the basic layout and back-end for the camera~~
- ~~Integrate mlkit's face detector~~
- ~~Add logic for movement~~
- ~~Create custom graphic to include movement text~~
- Tweak threshold parameters for basic_movement_tracker
- Test OpenCV's sfm module <- Need to compile for android
- Display velocity in place of direction

Current Task Breakdown [To be expanded]:
- Setup a basic camera app
    + Need to process individual frames; Possible solutions: OpenCV's capture frame, MLKit's live camera scenario thing
- Look into the specifics of integrating libraries to be used later
- Look into libraries/apis suitable for the task

Promising Things:
- https://github.com/googlesamples/mlkit/tree/master/android/vision-quickstart
- https://github.com/googlesamples/mlkit/tree/master/android/material-showcase
- https://github.com/android/camera-samples/tree/main/CameraXBasic

Required Things:
- androidx.camera.view.PreviewView | androidx.camera.core.ImageProxy