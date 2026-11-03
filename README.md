# Distributed HTTP Aggregator

The active implementation target is Java 21 with Apache Maven. The existing Python implementation
is retained only as historical prototype evidence and is not part of the Maven build.

## Prerequisites

- JDK 21 (`java -version` and `javac -version`)
- PowerShell on Windows, or a POSIX shell with `curl` and `unzip`
- Network access on the first Maven Wrapper invocation to download Maven and pinned dependencies

## Foundation build

```powershell
.\mvnw.cmd clean test
```

The wrapper provisions Maven 3.9.9 under `.mvn/wrapper/dists/`. Java sources live under
`src/main/java`; JUnit 5 tests live under `src/test/java`.
