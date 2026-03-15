/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.indexing;

import dev.fararoni.core.core.indexing.model.SemanticUnit;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterRuby;
import org.treesitter.TreeSitterRust;
import org.treesitter.TreeSitterTypescript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class MultiLanguageSurgicalParser {
    private static final Set<String> PYTHON_FUNCTION_TYPES = Set.of(
        "function_definition"
    );

    private static final Set<String> PYTHON_CLASS_TYPES = Set.of(
        "class_definition"
    );

    private static final String PYTHON_DECORATED_DEFINITION = "decorated_definition";

    private static final Set<String> JS_FUNCTION_TYPES = Set.of(
        "function_declaration",
        "arrow_function",
        "method_definition",
        "function_expression",
        "generator_function_declaration"
    );

    private static final Set<String> JS_CLASS_TYPES = Set.of(
        "class_declaration",
        "class"
    );

    private static final Set<String> TS_SPECIFIC_TYPES = Set.of(
        "interface_declaration",
        "type_alias_declaration",
        "enum_declaration"
    );

    private static final Map<String, Supplier<TSLanguage>> EXTENSION_TO_LANGUAGE = Map.of(
        "py", TreeSitterPython::new,
        "js", TreeSitterJavascript::new,
        "jsx", TreeSitterJavascript::new,
        "ts", TreeSitterTypescript::new,
        "tsx", TreeSitterTypescript::new,
        "go", TreeSitterGo::new,
        "rs", TreeSitterRust::new,
        "rb", TreeSitterRuby::new
    );

    private final HtmlScriptExtractor htmlExtractor = new HtmlScriptExtractor();

    public List<SemanticUnit> parse(String content, String extension) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        TSLanguage language = getLanguage(extension);
        TSParser parser = new TSParser();
        parser.setLanguage(language);

        TSTree tree = parser.parseString(null, content);
        if (tree == null) {
            return List.of();
        }

        TSNode root = tree.getRootNode();
        return extractUnits(root, content, extension);
    }

    public boolean isSupported(String extension) {
        return EXTENSION_TO_LANGUAGE.containsKey(extension.toLowerCase());
    }

    public Set<String> getSupportedExtensions() {
        return EXTENSION_TO_LANGUAGE.keySet();
    }

    public List<SemanticUnit> parseHtml(String content, String extension) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        if (!htmlExtractor.isHtmlLikeExtension(extension)) {
            return List.of();
        }

        List<HtmlScriptExtractor.ScriptBlock> scripts = htmlExtractor.extract(content);
        if (scripts.isEmpty()) {
            return List.of();
        }

        List<SemanticUnit> allUnits = new ArrayList<>();

        for (HtmlScriptExtractor.ScriptBlock script : scripts) {
            String scriptExt = script.getExtension();
            List<SemanticUnit> units = parse(script.content(), scriptExt);

            int lineOffset = script.startLine() - 1;
            for (SemanticUnit unit : units) {
                allUnits.add(SemanticUnit.of(
                    unit.type(),
                    "[html:" + extension + "] " + unit.signature(),
                    unit.content(),
                    unit.startLine() + lineOffset,
                    unit.endLine() + lineOffset,
                    unit.usedFields()
                ));
            }
        }

        return allUnits;
    }

    public boolean isHtmlLikeExtension(String extension) {
        return htmlExtractor.isHtmlLikeExtension(extension);
    }

    private List<SemanticUnit> extractUnits(TSNode root, String source, String extension) {
        List<SemanticUnit> units = new ArrayList<>();
        traverseAndExtract(root, source, extension, units);
        return units;
    }

    private void traverseAndExtract(TSNode node, String source, String extension, List<SemanticUnit> units) {
        if (node == null || node.isNull()) {
            return;
        }

        String nodeType = node.getType();

        SemanticUnit unit = tryExtractUnit(node, source, extension, nodeType);
        if (unit != null) {
            units.add(unit);
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = node.getChild(i);
            traverseAndExtract(child, source, extension, units);
        }
    }

    private SemanticUnit tryExtractUnit(TSNode node, String source, String extension, String nodeType) {
        return switch (extension.toLowerCase()) {
            case "py" -> extractPythonUnit(node, source, nodeType);
            case "js", "jsx" -> extractJavaScriptUnit(node, source, nodeType);
            case "ts", "tsx" -> extractTypeScriptUnit(node, source, nodeType);
            case "go" -> extractGoUnit(node, source, nodeType);
            case "rs" -> extractRustUnit(node, source, nodeType);
            case "rb" -> extractRubyUnit(node, source, nodeType);
            default -> null;
        };
    }

    private SemanticUnit extractPythonUnit(TSNode node, String source, String nodeType) {
        if (PYTHON_DECORATED_DEFINITION.equals(nodeType)) {
            return extractDecoratedPythonUnit(node, source);
        }

        if (PYTHON_FUNCTION_TYPES.contains(nodeType)) {
            return createPythonUnit(node, source, SemanticUnit.TYPE_METHOD,
                extractPythonSignature(node, source), null);
        }

        if (PYTHON_CLASS_TYPES.contains(nodeType)) {
            return createPythonUnit(node, source, SemanticUnit.TYPE_CLASS,
                extractPythonClassName(node, source), null);
        }

        return null;
    }

    private SemanticUnit extractDecoratedPythonUnit(TSNode decoratedNode, String source) {
        TSNode definition = null;
        List<String> decorators = new ArrayList<>();

        int childCount = decoratedNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = decoratedNode.getChild(i);
            if (child == null || child.isNull()) continue;

            String childType = child.getType();
            if ("decorator".equals(childType)) {
                String decoratorText = extractDecoratorName(child, source);
                if (decoratorText != null) {
                    decorators.add(decoratorText);
                }
            } else if (PYTHON_FUNCTION_TYPES.contains(childType)) {
                definition = child;
            } else if (PYTHON_CLASS_TYPES.contains(childType)) {
                definition = child;
            }
        }

        if (definition == null) {
            return null;
        }

        String defType = definition.getType();
        String decoratorPrefix = decorators.isEmpty() ? "" :
            String.join("\n", decorators.stream().map(d -> "@" + d).toList()) + "\n";

        if (PYTHON_FUNCTION_TYPES.contains(defType)) {
            String signature = decoratorPrefix + extractPythonSignature(definition, source);
            return createPythonUnit(decoratedNode, source, SemanticUnit.TYPE_METHOD, signature, decorators);
        }

        if (PYTHON_CLASS_TYPES.contains(defType)) {
            String signature = decoratorPrefix + extractPythonClassName(definition, source);
            return createPythonUnit(decoratedNode, source, SemanticUnit.TYPE_CLASS, signature, decorators);
        }

        return null;
    }

    private String extractDecoratorName(TSNode decoratorNode, String source) {
        TSNode identifier = findChildByType(decoratorNode, "identifier");
        if (identifier != null) {
            return getNodeText(identifier, source);
        }

        TSNode call = findChildByType(decoratorNode, "call");
        if (call != null) {
            TSNode callId = findChildByType(call, "identifier");
            if (callId != null) {
                return getNodeText(call, source);
            }
        }

        TSNode attribute = findChildByType(decoratorNode, "attribute");
        if (attribute != null) {
            return getNodeText(attribute, source);
        }

        return null;
    }

    private SemanticUnit createPythonUnit(TSNode node, String source, String type,
            String signature, List<String> decorators) {
        int indentLevel = node.getStartPoint().getColumn();

        int startLine = node.getStartPoint().getRow() + 1;
        int endLine = node.getEndPoint().getRow() + 1;

        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        String content = source.substring(
            Math.min(startByte, source.length()),
            Math.min(endByte, source.length())
        );

        String finalSignature = signature;
        if (indentLevel > 0) {
            finalSignature = "[indent:" + indentLevel + "] " + signature;
        }

        return SemanticUnit.of(
            type,
            finalSignature,
            content,
            startLine,
            endLine,
            decorators != null ? Set.copyOf(decorators) : Set.of()
        );
    }

    private SemanticUnit extractJavaScriptUnit(TSNode node, String source, String nodeType) {
        if (JS_FUNCTION_TYPES.contains(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_METHOD, extractJsSignature(node, source, nodeType));
        }

        if (JS_CLASS_TYPES.contains(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_CLASS, extractJsClassName(node, source));
        }

        return null;
    }

    private SemanticUnit extractTypeScriptUnit(TSNode node, String source, String nodeType) {
        if (TS_SPECIFIC_TYPES.contains(nodeType)) {
            String type = switch (nodeType) {
                case "interface_declaration" -> SemanticUnit.TYPE_CLASS;
                case "type_alias_declaration" -> SemanticUnit.TYPE_FIELD;
                case "enum_declaration" -> SemanticUnit.TYPE_CLASS;
                default -> SemanticUnit.TYPE_CLASS;
            };
            return createUnit(node, source, type, extractTsTypeName(node, source, nodeType));
        }

        if (JS_FUNCTION_TYPES.contains(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_METHOD, extractJsSignature(node, source, nodeType));
        }

        if (JS_CLASS_TYPES.contains(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_CLASS, extractJsClassName(node, source));
        }

        return null;
    }

    private SemanticUnit extractGoUnit(TSNode node, String source, String nodeType) {
        if ("function_declaration".equals(nodeType) || "method_declaration".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_METHOD, extractGoSignature(node, source));
        }

        if ("type_declaration".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_CLASS, extractGoTypeName(node, source));
        }

        return null;
    }

    private SemanticUnit extractRustUnit(TSNode node, String source, String nodeType) {
        if ("function_item".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_METHOD, extractRustSignature(node, source));
        }

        if ("struct_item".equals(nodeType) || "enum_item".equals(nodeType) || "impl_item".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_CLASS, extractRustTypeName(node, source, nodeType));
        }

        return null;
    }

    private SemanticUnit extractRubyUnit(TSNode node, String source, String nodeType) {
        if ("method".equals(nodeType) || "singleton_method".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_METHOD, extractRubySignature(node, source));
        }

        if ("class".equals(nodeType) || "module".equals(nodeType)) {
            return createUnit(node, source, SemanticUnit.TYPE_CLASS, extractRubyClassName(node, source, nodeType));
        }

        return null;
    }

    private String extractPythonSignature(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        TSNode params = findChildByType(node, "parameters");
        String paramsStr = params != null ? getNodeText(params, source) : "()";

        boolean isAsync = findChildByType(node, "async") != null;
        String asyncPrefix = isAsync ? "async " : "";

        String returnType = extractPythonReturnType(node, source);
        String returnTypeStr = returnType != null ? " -> " + returnType : "";

        return asyncPrefix + "def " + name + paramsStr + returnTypeStr;
    }

    private String extractPythonReturnType(TSNode functionNode, String source) {
        int childCount = functionNode.getChildCount();
        boolean foundArrow = false;

        for (int i = 0; i < childCount; i++) {
            TSNode child = functionNode.getChild(i);
            if (child == null || child.isNull()) continue;

            String childType = child.getType();

            if ("->".equals(childType)) {
                foundArrow = true;
                continue;
            }

            if (foundArrow && "type".equals(childType)) {
                return getNodeText(child, source);
            }
        }

        return null;
    }

    private String extractPythonClassName(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        return "class " + (nameNode != null ? getNodeText(nameNode, source) : "anonymous");
    }

    private String extractJsSignature(TSNode node, String source, String nodeType) {
        if ("arrow_function".equals(nodeType)) {
            return "() => {...}";
        }

        TSNode nameNode = findChildByType(node, "identifier");
        if (nameNode == null) {
            nameNode = findChildByType(node, "property_identifier");
        }
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        TSNode params = findChildByType(node, "formal_parameters");
        String paramsStr = params != null ? getNodeText(params, source) : "()";

        return "function " + name + paramsStr;
    }

    private String extractJsClassName(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        return "class " + (nameNode != null ? getNodeText(nameNode, source) : "anonymous");
    }

    private String extractTsTypeName(TSNode node, String source, String nodeType) {
        TSNode nameNode = findChildByType(node, "identifier");
        if (nameNode == null) {
            nameNode = findChildByType(node, "type_identifier");
        }
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        return switch (nodeType) {
            case "interface_declaration" -> "interface " + name;
            case "type_alias_declaration" -> "type " + name;
            case "enum_declaration" -> "enum " + name;
            default -> name;
        };
    }

    private String extractGoSignature(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        TSNode params = findChildByType(node, "parameter_list");
        String paramsStr = params != null ? getNodeText(params, source) : "()";

        return "func " + name + paramsStr;
    }

    private String extractGoTypeName(TSNode node, String source) {
        TSNode spec = findChildByType(node, "type_spec");
        if (spec != null) {
            TSNode nameNode = findChildByType(spec, "type_identifier");
            if (nameNode != null) {
                return "type " + getNodeText(nameNode, source);
            }
        }
        return "type anonymous";
    }

    private String extractRustSignature(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        TSNode params = findChildByType(node, "parameters");
        String paramsStr = params != null ? getNodeText(params, source) : "()";

        return "fn " + name + paramsStr;
    }

    private String extractRustTypeName(TSNode node, String source, String nodeType) {
        TSNode nameNode = findChildByType(node, "type_identifier");
        if (nameNode == null) {
            nameNode = findChildByType(node, "identifier");
        }
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        return switch (nodeType) {
            case "struct_item" -> "struct " + name;
            case "enum_item" -> "enum " + name;
            case "impl_item" -> "impl " + name;
            default -> name;
        };
    }

    private String extractRubySignature(TSNode node, String source) {
        TSNode nameNode = findChildByType(node, "identifier");
        String name = nameNode != null ? getNodeText(nameNode, source) : "anonymous";

        TSNode params = findChildByType(node, "method_parameters");
        String paramsStr = params != null ? getNodeText(params, source) : "";

        return "def " + name + paramsStr;
    }

    private String extractRubyClassName(TSNode node, String source, String nodeType) {
        TSNode nameNode = findChildByType(node, "constant");
        String prefix = "module".equals(nodeType) ? "module " : "class ";
        return prefix + (nameNode != null ? getNodeText(nameNode, source) : "anonymous");
    }

    private TSLanguage getLanguage(String extension) {
        Supplier<TSLanguage> supplier = EXTENSION_TO_LANGUAGE.get(extension.toLowerCase());
        if (supplier == null) {
            throw new UnsupportedOperationException(
                "Language not supported: " + extension +
                ". Supported: " + EXTENSION_TO_LANGUAGE.keySet()
            );
        }
        return supplier.get();
    }

    private SemanticUnit createUnit(TSNode node, String source, String type, String signature) {
        int startLine = node.getStartPoint().getRow() + 1;
        int endLine = node.getEndPoint().getRow() + 1;

        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        String content = source.substring(
            Math.min(startByte, source.length()),
            Math.min(endByte, source.length())
        );

        return SemanticUnit.of(
            type,
            signature,
            content,
            startLine,
            endLine,
            Set.of()
        );
    }

    private String getNodeText(TSNode node, String source) {
        if (node == null || node.isNull()) {
            return "";
        }
        int start = node.getStartByte();
        int end = node.getEndByte();
        if (start >= 0 && end <= source.length() && start < end) {
            return source.substring(start, end);
        }
        return "";
    }

    private TSNode findChildByType(TSNode parent, String type) {
        if (parent == null || parent.isNull()) {
            return null;
        }

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = parent.getChild(i);
            if (child != null && !child.isNull() && type.equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    public ParseDiagnostic diagnose(String content, String extension) {
        if (!isSupported(extension)) {
            return new ParseDiagnostic(extension, false, 0, 0, 0, "Language not supported");
        }

        try {
            List<SemanticUnit> units = parse(content, extension);

            int methods = (int) units.stream().filter(SemanticUnit::isMethod).count();
            int classes = (int) units.stream().filter(SemanticUnit::isClass).count();

            return new ParseDiagnostic(
                extension,
                true,
                units.size(),
                methods,
                classes,
                null
            );
        } catch (Exception e) {
            return new ParseDiagnostic(extension, false, 0, 0, 0, e.getMessage());
        }
    }

    public record ParseDiagnostic(
        String extension,
        boolean success,
        int totalUnits,
        int methods,
        int classes,
        String errorMessage
    ) {}
}
