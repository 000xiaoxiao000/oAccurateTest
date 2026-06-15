package com.oAT.web.coverage.universal;

import com.oAT.web.coverage.universal.UniversalCoverageFile.BranchCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.FunctionCoverage;
import com.oAT.web.coverage.universal.UniversalCoverageFile.LineCoverage;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JacocoCoverageParser implements CoverageParser {
    @Override
    public SourceType sourceType() {
        return SourceType.JAVA;
    }

    @Override
    public List<UniversalCoverageFile> parse(byte[] rawData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(rawData));
            document.getDocumentElement().normalize();
            return parseReport(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 JaCoCo XML 覆盖率失败", e);
        }
    }

    private List<UniversalCoverageFile> parseReport(Document document) {
        Map<String, UniversalCoverageFile> files = new LinkedHashMap<>();
        NodeList packages = document.getElementsByTagName("package");
        for (int i = 0; i < packages.getLength(); i++) {
            Element packageElement = (Element) packages.item(i);
            String packageName = packageElement.getAttribute("name");
            parseSourceFiles(packageElement, packageName, files);
            parseClassMethods(packageElement, packageName, files);
        }
        return new ArrayList<>(files.values());
    }

    private void parseSourceFiles(Element packageElement, String packageName, Map<String, UniversalCoverageFile> files) {
        NodeList children = packageElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element sourceFileElement) || !"sourcefile".equals(sourceFileElement.getTagName())) {
                continue;
            }
            String filePath = filePath(packageName, sourceFileElement.getAttribute("name"));
            UniversalCoverageFile file = files.computeIfAbsent(filePath, key -> new UniversalCoverageFile(SourceType.JAVA, key));

            NodeList lineNodes = sourceFileElement.getElementsByTagName("line");
            for (int lineIndex = 0; lineIndex < lineNodes.getLength(); lineIndex++) {
                Element lineElement = (Element) lineNodes.item(lineIndex);
                int lineNumber = intAttr(lineElement, "nr");
                int coveredInstructions = intAttr(lineElement, "ci");
                int missedBranches = intAttr(lineElement, "mb");
                int coveredBranches = intAttr(lineElement, "cb");
                file.getLines().add(new LineCoverage(lineNumber, coveredInstructions));
                int branchTargetCount = missedBranches + coveredBranches;
                for (int branchIndex = 0; branchIndex < branchTargetCount; branchIndex++) {
                    int coveredCount = branchIndex < coveredBranches ? 1 : 0;
                    file.getBranches().add(new BranchCoverage(lineNumber, branchIndex, coveredCount, lineNumber + ""));
                }
            }
        }
    }

    private void parseClassMethods(Element packageElement, String packageName, Map<String, UniversalCoverageFile> files) {
        NodeList children = packageElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element classElement) || !"class".equals(classElement.getTagName())) {
                continue;
            }
            String sourceFileName = classElement.getAttribute("sourcefilename");
            if (sourceFileName == null || sourceFileName.isBlank()) {
                continue;
            }
            String filePath = filePath(packageName, sourceFileName);
            UniversalCoverageFile file = files.computeIfAbsent(filePath, key -> new UniversalCoverageFile(SourceType.JAVA, key));
            NodeList methodNodes = classElement.getElementsByTagName("method");
            for (int methodIndex = 0; methodIndex < methodNodes.getLength(); methodIndex++) {
                Element methodElement = (Element) methodNodes.item(methodIndex);
                int startLine = intAttr(methodElement, "line");
                int coveredInstructions = coveredCounter(methodElement, "INSTRUCTION");
                file.getFunctions().add(new FunctionCoverage(
                        methodElement.getAttribute("name"),
                        startLine,
                        startLine,
                        coveredInstructions));
            }
        }
    }

    private int coveredCounter(Element methodElement, String type) {
        NodeList counters = methodElement.getElementsByTagName("counter");
        for (int i = 0; i < counters.getLength(); i++) {
            Element counter = (Element) counters.item(i);
            if (type.equals(counter.getAttribute("type"))) {
                return intAttr(counter, "covered");
            }
        }
        return 0;
    }

    private String filePath(String packageName, String fileName) {
        if (packageName == null || packageName.isBlank()) {
            return fileName;
        }
        return packageName + "/" + fileName;
    }

    private int intAttr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }
}
