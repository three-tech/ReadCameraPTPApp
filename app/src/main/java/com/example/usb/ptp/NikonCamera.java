package com.example.usb.ptp;

import android.util.Log;

import com.example.usb.ptp.commands.nikon.NikonEventCheckCommand;
import com.example.usb.ptp.commands.nikon.NikonOpenSessionAction;

import java.util.Set;

public class NikonCamera extends PtpCamera {
    private int[] vendorPropCodes = new int[0];

    public NikonCamera(PtpUsbConnection connection, CameraListener listener) {
        super(connection, listener);
    }

    @Override
    protected void onOperationCodesReceived(Set<Integer> operations) {
        if (AppConfig.LOG) {
            for (int i = 0; i < deviceInfo.operationsSupported.length; ++i) {
                Log.e("PtpUsbService", String.format("operationsSupported: 0x%x", deviceInfo.operationsSupported[i]));
            }

            for (int i = 0; i < deviceInfo.devicePropertiesSupported.length; ++i) {
                Log.e("PtpUsbService", String.format("devicePropertiesSupported: 0x%x", deviceInfo.devicePropertiesSupported[i]));
            }
        }
    }

    public void setVendorPropCodes(int[] vendorPropCodes) {
        this.vendorPropCodes = vendorPropCodes;
        if (AppConfig.LOG) {
            for (int i = 0; i < vendorPropCodes.length; ++i) {
                Log.e("PtpUsbService", String.format("vendorPropCodes: 0x%x", vendorPropCodes[i]));
            }
        }
    }

    @Override
    protected void openSession() {
        queue.add(new NikonOpenSessionAction(this));
    }

    @Override
    protected void queueEventCheck() {
        queue.add(new NikonEventCheckCommand(this));
    }

    public boolean hasSupport(int oc) {
        if ((oc & 0x7000) == 0x1000) { /* commands */
            if ((oc & 0xf000) == 0x1000) { /* 通用命令 */
                return true;
            }

            for (int i = 0; i < this.deviceInfo.operationsSupported.length; ++i) {
                if (oc == this.deviceInfo.operationsSupported[i]) {
                    return true;
                }
            }
        } else if ((oc & 0x7000) == 0x5000) { /* properties */
            if ((oc & 0xf000) == 0x5000) { /* 通用属性 */
                return true;
            }

            for (int i = 0; i < this.deviceInfo.devicePropertiesSupported.length; ++i) {
                if (oc == this.deviceInfo.devicePropertiesSupported[i]) {
                    return true;
                }
            }
        }

        // 附加属性(供应商属性)
        for (int i = 0; i < this.vendorPropCodes.length; ++i) {
            if (oc == this.vendorPropCodes[i]) {
                return true;
            }
        }

        return false;
    }
}
