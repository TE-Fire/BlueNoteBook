package com.tefire.oss.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.tefire.framework.common.response.Response;
import com.tefire.oss.FeignFormConfig.FeignFormConfig;
import com.tefire.oss.constant.ApiConstants;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-26 12:31:24
 * @Description: 用于调用oss-biz 服务的文件上传
 */
@FeignClient(name = ApiConstants.SERVICE_NAME, configuration = FeignFormConfig.class)
public interface FileFeignApi {

    String PREFIX = "/file";

    /**
     * 文件上传
     * 
     * @param file
     * @return
     */
    @PostMapping(value = PREFIX + "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Response<?> uploadFile(@RequestPart(value = "file") MultipartFile file);
}
