# Fararoni — Guia de Instalacion Rapida

**Version: 1.0.0 | Marzo 2026**

> Esta guia te lleva paso a paso desde la descarga hasta tu primera conversacion
> con Fararoni. Tiempo estimado: 5-10 minutos.

---

## Requisitos Previos

Fararoni se distribuye como **binarios nativos** compilados con GraalVM Native Image.
**No necesitas tener Java instalado** para usar Fararoni — el core y los sidecars son
ejecutables independientes que no requieren JVM ni Node.js.

Solo necesitas una cosa:

### Ollama (servidor de modelos LLM)

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# Descargar desde: https://ollama.com/download/windows
```

Inicia Ollama y descarga un modelo:

```bash
# Iniciar Ollama (dejalo corriendo)
ollama serve

# En otra terminal, descargar un modelo
ollama pull qwen2.5-coder:7b
```

---

## Paso 1 — Descargar Fararoni

Descarga el instalador para tu plataforma desde
[GitHub Releases](https://github.com/ebercruzf/fararoni-ecosystem/releases):

| Plataforma | Archivo |
|------------|---------|
| **macOS** (Apple Silicon) | `Fararoni-Installer-macos-arm64.dmg` |
| **Linux** (x64) | `fararoni-v1.0.0-linux-x64.tar.gz` |
| **Windows** (x64) | `fararoni-v1.0.0-windows-x64.zip` |

O con Homebrew:

```bash
brew tap ebercruzf/fararoni
brew install fararoni
```

---

## Paso 2 — Instalar (macOS DMG)

1. Abre el archivo `Fararoni-Installer-macos-arm64.dmg`
2. Doble-clic en **"Instalar Fararoni.command"**

Si macOS bloquea la ejecucion: clic derecho > Abrir > Abrir.

Se abre una terminal y el instalador detecta que esta corriendo desde el DMG:

```
  Detectado: Ejecutando desde imagen de disco (DMG).
  El instalador necesita un disco con escritura.

  Copiando Fararoni Suite a: /Users/tu-usuario/Fararoni ...
  Copiado. Iniciando instalador...
```

Luego aparece el banner y comienza el wizard interactivo:

```
  ==================================================================

   ███████╗ █████╗ ██████╗  █████╗ ██████╗  ██████╗ ███╗   ██╗██╗
   ██╔════╝██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗████╗  ██║██║
   █████╗  ███████║██████╔╝███████║██████╔╝██║   ██║██╔██╗ ██║██║
   ██╔══╝  ██╔══██║██╔══██╗██╔══██║██╔══██╗██║   ██║██║╚██╗██║██║
   ██║     ██║  ██║██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║
   ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝

          Suite Installer v1.0.0 — Zero Dependencies

  ==================================================================
```

### 2.1 — [1/5] Verificando requisitos

El instalador verifica tu sistema automaticamente:

```
[1/5] Verificando requisitos...
[OK] Sistema: macOS (arm64)
[OK] Core nativo: fararoni-core
[OK] Sidecars nativos detectados en bin/
```

No necesitas hacer nada, solo espera.

### 2.2 — [2/5] Registrar Core como servicio

```
[2/5] Registrando Core como servicio del sistema...
  El Core se ejecutara como servicio de fondo (auto-start, auto-restart).
  Registrar como servicio? (s/n):
```

| Opcion | Que hace |
|--------|----------|
| **`s`** | Crea un LaunchAgent que inicia Fararoni automaticamente al encender tu Mac |
| **`n`** | No registra servicio, tendras que iniciar Fararoni manualmente cada vez |

**Escribe `s` y presiona Enter** (recomendado).

El instalador crea el archivo `~/Library/LaunchAgents/com.fararoni.core.plist`.

### 2.3 — [3/5] Instalar comando global

```
[3/5] Instalando comando global 'fararoni'...
[OK] Symlink: /Users/tu-usuario/bin/fararoni -> /Users/tu-usuario/Fararoni/bin/fararoni-core
[WARN] /Users/tu-usuario/bin no esta en tu PATH.
  Agrega a ~/.zshrc:  export PATH="/Users/tu-usuario/bin:$PATH"
```

Si aparece el warning de PATH, despues de la instalacion ejecuta:

```bash
echo 'export PATH="$HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### 2.4 — [4/5] Configurar Sidecars

El instalador pregunta por cada canal de mensajeria uno por uno.
Si no vas a usar un canal, responde `n` para saltarlo.

```
[4/5] Configurando Sidecars (binarios nativos)...

  Canales disponibles:
```

#### Telegram

```
  [1] Activar Telegram (Puerto 3001)? (s/n): s

  Credencial requerida: Token de BotFather para Telegram
  Documentacion: https://core.telegram.org/bots#how-do-i-create-a-bot
    1) Configurar ahora
    2) Configurar despues (no arranca)
    3) Arrancar sin credencial
  Opcion [1-3]: 3
[WARN] Telegram arrancara sin credencial.
[OK] Telegram: LaunchAgent creado
```

#### WhatsApp

```
  [2] Activar WhatsApp (Puerto 3000)? (s/n): s
[OK] WhatsApp: LaunchAgent creado
```

WhatsApp no pide credencial — usa codigo QR al iniciar el sidecar.

#### Discord

```
  [3] Activar Discord (Puerto 3002)? (s/n): s

  Credencial requerida: Token del Bot en Developer Portal
  Documentacion: https://discord.com/developers/applications
    1) Configurar ahora
    2) Configurar despues (no arranca)
    3) Arrancar sin credencial
  Opcion [1-3]: 3
[WARN] Discord arrancara sin credencial.
[OK] Discord: LaunchAgent creado
```

#### iMessage (solo macOS)

```
  [4] Activar iMessage (Puerto 3003)? (s/n): s

  Credencial requerida: Password del servidor BlueBubbles
  Documentacion: Requiere BlueBubbles Server en macOS
    1) Configurar ahora
    2) Configurar despues (no arranca)
    3) Arrancar sin credencial
  Opcion [1-3]: 3
[WARN] iMessage arrancara sin credencial.
[OK] iMessage: LaunchAgent creado
```

#### Opciones de credencial

Para cada canal que requiere credencial, el instalador ofrece 3 opciones:

| Opcion | Cuando usarla |
|--------|---------------|
| **`1`** | Ya tienes el token/password listo y quieres configurar ahora |
| **`2`** | Quieres activar el canal pero configuraras la credencial despues |
| **`3`** | Quieres probar sin credencial (el sidecar arranca pero no conecta) |

**Si no tienes las credenciales aun, escribe `2` o `3`** — puedes configurarlas despues
editando los archivos `.env` en `~/Fararoni/config/` o usando el comando `/config set` desde el CLI.

### 2.5 — [5/5] Resumen de instalacion

Al finalizar se muestra el resumen completo:

```
[5/5] Instalacion completada


  ==================================================================
              FARARONI SUITE v1.0.0 — OPERATIVO
  ==================================================================

  CORE (GraalVM Native):
    Binario:     /Users/tu-usuario/Fararoni/bin/fararoni-core
    Comando:     fararoni --server
    Servicio:    launchctl load ~/Library/LaunchAgents/com.fararoni.core.plist
    Logs:        tail -f /Users/tu-usuario/Fararoni/logs/core.log

  SIDECARS (Nativos SEA):
    telegram:  /Users/tu-usuario/Fararoni/logs/sidecar-telegram-out.log
    whatsapp:  /Users/tu-usuario/Fararoni/logs/sidecar-whatsapp-out.log
    discord:   /Users/tu-usuario/Fararoni/logs/sidecar-discord-out.log
    imessage:  /Users/tu-usuario/Fararoni/logs/sidecar-imessage-out.log

    Iniciar:     launchctl load ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist
    Detener:     launchctl unload ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist

  COMANDOS RAPIDOS:
    fararoni --version        Verificar
    fararoni                  CLI interactivo
    fararoni --server         Servidor (REST + Gateway)
    ./fararoni-launcher.sh    Iniciar todo (core + sidecars)

  ==================================================================
    Fararoni v1.0.0 instalado. Sistema operativo.
  ==================================================================
```

### Comandos Rapidos (referencia)

| Comando | Que hace |
|---------|----------|
| `fararoni --version` | Verificar que esta instalado |
| `fararoni` | Abrir CLI interactivo (chat con el LLM) |
| `fararoni --server` | Levantar servidor REST + Gateway (para sidecars y plugins) |
| `./fararoni-launcher.sh` | Iniciar todo junto (core + todos los sidecars) |
| `launchctl load ~/Library/LaunchAgents/com.fararoni.core.plist` | Iniciar core como servicio de fondo |
| `launchctl unload ~/Library/LaunchAgents/com.fararoni.core.plist` | Detener servicio core |
| `launchctl load ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist` | Iniciar sidecars como servicios |
| `launchctl unload ~/Library/LaunchAgents/com.fararoni.sidecar-*.plist` | Detener sidecars |

Al final veras:

```
Saving session...
...copying shared history...
...saving history...truncating history files...
...completed.

[Process completed]
```

**Esto es normal.** La terminal queda bloqueada (no responde a Enter ni Ctrl+C).
Simplemente **cierra esa ventana** con Cmd+W y abre una terminal nueva.

---

## Paso 3 — Primera Ejecucion

Abre una **terminal nueva** y escribe:

```bash
fararoni
```

> Si el comando no se encuentra, agrega el PATH primero:
> `echo 'export PATH="$HOME/bin:$PATH"' >> ~/.zshrc && source ~/.zshrc`

### 3.1 — Wizard de Primera Ejecucion

Fararoni detecta que es tu primera vez y lanza un wizard interactivo:

```
fararoni
[KERNEL] Iniciando Fararoni en: /Users/tu-usuario
[WorkspaceManager] Initialized: /Users/tu-usuario/.llm-fararoni (User Home Default)

========================================
     FARARONI - Primera Ejecucion
========================================

Bienvenido a FARARONI!

Parece que es tu primera vez usando esta herramienta.
Vamos a configurar los ajustes basicos.

Puedes omitir este proceso presionando Ctrl+C
y configurar manualmente despues.
```

### 3.2 — Elegir modo de operacion

```
¿Como quieres usar FARARONI?


  ✓ Ollama detectado en localhost:11434

  [1] Solo servidor externo (Ollama)
      → Usa solo el modelo grande de Ollama

  [2] Hibrido: Ollama + Motor local (Recomendado)
      → Modelo grande (Ollama) + modelo pequeño local como fallback
      → El motor local se usa para routing y cuando Ollama no esta disponible

  [3] Solo modo local (100% offline)
      → Todo local, funciona sin internet (~1.2 GB)

  [0] Omitir configuracion


Tu eleccion [1/2/3/0]:
```

**Escribe `2` y presiona Enter** (probado y recomendado).

| Opcion | Cuando usarla |
|--------|---------------|
| **`1`** | Ya tienes Ollama corriendo y no quieres motor local adicional |
| **`2`** | Recomendado — Ollama + modelo local pequeno como fallback (probado) |
| **`3`** | Quieres funcionar 100% offline sin Ollama |
| **`0`** | Omitir y configurar manualmente despues |

### 3.3 — Configurar servidor

```
PASO 1/4: URL del Servidor


  ✓ Ollama detectado - usando http://localhost:11434

Server URL [http://localhost:11434]:
  → Servidor: http://localhost:11434
```

**Presiona Enter** para aceptar el valor por defecto (recomendado).
Solo escribe otra URL si tienes Ollama en otra maquina o puerto.

### 3.4 — API Key

```
PASO 2/4: API Key


  Ollama no requiere API key. Presiona Enter para omitir.

API Key (opcional):
  → Sin API Key (no requerida)
```

**Presiona Enter** para omitir. Ollama no usa API key.

### 3.5 — Seleccionar modelo

```
PASO 3/4: Modelo por Defecto


  Modelos populares: qwen2.5-coder:1.5b-instruct, qwen2.5-coder:7b, llama3.2

Modelo [qwen2.5-coder:1.5b-instruct]:
```

**Presiona Enter** para aceptar `qwen2.5-coder:1.5b-instruct` (valor por defecto, recomendado).
Solo escribe otro nombre si quieres usar un modelo diferente que ya tengas descargado en Ollama.

Al confirmar muestra:

```
  → Modelo: qwen2.5-coder:1.5b-instruct
```

### 3.6 — Motor Local (Fallback)

Como elegiste la opcion 2 (Hibrido), el wizard configura el motor local:

```
PASO 4/4: Motor Local (Fallback)

El motor local se usara para:
  • Routing inteligente de consultas
  • Fallback cuando el servidor no este disponible
  • Tareas simples sin necesidad de servidor

Se descargara cuando inicies FARARONI (~5 MB motor + ~1.2 GB modelo)


  → Modo hibrido activado
```

No necesitas hacer nada — se configura automaticamente.
La descarga del motor y modelo local ocurre la primera vez que inicies Fararoni.

> **Nota tecnica**: El motor local utiliza `ollama.cpp`, una libreria nativa de inferencia.
> Fararoni detecta automaticamente la arquitectura de tu sistema (arm64, x64) y descarga
> la version compilada especifica para tu plataforma. No necesitas configurar nada manualmente.

### 3.7 — Configuracion de seguridad (3 Llaves)

```
========================================
     SEGURIDAD — Estrategia 3 Llaves
========================================

Fararoni protege tu sistema con 3 niveles:
  Llave 1: TOTP (Google Authenticator)
  Llave 2: Sandbox (carpeta de confianza)
  Llave 3: Master Password (elevacion sudo)

Canales externos (WhatsApp, Telegram) pediran
codigo 2FA. El CLI local NO lo requiere.


¿Configurar seguridad ahora? [S/n]:
```

**Escribe `s` y presiona Enter** (recomendado). La seguridad protege el acceso
desde canales externos y tambien te permite elevar privilegios con sudo.

### 3.8 — Vinculacion 2FA (Llave 1: TOTP)

> **Requisito previo**: Necesitas tener instalada la app **Google Authenticator**
> (o **Authy**) en tu celular. Es una app gratuita que genera codigos de 6 digitos
> cada 30 segundos. Descargala desde App Store (iOS) o Google Play (Android).

Al escribir `s`, aparece un codigo QR directamente en la terminal:

```
=================================================================
  CONFIGURACION DE SEGURIDAD — 2FA (Google Authenticator)
=================================================================

  1. Abre Google Authenticator (o Authy) en tu celular
  2. Escanea este codigo directamente desde tu terminal:

       ██████████████        ██            ██  ████████    ██████          ██████████████
       ██          ██  ████████  ████████████  ██  ████  ██████            ██          ██
       ██  ██████  ██    ████████  ██████  ████  ████████      ██  ██████  ██  ██████  ██
       ██  ██████  ██  ██            ██  ██    ██        ██████████    ██  ██  ██████  ██
       ██  ██████  ██        ████      ██  ██    ██  ██    ████    ██  ██  ██  ██████  ██
       ██          ██  ██  ██      ████  ████  ██  ████████████  ██  ████  ██          ██
       ██████████████  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██  ██████████████
       ...
       ██████████████  ██      ████  ████      ██████      ████  ██        ██████

     CLAVE MANUAL: XXXX4K2ML7EQRLZBLJW6DLDN7H3WXXXX

  3. Fararoni te pedira el codigo de 6 digitos al conectar
     por WhatsApp, Telegram o cualquier canal externo.

=================================================================

  Codigo de 6 digitos de tu app:
```

**Pasos:**

1. Abre **Google Authenticator** en tu celular
2. Toca **+** > **Escanear codigo QR**
3. Apunta la camara al QR que aparece en tu terminal
4. Si no puedes escanear, usa la **clave manual** que aparece debajo del QR
   (ingresala manualmente en la app como "clave de configuracion")
5. La app mostrara un codigo de 6 digitos que cambia cada 30 segundos
6. **Escribe ese codigo** en la terminal y presiona Enter

> Este codigo de 6 digitos es tu **Llave 1 (TOTP)**. Fararoni lo pedira cada vez
> que te conectes desde un canal externo (WhatsApp, Telegram, Discord, iMessage).
> Desde el CLI local no se requiere.

Al verificar el codigo aparece:

```
  Codigo de 6 digitos de tu app: ******
  → Codigo verificado correctamente
```

### 3.9 — Master Password (Llave 3)

```
PASO 6: Master Password (elevacion de privilegios)

Este password permite acceso temporal fuera del Sandbox
usando 'sudo <password>' (sesion de 15 min).


  Master Password (min 8 chars):
  Confirmar password:
  → Master Password guardado (BCrypt)
```

Escribe un password de minimo 8 caracteres y confirmalo.

> Esta es tu **Llave 3 (Master Password)**. Te permite elevar privilegios temporalmente
> (15 minutos) para que Fararoni pueda operar fuera de la carpeta de confianza (Sandbox).
> Desde el CLI se usa con el comando `sudo <tu-password>`.

### 3.10 — Carpeta de Confianza (Llave 2: Sandbox)

```
PASO 7: Carpeta de Confianza (Sandbox)

El agente solo puede leer/escribir dentro de esta carpeta.
Todo lo externo requiere 'sudo' para acceder.


  Ruta de confianza [/Users/tu-usuario/Documents/Proyectos]:
```

**Presiona Enter** para aceptar la ruta por defecto, o escribe otra ruta si tus
proyectos estan en otra ubicacion.

> Esta es tu **Llave 2 (Sandbox)**. Define la carpeta donde Fararoni puede operar
> libremente: leer archivos, escribir codigo, ejecutar comandos, etc. Cualquier
> operacion fuera de esta carpeta requiere elevar privilegios con `sudo <password>`.
> Esto protege el resto de tu sistema (documentos personales, configuracion del OS, etc.)
> de modificaciones accidentales por parte del agente.

### 3.11 — Resumen de seguridad

Al confirmar la carpeta de confianza, Fararoni muestra el resumen:

```
  → Sandbox: /Users/tu-usuario/Documents/Proyectos

----------------------------------------
  Seguridad configurada correctamente
----------------------------------------
  Llave 1 (TOTP):    Activa
  Llave 2 (Sandbox): Activa
  Llave 3 (BCrypt):  Configurada
----------------------------------------
```

Seguido del resumen de configuracion completa:

```
========================================
     Configuracion Completada!
========================================

FARARONI esta listo para usar.

Comandos utiles:
  fararoni              - Iniciar shell interactivo
  fararoni config show  - Ver configuracion
  fararoni --help       - Ver ayuda

Que tengas una excelente experiencia!
```

### 3.12 — Descarga del motor local

Inmediatamente despues, Fararoni detecta que faltan los componentes del motor local
(porque elegiste la opcion 2 — Hibrido) y te ofrece descargarlos:

```
[BOOTSTRAP] Verificando componentes del motor neural...
   [X] Motor nativo: NO INSTALADO
   [X] Modelo local (1.5B): NO DESCARGADO

[WARN] ATENCION: Faltan componentes para el modo local.
   Para funcionar localmente, se necesita descargar:
   • Motor nativo (~5 MB)
   • Modelo Qwen 1.5B (~1.2 GB)

   ¿Deseas iniciar la descarga ahora? [S/n]:
```

**Escribe `S` y presiona Enter.** La descarga comienza automaticamente:

```
[DOWNLOAD] Descargando motor nativo (~5 MB)...
   [OK] Motor descargado
   [OK] Motor nativo instalado

[DOWNLOAD] Descargando modelo Qwen 1.5B (~1.2 GB)...
   (Esto puede tomar varios minutos)
   Descargando: 2.9% (30.8 MB / 1.04 GB)
```

La descarga del motor nativo es rapida (~5 MB). El modelo Qwen 1.5B es mas grande
(~1.2 GB) y puede tomar varios minutos dependiendo de tu conexion a internet.
**Espera a que termine** — veras una barra de progreso.

> **Que se descarga?** El motor nativo es `ollama.cpp`, una libreria de inferencia
> que permite ejecutar modelos LLM localmente sin depender del servidor Ollama.
> El modelo Qwen 1.5B es un modelo pequeno que se usa como fallback para routing
> y tareas simples cuando Ollama no esta disponible.

### 3.13 — Inicializacion del sistema

Una vez completada la descarga, Fararoni arranca todos sus subsistemas:

```
[OK] Modelo descargado
[OK] Modelo local instalado

[OK] [BOOTSTRAP] Motor instalado y verificado.
Fararoni Online. Analizando territorio...
```

Fararoni escanea tu directorio de trabajo y muestra un resumen:

```
============ CONCIENCIA SITUACIONAL ============
Proyecto Detectado: (tipo de proyecto)
Ubicacion: tu-usuario
Archivos encontrados: (cantidad)
------------------------------------------------
ESTRUCTURA DE ARCHIVOS:
...
================================================
```

Luego inicializa todos los subsistemas internos:

```
Inicializando subsistemas...
[FARARONI] Enlace primario estable.
[FARARONI]  Persistencia Blindada ACTIVA (Outbox Pattern: sovereign_bus.db)
[FARARONI]  Repositorio de canales inicializado
[FARARONI]  MessagingChannelManager iniciado
[FARARONI]  Security Auth ACTIVO (TOTP + Sandbox + BCrypt)
[FARARONI]  GalvanicAgent iniciado en Bus Soberano
[FARARONI]  QuartermasterAgent iniciado en Bus Soberano
[FARARONI]  AgentTemplateManager iniciado (10 agentes activos, LLM inyectado)
[FARARONI]  SovereignMissionEngine activo
[RECOVERY] Servicio de Recuperacion de Desastres iniciado
[SAFETY] FileSystemIntentListener activo (Write + Restore)
[TOOL-REGISTRY] Herramientas base: 36+
 Dynamic Tool Prompting activado
```

> No te preocupes por estos mensajes — son logs informativos del arranque.
> Lo importante es que no aparezcan errores `[ERROR]`.

### 3.14 — Banner de bienvenida

Finalmente aparece el banner de Fararoni con la configuracion activa:

```
╔═══════════════════════════════════════════════════════════════════════╗
║                                                                       ║
║  ███████╗ █████╗ ██████╗  █████╗ ██████╗  ██████╗ ███╗   ██╗██╗       ║
║  ██╔════╝██╔══██╗██╔══██╗██╔══██╗██╔══██╗██╔═══██╗████╗  ██║██║       ║
║  █████╗  ███████║██████╔╝███████║██████╔╝██║   ██║██╔██╗ ██║██║       ║
║  ██╔══╝  ██╔══██║██╔══██╗██╔══██║██╔══██╗██║   ██║██║╚██╗██║██║       ║
║  ██║     ██║  ██║██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚████║██║       ║
║  ╚═╝     ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝       ║
║                                                                       ║
║                   THE EXTRA COMPASS EDITION                           ║
║     Inspired by the legacy of: Crispin Fararoni Alfonso (C.F. I)      ║
║                                                                       ║
║                        FARARONI Core v1.0.0                           ║
║                         Open Source LLM CLI                           ║
╠═══════════════════════════════════════════════════════════════════════╣
║  CONFIGURACION ACTIVA:                                                ║
║  [>] Servidor: http://localhost:11434                     (config)    ║
║  [>] Modelo:   qwen2.5-coder:1.5b-instruct                (config)    ║
║  [>] Contexto: 32,768 tokens | Streaming: + | Tokenizer: REMOTE       ║
╠═══════════════════════════════════════════════════════════════════════╣
║  [i] Variables de entorno: LLM_SERVER_URL, LLM_MODEL_NAME, LLM_API_KEY║
║  [>] Configurar: fararoni config set <key> <value>                    ║
╚═══════════════════════════════════════════════════════════════════════╝
```

Y el prompt interactivo:

```
❯
── ▶▶ qwen2.5-coder:1.5b · Local | qwen2.5-coder:32b · Remote     Context: 0%
```

La barra inferior muestra los dos modelos y su estado:

```
▶▶ qwen2.5-coder:1.5b · Local -A | qwen2.5-coder:32b · Remote -I     Context: 0%
```

| Indicador | Significado |
|-----------|-------------|
| **Local -A** (verde) | Modelo Rabbit **Activo** — esta respondiendo ahora |
| **Remote -I** | Modelo Turtle **Inactivo** — en espera |
| **Context: 0%** | Porcentaje de ventana de contexto utilizada |

### Ejemplo: primera conversacion

```
❯ hola

Fararoni: Hola! En que te ayudo?

❯
── ▶▶ qwen2.5-coder:1.5b · Local -A | qwen2.5-coder:32b · Remote -I
```

En este ejemplo, el Rabbit (Local) responde porque es una pregunta simple.
La barra muestra `Local -A` (Activo, en verde) y `Remote -I` (Inactivo).

Fararoni cambia automaticamente entre modelos segun la complejidad:

| Tipo de pregunta | Modelo usado | Barra de estado |
|------------------|-------------|-----------------|
| Chat casual ("hola", "que hora es") | **Rabbit** (Local) | `Local -A \| Remote -I` |
| Analisis de codigo, bugs complejos | **Turtle** (Remote) | `Local -I \| Remote -A` |
| Tool calling, misiones multi-agente | **Turtle** (Remote) | `Local -I \| Remote -A` |

El cambio es automatico — no necesitas hacer nada. El Rabbit responde en milisegundos
para tareas simples, y cuando detecta que la tarea es compleja, escala al Turtle
automaticamente. Tambien puedes forzar el cambio con `/reconfig` (Rabbit) o
`/reconfigt` (Turtle).

**Listo! Fararoni esta operativo.**

### Siguiente paso recomendado

Ahora que verificaste que Fararoni funciona, te sugerimos:

1. **Cierra esta terminal** (escribe `/exit` o Ctrl+C)
2. **Navega a tu carpeta de confianza** (la que configuraste en el Sandbox):
   ```bash
   cd ~/Documents/Proyectos
   ```
3. **Vuelve a iniciar Fararoni** desde ahi:
   ```bash
   fararoni
   ```
   Esto hace que Fararoni analice tu proyecto real en vez del directorio home.

4. **Si quieres configurar WhatsApp u otros canales**, inicia en modo servidor:
   ```bash
   fararoni --server
   ```
   Esto levanta el Core + Gateway, y luego en otra terminal inicias el sidecar
   (ver [Paso 5](#paso-5-opcional--activar-canales-de-mensajeria)).

---

## Paso 4 — Tu Primera Conversacion

Escribe cualquier pregunta en espanol o ingles:

```
fararoni> hola, analiza este proyecto

fararoni> crea un archivo README.md para mi proyecto

fararoni> explica que hace la clase Main.java
```

### Comandos utiles

| Comando | Que hace |
|---------|----------|
| `/help` | Ver todos los comandos |
| `/status` | Estado del sistema (modelo, memoria) |
| `/reconfig` | Cambiar modelo en caliente |
| `/exit` | Salir |

---

## Paso 5 (Opcional) — Activar Canales de Mensajeria

Si quieres que Fararoni responda por WhatsApp, Telegram, Discord o iMessage,
necesitas levantar el servidor y los sidecars.

### 5.1 — Levantar Core en modo servidor

```bash
# Terminal 1 — binario nativo (no requiere Java)
fararoni --server

# O con ruta completa
~/Fararoni/bin/fararoni-core --server
```

Esto levanta:
- Core Server en puerto **7070**
- OmniChannel Gateway en puerto **7071** (automatico)

### 5.2 — Levantar un Sidecar

Cada canal es un binario nativo independiente (no requiere Node.js).
Ejemplo con WhatsApp:

```bash
# Terminal 2
~/Fararoni/bin/sidecar-whatsapp
```

Escanea el **codigo QR** con WhatsApp (Dispositivos vinculados > Vincular).

Otros canales:

```bash
# Telegram (necesita token de @BotFather)
export TELEGRAM_TOKEN="tu_token"
~/Fararoni/bin/sidecar-telegram

# Discord (necesita token del Developer Portal)
export DISCORD_TOKEN="tu_token"
~/Fararoni/bin/sidecar-discord

# iMessage (necesita BlueBubbles)
export BLUEBUBBLES_PASSWORD="tu_password"
~/Fararoni/bin/sidecar-imessage
```

### 5.3 — MCP Bridge (Model Context Protocol)

Fararoni incluye un sidecar MCP (Model Context Protocol) que permite integrar
servidores MCP externos con el ecosistema. A diferencia de los otros sidecars
(que son binarios SEA de Node.js), el MCP Bridge es un modulo Java que se
compila con Maven.

```bash
# Compilar el MCP sidecar
cd fararoni-sidecar-mcp
mvn clean package -DskipTests

# Ejecutar
java -jar target/fararoni-sidecar-mcp-1.0.0.jar
```

> **Estado**: Operativo. Proximamente se publicaran resultados de pruebas
> y documentacion detallada de integracion con servidores MCP.

Para compilar el MCP sidecar si necesitas Java 25+ (a diferencia del core
y los sidecars de mensajeria que son binarios nativos).

---

## Resumen de Puertos

| Puerto | Servicio |
|--------|----------|
| 7070 | Fararoni Core Server |
| 7071 | OmniChannel Gateway |
| 3000 | Sidecar WhatsApp |
| 3001 | Sidecar Telegram |
| 3002 | Sidecar Discord |
| 3003 | Sidecar iMessage |
| 11434 | Ollama |

---

## Verificar que Todo Funciona

```bash
# Ollama corriendo?
curl http://localhost:11434/api/tags

# Core Server corriendo?
curl http://localhost:7070/health

# Gateway corriendo?
curl http://localhost:7071/health

# Sidecar WhatsApp corriendo?
curl http://localhost:3000/health
```

---

## Problemas Comunes

| Problema | Solucion |
|----------|----------|
| `Connection refused :11434` | Ejecutar `ollama serve` en otra terminal |
| Terminal se congela despues de instalar | Normal — cierra con Cmd+W y abre una nueva |
| macOS bloquea el instalador | Clic derecho > Abrir > Abrir |
| QR de WhatsApp no aparece | Verificar permisos: `chmod +x ~/Fararoni/bin/sidecar-whatsapp` |
| `command not found: fararoni` | Agregar PATH: `echo 'export PATH="$HOME/bin:$PATH"' >> ~/.zshrc && source ~/.zshrc` |

---

## Reiniciar Configuracion o Reinstalar

### Si te saliste antes de terminar la configuracion

Si cerraste la terminal o presionaste Ctrl+C durante el wizard de primera ejecucion,
Fararoni detecta que faltan componentes y te ofrece continuar:

```
fararoni
[KERNEL] Iniciando Fararoni en: /Users/tu-usuario
[WorkspaceManager] Initialized: /Users/tu-usuario/.llm-fararoni (User Home Default)

[BOOTSTRAP] Verificando componentes del motor neural...
   [X] Motor nativo: NO INSTALADO
   [X] Modelo local (1.5B): NO DESCARGADO

[WARN] ATENCION: Faltan componentes para el modo local.
   Para funcionar localmente, se necesita descargar:
   • Motor nativo (~5 MB)
   • Modelo Qwen 1.5B (~1.2 GB)

   ¿Deseas iniciar la descarga ahora? [S/n]:
```

**Escribe `S` y presiona Enter** para continuar donde te quedaste.

### Si quieres repetir la configuracion desde cero (sin reinstalar)

Solo borra la carpeta de configuracion y vuelve a ejecutar `fararoni`:

```bash
rm -rf ~/.llm-fararoni
fararoni
```

Esto reinicia el wizard de primera ejecucion. Los binarios en `~/Fararoni/` no se tocan.

### Desinstalar completamente

Para eliminar Fararoni por completo (binarios + configuracion + datos):

```bash
rm -rf ~/Fararoni
rm -rf ~/.fararoni
rm -rf ~/.llm-fararoni
```

Si instalaste con Homebrew:

```bash
brew uninstall fararoni
brew untap ebercruzf/fararoni
```

---

*Fararoni v1.0.0 — Sovereign AI Agent Orchestrator*
*Licencia: Apache 2.0*
*Autor: Eber Cruz*
