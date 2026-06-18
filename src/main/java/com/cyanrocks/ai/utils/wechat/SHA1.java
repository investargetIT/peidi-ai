/**
 * 对企业微信发送给企业后台的消息加解密示例代码.
 * 
 * @copyright Copyright (c) 1998-2014 Tencent Inc.
 */

// ------------------------------------------------------------------------

package com.cyanrocks.ai.utils.wechat;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * SHA1 class
 *
 * 计算消息签名接口.
 */
class SHA1 {

	/**
	 * Generate a SHA-1 signature from the given token, timestamp, nonce, and encrypted message.
	 *
	 * @param token     the token used in signature generation
	 * @param timestamp the timestamp string
	 * @param nonce     a random string
	 * @param encrypt   the encrypted message string
	 * @return          the lowercase hexadecimal SHA-1 digest of the concatenation of the inputs after lexicographic sorting
	 * @throws AesException if an error occurs while computing the signature
	 */
	public static String getSHA1(String token, String timestamp, String nonce, String encrypt) throws AesException
			  {
		try {
			String[] array = new String[] { token, timestamp, nonce, encrypt };
			StringBuffer sb = new StringBuffer();
			// 字符串排序
			Arrays.sort(array);
			for (int i = 0; i < 4; i++) {
				sb.append(array[i]);
			}
			String str = sb.toString();
			// SHA1签名生成
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(str.getBytes());
			byte[] digest = md.digest();

			StringBuffer hexstr = new StringBuffer();
			String shaHex = "";
			for (int i = 0; i < digest.length; i++) {
				shaHex = Integer.toHexString(digest[i] & 0xFF);
				if (shaHex.length() < 2) {
					hexstr.append(0);
				}
				hexstr.append(shaHex);
			}
			return hexstr.toString();
		} catch (Exception e) {
			e.printStackTrace();
			throw new AesException(AesException.ComputeSignatureError);
		}
	}
}
