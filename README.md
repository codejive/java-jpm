# jpm - Java Package Manager

A simple command line tool to manage Maven dependencies for Java projects that are not using build systems like Maven
or Gradle.

It takes inspiration from Node's npm but is more focused on managing dependencies and
is _not_ a build tool. Keep using Maven and Gradle for that. This tool is ideal for those who want to compile and
run Java code directly without making their lives difficult the moment they want to start using dependencies.

## Installation

### JBang

For now the simplest way to install `jpm` is to use [JBang](jbang.dev):

```shell
jbang app install jpm@codejive
```

## Usage

See:

```shell
jpm --help
```

## Development

To build the project simply run:

```shell
./mvnw spotless:apply clean install
```
