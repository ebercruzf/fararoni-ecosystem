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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.util.List;
import java.util.Map;

/**
 * Contract for analyzing project structure and code.
 *
 * <p>This interface provides operations for understanding project
 * layout, detecting technologies, and analyzing code structure.
 * Essential for AI agents to understand the codebase before making changes.</p>
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>Detect project type (Maven, Gradle, npm, etc.)</li>
 *   <li>Analyze directory structure</li>
 *   <li>Parse and understand code symbols (classes, methods)</li>
 *   <li>Identify dependencies and their versions</li>
 *   <li>Calculate code metrics</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Understand the project
 * FNLResult<ProjectInfo> project = analysisSkill.analyzeProject("/path/to/project");
 *
 * if (project.success()) {
 *     ProjectInfo info = project.data();
 *     System.out.println("Type: " + info.projectType());     // MAVEN, GRADLE, NPM, etc.
 *     System.out.println("Language: " + info.primaryLanguage()); // Java, TypeScript, etc.
 *     System.out.println("Dependencies: " + info.dependencies().size());
 * }
 *
 * // Find all classes implementing an interface
 * FNLResult<List<SymbolInfo>> impls = analysisSkill.findImplementations("ToolSkill");
 *
 * // Get method signatures in a file
 * FNLResult<List<SymbolInfo>> methods = analysisSkill.listSymbols("/src/Service.java", SymbolKind.METHOD);
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface ProjectAnalysisSkill extends ToolSkill {

    // ==================== Project-Level Analysis ====================

    /**
     * Analyzes a project and returns comprehensive information.
     *
     * @param projectPath the project root directory
     * @return result containing project analysis
     */
    @AgentAction(
        name = "analyze_project",
        description = "Analyzes project structure, type, dependencies, and technologies"
    )
    FNLResult<ProjectInfo> analyzeProject(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String projectPath
    );

    /**
     * Gets the project directory structure as a tree.
     *
     * @param projectPath the project root
     * @param maxDepth maximum depth to traverse
     * @param excludePatterns patterns to exclude (e.g., "node_modules", ".git")
     * @return result containing directory tree
     */
    @AgentAction(
        name = "get_structure",
        description = "Returns project directory structure as a tree"
    )
    FNLResult<DirectoryNode> getStructure(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String projectPath,
        int maxDepth,
        List<String> excludePatterns
    );

    /**
     * Lists all dependencies in the project.
     *
     * @param projectPath the project root
     * @return result containing dependency list
     */
    @AgentAction(
        name = "list_dependencies",
        description = "Lists all project dependencies with versions"
    )
    FNLResult<List<Dependency>> listDependencies(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String projectPath
    );

    // ==================== Code Symbol Analysis ====================

    /**
     * Lists code symbols in a file.
     *
     * @param filePath the source file path
     * @param kind filter by symbol kind (null for all)
     * @return result containing symbol list
     */
    @AgentAction(
        name = "list_symbols",
        description = "Lists classes, methods, functions, variables in a file"
    )
    FNLResult<List<SymbolInfo>> listSymbols(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String filePath,
        SymbolKind kind
    );

    /**
     * Finds implementations of an interface or class.
     *
     * @param symbolName the interface/class name to search
     * @return result containing implementing classes
     */
    @AgentAction(
        name = "find_implementations",
        description = "Finds all classes implementing an interface or extending a class"
    )
    FNLResult<List<SymbolInfo>> findImplementations(String symbolName);

    /**
     * Finds usages of a symbol across the project.
     *
     * @param symbolName the symbol name to find
     * @return result containing usage locations
     */
    @AgentAction(
        name = "find_usages",
        description = "Finds all usages of a class, method, or variable"
    )
    FNLResult<List<UsageLocation>> findUsages(String symbolName);

    /**
     * Gets the definition of a symbol.
     *
     * @param symbolName the symbol to find
     * @return result containing definition location
     */
    @AgentAction(
        name = "find_definition",
        description = "Finds where a symbol is defined"
    )
    FNLResult<SymbolInfo> findDefinition(String symbolName);

    /**
     * Gets the call hierarchy for a method.
     *
     * @param methodName the method name
     * @param incoming true for callers, false for callees
     * @return result containing call hierarchy
     */
    @AgentAction(
        name = "call_hierarchy",
        description = "Shows methods that call or are called by a method"
    )
    FNLResult<List<CallHierarchyItem>> getCallHierarchy(String methodName, boolean incoming);

    // ==================== Code Metrics ====================

    /**
     * Calculates code metrics for a file or directory.
     *
     * @param path the file or directory path
     * @return result containing code metrics
     */
    @AgentAction(
        name = "code_metrics",
        description = "Calculates lines of code, complexity, and other metrics"
    )
    FNLResult<CodeMetrics> getCodeMetrics(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    // ==================== Nested Types ====================

    /**
     * Comprehensive project information.
     *
     * @param name project name
     * @param projectType detected project type
     * @param primaryLanguage main programming language
     * @param languages all detected languages with file counts
     * @param buildTool detected build tool
     * @param framework detected framework (Spring, Express, etc.)
     * @param dependencies list of dependencies
     * @param sourceRoots source directory paths
     * @param testRoots test directory paths
     */
    record ProjectInfo(
        String name,
        ProjectType projectType,
        String primaryLanguage,
        Map<String, Integer> languages,
        String buildTool,
        String framework,
        List<Dependency> dependencies,
        List<String> sourceRoots,
        List<String> testRoots
    ) {}

    /**
     * Project types supported.
     */
    enum ProjectType {
        MAVEN, GRADLE, NPM, YARN, PNPM, CARGO, GO_MODULE,
        PIP, POETRY, COMPOSER, DOTNET, UNKNOWN
    }

    /**
     * A project dependency.
     *
     * @param name dependency name (groupId:artifactId for Maven)
     * @param version version string
     * @param scope dependency scope (compile, test, dev, etc.)
     * @param isDirect true if directly declared, false if transitive
     */
    record Dependency(
        String name,
        String version,
        String scope,
        boolean isDirect
    ) {}

    /**
     * Directory tree node.
     *
     * @param name node name
     * @param path full path
     * @param isDirectory whether this is a directory
     * @param children child nodes (for directories)
     * @param fileCount number of files (for directories)
     */
    record DirectoryNode(
        String name,
        String path,
        boolean isDirectory,
        List<DirectoryNode> children,
        int fileCount
    ) {}

    /**
     * Code symbol information.
     *
     * @param name symbol name
     * @param kind symbol kind
     * @param filePath file containing the symbol
     * @param startLine starting line number
     * @param endLine ending line number
     * @param signature full signature (for methods)
     * @param modifiers access modifiers
     * @param documentation extracted documentation
     */
    record SymbolInfo(
        String name,
        SymbolKind kind,
        String filePath,
        int startLine,
        int endLine,
        String signature,
        List<String> modifiers,
        String documentation
    ) {}

    /**
     * Symbol kinds.
     */
    enum SymbolKind {
        CLASS, INTERFACE, ENUM, RECORD, ANNOTATION,
        METHOD, CONSTRUCTOR, FIELD, CONSTANT,
        FUNCTION, VARIABLE, PARAMETER,
        MODULE, PACKAGE, NAMESPACE
    }

    /**
     * Location where a symbol is used.
     *
     * @param filePath file path
     * @param line line number
     * @param column column number
     * @param context surrounding code context
     * @param usageKind how the symbol is used
     */
    record UsageLocation(
        String filePath,
        int line,
        int column,
        String context,
        UsageKind usageKind
    ) {}

    /**
     * How a symbol is used.
     */
    enum UsageKind {
        READ, WRITE, CALL, TYPE_REFERENCE, IMPORT, EXTEND, IMPLEMENT
    }

    /**
     * Item in a call hierarchy.
     *
     * @param name method name
     * @param filePath file path
     * @param line line number
     * @param children nested calls
     */
    record CallHierarchyItem(
        String name,
        String filePath,
        int line,
        List<CallHierarchyItem> children
    ) {}

    /**
     * Code metrics for a file or directory.
     *
     * @param path analyzed path
     * @param linesOfCode total lines of code (excluding blanks/comments)
     * @param blankLines blank line count
     * @param commentLines comment line count
     * @param fileCount number of source files
     * @param classCount number of classes
     * @param methodCount number of methods
     * @param avgMethodComplexity average cyclomatic complexity
     * @param maxMethodComplexity maximum method complexity
     */
    record CodeMetrics(
        String path,
        int linesOfCode,
        int blankLines,
        int commentLines,
        int fileCount,
        int classCount,
        int methodCount,
        double avgMethodComplexity,
        int maxMethodComplexity
    ) {}
}
