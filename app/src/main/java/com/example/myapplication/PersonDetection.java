package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.view.TextureView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;

public class PersonDetection extends Thread {
    private final Context context;
    private final TextureView textureView;
    private final Tracking trackingObj;
    public boolean objectFound;

    public PersonDetection(Context context, TextureView textureView, Tracking trackingObj) {
        this.textureView = textureView;
        this.context = context;
        this.trackingObj = trackingObj;
    }

    String imageString = "";

    public String stringCoordinates;

    @Override
    public void run() {
        Bitmap bitmap = textureView.getBitmap();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        imageString = getStringImage(bitmap);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(MyApplication.getInstance()));
        }

        Python py = Python.getInstance();

        PyObject pyobj = py.getModule("myscript");
        PyObject obj = pyobj.callAttr("main", imageString);

        try {
            // returns string with coordinates in format _123_456_789_012_
            stringCoordinates = obj.toString();
        } catch (Exception e) {
            objectFound = false;
            Thread.currentThread().run();
        }
        objectFound = true;
        String[] coordinates = stringCoordinates.split(" ");

        // Separates the string values and saves them in valuesForRect
        int[] valuesForRect = new int[4];
        int pos = 0;
        for (String coordinate : coordinates) {
            try {
                valuesForRect[pos] = Integer.parseInt(coordinate);
                pos++;
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        int x = valuesForRect[0];
        int y = valuesForRect[1];
        int w = valuesForRect[2];
        int h = valuesForRect[3];

        // Downscaling
        trackingObj.initializedBoundingBox = new Rect2d(x / 2.5, y / 2.5, w / 2.5, h / 2.5);
        System.out.println(trackingObj.initializedBoundingBox);
        //Bitmap scaledFrame = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * 5), (int) (bitmap.getHeight() * 5), false);
        Tracking.initializeTracker(bitmap, trackingObj.initializedBoundingBox, trackingObj);


        ((Activity) context).runOnUiThread(() ->
                Toast.makeText(context, "Tracker inicializován", Toast.LENGTH_SHORT).show());


    }

    private String getStringImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

}