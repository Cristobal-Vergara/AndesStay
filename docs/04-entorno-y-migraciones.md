# Paso 4: entorno local, secretos y migraciones

## Problema y solución

Había configuración de conexiones y credenciales mezclada con el código y varios archivos de arranque que podían divergir. Las imágenes dependían de artefactos construidos previamente. Además, Hibernate modificaba automáticamente la estructura, sin una secuencia versionada verificable.

Ahora el arranque principal es `scripts/start-local.cmd`, que utiliza un Compose común. Cada aplicación se construye desde sus fuentes con un Dockerfile de varias etapas. Maven usa 3.9.15 y los cuatro módulos comparten Java 17 y Spring Boot 4.1.1. Los POM eliminan dependencias redundantes y el controlador Oracle ajeno a la base MySQL de esta aplicación.

Kafka funciona con KRaft, por lo que el entorno ya no necesita ZooKeeper. Los servicios esperan la salud de sus dependencias y las aplicaciones exponen pruebas de vida mediante Actuator. El frontend utiliza el mismo origen para llamadas a la API; CORS mantiene una lista configurable de orígenes permitidos.

Las versiones de imágenes están declaradas en Dockerfiles/Compose y npm utiliza su lockfile. Las etiquetas de imágenes pueden recibir revisiones del proveedor; una instalación que necesite fijar exactamente cada imagen debe conservar sus digest, además de las versiones.

## Secretos

`setup-local.cmd` genera contraseñas aleatorias e independientes para el administrador de MySQL, la cuenta de aplicación y RabbitMQ. Las guarda en `.env`, ignorada por Git. Las propiedades requieren contraseñas del entorno, sin contraseñas de respaldo dentro del código.

La clave PEM que estaba en el árbol del proyecto fue trasladada a la carpeta privada `%USERPROFILE%/.andesstay/private`. Las configuraciones originales con credenciales se conservaron allí para recuperación, con acceso limitado a la cuenta local y SYSTEM. El respaldo SQL se conserva en su ubicación original y no se incluye en Git.

El frontend recibe únicamente identificadores públicos y el scope de Entra. Nunca recibe contraseñas de base de datos o de brokers.

## Migraciones

Spring Boot integra Flyway y el módulo específico para MySQL. Hibernate valida las entidades contra el esquema resultante. Se utiliza un único mecanismo para evolucionar la estructura, conforme a la [documentación de inicialización de Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

- V1 crea las tres tablas del esquema anterior: unidades, reservas y auditoría.
- V2 incorpora el propietario y los índices de propietario/disponibilidad. Inspecciona la estructura y respeta columnas o índices creados en los pasos anteriores.
- V3 incorpora la marca de asignación al huésped y las tablas outbox/inbox, junto con los índices de publicación y auditoría.
- V4 amplía descripción de auditoría a 1024 caracteres y usuario a 512. El nombre permitido de 255 caracteres más el texto del evento ya no excede la antigua columna.

Las unidades de demostración se insertan con una migración repetible en `db/local`, incluida exclusivamente en el perfil local. No se cargan en otros perfiles.

## Procedimiento con datos existentes

1. Conserva una copia de la base anterior y comprueba qué esquema estás utilizando.
2. Ejecuta las migraciones sobre una copia aislada. El script de pruebas automatiza esto con el SQL incluido.
3. Para un esquema anterior que todavía no tenga historial Flyway, configura explícitamente `DB_BASELINE_EXISTING=true`. Reconoce la estructura existente como V1 y aplica V2 en adelante.
4. Después de migrar, vuelve a desactivar esa opción. Conserva el historial Flyway y evita editar migraciones ya ejecutadas.
5. Revisa conflictos de fechas, referencias huérfanas y reservas sin propietario antes de poner el historial en operación.

No se importa el respaldo sobre la base local del arranque normal. No se ejecuta `Flyway.clean`. El SQL original no se modifica.

## Verificación realizada

MySQL 8.4 ejecutó las cuatro migraciones desde un esquema vacío y una segunda ejecución no aplicó cambios. La copia del respaldo recibió V2–V4 después de reconocer V1. Las huellas SHA-256 de todos los campos originales de las tres tablas permanecieron iguales.

Las 31 pruebas de disponibilidad pasaron utilizando MySQL, incluyendo confirmaciones concurrentes. Se ejecutó además una prueba con las entidades en modo `validate` sobre el esquema migrado.

El informe detectó tres pares de solapamientos: 4/5, 4/6 y 5/6, todos en la unidad 2. Esos datos anteriores no se corrigieron automáticamente. La validación impide crear nuevas confirmaciones superpuestas, pero la decisión sobre la reserva válida pertenece a recepción.
