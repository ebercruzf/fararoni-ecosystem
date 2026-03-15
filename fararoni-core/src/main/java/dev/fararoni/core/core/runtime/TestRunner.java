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
package dev.fararoni.core.core.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TestRunner {
    private static final Logger LOG = Logger.getLogger(TestRunner.class.getName());

    public enum TestFramework {
        MAVEN("mvn test", "pom.xml"),

        GRADLE("./gradlew test", "build.gradle", "build.gradle.kts"),

        NPM("npm test", "package.json"),

        PYTEST("pytest", "pytest.ini", "pyproject.toml", "setup.py"),

        PYTHON_UNITTEST("python -m unittest discover", "tests.py", "test_*.py"),

        CARGO("cargo test", "Cargo.toml"),

        GO("go test ./...", "go.mod"),

        MAKE("make test", "Makefile"),

        RUBY("bundle exec rake test", "Gemfile"),

        DOTNET("dotnet test", "*.csproj", "*.sln"),

        UNKNOWN("", "");

        private final String command;
        private final String[] indicators;

        TestFramework(String command, String... indicators) {
            this.command = command;
            this.indicators = indicators;
        }

        public String getCommand() {
            return command;
        }

        public String[] getIndicators() {
            return indicators;
        }
    }

    public record TestResult(
            boolean success,
            TestFramework framework,
            String command,
            String output,
            String errorLog,
            int exitCode,
            long durationMs,
            int testsRun,
            int testsFailed
    ) {
        public boolean isSuccess() {
            return success;
        }

        public String getFailureLog() {
            if (!errorLog.isBlank()) return errorLog;
            if (!success) return output;
            return "";
        }

        public String getSummary() {
            return String.format("[%s] %s - %d tests, %d failed (%dms)",
                    framework, success ? "PASSED" : "FAILED",
                    testsRun, testsFailed, durationMs);
        }
    }

    private final ShellSession session;
    private TestResult lastResult;

    private static final String[] VENV_DIRS = {
            "venv", ".venv", "env", ".env", "virtualenv", ".virtualenv"
    };

    private static final String[][] LOCAL_WRAPPERS = {
            {"./gradlew", "gradlew.bat"},
            {"./mvnw", "mvnw.cmd"},
            {"./vendor/bin/phpunit"},
            {"./node_modules/.bin/jest"},
            {"./node_modules/.bin/mocha"},
            {"./node_modules/.bin/vitest"},
    };

    public TestRunner(ShellSession session) {
        if (session == null) {
            throw new IllegalArgumentException("ShellSession cannot be null");
        }
        this.session = session;
    }

    public TestResult runTests(Path projectRoot) {
        Path root = projectRoot != null ? projectRoot : session.getCurrentDirectory();
        TestFramework detected = detectFramework(root);

        if (detected == TestFramework.UNKNOWN) {
            lastResult = new TestResult(false, TestFramework.UNKNOWN, "",
                    "", "No se detecto un sistema de pruebas conocido", -1, 0, 0, 0);
            LOG.warning("[TEST-RUNNER] No test framework detected at: " + root);
            return lastResult;
        }

        return runTests(root, detected);
    }

    public TestResult runTests(Path projectRoot, TestFramework framework) {
        Path root = projectRoot != null ? projectRoot : session.getCurrentDirectory();

        LOG.info("[TEST-RUNNER] Running " + framework + " tests at: " + root);

        Path originalDir = session.getCurrentDirectory();
        session.setCurrentDirectory(root);

        try {
            String command = framework.getCommand();
            ShellSession.CommandResult cmdResult = session.execute(command);

            TestMetrics metrics = parseTestMetrics(cmdResult.stdout(), cmdResult.stderr(), framework);

            lastResult = new TestResult(
                    cmdResult.isSuccess(),
                    framework,
                    command,
                    cmdResult.stdout(),
                    cmdResult.stderr(),
                    cmdResult.exitCode(),
                    cmdResult.durationMs(),
                    metrics.testsRun,
                    metrics.testsFailed
            );

            if (lastResult.success) {
                LOG.info("[TEST-RUNNER] " + lastResult.getSummary());
            } else {
                LOG.warning("[TEST-RUNNER] " + lastResult.getSummary());
            }

            return lastResult;
        } finally {
            session.setCurrentDirectory(originalDir);
        }
    }

    public TestResult runSpecificTests(Path projectRoot, String testPath) {
        Path root = projectRoot != null ? projectRoot : session.getCurrentDirectory();
        TestFramework framework = detectFramework(root);

        if (framework == TestFramework.UNKNOWN) {
            lastResult = new TestResult(false, TestFramework.UNKNOWN, "",
                    "", "No se detecto un sistema de pruebas conocido", -1, 0, 0, 0);
            return lastResult;
        }

        String command = buildSpecificTestCommand(framework, testPath);

        Path originalDir = session.getCurrentDirectory();
        session.setCurrentDirectory(root);

        try {
            ShellSession.CommandResult cmdResult = session.execute(command);
            TestMetrics metrics = parseTestMetrics(cmdResult.stdout(), cmdResult.stderr(), framework);

            lastResult = new TestResult(
                    cmdResult.isSuccess(),
                    framework,
                    command,
                    cmdResult.stdout(),
                    cmdResult.stderr(),
                    cmdResult.exitCode(),
                    cmdResult.durationMs(),
                    metrics.testsRun,
                    metrics.testsFailed
            );

            return lastResult;
        } finally {
            session.setCurrentDirectory(originalDir);
        }
    }

    public TestFramework detectFramework(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return TestFramework.UNKNOWN;
        }

        TestFramework[] priority = {
                TestFramework.MAVEN,
                TestFramework.GRADLE,
                TestFramework.CARGO,
                TestFramework.GO,
                TestFramework.NPM,
                TestFramework.PYTEST,
                TestFramework.RUBY,
                TestFramework.DOTNET,
                TestFramework.MAKE,
                TestFramework.PYTHON_UNITTEST
        };

        for (TestFramework framework : priority) {
            if (hasIndicator(projectRoot, framework)) {
                LOG.fine("[TEST-RUNNER] Detected framework: " + framework);
                return framework;
            }
        }

        return TestFramework.UNKNOWN;
    }

    private boolean hasIndicator(Path root, TestFramework framework) {
        for (String indicator : framework.getIndicators()) {
            if (indicator.contains("*")) {
                try {
                    boolean found = Files.list(root)
                            .anyMatch(p -> p.getFileName().toString().matches(
                                    indicator.replace("*", ".*")));
                    if (found) return true;
                } catch (Exception e) {
                }
            } else {
                if (Files.exists(root.resolve(indicator))) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<TestFramework> getAvailableFrameworks(Path projectRoot) {
        List<TestFramework> available = new ArrayList<>();
        for (TestFramework fw : TestFramework.values()) {
            if (fw != TestFramework.UNKNOWN && hasIndicator(projectRoot, fw)) {
                available.add(fw);
            }
        }
        return available;
    }

    private String buildSpecificTestCommand(TestFramework framework, String testPath) {
        return switch (framework) {
            case MAVEN -> "mvn test -Dtest=" + testPath;
            case GRADLE -> "./gradlew test --tests " + testPath;
            case NPM -> "npm test -- " + testPath;
            case PYTEST -> "pytest " + testPath;
            case GO -> "go test " + testPath;
            case CARGO -> "cargo test " + testPath;
            default -> framework.getCommand() + " " + testPath;
        };
    }

    private record TestMetrics(int testsRun, int testsFailed) {}

    private TestMetrics parseTestMetrics(String stdout, String stderr, TestFramework framework) {
        String combined = stdout + "\n" + stderr;
        int run = 0;
        int failed = 0;

        try {
            switch (framework) {
                case MAVEN -> {
                    var runMatch = java.util.regex.Pattern.compile("Tests run: (\\d+)").matcher(combined);
                    var failMatch = java.util.regex.Pattern.compile("Failures: (\\d+)").matcher(combined);
                    var errorMatch = java.util.regex.Pattern.compile("Errors: (\\d+)").matcher(combined);
                    if (runMatch.find()) run = Integer.parseInt(runMatch.group(1));
                    if (failMatch.find()) failed += Integer.parseInt(failMatch.group(1));
                    if (errorMatch.find()) failed += Integer.parseInt(errorMatch.group(1));
                }
                case GRADLE -> {
                    var match = java.util.regex.Pattern.compile("(\\d+) tests? completed").matcher(combined);
                    var failMatch = java.util.regex.Pattern.compile("(\\d+) failed").matcher(combined);
                    if (match.find()) run = Integer.parseInt(match.group(1));
                    if (failMatch.find()) failed = Integer.parseInt(failMatch.group(1));
                }
                case PYTEST -> {
                    var passMatch = java.util.regex.Pattern.compile("(\\d+) passed").matcher(combined);
                    var failMatch = java.util.regex.Pattern.compile("(\\d+) failed").matcher(combined);
                    if (passMatch.find()) run += Integer.parseInt(passMatch.group(1));
                    if (failMatch.find()) {
                        int f = Integer.parseInt(failMatch.group(1));
                        run += f;
                        failed = f;
                    }
                }
                case NPM -> {
                    var totalMatch = java.util.regex.Pattern.compile("(\\d+) total").matcher(combined);
                    var failMatch = java.util.regex.Pattern.compile("(\\d+) failed").matcher(combined);
                    if (totalMatch.find()) run = Integer.parseInt(totalMatch.group(1));
                    if (failMatch.find()) failed = Integer.parseInt(failMatch.group(1));
                }
                default -> {
                    failed = (int) combined.lines()
                            .filter(l -> l.contains("FAIL") || l.contains("ERROR"))
                            .count();
                }
            }
        } catch (Exception e) {
            LOG.fine("[TEST-RUNNER] Could not parse test metrics: " + e.getMessage());
        }

        return new TestMetrics(run, failed);
    }

    public TestResult getLastResult() {
        return lastResult;
    }

    public ShellSession getSession() {
        return session;
    }

    public Optional<Path> detectVirtualEnv(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return Optional.empty();
        }

        for (String venvDir : VENV_DIRS) {
            Path venvPath = projectRoot.resolve(venvDir);
            if (Files.isDirectory(venvPath)) {
                Path binDir = venvPath.resolve(isWindows() ? "Scripts" : "bin");
                Path pythonExe = binDir.resolve(isWindows() ? "python.exe" : "python");

                if (Files.isExecutable(pythonExe) || Files.exists(pythonExe)) {
                    LOG.info("[TEST-RUNNER] Detected virtualenv at: " + venvPath);
                    return Optional.of(venvPath);
                }
            }
        }

        return Optional.empty();
    }

    public Path getVenvPython(Path venvPath) {
        Path binDir = venvPath.resolve(isWindows() ? "Scripts" : "bin");
        return binDir.resolve(isWindows() ? "python.exe" : "python");
    }

    public Path getVenvPytest(Path venvPath) {
        Path binDir = venvPath.resolve(isWindows() ? "Scripts" : "bin");
        return binDir.resolve(isWindows() ? "pytest.exe" : "pytest");
    }

    public Optional<String> detectLocalWrapper(Path projectRoot, TestFramework framework) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return Optional.empty();
        }

        return switch (framework) {
            case GRADLE -> detectWrapper(projectRoot, "./gradlew", "gradlew.bat");
            case MAVEN -> detectWrapper(projectRoot, "./mvnw", "mvnw.cmd");
            case NPM -> detectWrapper(projectRoot, "./node_modules/.bin/jest",
                    "./node_modules/.bin/mocha", "./node_modules/.bin/vitest");
            default -> Optional.empty();
        };
    }

    private Optional<String> detectWrapper(Path root, String... wrappers) {
        for (String wrapper : wrappers) {
            Path wrapperPath = root.resolve(wrapper.startsWith("./") ?
                    wrapper.substring(2) : wrapper);
            if (Files.exists(wrapperPath)) {
                if (isWindows() && !wrapper.endsWith(".bat") && !wrapper.endsWith(".cmd")) {
                    Path batPath = root.resolve(wrapper.substring(2) + ".bat");
                    Path cmdPath = root.resolve(wrapper.substring(2) + ".cmd");
                    if (Files.exists(batPath)) return Optional.of(batPath.toString());
                    if (Files.exists(cmdPath)) return Optional.of(cmdPath.toString());
                }
                LOG.fine("[TEST-RUNNER] Detected local wrapper: " + wrapper);
                return Optional.of(wrapper);
            }
        }
        return Optional.empty();
    }

    public String buildSmartCommand(Path projectRoot, TestFramework framework) {
        Optional<String> wrapper = detectLocalWrapper(projectRoot, framework);
        if (wrapper.isPresent()) {
            return switch (framework) {
                case GRADLE -> wrapper.get() + " test";
                case MAVEN -> wrapper.get() + " test";
                default -> wrapper.get();
            };
        }

        if (framework == TestFramework.PYTEST || framework == TestFramework.PYTHON_UNITTEST) {
            Optional<Path> venv = detectVirtualEnv(projectRoot);
            if (venv.isPresent()) {
                if (framework == TestFramework.PYTEST) {
                    Path pytest = getVenvPytest(venv.get());
                    if (Files.exists(pytest)) {
                        return pytest.toString();
                    }
                    Path python = getVenvPython(venv.get());
                    return python + " -m pytest";
                } else {
                    Path python = getVenvPython(venv.get());
                    return python + " -m unittest discover";
                }
            }
        }

        return framework.getCommand();
    }

    public TestResult runTestsSmart(Path projectRoot) {
        Path root = projectRoot != null ? projectRoot : session.getCurrentDirectory();
        TestFramework detected = detectFramework(root);

        if (detected == TestFramework.UNKNOWN) {
            lastResult = new TestResult(false, TestFramework.UNKNOWN, "",
                    "", "No se detecto un sistema de pruebas conocido", -1, 0, 0, 0);
            LOG.warning("[TEST-RUNNER] No test framework detected at: " + root);
            return lastResult;
        }

        String smartCommand = buildSmartCommand(root, detected);
        LOG.info("[TEST-RUNNER] Running smart command: " + smartCommand);

        Path originalDir = session.getCurrentDirectory();
        session.setCurrentDirectory(root);

        try {
            ShellSession.CommandResult cmdResult = session.execute(smartCommand);
            TestMetrics metrics = parseTestMetrics(cmdResult.stdout(), cmdResult.stderr(), detected);

            lastResult = new TestResult(
                    cmdResult.isSuccess(),
                    detected,
                    smartCommand,
                    cmdResult.stdout(),
                    cmdResult.stderr(),
                    cmdResult.exitCode(),
                    cmdResult.durationMs(),
                    metrics.testsRun,
                    metrics.testsFailed
            );

            if (lastResult.success) {
                LOG.info("[TEST-RUNNER] " + lastResult.getSummary());
            } else {
                LOG.warning("[TEST-RUNNER] " + lastResult.getSummary());
            }

            return lastResult;
        } finally {
            session.setCurrentDirectory(originalDir);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
