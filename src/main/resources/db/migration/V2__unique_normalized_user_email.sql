-- Preserve V1: it has already been applied to Supabase.
-- Protect against concurrent registrations and case variants, including legacy rows.
CREATE UNIQUE INDEX uk_users_email_normalized ON users (lower(btrim(email)));
