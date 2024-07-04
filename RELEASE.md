
# Release instructions

Use the following to create a release and deploy it:

## Step 1 - Set release versions

```bash
mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.incrementalVersion} versions:commit
```

## Step 2 - Building, staging and assembling

```bash
./mvnw clean deploy jreleaser:assemble -Prelease
```

## Step 3 - Do a dry-run of the full release

```bash
./mvnw jreleaser:full-release -Djreleaser.dry.run
```

## Step 4 - Commit and push the version changes

```bash
git commit -am "Prepare for release"
git push
```

## Step 5 - Perform the full release

```bash
./mvnw jreleaser:full-release
```

## Step 6 - Bump versions for next SNAPSHOT, commit and push

```bash
mvn build-helper:parse-version versions:set -DnewVersion=\${parsedVersion.majorVersion}.\${parsedVersion.minorVersion}.\${parsedVersion.nextIncrementalVersion}-SNAPSHOT versions:commit
git commit -am "Prepare for next development iteration"
git push
```
