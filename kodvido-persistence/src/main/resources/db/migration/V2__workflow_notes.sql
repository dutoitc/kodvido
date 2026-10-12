CREATE TABLE workflow_notes (
 project_id UUID PRIMARY KEY REFERENCES project(id) ON DELETE CASCADE,
 revision BIGINT NOT NULL CHECK (revision > 0),
 development VARCHAR(4000) NOT NULL,
 operations VARCHAR(4000) NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE workflow_phase (
 project_id UUID NOT NULL REFERENCES workflow_notes(project_id) ON DELETE CASCADE,
 phase_order INTEGER NOT NULL CHECK (phase_order BETWEEN 0 AND 4),
 phase_date DATE NOT NULL,
 description VARCHAR(500) NOT NULL,
 state VARCHAR(20) NOT NULL CHECK (state IN ('PLANNED','IN_PROGRESS','DONE')),
 PRIMARY KEY(project_id, phase_order)
);
