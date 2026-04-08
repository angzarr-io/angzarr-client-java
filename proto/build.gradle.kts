import com.google.protobuf.gradle.*

plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    api("io.grpc:grpc-protobuf:1.60.0")
    api("io.grpc:grpc-stub:1.60.0")
    api("com.google.protobuf:protobuf-java:3.25.1")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        // Use Gradle-managed protoc instead of system (system may lack proto3 optional support)
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

// Resolve proto source directory.
// When used as a composite build (e.g. examples-java), a system property can override
// the proto path to reference a sibling submodule directly (no file copying needed).
// Default: the nested angzarr submodule (angzarr-client-java/angzarr/proto).
val protoDirOverride = System.getProperty("angzarrProtoDir")
val protoDir = if (protoDirOverride != null) {
    // Resolve relative paths against the composite build root (parent of this included build)
    if (protoDirOverride.startsWith("/")) File(protoDirOverride)
    else rootDir.parentFile.resolve(protoDirOverride)
} else {
    file("${rootDir}/angzarr/proto")
}

// Additional proto exclusions (comma-separated globs) via system property.
// e.g. -DangzarrProtoExcludes=examples/ai_sidecar.proto
val extraExcludes = System.getProperty("angzarrProtoExcludes")
    ?.split(",")
    ?.map { it.trim() }
    ?: emptyList()

sourceSets {
    main {
        proto {
            // Use proto root so imports like "angzarr/types.proto" resolve
            srcDir(protoDir)
            // Exclude health protos - not needed for Java client
            exclude("health/**")
            extraExcludes.forEach { exclude(it) }
        }
    }
}
