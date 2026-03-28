package com.oAT.web.esDao.entity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public interface StandardDate {

    String dateFormat = "yyyy-MM-dd HH:mm:ss,SSS";

    default  String currentTimeToString() {
        return new SimpleDateFormat(dateFormat).format(new Date());
    }

    default Date parse(String standardTime) {
        try {
            return new SimpleDateFormat(dateFormat).parse(standardTime);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

}
