FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew assembleDebug

CMD ["echo", "Android build completed successfully"]
