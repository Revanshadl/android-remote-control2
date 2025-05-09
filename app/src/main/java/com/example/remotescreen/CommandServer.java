package com.example.remotescreen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class CommandServer extends AccessibilityService {
    private static final String TAG = "CommandServer";
    private static final int PORT = 5002;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                Log.i(TAG, "Command server started on port " + PORT);
                while (true) {
                    Socket client = serverSocket.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String command = reader.readLine();
                    if (command != null && command.startsWith("TAP")) {
                        String[] parts = command.split(" ");
                        if (parts.length == 3) {
                            float x = Float.parseFloat(parts[1]);
                            float y = Float.parseFloat(parts[2]);
                            performTap(x, y);
                        }
                    }
                    client.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in command server", e);
            }
        }).start();
    }

    private void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 100);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        // Not used
    }
}
