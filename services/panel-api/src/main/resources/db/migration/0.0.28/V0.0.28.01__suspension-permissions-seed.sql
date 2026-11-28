-- G-10: zawieszenia dla zarządu ZO (LOCAL) i ZK (GLOBAL). Skarbnik ZO celowo nie dostaje uprawnień:
-- zawieszanie nie jest jego kompetencją.

INSERT INTO permission_group_permissions (permission_group_id, permission_type)
SELECT g.id, p.permission_type::permission_type
FROM (VALUES ('LOCAL',  'District board', 'SUSPENSIONS_READ'),
             ('LOCAL',  'District board', 'SUSPENSIONS_MANAGE'),
             ('GLOBAL', 'National board', 'SUSPENSIONS_READ'),
             ('GLOBAL', 'National board', 'SUSPENSIONS_MANAGE'))
         AS p (scope, group_name, permission_type)
         JOIN permission_group g ON g.scope = p.scope::permission_scope AND g.name = p.group_name
ON CONFLICT DO NOTHING;
