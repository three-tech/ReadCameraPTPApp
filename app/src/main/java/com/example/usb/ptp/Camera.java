package com.example.usb.ptp;

import android.graphics.Bitmap;

import com.example.usb.ptp.model.ObjectInfo;

import java.io.File;
import java.util.ArrayList;

public interface Camera {
    public interface CameraListener {
        void onCameraStarted(Camera camera);
        void onCameraStopped(Camera camera);
        void onNoCameraFound();
        void onError(String message);
        void onCapturedPictureReceived(int objectHandle, ObjectInfo objectInfo, Bitmap bitmap);
        void onObjectAdded(int handle);
        void onPropertyChanged(final int p0, final int p1);
    }

    // callbacks aren't on UI thread
    public interface StorageInfoListener {
        void onAllStorageFound(Integer storageIds);
        void onImageHandlesRetrieved(int[] handles);
    }

    public interface RetrieveImageInfoListener {
        void onImageInfoRetrieved(int objectHandle, ObjectInfo objectInfo, Bitmap thumbnail);
    }

    public interface RetrieveImageListener {
        void onImageRetrieved(int objectHandle, Bitmap image);
    }

    public interface CopyRightInfo{
        void onCopyRightInfo();
    };

    String getDeviceName();

    boolean isSessionOpen();

    String getDeviceInfo();

    void retrievePicture(int objectHandle);

    void retrieveStorages(StorageInfoListener listener);

    void retrieveImageHandles(StorageInfoListener listener, int storageId, int objectFormat);

    void deleteImageHandles(int objectHandle, int objectFormat);

    void retrieveImageInfo(RetrieveImageInfoListener listener, int objectHandle);

    void retrieveImage(RetrieveImageListener listener, int objectHandle);
}
