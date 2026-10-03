# The starter 2.1.0 is built locally and not yet on Maven Central, so its jar/pom are
# seeded into the image's local repository before the build. Once 2.1.0 is published,
# the COPY below and the vendor/ directory can both be deleted.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY vendor/io/github/learnerview /root/.m2/repository/io/github/learnerview
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/simplydone4j-demo-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
