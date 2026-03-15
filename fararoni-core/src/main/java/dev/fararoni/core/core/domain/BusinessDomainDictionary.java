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
package dev.fararoni.core.core.domain;

import dev.fararoni.core.core.topology.ProjectTopology;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class BusinessDomainDictionary {

    private BusinessDomainDictionary() {}

    private static final Map<String, String> STATIC_MAP;

    static {
        Map<String, String> map = new HashMap<>();

        map.put("venta", "ventas");
        map.put("pedido", "ventas");
        map.put("orden", "ventas");
        map.put("factura", "ventas");
        map.put("sale", "sales");
        map.put("order", "sales");

        map.put("cliente", "clientes");
        map.put("usuario", "clientes");
        map.put("perfil", "clientes");
        map.put("customer", "customers");
        map.put("user", "customers");

        map.put("producto", "productos");
        map.put("articulo", "productos");
        map.put("stock", "productos");
        map.put("inventario", "productos");
        map.put("product", "products");
        map.put("item", "products");

        map.put("pago", "finanzas");
        map.put("cobro", "finanzas");
        map.put("tarjeta", "finanzas");
        map.put("banco", "finanzas");
        map.put("payment", "finance");
        map.put("invoice", "finance");

        map.put("auth", "seguridad");
        map.put("login", "seguridad");
        map.put("token", "seguridad");
        map.put("authentication", "security");
        map.put("permission", "security");

        map.put("envio", "logistica");
        map.put("entrega", "logistica");
        map.put("almacen", "logistica");
        map.put("shipping", "logistics");
        map.put("delivery", "logistics");

        map.put("empleado", "rrhh");
        map.put("nomina", "rrhh");
        map.put("employee", "hr");
        map.put("payroll", "hr");

        STATIC_MAP = Collections.unmodifiableMap(map);
    }

    private static final Map<String, String> DYNAMIC_MAP = new ConcurrentHashMap<>();

    public static void learnFromTopology(ProjectTopology topology) {
        if (topology == null) return;

        List<String> namespaces = topology.packages();
        if (namespaces == null) return;

        for (String pkg : namespaces) {
            if (pkg == null || pkg.isBlank()) continue;

            String[] parts = pkg.split("\\.");
            if (parts.length > 0) {
                String domainCandidate = parts[parts.length - 1].toLowerCase();

                if (isTechnicalPackage(domainCandidate)) continue;

                DYNAMIC_MAP.put(domainCandidate, domainCandidate);

                if (domainCandidate.endsWith("s") && domainCandidate.length() > 4) {
                    String singular = domainCandidate.substring(0, domainCandidate.length() - 1);
                    DYNAMIC_MAP.put(singular, domainCandidate);
                }
            }
        }

        if (!DYNAMIC_MAP.isEmpty()) {
            System.out.println("[ARCHITECT] Dominios aprendidos del proyecto: " + DYNAMIC_MAP.keySet());
        }
    }

    public static String lookup(String word) {
        if (word == null) return null;
        String key = word.toLowerCase();

        if (DYNAMIC_MAP.containsKey(key)) {
            return DYNAMIC_MAP.get(key);
        }

        return STATIC_MAP.get(key);
    }

    private static boolean isTechnicalPackage(String pkg) {
        return Set.of(
            "model", "service", "controller", "repository",
            "util", "utils", "config", "dto", "mapper",
            "impl", "core", "api", "common", "shared",
            "entity", "entities", "domain", "base"
        ).contains(pkg);
    }

    public static void clearDynamic() {
        DYNAMIC_MAP.clear();
    }
}
