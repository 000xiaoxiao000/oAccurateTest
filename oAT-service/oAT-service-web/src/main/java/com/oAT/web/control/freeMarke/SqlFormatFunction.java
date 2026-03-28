package com.oAT.web.control.freeMarke;

import com.alibaba.druid.sql.SQLUtils;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.springframework.util.Assert;

import java.util.List;

public class SqlFormatFunction implements TemplateMethodModelEx{

    @Override
    public Object exec(List list) throws TemplateModelException {
        Assert.isTrue(list.size() == 2,"必须包含 sql databaseType 两个参数");
        String sql = (String) list.get(0).toString();
        String type = (String) list.get(1).toString();
        return SQLUtils.format(sql, type);
    }

}
