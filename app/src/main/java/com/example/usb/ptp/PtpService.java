package com.example.usb.ptp;

import android.content.Context;

public interface PtpService {
    public interface PtpListener {
        void onCameraStarted(Camera camera);
        void onCameraStopped(Camera camera);
        void onNoCameraFound();
        void onError(String message);
        boolean hasExist(String filename);
    }

    void setPtpListener(PtpListener listener);
    void start();
    void stop();

    public static class Singleton {
        private static PtpService singleton;
        public static PtpService getInstance(Context context) {
            if (singleton == null) {
                singleton = new PtpUsbService(context);
            }
            return singleton;
        }
        public static void setInstance(PtpService service) {
            singleton = service;
        }
    }
}
