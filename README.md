# Relac It

Relac It is a Vaadin + Spring Boot web application (generated from start.vaadin.com) that demonstrates a small UI and server-side services for interacting with Modbus devices. Server code lives under `src/main/java/com/example/application` and frontend assets are in `src/main/frontend`.

## Key info

- Artifact: `relac-it`
- Java: 21
- Vaadin: 25

## Prerequisites

- Java 21 (JDK)
- Maven 3.6+ (or newer)

## Build & run

Development (recommended):

```bash
mvn spring-boot:run
```

Build and run the fat jar:

```bash
mvn -U clean package
java -jar target/relac-it-1.0-SNAPSHOT.jar
```

Run unit tests:

```bash
mvn test
```

Integration tests (Vaadin TestBench) — runs with profile `it`:

```bash
mvn verify -Pit
```

Provide WebDriver params when running integration tests locally:

```bash
mvn verify -Pit -Dwebdriver.chrome.driver=/path/to/chromedriver -Dcom.vaadin.testbench.Parameters.runLocally=chrome
```

## Project structure

- `src/main/java/com/example/application` — server-side Java sources (notable: `Application`, `MainView`, `ModbusConnectionService`, `ModbusTcpClient`, `ModbusView`)
- `src/main/frontend` — frontend sources and generated Vaadin client code
- `pom.xml` — Maven build and plugin configuration

## Development notes

- The Vaadin Maven plugin builds frontend resources during Maven build phases.
- Spring Boot Devtools and Vaadin dev mode are included to improve developer feedback loops.

## Contributing

Open issues and PRs are welcome. For local development, follow the build/run steps above.

## License

See LICENSE.md at the repository root.

---
If you want, I can add IDE run instructions or a short list of main classes and endpoints.
