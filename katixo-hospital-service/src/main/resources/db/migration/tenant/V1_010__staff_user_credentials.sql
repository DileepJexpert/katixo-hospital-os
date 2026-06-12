-- Built-in login credentials on staff_user_ref
-- (interim until shared ERP auth service is wired)

ALTER TABLE staff_user_ref ADD COLUMN username VARCHAR(100);
ALTER TABLE staff_user_ref ADD COLUMN password_hash VARCHAR(100);

CREATE UNIQUE INDEX idx_staff_user_username ON staff_user_ref(username) WHERE username IS NOT NULL;
