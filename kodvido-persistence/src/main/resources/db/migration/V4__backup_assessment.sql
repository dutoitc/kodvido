CREATE TABLE backup_assessment (
    project_id UUID PRIMARY KEY REFERENCES project(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE backup_evidence (
    project_id UUID NOT NULL REFERENCES backup_assessment(project_id) ON DELETE CASCADE,
    dimension VARCHAR(32) NOT NULL CHECK (dimension IN ('GIT_REMOTE','UNTRACKED_FILES','APPLICATION_DATA','RESTORE_TEST')),
    state VARCHAR(32) NOT NULL CHECK (state IN ('UNKNOWN','DECLARED_OK','DECLARED_FAILED')),
    checked_on DATE,
    evidence VARCHAR(1000) NOT NULL,
    PRIMARY KEY (project_id, dimension),
    CHECK ((state = 'UNKNOWN' AND checked_on IS NULL) OR (state <> 'UNKNOWN' AND checked_on IS NOT NULL AND TRIM(evidence) <> ''))
);
