I developed this project as my senior-year high school diploma project. **It brings together a Java backend, an Android GUI built with Material Design, electrical engineering, Python scripting, and 3D design and printing.**

The system consists of a smartphone mounted in a phone grip stand at the center of a custom-designed 3D-printed casing. 
An Android application on the phone uses the camera to recognize the user, initiate video recording, and continuously track their movement around the room—keeping them centered in the frame.

Tracking is achieved by sending instructions via Bluetooth to an Arduino microcontroller housed inside the 3D-printed casing. 
The Arduino then rotates a servo motor accordingly to follow the user.

The Android app features a simple GUI, with a Java backend handling user input and interacting with the operating system. 
At its core, the system relies on the YOLOv4-tiny algorithm to recognize the user, running within an embedded Python script along with OpenCV used for tracker initialization.

The codebase is admittedly quite messy—as I hadn’t yet learned the fundamentals of code decomposition at the time. 
So yes, prepare yourself for a generous serving of spaghetti code!

Disclamer:
The OpenCV library is not included in the repo to save space. 
For future use, the OpenCV library must be placed directly inside the TrackBot directory.

