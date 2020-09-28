all:

kafka-topics:
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server localhost:39092 \
		--create --if-not-exists --topic congreso.leyes.proyecto-importado-v1
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server localhost:39092 \
		--create --if-not-exists --topic congreso.leyes.seguimiento-importado-v1
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server localhost:39092 \
		--create --if-not-exists --topic congreso.leyes.expediente-importado-v1
	${KAFKA_HOME}/bin/kafka-topics.sh --bootstrap-server localhost:39092 \
		--create --if-not-exists --topic congreso.leyes.congresista-importado-v1
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server localhost:39092 \
		--entity-type topics --entity-name congreso.leyes.proyecto-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server localhost:39092 \
		--entity-type topics --entity-name congreso.leyes.seguimiento-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server localhost:39092 \
		--entity-type topics --entity-name congreso.leyes.expediente-importado-v1 \
		--alter --add-config cleanup.policy=compact
	${KAFKA_HOME}/bin/kafka-configs.sh --bootstrap-server localhost:39092 \
		--entity-type topics --entity-name congreso.leyes.congresista-importado-v1 \
		--alter --add-config cleanup.policy=compact

web-run:
	hugo serve

web-build:
	hugo

web-deploy: web-build
	cd public && \
		git add -A && git commit -m "publicar" && git push -f origin gh-pages