
Use the following to test an incremental release + deploy:

```
mvn -B release:clean release:prepare -DdryRun=true
```

If everything seems successful the following will actual perform a deploy:

```
mvn -B release:clean release:prepare release:perform
```

_WARNING: Make sure **JAVA_HOME** is set or the release will fail!_

If something went wrong you can roll back the release using:

```
mvn release:rollback
```

Use this command to set a specific version:

```
mvn -B release:update-versions -DdevelopmentVersion=1.2.3-SNAPSHOT
```

A manual deploy can be done like this:

```
mvn clean deploy -P release
```
