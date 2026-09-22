CREATE TABLE profiles (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL CHECK (length(trim(name)) > 0),
    role varchar(16) NOT NULL CHECK (role IN ('CLIENT', 'CONTRACTOR')),
    balance_cents integer NOT NULL CHECK (balance_cents >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE contracts (
    id uuid PRIMARY KEY,
    client_id uuid NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    contractor_id uuid NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    title varchar(120) NOT NULL CHECK (length(trim(title)) > 0),
    status varchar(16) NOT NULL CHECK (status = 'ACTIVE'),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (client_id <> contractor_id)
);

CREATE TABLE jobs (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL REFERENCES contracts(id) ON DELETE RESTRICT,
    title varchar(120) NOT NULL CHECK (length(trim(title)) > 0),
    amount_cents integer NOT NULL CHECK (amount_cents > 0),
    status varchar(16) NOT NULL CHECK (status IN ('OPEN', 'PAID')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL UNIQUE REFERENCES jobs(id) ON DELETE RESTRICT,
    amount_cents integer NOT NULL CHECK (amount_cents > 0),
    idempotency_key varchar(128) NOT NULL CONSTRAINT payments_idempotency_key_unique UNIQUE,
    status varchar(16) NOT NULL CHECK (status = 'COMPLETED'),
    created_at timestamptz NOT NULL DEFAULT now()
);
