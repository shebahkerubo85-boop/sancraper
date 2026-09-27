FROM eclipse-temurin:17-jre
WORKDIR /app
COPY . /app
RUN apt-get update && apt-get install -y gradle && rm -rf /var/lib/apt/lists/*
RUN gradle :resolver:installDist --no-daemon
EXPOSE 8080
ENTRYPOINT ["/app/resolver/build/install/resolver/bin/resolver", "server", "8080"]
