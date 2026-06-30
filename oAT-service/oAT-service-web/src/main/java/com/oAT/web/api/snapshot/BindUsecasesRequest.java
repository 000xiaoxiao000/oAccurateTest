package com.oAT.web.api.snapshot;

import java.util.List;

public class BindUsecasesRequest {
    private List<String> usecaseIds;

    public List<String> getUsecaseIds() { return usecaseIds; }
    public void setUsecaseIds(List<String> usecaseIds) { this.usecaseIds = usecaseIds; }
}
