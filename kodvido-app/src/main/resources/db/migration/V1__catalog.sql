CREATE TABLE project (
    id UUID PRIMARY KEY,
    name VARCHAR(250) NOT NULL,
    primary_path VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX ix_project_path ON project(primary_path);
