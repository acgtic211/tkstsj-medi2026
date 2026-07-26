# Efficient Processing of Top-k Spatio-Textual Similarity Join with IR-trees in main memory
## Quick Start

Run <code>AlternativeMain.java</code> (the current main entry point).

```bash
mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain
```

Useful modes:
- Interactive mode with a specific config:
  <code>mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain -Dexec.args="src/main/resources/json/ir-inverted-full.json"</code>
- Autonomous mode (no menu interaction):
  <code>mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain -Dexec.args="--autonomous src/main/resources/json/ir-inverted-full.json"</code>
- List available JSON configs:
  <code>mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain -Dexec.args="--list-configs"</code>

<code>OldMain.java</code> is kept as a legacy runner.

**Menu Quick Start:**
1. Open project in IntelliJ IDEA.
2. Edit AlternativeMain configuration launcher to use 14GB of memory (VM options: `-Xmx14g`)(only necessary for dual set joins).
3. Run the program. Select menu 8 to list available JSON configs. Select one number to load the configuration.
4. In the main menu, select 3 to generate the index. Then select 5 follow by 1 to run all query experiments.
5. The results will be saved in the `resources/results/metrics` folder.

## Requirements
IMPORTANT: Due to its size, the _Parks_ dataset is not included in the repository. You can download it from [LINK](https://drive.google.com/file/d/1me1-s-4F8_odf378kIPaTg8uEZ-52RrU/view?usp=sharing). After downloading, extract the files and place them in the <code>resources/data</code> folder.

## Configuration
Configuration is JSON-driven via <code>src/main/resources/json/*.json</code> and loaded by <code>AlternativeMain</code>.
For the full configuration reference, see <code>src/main/resources/json/README.md</code>.

## Datasets
The most important available datasets are:
- _Hotels_: A _very small_ dataset that represents a hotel chain in the USA.
- _Postal Codes_: A _small_ dataset of boundaries of Postal Codes areas.
- _Sports_: A _medium_ dataset of boundaries of Sports areas.
- _Parks_: A _heavy_ dataset of boundaries of Parks areas.

## Results
The query statistics are saved in the <code>resources/results/metrics</code> folder. The results are saved in csv and txt formats. The csv format is as follows:
```csv
qryType_param1,qryType_param2,qryType_param3, ...
result1_val1,result1_val2,result1_val3, ...
result2_val1,result2_val2,result2_val3, ...
...
```
The actual query results are saved in the <code>resources/results</code> folder.
Logs can be found in the <code>resources/log</code> folder.

## Using Docker

To run this program using Docker Compose:

1. Make sure you have Docker and Docker Compose installed on your system.
2. Navigate to the root directory of the project.
3. Run the following command:

```bash
docker-compose up --build
```

This will build the Docker image, compile the application with Maven, **automatically download the Parks dataset** if it doesn't exist locally, and run the Java program. Results will be saved in the `resources/results` directory.

To run with specific JVM options or arguments:

```bash
docker-compose run --rm -e JAVA_OPTS="-Xmx14g" spatio-textual-index java -jar target/InMemory-Spatio-Textual-Index-1.0-SNAPSHOT.jar
```

## License

This project is licensed under the LGPL 2.1 License - see the [LICENSE.md](LICENSE.md) file for details.

## Based on the following works
- https://libspatialindex.org
- https://github.com/rafi-kamal/Aggregate-Spatio-Textual-Query
- http://lisi.io/spatial-keyword%20code.zip
- https://tzaeschke.github.io/phtree-site/*
