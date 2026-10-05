FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY Server.java .
COPY public ./public
RUN javac Server.java
RUN useradd --system --create-home quickbite && mkdir -p /data && chown -R quickbite:quickbite /app /data
USER quickbite
ENV PORT=10000
ENV DATA_DIR=/data
EXPOSE 10000
CMD ["java", "Server"]
