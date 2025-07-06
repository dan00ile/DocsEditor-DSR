CREATE TABLE users (
   id UUID PRIMARY KEY,
   username VARCHAR(50) UNIQUE NOT NULL,
   email VARCHAR(100) UNIQUE NOT NULL,
   password_hash VARCHAR(255) NOT NULL,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE documents (
   id UUID PRIMARY KEY,
   title VARCHAR(255) NOT NULL,
   content TEXT NOT NULL,
   created_by UUID REFERENCES users(id),
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   version_counter INTEGER DEFAULT 1
);

CREATE TABLE document_versions (
   id UUID PRIMARY KEY,
   document_id UUID REFERENCES documents(id),
   version_name VARCHAR(100) NOT NULL,
   content TEXT NOT NULL,
   created_by UUID REFERENCES users(id),
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_sessions
(
    session_id    BIGSERIAL PRIMARY KEY,
    user_id       UUID REFERENCES users (id) ON DELETE CASCADE,
    token         VARCHAR(255) UNIQUE NOT NULL,
    device_info   TEXT,
    ip_address    VARCHAR(45),
    expires_at    TIMESTAMP NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);