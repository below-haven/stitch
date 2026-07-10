# Build

## Requirements

- macOS, Linux, or Windows with a shell capable of running the Gradle wrapper.
- Java Development Kit (JDK) installed through `asdf`.
  - This repository pins `openjdk-25.0.2` in `.tool-versions`.
  - `.envrc` uses `direnv` to set `JAVA_HOME` and add the JDK to `PATH`.
  - Gradle runs the build; the project itself compiles Java 8 bytecode.
- Network access for the first build so Gradle can download its wrapper distribution and dependencies.

- `direnv` enabled for the repository.

## Recommended setup

From the repository root:

```sh
asdf install
direnv allow
```

After that, `direnv` loads the pinned JDK automatically when you enter the repository.

## Create the jar

From the repository root:

```sh
./gradlew shadowJar
```

The runnable jar is written to:

```text
build/libs/stitch-0.7-SNAPSHOT-all.jar
```

Run it with:

```sh
java -jar build/libs/stitch-0.7-SNAPSHOT-all.jar
```

## Other useful commands

```sh
# Build the regular jar, sources jar, and fat jar
./gradlew assemble

# Run unit tests
./gradlew test

# Run checkstyle
./gradlew checkstyleMain checkstyleTest
```
