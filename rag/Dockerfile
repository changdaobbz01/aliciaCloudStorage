FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
COPY CloudStorageApi/pom.xml CloudStorageApi/pom.xml
COPY rag/pom.xml rag/pom.xml
COPY ragExecution/pom.xml ragExecution/pom.xml

RUN chmod +x mvnw \
    && ./mvnw -pl CloudStorageApi,rag,ragExecution -Dmaven.test.skip=true -DincludeScope=runtime dependency:resolve \
    && ./mvnw -pl CloudStorageApi,rag,ragExecution -Dmaven.test.skip=true -Dspring-boot.repackage.skip=true resources:resources compiler:compile jar:jar spring-boot:repackage \
    && rm -rf CloudStorageApi/target rag/target ragExecution/target

RUN ./mvnw -pl CloudStorageApi,rag,ragExecution -Dmaven.test.skip=true surefire:test

COPY rag/src rag/src

RUN ./mvnw -pl rag -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /build/rag/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
