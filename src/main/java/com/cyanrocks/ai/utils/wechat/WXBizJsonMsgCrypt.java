package com.cyanrocks.ai.utils.wechat;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;

/**
 * @Author wjq
 * @Date 2026/1/30 10:12
 */
public class WXBizJsonMsgCrypt {
    static Charset CHARSET = Charset.forName("utf-8");
    Base64 base64 = new Base64();
    byte[] aesKey;
    String token;
    String receiveid;

    /**
     * Create a WXBizJsonMsgCrypt configured with the given token, EncodingAESKey, and receiveid.
     *
     * The provided EncodingAESKey must be a 43-character string; it will be base64-decoded to derive the AES key material.
     *
     * @param token         the developer token configured in the Enterprise WeChat backend used for signature generation
     * @param encodingAesKey the 43-character EncodingAESKey configured in the Enterprise WeChat backend (base64 string)
     * @param receiveid     scene-specific identifier to be validated against decrypted payloads
     * @throws AesException if the provided EncodingAESKey length is not 43 characters (IllegalAesKey) or other initialization failures occur
     */
    public WXBizJsonMsgCrypt(String token, String encodingAesKey, String receiveid) throws AesException {
        if (encodingAesKey.length() != 43) {
            throw new AesException(AesException.IllegalAesKey);
        }

        this.token = token;
        this.receiveid = receiveid;
        aesKey = Base64.decodeBase64(encodingAesKey + "=");
    }

    /**
     * Encode an integer into a 4-byte array using network (big-endian) byte order.
     *
     * @param sourceNumber the integer to encode
     * @return a 4-byte array containing the big-endian representation of {@code sourceNumber}
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
     * Reconstructs a 32-bit integer from a 4-byte array interpreted in network (big-endian) byte order.
     *
     * @param orderBytes a 4-byte array containing the integer in big-endian (network) order
     * @return the integer value represented by the four bytes
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
     * Produces a 16-character random string composed of upper- and lower-case letters and digits.
     *
     * @return a newly generated 16-character alphanumeric string
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
     * Encrypts a plaintext payload and returns it as a Base64-encoded ciphertext.
     *
     * The payload is constructed as: `randomStr || 4-byte network-order(text length) || text || receiveid`, then PKCS7-padded and encrypted with AES/CBC/NoPadding using the instance key and IV. The resulting ciphertext is Base64-encoded.
     *
     * @param randomStr a 16-character random prefix included at the start of the payload
     * @param text the plaintext JSON/text to encrypt
     * @return the Base64-encoded ciphertext
     * @throws AesException if an error occurs during encryption
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
     * Decrypts a Base64-encoded AES/CBC/NoPadding ciphertext, extracts the inner JSON payload, and validates the configured receiveid.
     *
     * @param text Base64-encoded ciphertext produced by the corresponding encrypt routine
     * @return the decrypted JSON content extracted from the payload
     * @throws AesException if AES decryption or Base64 decoding fails, if padding or buffer parsing is invalid, or if the decrypted receiveid does not match the configured receiveid
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

        String jsonContent, from_receiveid;
        try {
            // 去除补位字符
            byte[] bytes = PKCS7Encoder.decode(original);

            // 分离16位随机字符串,网络字节序和receiveid
            byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);

            int jsonLength = recoverNetworkBytesOrder(networkOrder);

            jsonContent = new String(Arrays.copyOfRange(bytes, 20, 20 + jsonLength), CHARSET);
            from_receiveid = new String(Arrays.copyOfRange(bytes, 20 + jsonLength, bytes.length),
                    CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
            throw new AesException(AesException.IllegalBuffer);
        }

        // receiveid不相同的情况
        if (!from_receiveid.equals(receiveid)) {
            throw new AesException(AesException.ValidateCorpidError);
        }
        return jsonContent;

    }

    /**
     * Encrypts a reply message, computes its SHA1 signature, and returns the packaged JSON ready to be sent to Enterprise WeChat.
     *
     * @param replyMsg the reply payload as a JSON-formatted string
     * @param timeStamp the timestamp to include in the package; if an empty string is provided the current system time in milliseconds is used
     * @param nonce a random string to include in the package
     * @return a JSON-formatted string containing `msg_signature`, `timestamp`, `nonce`, and `encrypt`
     * @throws AesException if encryption, signature generation, or packaging fails
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
        // 生成发送的json
        String result = JsonParse.generate(encrypt, signature, timeStamp, nonce);
        return result;
    }

    /**
     * Verify the message signature and return the decrypted plaintext.
     *
     * Verifies the SHA1 signature computed from the configured token, the provided
     * timestamp, nonce, and the ciphertext contained in postData. If signature
     * verification succeeds, extracts the encrypted payload from postData, decrypts
     * it, and returns the resulting plaintext.
     *
     * @param msgSignature signature string from the URL parameter `msg_signature`
     * @param timeStamp timestamp from the URL parameter `timestamp`
     * @param nonce random string from the URL parameter `nonce`
     * @param postData POST body containing the encrypted message (JSON)
     * @return the decrypted plaintext message
     * @throws AesException if signature validation or decryption fails; check the exception's error code for details
     */
    public String DecryptMsg(String msgSignature, String timeStamp, String nonce, String postData)
            throws AesException {

        // 密钥，公众账号的app secret
        // 提取密文
        Object[] encrypt = JsonParse.extract(postData);

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
     * Verify the URL signature and decrypt the provided `echostr`.
     *
     * @param msgSignature the `msg_signature` URL parameter to verify
     * @param timeStamp the `timestamp` URL parameter used in signature verification
     * @param nonce the `nonce` URL parameter used in signature verification
     * @param echoStr the `echostr` URL parameter (Base64-encoded encrypted payload)
     * @return the decrypted `echostr` content
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
