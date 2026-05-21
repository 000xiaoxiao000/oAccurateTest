package com.oAT.web.control;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.exceptions.UserOperationException;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.UserRegisterVo;
import com.oAT.web.service.entity.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import javax.servlet.http.HttpSession;

@Controller
public class UserControl {
    static final Logger logger = LoggerFactory.getLogger(UserControl.class);

    @Autowired
    UserService userService;

    @RequestMapping("/register")
    public String openRegisterView() {
        return "forward:/index.html";
    }

    @RequestMapping("/login")
    public String openLoginView(String redirect, Model model) {
        model.addAttribute("redirect", redirect);
        return "forward:/index.html";
    }

    @RequestMapping("/legacy/login")
    public String openLegacyLoginView(String redirect, Model model) {
        model.addAttribute("redirect", redirect);
        return "redirect:/login";
    }

    @RequestMapping("/doRegister")
    public String doRegister(UserRegisterVo user, Model model) {
        userService.doRegister(user);
        model.addAttribute("newUser", user);
        return "redirect:/login?registered=1";
    }

    @RequestMapping("/doLogin")
    public String doLogin(HttpSession session, String nameOrEmail, String password, String redirect, Model model) {
        try {
            UserVo user = userService.doLogin(nameOrEmail, nameOrEmail, password);
            session.setAttribute("user", user);
            logger.info("当前用户已登录，登录的用户名为：" + user.getName());
        } catch (UserOperationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "redirect:/login?error=" + org.springframework.web.util.UriUtils.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (StringUtils.hasText(redirect)) {
            return "redirect:" + LoginInterceptor.normalizeRedirect(redirect);
        }
        return "redirect:/projects";
    }

    @RequestMapping("/user/info")
    public String openUserInfoView(@SessionAttribute UserVo user, Model model) {
        return "redirect:/account";
    }

    @RequestMapping("/user/password")
    public String openPasswordView() {
        return "redirect:/account?tab=password";
    }

    @RequestMapping(value = "/user/doUpdatePassword", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<String> UpdatePassword(@SessionAttribute UserVo user, String oldPassword, String newPassword, String newPasswordConfirm,
                                                 Model model) {
        if (!newPassword.equals(newPasswordConfirm)) {
            ResultNotified r = new ResultNotified(false, "密码修改失败");
            r.setErrorMessage("两次密码输入不一至");
            return r;
        }
        try {
            userService.changePassword(user.getId(), oldPassword, newPassword);
        } catch (UserOperationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            logger.error("密码修改失败", e);
            ResultNotified r = new ResultNotified(false, "密码修改失败");
            r.setErrorMessage(e.getMessage());
            return r;
        }
        return new ResultNotified(true, "密码修改成功");
    }


    @RequestMapping("/user/doUpdateInfo")
    public String updateUserInfo(@SessionAttribute UserVo user, UserVo userVo, Model model) {
        userVo.setId(user.getId());
        userService.updateUser(userVo);
        return "redirect:/account";
    }


    @RequestMapping("/user/logout")
    public String updateUserInfo(HttpSession session, @SessionAttribute UserVo user) {
        session.removeAttribute("user");
        return "redirect:/login";
    }

}
