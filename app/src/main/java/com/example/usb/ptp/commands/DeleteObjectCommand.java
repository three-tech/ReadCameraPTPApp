package com.example.usb.ptp.commands;

import android.util.Log;

import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpConstants;

import java.nio.ByteBuffer;

public class DeleteObjectCommand extends Command {
    private final int objectHandle;
    private final int objectFormat;

    public DeleteObjectCommand(PtpCamera camera, int objectHandle, int objectFormat) {
        super(camera);
        this.objectHandle = objectHandle;
        this.objectFormat = objectFormat;
    }

    @Override
    public void exec(PtpCamera.IO io) {
        io.handleCommand(this);
        if (responseCode == PtpConstants.Response.DeviceBusy) {
            camera.onDeviceBusy(this, false);
            return;
        }

        if (responseCode != PtpConstants.Response.Ok) {
            Log.e("PtpUsbService", String.format("Couldn't delete,code \"%s\",%d-%d",
                    PtpConstants.responseToString(responseCode), objectHandle, objectFormat));
            return;
        }
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        // 对于尼康：
        // SDK文档里面可以带objectFormat这个参数，实际测试如果加上，会报无效的参数
        // ObjectFormatCode是在ObjectHandle设置为0xFFFFFFFF以外的值的情况下指定的。
        super.encodeCommand(b, PtpConstants.Operation.DeleteObject, objectHandle);
    }
}
