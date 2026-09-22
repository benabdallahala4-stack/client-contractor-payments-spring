CREATE INDEX contracts_client_id_idx ON contracts (client_id);
CREATE INDEX contracts_contractor_id_idx ON contracts (contractor_id);
CREATE INDEX jobs_contract_id_idx ON jobs (contract_id);
CREATE INDEX jobs_open_by_contract_idx ON jobs (contract_id, created_at) WHERE status = 'OPEN';

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    aggregate_type varchar(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(80) NOT NULL,
    event_version integer NOT NULL CHECK (event_version > 0),
    payload jsonb NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at timestamptz NOT NULL,
    locked_until timestamptz,
    last_error varchar(500),
    created_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX outbox_events_claim_idx
    ON outbox_events (available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
