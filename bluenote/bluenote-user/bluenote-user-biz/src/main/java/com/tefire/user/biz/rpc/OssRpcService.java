package com.tefire.user.biz.rpc;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.tefire.framework.common.response.Response;
import com.tefire.oss.api.FileFeignApi;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 20:52:13
 * @Description: 对象存储服务调用
 */
@Component
public class OssRpcService {
    
    @Resource FileFeignApi fileFeignApi;

    public String uploadFile(MultipartFile file) {
        // 调用对象存储服务上的文件上传
        Response<?> response = fileFeignApi.uploadFile(file);

        if (!response.isSuccess()) {
            return null;
        }

        // 返回图片访问链接
        return (String) response.getData();
    }
}
