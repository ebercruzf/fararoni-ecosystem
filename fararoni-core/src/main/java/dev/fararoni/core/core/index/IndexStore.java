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
package dev.fararoni.core.core.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IndexStore implements ProjectKnowledgeBase, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(IndexStore.class.getName());

    private static final String DEFAULT_DB_DIR = ".fararoni";

    private static final String DB_FILE_NAME = "fararoni-index";

    private static final int MAX_PATH_LENGTH = 4096;

    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS file_manifest (
            path VARCHAR(4096) PRIMARY KEY,
            last_modified BIGINT NOT NULL,
            content_hash VARCHAR(64) NOT NULL,
            parse_status VARCHAR(20) DEFAULT 'SUCCESS',
            language VARCHAR(20),
            indexed_at BIGINT NOT NULL
        )
        """;

    private static final String CREATE_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_status ON file_manifest(parse_status)
        """;

    private static final String INSERT_OR_UPDATE = """
        MERGE INTO file_manifest (path, last_modified, content_hash, parse_status, language, indexed_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_BY_PATH = """
        SELECT path, last_modified, content_hash, parse_status, language, indexed_at
        FROM file_manifest WHERE path = ?
        """;

    private static final String DELETE_BY_PATH = """
        DELETE FROM file_manifest WHERE path = ?
        """;

    private static final String SELECT_ALL = """
        SELECT path, last_modified, content_hash, parse_status, language, indexed_at
        FROM file_manifest
        """;

    private static final String COUNT_BY_STATUS = """
        SELECT parse_status, COUNT(*) as cnt FROM file_manifest GROUP BY parse_status
        """;

    private static final String COUNT_TOTAL = """
        SELECT COUNT(*) FROM file_manifest
        """;

    private static final String UPDATE_STATUS = """
        UPDATE file_manifest SET parse_status = ?, indexed_at = ? WHERE path = ?
        """;

    private Connection connection;

    private final Path dbPath;

    private volatile boolean available = false;

    public IndexStore() {
        this(resolveDefaultDbPath());
    }

    public IndexStore(Path dbPath) {
        this.dbPath = dbPath;
        initialize();
    }

    public static IndexStore inMemory() {
        IndexStore store = new IndexStore(null);
        return store;
    }

    private void initialize() {
        try {
            String jdbcUrl;
            if (dbPath == null) {
                String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);
                jdbcUrl = "jdbc:h2:mem:fararoni-index-" + uniqueId;
            } else {
                Path parent = dbPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }
                jdbcUrl = "jdbc:h2:file:" + dbPath.toAbsolutePath() + ";AUTO_SERVER=TRUE";
            }

            connection = DriverManager.getConnection(jdbcUrl);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_TABLE);
                stmt.execute(CREATE_INDEX);
            }

            available = true;
            LOG.info("[IndexStore] Initialized: " + (dbPath != null ? dbPath : "in-memory"));
        } catch (SQLException | IOException e) {
            LOG.log(Level.WARNING, "[IndexStore] Initialization failed, continuing without persistence", e);
            available = false;
        }
    }

    private static Path resolveDefaultDbPath() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, DEFAULT_DB_DIR, DB_FILE_NAME);
    }

    public boolean needsReindexing(Path file, String currentHash) {
        if (!available || file == null || currentHash == null) {
            return true;
        }

        Optional<FileManifest> manifest = getManifest(file);
        if (manifest.isEmpty()) {
            return true;
        }

        return !ContentHasher.equals(manifest.get().contentHash(), currentHash);
    }

    public void updateManifest(FileManifest manifest) {
        if (!available || manifest == null) {
            return;
        }

        String pathStr = manifest.path().toAbsolutePath().toString();
        if (pathStr.length() > MAX_PATH_LENGTH) {
            LOG.warning("[IndexStore] Path too long, skipping: " + pathStr.substring(0, 100) + "...");
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement(INSERT_OR_UPDATE)) {
            stmt.setString(1, pathStr);
            stmt.setLong(2, manifest.lastModified());
            stmt.setString(3, manifest.contentHash());
            stmt.setString(4, manifest.parseStatus().name());
            stmt.setString(5, manifest.language());
            stmt.setLong(6, manifest.indexedAt());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to update manifest: " + pathStr, e);
        }
    }

    public void markAsUnparseable(Path file, String reason) {
        if (!available || file == null) {
            return;
        }

        String pathStr = file.toAbsolutePath().toString();

        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_STATUS)) {
            stmt.setString(1, FileManifest.ParseStatus.UNPARSEABLE.name());
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, pathStr);
            int updated = stmt.executeUpdate();

            if (updated == 0) {
                try {
                    String hash = ContentHasher.hashFile(file);
                    long lastModified = Files.getLastModifiedTime(file).toMillis();
                    FileManifest manifest = FileManifest.unparseable(file, lastModified, hash);
                    updateManifest(manifest);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "[IndexStore] Failed to hash file for unparseable: " + pathStr, e);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to mark as unparseable: " + pathStr, e);
        }

        LOG.fine("[IndexStore] Marked as unparseable: " + pathStr + " - " + reason);
    }

    public void markAsFailed(Path file, String reason) {
        if (!available || file == null) {
            return;
        }

        String pathStr = file.toAbsolutePath().toString();

        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_STATUS)) {
            stmt.setString(1, FileManifest.ParseStatus.FAILED.name());
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, pathStr);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to mark as failed: " + pathStr, e);
        }

        LOG.fine("[IndexStore] Marked as failed: " + pathStr + " - " + reason);
    }

    public Optional<FileManifest> getManifest(Path file) {
        if (!available || file == null) {
            return Optional.empty();
        }

        String pathStr = file.toAbsolutePath().toString();

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_PATH)) {
            stmt.setString(1, pathStr);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(resultSetToManifest(rs));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to get manifest: " + pathStr, e);
        }

        return Optional.empty();
    }

    public boolean delete(Path file) {
        if (!available || file == null) {
            return false;
        }

        String pathStr = file.toAbsolutePath().toString();

        try (PreparedStatement stmt = connection.prepareStatement(DELETE_BY_PATH)) {
            stmt.setString(1, pathStr);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to delete: " + pathStr, e);
            return false;
        }
    }

    public int pruneDeleted() {
        if (!available) {
            return 0;
        }

        List<String> toDelete = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {
            while (rs.next()) {
                String pathStr = rs.getString("path");
                Path path = Path.of(pathStr);
                if (!Files.exists(path)) {
                    toDelete.add(pathStr);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to query for pruning", e);
            return 0;
        }

        int deleted = 0;
        for (String pathStr : toDelete) {
            try (PreparedStatement stmt = connection.prepareStatement(DELETE_BY_PATH)) {
                stmt.setString(1, pathStr);
                if (stmt.executeUpdate() > 0) {
                    deleted++;
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "[IndexStore] Failed to delete pruned: " + pathStr, e);
            }
        }

        if (deleted > 0) {
            LOG.info("[IndexStore] Pruned " + deleted + " deleted files");
        }

        return deleted;
    }

    public IndexStats getStats() {
        if (!available) {
            return IndexStats.EMPTY;
        }

        long total = 0;
        long success = 0;
        long failed = 0;
        long unparseable = 0;
        long pending = 0;

        try (Statement stmt = connection.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(COUNT_TOTAL)) {
                if (rs.next()) {
                    total = rs.getLong(1);
                }
            }

            try (ResultSet rs = stmt.executeQuery(COUNT_BY_STATUS)) {
                while (rs.next()) {
                    String status = rs.getString("parse_status");
                    long count = rs.getLong("cnt");

                    switch (status) {
                        case "SUCCESS" -> success = count;
                        case "FAILED" -> failed = count;
                        case "UNPARSEABLE" -> unparseable = count;
                        case "PENDING" -> pending = count;
                    }
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[IndexStore] Failed to get stats", e);
            return IndexStats.EMPTY;
        }

        return new IndexStats(total, success, failed, unparseable, pending);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public Path getDbPath() {
        return dbPath;
    }

    public String getProjectStructureMap() {
        return getProjectStructureMap(Path.of(System.getProperty("user.dir")));
    }

    public String getProjectStructureMap(Path workspaceRoot) {
        if (!available) {
            return "(Index not available)";
        }

        StringBuilder map = new StringBuilder();

        try {
            var stats = getStats();
            map.append("Project Stats: ")
               .append(stats.totalFiles()).append(" files indexed, ")
               .append(stats.successFiles()).append(" parsed successfully.\n\n");

            String sql = """
                SELECT path
                FROM file_manifest
                ORDER BY path ASC
                LIMIT 1000
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                map.append("Directory Structure (Depth-Aware View):\n");

                java.util.Set<String> printedDirs = new java.util.LinkedHashSet<>();
                int visibleItems = 0;
                final int MAX_VISIBLE_ITEMS = 80;

                Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();

                while (rs.next() && visibleItems < MAX_VISIBLE_ITEMS) {
                    String pathStr = rs.getString("path");
                    Path absolutePath = Path.of(pathStr);

                    Path p;
                    try {
                        if (absolutePath.startsWith(normalizedRoot)) {
                            p = normalizedRoot.relativize(absolutePath);
                        } else {
                            p = absolutePath.getFileName();
                            if (p == null) continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }

                    if (isNoise(p)) continue;

                    if (p.getNameCount() >= 1) {
                        String root = p.getName(0).toString();
                        if (printedDirs.add("L1:" + root)) {
                            boolean isDir = p.getNameCount() > 1;
                            map.append(isDir ? "[D]" : "[F]")
                               .append(root)
                               .append(isDir ? "/" : "")
                               .append("\n");
                            visibleItems++;
                        }
                    }

                    if (p.getNameCount() >= 2 && visibleItems < MAX_VISIBLE_ITEMS) {
                        String root = p.getName(0).toString();
                        String sub = p.getName(1).toString();
                        String key = "L2:" + root + "/" + sub;

                        if (printedDirs.add(key) && !isNoiseComponent(sub)) {
                            boolean isDir = p.getNameCount() > 2;
                            map.append("   └── ")
                               .append(isDir ? "[D]" : "[F]")
                               .append(sub)
                               .append(isDir ? "/" : "")
                               .append("\n");
                            visibleItems++;
                        }
                    }
                }

                if (visibleItems >= MAX_VISIBLE_ITEMS) {
                    map.append("   ... (truncated for efficiency, ")
                       .append(stats.totalFiles()).append(" total files)\n");
                }
            }
        } catch (SQLException e) {
            LOG.warning("[IndexStore] Error generando mapa de proyecto: " + e.getMessage());
            return "(Structure map error: " + e.getMessage() + ")";
        }

        return map.toString();
    }

    public String getJavaFilesMap(Path workspaceRoot) {
        if (!available) {
            System.out.println("[DEBUG-INDEX] IndexStore no disponible");
            return "";
        }

        StringBuilder map = new StringBuilder();
        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        System.out.println("[DEBUG-INDEX] Buscando archivos Java para workspace: " + normalizedRoot);

        String sql = """
            SELECT path FROM file_manifest
            WHERE path LIKE '%.java'
            AND path LIKE ?
            ORDER BY path ASC
            LIMIT 50
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, normalizedRoot.toString() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                int skippedExample = 0;

                while (rs.next()) {
                    String pathStr = rs.getString("path");

                    if (pathStr.contains("com/example") || pathStr.contains("com\\example")) {
                        skippedExample++;
                        continue;
                    }

                    Path absolutePath = Path.of(pathStr);

                    if (absolutePath.startsWith(normalizedRoot)) {
                        Path relative = normalizedRoot.relativize(absolutePath);
                        String relativeStr = relative.toString();

                        if (relativeStr.contains("src/main/java") || relativeStr.contains("src\\main\\java")) {
                            map.append("- ").append(relativeStr).append("\n");
                            count++;
                        }
                    }
                }

                System.out.println("[DEBUG-INDEX] Archivos encontrados: " + count + ", saltados (example): " + skippedExample);

                if (count == 0) {
                    return "";
                }
            }
        } catch (SQLException e) {
            System.out.println("[DEBUG-INDEX] Error SQL: " + e.getMessage());
            return "";
        }

        return map.toString();
    }

    private boolean isNoise(Path p) {
        for (Path part : p) {
            if (isNoiseComponent(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isNoiseComponent(String name) {
        if (name.startsWith(".")) return true;

        if (name.equals("target") || name.equals("build") || name.equals("out") || name.equals("dist")) return true;

        if (name.equals("node_modules") || name.equals("__pycache__") || name.equals("venv") || name.equals(".venv")) return true;

        if (name.equals("cache") || name.equals(".cache") || name.equals("tmp") || name.equals("temp")) return true;

        return false;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                LOG.info("[IndexStore] Closed");
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "[IndexStore] Error closing connection", e);
            }
        }
        available = false;
    }

    private FileManifest resultSetToManifest(ResultSet rs) throws SQLException {
        return new FileManifest(
            Path.of(rs.getString("path")),
            rs.getLong("last_modified"),
            rs.getString("content_hash"),
            FileManifest.ParseStatus.valueOf(rs.getString("parse_status")),
            rs.getString("language"),
            rs.getLong("indexed_at")
        );
    }

    public List<Path> getAllJavaFiles(Path workspaceRoot) {
        List<Path> result = new ArrayList<>();

        if (!available || connection == null) {
            LOG.warning("[INDEX] getAllJavaFiles: Index not available");
            return result;
        }

        String workspacePrefix = workspaceRoot.toAbsolutePath().toString();
        System.out.println("[DEBUG-INDEX] Buscando archivos Java para workspace: " + workspacePrefix);

        String sql = """
            SELECT path FROM file_manifest
            WHERE LOWER(path) LIKE '%.java'
            ORDER BY path ASC
            """;

        int total = 0;
        int skipped = 0;

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String pathStr = rs.getString("path");
                total++;

                if (!pathStr.startsWith(workspacePrefix)) {
                    continue;
                }

                if (pathStr.contains("com/example") || pathStr.contains("com\\example")) {
                    skipped++;
                    continue;
                }

                if (pathStr.contains("/target/") || pathStr.contains("/build/") ||
                    pathStr.contains("\\target\\") || pathStr.contains("\\build\\")) {
                    continue;
                }

                result.add(Path.of(pathStr));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[INDEX] Error en getAllJavaFiles: " + e.getMessage(), e);
        }

        System.out.println("[DEBUG-INDEX] Archivos encontrados: " + result.size() +
                          ", saltados (example): " + skipped);
        System.out.println("[INDEX] getAllJavaFiles: " + result.size() + " archivos encontrados");

        return result;
    }

    @Override
    public void registerFile(Path absolutePath) {
        if (!available || absolutePath == null) {
            return;
        }

        try {
            if (!Files.exists(absolutePath) || Files.isDirectory(absolutePath)) {
                return;
            }

            long lastModified = Files.getLastModifiedTime(absolutePath).toMillis();
            String hash = ContentHasher.hashFile(absolutePath);
            String language = detectLanguage(absolutePath);

            FileManifest manifest = FileManifest.success(
                absolutePath, lastModified, hash, language
            );

            updateManifest(manifest);

            LOG.info("[INDEX] Archivo registrado: " + absolutePath.getFileName());
        } catch (IOException e) {
            LOG.warning("[INDEX] Error registrando archivo: " + absolutePath + " - " + e.getMessage());
        }
    }

    private String detectLanguage(Path path) {
        if (path == null || path.getFileName() == null) {
            return "unknown";
        }

        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "javascript";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".rs")) return "rust";
        if (name.endsWith(".rb")) return "ruby";
        if (name.endsWith(".kt")) return "kotlin";
        if (name.endsWith(".scala")) return "scala";
        if (name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".cxx") || name.endsWith(".hpp")) return "cpp";
        if (name.endsWith(".c") || name.endsWith(".h")) return "c";
        if (name.endsWith(".cs")) return "csharp";
        if (name.endsWith(".php")) return "php";
        if (name.endsWith(".swift")) return "swift";
        if (name.endsWith(".dart")) return "dart";

        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".xml")) return "xml";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
        if (name.endsWith(".toml")) return "toml";
        if (name.endsWith(".ini") || name.endsWith(".properties")) return "config";

        if (name.endsWith(".md")) return "markdown";
        if (name.endsWith(".txt")) return "text";
        if (name.endsWith(".rst")) return "restructuredtext";
        if (name.endsWith(".adoc")) return "asciidoc";

        if (name.endsWith(".html") || name.endsWith(".htm")) return "html";
        if (name.endsWith(".css")) return "css";
        if (name.endsWith(".scss") || name.endsWith(".less")) return "css";

        if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh") || name.endsWith(".fish")) return "shell";
        if (name.endsWith(".ps1")) return "powershell";
        if (name.endsWith(".bat")) return "batch";

        if (name.endsWith(".gradle")) return "gradle";
        if (name.endsWith(".sbt")) return "sbt";
        if (name.equals("dockerfile") || name.endsWith(".dockerfile")) return "dockerfile";
        if (name.equals("makefile") || name.endsWith(".makefile") || name.endsWith(".mk")) return "makefile";

        if (name.endsWith(".sql")) return "sql";
        if (name.endsWith(".graphql") || name.endsWith(".gql")) return "graphql";
        if (name.endsWith(".proto")) return "protobuf";

        return "unknown";
    }

    @Override
    public void refresh() {
        if (!available) {
            LOG.warning("[IndexStore] refresh() called but index not available");
            return;
        }

        LOG.info("[IndexStore] Refreshing index view...");

        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(PASSIVE)");
        } catch (SQLException e) {
            LOG.warning("[IndexStore] WAL checkpoint failed: " + e.getMessage());
        }
    }

    @Override
    public String generateTreeView(String rootPathStr) {
        if (!available) {
            return "(Index not available)";
        }

        Path startPath;
        try {
            startPath = Path.of(rootPathStr).toAbsolutePath().normalize();
        } catch (Exception e) {
            startPath = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[D]").append(startPath.getFileName()).append("/\n");

        String sql = """
            SELECT path FROM file_manifest
            WHERE path LIKE ?
            ORDER BY path ASC
            LIMIT 200
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, startPath + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                java.util.Set<String> printedDirs = new java.util.LinkedHashSet<>();
                int itemCount = 0;
                final int MAX_ITEMS = 80;

                while (rs.next() && itemCount < MAX_ITEMS) {
                    String pathStr = rs.getString("path");
                    Path absolutePath = Path.of(pathStr);

                    Path relative;
                    try {
                        if (absolutePath.startsWith(startPath)) {
                            relative = startPath.relativize(absolutePath);
                        } else {
                            continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }

                    if (isNoise(relative)) continue;

                    int depth = relative.getNameCount();
                    if (depth > 4) continue;

                    for (int level = 1; level <= depth; level++) {
                        String pathKey = relative.subpath(0, level).toString();

                        if (printedDirs.add(pathKey)) {
                            String indent = "  ".repeat(level);
                            String name = relative.getName(level - 1).toString();
                            boolean isFile = (level == depth);

                            sb.append(indent)
                              .append("├─ ")
                              .append(isFile ? "[F]" : "[D]")
                              .append(name)
                              .append(isFile ? "" : "/")
                              .append("\n");

                            itemCount++;
                        }
                    }
                }

                if (itemCount >= MAX_ITEMS) {
                    sb.append("  ... (truncado, ver getProjectStructureMap() para más)\n");
                }
            }
        } catch (SQLException e) {
            LOG.warning("[IndexStore] Error generando TreeView: " + e.getMessage());
            return "(Error generating tree: " + e.getMessage() + ")";
        }

        return sb.toString();
    }

    @Override
    public String generateHighLevelMap() {
        if (!available) {
            return "(Index not available)";
        }

        Path workspaceRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        StringBuilder sb = new StringBuilder();

        String sql = """
            SELECT path FROM file_manifest
            ORDER BY path ASC
            LIMIT 500
            """;

        try {
            var stats = getStats();
            sb.append("Project Skeleton (")
              .append(stats.totalFiles())
              .append(" files total):\n");

            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                java.util.Set<String> printedL1 = new java.util.LinkedHashSet<>();
                java.util.Set<String> printedL2 = new java.util.LinkedHashSet<>();
                int lineCount = 0;
                final int MAX_LINES = 20;

                while (rs.next() && lineCount < MAX_LINES) {
                    String pathStr = rs.getString("path");
                    Path absolutePath = Path.of(pathStr);

                    Path relative;
                    try {
                        if (absolutePath.startsWith(workspaceRoot)) {
                            relative = workspaceRoot.relativize(absolutePath);
                        } else {
                            continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }

                    if (isNoise(relative)) continue;

                    int depth = relative.getNameCount();

                    if (depth >= 1) {
                        String name = relative.getName(0).toString();
                        if (printedL1.add(name)) {
                            boolean isDir = depth > 1;
                            sb.append(isDir ? "[D]" : "[F]")
                              .append(name)
                              .append(isDir ? "/" : "")
                              .append("\n");
                            lineCount++;
                        }
                    }

                    if (depth >= 2 && lineCount < MAX_LINES) {
                        String parent = relative.getName(0).toString();
                        String child = relative.getName(1).toString();
                        String key = parent + "/" + child;

                        boolean isDir = depth > 2;
                        if (isDir && printedL2.add(key) && !isNoiseComponent(child)) {
                            sb.append("   └─ [D]")
                              .append(child)
                              .append("/\n");
                            lineCount++;
                        }
                    }
                }
            }

            sb.append("[Use 'ListFiles' to explore deeper]");
        } catch (SQLException e) {
            LOG.warning("[IndexStore] Error generando HighLevelMap: " + e.getMessage());
            return "(Error generating map: " + e.getMessage() + ")";
        }

        return sb.toString();
    }

    @Override
    public String generateMap(ContextProfile profile) {
        if (!available || connection == null) {
            return "(Index not available)";
        }

        StringBuilder sb = new StringBuilder();
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        String sql = "SELECT path FROM file_manifest ORDER BY path ASC LIMIT ?";

        try {
            var stats = getStats();
            sb.append(String.format("Project Map [%s Mode] (%d files, Depth: %d):\n",
                profile.name(), stats.totalFiles(), profile.maxDepth));

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, profile.maxLines * 3);

                try (ResultSet rs = stmt.executeQuery()) {
                java.util.Set<String> printedNodes = new java.util.HashSet<>();
                int lineCount = 0;

                while (rs.next() && lineCount < profile.maxLines) {
                    String pathStr = rs.getString("path");
                    Path absPath = Path.of(pathStr);
                    Path rel;

                    try {
                        if (absPath.startsWith(root)) {
                            rel = root.relativize(absPath);
                        } else {
                            continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }

                    if (isNoise(rel)) continue;

                    int depth = rel.getNameCount();

                    if (depth > profile.maxDepth) continue;

                    for (int i = 1; i <= depth && lineCount < profile.maxLines; i++) {
                        String subpath = rel.subpath(0, i).toString();

                        if (printedNodes.add(subpath)) {
                            String indent = "  ".repeat(i - 1);
                            String name = rel.getName(i - 1).toString();
                            boolean isLastPart = (i == depth);

                            boolean isDir = !isLastPart || (depth > i);

                            if (isLastPart) {
                                isDir = java.nio.file.Files.isDirectory(absPath);
                            }

                            String icon = isDir ? "[D]" : "[F]";

                            sb.append(indent)
                              .append(i > 1 ? "├─ " : "")
                              .append(icon)
                              .append(name)
                              .append(isDir ? "/" : "")
                              .append("\n");
                            lineCount++;
                        }
                    }
                }

                if (lineCount >= profile.maxLines) {
                    sb.append("\n... [Truncated by ").append(profile.name()).append(" profile limit (")
                      .append(profile.maxLines).append(" lines)]\n");
                }
                }
            }

            if (profile == ContextProfile.SKELETAL || profile == ContextProfile.TACTICAL) {
                sb.append("\n[Tip: Use 'DeepScan' for full architectural view (10 levels)]");
            } else {
                sb.append("\n[Tip: Use 'ListFiles' to explore specific folders]");
            }

            if (profile == ContextProfile.STRATEGIC) {
                sb.append("\n\n--- KEY BUSINESS DEFINITIONS ---\n");
                List<Path> priorityFiles = getPriorityFiles(root);

                for (Path p : priorityFiles) {
                    if (sb.length() > STRATEGIC_HARD_CAP) {
                        sb.append("\n... [TRUNCATED: Context limit reached (")
                          .append(STRATEGIC_HARD_CAP).append(" chars)]\n");
                        break;
                    }

                    String content = readFileHead(p, 40);
                    if (content != null && !content.isBlank()) {
                        sb.append("\n[F]").append(root.relativize(p)).append(":\n");
                        sb.append("```\n").append(content).append("\n```\n");
                    }
                }
            }

            if (sb.length() > STRATEGIC_HARD_CAP) {
                sb.setLength(STRATEGIC_HARD_CAP);
                sb.append("\n... [HARD CAP REACHED]");
            }
        } catch (SQLException e) {
            LOG.warning("[IndexStore] Error generating map with profile " + profile + ": " + e.getMessage());
            return "(Map generation error: " + e.getMessage() + ")";
        }

        return sb.toString();
    }

    private static final int STRATEGIC_HARD_CAP = 20_000;

    private List<Path> getPriorityFiles(Path root) {
        List<Path> result = new ArrayList<>();

        Path readme = root.resolve("README.md");
        if (Files.exists(readme) && Files.isRegularFile(readme)) {
            result.add(readme);
        }

        Path pom = root.resolve("pom.xml");
        if (Files.exists(pom) && Files.isRegularFile(pom)) {
            result.add(pom);
        }

        try {
            String sql = """
                SELECT path FROM file_manifest
                WHERE (path LIKE '%/model/%' OR path LIKE '%/domain/%' OR path LIKE '%/entity/%')
                  AND path LIKE '%.java'
                ORDER BY path ASC
                LIMIT 8
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Path p = Path.of(rs.getString("path"));
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        result.add(p);
                    }
                }
            }
        } catch (SQLException e) {
            LOG.fine("[IndexStore] Error buscando archivos prioritarios: " + e.getMessage());
        }

        return result.size() > 10 ? result.subList(0, 10) : result;
    }

    private String readFileHead(Path path, int maxLines) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            int limit = Math.min(lines.size(), maxLines);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                sb.append(lines.get(i)).append("\n");
            }

            if (lines.size() > maxLines) {
                sb.append("... [").append(lines.size() - maxLines).append(" more lines]");
            }

            return sb.toString();
        } catch (IOException e) {
            LOG.fine("[IndexStore] Error leyendo archivo " + path + ": " + e.getMessage());
            return null;
        }
    }

    public record IndexStats(
        long totalFiles,
        long successFiles,
        long failedFiles,
        long unparseableFiles,
        long pendingFiles
    ) {
        public static final IndexStats EMPTY = new IndexStats(0, 0, 0, 0, 0);

        public double successRate() {
            if (totalFiles == 0) return 0.0;
            return (successFiles * 100.0) / totalFiles;
        }

        public String toReport() {
            return String.format(
                "[INDEX STATS] Total: %d, Success: %d (%.1f%%), Failed: %d, Unparseable: %d, Pending: %d",
                totalFiles, successFiles, successRate(), failedFiles, unparseableFiles, pendingFiles
            );
        }
    }
}
