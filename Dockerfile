# The starter 2.1.0 is published on Maven Central
# (io.github.learnerview:simplydone4j-spring-boot-starter:2.1.0), so the COPY below
# and the vendor/ directory are now redundant and can be deleted; the build resolves
# 2.1.0 from Central without them. The vendored artifact is still seeded here for
# offline/hermetic builds. See README.md "Limitations & Known Issues" and "Roadmap".
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
