package com.example.usb.util;

import com.example.usb.bean.StorageIdBean;

import java.util.ArrayList;
import java.util.List;

/**
 * author cowards
 * created on 2019\2\22 0022
 **/
public class Globe {

    /**
     * 存放卡ID和卡名称
     */
    public static volatile List<StorageIdBean> storageIdBeans = new ArrayList<>();

}