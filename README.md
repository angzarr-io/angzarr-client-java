> **⚠️ Notice:** This repository was recently extracted from the [angzarr monorepo](https://github.com/angzarr-io/angzarr) and has not yet been validated as a standalone project. Expect rough edges. See the [Angzarr documentation](https://angzarr.io/) for more information.

# angzarr-client-java

Java client library for Angzarr event sourcing framework.

## Installation

```
<dependency>
  <groupId>io.angzarr</groupId>
  <artifactId>angzarr-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Usage

```
import io.angzarr.Client;

Client client = Client.newBuilder()
    .connect("localhost:50051")
    .build();
```

## License

BSD-3-Clause
