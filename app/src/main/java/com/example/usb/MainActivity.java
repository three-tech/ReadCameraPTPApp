package com.example.usb;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.example.atphoto.R;
import com.example.usb.ptp.Camera;
import com.example.usb.ptp.PtpService;
import com.example.usb.ptp.model.ObjectInfo;
import com.example.usb.util.ToastUtil;

public class MainActivity extends Activity implements Camera.CameraListener, PtpService.PtpListener {

    //日志
    private final String TAG = MainActivity.class.getSimpleName();
    private PtpService ptp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ptp = PtpService.Singleton.getInstance(getApplicationContext());
        ptp.setPtpListener(this);
        ptp.start();
    }

    @Override
    public void onCameraStarted(final Camera camera) {
        Log.e(TAG, "相机已打开会话:" + camera.isSessionOpen());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtil.showShortToast(MainActivity.this,"相机已连接");
            }
        });
    }

    @Override
    public void onCameraStopped(Camera camera) {
        ToastUtil.showShortToast(this, "停止连接相机");
    }

    @Override
    public void onNoCameraFound() {
        Log.e(TAG, "未发现相机onNoCameraFound...");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtil.showShortToast(MainActivity.this, "未发现相机");
            }
        });
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "onError...");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ToastUtil.showShortToast(MainActivity.this, "onError...");
            }
        });
    }

    @Override
    public void onCapturedPictureReceived(int objectHandle, ObjectInfo objectInfo, Bitmap bitmap) {

    }

    @Override
    public boolean hasExist(String filename) {
        return false;
    }

    @Override
    public void onObjectAdded(int handle) {

    }

    @Override
    public void onPropertyChanged(int p0, int p1) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        ptp.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}