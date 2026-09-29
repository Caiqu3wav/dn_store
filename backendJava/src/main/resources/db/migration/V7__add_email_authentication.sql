ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_verification_code_hash VARCHAR(64) NULL,
    ADD COLUMN email_verification_expiration TIMESTAMP NULL,
    ADD COLUMN email_verification_last_sent_at TIMESTAMP NULL,
    ADD COLUMN email_verification_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN mfa_challenge_token_hash VARCHAR(64) NULL,
    ADD COLUMN mfa_code_hash VARCHAR(64) NULL,
    ADD COLUMN mfa_code_expiration TIMESTAMP NULL,
    ADD COLUMN mfa_last_sent_at TIMESTAMP NULL,
    ADD COLUMN mfa_attempts INT NOT NULL DEFAULT 0;