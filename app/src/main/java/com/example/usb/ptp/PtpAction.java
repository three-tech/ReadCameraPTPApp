package com.example.usb.ptp;

public interface PtpAction {
    void exec(PtpCamera.IO io);

    /**
     * Reset an already used action so it can be re-used.
     */
    void reset();
}
