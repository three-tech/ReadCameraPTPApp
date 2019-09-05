package com.example.usb.ptp;

import android.graphics.Bitmap;
import android.hardware.usb.UsbRequest;
import android.mtp.MtpDevice;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.util.Log;

import com.example.usb.ptp.commands.CloseSessionCommand;
import com.example.usb.ptp.commands.Command;
import com.example.usb.ptp.commands.DeleteObjectCommand;
import com.example.usb.ptp.commands.GetDeviceInfoCommand;
import com.example.usb.ptp.commands.GetObjectHandlesCommand;
import com.example.usb.ptp.commands.GetStorageInfosAction;
import com.example.usb.ptp.commands.OpenSessionCommand;
import com.example.usb.ptp.commands.RetrieveImageAction;
import com.example.usb.ptp.commands.RetrieveImageInfoAction;
import com.example.usb.ptp.commands.RetrievePictureAction;
import com.example.usb.ptp.commands.SetDevicePropValueCommand;
import com.example.usb.ptp.model.DeviceInfo;
import com.example.usb.ptp.model.ObjectInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class PtpCamera implements Camera {


    private MtpDevice mtpDevice;
    private static volatile CancellationSignal cancellationSignal;
    protected Map<Integer, Integer> properties;
    protected Map<Integer, Integer> ptpProperties;
    protected Map<Integer, Integer> ptpToVirtualProperty;

    public interface IO {
        void handleCommand(Command command);
    }

    enum State {
        // initial state
        Starting,
        // open session
        Active,
        // someone has asked to close session
        Stoping,
        // thread has stopped
        Stopped,
        // error happened
        Error
    }

    private class WorkerThread extends Thread implements IO {
        public boolean stop;

        private int maxPacketOutSize;
        private int maxPacketInSize;
        private long lastEventCheck;
        private UsbRequest r1;
        private UsbRequest r2;
        private UsbRequest r3;
        private final int bigInSize = 0x4000;
        // buffers for async data io, size bigInSize
        private ByteBuffer bigIn1;
        private ByteBuffer bigIn2;
        private ByteBuffer bigIn3;
        // buffer for small packets like command and response
        private ByteBuffer smallIn;
        // buffer containing full data out packet for processing
        private int fullInSize = 0x4000;
        private ByteBuffer fullIn;

        @Override
        public void run() {
            notifyWorkStarted();
            maxPacketOutSize = connection.getMaxPacketOutSize();
            maxPacketInSize = connection.getMaxPacketInSize();

            if (maxPacketOutSize <= 0 || maxPacketOutSize > 0xffff) {
                onUsbError(String.format("Usb initialization error: out size invalid %d", maxPacketOutSize));
                return;
            }

            if (maxPacketInSize <= 0 || maxPacketInSize > 0xffff) {
                onUsbError(String.format("usb initialization error: in size invalid %d", maxPacketInSize));
                return;
            }

            smallIn = ByteBuffer.allocate(Math.max(maxPacketInSize, maxPacketOutSize));
            smallIn.order(ByteOrder.LITTLE_ENDIAN);

            bigIn1 = ByteBuffer.allocate(bigInSize);
            bigIn1.order(ByteOrder.LITTLE_ENDIAN);
            bigIn2 = ByteBuffer.allocate(bigInSize);
            bigIn2.order(ByteOrder.LITTLE_ENDIAN);
            bigIn3 = ByteBuffer.allocate(bigInSize);
            bigIn3.order(ByteOrder.LITTLE_ENDIAN);

            fullIn = ByteBuffer.allocate(fullInSize);
            fullIn.order(ByteOrder.LITTLE_ENDIAN);

            r1 = connection.createInRequest();
            r2 = connection.createInRequest();
            r3 = connection.createInRequest();

            while (true) {
                synchronized (this) {
                    if (stop) {
                        break;
                    }
                }

                if (lastEventCheck + AppConfig.EVENTCHECK_PERIOD < System.currentTimeMillis()) {
                    lastEventCheck = System.currentTimeMillis();
                    PtpCamera.this.queueEventCheck();
                }

                PtpAction action = null;
                try {
                    action = queue.poll(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // nop
                }

                if (action != null) {
                    action.exec(this);
                }
            }
            r3.close();
            r2.close();
            r1.close();

            notifyWorkEnded();
        }

        @Override
        public void handleCommand(Command command) {
            ByteBuffer b = smallIn;
            b.position(0);
            command.encodeCommand(b);
            int outLen = b.position();
            int res = connection.bulkTransferOut(b.array(), outLen, AppConfig.USB_TRANSFER_TIMEOUT);
            if (res < outLen) {
                onUsbError(String.format("Code CP %d %d", res, outLen));
                return;
            }

            if (command.hasDataToSend()) {
                b = ByteBuffer.allocate(connection.getMaxPacketOutSize());
                b.order(ByteOrder.LITTLE_ENDIAN);
                command.encodeData(b);
                outLen = b.position();
                res = connection.bulkTransferOut(b.array(), outLen, AppConfig.USB_TRANSFER_TIMEOUT);
                if (res < outLen) {
                    onUsbError(String.format("Code DP %d %d", res, outLen));
                    return;
                }
            }

            while (!command.hasResponseReceived()) {
                int maxPacketSize = maxPacketInSize;
                ByteBuffer in = smallIn;
                in.position(0);

                res = 0;
                while (res == 0) {
                    res = connection.bulkTransferIn(in.array(), maxPacketSize, AppConfig.USB_TRANSFER_TIMEOUT);
                }
                if (res < 12) {
                    onUsbError(String.format("Couldn't read header, only %d bytes available!", res));
                    return;
                }

                int read = res;
                int length = in.getInt();
                ByteBuffer infull = null;

                if (read < length) {
                    if (length > fullInSize) {
                        fullInSize = (int) (length * 1.5);
                        fullIn = ByteBuffer.allocate(fullInSize);
                        fullIn.order(ByteOrder.LITTLE_ENDIAN);
                    }
                    infull = fullIn;
                    infull.position(0);
                    infull.put(in.array(), 0, read);
                    maxPacketSize = bigInSize;

                    int nextSize = Math.min(maxPacketSize, length - read);
                    int nextSize2 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize));
                    int nextSize3 = 0;

                    r1.queue(bigIn1, nextSize);

                    if (nextSize2 > 0) {
                        r2.queue(bigIn2, nextSize2);
                    }

                    while (read < length) {
                        nextSize3 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize - nextSize2));
                        if (nextSize3 > 0) {
                            bigIn3.position(0);
                            r3.queue(bigIn3, nextSize3);
                        }

                        if (nextSize > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn1.array(), 0, infull.array(), read, nextSize);
                            read += nextSize;
                        }
                        nextSize = Math.max(0, Math.min(maxPacketSize, length - read - nextSize2 - nextSize3));
                        if (nextSize > 0) {
                            bigIn1.position(0);
                            r1.queue(bigIn1, nextSize);
                        }

                        if (nextSize2 > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn2.array(), 0, infull.array(), read, nextSize2);
                            read += nextSize2;
                        }
                        nextSize2 = Math.max(0, Math.min(maxPacketSize, length - read - nextSize - nextSize3));
                        if (nextSize2 > 0) {
                            bigIn2.position(0);
                            r2.queue(bigIn2, nextSize2);
                        }

                        if (nextSize3 > 0) {
                            connection.requestWait();
                            System.arraycopy(bigIn3.array(), 0, infull.array(), read, nextSize3);
                            read += nextSize3;
                        }
                    }
                } else {
                    infull = in;
                }

                infull.position(0);
                try {
                    command.receivedRead(infull);
                    infull = null;
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception " + e.getLocalizedMessage());
                    e.printStackTrace();
                    onPtpError(String.format("Error parsing %s with length %d", command.getClass().getSimpleName(),
                            length));
                }
            }
        }

        private void notifyWorkStarted() {
        }

        private void notifyWorkEnded() {
        }
    }

    private static final String TAG = "PtpCamera";
    private final WorkerThread workerThread = new WorkerThread();
    private final PtpUsbConnection connection;
    protected final Handler handler = new Handler();
    protected final LinkedBlockingQueue<PtpAction> queue = new LinkedBlockingQueue<PtpAction>();
    protected CameraListener listener;
    protected State state;
    private int transactionId;
    protected DeviceInfo deviceInfo;

    private final int vendorId;
    protected final int productId;

    // 压缩比
    private int inSampleSize = 1;

    public int getInSampleSize() {
        return inSampleSize;
    }

    public void setInSampleSize(int inSampleSize) {
        if (inSampleSize == this.inSampleSize) {
            return;
        }

        synchronized (this) {
            this.inSampleSize = inSampleSize;
        }
    }


    public PtpCamera(PtpUsbConnection connection, CameraListener listener) {
        this.connection = connection;
        this.listener = listener;
        state = State.Starting;
        vendorId = connection.getVendorId();
        productId = connection.getProductId();
        properties = new HashMap<>();
        ptpProperties = new HashMap<>();
        ptpToVirtualProperty = new HashMap<>();
        queue.add(new GetDeviceInfoCommand(this));
        openSession();
        workerThread.start();
    }

    public void shutdown() {
        state = State.Stoping;
        workerThread.lastEventCheck = System.currentTimeMillis() + 1000000L;
        queue.clear();
        closeSession();
    }

    public void shutdownHard() {
        state = State.Stopped;
        synchronized (workerThread) {
            workerThread.stop = true;
        }
        if (connection != null) {
            connection.close();
            //TODO possible NPE, need to join workerThread
            //connection = null;
        }
    }

    public State getState() {
        return state;
    }

    public int nextTransactionId() {
        return transactionId++;
    }

    public int currentTransactionId() {
        return transactionId;
    }

    public void resetTransactionId() {
        transactionId = 0;
    }

    public int getProductId() {
        return productId;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void enqueue(final Command cmd, int delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (state == State.Active) {
                    queue.add(cmd);
                }
            }
        }, delay);
    }

    public void setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        Set<Integer> operations = new HashSet<Integer>();
        for (int i = 0; i < deviceInfo.operationsSupported.length; ++i) {
            operations.add(deviceInfo.operationsSupported[i]);
        }
        onOperationCodesReceived(operations);
    }

    /**
     * Deriving classes should override this method to get the set of supported
     * operations of the camera. Based on this information functionality has to
     * be enabled/disabled.
     */
    protected abstract void onOperationCodesReceived(Set<Integer> operations);

    public void onSessionOpened() {
        if (this.getVendorId() != 1193 && (this.getVendorId() != 1200 || this.getProductId() != 1058) && this.connection != null) {
            (this.mtpDevice = new MtpDevice(this.connection.getUsbDevice())).open(this.connection.getConnection());
            Log.d("CameraEvent", "MtpDevice\u5df2\u5f00\u542f");
            this.setApplicationModeForD850(1);
        }
        state = State.Active;
        listener.onCameraStarted(PtpCamera.this);
    }

    public void onSessionClosed() {
        shutdownHard();
        listener.onCameraStopped(PtpCamera.this);
    }

    public void onPictureReceived(final int objectHandle,
                                  final ObjectInfo objectInfo,
                                  final Bitmap bitmap) {
        listener.onCapturedPictureReceived(objectHandle, objectInfo, bitmap);
    }

    public void onEventObjectAdded(final int handle) {
        listener.onObjectAdded(handle);
    }

    public void onDeviceBusy(PtpAction action, boolean requeue) {
        if (requeue) {
            action.reset();
            queue.add(action);
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // nop
        }
    }

    private void setApplicationModeForD850(final int n) {
        Log.e("CameraEvent", "setApplicationModeForD850+++" + n);
        if (this.getVendorId() == 1193 || (this.getVendorId() == 1200 && this.getProductId() == 1058)) {
            return;
        }
        this.queue.add(new SetDevicePropValueCommand(this, 53744, n, 2));
    }

    public void onPtpWarning(final String message) {
        Log.i(TAG, "onPtpWarning: " + message);
    }

    public void onPtpError(final String message) {
        state = State.Error;
        if (state == State.Active) {
            shutdown();
        } else {
            shutdownHard();
        }
        listener.onError(message);
    }

    private void onUsbError(final String message) {
        Log.e(TAG, "onUsbError: " + message);
        queue.clear();
        shutdownHard();
        state = State.Error;
        listener.onError(String.format("Error in USB communication: %s", message));
    }

    protected abstract void queueEventCheck();

    protected void openSession() {
        if (isSessionOpen()) {
            onSessionOpened();
        } else {
            queue.add(new OpenSessionCommand(this));
        }
    }

    protected void closeSession() {
        queue.add(new CloseSessionCommand(this));
    }

    @Override
    public String getDeviceName() {
        return deviceInfo != null ? deviceInfo.model : "";
    }

    @Override
    public boolean isSessionOpen() {
        return state == State.Active;
    }

    @Override
    public String getDeviceInfo() {
        return deviceInfo != null ? deviceInfo.toString() : "unknown";
    }

    @Override
    public void retrievePicture(int objectHandle) {
        queue.add(new RetrievePictureAction(this, objectHandle));
    }

    /**
     * 读卡取照片
     *
     * @param listener
     */
    @Override
    public void retrieveStorages(StorageInfoListener listener) {
        this.queue.add(new GetStorageInfosAction(this, listener));
    }

    public void onPropertyChanged(final int n, final int n2, final int n3) {
        Log.i("CameraEvent", "p " + n + " " + n2 + "   " + n3);
        if (n3 == 1) {
            if (this.getVendorId() != 1193 && (this.getVendorId() != 1200 || this.getProductId() != 1058) && Build.VERSION.SDK_INT >= 24) {
                if (PtpCamera.cancellationSignal != null && !PtpCamera.cancellationSignal.isCanceled()) {
                    PtpCamera.cancellationSignal.cancel();
                    Log.d("CameraEvent", "\u76d1\u542c\u4e8b\u4ef6\u88ab\u53d6\u6d88");
                }
                PtpCamera.cancellationSignal = new CancellationSignal();
                new Thread() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                while (true) {
                                    Log.d("CameraEvent", "\u5f00\u59cb\u76d1\u542c\u4e8b\u4ef6");
                                    Log.d("CameraEvent", "\u76d1\u542c\u5230\u4e8b\u4ef6:" + PtpCamera.this.mtpDevice.readEvent(PtpCamera.cancellationSignal).getEventCode());
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                                Log.d("CameraEvent", "\u76d1\u542c\u4e8b\u4ef6\u610f\u5916\u7ec8\u6b62");
                            }
                        }
                    }
                }.start();
            }
        } else if (n3 == 2) {
            if (this.getVendorId() != 1193 && (this.getVendorId() != 1200 || this.getProductId() != 1058) && Build.VERSION.SDK_INT >= 24 && PtpCamera.cancellationSignal != null && !PtpCamera.cancellationSignal.isCanceled()) {
                PtpCamera.cancellationSignal.cancel();
            }
        } else {
            this.ptpProperties.put(n, n2);
            final Integer n4 = this.ptpToVirtualProperty.get(n);
            if (n4 != null) {
                this.handler.post((Runnable) new Runnable() {
                    @Override
                    public void run() {
                        PtpCamera.this.properties.put(n4, n2);
                        if (PtpCamera.this.listener != null) {
                            PtpCamera.this.listener.onPropertyChanged(n4, n2);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void retrieveImageHandles(StorageInfoListener listener, int storageId, int objectFormat) {
        queue.add(new GetObjectHandlesCommand(this, listener, storageId, objectFormat));
    }

    @Override
    public void deleteImageHandles(int objectHandle, int objectFormat) {
        queue.add(new DeleteObjectCommand(this, objectHandle, objectFormat));
    }

    @Override
    public void retrieveImageInfo(RetrieveImageInfoListener listener, int objectHandle) {
        queue.add(new RetrieveImageInfoAction(this, listener, objectHandle));
    }

    @Override
    public void retrieveImage(RetrieveImageListener listener, int objectHandle) {
        queue.add(new RetrieveImageAction(this, listener, objectHandle));
    }
}