FROM maven:3.8-openjdk-8

WORKDIR /app

# Instalar dependencias necesarias para la descarga de archivos
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    unzip \
    curl \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Copiar el archivo pom.xml primero para aprovechar la caché de Docker
COPY pom.xml .

# Descargar todas las dependencias. 
# Separamos este paso del build para aprovechar la caché
RUN mvn dependency:go-offline -B

# Copiar el código fuente
COPY src ./src
COPY download-dataset.sh .
RUN chmod +x download-dataset.sh

# Asegurar que los directorios de recursos existan
RUN mkdir -p resources/data resources/results resources/logs

# Compilar la aplicación
RUN mvn package -DskipTests

# Usar el script como punto de entrada
ENTRYPOINT ["./download-dataset.sh"]

# Comando por defecto cuando se inicia el contenedor
CMD ["java", "-jar", "target/InMemory-Spatio-Textual-Index-1.0-SNAPSHOT-jar-with-dependencies.jar"]
