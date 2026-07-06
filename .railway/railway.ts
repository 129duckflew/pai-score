// @ts-ignore
import { project, service, postgres, github, volume, preserve } from "railway/iac";

const db = postgres("Postgres");
const dbVolume = volume("postgres-volume", {
  sizeMB: 5000,
  region: "us-west2",
  allowOnlineResize: true,
  alerts: {
    usage: {
      80: {},
      95: {},
      100: {},
    },
  },
});

const backend = service("backend", {
  source: github("129duckflew/pai-score", { branch: "main" }),
  build: {
    builder: "DOCKERFILE",
    dockerfilePath: "Dockerfile",
  },
  env: {
    PORT: preserve(),
    SOCKETIO_PORT: preserve(),
    SPRING_PROFILES_ACTIVE: preserve(),
    PGHOST: preserve(),
    PGPORT: preserve(),
    PGUSER: preserve(),
    PGPASSWORD: preserve(),
    PGDATABASE: preserve(),
    ADMIN_PASSWORD: preserve(),
  },
});

const frontend = service("frontend", {
  source: github("129duckflew/pai-score", {
    branch: "main",
    rootDirectory: "frontend",
  }),
  build: {
    builder: "DOCKERFILE",
    dockerfilePath: "Dockerfile",
  },
  env: {
    BACKEND_HOST: preserve(),
    BACKEND_PORT: preserve(),
    SOCKETIO_PORT: preserve(),
  },
});

export default project("pai-score", {
  resources: [db, dbVolume, backend, frontend],
});
