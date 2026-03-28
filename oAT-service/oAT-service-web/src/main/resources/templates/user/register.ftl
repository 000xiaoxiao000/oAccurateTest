<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>注册用户</title>
     <#include "../common.ftl">
</head>
<body>

<div class="ui middle aligned center aligned grid" style="width: 100%;height: 100%;">
    <div class="column" style="width: 550px">
        <h2 class="ui teal image header">
            <img src="/images/logo.png" class="ui image">
            <div class="content">
                注册新账户
            </div>
        </h2>
        <form class="ui form" method="post" action="/doRegister">
            <div class="ui left aligned  segment">
                <div class="field required">
                    <label>用户名 <span style="color: #aaaaaa">(只能包含数字、字母、下划线)</span>：</label>
                    <div class="ui left  input">
                        <input type="text" name="name">
                    </div>
                </div>
                <div class="field ">
                    <label>自定义名称
                        <span style="color: #aaaaaa">(用大家所熟悉你的名字)</span>
                        ：</label>
                    <div class="ui left  input">
                        <input type="text" name="nickname">
                    </div>
                </div>
                <div class="field required">
                    <label>邮箱：</label>
                    <div class="ui left  input">
                        <input type="text" name="email">
                    </div>
                </div>
                <div class="field required">
                    <label>密码：</label>
                    <div class="ui left  input">
                        <input type="password" name="password">
                    </div>
                </div>

                <div class="field required">
                    <label>确认密码：</label>
                    <div class="ui left  input">
                        <input type="password" name="againPassword">
                    </div>
                </div>
                <input class="ui fluid large teal submit button" type="submit" value="注册">
                <div class="ui fluid large  button" style="margin-top: 6px">取消</div>
            </div>
            <div class="ui error message"></div>
        </form>


    </div>
</div>
</body>
</html>