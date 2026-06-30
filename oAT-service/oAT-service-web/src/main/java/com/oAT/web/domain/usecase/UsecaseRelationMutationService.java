package com.oAT.web.domain.usecase;

import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.Usecase;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsecaseRelationMutationService {

    private final CaseCenterRepository centerRepository;
    private final UsecaseRelationBuilderService usecaseRelationBuilderService;

    public UsecaseRelationMutationService(CaseCenterRepository centerRepository,
                                          UsecaseRelationBuilderService usecaseRelationBuilderService) {
        this.centerRepository = centerRepository;
        this.usecaseRelationBuilderService = usecaseRelationBuilderService;
    }

    public void bindSnapshotToUsecases(String projectId, String operator, String snapshotId, String[] usecaseIds) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(snapshotId, "snapshotId不能为空");
        bindRelations(projectId, operator, usecaseIds, Usecase::getSnapshots, Usecase::setSnapshots, snapshotId);
    }

    public void batchAppendSnapshotsToUsecases(String projectId, String operator, String[] snapshotIds, String[] usecaseIds) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.isTrue(!ObjectUtils.isEmpty(snapshotIds), "snapshotIds不能为空");
        Assert.isTrue(!ObjectUtils.isEmpty(usecaseIds), "usecaseIds不能为空");
        LinkedHashSet<String> normalizedSnapshotIds = normalizeIds(snapshotIds);
        LinkedHashSet<String> normalizedUsecaseIds = normalizeIds(usecaseIds);
        if (normalizedSnapshotIds.isEmpty() || normalizedUsecaseIds.isEmpty()) {
            return;
        }
        for (String usecaseId : normalizedUsecaseIds) {
            CaseCenterIndex index = centerRepository.findById(usecaseId).orElse(null);
            if (index == null || index.getUsecase() == null) {
                continue;
            }
            Usecase usecase = index.getUsecase();
            Assert.isTrue(projectId.equals(usecase.getProjectId()), "the usecase not belong to project Id=" + projectId);
            LinkedHashSet<String> snapshotSet = normalizeIds(usecase.getSnapshots());
            if (!snapshotSet.addAll(normalizedSnapshotIds)) {
                continue;
            }
            usecase.setSnapshots(snapshotSet.toArray(new String[0]));
            rebuildSrcStack(usecase);
            touchUsecase(index, operator);
            centerRepository.save(index);
        }
    }

    public void bindSystemSnapshotToUsecases(String projectId, String operator, String systemSnapshotId, String[] usecaseIds) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(systemSnapshotId, "systemSnapshotId不能为空");
        bindRelations(projectId, operator, usecaseIds, Usecase::getSystemSnapshots, Usecase::setSystemSnapshots, systemSnapshotId);
    }

    public void bindCoverageFootprintToUsecases(String projectId, String operator, String footprintKey, String[] usecaseIds) {
        Assert.hasText(projectId, "projectId不能为空");
        Assert.hasText(footprintKey, "footprintKey不能为空");
        bindRelations(projectId, operator, usecaseIds, Usecase::getCoverageFootprints, Usecase::setCoverageFootprints, footprintKey);
    }

    public void removeCoverageFootprintRelation(String footprintKey) {
        removeRelation(footprintKey, Usecase::getCoverageFootprints, Usecase::setCoverageFootprints);
    }

    public int rebuildUsecaseSearchData(String projectId, String operator) {
        Assert.hasText(projectId, "projectId不能为空");
        List<CaseCenterIndex> list = centerRepository.findByUsecase_ProjectId(projectId);
        int updated = 0;
        for (CaseCenterIndex index : list) {
            if (index == null || index.getUsecase() == null) {
                continue;
            }
            Usecase current = index.getUsecase();
            if (ObjectUtils.isEmpty(current.getSnapshots()) && ObjectUtils.isEmpty(current.getSystemSnapshots())) {
                continue;
            }
            rebuildSrcStack(current);
            touchUsecase(index, operator);
            centerRepository.save(index);
            updated++;
        }
        return updated;
    }

    public void removeSnapshotRelation(String snapshotId) {
        removeRelation(snapshotId, Usecase::getSnapshots, Usecase::setSnapshots);
    }

    public void removeSystemSnapshotRelation(String systemSnapshotId) {
        removeRelation(systemSnapshotId, Usecase::getSystemSnapshots, Usecase::setSystemSnapshots);
    }

    private void bindRelations(String projectId, String operator, String[] usecaseIds,
                               Function<Usecase, String[]> getter,
                               BiConsumer<Usecase, String[]> setter,
                               String relationId) {
        LinkedHashSet<String> selectedIds = normalizeIds(usecaseIds);
        for (CaseCenterIndex index : centerRepository.findByUsecaseIsNotNull()) {
            if (index == null || index.getUsecase() == null) {
                continue;
            }
            Usecase usecase = index.getUsecase();
            if (!projectId.equals(usecase.getProjectId())) {
                continue;
            }
            LinkedHashSet<String> relationIds = normalizeIds(getter.apply(usecase));
            boolean changed = selectedIds.contains(index.getId())
                    ? relationIds.add(relationId)
                    : relationIds.remove(relationId);
            if (!changed) {
                continue;
            }
            setter.accept(usecase, relationIds.toArray(new String[0]));
            rebuildSrcStack(usecase);
            touchUsecase(index, operator);
            centerRepository.save(index);
        }
    }

    private void removeRelation(String relationId,
                                Function<Usecase, String[]> getter,
                                BiConsumer<Usecase, String[]> setter) {
        if (!StringUtils.hasText(relationId)) {
            return;
        }
        for (CaseCenterIndex index : centerRepository.findByUsecaseIsNotNull()) {
            Usecase usecase = index.getUsecase();
            if (usecase == null || ObjectUtils.isEmpty(getter.apply(usecase))) {
                continue;
            }
            String[] original = getter.apply(usecase);
            String[] filtered = Arrays.stream(original)
                    .filter(id -> !relationId.equals(id))
                    .toArray(String[]::new);
            if (filtered.length == original.length) {
                continue;
            }
            setter.accept(usecase, filtered);
            index.setUpdateTime(new Date());
            centerRepository.save(index);
        }
    }

    private void rebuildSrcStack(Usecase usecase) {
        if (ObjectUtils.isEmpty(usecase.getSnapshots()) && ObjectUtils.isEmpty(usecase.getSystemSnapshots())) {
            usecase.setSrcStack(null);
            return;
        }
        Usecase rebuilt = usecaseRelationBuilderService.buildUsecaseRelations(usecase.getSnapshots(), usecase.getSystemSnapshots());
        usecase.setSrcStack(rebuilt.getSrcStack());
    }

    private void touchUsecase(CaseCenterIndex index, String operator) {
        Usecase usecase = index.getUsecase();
        if (StringUtils.hasText(operator)) {
            usecase.setLastUpdateAuthor(operator);
            List<String> authors = new ArrayList<>(Arrays.asList(Optional.ofNullable(usecase.getAuthors()).orElse(new String[0])));
            if (!authors.contains(operator)) {
                authors.add(operator);
            }
            usecase.setAuthors(authors.toArray(new String[0]));
        }
        index.setUpdateTime(new Date());
    }

    private LinkedHashSet<String> normalizeIds(String[] ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(ids)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
