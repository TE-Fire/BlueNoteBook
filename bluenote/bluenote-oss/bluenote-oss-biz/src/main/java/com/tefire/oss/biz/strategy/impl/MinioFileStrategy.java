package com.tefire.oss.biz.strategy.impl;

import org.springframework.web.multipart.MultipartFile;

import com.tefire.oss.biz.strategy.FileStrategy;

import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 10:56:43
 * @Description: 存储到minio
 */
@Slf4j
public class MinioFileStrategy implements FileStrategy{
    @Override
    public String uploadFile(MultipartFile file, String bucketName) {
        log.info("## 上传文件至 Minio ...");
        return null;
    }
}
