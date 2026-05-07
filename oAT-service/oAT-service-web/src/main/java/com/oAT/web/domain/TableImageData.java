package com.oAT.web.domain;

public class TableImageData extends ImageData{

    public String dataBaseType;
    public String database;
    public String[] sqlContents;

    public TableImageData(String id) {
        super(id);
    }

}
