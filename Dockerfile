FROM openjdk:17

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew assembleDebug

CMD ["echo", "Android build completed successfully"]
