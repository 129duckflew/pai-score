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

## Railway Results

Environment URLs:

- Production backend: `https://backend-production-c630.up.railway.app`
- Test backend: `https://backend-test-f430.up.railway.app`
- Test frontend: `https://frontend-test-fd3b.up.railway.app`

Measured backend startup times from Railway deploy logs:

| Environment | Build | Deployment | Startup log | Ready time |
| --- | --- | --- | --- | --- |
| `production` | regular JVM jar from `main` | `ef540330-720d-4908-807b-8c273ea63cfc` | `Started HelloApplication in 2.597 seconds` | 2597 ms |
| `test` | JVM AOT jar from `spike/backend-startup-aot` | `9353f460-10db-42c8-b86d-8cd5cf861d0f` | `Application ready in 2869 ms` | 2869 ms |
| `test` restart | JVM AOT jar from `spike/backend-startup-aot` | `9353f460-10db-42c8-b86d-8cd5cf861d0f` | `Application ready in 2647 ms` | 2647 ms |

Functional checks on `test`:

- `GET /api/rooms/active` through frontend proxy returned 200.
- Socket.IO over the test frontend completed auth, room creation, joining, and `GET_ROOM_STATE`.
- After backend restart, the frontend service had to be restarted so nginx refreshed the backend upstream, matching the project's Railway deployment rule.

## Conclusion

JVM AOT did not produce a meaningful Railway startup improvement for this backend. The best observed AOT restart was 2647 ms, while the regular production JVM jar started in 2597 ms. The AOT path did reduce part of Spring context initialization (`Root WebApplicationContext` was roughly 286-364 ms in test vs 625 ms in the measured production run), but the total startup time remained dominated by Hibernate/JPA, PostgreSQL connection setup, and Socket.IO startup.

Recommendation: do not merge JVM AOT as a production optimization yet. The next useful spike should target either CDS/AppCDS on the JVM or a GraalVM native-image prototype, with a specific focus on Hibernate/JPA and `netty-socketio` compatibility. For serverless-style Railway sleep/wake behavior, measure full cold first-response latency separately from Spring's internal startup time.
