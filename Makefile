all: build backend-run backend-deploy web-deploy

build:
	mvn -q clean package

JAVA_HOME := ${JAVA15_HOME}
KAFKA_BOOTSTRAP_SERVERS := localhost:39092
PARTITIONS := 6

kafka-topics:
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--create --if-not-exists --topic congreso.leyes.proyecto-importado-v1 --partitions ${PARTITIONS}
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--create --if-not-exists --topic congreso.leyes.seguimiento-importado-v1 --partitions ${PARTITIONS}
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--create --if-not-exists --topic congreso.leyes.expediente-importado-v1 --partitions ${PARTITIONS}
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--create --if-not-exists --topic congreso.leyes.congresista-importado-v1 --partitions ${PARTITIONS}
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--create --if-not-exists --topic congreso.leyes.exportador-twitter-v1 --partitions ${PARTITIONS}
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--entity-type topics --entity-name congreso.leyes.proyecto-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--entity-type topics --entity-name congreso.leyes.seguimiento-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--entity-type topics --entity-name congreso.leyes.expediente-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--entity-type topics --entity-name congreso.leyes.congresista-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--entity-type topics --entity-name congreso.leyes.exportador-twitter-v1 \
		--alter --add-config cleanup.policy=compact

kafka-reset-offset-to-earliest:
	${KAFKA_HOME}/bin/kafka-consumer-groups.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--reset-offsets --group ${KAFKA_CONSUMER_GROUP} --to-earliest --topic ${KAFKA_TOPIC} --execute

kafka-reset-offset-to-latest:
	${KAFKA_HOME}/bin/kafka-consumer-groups.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--reset-offsets --group ${KAFKA_CONSUMER_GROUP} --to-latest --topic ${KAFKA_TOPIC} --execute

kafka-describe-offsets:
	${KAFKA_HOME}/bin/kafka-consumer-groups.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--describe --group ${KAFKA_CONSUMER_GROUP}

kafka-offsets-reset-seguimiento:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.seguimiento-v1 KAFKA_TOPIC=congreso.leyes.proyecto-importado-v1 kafka-reset-offset-to-earliest

kafka-offsets-describe-seguimiento:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.seguimiento-v1 kafka-describe-offsets

kafka-offsets-reset-expediente:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.expediente-v1 KAFKA_TOPIC=congreso.leyes.seguimiento-importado-v1 kafka-reset-offset-to-earliest

kafka-offsets-describe-expediente:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.expediente-v1 kafka-describe-offsets

kafka-offsets-describe-twitter:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.exportador-twitter-v1 kafka-describe-offsets

kafka-offsets-reset-twitter:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.exportador-twitter-v1 KAFKA_TOPIC=congreso.leyes.seguimiento-importado-v1 kafka-reset-offset-to-latest

importacion-proyecto:
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorProyecto"

importacion-seguimiento: kafka-offsets-reset-seguimiento
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorSeguimiento"

importacion-expediente: kafka-offsets-reset-expediente
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorExpediente"


exportacion-hugo:
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorHugo"

exportacion-csv:
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorCsv"

exportacion-twitter:
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorTwitter"

twitter-run:
	make exportacion-twitter >> logs/twitter.log 2>&1

web-run:
	hugo serve

web-build:
	hugo

web-deploy-prepare:
	rm -rf public/
	git worktree add -B gh-pages public origin/gh-pages

web-deploy: web-build
	cd public && \
		git add -A && git commit -m "publicar" && git push -f origin gh-pages

backend-run:
	mvn -q compile exec:java -Dexec.mainClass="congreso.leyes.Main"

backend-deploy:
	git checkout -B cambios
	git add content/ static/
	git commit -m 'cambios en contenido'
	git push -f origin cambios

cron:
	crontab cron.txt