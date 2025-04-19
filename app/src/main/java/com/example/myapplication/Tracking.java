package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.TextureView;
import android.widget.Toast;

import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.tracking.TrackerKCF;
import org.opencv.tracking.legacy_TrackerMedianFlow;
import org.opencv.core.Mat;
import org.opencv.android.Utils;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

public class Tracking extends Thread {
    public TrackerKCF tracker;
    public boolean isTracking = false;
    public Rect2d initializedBoundingBox;
    private Handler handler;
    public Tracking trackingObj;
    private BluetoothConnection connectToBluetooth;

    public Tracking(Tracking trackingObj, Handler handler, BluetoothConnection connectToBluetooth) {
        this.trackingObj = trackingObj;
        this.handler = handler;
        this.connectToBluetooth = connectToBluetooth;
    }

    public volatile Mat newFrame = new Mat();
    public volatile Rect firstBoundingBox = new Rect(0, 0, 0, 0);

    @Override
    public void run() {
        firstBoundingBox = new Rect(0, 0, 0, 0);
        boolean success;
        int cycles = 0;
        double centerXBoundingBox, centerYBoundingBox;
        int centerXFrame = MainActivity.mTextureViewWidth / 2, centerYFrame = MainActivity.mtexTureviewHeight / 2;
        int[] directionVector = new int[2];


        while (true) {
            if (newFrame != null) {
                try {
                    // The firstBoundingBox is 2,5x smaller than the real tracking object due to downscaling -> setNewFrameForTracking
                    success = tracker.update(newFrame, firstBoundingBox);
                } catch (Exception e) {
                    break;
                }
                if (success) {
                    centerXBoundingBox = firstBoundingBox.x * 2.5 + ((double) firstBoundingBox.width * 2.5) / 2;
                    centerYBoundingBox = firstBoundingBox.y * 2.5 + ((double) firstBoundingBox.height * 2.5) / 2;
                } else {
                    com.example.myapplication.Utils.toastFromThread("Objekt ztracen", handler);
                    return;
                }

                if (isBoundingBoxInitialized(com.example.myapplication.Utils.rectToRect2d(firstBoundingBox))) {
                    directionVector[0] = (int) (centerXBoundingBox - centerXFrame);
                    directionVector[1] = (int) (centerYBoundingBox - centerYFrame);
                }
                configureAndSendData(directionVector[0], directionVector[1]);

                System.out.println("Direction vectory: " + Integer.toString(directionVector[0]) + " " + Integer.toString(directionVector[1]));

                newFrame = null;
            }

        }
    }

    public static void initializeTracker(Bitmap bitmap, Rect2d boundingBox, Tracking trackingObj) {
        Mat videoFrame = new Mat();
        Mat videoFrameRGB = new Mat();
        Utils.bitmapToMat(bitmap, videoFrame);
        Imgproc.cvtColor(videoFrame, videoFrameRGB, Imgproc.COLOR_RGBA2BGR);
        trackingObj.tracker = TrackerKCF.create();
        trackingObj.tracker.init(videoFrameRGB, com.example.myapplication.Utils.rect2dToRect(boundingBox));


    }

    public boolean isBoundingBoxInitialized(Rect2d boundingBox) {
        if (boundingBox.x == 0 && boundingBox.y == 0 &&
                boundingBox.width == 0 && boundingBox.height == 0) {
            return false;
        }
        return true;
    }

    public void setNewFrameForTracking(Bitmap newBitmap, Tracking trackingObj) {
        Mat mat = new Mat();
        Utils.bitmapToMat(newBitmap, mat);

        // Changes the color space from RGBA to RGB
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR);

        // Downscales the image by a factor of 2,5
        Size newSize = new Size(mat.width() / 2.5, mat.height() / 2.5);
        Imgproc.resize(mat, mat, newSize, Imgproc.INTER_AREA);

        newFrame = mat.clone();
        mat.release();
    }

    public void configureAndSendData(int coordinationX, int coordinationY) {

        // Configure directionX

        int directionBufferX = 50;
        int directionX;

        if (coordinationX >= directionBufferX)
            directionX = 1; // Right
        else if (coordinationX <= -directionBufferX)
            directionX = 2; // Left
        else
            directionX = 0; // Stop

        // Configure directionY

        int directionBufferY = 50;
        int directionY;

        if (coordinationY >= directionBufferY)
            directionY = 1; // Down
        else if (coordinationY <= -directionBufferY)
            directionY = 2; // Up
        else
            directionY = 0; // Stop

        System.out.println("Orientace natáčení X:" + directionX);
        System.out.println("Orientace natáčení Y:" + directionY);

        // Configure speedX
        int distanceFromCenterX = Math.abs(coordinationX);
        int speedX;
//        }

        // Configure speedY
        int distanceFromCenterY = Math.abs(coordinationY);
        int speedY;

        System.out.println("Absolutní hodnota od středu X: " + distanceFromCenterX);
        System.out.println("Absolutní hodnota od středu Y: " + distanceFromCenterY);

        //connectToBluetooth.sendData(directionX, speedX, directionY, speedY);
        String config = directionX + "." + distanceFromCenterX + "." + directionY + "." + distanceFromCenterY + "n";
        connectToBluetooth.sendData(config);
        System.out.println(config);
    }
}


