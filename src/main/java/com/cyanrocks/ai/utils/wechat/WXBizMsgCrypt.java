/**
 * 对企业微信发送给企业后台的消息加解密示例代码.
 * 
 * @copyright Copyright (c) 1998-2014 Tencent Inc.
 */

// ------------------------------------------------------------------------

/**
 * 针对org.apache.commons.codec.binary.Base64，
 * 需要导入架包commons-codec-1.9（或commons-codec-1.8等其他版本）
 * 官方下载地址：http://commons.apache.org/proper/commons-codec/download_codec.cgi
 */
package com.cyanrocks.ai.utils.wechat;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;

/**
 * 提供接收和推送给企业微信消息的加解密接口(UTF8编码的字符串).
 * <ol>
 * 	<li>第三方回复加密消息给企业微信</li>
 * 	<li>第三方收到企业微信发送的消息，验证消息的安全性，并对消息进行解密。</li>
 * </ol>
 * 说明：异常java.security.InvalidKeyException:illegal Key Size的解决方案
 * <ol>
 * 	<li>在官方网站下载JCE无限制权限策略文件（JDK7的下载地址：
 *      http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html</li>
 * 	<li>下载后解压，可以看到local_policy.jar和US_export_policy.jar以及readme.txt</li>
 * 	<li>如果安装了JRE，将两个jar文件放到%JRE_HOME%\lib\security目录下覆盖原来的文件</li>
 * 	<li>如果安装了JDK，将两个jar文件放到%JDK_HOME%\jre\lib\security目录下覆盖原来文件</li>
 * </ol>
 */
public class WXBizMsgCrypt {
	static Charset CHARSET = Charset.forName("utf-8");
	Base64 base64 = new Base64();
	byte[] aesKey;
	String token;
	String receiveid;

	/**
	 * Initialize a WXBizMsgCrypt instance with the WeChat token, EncodingAESKey, and receiver identifier.
	 *
	 * <p>Validates and decodes the provided EncodingAESKey into the AES key used for message encryption/decryption.
	 *
	 * @param token the developer token configured in the enterprise WeChat backend
	 * @param encodingAesKey the EncodingAESKey configured in the enterprise WeChat backend; must be a 43-character Base64 string
	 * @param receiveid the recipient identifier used to validate decrypted messages (meaning varies by scenario)
	 * @throws AesException if the provided EncodingAESKey is invalid (e.g., length is not 43)
	public WXBizMsgCrypt(String token, String encodingAesKey, String receiveid) throws AesException {
		if (encodingAesKey.length() != 43) {
			throw new AesException(AesException.IllegalAesKey);
		}

		this.token = token;
		this.receiveid = receiveid;
		aesKey = Base64.decodeBase64(encodingAesKey + "=");
	}

	/**
	 * Convert an int to its 4-byte network byte order (big-endian) representation.
	 *
	 * @param sourceNumber the integer to encode as a 4-byte big-endian array
	 * @return a 4-byte array representing the integer in network byte order (big-endian)
	 */
	byte[] getNetworkBytesOrder(int sourceNumber) {
		byte[] orderBytes = new byte[4];
		orderBytes[3] = (byte) (sourceNumber & 0xFF);
		orderBytes[2] = (byte) (sourceNumber >> 8 & 0xFF);
		orderBytes[1] = (byte) (sourceNumber >> 16 & 0xFF);
		orderBytes[0] = (byte) (sourceNumber >> 24 & 0xFF);
		return orderBytes;
	}

	/**
	 * Convert a 4-byte array in network byte order (big-endian) to an int.
	 *
	 * @param orderBytes a 4-byte array representing an unsigned 32-bit value in network (big-endian) byte order
	 * @return the integer value reconstructed from the 4 bytes
	 */
	int recoverNetworkBytesOrder(byte[] orderBytes) {
		int sourceNumber = 0;
		for (int i = 0; i < 4; i++) {
			sourceNumber <<= 8;
			sourceNumber |= orderBytes[i] & 0xff;
		}
		return sourceNumber;
	}

	/**
	 * Generate a 16-character random string using uppercase letters, lowercase letters, and digits.
	 *
	 * @return a 16-character string containing characters A–Z, a–z, and 0–9
	 */
	String getRandomStr() {
		String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		Random random = new Random();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 16; i++) {
			int number = random.nextInt(base.length());
			sb.append(base.charAt(number));
		}
		return sb.toString();
	}

	/**
	 * Encrypts a plaintext payload using the enterprise WeChat format: a 16-byte random prefix, a 4-byte network-order length, the plaintext, and the receiveid, padded with the class's PKCS7 encoder and encrypted with AES/CBC (NoPadding).
	 *
	 * @param randomStr a 16-byte random string used as the message prefix
	 * @param text the plaintext XML to encrypt
	 * @return a Base64-encoded ciphertext string
	 * @throws AesException if encryption or encoding fails
	 */
	String encrypt(String randomStr, String text) throws AesException {
		ByteGroup byteCollector = new ByteGroup();
		byte[] randomStrBytes = randomStr.getBytes(CHARSET);
		byte[] textBytes = text.getBytes(CHARSET);
		byte[] networkBytesOrder = getNetworkBytesOrder(textBytes.length);
		byte[] receiveidBytes = receiveid.getBytes(CHARSET);

		// randomStr + networkBytesOrder + text + receiveid
		byteCollector.addBytes(randomStrBytes);
		byteCollector.addBytes(networkBytesOrder);
		byteCollector.addBytes(textBytes);
		byteCollector.addBytes(receiveidBytes);

		// ... + pad: 使用自定义的填充方式对明文进行补位填充
		byte[] padBytes = PKCS7Encoder.encode(byteCollector.size());
		byteCollector.addBytes(padBytes);

		// 获得最终的字节流, 未加密
		byte[] unencrypted = byteCollector.toBytes();

		try {
			// 设置加密模式为AES的CBC模式
			Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
			SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
			IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);

			// 加密
			byte[] encrypted = cipher.doFinal(unencrypted);

			// 使用BASE64对加密后的字符串进行编码
			String base64Encrypted = base64.encodeToString(encrypted);

			return base64Encrypted;
		} catch (Exception e) {
			e.printStackTrace();
			throw new AesException(AesException.EncryptAESError);
		}
	}

	/**
	 * Decrypts a Base64-encoded WeChat AES-CBC ciphertext and returns the decrypted XML payload.
	 *
	 * The method validates the embedded receiver identifier and will fail if the decrypted
	 * buffer is malformed or the receiver id does not match the configured value.
	 *
	 * @param text Base64-encoded ciphertext to decrypt (as received from WeChat).
	 * @return the decrypted XML plaintext.
	 * @throws AesException with code DecryptAESError if AES/Base64/cipher operations fail.
	 * @throws AesException with code IllegalBuffer if the decrypted buffer, padding, or length fields are invalid.
	 * @throws AesException with code ValidateCorpidError if the decrypted receiver id does not match the configured receiveid.
	 */
	String decrypt(String text) throws AesException {
		byte[] original;
		try {
			// 设置解密模式为AES的CBC模式
			Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
			SecretKeySpec key_spec = new SecretKeySpec(aesKey, "AES");
			IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
			cipher.init(Cipher.DECRYPT_MODE, key_spec, iv);

			// 使用BASE64对密文进行解码
			byte[] encrypted = Base64.decodeBase64(text);

			// 解密
			original = cipher.doFinal(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AesException(AesException.DecryptAESError);
		}

		String xmlContent, from_receiveid;
		try {
			// 去除补位字符
			byte[] bytes = PKCS7Encoder.decode(original);

			// 分离16位随机字符串,网络字节序和receiveid
			byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);

			int xmlLength = recoverNetworkBytesOrder(networkOrder);

			xmlContent = new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), CHARSET);
			from_receiveid = new String(Arrays.copyOfRange(bytes, 20 + xmlLength, bytes.length),
					CHARSET);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AesException(AesException.IllegalBuffer);
		}

		// receiveid不相同的情况
		if (!from_receiveid.equals(receiveid)) {
			throw new AesException(AesException.ValidateCorpidError);
		}
		return xmlContent;

	}

	/**
	 * Encrypts a reply XML message for Enterprise WeChat, signs it, and packages the result into the platform-required XML.
	 *
	 * @param replyMsg the reply message as an XML-formatted string to be encrypted
	 * @param timeStamp the timestamp to include in the signature and package; if empty, the current time in milliseconds is used
	 * @param nonce a random string to include in the signature and package
	 * @return an XML string containing the ciphertext and associated fields (msg_signature, timestamp, nonce, encrypt) ready to send to the platform
	 * @throws AesException if encryption, signing, or packaging fails (see the exception's error codes for details)
	 */
	public String EncryptMsg(String replyMsg, String timeStamp, String nonce) throws AesException {
		// 加密
		String encrypt = encrypt(getRandomStr(), replyMsg);

		// 生成安全签名
		if (timeStamp == "") {
			timeStamp = Long.toString(System.currentTimeMillis());
		}

		String signature = SHA1.getSHA1(token, timeStamp, nonce, encrypt);

		// System.out.println("发送给平台的签名是: " + signature[1].toString());
		// 生成发送的xml
		String result = XMLParse.generate(encrypt, signature, timeStamp, nonce);
		return result;
	}

	/**
	 * Verify an inbound message's signature and return its decrypted plaintext XML.
	 *
	 * Verifies the provided signature against the SHA1 of the token, timestamp, nonce,
	 * and the encrypted payload extracted from postData; if the signature matches the
	 * message is decrypted and returned.
	 *
	 * @param msgSignature the msg_signature URL parameter to verify
	 * @param timeStamp the timestamp URL parameter used in signature calculation
	 * @param nonce the nonce URL parameter used in signature calculation
	 * @param postData the POST request body containing the encrypted XML payload
	 * @return the decrypted original XML message
	 * @throws AesException if signature validation or decryption fails (see error codes)
	 */
	public String DecryptMsg(String msgSignature, String timeStamp, String nonce, String postData)
			throws AesException {

		// 密钥，公众账号的app secret
		// 提取密文
		Object[] encrypt = XMLParse.extract(postData);

		// 验证安全签名
		String signature = SHA1.getSHA1(token, timeStamp, nonce, encrypt[1].toString());

		// 和URL中的签名比较是否相等
		// System.out.println("第三方收到URL中的签名：" + msg_sign);
		// System.out.println("第三方校验签名：" + signature);
		if (!signature.equals(msgSignature)) {
			throw new AesException(AesException.ValidateSignatureError);
		}

		// 解密
		String result = decrypt(encrypt[1].toString());
		return result;
	}

	/**
	 * Verify the URL by validating the signature and decrypting the provided echo string.
	 *
	 * @param msgSignature the msg_signature URL parameter to validate
	 * @param timeStamp the timestamp URL parameter used in signature calculation
	 * @param nonce the nonce URL parameter used in signature calculation
	 * @param echoStr the echostr URL parameter containing the Base64 AES ciphertext to decrypt
	 * @return the decrypted echo string (plain XML or plaintext)
	 * @throws AesException if signature validation fails or decryption cannot be completed
	 */
	public String VerifyURL(String msgSignature, String timeStamp, String nonce, String echoStr)
			throws AesException {
		String signature = SHA1.getSHA1(token, timeStamp, nonce, echoStr);

		if (!signature.equals(msgSignature)) {
			throw new AesException(AesException.ValidateSignatureError);
		}

		String result = decrypt(echoStr);
		return result;
	}

}