package com.example.usb.ptp;

import android.util.Log;

import com.example.usb.ptp.commands.eos.EosEventCheckCommand;
import com.example.usb.ptp.commands.eos.EosOpenSessionAction;

import java.util.Set;

public class EosCamera extends PtpCamera {
    public EosCamera(PtpUsbConnection connection, CameraListener listener) {
        super(connection, listener);
    }

    public void onEventDirItemCreated(int objectHandle, int storageId, int objectFormat, String filename) {
        onEventObjectAdded(objectHandle);
    }

    @Override
    protected void openSession() {
        queue.add(new EosOpenSessionAction(this));
    }

    @Override
    protected void queueEventCheck() {
        queue.add(new EosEventCheckCommand(this));
    }

    @Override
    protected void onOperationCodesReceived(Set<Integer> operations) {
        if (AppConfig.LOG) {
            for (int i = 0; i < deviceInfo.operationsSupported.length; ++i) {
                Log.i("PtpUsbService", String.format("operationsSupported: 0x%x", deviceInfo.operationsSupported[i]));
            }

            for (int i = 0; i < deviceInfo.devicePropertiesSupported.length; ++i) {
                Log.i("PtpUsbService", String.format("devicePropertiesSupported: 0x%x", deviceInfo.devicePropertiesSupported[i]));
            }
        }
    }

    public boolean hasSupport(int operation) {
        for (int i = 0; i < this.deviceInfo.operationsSupported.length; ++i) {
            if (operation == this.deviceInfo.operationsSupported[i]) {
                return true;
            }
        }
        return false;
    }
}