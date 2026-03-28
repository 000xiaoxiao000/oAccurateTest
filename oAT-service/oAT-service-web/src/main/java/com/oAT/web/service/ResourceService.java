package com.oAT.web.service;

import java.io.File;

public interface ResourceService {

    @Deprecated
    public String addResource(byte[] content);

    // 引用数加1
    @Deprecated
    void reference(String id);

    // 取消引用，引用数减1
    @Deprecated
    void cancelReference(String id);

    @Deprecated
    void removeResource(String id);


    File createCacheFile(String md5, String fileName);

    String getCachePath(String md5, String fileName);

    String getCacheRoot();

    String getGitCacheRoot();

    void checkAndCleanDiskSpace();
}
