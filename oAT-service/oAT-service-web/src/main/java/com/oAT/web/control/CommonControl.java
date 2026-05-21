package com.oAT.web.control;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@Controller
public class CommonControl implements ErrorController{
    private static final String ERROR_PATH = "/error";

    @RequestMapping("/error/404")
    public String open404View(String errorMessage, Model model) {
        return "forward:/index.html";
    }

    @RequestMapping(ERROR_PATH)
    public String error(Model model, HttpServletRequest request) {
        return "forward:/index.html";
    }

    // Spring Boot 2.7+: getErrorPath() 已废弃，通过配置 server.error.path 指定错误路径

}
