package com.cyanrocks.ai.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description
 * @Author yaoshunyu
 * @Date 2021-12-22 19:46
 * @Version 1.0
 **/
public class LogUtils {

    private static final Logger alertErrorLog = LoggerFactory.getLogger("AlertErrorLog");

    public static void alertError(String errorKey, String msg, Exception e) {
        alertErrorLog.error(errorKey + "##" + msg, e);
    }

    public static void alertError(String errorKey, String msg, Throwable e) {
        alertErrorLog.error(errorKey + "##" + msg, e);
    }

    public static void commonError(String msg, Exception e, Logger logger) {
        logger.error(msg, e);
    }

    public static void commonInfo(String msg, Logger logger) {
        logger.info(msg);
    }

}
