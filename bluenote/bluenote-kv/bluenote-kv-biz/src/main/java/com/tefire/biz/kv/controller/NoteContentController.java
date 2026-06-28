package com.tefire.biz.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.biz.kv.service.NoteContentService;
import com.tefire.framework.common.response.Response;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.DeleteNoteContentReqDTO;
import com.tefire.kv.dto.req.FindNoteContentReqDTO;
import com.tefire.kv.dto.rsp.FindNoteContentRspDTO;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 14:32:44
 * @Description: 键值服务
 */
@RestController
@RequestMapping("/kv")
public class NoteContentController {
    
    @Resource
    private NoteContentService noteContentService;


    @PostMapping("/note/content/add")
    public Response<?> addNoteContent(@Validated @RequestBody AddNoteContentReqDTO addNoteContentReqDTO) {
        return noteContentService.addNoteContent(addNoteContentReqDTO);
    }

    @PostMapping(value = "/note/content/find")
    public Response<FindNoteContentRspDTO> findNoteContent(@Validated @RequestBody FindNoteContentReqDTO findNoteContentReqDTO) {
        return noteContentService.findNoteContent(findNoteContentReqDTO);
    }

    @PostMapping(value = "/note/content/delete")
    public Response<?> deleteNoteContent(@Validated @RequestBody DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        return noteContentService.deleteNoteContent(deleteNoteContentReqDTO);
    }
}
