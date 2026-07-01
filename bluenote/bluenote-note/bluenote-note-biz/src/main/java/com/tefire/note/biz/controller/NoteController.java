package com.tefire.note.biz.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tefire.framework.biz.operationlog.aspect.ApiOperationLog;
import com.tefire.framework.common.response.Response;
import com.tefire.note.biz.model.vo.FindNoteDetailReqVO;
import com.tefire.note.biz.model.vo.FindNoteDetailRspVO;
import com.tefire.note.biz.model.vo.PublishNoteReqVO;
import com.tefire.note.biz.service.NoteService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/note")
@Slf4j
public class NoteController {
    
    @Resource
    private NoteService noteService;

    @PostMapping(value = "/publish")
    @ApiOperationLog(description = "笔记发布")
    public Response<?> publishNote(@Validated @RequestBody PublishNoteReqVO publishNoteReqVO) {
        return noteService.publishNote(publishNoteReqVO);
    }

    @PostMapping(value = "/detail")
    @ApiOperationLog(description = "笔记详情")
    public Response<FindNoteDetailRspVO> findNoteDetail(@Validated @RequestBody FindNoteDetailReqVO findNoteDetailReqVO) {
        return noteService.findNoteDetail(findNoteDetailReqVO);
    }
}
