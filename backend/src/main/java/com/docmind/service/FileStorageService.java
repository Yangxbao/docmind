package com.docmind.service;

import com.docmind.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件磁盘存储服务。
 * 上传的原始文件保存在本地磁盘 {docmind.upload-dir}/{userId}/{uuid}.{ext}，
 * 数据库里只存相对路径（file_path），全部文件都在自己电脑上，不出内网。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${docmind.upload-dir}")
    private String uploadDir;

    /** 存储根目录的绝对路径 */
    private Path baseDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 保存上传的文件。
     * @return 相对路径，如 "1/8f3a2b...c9.pdf"，数据库里就存这个
     */
    public String save(Long userId, String originalFilename, byte[] data) {
        String ext = getExtension(originalFilename);
        String relativePath = userId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path target = baseDir().resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return relativePath;
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 相对路径 → 绝对路径。
     * 带目录越界校验：如果路径里被恶意构造了 ../ 跳出存储目录，直接拒绝。
     */
    public Path resolve(String relativePath) {
        Path base = baseDir();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new BizException("非法的文件路径");
        }
        return target;
    }

    /** 删除磁盘文件（删除文档时调用；文件不存在只记日志不报错） */
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception e) {
            log.warn("删除磁盘文件失败: {}, 原因: {}", relativePath, e.getMessage());
        }
    }

    /** 取小写扩展名（不含点），没有扩展名返回 bin */
    public static String getExtension(String filename) {
        if (filename == null) {
            return "bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
