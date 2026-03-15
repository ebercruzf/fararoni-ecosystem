/**
 * Diagnostics - Herramientas de Monitoreo de Grado Militar
 *
 * Este paquete contiene componentes para monitorear y diagnosticar
 * el estado del sistema Fararoni en tiempo real.
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li>{@link SatiHealthMonitor} - Dashboard de emergencia para el enjambre SATI</li>
 * </ul>
 *
 * <h2>Arquitectura de Soberania</h2>
 * <pre>
 *   +------------------+
 *   |  Kernel (Java)   |  <-- Cerebro: orquesta todo
 *   +--------+---------+
 *            |
 *   +--------v---------+
 *   | Sidecar (Java)   |  <-- Frontera: habla NATS + TCP/HTTP
 *   +--------+---------+
 *            |
 *   +--------v---------+
 *   | Recurso Externo  |  <-- Caja Negra: MCP, DB, API, etc.
 *   | (Cualquier lang) |
 *   +------------------+
 * </pre>
 *
 * <h2>Nota Importante</h2>
 * El Kernel NUNCA toca el recurso externo directamente.
 * Solo toca al Sidecar de Java, quien es responsable de:
 * <ul>
 *   <li>Detectar latencia del recurso</li>
 *   <li>Reportar degradacion al Kernel</li>
 *   <li>Aislar fallos del recurso externo</li>
 * </ul>
 *
 * @since 1.0.0
 * @see SatiHealthMonitor
 */
package dev.fararoni.core.core.diagnostics;
