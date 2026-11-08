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
    ALTER COLUMN trgm_search SET EXPRESSION AS (
        preferred_name_first || ' ' ||
        COALESCE(preferred_name_middle || ' ', '') ||
        preferred_name_last || ' ' ||
        formal_name ||
        COALESCE(' ' || pseudonym, '') ||
        COALESCE(' ' || domain_email, '') ||
        ' ' || array_to_string_immutable(domain_email_aliases, ' ') ||
        ' ' || array_to_string_immutable(private_emails, ' ')
        );

ALTER TABLE bodies
    ALTER COLUMN trgm_search SET EXPRESSION AS (name || COALESCE(' ' || email, ''));
