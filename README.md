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

Apache 2.0
