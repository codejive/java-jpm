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

### Main Command

```
Usage: jpm [-hvV] [COMMAND]

Options:
  -h, --help      Show this help message and exit.
  -v, --verbose   Enable verbose output for debugging
  -V, --version   Print version information and exit.

Commands:
  search   Search for Maven artifacts in repositories.
  install  Install artifacts and add them to app.yml dependencies.
  copy     Copy artifacts to a directory without modifying app.yml.
  path     Print the classpath for the specified artifacts or app.yml dependencies.
  do       Execute an action defined in app.yml.
  exec     Execute a shell command.
```

### Commands

#### search (alias: s)

Search for Maven artifacts in repositories.

```
Usage: jpm search [-iLqv] [-a=<appInfoFile>] [-b=<backend>] [-c=<cacheDir>]
                  [-d=<directory>] [-m=<max>] [-r=<repositories>]...
                  artifactPattern

Parameters:
  artifactPattern       Partial or full artifact name to search for.

Options:
  -i, --interactive     Interactively search and select artifacts to install
  -b, --backend=<backend>
                        The search backend to use. Supported values:
                          rest_smo, rest_csc
  -m, --max=<max>       Maximum number of results to return
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JPM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JPM_REPO_<name>_USER and JPM_REPO_<name>_PASSWORD
                        environment variables.
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm search httpclient
```

#### install (alias: i)

Install artifacts and add them to app.yml dependencies.

```
Usage: jpm install [-Lqv] [-a=<appInfoFile>] [-c=<cacheDir>] [-d=<directory>]
                   [-r=<repositories>]... [artifacts...]

Parameters:
  [artifacts...]        One or more artifacts to resolve. Artifacts have the
                        format <group>:<artifact>[:<extension>
                        [:<classifier>]]:<version>

Options:
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JPM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JPM_REPO_<name>_USER and JPM_REPO_<name>_PASSWORD
                        environment variables.
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm install org.apache.httpcomponents:httpclient:4.5.14
  jpm install                  # Install dependencies from app.yml
```

#### copy (alias: c)

Copy artifacts to a directory without modifying app.yml.

```
Usage: jpm copy [-Lqsv] [-c=<cacheDir>] [-d=<directory>] [-r=<repositories>]...
                artifacts...

Parameters:
  artifacts...          One or more artifacts to resolve. Artifacts have the
                        format <group>:<artifact>[:<extension>
                        [:<classifier>]]:<version>

Options:
  -s, --sync            Makes sure the target directory will only contain
                        the mentioned artifacts and their dependencies,
                        possibly removing other files present in the
                        directory
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JPM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JPM_REPO_<name>_USER and JPM_REPO_<name>_PASSWORD
                        environment variables.
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm copy org.apache.httpcomponents:httpclient:4.5.14
```

#### path (alias: p)

Print the classpath for the specified artifacts or app.yml dependencies.

```
Usage: jpm path [-Lv] [-a=<appInfoFile>] [-c=<cacheDir>] [-d=<directory>]
                [-r=<repositories>]... [artifacts...]

Parameters:
  [artifacts...]        One or more artifacts to resolve. Artifacts have the
                        format <group>:<artifact>[:<extension>
                        [:<classifier>]]:<version>

Options:
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JPM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JPM_REPO_<name>_USER and JPM_REPO_<name>_PASSWORD
                        environment variables.
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm path org.apache.httpcomponents:httpclient:4.5.14
  jpm path             # Print classpath from app.yml dependencies
```

#### do

Execute an action defined in app.yml.

```
Usage: jpm do [-lLqv] [-a=<appInfoFile>] [-c=<cacheDir>] [-d=<directory>]
              [-r=<repositories>]... [action...] [actionsAndArguments...]

Parameters:
  [action...]           Name of the action to execute as defined in app.yml
  [actionsAndArguments...]
                        Optional additional actions and/or arguments to be
                        passed to the action(s)

Options:
  -l, --list            List all available actions
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
                        (default: value of JPM_CACHE environment variable;
                        whatever is set in Maven's settings.xml or
                        $HOME/.m2/repository
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository to use when resolving
                        artifacts. Can be preceded by a name and an equals
                        sign, e.g. -r myrepo=https://my.repo.com/maven2.
                        When needing to pass user and password you can set
                        JPM_REPO_<name>_USER and JPM_REPO_<name>_PASSWORD
                        environment variables.
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm do --list                # List all actions
  jpm do build                 # Execute the build action
  jpm do test --arg verbose    # Pass 'verbose' arg to test action
  jpm do build -a --fresh test -a verbose  # Chain actions
```

#### clean / build / run / test

Convenient aliases for executing the corresponding action from app.yml.

```
Usage: jpm clean [args...]
Usage: jpm build [args...]
Usage: jpm run [args...]
Usage: jpm test [args...]

Parameters:
  [args...]             Optional arguments to pass to the action

Options:
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository
  -v, --verbose         Enable verbose output for debugging

Example:
  jpm build
  jpm run --verbose debug
  jpm test
```

#### exec

Execute a shell command with platform-independent path handling.

```
Usage: jpm exec [-Lqv] [-a=<appInfoFile>] [-c=<cacheDir>] [-d=<directory>]
                [-r=<repositories>]... [command...]

Parameters:
  [command...]          The command to execute

Options:
  -a, --appinfo=<appInfoFile>
                        App info file to use (default './app.yml')
  -c, --cache=<cacheDir>
                        Directory where downloaded artifacts will be cached
  -d, --directory=<directory>
                        Directory to copy artifacts to
  -L, --no-links        Always copy artifacts, don't try to create symlinks
  -r, --repo=<repositories>
                        URL to additional repository
  -q, --quiet           Don't output non-essential information
  -v, --verbose         Enable verbose output for debugging

Supported tokens:
  {{deps}}              The classpath of all dependencies defined in app.yml
  {/}                   The OS' file path separator
  {:}                   The OS' class path separator
  {~}                   The user's home directory using the OS' class path format
  {;}                   The OS' command separator
  {./file/path}         A path using the OS' path format (must start with './'!)
  {./lib:./ext}         A class path using the OS' class path format
  @[ ... ]              Writes contents to a file and inserts @<path-to-file>

Example:
  jpm exec javac -cp {{deps}} -d out/classes --source-path src/main/java App.java
  jpm exec java -cp {{deps}} Main
  jpm exec @kotlinc -cp {{deps}} -d out/classes src/main/kotlin/App.kt
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
