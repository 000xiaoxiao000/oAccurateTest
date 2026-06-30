package com.oAT.web.coveragecore.source;

import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CoverageSourceColoringService {
    public String renderColoredSource(String content, ClassCoverageIndex classCoverage) {
        return renderColoredSource(content, classCoverage, false);
    }

    public String renderColoredSource(String content, ClassCoverageIndex classCoverage, boolean showBranchDetails) {
        String[] lines = content.split("\\r?\\n");
        StringBuilder html = new StringBuilder();

        Map<Integer, String> lineColors = new HashMap<>();
        Map<Integer, String> branchLineColors = new HashMap<>();
        Map<Integer, String> branchLineDetails = new HashMap<>();
        Map<String, Integer> methodStartLines = new HashMap<>();

        if (classCoverage.getMethods() != null) {
            for (int i = 0; i < classCoverage.getMethods().size(); i++) {
                MethodCoverageDetail method = classCoverage.getMethods().get(i);
                List<Integer> totalLines = method.getTotalLineNumbers();
                List<Integer> coveredLines = method.getCoveredLineNumbers();

                if (totalLines != null && !totalLines.isEmpty()) {
                    List<Integer> sortedLines = new ArrayList<>(totalLines);
                    Collections.sort(sortedLines);
                    methodStartLines.put("method_" + i, sortedLines.get(0));

                    for (Integer lineNumber : totalLines) {
                        if (coveredLines != null && coveredLines.contains(lineNumber)) {
                            lineColors.put(lineNumber, "green");
                        } else if (!"green".equals(lineColors.get(lineNumber))) {
                            lineColors.put(lineNumber, "red");
                        }
                    }
                }
                mergeBranchLineColors(branchLineColors,
                        branchLineDetails,
                        method.getTotalBranchTargetProbeMap(),
                        method.getCoveredBranchTargetProbeMap());
            }
        }

        int totalLineCount = lines.length;
        int lineWidth = String.valueOf(totalLineCount).length();

        html.append("<pre style='font-family: monospace; white-space: pre; display:inline-block; min-width:100%; box-sizing:border-box;'>");
        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;

            for (Map.Entry<String, Integer> entry : methodStartLines.entrySet()) {
                if (entry.getValue() == lineNumber) {
                    html.append("<a name='").append(entry.getKey()).append("'></a>");
                }
            }

            String color = branchLineColors.containsKey(lineNumber) ? branchLineColors.get(lineNumber) : lineColors.get(lineNumber);
            String style = "position:relative;";
            if ("green".equals(color)) {
                style += "background-color: #ace1af;";
            } else if ("orange".equals(color)) {
                style += "background-color: #ffe5b4;";
            } else if ("red".equals(color)) {
                style += "background-color: #f5c6cb;";
            }
            String branchClassAttr = "";
            if (branchLineDetails.containsKey(lineNumber)) {
                branchClassAttr = " class='branch-line branch-" + (color == null ? "green" : color) + "'";
                style += "--line-number-width: " + lineWidth + ".2em;";
            }
            html.append("<div style='display:flex;min-width:max-content;").append(style).append("'");
            if (StringUtils.hasText(branchClassAttr)) {
                html.append(branchClassAttr);
            }
            html.append(">");
            html.append("<span style='color: #999; flex-shrink:0; width: ")
                    .append(lineWidth)
                    .append(".2em; text-align: right; display: inline-block; user-select:none; margin-right: 20px;'>")
                    .append(lineNumber)
                    .append("</span>")
                    .append(escapeHtml(lines[i]));
            if (branchLineDetails.containsKey(lineNumber)) {
                html.append("<span class='branch-flag' aria-hidden='true'></span>");
                html.append("<span class='branch-tooltip'>")
                        .append(escapeHtml(branchLineDetails.get(lineNumber)))
                        .append("</span>");
            }
            if (showBranchDetails && branchLineDetails.containsKey(lineNumber)) {
                html.append("<span style='margin-left: 16px; color: #666; font-size: 12px; white-space: nowrap;'>// ")
                        .append(escapeHtml(branchLineDetails.get(lineNumber)))
                        .append("</span>");
            }
            html.append("</div>");
        }
        html.append("</pre>");

        return html.toString();
    }

    private void mergeBranchLineColors(Map<Integer, String> branchLineColors,
                                       Map<Integer, String> branchLineDetails,
                                       Map<String, List<Integer>> totalBranchTargetProbeMap,
                                       Map<String, List<Integer>> coveredBranchTargetProbeMap) {
        if (totalBranchTargetProbeMap == null || totalBranchTargetProbeMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<Integer>> entry : totalBranchTargetProbeMap.entrySet()) {
            Integer branchLine = parsePositiveInt(entry.getKey());
            if (branchLine == null) {
                continue;
            }
            LinkedHashSet<Integer> totalSet = entry.getValue() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(entry.getValue());
            LinkedHashSet<Integer> coveredSet = new LinkedHashSet<>();
            if (coveredBranchTargetProbeMap != null) {
                List<Integer> covered = coveredBranchTargetProbeMap.get(entry.getKey());
                if (covered != null) {
                    coveredSet.addAll(covered);
                }
            }
            int totalCount = totalSet.size();
            int coveredCount = coveredSet.size();
            String color = coveredCount <= 0 ? "red" : (coveredCount >= totalCount ? "green" : "orange");
            String currentColor = branchLineColors.get(branchLine);
            if ("green".equals(color)) {
                branchLineColors.put(branchLine, currentColor == null ? "green" : currentColor);
            } else {
                branchLineColors.put(branchLine, pickCoverageColor(currentColor, color));
            }
            branchLineDetails.put(branchLine, mergeBranchDetailText(
                    branchLineDetails.get(branchLine),
                    buildBranchDetailText(totalSet, coveredSet)));
        }
    }

    private String mergeBranchDetailText(String currentDetail, String newDetail) {
        if (!StringUtils.hasText(currentDetail)) {
            return newDetail;
        }
        if (!StringUtils.hasText(newDetail) || currentDetail.equals(newDetail)) {
            return currentDetail;
        }
        return currentDetail + " | " + newDetail;
    }

    private String buildBranchDetailText(Set<Integer> totalSet, Set<Integer> coveredSet) {
        String totalText = totalSet == null || totalSet.isEmpty()
                ? "[]"
                : totalSet.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
        String coveredText = coveredSet == null || coveredSet.isEmpty()
                ? "[]"
                : coveredSet.stream().sorted().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
        String status;
        String statusIcon;
        if (coveredSet == null || coveredSet.isEmpty()) {
            status = "未覆盖";
            statusIcon = "🔴";
        } else if (totalSet != null && coveredSet.size() >= totalSet.size()) {
            status = "全覆盖";
            statusIcon = "🟢";
        } else {
            status = "部分覆盖";
            statusIcon = "🟠";
        }
        return statusIcon + " 分支状态：" + status + "\n已处理分支： " + coveredText + "\n总分支： " + totalText;
    }

    private String pickCoverageColor(String currentColor, String newColor) {
        if (currentColor == null) {
            return newColor;
        }
        if ("red".equals(currentColor) || "red".equals(newColor)) {
            return "red";
        }
        if ("orange".equals(currentColor) || "orange".equals(newColor)) {
            return "orange";
        }
        return "green";
    }

    private Integer parsePositiveInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
