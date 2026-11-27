# Duo

Duo es un planificador para dos personas: la semana de cada uno, los planes que hacéis juntos, el dinero y los gastos con amigos, todo en una sola web que corre en tu propio ordenador.

Nació como el proyecto personal con el que mi pareja y yo nos organizamos. Este repositorio es la versión para que cualquiera la descargue y la use: sin contraseñas ni nombres reales, solo el código de la web, lista para descargar y modificar. Es gratis y abierta (licencia MIT); si te sirve, me alegrará mucho.

**Página del proyecto:** [landing.duo-planing.uk](https://landing.duo-planing.uk): qué es Duo, capturas y cómo está hecho.

Autor: **Denis Navío** ([@denisnavioo](https://github.com/denisnavioo)).

## Qué incluye

- **Resumen:** próximos planes, notas del día, la semana y tu ahorro del mes.
- **Calendario compartido:** planes de cada uno o de los dos. Los planes juntos quedan pendientes hasta que la otra persona los acepta o rechaza. Cada plan admite descripción con enlaces y hasta 6 fotos.
- **Avisos en el móvil:** cada día a las 20:00, una notificación con los planes del día siguiente (Web Push estándar: el aviso va cifrado y el servicio que lo entrega no puede leerlo). Se activa en **Mi perfil**. En iPhone hay que añadir Duo a la pantalla de inicio.
- **Horarios:** bloques semanales de clase, trabajo y otras actividades.
- **Ahorros:** ingresos, plan de reparto mensual, gastos, suscripciones, movimientos y metas. Lo personal solo lo ve su dueño.
- **Conexión bancaria opcional:** con [Enable Banking](https://enablebanking.com) (PSD2, solo lectura) importa movimientos y saldo. Duo no inicia pagos ni pide las claves del banco.
- **Repartir:** quedadas con amigos, quién pagó qué y quién debe a quién; cada Bizum se puede comprobar con un movimiento del banco.
- **Nota diaria y novedades:** mensajes entre los dos y avisos de lo que cambia.

La interfaz está en español, funciona en móvil y ordenador, y sigue el modo claro u oscuro del dispositivo. Azul para la primera persona, rosa para la segunda y violeta para lo de los dos.

## Cómo se entra

No hay contraseñas. Al abrir Duo eliges **«¿Quién eres?»** entre las dos personas y la sesión lo recuerda; desde el menú puedes **cambiar de persona**.

Por eso Duo solo escucha en tu propio ordenador (`127.0.0.1`): es para usarla y modificarla en local.

## Ejecutar

Necesitas **JDK 21** (`java -version`). No hace falta instalar Maven: el repositorio trae el Maven Wrapper.

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usa `./mvnw spring-boot:run`. Abre <http://localhost:8080> y elige quién eres.

Para generar un JAR:

```powershell
.\mvnw.cmd -DskipTests package
java -jar .\target\duo-0.0.1-SNAPSHOT.jar
```

Los datos se guardan en `data/duo.mv.db` (H2 en un archivo). Haz copia de esa carpeta si te importan; está en `.gitignore`.

## Configuración

Todo es opcional. Se pasa con variables de entorno:

| Variable | Por defecto | Para qué |
|---|---|---|
| `DUO_PERSONA_A_NOMBRE` | `Ana` | Nombre de la primera persona (azul) al crearla. Luego se cambia en **Mi perfil**. |
| `DUO_PERSONA_B_NOMBRE` | `Leo` | Nombre de la segunda persona (rosa). |
| `DUO_AVISOS_CRON` | `0 0 20 * * *` | Cuándo se manda el aviso de los planes de mañana. |
| `DUO_AVISOS_CONTACTO` | `https://example.com` | Web de contacto en la firma de los avisos (lo piden Apple y Google). |
| `DUO_BANCO_APP_ID` / `DUO_BANCO_CLAVE_PRIVADA` | — | Credenciales de tu aplicación en Enable Banking. Sin ellas, la sección Banco se muestra como no disponible. |
| `DUO_BANCO_NOMBRE` | — | Tu banco tal como aparece en Enable Banking (basta con una parte del nombre). |
| `DUO_BANCO_USUARIOS` | `persona-a,persona-b` | Quién puede conectar su banco. |
| `DUO_BANCO_REDIRECT_URL` | `http://localhost:8080/ahorros/banco/vuelta` | URL de vuelta registrada en Enable Banking. |

Los identificadores internos de las dos personas son `persona-a` y `persona-b`.

## Tests

```powershell
.\mvnw.cmd test
```

Usan una base H2 en memoria; no tocan `data/`.

## Cómo está hecho

- Java 21, Spring Boot 4, Spring MVC, Spring Security (sesión, CSRF y cabeceras como CSP) y Spring Data JPA / Hibernate.
- Vistas con Thymeleaf; HTML, CSS y JavaScript sin framework ni paso de compilación.
- H2 como base de datos, guardada en un archivo.
- Web Push implementado a mano (VAPID y cifrado `aes128gcm`), sin librerías externas.

```text
src/main/java/com/pareja/duo/
├── actividad/   calendario y planes
├── ahorros/     finanzas, plan, metas y banco
├── aviso/       avisos en el móvil
├── config/      seguridad y datos iniciales
├── horario/     horarios semanales
├── nota/        nota diaria
├── novedad/     campanita de novedades
├── repartir/    quedadas y deudas
├── usuario/     perfil
└── web/         páginas y «¿Quién eres?»
```

## Licencia

MIT: puedes usar, copiar y adaptar el código libremente manteniendo el aviso de copyright. Ver [`LICENSE`](LICENSE).
