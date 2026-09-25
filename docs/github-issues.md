# Issues para GitHub Projects

Lista lista para copiar y pegar. Cada bloque es un issue: el título va en el campo de título,
el cuerpo en la descripción, y las etiquetas se añaden desde la barra lateral.

**Milestone único para todos:** `Entrega Semana 7 — 25 de septiembre`

**Etiquetas a crear primero** (Issues → Labels → New label):

| Etiqueta | Color sugerido | Para qué |
|---|---|---|
| `infra` | `#0E8A16` | Despliegue, Docker, CI |
| `feature` | `#1D76DB` | Funcionalidad nueva |
| `docs` | `#5319E7` | Informe y documentación |
| `test` | `#FBCA04` | Pruebas y verificación |
| `bug` | `#D73A4A` | Defectos |
| `blocked` | `#B60205` | Detenido por una dependencia externa |
| `priority:alta` | `#E99695` | Entra sí o sí en la entrega |
| `priority:media` | `#F9D0C4` | Deseable |

---

## Issue 1

**Título:** Desplegar el backend en AWS (ECS + RDS)

**Etiquetas:** `infra`, `priority:alta`, `blocked`

**Asignado sugerido:** Sergio Rojas Llanos

**Descripción:**
```
Desplegar la API en AWS para cubrir la seccion 8 de la rubrica (2.0 puntos).

Pasos:
- Instancia PostgreSQL 16 en RDS, sin acceso publico, en la misma VPC.
- Repositorio en ECR y subida de la imagen construida con el Dockerfile actual.
- Servicio ECS Fargate detras de un Application Load Balancer.
- Variables de entorno: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, MAIL_*.
- Security groups: RDS solo acepta trafico del SG de ECS en el 5432.

Bloqueado: a la espera de las credenciales de AWS.
Alternativa si no llegan a tiempo: Railway, que vale 1.0 en vez de 2.0.

Criterio de aceptacion:
- GET /api/v1/equipment-categories responde 200 desde el dominio publico.
- Swagger carga desde ese dominio.
- Ninguna credencial en el repositorio.
```

---

## Issue 2

**Título:** Añadir el enlace de deployment al README

**Etiquetas:** `docs`, `priority:alta`

**Asignado sugerido:** Sergio Rojas Llanos

**Descripción:**
```
La portada del README tiene la linea "Deployment: pendiente". Sustituirla por la URL
real una vez desplegado.

Con esto se cierra el criterio 9.1 de la rubrica: el diagrama de arquitectura ya esta
incluido, y el enlace es lo unico que falta para los 0.4 completos.

Depende del issue 1.
```

---

## Issue 3

**Título:** Configurar CI con GitHub Actions

**Etiquetas:** `infra`, `priority:media`

**Asignado sugerido:** Ernesto Rodrigo Corzo

**Descripción:**
```
Crear .github/workflows/ci.yml que en cada push y pull request a main:
- haga checkout
- configure JDK 21 (actions/setup-java@v4, distribucion temurin)
- cachee las dependencias de Maven
- ejecute ./mvnw -B verify

Las pruebas corren sobre H2, asi que NO hace falta levantar PostgreSQL en el runner.
En Linux hay que dar permisos al wrapper: chmod +x mvnw.

Cuenta como elemento bonus de la rubrica (CI/CD).

Criterio de aceptacion:
- El workflow aparece en verde sobre un commit de main.
- Un PR abierto muestra el check antes de poder mergear.
- El badge de estado esta en el README.
```

---

## Issue 4

**Título:** Verificar la colección de Postman contra la API levantada

**Etiquetas:** `test`, `priority:alta`

**Asignado sugerido:** Valeria Ayala Vega

**Descripción:**
```
postman_collection.json se regenero tras el refactor de seguridad, pero solo se valido
su estructura: nunca se ejecuto contra el backend corriendo.

Pasos:
- docker compose down -v && docker compose up -d --build
- Importar la coleccion y correrla completa con el Collection Runner
- Verificar que las 39 peticiones pasan sobre una base limpia

Ojo: la coleccion genera RUC, emails y numero de serie unicos por ejecucion, asi que
se puede correr varias veces seguidas.

Criterio de aceptacion:
- El Runner termina sin fallos.
- Los casos de error devuelven los codigos esperados: 401, 403, 409.
```

---

## Issue 5

**Título:** Verificar el envío de correos de extremo a extremo en Mailpit

**Etiquetas:** `test`, `priority:media`

**Asignado sugerido:** Anthony Cadenas Hidalgo

**Descripción:**
```
El modulo de eventos esta cubierto por pruebas (publicacion y renderizado de plantillas),
pero nunca se vio un correo llegar a un servidor SMTP real.

Pasos:
- docker compose up -d --build (levanta Mailpit junto al backend)
- Crear una reserva desde Postman
- Abrir http://localhost:8025 y comprobar que llegan los dos correos: al arrendador y
  al arrendatario
- Confirmar la reserva y comprobar el correo de confirmacion
- Cancelarla y comprobar que llega a ambas partes con el motivo

Criterio de aceptacion:
- Los correos se ven en Mailpit con el HTML bien formado.
- Los montos y fechas coinciden con la reserva.
```

---

## Issue 6

**Título:** Crear el tablero de GitHub Projects con labels y milestone

**Etiquetas:** `docs`, `priority:alta`

**Asignado sugerido:** Mary Sofía Ramos Calderón

**Descripción:**
```
Cubre el criterio 9.3 de la rubrica (0.2 puntos).

Pasos:
- Crear un Project de tipo tablero asociado al repositorio.
- Columnas: Backlog / En progreso / En review / Hecho.
- Crear las etiquetas listadas en docs/github-issues.md.
- Crear el milestone "Entrega Semana 7 - 25 de septiembre".
- Dar de alta estos issues, asignarlos y asociarlos al milestone.
- Enlazar cada PR a su issue con "Closes #N" para que el tablero se mueva solo.
```

---

## Issue 7

**Título:** Revisar y aprobar el PR de eventos y correo

**Etiquetas:** `test`, `priority:alta`

**Asignado sugerido:** Ernesto Rodrigo Corzo (revisor)

**Descripción:**
```
La rama feature/domain-events-and-mail esta subida y lista para PR.

La rubrica premia explicitamente los pull requests con code review (criterio 9.2),
asi que debe aprobarlo un integrante distinto al autor. Una autoaprobacion se sostiene
mucho peor ante el corrector.

Que revisar:
- Que ./mvnw test pase en local (29 pruebas).
- Que el listener sea AFTER_COMMIT y @Async, no un @EventListener normal.
- Que los eventos lleven ReservationSnapshot y no entidades JPA.
- Que ningun fallo de correo pueda propagarse al flujo de reserva.
```

---

## Issue 8

**Título:** Migrar de ddl-auto=update a Flyway

**Etiquetas:** `infra`, `priority:media`

**Asignado sugerido:** Sergio Rojas Llanos

**Descripción:**
```
Hoy el esquema lo genera Hibernate con ddl-auto=update. Sirve para la demo, pero en
produccion no permite versionar ni revisar los cambios de esquema.

Pasos:
- Añadir la dependencia de Flyway.
- Generar el script inicial V1__schema.sql desde el esquema actual.
- Cambiar ddl-auto a validate.

No es imprescindible para la entrega, pero es la primera deuda tecnica a pagar despues.
```

---

## Issue 9

**Título:** Persistir los refresh tokens para poder revocarlos

**Etiquetas:** `feature`, `priority:media`

**Asignado sugerido:** Anthony Cadenas Hidalgo

**Descripción:**
```
Los refresh tokens actuales son sin estado: una vez emitidos son validos hasta que
expiran (7 dias), y no hay forma de invalidar uno robado.

Propuesta:
- Entidad RefreshToken con el hash del token, usuario, expiracion y marca de revocado.
- Invalidar el anterior al emitir uno nuevo (rotacion).
- Endpoint de logout que revoque el token activo.

Mitigacion parcial ya implementada: /auth/refresh recarga al usuario desde la base, asi
que una cuenta deshabilitada deja de renovar de inmediato.
```

---

## Issue 10

**Título:** Subir imágenes de maquinaria a S3 de forma asíncrona

**Etiquetas:** `feature`, `priority:media`

**Asignado sugerido:** Valeria Ayala Vega

**Descripción:**
```
Equipment.imageUrls guarda URLs, pero no hay endpoint de subida: hoy las URLs se
escriben a mano.

Propuesta:
- Endpoint POST /api/v1/equipment/{id}/images con multipart.
- Subida a S3 en segundo plano con @Async, reutilizando el executor de AsyncConfig.
- Guardar la URL resultante en la entidad.

Es un elemento bonus de la rubrica (upload de archivos a S3) y encaja con la
infraestructura de asincronia ya montada.
```
