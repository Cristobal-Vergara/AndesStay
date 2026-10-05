# Paso 7: un frontend natural y moderno

Fecha de revisión: 4 de octubre de 2026.

El diseño adopta la preferencia elegida: verde bosque, crema y terracota. Se actualizaron acceso, inicio, reservas, catálogo, reportes y auditoría dentro del proyecto React existente.

## Qué cambió y por qué

| Cambio | Motivo |
| --- | --- |
| Verde bosque para navegación y acciones principales | Da identidad a AndesStay y permite reconocer qué acción tiene prioridad. |
| Fondos claros y superficies crema | Separan los bloques de información sin recargar la pantalla. |
| Terracota en detalles y estados | Añade calidez y facilita distinguir mensajes y categorías. |
| Ilustración SVG de montañas, bosque y cabaña | Relaciona el acceso con los alojamientos y se mantiene nítida al cambiar de tamaño. |
| Navegación lateral en escritorio y menú desplegable en móvil | Mantiene las secciones a mano y deja espacio para el contenido. |
| Catálogo en tarjetas | Hace más fácil comparar nombre, capacidad, tarifa y habilitación. La habilitación no garantiza disponibilidad para unas fechas. |
| Formulario de unidades al crear o editar | El catálogo se puede recorrer sin un formulario grande delante. |
| Reservas y auditoría en tablas | Permiten comparar fechas y estados, y conservar las acciones de gestión junto al registro. |
| Etiquetas de estado compartidas | El mismo estado usa siempre la misma apariencia y sigue teniendo un nombre legible. |
| Cuatro indicadores principales y un resumen de estados | Destacan las métricas más útiles y conservan la información de los indicadores anteriores. |
| Gráfico por hora en reportes | Hace visible la distribución histórica de solicitudes, con valores y descripciones accesibles. |
| Mensajes de carga, error y listas vacías | Explican lo que sucede cuando no hay datos o falla una consulta. |

Al editar una unidad o reserva, o al asignar una cuenta de huésped, la pantalla lleva el foco al formulario correspondiente. El acceso muestra un estado de conexión para impedir clics repetidos y permite reintentar si Microsoft devuelve un error.

## Organización del código

- `src/index.css`: paleta, espaciado, tipografía, formularios, tablas y adaptación a distintos anchos.
- `Navbar`: menú según los roles de la cuenta, página activa, cuenta y cierre de sesión.
- `Brand` e `Icon`: identidad e iconos SVG.
- `PageHeader`, `StatusBadge` y `EmptyState`: elementos comunes de las pantallas.
- `KpiCards` y `Pagination`: presentación compartida de indicadores y navegación entre resultados.
- `public/andes-landscape.svg` y `public/favicon.svg`: ilustración e identificador de AndesStay.

Se usaron fuentes del sistema y SVG propios. No se añadieron librerías de iconos, gráficos ni fuentes externas. Se retiraron los recursos sin uso de la plantilla inicial de Vite.

## Funciones y permisos

Las pantallas siguen usando los mismos endpoints, autenticación con Microsoft y restricciones por rol. El huésped consulta sus reservas y solicita una estadía; recepción y administración conservan sus acciones de gestión; reportes sigue reservado a administración y auditoría a sus roles correspondientes.

Se mantuvieron las tarifas en pesos chilenos, los filtros de auditoría, la paginación y las transiciones de reserva permitidas. Los reportes no muestran tendencias inventadas: sus valores proceden de la API y el gráfico está identificado como histórico. El promedio de confirmación muestra un guion cuando no hay reservas con las que calcularlo.

## Verificación realizada

- 18 pruebas del frontend aprobadas: las 13 anteriores y 5 sobre navegación, foco del menú, Escape y fallos o clics repetidos durante el acceso.
- ESLint sin errores y compilación de producción correcta.
- Inspección visual de las seis pantallas, con revisión de panel, reservas y catálogo en móvil.
- Acceso real revisado en escritorio y móvil; sin desbordamiento horizontal en anchos móviles de 390 y 320 píxeles.
- Menú móvil comprobado al abrir, seleccionar una sección y cerrar con Escape.
- Catálogo del huésped, listas vacías y errores revisados con respuestas de prueba.
- Frontend actualizado en Docker y comprobado en `http://localhost:5173`.

Las pantallas privadas se inspeccionaron en una herramienta local de pruebas dentro de `.validation/frontend-design`, con cuenta y datos ficticios identificados en pantalla. Esa herramienta no forma parte de la compilación ni añade rutas de acceso al producto. La validación con una cuenta real de Microsoft requiere la sesión del usuario.

## Capturas

- [Acceso real en escritorio](images/frontend/login-desktop.png).
- [Acceso real en móvil](images/frontend/login-mobile.png).
- [Catálogo móvil con datos ficticios de prueba](images/frontend/catalogo-mobile-prueba.png).

El código y los recursos previos al cambio están respaldados en `backup/code-step-07-frontend`.
