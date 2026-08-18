FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY CloudStorageApi/pom.xml CloudStorageApi/pom.xml
COPY identityApi/pom.xml identityApi/pom.xml
COPY rag/pom.xml rag/pom.xml
COPY identityApi/src identityApi/src

RUN chmod +x mvnw && ./mvnw -pl identityApi -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/identityApi/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
