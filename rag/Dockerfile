FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY CloudStorageApi/pom.xml CloudStorageApi/pom.xml
COPY rag/pom.xml rag/pom.xml
COPY rag/src rag/src

RUN chmod +x mvnw && ./mvnw -pl rag -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/rag/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
