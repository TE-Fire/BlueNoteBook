package com.tefire.oss.biz.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tefire.framework.common.response.Response;
import com.tefire.oss.biz.service.FileService;
import com.tefire.oss.biz.strategy.FileStrategy;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileServiceImpl implements FileService {
    
    @Resource
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "bluenote";
    @Override
    public Response<?> uploadFile(MultipartFile file) {
        // 上传文件到
        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Response.success(url);
    }
}
