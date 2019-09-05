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

import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;
import com.example.usb.ptp.PtpConstants.Operation;
import com.example.usb.ptp.PtpConstants.Response;

import java.nio.ByteBuffer;

public class OpenSessionCommand extends Command {
    public OpenSessionCommand(PtpCamera camera) {
        super(camera);
    }

    @Override
    public void exec(IO io) {
        io.handleCommand(this);
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        camera.resetTransactionId();
        encodeCommand(b, Operation.OpenSession, 1);
    }
}
