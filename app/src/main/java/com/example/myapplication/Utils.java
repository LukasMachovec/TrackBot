package com.example.myapplication;

import android.os.Handler;
import android.widget.Toast;

import org.opencv.core.Rect;
import org.opencv.core.Rect2d;

public class Utils {

    public static void toast(String message){
        try {
            Toast.makeText(MyApplication.getInstance(), message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {

        }
    }

    public static void toastFromThread(String message, Handler handler){
                    handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MyApplication.getInstance(), message, Toast.LENGTH_SHORT).show();
                    return;
                }
            });
    }
    public static Rect rect2dToRect(Rect2d rect2d){
        Rect rect = new Rect((int) rect2d.x, (int) rect2d.y,
                (int) rect2d.width, (int) rect2d.height);
        return rect;
    }

    public static Rect2d rectToRect2d(Rect rect){
        Rect2d rect2d = new Rect2d((double)rect.x,(double)rect.y,
                (double)rect.width,(double)rect.height);
        return rect2d;
    }
}
