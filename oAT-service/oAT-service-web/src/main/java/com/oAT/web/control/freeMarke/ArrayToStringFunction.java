package com.oAT.web.control.freeMarke;

import freemarker.template.SimpleSequence;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.springframework.util.Assert;

import java.util.List;

public class ArrayToStringFunction implements TemplateMethodModelEx{

    @Override
    public Object exec(List list) throws TemplateModelException {
        Assert.isTrue(!list.isEmpty(), "至少要包含一个参数");
        Assert.isTrue(list.get(0) instanceof SimpleSequence, "第一个参数必须是数组类型");
        SimpleSequence sequence = (SimpleSequence) list.get(0);
        String split = list.size() > 1 ? list.get(1).toString() : ",";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < sequence.size(); i++) {
            if (i > 0) {
                sb.append(split);
            }
            sb.append(sequence.get(i).toString());
        }
        return sb.toString();
    }

}
