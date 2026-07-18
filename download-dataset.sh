#!/bin/bash

# Definir el archivo y directorios
DATASET_URL="https://drive.google.com/uc?export=download&id=1me1-s-4F8_odf378kIPaTg8uEZ-52RrU"
DATASET_ZIP="/tmp/parks_dataset.zip"
RESOURCES_DIR="/app/resources"
DATA_DIR="${RESOURCES_DIR}/data"
PARKS_DIR="${DATA_DIR}/Parks"

# Crear directorios si no existen
mkdir -p ${DATA_DIR}

# Verificar si el dataset ya existe
if [ -d "${PARKS_DIR}" ] && [ "$(ls -A ${PARKS_DIR})" ]; then
    echo "Dataset Parks ya existe, omitiendo descarga..."
else
    echo "Descargando dataset Parks..."
    
    # Instalar herramienta gdown para descargar desde Google Drive
    pip install --no-cache-dir gdown
    
    # Descargar el archivo
    gdown ${DATASET_URL} -O ${DATASET_ZIP}
    
    # Verificar si la descarga fue exitosa
    if [ $? -eq 0 ] && [ -f ${DATASET_ZIP} ]; then
        # Crear directorio si no existe
        mkdir -p ${PARKS_DIR}
        
        # Descomprimir el archivo
        echo "Descomprimiendo dataset..."
        unzip -q ${DATASET_ZIP} -d ${DATA_DIR}
        
        # Limpiar
        rm ${DATASET_ZIP}
        
        echo "Dataset Parks descargado y extraído correctamente."
    else
        echo "Error al descargar el dataset Parks."
        exit 1
    fi
fi

# Continuar con la ejecución del programa Java
exec "$@"
