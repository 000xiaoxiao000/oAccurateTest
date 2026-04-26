(function ($) {
    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function renderMenu(currentPage, totalPages) {
        var html = '<div class="ui pagination menu">';
        html += '<a class="icon item ' + (currentPage === 1 ? 'disabled' : '') + '" data-page="' + (currentPage - 1) + '"><i class="left chevron icon"></i></a>';
        for (var page = 1; page <= totalPages; page++) {
            if (page === 1 || page === totalPages || Math.abs(page - currentPage) <= 2) {
                html += '<a class="item ' + (page === currentPage ? 'active' : '') + '" data-page="' + page + '">' + page + '</a>';
            } else if (page === currentPage - 3 || page === currentPage + 3) {
                html += '<span class="disabled item">…</span>';
            }
        }
        html += '<a class="icon item ' + (currentPage === totalPages ? 'disabled' : '') + '" data-page="' + (currentPage + 1) + '"><i class="right chevron icon"></i></a>';
        html += '</div>';
        return html;
    }

    function initListControl() {
        var $toolbar = $(this);
        var $table = $($toolbar.data('table'));
        if (!$table.length || $toolbar.data('initialized')) {
            return;
        }
        $toolbar.data('initialized', true);

        var clientPagination = $toolbar.data('client-pagination') !== false && String($toolbar.data('client-pagination')) !== 'false';
        var pageSize = parseInt($toolbar.data('page-size'), 10) || 10;
        var currentPage = 1;
        var placeholder = $toolbar.data('search-placeholder') || '搜索当前列表';
        var emptyText = $toolbar.data('empty-text') || '暂无匹配数据';
        var colspan = parseInt($toolbar.data('empty-colspan'), 10) || $table.find('thead th').length || 1;
        var $tbody = $table.find('tbody').first();
        var $primaryRows = $tbody.children('tr').not('.js-list-detail');
        var $pagination = $('<div class="oat-list-pagination"></div>');

        $toolbar.html(
            '<div class="ui icon input oat-list-search">' +
            '<i class="search icon"></i>' +
            '<input type="text" class="js-list-search-input" placeholder="' + escapeHtml(placeholder) + '">' +
            '</div>' +
            (clientPagination ? '<div class="oat-list-tools">' +
            '<span>每页</span>' +
            '<select class="ui compact selection dropdown oat-list-page-size js-list-page-size">' +
            '<option value="10">10 条</option>' +
            '<option value="20">20 条</option>' +
            '<option value="50">50 条</option>' +
            '</select>' +
            '</div>' : '<div class="oat-list-tools">搜索当前页</div>')
        );
        if (clientPagination) {
            $toolbar.find('.js-list-page-size').val(String(pageSize)).dropdown();
        }
        $table.after($pagination);

        function rowText($row) {
            var $detail = $row.next('.js-list-detail');
            return ($row.text() + ' ' + ($detail.length ? $detail.text() : '')).toLowerCase();
        }

        function apply() {
            var keyword = $.trim($toolbar.find('.js-list-search-input').val()).toLowerCase();
            var matchedRows = $primaryRows.filter(function () {
                return !keyword || rowText($(this)).indexOf(keyword) !== -1;
            });
            var total = matchedRows.length;
            var totalPages = clientPagination ? Math.max(1, Math.ceil(total / pageSize)) : 1;
            currentPage = Math.min(Math.max(currentPage, 1), totalPages);
            var startIndex = clientPagination ? (currentPage - 1) * pageSize : 0;
            var endIndex = clientPagination ? Math.min(startIndex + pageSize, total) : total;

            $tbody.find('.oat-list-empty-row').remove();
            $primaryRows.hide().next('.js-list-detail').hide();
            matchedRows.slice(startIndex, endIndex).show();

            if (!total) {
                $tbody.append('<tr class="oat-list-empty-row"><td colspan="' + colspan + '">' + escapeHtml(emptyText) + '</td></tr>');
                $pagination.addClass('is-hidden').empty();
                return;
            }

            if (!clientPagination) {
                $pagination.addClass('is-hidden').empty();
                return;
            }

            $pagination.removeClass('is-hidden').html(
                '<div class="oat-list-page-info">显示 ' + (startIndex + 1) + '-' + endIndex + ' 条，共 ' + total + ' 条</div>' +
                renderMenu(currentPage, totalPages)
            );
        }

        function refresh(resetPage) {
            $tbody = $table.find('tbody').first();
            $primaryRows = $tbody.children('tr').not('.js-list-detail');
            if (resetPage) {
                currentPage = 1;
            }
            apply();
        }

        $toolbar.on('input', '.js-list-search-input', function () {
            currentPage = 1;
            apply();
        });
        $toolbar.on('change', '.js-list-page-size', function () {
            pageSize = parseInt($(this).val(), 10) || 10;
            currentPage = 1;
            apply();
        });
        $pagination.on('click', '.item[data-page]:not(.disabled)', function () {
            currentPage = parseInt($(this).data('page'), 10) || 1;
            apply();
        });

        $toolbar.data('oatListControlRefresh', refresh);
        apply();
    }

    window.OatListControls = window.OatListControls || {};
    window.OatListControls.refresh = function (tableSelector, resetPage) {
        $('.js-list-control').each(function () {
            var $toolbar = $(this);
            if ($toolbar.data('table') === tableSelector && typeof $toolbar.data('oatListControlRefresh') === 'function') {
                $toolbar.data('oatListControlRefresh')(resetPage);
            }
        });
    };

    $(function () {
        $('.js-list-control').each(initListControl);
    });
})(jQuery);
