<div class="ui block header attached ">
    保存当前快照
</div>
    <i class="close icon" style=" top: 1rem;right: 1rem;color: rgba(0,0,0,.87)"></i>
    <div class="ui content">
        <form id="newSnapshotForm" method="post" class="ui form" action="/p/${project.id}/snapshot/doUpdate">
            <input type="hidden" name="id" value="${snapshot.id}">
            <div class="required field">
                <label>快照名称</label>
                <input type="text" name="name" placeholder="输入快照名称" value="${snapshot.name}">
            </div>
            <div class="field">
                <label>添加标签</label>
                <div  class="ui multiple search normal selection dropdown labelSelection">
                    <input type="hidden" name="labels" value="${snapshotLabel!}">
                    <i class="dropdown icon"></i>
                    <div class="default text">选择标签</div>
                    <div class="menu">
                        <#list labels as lab>
                            <div class="item" data-value="${lab.name}">${lab.name}</div>
                        </#list>
                    </div>
                </div>
            </div>

            <div class="field">
                <lable> 快照描述</lable>
                <div class="content">
                    <textarea rows="3" name="describe">${snapshot.describe!}</textarea>
                </div>
            </div>
        </form>
    </div>

    <div class="actions">
        <div class="ui black deny button">
            取消
        </div>
        <div id="saveSnapshotButton" class="ui positive right labeled icon button save"
             onclick="submitSnapshotUpdate();">
            保存
            <i class="checkmark icon"></i>
        </div>
    </div>
<script>
    function submitSnapshotUpdate() {
        var $form = $('#newSnapshotForm');
        $.ajax({
            type: "POST",
            url: $form.attr('action'),
            data: $form.serialize(),
            success: function(res) {
                if (res.success || res.result) {
                    $('#snapshotEditDialog').modal('hide');
                    showToast(res.message || '更新成功', 'success');
                    // 延迟刷新页面以展示更新后的数据
                    setTimeout(function() {
                        location.reload();
                    }, 1000);
                } else {
                    showToast('更新失败: ' + (res.message || '未知错误'), 'error');
                }
            },
            error: function() {
                showToast('网络请求失败', 'error');
            }
        });
    }

    // 这里不能用ID进行选择定位，因为有可能造成原来的ID对象没有销毁
    $('.dropdown.labelSelection').dropdown({
        // maxSelections: 5
    });
</script>
