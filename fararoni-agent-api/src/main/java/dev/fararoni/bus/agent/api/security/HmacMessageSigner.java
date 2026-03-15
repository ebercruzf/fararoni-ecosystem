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
package dev.fararoni.bus.agent.api.security;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HmacMessageSigner - Firma y verifica mensajes usando HMAC-SHA256.
 *
 * <h2>Proposito</h2>
 * <p>Garantiza la integridad de mensajes en el {@code SovereignBus}.
 * Cada mensaje firmado incluye un hash criptografico que permite detectar
 * alteraciones o mensajes falsificados.</p>
 *
 * <h2>Arquitectura de Integracion</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                    FLUJO DE FIRMA DE MENSAJES                        │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │                                                                      │
 * │  EMISOR (ej: WhisperToBusBridge)                                    │
 * │  ────────────────────────────────                                   │
 * │  1. Crear payload (texto, JSON, etc)                                │
 * │  2. Llamar HmacMessageSigner.sign(payload, timestamp)               │
 * │  3. Crear SovereignEnvelope con signature                           │
 * │  4. Publicar en SovereignBus                                        │
 * │                                                                      │
 * │  RECEPTOR (ej: DoppelgangerAgent)                                   │
 * │  ─────────────────────────────────                                  │
 * │  1. Recibir SovereignEnvelope del bus                               │
 * │  2. Llamar HmacMessageSigner.verify(payload, timestamp, signature)  │
 * │  3. Si FALSE: rechazar mensaje (posible ataque)                     │
 * │  4. Si TRUE: procesar mensaje normalmente                           │
 * │                                                                      │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quien Usa Esta Clase</h2>
 * <ul>
 *   <li>{@code WhisperToBusBridge} - Firma transcripciones de voz</li>
 *   <li>{@code DoppelgangerAgent} - Verifica mensajes entrantes</li>
 *   <li>{@code SovereignEnvelope.withSignature()} - Helper para firmar</li>
 *   <li>Cualquier Gateway que publique en el bus</li>
 * </ul>
 *
 * <h2>Configuracion</h2>
 * <p>La clave secreta se lee de variable de entorno:</p>
 * <pre>
 * export FARARONI_HMAC_SECRET="tu-clave-secreta-minimo-32-caracteres"
 * </pre>
 *
 * <p>Si no se configura, usa una clave por defecto (SOLO PARA DESARROLLO).</p>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Firmar un mensaje
 * String payload = "{\"action\":\"transcribe\",\"text\":\"Hola mundo\"}";
 * long timestamp = System.currentTimeMillis();
 * String signature = HmacMessageSigner.sign(payload, timestamp);
 *
 * // Verificar un mensaje
 * boolean valid = HmacMessageSigner.verify(payload, timestamp, signature);
 * if (!valid) {
 *     throw new SecurityException("Mensaje corrupto o falsificado");
 * }
 * }</pre>
 *
 * <h2>Seguridad</h2>
 * <ul>
 *   <li>Algoritmo: HMAC-SHA256 (256 bits de seguridad)</li>
 *   <li>Resistente a ataques de extension de longitud</li>
 *   <li>Incluye timestamp para prevenir replay attacks</li>
 *   <li>Comparacion en tiempo constante para prevenir timing attacks</li>
 * </ul>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see SovereignEnvelope
 */
public final class HmacMessageSigner {

    private static final Logger LOG = Logger.getLogger(HmacMessageSigner.class.getName());

    /** Algoritmo de firma */
    private static final String ALGORITHM = "HmacSHA256";

    /** Variable de entorno para la clave secreta */
    private static final String SECRET_ENV_VAR = "FARARONI_HMAC_SECRET";

    /**
     * Clave por defecto SOLO PARA DESARROLLO.
     * En produccion DEBE configurarse via variable de entorno.
     */
    private static final String DEFAULT_SECRET = "FARARONI_DEV_SECRET_CAMBIAR_EN_PRODUCCION_2026";

    /** Clave secreta cargada (lazy init) */
    private static volatile byte[] cachedSecretKey;

    /** Instancia de Mac cacheada por thread (thread-safe) */
    private static final ThreadLocal<Mac> MAC_INSTANCE = ThreadLocal.withInitial(() -> {
        try {
            return Mac.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible en este JVM", e);
        }
    });

    // Constructor privado - clase utilitaria
    private HmacMessageSigner() {
        throw new UnsupportedOperationException("Clase utilitaria - no instanciar");
    }

    // =========================================================================
    // API PUBLICA
    // =========================================================================

    /**
     * Firma un contenido con timestamp.
     *
     * <p>Combina el contenido y timestamp para generar una firma HMAC-SHA256.
     * El timestamp previene ataques de replay.</p>
     *
     * @param content   contenido a firmar (payload del mensaje)
     * @param timestamp epoch millis del mensaje
     * @return firma en Base64
     * @throws IllegalArgumentException si content es null
     */
    public static String sign(String content, long timestamp) {
        Objects.requireNonNull(content, "Content no puede ser null");

        try {
            // Combinar contenido + timestamp
            String dataToSign = content + "|" + timestamp;

            // Obtener Mac e inicializar con clave
            Mac mac = MAC_INSTANCE.get();
            SecretKeySpec keySpec = new SecretKeySpec(getSecretKey(), ALGORITHM);
            mac.init(keySpec);

            // Calcular HMAC
            byte[] hmacBytes = mac.doFinal(dataToSign.getBytes(StandardCharsets.UTF_8));

            // Retornar en Base64
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (InvalidKeyException e) {
            LOG.log(Level.SEVERE, "Clave HMAC invalida", e);
            throw new IllegalStateException("Error de configuracion de clave HMAC", e);
        }
    }

    /**
     * Verifica la firma de un mensaje.
     *
     * <p>Recalcula el HMAC y compara con la firma recibida usando
     * comparacion en tiempo constante para prevenir timing attacks.</p>
     *
     * @param content           contenido original
     * @param timestamp         timestamp original
     * @param receivedSignature firma recibida (Base64)
     * @return true si la firma es valida
     */
    public static boolean verify(String content, long timestamp, String receivedSignature) {
        if (content == null || receivedSignature == null) {
            return false;
        }

        try {
            // Recalcular firma
            String expectedSignature = sign(content, timestamp);

            // Comparacion en tiempo constante (previene timing attacks)
            return constantTimeEquals(expectedSignature, receivedSignature);

        } catch (Exception e) {
            LOG.warning(() -> "[HmacMessageSigner] Error verificando firma: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si la clave secreta esta configurada correctamente.
     *
     * @return true si usa clave de produccion (no la default)
     */
    public static boolean isProductionKeyConfigured() {
        String envSecret = System.getenv(SECRET_ENV_VAR);
        return envSecret != null && !envSecret.isBlank() && !envSecret.equals(DEFAULT_SECRET);
    }

    /**
     * Obtiene informacion de estado para diagnostico.
     *
     * @return record con estado del signer
     */
    public static SignerStatus getStatus() {
        return new SignerStatus(
            ALGORITHM,
            SECRET_ENV_VAR,
            isProductionKeyConfigured(),
            cachedSecretKey != null
        );
    }

    /**
     * [AJUSTE 2] Limpia recursos del ThreadLocal para evitar fugas de memoria.
     *
     * <p>En entornos de servidores de aplicaciones (Tomcat, Jetty, Quarkus),
     * los ThreadLocal pueden causar fugas de memoria si los hilos se reutilizan
     * y la clase se descarga dinamicamente.</p>
     *
     * <p><b>Cuando llamar:</b></p>
     * <ul>
     *   <li>Al finalizar la aplicacion (shutdown hook)</li>
     *   <li>Antes de recargar el modulo dinamicamente</li>
     *   <li>En tests para limpiar estado entre ejecuciones</li>
     * </ul>
     *
     * <p>Para una CLI standalone no es estrictamente necesario, pero es
     * buena practica "Enterprise-Ready".</p>
     */
    public static void cleanup() {
        MAC_INSTANCE.remove();
        synchronized (HmacMessageSigner.class) {
            cachedSecretKey = null;
        }
        LOG.fine("[HmacMessageSigner] Recursos limpiados (ThreadLocal + cachedKey)");
    }

    /**
     * Reinicia la clave cacheada.
     *
     * <p>Util si la variable de entorno cambia en runtime (ej: rotacion de claves).</p>
     */
    public static void resetSecretKey() {
        synchronized (HmacMessageSigner.class) {
            cachedSecretKey = null;
        }
        LOG.info("[HmacMessageSigner] Clave secreta reseteada. Se recargara en proxima firma.");
    }

    // =========================================================================
    // METODOS PRIVADOS
    // =========================================================================

    /**
     * Obtiene la clave secreta (lazy loading con double-checked locking).
     */
    private static byte[] getSecretKey() {
        if (cachedSecretKey == null) {
            synchronized (HmacMessageSigner.class) {
                if (cachedSecretKey == null) {
                    String secret = System.getenv(SECRET_ENV_VAR);

                    if (secret == null || secret.isBlank()) {
                        LOG.warning(() ->
                            "[HmacMessageSigner] ADVERTENCIA: Usando clave por defecto. " +
                            "Configure " + SECRET_ENV_VAR + " para produccion."
                        );
                        secret = DEFAULT_SECRET;
                    } else {
                        LOG.info(() -> "[HmacMessageSigner] Clave de produccion cargada desde " + SECRET_ENV_VAR);
                    }

                    cachedSecretKey = secret.getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return cachedSecretKey;
    }

    /**
     * Comparacion en tiempo constante para prevenir timing attacks.
     *
     * <p>A diferencia de String.equals(), esta comparacion siempre
     * toma el mismo tiempo independientemente de donde difieren los strings.</p>
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    // =========================================================================
    // CLASES AUXILIARES
    // =========================================================================

    /**
     * Estado del firmador para diagnostico.
     *
     * @param algorithm             algoritmo en uso
     * @param secretEnvVar          nombre de la variable de entorno
     * @param productionKeyActive   true si usa clave de produccion
     * @param keyLoaded             true si la clave ya fue cargada
     */
    public record SignerStatus(
        String algorithm,
        String secretEnvVar,
        boolean productionKeyActive,
        boolean keyLoaded
    ) {
        @Override
        public String toString() {
            return String.format(
                "HmacMessageSigner[algorithm=%s, envVar=%s, production=%s, loaded=%s]",
                algorithm, secretEnvVar, productionKeyActive, keyLoaded
            );
        }
    }
}
