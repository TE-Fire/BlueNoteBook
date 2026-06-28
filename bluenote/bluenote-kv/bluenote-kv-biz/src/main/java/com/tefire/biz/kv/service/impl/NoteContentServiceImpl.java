package com.tefire.biz.kv.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tefire.biz.enums.ResponseCodeEnum;
import com.tefire.biz.kv.domain.dataobject.NoteContentDO;
import com.tefire.biz.kv.domain.repository.NoteContentRepository;
import com.tefire.biz.kv.service.NoteContentService;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.FindNoteContentReqDTO;
import com.tefire.kv.dto.rsp.FindNoteContentRspDTO;

import jakarta.annotation.Resource;

@Service
public class NoteContentServiceImpl implements NoteContentService {
    

    @Resource
    private NoteContentRepository noteContentRepository;

    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        Long noteId = addNoteContentReqDTO.getNoteId();
        String content = addNoteContentReqDTO.getContent();

        NoteContentDO noteContentDO = NoteContentDO.builder()
                        .id(UUID.randomUUID()) // TODO: 暂时用 UUID, 目的是为了下一章讲解压测，不用动态传笔记 ID。后续改为笔记服务传过来的笔记 ID
                        .content(content)
                        .build();

        noteContentRepository.save(noteContentDO);

        return Response.success();
    }

    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String noteId = findNoteContentReqDTO.getNoteId();

        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(noteId));

        if (!optional.isPresent()) {
            throw new BizException(ResponseCodeEnum.NOTE_CONTENT_NOT_FOUND);
        }

        NoteContentDO noteContentDO = optional.get();

        FindNoteContentRspDTO findNoteContentRspDTO = FindNoteContentRspDTO.builder()
                        .noteId(noteContentDO.getId())
                        .content(noteContentDO.getContent())
                        .build();

        return Response.success(findNoteContentRspDTO);
    }
}
