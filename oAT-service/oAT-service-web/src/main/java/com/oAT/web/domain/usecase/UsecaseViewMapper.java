package com.oAT.web.domain.usecase;

import com.oAT.web.common.DateUtil;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.UsecaseDetailVo;
import com.oAT.web.service.entity.UsecaseDirectoryVo;
import com.oAT.web.service.entity.UsecaseVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

@Service
public class UsecaseViewMapper {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private CaseCenterRepository centerRepository;

    @Autowired
    private SystemSnapshotRepository systemSnapshotRepository;

    public UsecaseDetailVo convertUsecaseDetail(CaseCenterIndex caseCenterIndex) {
        UsecaseDetailVo detailVo = new UsecaseDetailVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), detailVo);
        detailVo.setId(caseCenterIndex.getId());
        detailVo.setCreateTime(caseCenterIndex.getCreateTime());
        detailVo.setUpdateTime(caseCenterIndex.getUpdateTime());
        return detailVo;
    }

    public UsecaseDirectoryVo convertDirectory(CaseCenterIndex index) {
        UsecaseDirectoryVo vo = new UsecaseDirectoryVo();
        BeanUtils.copyProperties(index.getDirectory(), vo);
        vo.setId(index.getId());
        vo.setUpdateTime(index.getUpdateTime());
        vo.setUpdateTimeText(formatDateTime(index.getUpdateTime()));
        vo.setUpdateTimeRelativeText(formatRelativeTime(index.getUpdateTime()));
        return vo;
    }

    public UsecaseVo convertUsecase(CaseCenterIndex caseCenterIndex) {
        UsecaseVo usecaseVo = new UsecaseVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
        usecaseVo.setId(caseCenterIndex.getId());
        usecaseVo.setCreateTime(caseCenterIndex.getCreateTime());
        usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
        usecaseVo.setUpdateTimeText(formatDateTime(caseCenterIndex.getUpdateTime()));
        usecaseVo.setSnapshotCount(countExistingSnapshots(usecaseVo.getSnapshots()));
        usecaseVo.setSystemSnapshotCount(countExistingSystemSnapshots(usecaseVo.getSystemSnapshots()));
        usecaseVo.setCoverageFootprintCount(ObjectUtils.isEmpty(usecaseVo.getCoverageFootprints()) ? 0 : usecaseVo.getCoverageFootprints().length);
        return usecaseVo;
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    private String formatRelativeTime(Date date) {
        if (date == null) {
            return "-";
        }
        return DateUtil.timeDifference(date) + "前";
    }

    private int countExistingSnapshots(String[] snapshotIds) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return 0;
        }
        int count = 0;
        for (CaseCenterIndex index : centerRepository.findAllById(Arrays.asList(snapshotIds))) {
            if (index != null && index.getSnapshot() != null) {
                count++;
            }
        }
        return count;
    }

    private int countExistingSystemSnapshots(String[] systemSnapshotIds) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return 0;
        }
        int count = 0;
        for (SystemSnapshot snapshot : systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds))) {
            if (snapshot != null) {
                count++;
            }
        }
        return count;
    }
}
