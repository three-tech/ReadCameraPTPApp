/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.usb.ptp.commands;

import android.graphics.Bitmap;

import com.example.usb.ptp.PtpAction;
import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;
import com.example.usb.ptp.PtpConstants.Response;
import com.example.usb.ptp.model.ObjectInfo;

public class RetrievePictureAction implements PtpAction {

    private final PtpCamera camera;
    private final int objectHandle;

    public RetrievePictureAction(PtpCamera camera, int objectHandle) {
        this.camera = camera;
        this.objectHandle = objectHandle;
    }

    @Override
    public void exec(IO io) {
        GetObjectInfoCommand getInfo = new GetObjectInfoCommand(camera, objectHandle);
        io.handleCommand(getInfo);
        if (getInfo.getResponseCode() != Response.Ok) {
            return;
        }

        ObjectInfo objectInfo = getInfo.getObjectInfo();
        if (objectInfo == null) {
            return;
        }
        // 只处理jpg格式的文件
        if (objectInfo.objectFormat != PtpConstants.ObjectFormat.EXIF_JPEG) {
            return;
        }

        GetObjectCommand getObject = new GetObjectCommand(camera, objectHandle);
        io.handleCommand(getObject);
        if (getObject.getResponseCode() != Response.Ok) {
            return;
        }
        if (getObject.getBitmap() == null) {
            if (getObject.isOutOfMemoryError()) {
                camera.onPictureReceived(objectHandle, objectInfo, null);
            }
            return;
        }
        camera.onPictureReceived(objectHandle, objectInfo, getObject.getBitmap());
    }

    @Override
    public void reset() {
    }
}
