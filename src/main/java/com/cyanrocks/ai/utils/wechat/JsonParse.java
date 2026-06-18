/**
 * 对企业微信发送给企业后台的消息加解密示例代码.
 * 
 * @copyright Copyright (c) 1998-2020 Tencent Inc.
 */

// ------------------------------------------------------------------------

package com.cyanrocks.ai.utils.wechat;

import cn.hutool.json.JSONObject;

/**
 * JsonParse class
 *
 * 提供提取消息格式中的密文及生成回复消息格式的接口.
 */
class JsonParse {

	/**
	 * Extracts encryption and routing fields from a WeCom callback JSON string.
	 *
	 * @param jsontext the incoming WeCom callback JSON containing "encrypt", "tousername", and "agentid" fields
	 * @return an Object[] whose elements are: index 0 = `tousername`, index 1 = encrypted message (`encrypt`), index 2 = `agentid`
	 * @throws AesException if the input cannot be parsed or required fields cannot be read
	 */
	public static Object[] extract(String jsontext) throws AesException     {
		Object[] result = new Object[3];
		try {

			JSONObject json = new JSONObject(jsontext);
        	String encrypt_msg = json.getStr("encrypt");
			String tousername  = json.getStr("tousername");
			String agentid     = json.getStr("agentid");

			result[0] = tousername;
			result[1] = encrypt_msg;
			result[2] = agentid;
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AesException(AesException.ParseJsonError);
		}
	}

	/**
	 * Create a JSON-formatted string containing the encrypted payload and its signature metadata.
	 *
	 * @param encrypt  the encrypted message ciphertext
	 * @param signature the message signature (msgsignature)
	 * @param timestamp the timestamp value as a string
	 * @param nonce    a random string (nonce)
	 * @return the JSON string with keys "encrypt", "msgsignature", "timestamp", and "nonce"
	 */
	public static String generate(String encrypt, String signature, String timestamp, String nonce) {

		String format = "{\"encrypt\":\"%1$s\",\"msgsignature\":\"%2$s\",\"timestamp\":\"%3$s\",\"nonce\":\"%4$s\"}";
		return String.format(format, encrypt, signature, timestamp, nonce);

	}
}
