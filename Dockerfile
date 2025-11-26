FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x gradlew

CMD ["echo", "Android project container initialized successfully"]
