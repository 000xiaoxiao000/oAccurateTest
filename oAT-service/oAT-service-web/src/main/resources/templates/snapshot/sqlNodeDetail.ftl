<div class="ui small header">
    URL
    <div class=" sub header ">${node.jdbcUrl!}</div>
</div>
<div class="ui small header">
    SQL语句
</div>
<pre><code class="sql">${sql!"没有任何SQL信息"}</code></pre>
 <script>
     document.querySelectorAll('pre code').forEach(
             function (block) {
                 hljs.highlightBlock(block);
             });
 </script>
