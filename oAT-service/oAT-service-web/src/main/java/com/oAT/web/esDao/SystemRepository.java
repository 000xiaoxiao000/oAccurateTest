package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.SystemIndex;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SystemRepository extends ElasticsearchRepository<SystemIndex, String>{

    List<SystemIndex> findByUserNameOrUserEmail(String name, String email);
    List<SystemIndex> findByProjectMember_MemberId(String memberId,Pageable pageable);
    List<SystemIndex> findByAppCreateProjectIdOrAppRange(String createProjectId, String range);

    List<SystemIndex> findByLabelGroup_ProjectidAndLabelGroup_Type(String projectId, String type);

    List<SystemIndex> findByProjectMember_ProjectId(String projectId, Pageable pageable);

    List<SystemIndex> findByProjectMember_ProjectIdAndProjectMember_MemberId(String projectId, String memberId);

    List<SystemIndex> findByType(String type,Pageable pageable);
    List<SystemIndex> findBySystemLog_ProjectId(String projectId,Pageable pageable);

}
