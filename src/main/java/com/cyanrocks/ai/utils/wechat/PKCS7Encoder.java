/**
 * 对企业微信发送给企业后台的消息加解密示例代码.
 * 
 * @copyright Copyright (c) 1998-2014 Tencent Inc.
 */

// ------------------------------------------------------------------------

package com.cyanrocks.ai.utils.wechat;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * 提供基于PKCS7算法的加解密接口.
 */
class PKCS7Encoder {
	static Charset CHARSET = Charset.forName("utf-8");
	static int BLOCK_SIZE = 32;

	/**
	 * Generate PKCS#7-style padding bytes for a plaintext of the given length.
	 *
	 * @param count the number of plaintext bytes to be padded
	 * @return a byte array of padding bytes whose length is between 1 and BLOCK_SIZE (inclusive); each byte's value equals the padding length
	 */
	static byte[] encode(int count) {
		// 计算需要填充的位数
		int amountToPad = BLOCK_SIZE - (count % BLOCK_SIZE);
		if (amountToPad == 0) {
			amountToPad = BLOCK_SIZE;
		}
		// 获得补位所用的字符
		char padChr = chr(amountToPad);
		String tmp = new String();
		for (int index = 0; index < amountToPad; index++) {
			tmp += padChr;
		}
		return tmp.getBytes(CHARSET);
	}

	/**
	 * Remove PKCS#7-style padding bytes from a decrypted plaintext block.
	 *
	 * The padding length is taken from the last byte of the input and must be between 1 and 32;
	 * if the value is outside that range it is treated as zero (no padding removed).
	 *
	 * @param decrypted the decrypted plaintext bytes that may include PKCS#7 padding
	 * @return a new byte array with the padding bytes removed
	 */
	static byte[] decode(byte[] decrypted) {
		int pad = (int) decrypted[decrypted.length - 1];
		if (pad < 1 || pad > 32) {
			pad = 0;
		}
		return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
	}

	/**
	 * Convert an integer to a single character derived from its low 8 bits for use as a padding byte.
	 *
	 * @param a the integer whose low 8 bits will be converted into a padding character
	 * @return a character representing the low 8 bits of the input integer
	 */
	static char chr(int a) {
		byte target = (byte) (a & 0xFF);
		return (char) target;
	}

}
