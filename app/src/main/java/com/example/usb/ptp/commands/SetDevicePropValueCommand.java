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

import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;
import com.example.usb.ptp.PtpConstants.Datatype;
import com.example.usb.ptp.PtpConstants.Operation;
import com.example.usb.ptp.PtpConstants.Type;

import java.nio.ByteBuffer;

public class SetDevicePropValueCommand extends Command {

    private final int property;
    private final int value;
    private final int datatype;

    public SetDevicePropValueCommand(PtpCamera camera, int property, int value, int datatype) {
        super(camera);
        this.property = property;
        this.value = value;
        this.datatype = datatype;
        hasDataToSend = true;
    }

    @Override
    public void exec(IO io) {
        if (this.property == 53744) {
            this.camera.onPropertyChanged(this.property, this.value, 1);
        }
        io.handleCommand(this);
        if (this.property == 53744) {
            this.camera.onPropertyChanged(this.property, this.value, 2);
        }
        if (this.responseCode == 8217) {
            this.camera.onDeviceBusy(this, true);
        }
        else if (this.responseCode == 8193) {
            this.camera.onPropertyChanged(this.property, this.value, 0);
        }
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        encodeCommand(b, Operation.SetDevicePropValue, property);
    }

    @Override
    public void encodeData(ByteBuffer b) {
        // header
        b.putInt(12 + PtpConstants.getDatatypeSize(datatype));
        b.putShort((short) Type.Data);
        b.putShort((short) Operation.SetDevicePropValue);
        b.putInt(camera.currentTransactionId());
        // specific block
        if (datatype == Datatype.int8 || datatype == Datatype.uint8) {
            b.put((byte) value);
        } else if (datatype == Datatype.int16 || datatype == Datatype.uint16) {
            b.putShort((short) value);
        } else if (datatype == Datatype.int32 || datatype == Datatype.uint32) {
            b.putInt(value);
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
