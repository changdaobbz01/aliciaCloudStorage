FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY CloudStorageApi/pom.xml CloudStorageApi/pom.xml
COPY CloudStorageApi/src CloudStorageApi/src

RUN chmod +x mvnw && ./mvnw -pl CloudStorageApi -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/CloudStorageApi/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
