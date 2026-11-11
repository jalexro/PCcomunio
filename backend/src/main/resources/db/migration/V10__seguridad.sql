-- Verificación de email y recuperación de contraseña.

ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Las cuentas que ya existían se dan por verificadas: nadie pudo verificarlas antes de que
-- existiera el mecanismo, y con app.auth.exigir-email-verificado=true quedarían sin acceso.
UPDATE app_user SET email_verified = TRUE;

-- Tokens de un solo uso enviados por correo. Igual que los refresh token, solo se guarda el
-- hash SHA-256: un volcado de la base de datos no permite usarlos.
CREATE TABLE user_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('VERIFICACION', 'RESET')),
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_token_user ON user_token (user_id, type);
