# Congreso Perú

Este proyecto intenta brindar un mejor acceso a la información al que actualmente ofrece el sitio web del Congreso peruano.

## Objectivos

- Rapido acceso al estado actual, seguimiento y documentación relacionada a los proyectos de ley.
- Exponer datos que permitan el análisis de rendimiento del congreso.

## Detalle del código fuente

### Documentación

- `README.md`: 
- `LICENSE`:

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
- `data/`: Archivos de datos utilizados por la plantilla.
