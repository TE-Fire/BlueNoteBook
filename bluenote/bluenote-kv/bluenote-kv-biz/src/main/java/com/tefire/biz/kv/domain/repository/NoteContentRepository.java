package com.tefire.biz.kv.domain.repository;

import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;

import com.tefire.biz.kv.domain.dataobject.NoteContentDO;

public interface NoteContentRepository extends CassandraRepository<NoteContentDO, UUID> {
    
}
