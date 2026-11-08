/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

ALTER TABLE members
    DROP CONSTRAINT members_private_emails_key;

ALTER TABLE members
    ADD COLUMN directory_user_id TEXT UNIQUE;

CREATE TABLE member_emails
(
    email     TEXT   NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members (id) ON DELETE CASCADE
);

CREATE INDEX member_emails_member_id_idx ON member_emails (member_id);

CREATE FUNCTION members_reject_own_duplicate_emails() RETURNS TRIGGER AS
$$
DECLARE
    duplicate TEXT;
BEGIN
    SELECT normalized
    INTO duplicate
    FROM (SELECT LOWER(BTRIM(e)) AS normalized
          FROM UNNEST(ARRAY [NEW.domain_email] || NEW.domain_email_aliases || NEW.private_emails) AS e) AS emails
    WHERE normalized <> ''
    GROUP BY normalized
    HAVING COUNT(*) > 1
    LIMIT 1;

    IF duplicate IS NOT NULL THEN
        RAISE EXCEPTION 'duplicate e-mail "%" within one member', duplicate
            USING ERRCODE = 'unique_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION members_sync_emails() RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        DELETE FROM member_emails WHERE member_id = NEW.id;
    END IF;

    INSERT INTO member_emails (email, member_id)
    SELECT LOWER(BTRIM(e)), NEW.id
    FROM UNNEST(ARRAY [NEW.domain_email] || NEW.domain_email_aliases || NEW.private_emails) AS e
    WHERE LOWER(BTRIM(e)) <> '';

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

INSERT INTO member_emails (email, member_id)
SELECT LOWER(BTRIM(e)), m.id
FROM members m,
     UNNEST(ARRAY [m.domain_email] || m.domain_email_aliases || m.private_emails) AS e
WHERE LOWER(BTRIM(e)) <> '';

CREATE TRIGGER members_reject_own_duplicate_emails
    BEFORE INSERT OR UPDATE OF domain_email, domain_email_aliases, private_emails
    ON members
    FOR EACH ROW
EXECUTE FUNCTION members_reject_own_duplicate_emails();

CREATE TRIGGER members_sync_emails
    AFTER INSERT OR UPDATE OF domain_email, domain_email_aliases, private_emails
    ON members
    FOR EACH ROW
EXECUTE FUNCTION members_sync_emails();
