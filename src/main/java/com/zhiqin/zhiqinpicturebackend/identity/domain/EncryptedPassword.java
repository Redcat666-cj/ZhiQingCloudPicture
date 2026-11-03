package com.zhiqin.zhiqinpicturebackend.identity.domain;

import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码值对象。
 *
 * <p>把「加盐 + MD5」这条加密规则从 service 里收进领域：
 * 之前 {@code UserServiceImpl#getEncryptPassword} 里硬编码的 {@code "redcat"} 盐、
 * 以及散落在 controller 的加密调用，现在只有这一个入口。</p>
 *
 * <p>用 JDK 的 MessageDigest 实现，避免领域层依赖 Spring/Hutool。</p>
 */
public final class EncryptedPassword {

    /**
     * 历史遗留盐值，必须与旧数据保持一致，不能改
     */
    private static final String SALT = "redcat";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String value;

    private EncryptedPassword(String value) {
        this.value = value;
    }

    /**
     * 由明文密码生成密文值对象
     */
    public static EncryptedPassword ofRaw(String rawPassword) {
        if (rawPassword == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }
        return new EncryptedPassword(md5Hex(SALT + rawPassword));
    }

    /**
     * 由数据库中的密文还原值对象（用于比对）
     */
    public static EncryptedPassword ofEncrypted(String encrypted) {
        return new EncryptedPassword(encrypted);
    }

    public String value() {
        return value;
    }

    /**
     * 常量时间比较，避免时序侧信道
     */
    public boolean matches(EncryptedPassword other) {
        if (other == null || other.value == null || this.value == null) {
            return false;
        }
        byte[] a = this.value.getBytes(StandardCharsets.UTF_8);
        byte[] b = other.value.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    @Override
    public String toString() {
        // 避免密码密文被打进日志
        return "EncryptedPassword(******)";
    }

    private static String md5Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                chars[i * 2] = HEX[b >>> 4];
                chars[i * 2 + 1] = HEX[b & 0x0F];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 MD5", e);
        }
    }
}
