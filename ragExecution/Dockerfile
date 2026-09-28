FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY CloudStorageApi/pom.xml CloudStorageApi/pom.xml
COPY rag/pom.xml rag/pom.xml
COPY ragExecution/pom.xml ragExecution/pom.xml
COPY ragExecution/src ragExecution/src

RUN chmod +x mvnw && ./mvnw -pl ragExecution -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/ragExecution/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
