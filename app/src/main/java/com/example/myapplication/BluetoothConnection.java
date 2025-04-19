package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothConnection extends Thread {

    private final Context context;
    private final FloatingActionButton bluetoothButton;

    public BluetoothConnection(Context context, FloatingActionButton bluetoothButton) {
        this.bluetoothButton = bluetoothButton;
        this.context = context;
    }

    static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public boolean connectionSuccessful = false;
    public BluetoothSocket finalBtSocket;
    public boolean hasAlreadyBeenConnected = false;

    @Override
    public void run() {

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice hc05 = btAdapter.getRemoteDevice("00:22:01:00:12:14");

        try {


            finalBtSocket = hc05.createInsecureRfcommSocketToServiceRecord(mUUID);
            finalBtSocket.connect();
            connectionSuccessful = true;
            hasAlreadyBeenConnected = true;

            ((Activity) context).runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(context, "Arduino úspěšně připojeno", Toast.LENGTH_SHORT).show();
                }
            });


        } catch (IOException e) {
            connectionSuccessful = false;
            ((Activity) context).runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(context, "Arduino nelze připojit", Toast.LENGTH_SHORT).show();
                }
            });
            Thread.currentThread().interrupt();
        }

    }

    public void sendData(String data) {
        try {
            OutputStream outputStream = finalBtSocket.getOutputStream();

            for (int i = 0; i < data.length(); i++) {
                char c = data.charAt(i);
                outputStream.write(c);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}