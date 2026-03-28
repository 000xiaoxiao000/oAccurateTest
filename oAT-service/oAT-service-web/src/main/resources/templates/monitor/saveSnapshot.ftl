<div class="ui block header attached ">
    保存当前快照
</div>
    <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>
    <div class="ui content">
        <form id="newSnapshotForm" class="ui form" action="/p/${project.id}/snapshot/save">
            <div class="required field">
                <label>快照名称</label>
                <input type="text" name="name" placeholder="输入快照名称">
            </div>
            <div class="field">
                <label>添加标签</label>
                <select multiple="3" class="ui search dropdown" name="labels">
                                    <#list labels as lab>
                                        <option value="${lab.name}">${lab.name}</option>
                                    </#list>
                </select>
            </div>
            <div class="field">
                <lable> 快照描述</lable>
                <div class="content">
                    <textarea rows="3" name="describe"></textarea>
                </div>
            </div>
            <div class="ui error message">

            </div>
        </form>
    </div>
    <div class="actions">
        <div class="ui black deny button">
            取消
        </div>
        <div id="saveSnapshotButton" class="ui positive right labeled icon button save">
            保存
            <i class="checkmark icon"></i>
        </div>
    </div>

<script>
    // 初始化表单验证规则
    $(function () {
        $('#newSnapshotForm')
                .form({
                    inline: false,
                    onFailure: function (formErrors, fields) {
                        if (formErrors && formErrors.length > 0) {
                            showToast(formErrors[0], 'error');
                        }
                        return false;
                    },
                    fields: {
                        name: {
                            identifier: 'name',
                            rules: [
                                {
                                    type: 'empty',
                                    prompt: '请输入快照名称'
                                },
                                {
                                    type: 'minLength[4]',
                                    prompt: '快照名称至少包含4个字符'
                                },
                                {
                                    type: 'maxLength[50]',
                                    prompt: '快照名称不能超过50个字符'
                                }
                            ]
                        },
                        describe: {
                            identifier: 'describe',
                            rules: [
                                {
                                    type: 'maxLength[512]',
                                    prompt: '快照描述不能超过512个字符'
                                }
                            ]
                        }
                    }
                });

        $('#saveSnapshotButton').on('click', function() {
            if ($('#newSnapshotForm').form('validate form')) {
                doSaveSnapshot('${project.id}');
                // 关闭当前模型
                $("#newSnapshotForm").parents(".modal").modal('hide');
            }
        });
    });

    $(function () {
        $("#newSnapshotForm").submit(function(e){
            e.preventDefault();
            if ($(this).form('validate form')) {
                doSaveSnapshot('${project.id}');
                // 关闭当前模型
                $("#newSnapshotForm").parents(".modal").modal('hide');
            }
            return false;
        });
    });
</script>

