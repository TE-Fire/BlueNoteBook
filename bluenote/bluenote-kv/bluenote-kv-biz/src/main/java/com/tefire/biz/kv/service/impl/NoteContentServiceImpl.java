package com.tefire.biz.kv.service.impl;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tefire.biz.kv.domain.dataobject.NoteContentDO;
import com.tefire.biz.kv.domain.repository.NoteContentRepository;
import com.tefire.biz.kv.enums.ResponseCodeEnum;
import com.tefire.biz.kv.service.NoteContentService;
import com.tefire.framework.common.exception.BizException;
import com.tefire.framework.common.response.Response;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.DeleteNoteContentReqDTO;
import com.tefire.kv.dto.req.FindNoteContentReqDTO;
import com.tefire.kv.dto.rsp.FindNoteContentRspDTO;

import jakarta.annotation.Resource;

@Service
public class NoteContentServiceImpl implements NoteContentService {
    

    @Resource
    private NoteContentRepository noteContentRepository;

    @Override
    public Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO) {
        String uuid = addNoteContentReqDTO.getUuid();
        String content = addNoteContentReqDTO.getContent();

        NoteContentDO noteContentDO = NoteContentDO.builder()
                        .id(UUID.fromString(uuid)) 
                        .content(content)
                        .build();

        noteContentRepository.save(noteContentDO);

        return Response.success();
    }

    @Override
    public Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO) {
        String uuid = findNoteContentReqDTO.getUuid();

        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString(uuid));

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

    @Override
    public Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO) {
        String uuid = deleteNoteContentReqDTO.getUuid();

        noteContentRepository.deleteById(UUID.fromString(uuid));

        return Response.success();
    }
}
