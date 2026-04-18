<#include "../common.ftl">
<span class="ui sub header">为您找到:${searchPage.total}条结果</span>
<div class="ui link divided  items">
       <#list searchPage.contents as item>
           <a class="item" target="_blank" href="${systemSnapshotDetailHref(project.id, item.appId, item.id)}">
               <div class="ui tiny image">
                   <#if item.headImage??>
                       <img src="/r/${item.headImage}">
                   <#else>
                         <img src="/images/image.png">
                   </#if>
               </div>


               <div class="content">
                   <div class="header">
                       <#if item.titleFragment??>
                           ${item.titleFragment}
                       <#else >
                           ${item.title}
                       </#if>
                   </div>
                   <div class="description">
                       <p>
                       <#--用例文本内容高亮片段-->
                           <#if item.describeFragments??>
                               ${arrayToString(item.describeFragments," ")}
                           </#if>
                       <#--sql 高亮选段 -->
                           <#if item.sqlContentFragments??>
                               ${arrayToString(item.sqlContentFragments," ")}
                           </#if>
                       <#--sql 远程调用高亮选段 -->
                           <#if item.remoteContentFragments??>
                               ${arrayToString(item.remoteContentFragments," ")}
                           </#if>
                       </p>
                   </div>
               </div>
           </a>
       </#list>
</div>