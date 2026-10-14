CREATE TABLE api_key_permissions (
    api_key_id BIGINT NOT NULL REFERENCES api_keys (id) ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL,
    PRIMARY KEY (api_key_id, permission)
);
