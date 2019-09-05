/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.usb.ptp.commands.nikon;

import android.util.Log;

import com.example.usb.ptp.NikonCamera;
import com.example.usb.ptp.PtpAction;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;
import com.example.usb.ptp.PtpConstants.Datatype;
import com.example.usb.ptp.PtpConstants.Operation;
import com.example.usb.ptp.PtpConstants.Property;
import com.example.usb.ptp.commands.OpenSessionCommand;
import com.example.usb.ptp.commands.SetDevicePropValueCommand;

public class NikonOpenSessionAction implements PtpAction {

    private String TAG = getClass().getSimpleName();
    private final NikonCamera camera;

    public NikonOpenSessionAction(NikonCamera camera) {
        this.camera = camera;
    }

    @Override
    public void exec(IO io) {
        OpenSessionCommand openSession = new OpenSessionCommand(camera);
        io.handleCommand(openSession);
        if (openSession.getResponseCode() != PtpConstants.Response.Ok) {
            if (openSession.getResponseCode() == PtpConstants.Response.SessionAlreadyOpen) {
                // SessionAlreadyOpen
                Log.e(TAG, "打开session成功：" + openSession.getResponseCode());
                camera.onSessionOpened();
            } else if (openSession.getResponseCode() == PtpConstants.Response.DeviceBusy) {
                Log.e(TAG, "打开session繁忙：" + openSession.getResponseCode());
                camera.onDeviceBusy(this, false);
            } else {
                Log.e(TAG, "打开session失败：" + openSession.getResponseCode());
                camera.onPtpError(String.format(
                        "打开session失败\"%s\"",
                        PtpConstants.responseToString(openSession.getResponseCode())));
            }
            return;
        }

        if (camera.hasSupport(Operation.NikonGetVendorPropCodes)) {
            NikonGetVendorPropCodesCommand getPropCodes = new NikonGetVendorPropCodesCommand(camera);
            io.handleCommand(getPropCodes);
            if (getPropCodes.getResponseCode() == PtpConstants.Response.Ok) {
                camera.setVendorPropCodes(getPropCodes.getPropertyCodes());
            }

            if (camera.getProductId() == PtpConstants.Product.NikonD5) {
                camera.onSessionOpened();
                return;
            }

            if (camera.hasSupport(Property.NikonRecordingMedia)) {
                // 如果为2，则删除照片可能不支持，需要验证 0: Card（卡）, 1: SDRAM（内存）, 2: Card and SDRAM（内存和卡）
                SetDevicePropValueCommand c = new SetDevicePropValueCommand(
                        camera,
                        Property.NikonRecordingMedia,
                        0,
                        Datatype.uint8);
                io.handleCommand(c);
                if (c.getResponseCode() != PtpConstants.Response.Ok) {
                    Log.e("PtpUsbService", String.format(
                            "设置记录方式失败\"%s\"",
                            PtpConstants.responseToString(c.getResponseCode())));
                }
            }

            // 设置应用模式
            if (camera.hasSupport(Operation.NikonApplicationMode)) {
                // D5先屏蔽，没有拿到机器，所以不好测试
                if (camera.getProductId() != PtpConstants.Product.NikonD5) {
                    SetDevicePropValueCommand spv  = new SetDevicePropValueCommand(
                            camera,
                            Operation.NikonApplicationMode,
                            1,
                            Datatype.uint8);
                    io.handleCommand(spv);
                    if (spv.getResponseCode() != PtpConstants.Response.Ok) {
                        Log.e("PtpUsbService", String.format(
                                "设置应用模式失败\"%s\"",
                                PtpConstants.responseToString(spv.getResponseCode())));
                    }
                }
            }
        }
        camera.onSessionOpened();
    }

    @Override
    public void reset() {
    }
}
