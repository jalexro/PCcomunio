# Liga Comunio

Juego tipo Comunio sobre LaLiga: ligas privadas con amigos, plantilla propia, mercado de fichajes,
alineación por jornadas y clasificación. Hay una versión web y el proyecto está preparado (aunque
aparcado) para empaquetarse como aplicación Android con Capacitor.

La temporada que se juega es **LaLiga 2024/25**: los equipos, las plantillas y el calendario son reales,
y los partidos se **simulan** jornada a jornada para poder llegar a la 38 sin depender de una API de pago.

---

## Cómo se juega

1. **Creas una liga** o te unes a una con su código de 8 caracteres. Solo se puede entrar antes de que se
   juegue la primera jornada.
2. **Quien crea la liga elige con qué se empieza** (y vale igual para quien se una después):

   | Modalidad | Con qué empiezas |
   | --- | --- |
   | **11 al azar** | 11 jugadores gratis (1 portero, 4 defensas, 4 centrocampistas, 2 delanteros) y el presupuesto que se haya fijado (40 M€ por defecto). |
   | **Sin jugadores** | La plantilla vacía y el presupuesto que se haya fijado: todo se ficha en el mercado. |
   | **11 al azar con tope** | 11 jugadores cuya **suma de valores no pasa de un tope** (30 M€ por defecto) y, aparte, un presupuesto (20 M€ por defecto). |

   Ojo con «Sin jugadores»: por la penalización de puestos vacíos (abajo), quien no fiche a nadie llega a
   la primera jornada con **−44 puntos**. Es a propósito, para que haya que moverse en el mercado.
3. **Fichas y vendes** en el mercado: 10 jugadores libres rotando cada jornada (se puja y se resuelve al
   avanzar), ofertas directas a otros miembros, y venta instantánea al sistema por el valor del jugador.
   Un jugador solo puede tener un dueño en cada liga.
4. **Alineas a 11** en la pestaña **Táctica**: el campo a un lado y el banquillo al otro, y arrastras a
   cada jugador a su puesto (o lo tocas y después tocas el puesto, que es lo que funciona en el móvil).
   Las tácticas son 4-3-3, 5-3-2, 4-4-2 y 3-5-2, y el campo cambia de puestos al elegirla. Un puesto solo
   acepta jugadores de su posición, y soltar encima de uno ocupado intercambia a los dos. El banquillo no
   puntúa. La pestaña **Jugadores** es la otra mitad: ahí ves lo que vale cada uno, lo que pagaste, lo que
   puntuó y puedes venderlo.
5. **Se juega la jornada**: cada titular aporta los puntos que ha hecho en su partido y cobras por ellos.

### Cuándo se juega la jornada

El dueño de la liga elige el modo en la pestaña **Opciones**:

| Modo | Qué pasa |
| --- | --- |
| **Por días** | La jornada se juega cuando vence el plazo (3 días por defecto; se puede bajar hasta 10 s para probar). |
| **Al confirmar todos** | La jornada se juega en cuanto **todos** los miembros confirman su alineación. El plazo pasa a ser la **fecha límite**: a quien no haya confirmado se le juega la alineación que tenga puesta. |

En los dos modos, si no cambias nada se juega con la alineación de la jornada anterior. Guardar una
alineación nueva deshace tu confirmación, así que confirma cuando la tengas como quieras.

### Puestos vacíos: −4 puntos

Si vendes o te compran un titular, ese puesto de la táctica se queda **vacío**. La jornada se juega igual
con los que te queden, pero **cada puesto vacío resta 4 puntos**. Es decir, quedarse con 9 titulares
cuesta 8 puntos antes de empezar. La clasificación muestra cuántos huecos tenía cada uno en la última
jornada.

### Dinero

Al puntuar una jornada cada miembro cobra:

- **100 000 € por punto** (configurable por el dueño), y
- un **premio por el puesto** que ocupa en esa jornada (3 M€, 2 M€ y 1 M€ por defecto).

Los empates comparten puesto y premio, y quien no suma puntos positivos no cobra nada.

### Puntuación de un jugador

Los pesos están todos en `ScoringService` y son ajustables. A grandes rasgos: minutos jugados, goles,
asistencias, entradas ganadas, porcentaje de pases acertados y tiros fallados, con pesos distintos según
la posición (un gol de un defensa vale más que uno de un delantero). Las tarjetas restan. Los porteros
puntúan por paradas, portería a cero y goles encajados.

---

## Los datos

| Qué | De dónde sale |
| --- | --- |
| Equipos, plantillas y calendario de 2024/25 | API-Football (liga 140), vía `/api/admin/import/*` |
| Valor de mercado y estado (lesionado, sancionado, duda) | Archivo JSON de Biwenger subido a mano |
| Valor de los jugadores que no estén en ese archivo | Generado de forma estable a partir de su identificador |
| Estadísticas de cada jugador en cada partido | **Simuladas** (salvo las jornadas ya importadas de verdad) |

### Por qué se simula

El plan gratuito de API-Football da 100 peticiones al día y hace falta **una por partido** para las
estadísticas: 380 partidos serían casi cuatro días de cuota, y la temporada en curso exige plan de pago.
Las plantillas completas, en cambio, caben en un día (unas 30 peticiones). Así que se importan los datos
que sí son asequibles —equipos, jugadores y calendario, todos reales— y se inventan las actuaciones.

La simulación (paquete `simulacion`) funciona así:

- Es **reproducible**: el azar se siembra con el identificador del partido y del jugador más
  `app.simulacion.semilla`, nunca con el reloj. Simular dos veces la misma jornada da lo mismo, y todas
  las ligas ven los mismos puntos. Cambiar la semilla genera otra temporada.
- Respeta el **marcador real** del partido cuando existe (los de 2024/25 lo tienen) y solo inventa quién
  marcó; si no hay marcador, también lo genera.
- Alinea a los **mejores por valor de mercado** con algo de rotación, hace 2-4 cambios por partido y
  reparte goles, asistencias, entradas, tarjetas, pases y paradas según la posición y el nivel.
- **Nunca sobrescribe** estadísticas que ya existan, así que las jornadas importadas de verdad se
  conservan y se pueden mezclar con las simuladas.
- Los puntos los calcula el mismo `ScoringService` que con los datos reales: el simulador solo produce
  datos brutos.

---

## Puesta en marcha

### Con Docker (lo normal)

```bash
cp .env.example .env     # y rellena JWT_SECRET como mínimo
docker compose up --build
```

- Frontend: <http://localhost:5173>
- Backend: <http://localhost:8080>
- PostgreSQL: `localhost:5432` (base `ligacomunio`, usuario y clave `ligacomunio`)

Variables de `.env` que importan: `JWT_SECRET` (obligatoria, 32 caracteres o más: sin ella el backend no
arranca), `ADMIN_EMAIL` y `ADMIN_PASSWORD` (crean el primer administrador al arrancar),
`API_FOOTBALL_KEY` (solo para importar datos nuevos) y las de correo (`MAIL_*`, opcionales: sin
`MAIL_HOST` los enlaces de verificación y de recuperación de contraseña salen por el log del backend).

### Preparar los datos la primera vez

Con una cuenta de administrador, **en este orden**:

```bash
POST /api/admin/import/teams?season=2024      # 1 petición
POST /api/admin/import/squads?season=2024     # ~30 peticiones: catálogo completo de jugadores
POST /api/admin/import/fixtures?season=2024   # 1 petición: las 38 jornadas
POST /api/admin/import/values/biwenger        # multipart, campo "file": el JSON de Biwenger
POST /api/admin/simulate/season?season=2024   # simula de golpe toda la temporada (no gasta cuota)
```

El último paso es opcional: si no se hace, cada jornada se simula sola la primera vez que una liga tiene
que jugarla.

El JSON de valores se baja a mano desde el navegador de
`https://cf.biwenger.com/api/v2/competitions/la-liga/data?lang=es&score=5` y se guarda como
`biwinger-laliga.json` (no se hacen peticiones automáticas a Biwenger).

### Sin Docker

```bash
cd backend  && ./mvnw spring-boot:run   # necesita un PostgreSQL accesible
cd frontend && npm install && npm run dev
```

### Android (aparcado)

La plataforma está generada en `frontend/android/` y funciona, pero **no se está manteniendo**: la
versión móvil queda para una actualización futura. Para retomarla hace falta crear a mano
`frontend/.env.android` (no se versiona, porque lleva una IP de tu red):

```bash
# frontend/.env.android
VITE_API_URL=http://10.0.2.2:8080   # emulador; para un móvil real, la IP del PC en la Wi-Fi
```

Y después `npm run build:android` y `npx cap sync android`. Antes de publicar hay que quitar
`usesCleartextTraffic="true"` del manifest y `androidScheme: 'http'` de `capacitor.config.ts`.
iOS no se puede compilar desde Windows.

---

## Cuentas y seguridad

- Registro con email, nombre de usuario y contraseña (bcrypt). Roles `USER` y `ADMIN`.
- Sesión con **access token** JWT de 15 minutos y **refresh token** opaco de 30 días con rotación; en la
  base de datos solo se guarda su hash SHA-256.
- **Verificación de email** y **recuperación de contraseña** por enlace de un solo uso.
- **Límite de intentos de login**: 5 fallos seguidos por email o por IP bloquean 15 minutos.
- `/api/admin/**` exige rol `ADMIN`.

Queda pendiente servir por HTTPS y cifrar de verdad el token guardado en el móvil.

---

## Cómo está hecho

```
backend/     Spring Boot 3.5 · Java 21 · Spring Data JPA · PostgreSQL · Flyway
frontend/    React 19 · TypeScript · Vite · react-router · Capacitor
docker-compose.yml
```

Paquetes del backend, por dominio: `auth` y `user` (cuentas y sesiones), `team`, `player`, `match`,
`scoring` (reglas de puntuación), `importacion` (API-Football detrás de la interfaz `StatsProvider`),
`valores` (valores de mercado), `simulacion` (partidos inventados) y `league` (ligas, plantillas,
mercado, jornadas y clasificación).

El esquema lo gestiona Flyway (`backend/src/main/resources/db/migration`) e Hibernate solo valida.

### Comandos

```bash
cd backend  && ./mvnw test            # tests (los de integración se omiten si no hay Docker)
cd backend  && ./mvnw -DskipTests package
cd frontend && npm run build          # tsc + vite
cd frontend && npm run lint           # oxlint
```

Los tests de integración levantan un PostgreSQL real con Testcontainers. `SimulacionIntegrationTest`
juega cinco jornadas seguidas con cuatro usuarios en la misma liga.

Para el detalle de decisiones, convenciones y trampas ya resueltas, ver `CLAUDE.md`.
