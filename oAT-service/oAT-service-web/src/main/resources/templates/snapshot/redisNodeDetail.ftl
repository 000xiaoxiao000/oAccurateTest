<div class="ui small header">
    连接名称
    <div class=" sub header ">${connectionName!}</div>
</div>
<div class="ui small header">
    Redis语句
</div>
<pre><code class="hljs-javadoc">${cmd!"没有任何SQL信息"}</code></pre>
 <script>
     document.querySelectorAll('pre code').forEach(
             function (block) {
                 hljs.highlightBlock(block);
             });
 </script>