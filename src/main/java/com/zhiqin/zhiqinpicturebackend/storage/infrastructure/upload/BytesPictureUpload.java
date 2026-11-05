package com.zhiqin.zhiqinpicturebackend.storage.infrastructure.upload;

import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * 内存字节上传模板：批量抓取等"图片已在内存"的场景专用。
 *
 * <p>相比 URL 上传少一次网络下载（内容已先行下载做指纹判重），
 * 校验用图片魔数而非文件名后缀——内存里的字节没有文件名，魔数才是真实格式。</p>
 */
@Service
public class BytesPictureUpload extends PictureUploadTemplate {

    /** 图片大小上限，对齐 URL 上传的 3MB */
    private static final long MAX_SIZE = 3 * 1024 * 1024L;

    /** 魔数前缀 → 文件后缀（校验与命名共用；顺序重要：WEBP 的 RIFF 头要先比对完整签名） */
    private static final Map<String, String> MAGIC_SUFFIX = Map.of(
            "FFD8FF", "jpeg",
            "89504E47", "png",
            "47494638", "gif",
            "424D", "bmp");

    @Override
    protected void ProcessFile(Object file, File tempFile) throws IOException {
        Files.write(tempFile.toPath(), (byte[]) file);
    }

    @Override
    protected void vaildPicture(Object file) {
        byte[] bytes = (byte[]) file;
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(bytes.length > MAX_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过3MB");
        ThrowUtils.throwIf(detectSuffix(bytes) == null, ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    @Override
    protected String getOriginalFilename(Object file) {
        byte[] bytes = (byte[]) file;
        String suffix = detectSuffix(bytes);
        // 魔数判定失败在 vaildPicture 已拦截，这里必有值；兜底给 jpeg
        return "crawl." + (suffix == null ? "jpeg" : suffix);
    }

    /**
     * 按魔数识别图片格式，返回小写后缀；非支持格式返回 null。
     */
    private String detectSuffix(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        // WebP：RIFF????WEBP
        if ((bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x46
                && (bytes[8] & 0xFF) == 0x57 && (bytes[9] & 0xFF) == 0x45
                && (bytes[10] & 0xFF) == 0x42 && (bytes[11] & 0xFF) == 0x50) {
            return "webp";
        }
        String hex = hexOf(bytes, Math.min(4, bytes.length));
        for (Map.Entry<String, String> entry : MAGIC_SUFFIX.entrySet()) {
            if (hex.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String hexOf(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
