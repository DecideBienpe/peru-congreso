# Base lógica del proyecto

## Tecnologías

### Java

Java es el lenguaje de programación utilizado para la lógica detrás de la importación y exportación de contenido.

El motivo principal es la familiaridad del lenguaje, y por el uso de librerias de Apache Kafka.

Actualmente utilizando Java 15.

### Apache Kafka

[Apache Kafka](https://kafka.apache.org) es utilizado como repositorio de eventos generados a partir de la importación de datos.

Kafka permite el almacenamiento de nuevos proyectos de ley, así como los cambios de estado, en el orden en el que suceden, facilitando la reutilización de eventos en distintos contextos.

Kafka Streams es utilizado para almacenamiento local de exportaciones y validación de datos ya procesados.

Versión actual: 2.6.0

### Hugo

[Hugo](https://gohugo.io/) es una herramienta de creación de sitios web basado en formatos de archivo de texto; principalmente Markdown.

## Patrones

### Importación de datos

La importación ha sido diseñada alrededor de las limitaciones actuales del sitio web del Congreso.

Como la información es expuesta directamente en la web, y no a través de un servicio web HTTP o formato estándar (ej. CSV), obliga a utilizar librerias que accedan y extraigan información directamente del sitio web.

La importación de datos utiliza la librería [jsoup](https://jsoup.org/) que permite acceder al sitio web y navegar la estructura HTML para extraer los datos.

Una vez importados los datos, son estructurados utilizando [Protocol Buffers](https://developers.google.com/protocol-buffers/). Este [esquema](src/main/resources/Proyecto.proto) define la información actualmente considerada por el proyecto.

La información serializada en este formato es almacenada en tópicos de Apache Kafka para ser procesados luego.

Hay 3 procesos de importación:

- [Importación del Proyecto](src/main/java/congreso/leyes/importador/ImportadorProyecto.java)
- [Importación del Seguimiento](src/main/java/congreso/leyes/importador/ImportadorSeguimiento.java)
- [Importación del Expediente](src/main/java/congreso/leyes/importador/ImportadorExpediente.java)

### Exportación de datos

Una vez que los datos son expuestos en tópicos de Apache Kafka distintos procesos de exportación son utilizados para generar activos que luego son desplegados:

- [Exportación a CSV](src/main/java/congreso/leyes/exportador/ExportadorCsv.java)
- [Exportación del Sitio Web](src/main/java/congreso/leyes/exportador/ExportadorHugo.java)
- [Exportación a Twitter](src/main/java/congreso/leyes/exportador/ExportadorTwitter.java)

## Ejecución

### Back-end

Actualmente el back-end es local, es decir, Apache Kafka se ejecuta en un ambiente local (ej. Laptop) y los importadores ejecutados para obtener datos.

### Front-end

Los exportadores generan el sitio web que es desplegado en el hosting de GitHub.