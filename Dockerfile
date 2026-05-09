# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

# Install dependencies and compile the PKCS#11 Proxy client so that the pcks config could use a local .so file
RUN apt-get update && apt-get install -y git cmake make gcc libssl-dev libseccomp-dev && \
    git clone https://github.com/SUNET/pkcs11-proxy.git /tmp/pkcs11-proxy && \
    cd /tmp/pkcs11-proxy && mkdir build && cd build && \
    cmake -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_C_FLAGS="-Wno-incompatible-pointer-types -Wno-deprecated-declarations" .. && make && make install && \
    rm -rf /tmp/pkcs11-proxy && \
    apt-get remove -y git cmake make gcc libseccomp-dev && apt-get autoremove -y

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Expose the default application port (change if your app uses a different port)
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]