#FROM eclipse-temurin:8-jdk
#COPY ./bge-m3 /app/models/bge-m3
#COPY target/*.jar app.jar
#EXPOSE 8091
#CMD ["java","-jar","app.jar"]

FROM amazoncorretto:8-alpine-jre
COPY target/*.jar app.jar
EXPOSE 8091
CMD ["java","-jar","app.jar"]