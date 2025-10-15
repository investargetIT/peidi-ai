package com.cyanrocks.ui.constants;

/**
 * @Description
 * @Author yaoshunyu
 * @Date 2022-1-5 20:14
 * @Version 1.0
 **/
public enum GlobalLogEnum {
    GLOBAL_ERROR("common:Global error");


    public String getKey() {
        return key;
    }

    private final String key;

    GlobalLogEnum(String key) {
        this.key = key;
    }
}
