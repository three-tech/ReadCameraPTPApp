package com.example.usb.ptp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.Handler;
import android.provider.SyncStateContract;
import android.util.Log;
import android.widget.Toast;

import com.example.usb.ptp.model.ObjectInfo;
import com.example.usb.util.Globe;
import com.example.usb.util.ImgUtils;
import com.example.usb.util.ToastUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class PtpUsbService implements PtpService,
        Camera.CameraListener,
        Camera.StorageInfoListener {
    private final String TAG = "PtpUsbService";
    private static final String ACTION_USB_PERMISSION = "com.jsbn.jbox.USB_PERMISSION";

    private final UsbManager usbManager;
    private final Context mContext;
    private PtpCamera mCamera;
    private PtpListener mListener;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    connect();
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    // 关闭相机设备
                    stopCamera();
                } else if (ACTION_USB_PERMISSION.equals(action)) {
                    connect();
                }
            }
        }
    };

    public PtpUsbService(Context context) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.mContext = context;
    }

    @Override
    public void start() {
        // 注册监听
        registerUsbReceiver();
        connect();
    }

    @Override
    public void stop() {
        // 取消事件监听
        unregisterUsbReceiver();
        stopCamera();
    }

    private void stopCamera() {
        // 关闭相机
        if (mCamera != null) {
            mCamera.shutdown();
        }
        if (Globe.storageIdBeans != null) {
            if (Globe.storageIdBeans.size() > 0) {
                Globe.storageIdBeans.clear();
            }
        }
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        filter.setPriority(1000);
        this.mContext.registerReceiver(usbReceiver, filter);
    }

    private void unregisterUsbReceiver() {
        this.mContext.unregisterReceiver(usbReceiver);
    }

    private boolean connect() {
        // 关闭相机设备
        stopCamera();
        UsbDevice device = null;
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> e : deviceList.entrySet()) {
            UsbDevice d = e.getValue();
            if (d.getVendorId() == PtpConstants.CanonVendorId
                    || d.getVendorId() == PtpConstants.NikonVendorId
                    || d.getVendorId() == PtpConstants.SonyVendorId) {
                device = d;
                break;
            }
        }
        if (device == null) {
            this.mListener.onNoCameraFound();
            return false;
        }

        // 判断是否有权限，没有则请求授权
        if (!usbManager.hasPermission(device)) {
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(
                    this.mContext,
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    0);
            usbManager.requestPermission(device, mPermissionIntent);
            this.mListener.onError("相机未授权");
            return false;
        }

        for (int i = 0, n = device.getInterfaceCount(); i < n; ++i) {
            UsbInterface anInterface = device.getInterface(i);
            if (anInterface.getEndpointCount() != 3) {
                continue;
            }

            UsbEndpoint in = null;
            UsbEndpoint out = null;
            for (int e = 0, en = anInterface.getEndpointCount(); e < en; ++e) {
                UsbEndpoint endpoint = anInterface.getEndpoint(e);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        in = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        out = endpoint;
                    }
                }
            }
            if (in == null || out == null) {
                continue;
            }

            if (device.getVendorId() == PtpConstants.CanonVendorId) {
                PtpUsbConnection connection = new PtpUsbConnection(
                        usbManager.openDevice(device),
                        in,
                        out,
                        device.getVendorId(),
                        device.getProductId(),
                        device
                );
                mCamera = new EosCamera(connection, this);
            } else if (device.getVendorId() == PtpConstants.NikonVendorId) {
                PtpUsbConnection connection = new PtpUsbConnection(
                        usbManager.openDevice(device),
                        in,
                        out,
                        device.getVendorId(),
                        device.getProductId(),
                        device
                );
                mCamera = new NikonCamera(connection, this);
            } else {
                // 索尼只是做测试，未投入使用
                if (device.getVendorId() == PtpConstants.SonyVendorId) {
                    PtpUsbConnection connection = new PtpUsbConnection(
                            usbManager.openDevice(device),
                            in,
                            out,
                            device.getVendorId(),
                            device.getProductId(),
                            device
                    );
                    mCamera = new PtpCamera(connection, this) {
                        @Override
                        protected void onOperationCodesReceived(Set<Integer> operations) {
                        }

                        @Override
                        protected void queueEventCheck() {
                        }
                    };
                }
                Log.e(TAG, "不支持的设备id" + device.getVendorId());
            }
            return true;
        }

        this.mListener.onNoCameraFound();
        return false;
    }

    @Override
    public void setPtpListener(PtpListener listener) {
        this.mListener = listener;
    }

    @Override
    public void onCameraStarted(Camera camera) {
        this.mListener.onCameraStarted(camera);
        // 读取存储卡上的照片
        camera.retrieveStorages(this);
    }

    @Override
    public void onCameraStopped(Camera camera) {
        this.mListener.onCameraStopped(camera);
    }

    @Override
    public void onNoCameraFound() {
        this.mListener.onNoCameraFound();
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "onError: " + message);
        this.mListener.onError(message);
    }

    @Override
    public void onCapturedPictureReceived(int objectHandle,
                                          ObjectInfo objectInfo,
                                          final Bitmap bitmap) {
        String path = "";
        // 组装文件名,然后剔重
        final String repFilename = objectInfo.filename.substring(0, objectInfo.filename.lastIndexOf('.')) + ".JPG";

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                ToastUtil.showShortToast(PtpUsbService.this.mContext, "读取到:" + repFilename);
            }
        });
        try {
            //7O6A8489.JPG
            final File files = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Canon/");
            if (!files.exists()) {
                files.mkdirs();
            }
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Canon/" + repFilename);
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    ToastUtil.showShortToast(PtpUsbService.this.mContext, "保存成功:" + files.getAbsolutePath());
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            bitmap.recycle();
            Log.e(TAG, "读取异常:" + e.toString());
        }
    }

    @Override
    public void onObjectAdded(int handle) {
        // 处理文件
        mCamera.retrievePicture(handle);
    }

    @Override
    public void onPropertyChanged(int p0, int p1) {

    }

    /**
     * 传指定内存卡ID读照片
     *
     * @param storageId
     */
    @Override
    public void onAllStorageFound(Integer storageId) {
        //读照片
        mCamera.retrieveImageHandles(
                PtpUsbService.this,
                storageId,
                PtpConstants.ObjectFormat.EXIF_JPEG
        );
    }

    @Override
    public void onImageHandlesRetrieved(int[] handles) {
        for (int i = 0; i < handles.length; ++i) {
            mCamera.retrievePicture(handles[i]);
        }
    }
}