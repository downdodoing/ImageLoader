package com.example.mvp.myimageloader.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 将图片的url转化为key，为了防止图片中url的特殊字符
 * <p>
 * Created by MVP on 2017/7/28.
 */

public class UrlToKey {
    public static String hashKeyFromUrl(String url) {
        String cacheKey = null;

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return cacheKey;
    }

    private static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
