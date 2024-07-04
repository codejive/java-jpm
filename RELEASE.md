
# Release instructions

Use the following create a release and deploy:

## Step 1 - Building, staging and assembling

```bash
./mvnw clean deploy jreleaser:assemble -Prelease
```

## Step 2 - Do a dry-run of the full release

```bash
./mvnw jreleaser:full-release -Djreleaser.dry.run
```

## Step 3 - Perform the full release

```bash
./mvnw jreleaser:full-release
```
