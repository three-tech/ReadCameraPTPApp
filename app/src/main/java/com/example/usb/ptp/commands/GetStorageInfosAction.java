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
package com.example.usb.ptp.commands;

import android.nfc.Tag;
import android.util.Log;

import com.example.usb.bean.StorageIdBean;
import com.example.usb.ptp.Camera;
import com.example.usb.ptp.Camera.StorageInfoListener;
import com.example.usb.ptp.PtpAction;
import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;
import com.example.usb.ptp.PtpConstants.Response;
import com.example.usb.util.Globe;

import java.util.ArrayList;

public class GetStorageInfosAction implements PtpAction {

    private String TAG = getClass().getSimpleName();
    private final PtpCamera camera;
    private final StorageInfoListener listener;

    public GetStorageInfosAction(PtpCamera camera, Camera.StorageInfoListener listener) {
        this.camera = camera;
        this.listener = listener;
    }

    @Override
    public void exec(IO io) {
        ArrayList<Integer> storageIds = new ArrayList();//读取设备上的卡数量
        GetStorageIdsCommand getStorageIds = new GetStorageIdsCommand(camera);
        io.handleCommand(getStorageIds);
        if (getStorageIds.getResponseCode() != Response.Ok) {
            // 设备忙
            if (getStorageIds.getResponseCode() == PtpConstants.Response.DeviceBusy) {
                camera.onDeviceBusy(this, false);
                return;
            }
            Log.e(getClass().getSimpleName(), "设备忙");
            listener.onAllStorageFound(storageIds.get(0));
            return;
        }
        int ids[] = getStorageIds.getStorageIds();
        for (int i = 0; i < ids.length; ++i) {
            int storageId = ids[i];
            Log.e(getClass().getSimpleName(), "storageId:" + storageId);
            // 131072或者65536表没有插卡
            if (storageId == 131072 || storageId == 65536) {
                continue;
            }
            //不取存储卡名称，以下代码注释掉
            GetStorageInfoCommand c = new GetStorageInfoCommand(camera, storageId);
            io.handleCommand(c);
            if (c.getResponseCode() != Response.Ok) {
                Log.e(TAG, "c.getResponseCode() != Response.Ok");
                listener.onAllStorageFound(storageIds.get(0));
                return;
            }
            String label = c.getStorageInfo().volumeLabel.isEmpty()
                    ? c.getStorageInfo().storageDescription
                    : c.getStorageInfo().volumeLabel;
            if (label == null || label.isEmpty()) {
                label = "Storage " + i;
            }
            Log.e(getClass().getSimpleName(), "内存卡名称:" + c.getStorageInfo().storageDescription);
            storageIds.add(storageId);

            StorageIdBean storageIdBean = new StorageIdBean();
            storageIdBean.setStorage(storageId);
            storageIdBean.setCardName(c.getStorageInfo().storageDescription);
            if (Globe.storageIdBeans.size() == 0) {
                Globe.storageIdBeans.add(0, storageIdBean);
            }
            if (Globe.storageIdBeans.size() == 1) {
                Globe.storageIdBeans.add(1, storageIdBean);
            }
        }

        /**   不管相机内插了几张卡，只要卡数量大于0，就默认读第一张卡  **/
        if (Globe.storageIdBeans.size() > 0) {
            listener.onAllStorageFound(Globe.storageIdBeans.get(0).storage);
        }
    }

    @Override
    public void reset() {
    }
}