FROM openjdk:8u201-jdk-alpine3.9

RUN mkdir /app/
COPY target/scala-2.12/s3-static-web-assembly-0.0.1-SNAPSHOT.jar /app/

EXPOSE 8080

CMD ["java", "-jar", "/app/s3-static-web-assembly-0.0.1-SNAPSHOT.jar"]
