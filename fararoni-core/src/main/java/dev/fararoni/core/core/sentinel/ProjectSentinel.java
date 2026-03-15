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
package dev.fararoni.core.core.sentinel;

import dev.fararoni.core.core.index.IndexStore;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ProjectSentinel implements Runnable, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ProjectSentinel.class.getName());

    private final Path rootPath;

    private final IndexStore indexStore;

    private final WatchService watchService;

    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();

    private volatile boolean running = true;

    private Thread sentinelThread;

    public ProjectSentinel(Path rootPath, IndexStore indexStore) throws IOException {
        this.rootPath = rootPath.toAbsolutePath();
        this.indexStore = indexStore;
        this.watchService = FileSystems.getDefault().newWatchService();

        registerDirectoryTree(this.rootPath);

        LOG.info("[SENTINEL] Inicializado para: " + this.rootPath);
    }

    private void registerDirectoryTree(Path start) throws IOException {
        Files.walk(start)
            .filter(Files::isDirectory)
            .filter(this::isNotIgnored)
            .forEach(this::registerDirectory);
    }

    private void registerDirectory(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE
            );
            watchKeys.put(key, dir);
            LOG.fine("[SENTINEL] Directorio registrado: " + dir);
        } catch (IOException e) {
            LOG.warning("[SENTINEL] No se pudo registrar directorio: " + dir + " - " + e.getMessage());
        }
    }

    private boolean isNotIgnored(Path path) {
        String s = path.toString();
        return !s.contains(".git")
            && !s.contains("target")
            && !s.contains("build")
            && !s.contains("node_modules")
            && !s.contains(".idea")
            && !s.contains(".vscode")
            && !s.contains("__pycache__")
            && !s.contains(".gradle");
    }

    @Override
    public void run() {
        LOG.info("[SENTINEL] Iniciando vigilancia de: " + rootPath);

        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("[SENTINEL] Vigilancia interrumpida");
                return;
            } catch (ClosedWatchServiceException e) {
                LOG.info("[SENTINEL] WatchService cerrado");
                return;
            }

            Path dir = watchKeys.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                handleEvent(dir, event);
            }

            boolean valid = key.reset();
            if (!valid) {
                watchKeys.remove(key);
                LOG.fine("[SENTINEL] Directorio removido de vigilancia: " + dir);
            }
        }

        LOG.info("[SENTINEL] Loop de vigilancia terminado");
    }

    private void handleEvent(Path dir, WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) {
            LOG.warning("[SENTINEL] Overflow detectado - algunos eventos pueden haberse perdido");
            return;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path name = ev.context();
        Path child = dir.resolve(name);

        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            handleCreate(child);
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            handleDelete(child);
        }
    }

    private void handleCreate(Path child) {
        if (Files.isDirectory(child)) {
            if (isNotIgnored(child)) {
                registerDirectory(child);
                LOG.info("[SENTINEL] Nuevo directorio detectado: " + child.getFileName());
            }
        } else if (isSourceFile(child)) {
            LOG.info("[SENTINEL] Nuevo archivo detectado: " + child.getFileName());
            indexStore.registerFile(child);
        }
    }

    private void handleDelete(Path child) {
        if (isSourceFile(child)) {
            LOG.info("[SENTINEL] Archivo eliminado: " + child.getFileName());
            indexStore.delete(child);
        }
    }

    private boolean isSourceFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".js") ||
            name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".jsx") ||
            name.endsWith(".go") || name.endsWith(".rs") || name.endsWith(".rb") ||
            name.endsWith(".kt") || name.endsWith(".scala") || name.endsWith(".cpp") ||
            name.endsWith(".cc") || name.endsWith(".cxx") || name.endsWith(".hpp") ||
            name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".cs") ||
            name.endsWith(".php") || name.endsWith(".swift") || name.endsWith(".dart")) {
            return true;
        }

        if (name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".yaml") ||
            name.endsWith(".yml") || name.endsWith(".toml") || name.endsWith(".ini") ||
            name.endsWith(".properties")) {
            return true;
        }

        if (name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".rst") ||
            name.endsWith(".adoc")) {
            return true;
        }

        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".css") ||
            name.endsWith(".scss") || name.endsWith(".less")) {
            return true;
        }

        if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh") ||
            name.endsWith(".fish") || name.endsWith(".ps1") || name.endsWith(".bat")) {
            return true;
        }

        if (name.endsWith(".gradle") || name.endsWith(".sbt") ||
            name.equals("dockerfile") || name.endsWith(".dockerfile") ||
            name.equals("makefile") || name.endsWith(".makefile") || name.endsWith(".mk")) {
            return true;
        }

        if (name.endsWith(".sql") || name.endsWith(".graphql") || name.endsWith(".gql") ||
            name.endsWith(".proto")) {
            return true;
        }

        return false;
    }

    public void start() {
        this.sentinelThread = Thread.ofVirtual()
            .name("ProjectSentinel-" + rootPath.getFileName())
            .start(this);

        LOG.info("[SENTINEL] Virtual Thread iniciado");
    }

    @Override
    public void close() {
        running = false;

        try {
            watchService.close();
        } catch (IOException e) {
            LOG.warning("[SENTINEL] Error cerrando WatchService: " + e.getMessage());
        }

        if (sentinelThread != null) {
            try {
                sentinelThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        watchKeys.clear();
        LOG.info("[SENTINEL] Vigilancia detenida para: " + rootPath);
    }

    public boolean isRunning() {
        return running && sentinelThread != null && sentinelThread.isAlive();
    }

    public Path getRootPath() {
        return rootPath;
    }

    public int getWatchedDirectoryCount() {
        return watchKeys.size();
    }
}
