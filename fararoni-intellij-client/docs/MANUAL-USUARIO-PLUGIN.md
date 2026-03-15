# Fararoni Sentinel AI - Manual de Usuario

**Version:** 2.0.0
**Fecha:** Febrero 2026
**Autor:** Eber Cruz

---

## Tabla de Contenidos

1. [Introduccion](#1-introduccion)
2. [Requisitos](#2-requisitos)
3. [Instalacion](#3-instalacion)
4. [Inicio Rapido](#4-inicio-rapido)
5. [Funcionalidades](#5-funcionalidades)
   - [5.1 Chat Interactivo](#51-chat-interactivo)
   - [5.2 Preguntar sobre Codigo](#52-preguntar-sobre-codigo)
   - [5.3 Sugerencias Proactivas](#53-sugerencias-proactivas)
   - [5.4 Aplicar Fixes con Alt+Enter](#54-aplicar-fixes-con-altenter)
   - [5.5 Deteccion de Errores](#55-deteccion-de-errores)
   - [5.6 Menu Contextual](#56-menu-contextual)
6. [Atajos de Teclado](#6-atajos-de-teclado)
7. [Arquitectura](#7-arquitectura)
8. [Solucion de Problemas](#8-solucion-de-problemas)

---

## 1. Introduccion

**Fararoni Sentinel AI** es un plugin de IntelliJ IDEA que conecta tu IDE con el motor de inteligencia artificial Fararoni Core. A diferencia de otros asistentes de codigo, Fararoni se ejecuta **100% localmente** en tu maquina, manteniendo tu codigo privado y seguro.

### Caracteristicas Principales

- **Chat Interactivo** - Panel lateral para conversaciones con la IA
- **Analisis de Codigo** - Selecciona codigo y pregunta sobre el
- **Sugerencias Proactivas** - Warnings amarillos con mejoras sugeridas
- **Fixes Quirurgicos** - Aplica correcciones con un clic (Alt+Enter)
- **Deteccion de Errores** - Analiza errores de compilacion automaticamente
- **100% Local** - Tu codigo nunca sale de tu maquina

---

## 2. Requisitos

### Software Requerido

| Componente | Version Minima |
|------------|----------------|
| IntelliJ IDEA | 2023.3+ |
| Java (para el servidor) | 21+ |
| Fararoni Core Server | 0.11.30+ |

### Puertos Utilizados

| Puerto | Servicio |
|--------|----------|
| 7070 | Fararoni Core REST API |
| 7071 | Fararoni Gateway (Plugin) |
| 9999 | Plugin CallbackServer |

---

## 3. Instalacion

### Paso 1: Instalar el Plugin

1. Abre IntelliJ IDEA
2. Ve a `Settings` → `Plugins` → `Marketplace`
3. Busca "Fararoni Sentinel AI"
4. Click en `Install`
5. Reinicia IntelliJ

### Paso 2: Iniciar el Servidor Fararoni

Antes de usar el plugin, debes iniciar el servidor:

```bash
cd /ruta/a/fararoni
GATEWAY_JAR="fararoni-gateway-rest/target/fararoni-gateway-rest-0.1.0.jar"
CORE_JAR="fararoni-core/target/fararoni-core-0.11.30.jar"
java --enable-preview -cp "$GATEWAY_JAR:$CORE_JAR" dev.fararoni.core.FararoniMain --server
```

Deberias ver:
```
╔═══════════════════════════════════════════════════════════════╗
║  [ACTIVE] SERVIDOR ACTIVO                                     ║
╠═══════════════════════════════════════════════════════════════╣
║  URL Base:    http://localhost:7070                          ║
╚═══════════════════════════════════════════════════════════════╝
```

---

## 4. Inicio Rapido

1. **Inicia el servidor** (ver seccion 3)
2. **Abre el panel de Fararoni**: `Ctrl+Shift+G` (o `Cmd+Shift+G` en Mac)
3. **Escribe tu pregunta** en el campo de texto
4. **Presiona Enter** o click en "Enviar"
5. **Espera la respuesta** con efecto de escritura

---

## 5. Funcionalidades

### 5.1 Chat Interactivo

El panel lateral permite conversar con Fararoni de forma interactiva.

**Como acceder:**
- Click en el icono de Fararoni en la barra lateral derecha
- O presiona `Ctrl+Shift+G` / `Cmd+Shift+G`

**Ejemplos de uso:**
```
Tu: Explica que hace este metodo
Tu: Como puedo optimizar este bucle?
Tu: Genera tests unitarios para esta clase
Tu: Refactoriza este codigo aplicando SOLID
```

**Caracteristicas:**
- Efecto de escritura letra por letra
- Historial de conversacion
- Contexto del proyecto actual

---

### 5.2 Preguntar sobre Codigo

Selecciona un fragmento de codigo y pregunta directamente sobre el.

**Como usar:**

1. Selecciona codigo en el editor
2. Presiona `Ctrl+Alt+F` (o `Cmd+Alt+F` en Mac)
3. O click derecho → "Preguntar a Fararoni"

**Ejemplo:**
```java
// Selecciona este codigo:
public void processData(List<String> items) {
    for (int i = 0; i < items.size(); i++) {
        String item = items.get(i);
        System.out.println(item.toUpperCase());
    }
}
// Presiona Ctrl+Alt+F
// Fararoni analizara y sugerira mejoras
```

---

### 5.3 Sugerencias Proactivas

Fararoni analiza tu codigo automaticamente y muestra **warnings amarillos** cuando detecta oportunidades de mejora.

**Como funciona:**

1. Abre cualquier archivo `.java` o `.kt`
2. Escribe o modifica codigo
3. Espera 2-3 segundos
4. Apareceran subrayados amarillos en codigo mejorable

**Tipos de sugerencias:**
- Null safety (NPE potencial)
- Code smells
- Violaciones SOLID
- Performance issues
- Clean Code improvements

**Ver la sugerencia:**
- Coloca el cursor sobre el codigo subrayado
- Aparecera un tooltip con la sugerencia

---

### 5.4 Aplicar Fixes con Alt+Enter

Cuando Fararoni tiene una sugerencia de fix, puedes aplicarla con un solo atajo.

**Como usar:**

1. Coloca el cursor en codigo con warning amarillo de Fararoni
2. Presiona `Alt+Enter`
3. Selecciona "Apply Fararoni Fix"
4. El codigo se modificara automaticamente

**Caracteristicas:**
- Cambios atomicos (todo o nada)
- Soporte para Undo (`Ctrl+Z`)
- Guardado automatico

**Ejemplo:**
```java
// ANTES (con warning):
String name = null;
System.out.println(name.length());

// DESPUES (fix aplicado):
String name = null;
if (name != null) {
    System.out.println(name.length());
}
```

---

### 5.5 Deteccion de Errores

El **FararoniErrorSensor** detecta errores de compilacion automaticamente y solicita sugerencias al LLM.

**Como funciona:**

1. Escribe codigo con error de compilacion
2. El sensor detecta el error
3. Automaticamente pide sugerencia a Fararoni
4. Aparece notificacion con la solucion

**Errores detectados:**
- Errores de sintaxis
- Tipos incompatibles
- Metodos no encontrados
- Variables no declaradas

---

### 5.6 Menu Contextual

Accede a las funciones de Fararoni desde el menu contextual del editor.

**Como usar:**

1. Click derecho en el editor
2. Busca "Preguntar a Fararoni" al final del menu
3. Click para enviar el codigo seleccionado

**Menu Tools:**

Tambien puedes acceder desde el menu principal:
- `Tools` → `Fararoni AI` → `Preguntar a Fararoni`
- `Tools` → `Fararoni AI` → `Abrir Panel Fararoni`

---

## 6. Atajos de Teclado

| Atajo | Mac | Accion |
|-------|-----|--------|
| `Ctrl+Alt+F` | `Cmd+Alt+F` | Preguntar sobre codigo seleccionado |
| `Ctrl+Shift+G` | `Cmd+Shift+G` | Abrir/Cerrar panel de Fararoni |
| `Alt+Enter` | `Alt+Enter` | Aplicar fix de Fararoni |

### Personalizar Atajos

1. Ve a `Settings` → `Keymap`
2. Busca "Fararoni"
3. Click derecho en la accion → "Add Keyboard Shortcut"
4. Configura el atajo deseado

---

## 7. Arquitectura

El plugin usa una arquitectura **Sidecar Gateway** para comunicacion asincrona:

```
┌─────────────────────────────────────────────────────────────────┐
│                     INTELLIJ IDEA                               │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │ FararoniToolWindow│    │ FararoniBridge   │                  │
│  │ (Panel de Chat)  │    │ (HTTP Client)    │                  │
│  └────────┬─────────┘    └────────┬─────────┘                  │
│           │                       │                             │
│           │    ┌──────────────────┘                             │
│           │    │                                                │
│           ▼    ▼                                                │
│  ┌──────────────────┐                                           │
│  │ CallbackServer   │◄──────────────────────┐                   │
│  │ (Puerto 9999)    │                       │                   │
│  └──────────────────┘                       │                   │
└─────────────────────────────────────────────│───────────────────┘
                                              │
              HTTP POST                       │ HTTP POST (callback)
              ────────►                       │
                                              │
┌─────────────────────────────────────────────│───────────────────┐
│                  FARARONI SERVER            │                   │
│  ┌──────────────────┐    ┌──────────────────┴─────┐            │
│  │ Gateway :7071    │───►│ IntelliJEgressAdapter │            │
│  └────────┬─────────┘    └────────────────────────┘            │
│           │                                                     │
│           ▼                                                     │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │ OmniChannelRouter│───►│ LLM (Ollama)     │                  │
│  └──────────────────┘    └──────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
```

### Flujo de Comunicacion

1. Usuario escribe en panel de chat
2. FararoniBridge envia POST a Gateway:7071
3. Gateway publica mensaje al bus
4. OmniChannelRouter procesa y consulta LLM
5. IntelliJEgressAdapter envia respuesta a CallbackServer:9999
6. CallbackServer actualiza UI con efecto typing

---

## 8. Solucion de Problemas

### El plugin no se conecta

**Sintoma:** Mensaje "Pensando..." se queda indefinidamente

**Solucion:**
1. Verifica que el servidor este corriendo:
   ```bash
   curl http://localhost:7071/gateway/v1/health
   ```
2. Debe responder: `{"status":"healthy",...}`

### No aparecen sugerencias proactivas

**Sintoma:** No hay warnings amarillos en el codigo

**Posibles causas:**
- El archivo no es `.java` o `.kt`
- El servidor no esta corriendo
- El FararoniExternalAnnotator no esta registrado

**Solucion:**
1. Verifica en `Settings` → `Editor` → `Inspections`
2. Busca "Fararoni" - debe estar habilitado

### Alt+Enter no muestra opcion de Fararoni

**Sintoma:** No aparece "Apply Fararoni Fix" en el menu

**Solucion:**
- Solo aparece cuando hay un warning de Fararoni
- Asegurate de que el codigo tenga subrayado amarillo de Fararoni

### Error de conexion al Gateway

**Sintoma:** Error en logs del plugin

**Solucion:**
1. Verifica que el Gateway este corriendo en puerto 7071
2. Verifica que el canal `intellij` este configurado:
   ```bash
   curl http://localhost:7071/gateway/v1/admin/channels/status
   ```
3. Debe incluir `agency.input.intellij` en la lista de topics

### Reiniciar el servidor

Si algo no funciona, intenta reiniciar:

```bash
# Detener
pkill -f "fararoni-core"

# Iniciar
java --enable-preview -cp "$GATEWAY_JAR:$CORE_JAR" \
  dev.fararoni.core.FararoniMain --server
```

---

## Soporte

- **Web:** https://fararoni.dev
- **Documentacion:** Ver README.md del proyecto

---

*Fararoni Sentinel AI - Inteligencia Tactica para Desarrolladores*
