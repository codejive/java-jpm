# jpm - Java Package Manager

A simple command line tool to manage Maven dependencies for Java projects that are not using build systems like Maven
or Gradle.

It takes inspiration from Node's npm but is more focused on managing dependencies and
is _not_ a build tool. Keep using Maven and Gradle for that. This tool is ideal for those who want to compile and
run Java code directly without making their lives difficult the moment they want to start using dependencies.

## Example

**TL;DR**

```shell
$ jpm install com.github.lalyos:jfiglet:0.0.9
Artifacts new: 1, updated: 0, deleted: 0
$ javac -cp deps/* HelloWorld.java
  _   _      _ _         __        __         _     _ _
 | | | | ___| | | ___    \ \      / /__  _ __| | __| | |
 | |_| |/ _ \ | |/ _ \    \ \ /\ / / _ \| '__| |/ _` | |
 |  _  |  __/ | | (_) |    \ V  V / (_) | |  | | (_| |_|
 |_| |_|\___|_|_|\___( )    \_/\_/ \___/|_|  |_|\__,_(_)
```

**Slightly longer explanation:**

Imagine you're writing a simple Java console command, and you want to use JFigletFont for some more
impactful visuals. You've written the following code:

```java
import com.github.lalyos.jfiglet.FigletFont;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        System.out.println(FigletFont.convertOneLine("Hello, World!"));
    }
}
```

But now you get to the point that you need to add the JFigletFont library to your project. You could start
using Maven or Gradle, but that seems overkill for such a simple project. Instead, you can use `jpm`.
First you can search for the library if you can't remember the exact name and version:

```shell
$ jpm search jfiglet
com.github.dtmo.jfiglet:jfiglet:1.0.1
com.github.lalyos:jfiglet:0.0.9
```

So let's install the second library from that list:

```shell
$ jpm install com.github.lalyos:jfiglet:0.0.9
Artifacts new: 1, updated: 0, deleted: 0
```

Let's see what that did:

```shell
$ tree
.
├── app.json
├── deps
│   └── jfiglet-0.0.9.jar -> /home/user/.m2/repository/com/github/lalyos/jfiglet/0.0.9/jfiglet-0.0.9.jar
└── HelloWorld.java
```

As you can see `jpm` has created a `deps` directory and copied the JFigletFont library there
(_although in fact it didn't actually copy the library itself, but instead it created a symbolic link to save space_).

We can now simply run the program like this (using Java 11 or newer):

```shell
$ javac -cp deps/* HelloWorld.java
  _   _      _ _         __        __         _     _ _
 | | | | ___| | | ___    \ \      / /__  _ __| | __| | |
 | |_| |/ _ \ | |/ _ \    \ \ /\ / / _ \| '__| |/ _` | |
 |  _  |  __/ | | (_) |    \ V  V / (_) | |  | | (_| |_|
 |_| |_|\___|_|_|\___( )    \_/\_/ \___/|_|  |_|\__,_(_)
```

But if we look again at the above output of `tree`, we also see an `app.json` file.
This file is used by `jpm` to keep track of the dependencies of your project. If you want to share your project
with someone else, you can simply share the `app.json` file along with the code, and they can run `jpm install`
to get the required dependencies to run the code.

_NB: We could have used `jpm copy` instead of `jpm install` to copy the dependencies but that would not have created
the `app.json` file._

### JBang

For now the simplest way to install `jpm` is to use [JBang](jbang.dev):

```shell
jbang app install jpm@codejive
```

## Usage

See:

```shell
$ jpm --help
Usage: jpm [-hV] [COMMAND]
Simple command line tool for managing Maven artifacts
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  copy, c     Resolves one or more artifacts and copies them and all their
                dependencies to a target directory. By default jpm will try to
                create symbolic links to conserve space.

              Example:
                jpm copy org.apache.httpcomponents:httpclient:4.5.14

  search, s   Without arguments this command will start an interactive search
                asking the user to provide details of the artifact to look for
                and the actions to take. When provided with an argument this
                command finds and returns the names of those artifacts that
                match the given (partial) name.

              Example:
                jpm search httpclient

  install, i  This adds the given artifacts to the list of dependencies
                available in the app.json file. It then behaves just like 'copy
                --sync' and copies all artifacts in that list and all their
                dependencies to the target directory while at the same time
                removing any artifacts that are no longer needed (ie the ones
                that are not mentioned in the app.json file). If no artifacts
                are passed the app.json file will be left untouched and only
                the existing dependencies in the file will be copied.

              Example:
                jpm install org.apache.httpcomponents:httpclient:4.5.14

  path, p     Resolves one or more artifacts and prints the full classpath to
                standard output. If no artifacts are passed the classpath for
                the dependencies defined in the app.json file will be printed
                instead.

              Example:
                jpm path org.apache.httpcomponents:httpclient:4.5.14
```

## Development

To build the project simply run:

```shell
./mvnw spotless:apply clean install
```
