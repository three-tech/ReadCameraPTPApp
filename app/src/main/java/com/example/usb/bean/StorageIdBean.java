package com.example.usb.bean;

/**
 * author cowards
 * created on 2019\2\22 0022
 **/
public class StorageIdBean {
    public Integer storage;//内存卡ID
    public String cardName;//卡名称

    public Integer getStorage() {
        return storage;
    }

    public void setStorage(Integer storage) {
        this.storage = storage;
    }

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    @Override
    public String toString() {
        return "StorageIdBean{" +
                "storage=" + storage +
                ", cardName='" + cardName + '\'' +
                '}';
    }
}
