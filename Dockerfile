## yacy_grid_parser dockerfile
## examples:
# docker build -t yacy_grid_parser .
# docker run -d --rm -p 8500:8500 --name yacy_grid_parser yacy_grid_parser
## Check if the service is running:
# curl http://localhost:8500/yacy/grid/mcp/info/status.json

# build app
FROM eclipse-temurin:8-jdk AS appbuilder
COPY ./ /app
WORKDIR /app
RUN ./gradlew clean shadowDistTar

# build dist
FROM eclipse-temurin:8-jre
LABEL maintainer="Michael Peter Christen <mc@yacy.net>"
ENV DEBIAN_FRONTEND noninteractive
ARG default_branch=master
COPY ./conf /app/conf/
COPY --from=appbuilder /app/build/libs/ ./app/build/libs/
WORKDIR /app
EXPOSE 8500

# for some weird reason the jar file is sometimes not named correctly
RUN if [ -e /app/build/libs/app-0.0.1-SNAPSHOT-all.jar ] ; then mv /app/build/libs/app-0.0.1-SNAPSHOT-all.jar /app/build/libs/yacy_grid_parser-0.0.1-SNAPSHOT-all.jar; fi

CMD ["java", "-Xms320M", "-Xmx4G", "-jar", "/app/build/libs/yacy_grid_parser-0.0.1-SNAPSHOT-all.jar"]
