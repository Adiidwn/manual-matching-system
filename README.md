# Transaction Reconciliation

Small maker-checker-signer workflow for manually reconciling transactions from two sources. The project is a modular Spring Boot application backed by PostgreSQL; Docker Compose includes Kafka, Redis, and Elasticsearch for later workflow stages.

## Start

```shell
docker compose up --build
```

The API will be available at `http://localhost:8080`; health is at `/actuator/health`.

## Planned implementation steps

1. Project skeleton, local infrastructure, and health endpoint.
2. Transaction schema, indexed reconciliation GET, and a repeatable one-million-row demo data loader.
3. Manual match creation and maker-checker-signer approval workflow with audit history.
4. Asynchronous batch import through Kafka.
5. Measured Redis cache for read-heavy reference data and Elasticsearch for free-text transaction search.

The one-million-row dataset is synthetic. Users inspect paginated queues and candidate matches; they do not manually review every row.
