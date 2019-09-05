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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.usb.ptp.PtpCamera;
import com.example.usb.ptp.PtpCamera.IO;
import com.example.usb.ptp.PtpConstants;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;

/**
 * Read file data from camera with specified {@code objectHandle}.
 */
public class GetObjectCommand extends Command {

    private static final String TAG = GetObjectCommand.class.getSimpleName();

    private final int objectHandle;

    private final BitmapFactory.Options options;
    private Bitmap inBitmap;
    private boolean outOfMemoryError;

    public GetObjectCommand(PtpCamera camera, int objectHandle) {
        super(camera);
        this.objectHandle = objectHandle;
        options = new BitmapFactory.Options();
    }

    public Bitmap getBitmap() {
        return inBitmap;
    }

    public boolean isOutOfMemoryError() {
        return outOfMemoryError;
    }

    @Override
    public void exec(IO io) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        super.reset();
        inBitmap = null;
        outOfMemoryError = false;
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        encodeCommand(b, PtpConstants.Operation.GetObject, objectHandle);
    }

    @Override
    protected void decodeData(ByteBuffer b, int length) {
        try {
            options.inSampleSize = this.camera.getInSampleSize();
            // 12 == offset of data header
            SoftReference softRef = new SoftReference(BitmapFactory.decodeByteArray(
                    b.array(),
                    12,
                    length - 12,
                    options));
            inBitmap = (Bitmap) softRef.get();
        } catch (RuntimeException e) {
            Log.i(TAG, "exception on decoding picture : " + e.toString());
        } catch (OutOfMemoryError e) {
            System.gc();
            outOfMemoryError = true;
        }

    }
}