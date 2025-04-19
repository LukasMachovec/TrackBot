package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {


    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 0;
    public TextureView mTextureView;
    public FloatingActionButton mBluetoothButton;
    public FloatingActionButton mOptionsButton;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            mTextureViewWidth = width;
            mtexTureviewHeight = height;
            setupCamera(mTextureViewWidth, mtexTureviewHeight);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        private final ExecutorService executorService = Executors.newFixedThreadPool(2);

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            if (trackingObj == null) {
                return;
            }
            Bitmap bitmapForTracking = mTextureView.getBitmap();

            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    trackingObj.setNewFrameForTracking(bitmapForTracking, trackingObj);
                }
            });
        }
    };

    private CameraDevice mCameraDevice;
    private final CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            if (isRecording) {
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                startRecord();
                mMediaRecorder.start();
            } else {
                startPreview();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };

    private File mVideoFolder;
    private String mVideoFileName;
    private ArrayAdapter<String> mAdapter;
    private ListView mListView;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;

    private Size mPreviewSize;
    private MediaRecorder mMediaRecorder;

    private Size mVideoSize;

    private int mTotalRotation;

    private CaptureRequest.Builder mCaptureRequestBuilder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }


    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getHeight() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private boolean isRecording = false;

    private boolean checkRecordingConditions() {
        if (!mRecordingAllowed) {
            Utils.toast("Nahrávání zakázáno");
            return false;
        }
        return true;
    }

    private PersonDetection detectPerson;
    private Tracking trackingObj = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Handler handler = new Handler();

        OpenCVLoader.initDebug();

        createVideoFolder();

        mMediaRecorder = new MediaRecorder();

        mTextureView = findViewById(R.id.textureView);
        mBluetoothButton = findViewById(R.id.btnBluetooth);

        BluetoothConnection connectToBluetooth = new BluetoothConnection(MainActivity.this, mBluetoothButton);
        mBluetoothButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread onClickThread = new Thread(new Runnable() {
                    private Context context;

                    @Override
                    public void run() {

                        if (connectToBluetooth.getState() == Thread.State.TERMINATED) {
                            Utils.toast("Arduino je již připojeno");

                        } else if (connectToBluetooth.getState() == Thread.State.RUNNABLE) {
                            Utils.toast("Arduino se páruje");
                        } else {
                            connectToBluetooth.start();
                        }


                        // Waits until bluetooth connection has been established
                        try {
                            connectToBluetooth.join();
                            if (!connectToBluetooth.connectionSuccessful) {
                                return;
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }


                        try {
                            if (trackingObj.isAlive())
                                trackingObj.interrupt();
                            if (detectPerson.isAlive())
                                detectPerson.interrupt();
                        } catch (Exception e) {

                        }

                        trackingObj = new Tracking(trackingObj, handler, connectToBluetooth);
                        Bitmap frame = mTextureView.getBitmap();

                        Utils.toast("Inicializace trackeru");
                        detectPerson = new PersonDetection(MainActivity.this, mTextureView, trackingObj);
                        detectPerson.start();

                        // Waits until python algorithm finds the user
                        try {
                            detectPerson.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        if (!detectPerson.objectFound)
                            return;

                        if (!trackingObj.isTracking) {
                            trackingObj.isTracking = true;
                            trackingObj.start();

                        } else {
                            if (trackingObj.isAlive()) {
                                trackingObj.interrupt();
                            }
                            trackingObj.isTracking = false;
                        }


                    }
                });

                if (onClickThread.isAlive()) {
                    onClickThread.interrupt();
                    detectPerson.interrupt();
                    trackingObj.interrupt();
                }

                boolean recordingAllowed = checkRecordingConditions();
                if (recordingAllowed) {
                    if (isRecording) {
                        isRecording = false;
                        mMediaRecorder.stop();
                        mMediaRecorder.reset();
                        startPreview();
                    } else {
                        checkWriteStoragePermission();
                    }
                }

                onClickThread.start();
            }
        });

        mListView = findViewById(R.id.listView);
        String[] content = {"Rozlišení videa", "Framerate videa", "Nahrávání", "Reset"};
        mAdapter = new ArrayAdapter<>(this, R.layout.options_list_items, content);
        mListView.setAdapter(mAdapter);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);

        mOptionsButton = findViewById(R.id.btnOptions);

        mOptionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // connectToBluetooth.sendData("-5");
                // ODKOMENTOVAT

                if (mListView.getVisibility() == View.VISIBLE) {
                    mListView.startAnimation(fadeOut);
                    mListView.setVisibility(View.GONE);
                } else {
                    mListView.setVisibility(View.VISIBLE);
                    mListView.startAnimation(fadeIn);
                }


            }
        });


        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {


                switch (position) {
                    case 0:
                        view.startAnimation(fadeIn);
                        PopupMenu popupMenuResolution = new PopupMenu(MainActivity.this, view);
                        popupMenuResolution.getMenuInflater().inflate(R.menu.popup_menu_resolution, popupMenuResolution.getMenu());
                        popupMenuResolution.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.HD:
                                        bitRate = 20000000;
                                        Utils.toast("Rozlišení nastaveno na HD");
                                        return true;
                                    case R.id.FHD:
                                        bitRate = 40000000;
                                        Utils.toast("Rozlišení nastaveno na FullHD");
                                        return true;
                                    case R.id.UHD:
                                        bitRate = 80000000;
                                        Utils.toast("Rozlišení nastaveno na 4K");
                                        return true;
                                    default:
                                        return false;
                                }

                            }
                        });

                        popupMenuResolution.show();
                        break;


                    case 1:
                        view.startAnimation(fadeIn);
                        PopupMenu popupMenuFPS = new PopupMenu(MainActivity.this, view);
                        popupMenuFPS.getMenuInflater().inflate(R.menu.popup_menu_fps, popupMenuFPS.getMenu());
                        popupMenuFPS.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.FPS30:
                                        FPS = 30;
                                        Utils.toast("Framerate nastaven na 30 fps");
                                        return true;
                                    case R.id.FPS60:
                                        FPS = 60;
                                        Utils.toast("Framerate nastaven na 60 fps");
                                        return true;
                                    default:
                                        return false;
                                }
                            }
                        });
                        popupMenuFPS.show();
                        break;
                    case 2:
                        view.startAnimation(fadeIn);
                        PopupMenu popupMenuRecording = new PopupMenu(MainActivity.this, view);
                        popupMenuRecording.getMenuInflater().inflate(R.menu.popup_menu_recording, popupMenuRecording.getMenu());
                        popupMenuRecording.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                            @Override
                            public boolean onMenuItemClick(MenuItem menuItem) {
                                switch (menuItem.getItemId()) {
                                    case R.id.recordingOn:
                                        //code specific to first list item
                                        Utils.toast("Nahrávání zapnuto");
                                        mRecordingAllowed = true;
                                        return true;
                                    case R.id.recordingOff:
                                        mRecordingAllowed = false;
                                        Utils.toast("Nahrávání vypnuto");
                                        return true;
                                    default:
                                        return false;
                                }
                            }
                        });
                        popupMenuRecording.show();
                        break;
                    case 3:
                        view.startAnimation(fadeIn);
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Resetovat aplikaci?");
                        builder.setMessage("Opravdu chceš resetovat aplikaci do původního nastavení?");
                        builder.setPositiveButton("Ano", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Pressed Yes
                                FPS = 30;
                                bitRate = 60;
                                connectToBluetooth.finalBtSocket = null;
                            }
                        });
                        builder.setNegativeButton("Ne", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Pressed No
                            }
                        });
                        builder.show();
                }
            }
        });
    }

    public static int mTextureViewWidth;
    public static int mtexTureviewHeight;

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Aplikace potřebuje oprávnění pro použití kamery",
                        Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                isRecording = true;
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Toast.makeText(this,
                    "Oprávnění uděleno", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                    "App needs to save video to run", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                // Todo: umožnit uživateli nastavit buď přední nebo zadní kameru + přidat v menu
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), 1920, 1080);
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedWidth, rotatedHeight);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "Aplikace potřebuje oprávnění pro použití kamery",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void startRecord() {
        try {
            setupMediaRecorder();
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);


            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            try {
                                session.setRepeatingRequest(
                                        mCaptureRequestBuilder.build(), null, null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Utils.toast("Konfigurace kamery selhala");
                        }
                    }, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(List.of(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                null, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getApplicationContext(),
                            "Nelze inicializovat kameru", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Trackbot");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    private void createVideoFolder() {
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder = new File(movieFile, "TrackBotVideos");
        if (!mVideoFolder.exists()) {
            mVideoFolder.mkdir();
        }
    }

    private File createVideoFileName() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyddMM_HHmmss").format(new Date());
        String prepend = "VIDEO_" + timestamp + "_";
        File videoFile = File.createTempFile(prepend, ".mp4", mVideoFolder);
        mVideoFileName = videoFile.getAbsolutePath();
        return videoFile;
    }

    private boolean mRecordingAllowed = true;

    private void checkWriteStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                isRecording = true;
                try {
                    createVideoFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                mMediaRecorder.start();

            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Bez povolení nelze ukládat videa", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        } else {
            isRecording = true;
            try {
                createVideoFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
            startRecord();
            mMediaRecorder.start();
        }
    }

    private int FPS = 30;
    private int bitRate = 40000000;

    private void setupMediaRecorder() throws IOException {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(bitRate);
        mMediaRecorder.setVideoFrameRate(FPS);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }


}


