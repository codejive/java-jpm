# jpm - Java Package Manager

A simple command line tool to manage Maven dependencies for Java projects that are not using build systems like Maven
or Gradle.

It takes inspiration from Node's npm but is more focused on managing dependencies and
is _not_ a build tool. Keep using Maven and Gradle for that. This tool is ideal for those who want to compile and
run Java code directly without making their lives difficult the moment they want to start using dependencies.

[![asciicast](https://asciinema.org/a/ZqmYDG93jSJxQH8zaFRe7ilG0.svg)](https://asciinema.org/a/ZqmYDG93jSJxQH8zaFRe7ilG0)

## Example

**TL;DR**

```shell
$ jpm install com.github.lalyos:jfiglet:0.0.9
Artifacts new: 1, updated: 0, deleted: 0
$ java -cp deps/* HelloWorld.java
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
├── app.yml
├── deps
│   └── jfiglet-0.0.9.jar -> /home/user/.m2/repository/com/github/lalyos/jfiglet/0.0.9/jfiglet-0.0.9.jar
└── HelloWorld.java
```

As you can see `jpm` has created a `deps` directory and copied the JFigletFont library there
(_although in fact it didn't actually copy the library itself, but instead it created a symbolic link to save space_).

We can now simply run the program like this (using Java 11 or newer):

```shell
$ java -cp "deps/*" HelloWorld.java
  _   _      _ _         __        __         _     _ _
 | | | | ___| | | ___    \ \      / /__  _ __| | __| | |
 | |_| |/ _ \ | |/ _ \    \ \ /\ / / _ \| '__| |/ _` | |
 |  _  |  __/ | | (_) |    \ V  V / (_) | |  | | (_| |_|
 |_| |_|\___|_|_|\___( )    \_/\_/ \___/|_|  |_|\__,_(_)
```

But if we look again at the above output of `tree`, we also see an `app.yml` file.
This file is used by `jpm` to keep track of the dependencies of your project. If you want to share your project
with someone else, you can simply share the `app.yml` file along with the code, and they can run `jpm install`
to get the required dependencies to run the code.

_NB: We could have used `jpm copy` instead of `jpm install` to copy the dependencies but that would not have created
the `app.yml` file._

## Actions

The `app.yml` file doesn't just track dependencies - it can also define custom actions that can be executed with the `jpm do` command or through convenient alias commands.

### Defining Actions

Actions are defined in the `actions` section of your `app.yml` file:

```yaml
dependencies:
  com.github.lalyos:jfiglet:0.0.9

actions:
  setup: "echo Do some actual work here..."
  build: "javac -cp {{deps}} *.java"
  run: "java -cp {{deps}} HelloWorld"
  test: "java -cp {{deps}} TestRunner"
  it: "java -cp {{deps}} IntegrationTestRunner"
  clean: "rm -f *.class"
```

### Executing Actions

You can execute actions using the `jpm do` command:

```shell
$ jpm do --list                 # Lists all available actions
$ jpm do build                  # Runs the build action
$ jpm do run --arg foo -a bar   # Passes "foo" and "bar" to the run action
$ jpm do build -a --verbose run -a fubar   # Passes "--verbose" to build and "fubar" to run
```

Or use the convenient alias commands that exist especially for "clean", "build", "test" and "run":

```shell
$ jpm build        # Executes the 'build' action
$ jpm run          # Executes the 'run' action
$ jpm test         # Executes the 'test' action
$ jpm clean        # Executes the 'clean' action
```

Alias commands can accept additional arguments that will be passed through to the underlying action:

```shell
$ jpm run --verbose debug    # Passes '--verbose debug' to the run action
```

### Variable Substitution

Actions support several variable substitution features for cross-platform compatibility:

- **`{{deps}}`** - Replaced with the full classpath of all dependencies
- **`{/}`** - Replaced with the file separator (`\` on Windows, `/` on Linux/Mac)
- **`{:}`** - Replaced with the path separator (`;` on Windows, `:` on Linux/Mac)
- **`{~}`** - Replaced with the user's home directory (The actual path on Windows, `~` on Linux/Mac)
- **`{./path/to/file}`** - Converts relative paths to platform-specific format
- **`{./libs:./ext:~/usrlibs}`** - Converts entire class paths to platform-specific format
- **`{;}`** - For use with multi-command actions (`&` on Windows, `;` on Linux/Mac). Really not _that_ useful, you can use `&&` instead which works on all platforms

Example with cross-platform compatibility:

```yaml
actions:
  build: "javac -cp {{deps}} -d {./target/classes} src{/}*.java"
  run: "java -cp {{deps}}{:}{./target/classes} Main"
  test: "java -cp {{deps}}{:}{./target/classes} org.junit.runner.JUnitCore TestSuite"
```

NB: The `{{deps}}` variable substitution is only performed when needed - if your action doesn't contain `{{deps}}`, jpm won't resolve the classpath, making execution faster for simple actions that don't require dependencies.

NB2: These actions are just a very simple convenience feature. For a much more full-featured cross-platform action runner I recommend taking a look at:

 - [Just](https://github.com/casey/just) - Just a command runner

## Installation

For now the simplest way to install `jpm` is to use [JBang](https://www.jbang.dev/download/):

```shell
jbang app install jpm@codejive
```

But you can also simply download and unzip the [release package](https://github.com/codejive/java-jpm/releases/latest) and run the `bin/jpm` script.

## Usage

See:

```
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
                available in the app.yml file. It then behaves just like 'copy
                --sync' and copies all artifacts in that list and all their
                dependencies to the target directory while at the same time
                removing any artifacts that are no longer needed (ie the ones
                that are not mentioned in the app.yml file). If no artifacts
                are passed the app.yml file will be left untouched and only the
                existing dependencies in the file will be copied.

              Example:
                jpm install org.apache.httpcomponents:httpclient:4.5.14

  path, p     Resolves one or more artifacts and prints the full classpath to
                standard output. If no artifacts are passed the classpath for
                the dependencies defined in the app.yml file will be printed
                instead.

              Example:
                jpm path org.apache.httpcomponents:httpclient:4.5.14

  exec        Executes a shell command that can use special tokens to deal with
                OS-specific quirks like paths. This means that commands can be
                written in a somewhat platform independent way and will work on
                Windows, Linux and MacOS.

              Supported tokens and what they expand to:
                {{deps}}  : the classpath of all dependencies defined in the app.yml file
                {/} : the OS' file path separator
                {:} : the OS' class path separator
                {~} : the user's home directory using the OS' class path format
                {;} : the OS' command separator
                {./file/path} : a path using the OS' path format (must start with './'!)
                {./lib:./ext} : a class path using the OS' class path format (must start with './'!)
                @[ ... ] : writes contents to a file and inserts @<path-to-file> instead

              In actuality the command is pretty smart and will try to do the
                right thing, as long as {{deps}} is the only token you use. In
                the examples below the first line shows how to do it the hard
                way, by specifying everything manually, while the second line
                shows how much easier it is when you can rely on the built-in
                smart feature. Is the smart feature bothering you? Just use any
                of the other tokens besides {{deps}} and it will be turned off.
                By default args files will only be considered for Java commands
                that are know to support them (java, javac, javadoc, etc), but
                you can indicate that your command supports it as well by
                adding a single @ as the first character of the command.

              Example:
                jpm exec javac -cp @[{{deps}}] -d {./out/classes} --source-path {./src/main/java} App.java
                jpm exec javac -cp {{deps}} -d out/classes --source-path src/main/java App.java
                jpm exec @kotlinc -cp {{deps}} -d out/classes src/main/kotlin/App.kt

  do          Executes an action command defined in the app.yml file. The
                command is executed using the same rules as the exec command,
                so it can use all the same tokens and features. You can also
                pass additional arguments to the action using -a or --arg
                followed by the argument value. You can chain multiple actions
                and their arguments in a single command line.
              Example:
                jpm do build
                jpm do test --arg verbose
                jpm do build -a --fresh test -a verbose

  clean       Executes the 'clean' action as defined in the app.yml file.
  build       Executes the 'build' action as defined in the app.yml file.
  run         Executes the 'run' action as defined in the app.yml file.
  test        Executes the 'test' action as defined in the app.yml file.
```

## Development

To build the project simply run:

```shell
./mvnw spotless:apply clean install
```

Of course, once you've got `jpm` installed you can do:

```shell
jpm do clean build
jpm test
jpm run
```
