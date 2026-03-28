package com.oAT.web.control.freeMarke;

import com.oAT.web.common.DateUtil;
import freemarker.template.SimpleDate;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.springframework.util.Assert;

import java.util.List;

public class BeforeTimeFormat implements TemplateMethodModelEx{

    @Override
    public Object exec(List arguments) throws TemplateModelException {
        Assert.notEmpty(arguments, "参数不能为空");
        Assert.isTrue(arguments.get(0) instanceof SimpleDate, "必须传入日期类型参数");
        SimpleDate time = (SimpleDate) arguments.get(0);
        // 与当前时间比较差异
        return DateUtil.timeDifference(time.getAsDate())+"前";
    }

}
