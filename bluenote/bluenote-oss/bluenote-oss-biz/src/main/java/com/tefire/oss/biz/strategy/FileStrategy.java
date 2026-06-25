package com.tefire.oss.biz.strategy;

import org.springframework.web.multipart.MultipartFile;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 10:55:07
 * @Description: 文件策略接口
 */
public interface FileStrategy {
    /**
     * 文件上传
     * 
     * @param file
     * @param bucketName
     * @return
     */
    String uploadFile(MultipartFile file, String bucketName);
}
