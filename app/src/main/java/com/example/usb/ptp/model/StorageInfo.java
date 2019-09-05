package com.example.usb.ptp.model;

import com.example.usb.ptp.PacketUtil;

import java.nio.ByteBuffer;

public class StorageInfo {
    public int storageType;
    public int filesystemType;
    public int accessCapability;
    public long maxCapacity;
    public long freeSpaceInBytes;
    public int freeSpaceInImages;
    public String storageDescription;//卡名称  SD或CF
    public String volumeLabel;

    public StorageInfo(ByteBuffer b, int length) {
        decode(b, length);
    }

    private void decode(ByteBuffer b, int length) {
        storageType = b.getShort() & 0xffff;
        filesystemType = b.getShort() & 0xffff;
        accessCapability = b.getShort() & 0xff;
        maxCapacity = b.getLong();
        freeSpaceInBytes = b.getLong();
        freeSpaceInImages = b.getInt();
        storageDescription = PacketUtil.readString(b);
        volumeLabel = PacketUtil.readString(b);
    }

    @Override
    public String toString() {
        return "StorageInfo{" +
                "storageType=" + storageType +
                ", filesystemType=" + filesystemType +
                ", accessCapability=" + accessCapability +
                ", maxCapacity=" + maxCapacity +
                ", freeSpaceInBytes=" + freeSpaceInBytes +
                ", freeSpaceInImages=" + freeSpaceInImages +
                ", storageDescription='" + storageDescription + '\'' +
                ", volumeLabel='" + volumeLabel + '\'' +
                '}';
    }
}
