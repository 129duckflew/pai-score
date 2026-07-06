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
    PORT: "8081",
    SOCKETIO_PORT: "8089",
    SPRING_PROFILES_ACTIVE: "railway",
    PGHOST: db.env.PGHOST,
    PGPORT: db.env.PGPORT,
    PGUSER: db.env.PGUSER,
    PGPASSWORD: db.env.PGPASSWORD,
    PGDATABASE: db.env.PGDATABASE,
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
    BACKEND_HOST: backend.env.RAILWAY_PRIVATE_DOMAIN,
    BACKEND_PORT: "8081",
    SOCKETIO_PORT: "8089",
  },
});

export default project("pai-score", {
  resources: [db, dbVolume, backend, frontend],
});
