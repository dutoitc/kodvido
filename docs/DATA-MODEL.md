# Initial logical data model (not yet all implemented)

```mermaid
erDiagram
  PROJECT ||--o{ PROJECT_LOCATION : has
  PROJECT ||--o{ SCAN_RUN : scanned
  SCAN_RUN ||--o{ OBSERVATION : emits
  PROJECT ||--o{ PROJECT_PHASE : tracks
  PROJECT ||--o{ HUB_INTERACTION : used
  PROJECT ||--o{ DETECTOR_GAP : reports
  PROJECT { uuid id PK string name string lifecycle string description }
  PROJECT_LOCATION { uuid id PK uuid project_id string path boolean is_primary }
  SCAN_RUN { uuid id PK uuid project_id datetime started_at datetime ended_at string outcome }
  OBSERVATION { uuid id PK uuid run_id string detector_id string state string source datetime observed_at string payload_json }
  PROJECT_PHASE { uuid id PK uuid project_id date phase_date string text string status }
  HUB_INTERACTION { uuid id PK uuid project_id datetime when string action }
  DETECTOR_GAP { uuid id PK uuid project_id string detector_id string summary string status }
```

Do not create a large ORM model before M1. Enforce UUID identifiers independent of filesystem location. Phases may reside in PROJECT.md instead of database when detected as project-owned documentation; resolve source-of-truth and precedence with ADR before implementation.
