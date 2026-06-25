package com.tefire.oss.biz.service;

import org.springframework.web.multipart.MultipartFile;

import com.tefire.framework.common.response.Response;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-25 11:07:16
 * @Description: 文件业务接口
 */
public interface FileService {
    
    /**
     * 上传文件
     * 
     * @param file
     * @return
     */
    Response<?> uploadFile(MultipartFile file);
}
