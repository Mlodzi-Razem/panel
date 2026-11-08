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

DO
$$
    DECLARE
        next_id BIGINT;
    BEGIN
        SELECT GREATEST(COALESCE((SELECT MAX(id) FROM members), 0),
                        COALESCE(PG_SEQUENCE_LAST_VALUE('seq_members_id'), 0)) + 1
        INTO next_id;

        EXECUTE FORMAT('ALTER SEQUENCE seq_members_id MINVALUE 1 START WITH 1 RESTART WITH %s', next_id);
    END
$$;

ALTER TABLE suspensions
    ALTER COLUMN id SET DEFAULT NEXTVAL('seq_suspensions_id');

ALTER TABLE suspensions
    ADD FOREIGN KEY (suspended_user_id) REFERENCES members (id);

CREATE INDEX suspensions_suspended_user_id_idx ON suspensions (suspended_user_id);

ALTER TABLE body_template_member_types
    ADD FOREIGN KEY (permission_group_id) REFERENCES permission_group (id);

CREATE INDEX body_template_member_types_permission_group_id_idx ON body_template_member_types (permission_group_id);

ALTER TABLE audited_operations
    ALTER COLUMN operation_timestamp TYPE timestamptz USING operation_timestamp AT TIME ZONE 'UTC';
