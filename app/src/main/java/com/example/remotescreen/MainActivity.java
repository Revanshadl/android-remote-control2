package com.example.remotescreen;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 1000;
    private static final String TAG = "RemoteScreen";
    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private int mWidth;
    private int mHeight;
    private int mDensity;
    private Socket mSocket;
    private OutputStream mOutputStream;
    private boolean mRunning = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize MediaProjectionManager
        mProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "Permission denied");
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        setupVirtualDisplay();
        startStreaming();
    }

    private void setupVirtualDisplay() {
        WindowManager window = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        window.getDefaultDisplay().getMetrics(metrics);
        mDensity = metrics.densityDpi;
        mWidth = metrics.widthPixels;
        mHeight = metrics.heightPixels;

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        Surface surface = mImageReader.getSurface();
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("RemoteScreen",
                mWidth, mHeight, mDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null);
    }

    private void startStreaming() {
        new Thread(() -> {
            try {
                mSocket = new Socket("192.168.162.177", 5001); // Replace with your server IP
                mOutputStream = mSocket.getOutputStream();
                while (mRunning) {
                    Image image = mImageReader.acquireLatestImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        if (planes.length > 0) {
                            ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();
                            int rowPadding = rowStride - pixelStride * mWidth;
                            Bitmap bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride,
                                    mHeight, Bitmap.Config.ARGB_8888);
                            bitmap.copyPixelsFromBuffer(buffer);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                            byte[] bytes = baos.toByteArray();
                            int size = bytes.length;
                            mOutputStream.write(intToByteArray(size));
                            mOutputStream.write(bytes);
                            mOutputStream.flush();
                            bitmap.recycle();
                        }
                        image.close();
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                Log.e(TAG, "Streaming error", e);
            }
        }).start();
    }

    private byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRunning = false;
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        if (mMediaProjection != null) mMediaProjection.stop();
        try {
            if (mOutputStream != null) mOutputStream.close();
            if (mSocket != null) mSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing resources", e);
        }
    }
}
