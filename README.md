# Memcyco Scheduling System

A full-stack scheduling application for creating, managing, and executing scheduled tasks. The system supports multiple scheduling strategies, configurable task parameters, persistent storage, and automated execution through Quartz Scheduler.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.5 |
| Frontend | React, TypeScript, Vite |
| Scheduling | Quartz Scheduler with JDBC JobStore |
| Database | H2 (file-based) |
| Testing | JUnit, JaCoCo, Vitest |
| Deployment | Docker Compose |

## Features

- Create, view, update, enable/disable, and delete schedules.
- Four scheduling strategies:
  - **One-time:** Execute at a specified future date and time.
  - **Interval:** Execute repeatedly at a configurable interval.
  - **Weekly:** Execute on selected weekdays at a specified time.
  - **Cron:** Execute according to a Quartz cron expression.
- Two predefined task types:
  - **LOG:** Write a configurable message to the application logs.
  - **DUMMY_EMAIL:** Simulate an email task using recipient, subject, and body parameters.
- Server-side input validation.
- Persistent schedules and Quartz triggers.
- Startup reconciliation to recover scheduling state after restarts.
- Structured application logging.

## Quick Start — Docker

**Requirements:** Docker Desktop or Docker Engine with Docker Compose.

From the project root:

```bash
docker compose up --build
```

Once the services are running:

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Health Check | http://localhost:8080/api/health |
| H2 Console | http://localhost:8080/h2-console/ |

To stop the application:

```bash
docker compose down
```

The database is stored in the Docker named volume `h2-data`, which preserves data across container recreation.

**Note:** Running `docker compose down -v` also removes the named volume and its persisted data.

## Running Without Docker

### Prerequisites

- Java 17
- Maven 3.9+
- Node.js 20+
- npm

### Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Frontend

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

Vite proxies `/api` requests to the backend running on port 8080.

The local H2 database is stored under `backend/data` when the backend is launched from the `backend` directory.

## REST API

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/tasks` | Retrieve available task types |
| GET | `/api/schedules` | List all schedules |
| GET | `/api/schedules/{id}` | Retrieve a schedule |
| POST | `/api/schedules` | Create a schedule |
| PUT | `/api/schedules/{id}` | Update a schedule |
| DELETE | `/api/schedules/{id}` | Delete a schedule |
| GET | `/api/health` | Health check |

### Task Parameters

| Task Type | Required Parameters |
|---|---|
| `LOG` | `message` |
| `DUMMY_EMAIL` | `recipient`, `subject`, `body` |

### Time Handling

All scheduling calculations use UTC.

- One-time execution timestamps use ISO-8601 with an explicit offset and are returned normalized to UTC (`Z`).
- Weekly execution times are interpreted in UTC.
- Cron expressions are evaluated in UTC.
- Cron scheduling uses Quartz's six/seven-field syntax, not standard five-field Unix cron.

## Architecture and Persistence

The application separates HTTP request handling, business validation, schedule management, task execution, and persistence.

The general execution flow is:

1. The frontend submits schedule requests through the REST API.
2. The backend validates the request and persists the schedule.
3. The scheduling service synchronizes the corresponding Quartz job and trigger.
4. Quartz executes the configured task at its scheduled time.
5. Execution results are recorded in the application logs.

### Quartz Persistence

Quartz uses a persistent JDBC JobStore backed by H2.

Jobs are associated with stable schedule IDs, allowing the system to reconcile application schedules with Quartz state.

During startup, reconciliation:

- Preserves existing valid triggers.
- Recreates missing recurring or future triggers.
- Removes Quartz jobs for disabled schedules.
- Avoids recreating expired one-time triggers.

The database schema initialization creates missing tables and indexes without dropping existing Quartz data. Spring Boot's destructive packaged Quartz schema initializer is disabled.

## Testing and Coverage

### Backend

```bash
cd backend
mvn clean test
```

The backend uses automated tests and JaCoCo coverage reporting.

Coverage report:

`backend/target/site/jacoco/index.html`

### Frontend

```bash
cd frontend
npm install
npm test -- --run
npm run build
```

Generate the frontend coverage report:

```bash
npm run test:coverage
```

Coverage report:

`frontend/coverage/index.html`

### Verified Test Results

| Component | Result |
|---|---|
| Backend | 30 tests passed |
| Frontend | 13 tests passed |
| Backend instruction coverage | 87% |
| Backend branch coverage | 69% |
| Backend JAR build | Passed |
| Frontend production build | Passed |

These results reflect the latest completed local verification.

## H2 Database Console

When running with Docker Compose, open:

http://localhost:8080/h2-console/

Use the following connection settings:

| Setting | Value |
|---|---|
| Driver | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:/app/data/scheduling;DB_CLOSE_ON_EXIT=FALSE` |
| Username | `sa` |
| Password | Leave blank |

The console is enabled only under the `local` Spring profile, activated by Docker Compose.

The backend port is bound to `127.0.0.1` to restrict access to the Docker host.

### Inspecting Stored Data

Application schedules:

```sql
SELECT * FROM SCHEDULES;
```

Quartz triggers:

```sql
SELECT * FROM QRTZ_TRIGGERS;
```

Additional Quartz tables include `QRTZ_JOB_DETAILS`, `QRTZ_SIMPLE_TRIGGERS`, and `QRTZ_CRON_TRIGGERS`.

The Docker named volume `h2-data` persists both application and Quartz data across container restarts.

## AI-Assisted Development

This project was developed with the assistance of AI tools:

- **ChatGPT (OpenAI):** Used for architectural discussions, requirement analysis, technical decision-making, code reviews, debugging guidance, and test planning.
- **Codex:** Used for code generation, implementation, refactoring, test creation, and development workflow assistance.

AI tools were used to accelerate development and reduce repetitive implementation work.

Architecture decisions, implementation direction, integration testing, debugging, and final verification were guided and reviewed by the developer.

The application was also manually tested end-to-end, including Docker deployment, database persistence, and scheduled task execution.

**AI was used as a development tool, not as a substitute for engineering judgment or verification.**