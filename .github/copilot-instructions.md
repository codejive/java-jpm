# jpm - Java Package Manager

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

jpm is a Java 11+ Maven-based command line tool for managing Maven dependencies in non-Maven/Gradle Java projects. It creates symbolic links to dependencies and manages app.yml configuration files.

## Working Effectively

### Bootstrap and Build the Repository
- **CRITICAL**: Make mvnw executable first: `chmod +x mvnw`
- Build and format code: `./mvnw spotless:apply clean install`
  - **NEVER CANCEL**: Takes ~35 seconds. Set timeout to 120+ seconds.
- Quick rebuild after changes: `./mvnw clean verify`
  - **NEVER CANCEL**: Takes ~5 seconds. Set timeout to 60+ seconds.
- CI build with release assembly: `./mvnw -B verify jreleaser:assemble -Prelease`
  - **NEVER CANCEL**: Takes ~32 seconds. Set timeout to 120+ seconds.

### Code Formatting and Validation
- **ALWAYS** run formatting before committing: `./mvnw spotless:apply`
  - Takes ~2 seconds. Required for CI to pass.
- Check formatting only: `./mvnw spotless:check`
- **ALWAYS** run `./mvnw spotless:apply` before making any commits or the CI (.github/workflows/ci.yml) will fail.

### Testing and Running jpm
- **No unit tests exist** - the Maven test phase skips with "No tests to run"
- Run jpm using the binary distribution: `./target/binary/bin/jpm [commands]`
- Run jpm using the CLI jar: `java -jar target/jpm-0.4.1-cli.jar [commands]`

## Validation

### Manual End-to-End Testing
Always manually validate jpm functionality after making changes:

1. **Build the project**: `./mvnw spotless:apply clean install`
2. **Test help**: `./target/binary/bin/jpm --help`
3. **Test version**: `./target/binary/bin/jpm --version`
4. **Create test directory**: `mkdir -p /tmp/jpm-test && cd /tmp/jpm-test`
5. **Test copy functionality**: `[repo-path]/target/binary/bin/jpm copy com.github.lalyos:jfiglet:0.0.9`
   - Verify: Creates `deps/` directory with symlink to jar
6. **Test install functionality**: `[repo-path]/target/binary/bin/jpm install com.github.lalyos:jfiglet:0.0.9`
   - Verify: Creates `app.yml` file with dependency entry
7. **Test path command**: `[repo-path]/target/binary/bin/jpm path`
   - Verify: Outputs classpath to dependency jars
8. **Test complete Java workflow**:
   ```java
   // Create HelloWorld.java
   import com.github.lalyos.jfiglet.FigletFont;
   public class HelloWorld {
       public static void main(String[] args) throws Exception {
           System.out.println(FigletFont.convertOneLine("Hello, World!"));
       }
   }
   ```
   - Run: `java -cp "deps/*" HelloWorld.java`
   - Verify: ASCII art output is displayed correctly

### Search Function Limitation
- The `jpm search` command may fail with YAML parsing errors in some network environments
- This is a known limitation, focus testing on copy, install, and path commands

## Build Artifacts and Outputs

### Key Build Products
- `target/jpm-0.4.1.jar` - Main jar (not standalone)
- `target/jpm-0.4.1-cli.jar` - Standalone executable jar (~5.8MB)
- `target/binary/bin/jpm` - Executable shell script
- `target/binary/lib/` - All dependency jars for distribution

### Distribution Files
- **For users**: Use `target/jpm-0.4.1-cli.jar` or `target/binary/` directory
- **For development**: Use `./target/binary/bin/jpm` for testing

## Repository Structure

### Key Directories
- `src/main/java/org/codejive/jpm/` - Main source code
  - `Main.java` - CLI entry point with PicoCLI commands (Copy, Search, Install, PrintPath)
  - `Jpm.java` - Core business logic for artifact resolution and management
  - `util/` - Utility classes (FileUtils, ResolverUtils, SearchUtils, etc.)
  - `json/` - AppInfo class for app.yml file handling
- `.github/workflows/ci.yml` - CI pipeline (build + jreleaser assembly)
- `jreleaser.yml` - Release configuration
- `pom.xml` - Maven build configuration

### Important Files
- `pom.xml` - Maven configuration with Spotless formatting, Shade plugin, Appassembler
- `app.yml` - jpm's actual runtime dependencies and actions (NOT an example file). Dependencies should be kept up-to-date with the (non-test) dependencies in pom.xml
- `RELEASE.md` - Release process documentation
- `.gitignore` - Excludes target/, deps/, IDE files
- `src/main/java/org/codejive/jpm/Main.java` - The main entry point of the code, but also contains JBang directives like //DEPS and //SOURCES. The //DEPS lines should be kept up-to-date with the (non-test) dependencies in pom.xml. The //SOURCES lines should contain a list of all source files (except for Main.java itself) needed to compile.

## Common Commands Reference

### Repository Root Files
```bash
ls -la
# Key files: README.md, pom.xml, mvnw, jreleaser.yml, app.yml, src/
```

### Maven Build Profiles
- Default: Builds jar and CLI jar with binary distribution
- `-Prelease`: Adds source and javadoc jars for releases
- Spotless enforced in verify phase

### Java Version Requirements
- **Java 11+** required for compilation and runtime
- Main class: `org.codejive.jpm.Main`
- Uses PicoCLI for command parsing, Maven Resolver for dependency resolution

## CI and Release Information
- CI runs on Ubuntu with Java 21 (Temurin distribution)
- Build command: `mvn -B verify jreleaser:assemble -Prelease`
- Spotless formatting check is enforced - builds fail if formatting is incorrect
- Release uses JReleaser for GitHub releases and Maven Central deployment

## Development Tips
- Always run `./mvnw spotless:apply` before committing changes
- The application has no unit tests - rely on manual validation scenarios
- Search functionality may not work in all network environments, focus on core copy/install/path features
- Build times are fast (~5-35 seconds) but always set generous timeouts to avoid premature cancellation
- Use `chmod +x mvnw` if you get permission denied errors on fresh clones
