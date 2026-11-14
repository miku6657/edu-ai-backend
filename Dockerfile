
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/app.jar /app/app.jar

RUN mkdir -p /data/pdfs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

