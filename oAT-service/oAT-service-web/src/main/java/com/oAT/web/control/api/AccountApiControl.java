package com.oAT.web.control.api;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.exceptions.UserOperationException;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.UserVo;
import javax.servlet.http.HttpSession;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/account")
public class AccountApiControl {

    private final UserService userService;

    public AccountApiControl(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResultNotified<FrontendContextApiControl.UserSummary> profile(@SessionAttribute UserVo user) {
        return new ResultNotified<>(true, "获取账户信息成功", toUserSummary(loadEffectiveUser(user)));
    }

    @PostMapping("/profile")
    public ResultNotified<FrontendContextApiControl.UserSummary> updateProfile(@SessionAttribute UserVo user,
                                                                               @RequestBody UpdateProfileRequest request,
                                                                               HttpSession session) {
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getName(), "用户名称不能为空");
        Assert.hasText(request.getEmail(), "邮箱地址不能为空");

        UserVo updated = loadEffectiveUser(user);
        updated.setName(request.getName().trim());
        updated.setEmail(request.getEmail().trim());
        updated.setNickname(request.getNickname());
        updated.setPhone(request.getPhone());
        updated.setReadme(request.getReadme());
        userService.updateUser(updated);

        UserVo latest = userService.getUser(user.getId());
        session.setAttribute("user", latest);
        return new ResultNotified<>(true, "账户信息更新成功", toUserSummary(latest));
    }

    @PostMapping("/password")
    public ResultNotified<String> updatePassword(@SessionAttribute UserVo user,
                                                 @RequestBody UpdatePasswordRequest request,
                                                 HttpSession session) {
        Assert.notNull(request, "请求体不能为空");
        Assert.hasText(request.getOldPassword(), "旧密码不能为空");
        Assert.hasText(request.getNewPassword(), "新密码不能为空");
        Assert.hasText(request.getNewPasswordConfirm(), "确认密码不能为空");
        Assert.isTrue(request.getNewPassword().equals(request.getNewPasswordConfirm()), "两次密码输入不一致");
        try {
            userService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
            session.setAttribute("user", userService.getUser(user.getId()));
            return new ResultNotified<>(true, "密码修改成功", "OK");
        } catch (UserOperationException e) {
            ResultNotified<String> result = new ResultNotified<>(false, "密码修改失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    private UserVo loadEffectiveUser(UserVo user) {
        UserVo latest = userService.getUser(user.getId());
        return latest != null ? latest : user;
    }

    private FrontendContextApiControl.UserSummary toUserSummary(UserVo user) {
        FrontendContextApiControl.UserSummary summary = new FrontendContextApiControl.UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    public static class UpdateProfileRequest {
        private String name;
        private String nickname;
        private String email;
        private String phone;
        private String readme;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getReadme() {
            return readme;
        }

        public void setReadme(String readme) {
            this.readme = readme;
        }
    }

    public static class UpdatePasswordRequest {
        private String oldPassword;
        private String newPassword;
        private String newPasswordConfirm;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getNewPasswordConfirm() {
            return newPasswordConfirm;
        }

        public void setNewPasswordConfirm(String newPasswordConfirm) {
            this.newPasswordConfirm = newPasswordConfirm;
        }
    }
}
