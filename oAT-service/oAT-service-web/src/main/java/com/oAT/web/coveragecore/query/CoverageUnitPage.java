package com.oAT.web.coveragecore.query;

import com.oAT.web.coveragecore.model.CoverageUnit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CoverageUnitPage implements Serializable {
    private List<CoverageUnit> content = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public List<CoverageUnit> getContent() { return content; }
    public void setContent(List<CoverageUnit> content) { this.content = content == null ? new ArrayList<>() : content; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
