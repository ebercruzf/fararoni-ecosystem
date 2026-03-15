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
package dev.fararoni.core.core.security.auth;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class SecurityConstants {
    private SecurityConstants() {}

    private static final Path FARARONI_HOME = Paths.get(
            System.getProperty("user.home"), ".fararoni");

    public static final Path SECRET_PATH = FARARONI_HOME.resolve("secret.bin");

    public static final Path MASTER_PATH = FARARONI_HOME.resolve("master.bin");

    public static final Path GUESTS_PATH = FARARONI_HOME.resolve("guests.txt");

    public static final Path SECURITY_YML = FARARONI_HOME.resolve("config").resolve("security.yml");

    public static final Path AUDIT_DIR = FARARONI_HOME.resolve("audit");

    public static final Path AUDIT_LOG = AUDIT_DIR.resolve("security.log");

    public static final long SESSION_DURATION_MS = 1000L * 60 * 60 * 4;

    public static final long ADMIN_DURATION_MS = 1000L * 60 * 15;

    public static final int BCRYPT_SALT_ROUNDS = 12;

    public static final String TOTP_PATTERN = "^\\d{6}$";

    public static final String SUDO_PREFIX = "sudo ";

    public static final String CMD_AUTHORIZE = "fara autorizar ";

    public static final String CMD_REVOKE = "fara revocar ";

    public static final String CMD_LIST_GUESTS = "fara lista-invitados";

    public static final String[] LOGOUT_COMMANDS = {"salir", "logout", "cerrar"};

    public static final String TOTP_ISSUER = "Fararoni-Agente";

    public static final String POSIX_OWNER_ONLY = "rw-------";

    public static final String POSIX_OWNER_GROUP_READ = "rw-r-----";
}
