-- G-10 (art. 10 statutu). Numer roboczy 0.0.26; finalny nadaje Landing.
-- Nowe typy uprawnień dodane w osobnej migracji: PostgreSQL nie pozwala użyć nowej wartości enuma
-- w tej samej transakcji, która ją dodała (wzór V0.0.6.01). Seed w V0.0.26.01.

ALTER TYPE permission_type ADD VALUE 'SUSPENSIONS_READ';
ALTER TYPE permission_type ADD VALUE 'SUSPENSIONS_MANAGE';
