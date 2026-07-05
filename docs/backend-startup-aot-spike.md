# Backend Startup AOT Spike

Branch: `spike/backend-startup-aot`

## Goal

Evaluate Spring Boot JVM AOT on Railway before attempting GraalVM native image.

## Current Spike

- Maven profile: `railway-aot`
- Build command: `mvn -Prailway-aot package -DskipTests -B`
- Docker build defaults to `railway-aot` through `ARG MAVEN_PROFILES=railway-aot`.
- Runtime enables AOT with `-Dspring.aot.enabled=true`.
- Application logs `Application ready in <ms> ms` from `ApplicationReadyEvent`.

## Important Constraint

The `railway-aot` profile runs Spring AOT with the `railway` Spring profile. This is intentional because AOT evaluates profile and conditional bean choices at build time.

An AOT jar built this way expects Railway-style PostgreSQL configuration at runtime. It is not a drop-in local H2 jar unless a separate local AOT profile is added.

## Verification

Local checks run so far:

```bash
mvn test
mvn -Prailway-aot package -DskipTests -B
```

Both commands completed successfully.

## Railway Measurement Plan

1. Deploy this branch to the backend service.
2. Confirm Railway logs contain `Starting AOT-processed HelloApplication`.
3. Compare `Application ready in <ms> ms` with the current production JVM jar baseline.
4. Trigger a cold wake and measure first successful response from `/api/rooms/active`.
5. Verify Socket.IO connect/reconnect still works through the frontend.
