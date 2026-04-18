package com.oAT.web.control.freeMarke;

import com.oAT.web.common.DateUtil;
import freemarker.ext.beans.BeanModel;
import freemarker.template.SimpleDate;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;

public class BeforeTimeFormat implements TemplateMethodModelEx {

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        Assert.notEmpty(arguments, "参数不能为空");
        Date time = resolveDate(arguments.get(0));
        return DateUtil.timeDifference(time) + "前";
    }

    private Date resolveDate(Object value) throws TemplateModelException {
        if (value instanceof SimpleDate) {
            return ((SimpleDate) value).getAsDate();
        }
        if (value instanceof TemplateDateModel) {
            return ((TemplateDateModel) value).getAsDate();
        }
        if (value instanceof BeanModel) {
            Object wrappedObject = ((BeanModel) value).getWrappedObject();
            if (wrappedObject instanceof Date) {
                return (Date) wrappedObject;
            }
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        throw new TemplateModelException("必须传入日期类型参数");
    }
}

