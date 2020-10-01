# Congreso Perú

<https://jeqo.github.io/peru-congreso>

Este proyecto es un intento de brindar un mejor acceso a la información al que actualmente ofrece el sitio web del Congreso peruano.

El actual sitio web del Congreso ofrece información fragmentada, y de difícil acceso.

La apertura de datos puede habilitar una fiscalización más fácil del lado de los ciudadanos, tener claridad sobre "quién hace qué" en el Congreso, así como la eficiencia (velocidad en el procesamiento de proyectos de ley), orientación y relevancia de las leyes.

## Objectivos

- Rápido acceso al estado actual, seguimiento y documentación de los proyectos de ley.
- Exponer datos que permitan el análisis de rendimiento del congreso.

## Alcance actual

El proyecto actualmente importa y expone datos sobre:

- Proyectos de Ley
- Directorio de Congresistas

*La frecuencia de actualización es de una actualización al día.*

Importante:

La importación de datos está altamente acoplado a como las interfaces web lucen actualmente.
Cualquier cambio significativo (ojalá que para mejor) del sitio web actual forzará a cambiar la aplicación de importación, ya que los datos no son expuestos en un formato estándar.

Idealmente, el sitio web del Congreso debería ser un repositorio abierto, y los datos expuestos en formatos más accesibles.

## Activos principales

### Proyectos de Ley

- Sitio web: <https://jeqo.github.io/peru-congreso/proyectos-ley>
- Git: <https://github.com/jeqo/peru-congreso/tree/trunk/content/proyectos-ley>

### Proyectos de Ley en formato CSV

- Sitio web: <https://jeqo.github.io/peru-congreso/proyectos-ley/2016-2021.csv>
- Git: <https://github.com/jeqo/peru-congreso/blob/trunk/static/proyectos-ley/2016-2021.csv>

### Directorio de Congresistas

- Sitio web: <https://jeqo.github.io/peru-congreso/congresistas/2016-2021>
- Git: <https://github.com/jeqo/peru-congreso/blob/trunk/content/congresistas/2016-2021.md>

## Detalle del código fuente

Mayor detalle [aquí](./RATIONALE.md)

### Documentación

- `README.md`: Descripción del proyecto.
- `LICENSE`: Licencia del proyecto de software como dedicación al dominio público.

### Archivos de Proyecto

- `Makefile`: Comandos utilizados para preparar, ejecutar, y desplegar el proyecto.

#### Importación y exportación de contenido

- `pom.xml`: Archivo de configuración del proyecto Maven (Java) de Importación y Exportación de datos.
- `docker-compose.yml`: Archivo de configuración de la infraestructura local utilizada por el proyecto. Incluye Apache Kafka y Apache Zookeeper.
- `src/`: código fuente en Java.

#### Sitio Web

- `config.toml`: Archivo de configuración de sitio web usando [Hugo](https://gohugo.io).
- `content/`: Contenido en formato [Markdown](https://commonmark.org) sobre proyectos de ley.
- `static/`: Archivos estáticos expuestos en el sitio web (e.j. CSV con estado actual de proyectos de ley)
- `themes/`: Plantilla de sitio web.
