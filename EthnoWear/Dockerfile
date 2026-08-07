FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml ./
COPY lib/jade.jar /tmp/jade.jar
RUN mvn -B org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile=/tmp/jade.jar \
    -DgroupId=com.tilab.jade \
    -DartifactId=jade \
    -Dversion=4.6.0 \
    -Dpackaging=jar \
    -DgeneratePom=true
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
RUN groupadd --system ethnowear \
    && useradd --system --gid ethnowear --home-dir /app ethnowear \
    && mkdir -p /app/Ontology \
    && chown -R ethnowear:ethnowear /app

COPY --from=build --chown=ethnowear:ethnowear /build/target/ethnowear-*.jar /app/ethnowear.jar
COPY --chown=ethnowear:ethnowear Ontology /app/Ontology

USER ethnowear
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/ethnowear.jar"]
