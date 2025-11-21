FROM eclipse-temurin:21-jre

EXPOSE 8080

WORKDIR /app

COPY ./target/IlpTutorial1-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]