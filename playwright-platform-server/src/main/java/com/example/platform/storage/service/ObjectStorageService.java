package com.example.platform.storage.service;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 对象存储服务接口，定义文件存储和检索的操作方法。
 *
 * <p>核心职责：
 * <ul>
 *   <li>上传整个目录到对象存储</li>
 *   <li>上传单个文件到对象存储</li>
 *   <li>创建预签名下载 URL</li>
 *   <li>获取对象的输入流</li>
 *   <li>删除指定对象</li>
 * </ul>
 *
 * <p>依赖说明：无外部依赖。
 */
public interface ObjectStorageService {
    
    /**
     * 上传整个目录到对象存储
     *
     * @param bucket 存储桶名称
     * @param objectPrefix 对象键前缀
     * @param sourceDirectory 源目录路径
     * @return 上传后的访问 URL（通常指向 index.html）
     */
    String uploadDirectory(String bucket, String objectPrefix, Path sourceDirectory);

    /**
     * 上传单个文件到对象存储
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键（存储路径）
     * @param sourceFile 源文件路径
     * @return 上传后的访问 URL
     */
    String uploadFile(String bucket, String objectKey, Path sourceFile);

    /**
     * 创建预签名下载 URL
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     * @return 预签名下载 URL
     */
    String createPresignedGetUrl(String bucket, String objectKey);

    /**
     * 获取对象的输入流
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     * @return 对象内容的输入流
     */
    InputStream getObject(String bucket, String objectKey);

    /**
     * 删除指定对象
     *
     * @param bucket 存储桶名称
     * @param objectKey 对象键
     */
    void deleteObject(String bucket, String objectKey);
}