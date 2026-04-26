<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>设置-标签管理</title>
    <#include "../common.ftl">
    <style>
        .project-settings-page {
            margin-top: 18px;
            margin-bottom: 42px;
        }

        .project-settings-breadcrumb {
            margin: 6px auto 18px !important;
            color: #6b7785;
        }

        .project-settings-layout {
            display: grid;
            grid-template-columns: 280px minmax(0, 1fr);
            gap: 20px;
            align-items: start;
        }

        .project-settings-side,
        .project-settings-main {
            background: #fff;
            border: 1px solid #e7edf5;
            border-radius: 16px;
            box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
        }

        .project-settings-side {
            padding: 18px;
        }

        .project-settings-main {
            overflow: hidden;
        }

        .project-settings-hero {
            padding: 26px 28px;
            background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 55%, #f9fbfd 100%);
            border-bottom: 1px solid #e6eef7;
        }

        .project-settings-hero-label {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 999px;
            background: rgba(33, 133, 208, 0.08);
            color: #1d6fa5;
            font-size: 12px;
            font-weight: 600;
            letter-spacing: 0.04em;
            margin-bottom: 14px;
        }

        .project-settings-hero-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 28px;
            font-weight: 700;
        }

        .project-settings-hero-desc {
            margin: 0;
            color: #617080;
            line-height: 1.8;
            max-width: 780px;
        }

        .project-settings-meta {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 14px;
            margin-top: 20px;
        }

        .project-settings-meta-card {
            background: rgba(255, 255, 255, 0.82);
            border: 1px solid #e4edf7;
            border-radius: 14px;
            padding: 14px 16px;
        }

        .project-settings-meta-label {
            color: #8a97a6;
            font-size: 12px;
            margin-bottom: 6px;
        }

        .project-settings-meta-value {
            color: #1f2937;
            font-size: 18px;
            font-weight: 700;
            line-height: 1.4;
            word-break: break-word;
        }

        .project-settings-content {
            padding: 28px;
        }

        .project-settings-section-title {
            margin: 0 0 8px;
            color: #1f2937;
            font-size: 22px;
            font-weight: 700;
        }

        .project-settings-section-desc {
            margin: 0 0 24px;
            color: #6b7785;
            line-height: 1.75;
        }

        .project-label-card {
            border: 1px solid #e7edf5;
            border-radius: 16px;
            padding: 22px;
            background: #fff;
        }

        .project-label-card + .project-label-card {
            margin-top: 18px;
        }

        .project-label-card-title {
            margin: 0 0 10px;
            font-size: 18px;
            font-weight: 700;
            color: #1f2937;
        }

        .project-label-card-desc {
            margin: 0 0 16px;
            color: #6b7785;
            line-height: 1.7;
        }

        .project-label-list {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-bottom: 18px;
        }

        .project-label-list .ui.label {
            margin: 0;
            border-radius: 999px;
            padding: 10px 14px;
        }

        .project-label-empty {
            color: #94a3b8;
            margin-bottom: 18px;
        }

        .project-label-form {
            display: flex;
            align-items: center;
            gap: 12px;
            flex-wrap: wrap;
        }

        .project-label-form .ui.input {
            min-width: 260px;
        }

        .project-label-form .ui.dropdown.label {
            border-radius: 0 10px 10px 0 !important;
        }

        .project-label-form .ui.button {
            border-radius: 10px;
        }

        @media only screen and (max-width: 960px) {
            .project-settings-layout {
                grid-template-columns: 1fr;
            }
        }

        @media only screen and (max-width: 767px) {
            .project-settings-hero,
            .project-settings-side,
            .project-settings-content {
                padding: 22px 20px !important;
            }

            .project-label-form {
                flex-direction: column;
                align-items: stretch;
            }

            .project-label-form .ui.input,
            .project-label-form .ui.button {
                width: 100%;
            }
        }
    </style>
</head>
<body>

<!--头部菜单 引入-->
<#assign settingItemActive="active">
<#include "../projectHeader.ftl">

<div class="ui container project-settings-page">
    <div class="ui small breadcrumb project-settings-breadcrumb">
        <a class="section" href="/p/${project.id}/home">${project.name}</a>
        <span class="divider">/</span>
        <div class="active section">标签管理</div>
    </div>

    <div class="project-settings-layout">
        <div class="project-settings-side">
            <#assign settingsLabelActive="active"/>
            <#assign labelItemActive="active"/>
            <#assign loginRole=loginNameRole />
            <#include "LeftNavigationMenu.ftl">
        </div>

        <div class="project-settings-main">
            <div class="project-settings-hero">
                <div class="project-settings-hero-label">
                    <i class="tags icon"></i>
                    标签体系维护
                </div>
                <h1 class="project-settings-hero-title">标签管理</h1>
                <p class="project-settings-hero-desc">
                    为用例和快照维护统一标签体系，便于分类筛选、协作检索和后续批量管理。
                </p>
                <div class="project-settings-meta">
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">用例标签数</div>
                        <div class="project-settings-meta-value">${usecaseLables?size}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">快照标签数</div>
                        <div class="project-settings-meta-value">${snapshotLables?size}</div>
                    </div>
                    <div class="project-settings-meta-card">
                        <div class="project-settings-meta-label">当前权限</div>
                        <div class="project-settings-meta-value"><#if loginNameRole == "visitor">访客<#else>${loginNameRole}</#if></div>
                    </div>
                </div>
            </div>

            <div id="center-content" class="project-settings-content">
                <h2 class="project-settings-section-title">标签列表与维护</h2>
                <p class="project-settings-section-desc">标签颜色用于提升视觉辨识度。非访客角色可新增和删除标签。</p>

                <div class="project-label-card">
                    <h3 class="project-label-card-title">用例标签</h3>
                    <p class="project-label-card-desc">用于标记测试用例的业务分类、优先级或维护归属。</p>
                    <#if usecaseLables?size gt 0>
                        <div class="project-label-list">
                            <#list usecaseLables as lab>
                                <div class="ui ${lab.color} label" style="margin-top: 0">${lab.name}
                                    <a href="/p/${project.id}/label/delete?type=usecase&name=${lab.name}" data-tooltip="删除标签" data-position="top left" data-inverted="">
                                        <i class="ui icon delete"></i>
                                    </a>
                                </div>
                            </#list>
                        </div>
                    <#else>
                        <div class="project-label-empty">暂无用例标签。</div>
                    </#if>

                    <form class="ui form project-label-form" action="/p/${project.id}/label/add">
                        <input type="hidden" name="type" value="usecase">
                        <div class="ui small right labeled input">
                            <input type="text" name="name" placeholder="标签名称">
                            <div class="ui dropdown label">
                                <input type="hidden" name="color">
                                <div class="text">颜色选择</div>
                                <div class=" menu">
                                    <div class="item" data-value="red"><div class="ui red label"></div></div>
                                    <div class="item" data-value="orange"><div class="ui orange label"></div></div>
                                    <div class="item" data-value="yellow"><div class="ui yellow label"></div></div>
                                    <div class="item" data-value="olive"><div class="ui olive label"></div></div>
                                    <div class="item" data-value="green"><div class="ui green label"></div></div>
                                    <div class="item" data-value="teal"><div class="ui teal label"></div></div>
                                    <div class="item" data-value="blue"><div class="ui blue label"></div></div>
                                    <div class="item" data-value="violet"><div class="ui violet label"></div></div>
                                    <div class="item" data-value="purple"><div class="ui purple label"></div></div>
                                    <div class="item" data-value="pink"><div class="ui pink label"></div></div>
                                    <div class="item" data-value="brown"><div class="ui brown label"></div></div>
                                    <div class="item" data-value="grey"><div class="ui grey label"></div></div>
                                    <div class="item" data-value="black"><div class="ui black label"></div></div>
                                </div>
                            </div>
                        </div>
                        <#if loginNameRole != "visitor">
                            <button class="ui small positive button" type="submit">添加</button>
                        </#if>
                    </form>
                </div>

                <div class="project-label-card">
                    <h3 class="project-label-card-title">快照标签</h3>
                    <p class="project-label-card-desc">用于标识系统快照的环境、阶段或特定场景。</p>
                    <#if snapshotLables?size gt 0>
                        <div class="project-label-list">
                            <#list snapshotLables as lab>
                                <div class="ui ${lab.color} label" style="margin-top: 0">${lab.name}
                                    <a href="/p/${project.id}/label/delete?type=snapshot&name=${lab.name}" data-tooltip="删除标签" data-position="top left" data-inverted="">
                                        <i class="ui icon delete"></i>
                                    </a>
                                </div>
                            </#list>
                        </div>
                    <#else>
                        <div class="project-label-empty">暂无快照标签。</div>
                    </#if>

                    <form class="ui form project-label-form" action="/p/${project.id}/label/add">
                        <input type="hidden" name="type" value="snapshot">
                        <div class="ui small right labeled input">
                            <input type="text" name="name" placeholder="标签名称">
                            <div class="ui dropdown label">
                                <input type="hidden" name="color">
                                <div class="text">颜色选择</div>
                                <div class=" menu">
                                    <div class="item" data-value="red"><div class="ui red label"></div></div>
                                    <div class="item" data-value="orange"><div class="ui orange label"></div></div>
                                    <div class="item" data-value="yellow"><div class="ui yellow label"></div></div>
                                    <div class="item" data-value="olive"><div class="ui olive label"></div></div>
                                    <div class="item" data-value="green"><div class="ui green label"></div></div>
                                    <div class="item" data-value="teal"><div class="ui teal label"></div></div>
                                    <div class="item" data-value="blue"><div class="ui blue label"></div></div>
                                    <div class="item" data-value="violet"><div class="ui violet label"></div></div>
                                    <div class="item" data-value="purple"><div class="ui purple label"></div></div>
                                    <div class="item" data-value="pink"><div class="ui pink label"></div></div>
                                    <div class="item" data-value="brown"><div class="ui brown label"></div></div>
                                    <div class="item" data-value="grey"><div class="ui grey label"></div></div>
                                    <div class="item" data-value="black"><div class="ui black label"></div></div>
                                </div>
                            </div>
                        </div>
                        <#if loginNameRole != "visitor">
                            <button class="ui small positive button" type="submit">添加</button>
                        </#if>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    $('.project-settings-page .ui.dropdown').dropdown({
        on: 'hover'
    });

    $('.project-label-form').on('submit', function() {
        var $form = $(this);
        if (oatIsFormSubmitting($form)) {
            return false;
        }
        oatSetFormSubmitting($form, true, {
            keepFieldsEnabled: true,
            readonlyFields: true,
            message: '正在添加标签...'
        });
        return true;
    });

    $('.project-label-list a').on('click', function() {
        var $link = $(this);
        if ($link.hasClass('disabled')) {
            return false;
        }
        $link.addClass('disabled').attr('aria-disabled', 'true');
        $link.closest('.ui.label').addClass('oat-submitting-scope');
        return true;
    });
</script>
</body>
</html>
