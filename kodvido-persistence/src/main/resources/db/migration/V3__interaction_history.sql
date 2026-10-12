CREATE TABLE project_interaction (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('INSPECTION_REQUESTED', 'WORKFLOW_SAVED', 'RELOCATED')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_interaction_project_sequence ON project_interaction(project_id, sequence DESC);
