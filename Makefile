all:

build:
	mvn clean package

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
		--reset-offsets --group ${KAFKA_CONSUMER_GROUP} --to-earliest --all-topics --execute

kafka-describe-offsets:
	${KAFKA_HOME}/bin/kafka-consumer-groups.sh --bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS} \
		--describe --group ${KAFKA_CONSUMER_GROUP}

kafka-offsets-reset-seguimiento:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.seguimiento-v1 kafka-reset-offset-to-earliest

kafka-offsets-describe-seguimiento:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.seguimiento-v1 kafka-describe-offsets

kafka-offsets-reset-expediente:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.expediente-v1 kafka-reset-offset-to-earliest

kafka-offsets-describe-expediente:
	make KAFKA_CONSUMER_GROUP=congreso.leyes.expediente-v1 kafka-describe-offsets

importacion-proyecto:
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorProyecto"

importacion-seguimiento: kafka-offsets-reset-seguimiento
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorSeguimiento"

importacion-expediente: kafka-offsets-reset-expediente
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.importador.ImportadorExpediente"


exportacion-hugo:
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorHugo"

exportacion-csv:
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorCsv"

include .env
exportacion-twitter:
	mvn compile exec:java -Dexec.mainClass="congreso.leyes.exportador.ExportadorTwitter"

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