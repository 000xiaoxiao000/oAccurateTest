package com.oAT.web.config;

import com.oAT.web.control.freeMarke.ArrayToStringFunction;
import com.oAT.web.control.freeMarke.BeforeTimeFormat;
import com.oAT.web.control.freeMarke.SqlFormatFunction;
import freemarker.template.TemplateModelException;
import org.springframework.beans.factory.InitializingBean;

@org.springframework.context.annotation.Configuration
public class FreeMarkerFunctionConfig implements InitializingBean {

    private final freemarker.template.Configuration freeMarkerConfiguration;

    public FreeMarkerFunctionConfig(freemarker.template.Configuration freeMarkerConfiguration) {
        this.freeMarkerConfiguration = freeMarkerConfiguration;
    }

    @Override
    public void afterPropertiesSet() throws TemplateModelException {
        freeMarkerConfiguration.setSharedVariable("beforeTime", new BeforeTimeFormat());
        freeMarkerConfiguration.setSharedVariable("arrayToString", new ArrayToStringFunction());
        freeMarkerConfiguration.setSharedVariable("sqlFormat", new SqlFormatFunction());
    }
}
