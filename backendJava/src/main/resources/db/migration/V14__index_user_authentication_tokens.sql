CREATE INDEX idx_users_reset_token ON users (reset_token);

CREATE INDEX idx_users_mfa_challenge_token_hash ON users (mfa_challenge_token_hash);