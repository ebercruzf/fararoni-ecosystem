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
package dev.fararoni.core.core.commands;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.persistence.ChannelContactStore;
import dev.fararoni.core.core.persistence.ChannelContactStore.ChannelContact;
import dev.fararoni.core.core.persistence.ChannelContactStore.ChannelPairingRequest;
import dev.fararoni.core.core.security.ChannelAccessGuard;
import dev.fararoni.core.core.security.ChannelAccessGuard.ChannelApprovalResult;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ChannelAccessCommand implements ConsoleCommand {
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public String getTrigger() {
        return "/security";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/sec", "/acl" };
    }

    @Override
    public String getDescription() {
        return "Gestiona seguridad: allowlist, pairing y control de acceso";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ENTERPRISE;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            printHelp();
            return;
        }

        FararoniCore core = getCore(ctx);
        if (core == null) {
            System.out.println("[ERROR] Core no disponible.");
            return;
        }

        ChannelContactStore store = core.getChannelContactStore();
        ChannelAccessGuard guard = core.getChannelAccessGuard();

        if (store == null || guard == null) {
            System.out.println("[ERROR] Sistema de seguridad no inicializado.");
            System.out.println("Verifica que FASE 45 este habilitada en la configuracion.");
            return;
        }

        String[] parts = args.trim().split("\\s+", 3);
        String subcommand = parts[0].toLowerCase();

        switch (subcommand) {
            case "status" -> showStatus(store, guard);
            case "pairing" -> handlePairing(parts, guard);
            case "allowlist", "allow" -> handleAllowList(parts, store);
            case "groups", "group" -> handleGroups(parts, store);
            case "owner" -> handleOwner(parts, store);
            default -> {
                System.out.println("[ERROR] Subcomando desconocido: " + subcommand);
                printHelp();
            }
        }
    }

    private void showStatus(ChannelContactStore store, ChannelAccessGuard guard) {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("               ESTADO DE SEGURIDAD");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        String owner = store.getOwner().orElse("(no configurado)");
        System.out.println("  Owner:              " + owner);

        List<ChannelContact> allowlist = store.getAllowList();
        System.out.println("  Contactos Allow:    " + allowlist.size());

        List<ChannelContact> groups = store.getAllowedGroups();
        System.out.println("  Grupos Allow:       " + groups.size());

        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        System.out.println("  Pairing Pendientes: " + pending.size() + "/" + ChannelAccessGuard.MAX_PENDING_PAIRINGS);

        System.out.println();
        System.out.println("  Usa '/security <subcomando>' para gestionar cada seccion.");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void handlePairing(String[] parts, ChannelAccessGuard guard) {
        if (parts.length < 2) {
            System.out.println("Uso: /security pairing <list|approve|deny> [codigo]");
            return;
        }

        String action = parts[1].toLowerCase();

        switch (action) {
            case "list", "ls" -> listPairings(guard);
            case "approve", "accept", "a" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security pairing approve <codigo>");
                    return;
                }
                approvePairing(parts[2], guard);
            }
            case "deny", "reject", "d" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security pairing deny <codigo>");
                    return;
                }
                denyPairing(parts[2], guard);
            }
            default -> System.out.println("Accion desconocida: " + action);
        }
    }

    private void listPairings(ChannelAccessGuard guard) {
        List<ChannelPairingRequest> pending = guard.listPendingPairings();

        System.out.println();
        System.out.println("══════════════════ SOLICITUDES DE PAIRING ══════════════════");

        if (pending.isEmpty()) {
            System.out.println("  (no hay solicitudes pendientes)");
        } else {
            System.out.println();
            System.out.println("  CODIGO   SENDER                 PROTOCOLO  EXPIRA EN");
            System.out.println("  ------   --------------------   ---------  ----------");

            for (ChannelPairingRequest pr : pending) {
                Duration remaining = Duration.between(Instant.now(), pr.expiresAt());
                String expiresIn = remaining.toMinutes() + " min";

                System.out.printf("  %s   %-20s   %-9s  %s%n",
                    pr.code(),
                    truncate(pr.senderId(), 20),
                    pr.protocol(),
                    expiresIn
                );

                if (pr.senderName() != null && !pr.senderName().isBlank()) {
                    System.out.println("           Nombre: " + pr.senderName());
                }
            }
        }

        System.out.println();
        System.out.println("  Comandos:");
        System.out.println("    /security pairing approve <codigo>  - Autorizar contacto");
        System.out.println("    /security pairing deny <codigo>     - Rechazar solicitud");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void approvePairing(String code, ChannelAccessGuard guard) {
        ChannelApprovalResult result = guard.approvePairing(code, null);

        switch (result.status()) {
            case APPROVED -> {
                System.out.println();
                System.out.println("[OK] Contacto autorizado: " + result.request().senderId());
                System.out.println("     Nombre: " + result.request().senderName());
                System.out.println("     Protocolo: " + result.request().protocol());
                System.out.println();
            }
            case NOT_FOUND -> System.out.println("[ERROR] Codigo no encontrado: " + code);
            case EXPIRED -> System.out.println("[ERROR] Codigo expirado: " + code);
            default -> System.out.println("[ERROR] " + result.message());
        }
    }

    private void denyPairing(String code, ChannelAccessGuard guard) {
        ChannelApprovalResult result = guard.denyPairing(code);

        switch (result.status()) {
            case DENIED -> {
                System.out.println();
                System.out.println("[OK] Solicitud rechazada para: " + result.request().senderId());
                System.out.println();
            }
            case NOT_FOUND -> System.out.println("[ERROR] Codigo no encontrado: " + code);
            default -> System.out.println("[ERROR] " + result.message());
        }
    }

    private void handleAllowList(String[] parts, ChannelContactStore store) {
        if (parts.length < 2) {
            System.out.println("Uso: /security allowlist <list|add|remove> [id] [nota]");
            return;
        }

        String action = parts[1].toLowerCase();

        switch (action) {
            case "list", "ls" -> listAllowList(store);
            case "add" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security allowlist add <id> [nota]");
                    return;
                }
                String[] idAndNote = parts[2].split("\\s+", 2);
                String id = idAndNote[0];
                String note = idAndNote.length > 1 ? idAndNote[1] : "Agregado manualmente";
                addToAllowList(id, note, store);
            }
            case "remove", "rm", "delete" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security allowlist remove <id>");
                    return;
                }
                removeFromAllowList(parts[2].split("\\s+")[0], store);
            }
            default -> System.out.println("Accion desconocida: " + action);
        }
    }

    private void listAllowList(ChannelContactStore store) {
        List<ChannelContact> entries = store.getAllowList();

        System.out.println();
        System.out.println("═══════════════════ CONTACTOS AUTORIZADOS ═══════════════════");

        if (entries.isEmpty()) {
            System.out.println("  (lista vacia)");
        } else {
            System.out.println();
            System.out.println("  ID                       TIPO      NOTA                DESDE");
            System.out.println("  -----------------------  --------  ------------------  ----------");

            for (ChannelContact entry : entries) {
                System.out.printf("  %-23s  %-8s  %-18s  %s%n",
                    truncate(entry.id(), 23),
                    entry.type(),
                    truncate(entry.note(), 18),
                    DATE_FMT.format(entry.createdAt())
                );
            }
        }

        System.out.println();
        System.out.println("  Comandos:");
        System.out.println("    /security allowlist add <id> [nota]  - Agregar contacto");
        System.out.println("    /security allowlist remove <id>      - Remover contacto");
        System.out.println("═════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void addToAllowList(String id, String note, ChannelContactStore store) {
        store.addToAllowList(id, note);
        System.out.println("[OK] Contacto agregado: " + id);
    }

    private void removeFromAllowList(String id, ChannelContactStore store) {
        if (store.removeFromAllowList(id)) {
            System.out.println("[OK] Contacto removido: " + id);
        } else {
            System.out.println("[WARN] Contacto no encontrado: " + id);
        }
    }

    private void handleGroups(String[] parts, ChannelContactStore store) {
        if (parts.length < 2) {
            System.out.println("Uso: /security groups <list|add|remove> [id] [nota]");
            return;
        }

        String action = parts[1].toLowerCase();

        switch (action) {
            case "list", "ls" -> listGroups(store);
            case "add" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security groups add <group_id> [nota]");
                    return;
                }
                String[] idAndNote = parts[2].split("\\s+", 2);
                String id = idAndNote[0];
                String note = idAndNote.length > 1 ? idAndNote[1] : "Grupo agregado manualmente";
                addGroup(id, note, store);
            }
            case "remove", "rm", "delete" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security groups remove <group_id>");
                    return;
                }
                removeGroup(parts[2].split("\\s+")[0], store);
            }
            default -> System.out.println("Accion desconocida: " + action);
        }
    }

    private void listGroups(ChannelContactStore store) {
        List<ChannelContact> groups = store.getAllowedGroups();

        System.out.println();
        System.out.println("════════════════════ GRUPOS AUTORIZADOS ════════════════════");

        if (groups.isEmpty()) {
            System.out.println("  (no hay grupos autorizados)");
        } else {
            System.out.println();
            System.out.println("  GROUP ID                 NOTA                      DESDE");
            System.out.println("  -----------------------  ------------------------  ----------");

            for (ChannelContact entry : groups) {
                System.out.printf("  %-23s  %-24s  %s%n",
                    truncate(entry.id(), 23),
                    truncate(entry.note(), 24),
                    DATE_FMT.format(entry.createdAt())
                );
            }
        }

        System.out.println();
        System.out.println("  Comandos:");
        System.out.println("    /security groups add <group_id> [nota]  - Autorizar grupo");
        System.out.println("    /security groups remove <group_id>      - Revocar grupo");
        System.out.println("═════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private void addGroup(String groupId, String note, ChannelContactStore store) {
        store.addGroupToAllowList(groupId, note);
        System.out.println("[OK] Grupo agregado: " + groupId);
    }

    private void removeGroup(String groupId, ChannelContactStore store) {
        if (store.removeGroupFromAllowList(groupId)) {
            System.out.println("[OK] Grupo removido: " + groupId);
        } else {
            System.out.println("[WARN] Grupo no encontrado: " + groupId);
        }
    }

    private void handleOwner(String[] parts, ChannelContactStore store) {
        if (parts.length < 2) {
            System.out.println("Uso: /security owner <show|set> [id]");
            return;
        }

        String action = parts[1].toLowerCase();

        switch (action) {
            case "show", "get" -> showOwner(store);
            case "set" -> {
                if (parts.length < 3) {
                    System.out.println("Uso: /security owner set <id>");
                    return;
                }
                setOwner(parts[2].split("\\s+")[0], store);
            }
            default -> System.out.println("Accion desconocida: " + action);
        }
    }

    private void showOwner(ChannelContactStore store) {
        String owner = store.getOwner().orElse(null);

        System.out.println();
        if (owner != null) {
            System.out.println("  Owner actual: " + owner);
        } else {
            System.out.println("  [WARN] No hay owner configurado.");
            System.out.println("  Usa: /security owner set <telefono>");
        }
        System.out.println();
    }

    private void setOwner(String id, ChannelContactStore store) {
        store.setOwner(id);
        System.out.println();
        System.out.println("[OK] Owner establecido: " + id);
        System.out.println("     Este contacto tiene acceso total al sistema.");
        System.out.println();
    }

    private FararoniCore getCore(ExecutionContext ctx) {
        Object coreObj = ctx.getCore();
        if (coreObj instanceof FararoniCore core) {
            return core;
        }
        return null;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + ".." : s;
    }

    private void printHelp() {
        System.out.println("""

            ═══════════════════════════════════════════════════════════════
                          COMANDO /security
            ═══════════════════════════════════════════════════════════════

            Uso: /security <subcomando> [opciones]

            SUBCOMANDOS:

              status                       Estado general del sistema

              pairing list                 Solicitudes de emparejamiento pendientes
              pairing approve <codigo>     Autorizar contacto con codigo
              pairing deny <codigo>        Rechazar solicitud

              allowlist list               Contactos autorizados
              allowlist add <id> [nota]    Agregar contacto a lista blanca
              allowlist remove <id>        Remover contacto

              groups list                  Grupos autorizados
              groups add <id> [nota]       Autorizar grupo
              groups remove <id>           Revocar grupo

              owner show                   Mostrar owner actual
              owner set <id>               Establecer owner del sistema

            ALIASES: /sec, /acl

            EJEMPLOS:

              /security status
              /security pairing list
              /security pairing approve 847291
              /security allowlist add +521234567890 Cliente VIP
              /security owner set +521234567890

            ═══════════════════════════════════════════════════════════════
            """);
    }
}
