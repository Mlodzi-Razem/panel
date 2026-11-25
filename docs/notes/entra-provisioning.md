# Provisioning kont członkowskich w Microsoft Graph / Entra ID — research (B1)

> **Status:** research read-only, fala 0, pakiet B1. Wejście dla B2 (rdzeń provisioningu) i B3 (konto przy przyjęciu).
> **Autor:** analityk B1 (sesja junior), 2026-11-24. Workspace: `panel-b1` (gałąź `docs/entra-provisioning-research` @ `e30a511`); zmieniony wyłącznie ten plik. Zero zmian w kodzie, zero sekretów.
> **Metodyka:** dokumentacja Microsoft Learn pobierana 2026-11-22 (nuty `/tmp/user-provisioning-research/notes/03-graph-powershell.md`, tam oznaczone jako zweryfikowane) oraz 2026-11-24 (ta sesja). Każde twierdzenie ma tag źródła; cytaty EN przytaczam werbatim, tłumaczenie/parafraza własna bez cudzysłowu. Tagi:
>
> - `[spec]` — referencja API Microsoft Graph (learn.microsoft.com/graph/api),
> - `[docs]` — oficjalna dokumentacja Microsoft Learn (Entra, M365, PowerShell),
> - `[community]` — Microsoft Q&A / Tech Community / Stack Overflow (informacyjne, **nie** kontrakt),
> - `[wniosek własny]` — moja konkluzja, decyzja dla B2/B3 albo model do potwierdzenia na tenancie,
> - `[unverified]` / `[do weryfikacji]` — nie potwierdzone w dokumentacji; do sprawdzenia smoke-testem przez właściciela.
>
> Stosunek do decyzji właściciela (PLAN-WORKFLOW §2): **D1** (role Entra na service principal zawężone do AU „Członkowie MR”, zamiast uprawnień Graph na cały tenant), **D2** (SKU jako konfiguracja; tenant-variant: 300 BB + F1), **D3** (TAP przez jednorazowy link; TAP nigdy w bazie ani w logach), **D4** (trial `990000–999999`).

---

## 1. Punkt wyjścia: co już ma Panel

- `EntraGraphAppToken` (`services/panel-api/src/main/java/.../directory/entra/`) realizuje **client credentials**: token aplikacyjny (app-only) z certyfikatu (JWS `x5t`, RS256/ES256) albo sekretu, `scope = https://graph.microsoft.com/.default`. B2 dokłada klienta zapisu na tym samym tokenie.
- `GraphEntraUserLookup` czyta `GET /v1.0/users/{id}?$select=id,employeeId,accountEnabled` i 404 mapuje na „nie znaleziono” — to gotowy schemat idempotentnego odczytu-od-wyniku przed operacją.
- Autoryzacja aplikacji w Entra dzieje się na **service principanie** aplikacji — i o to właśnie obchodzi nas D1: *roli nadanej SP-owi*, nie „aplikacji” abstrakcyjnie.

**[wniosek własny]** Cały provisioning (B2/B3) powinien być kontrolowany zgodnie z D1: żadnych Graph *application permissions* dla operacji katalogowych poza TAP (§4.4 — wyjątek techniczny), natomiast **role Entra nadane service principallowi w zasięgu administracyjnej jednostki (AU)** + wąskie, wyspecjalizowane uprawnienie tylko tam, gdzie rola się nie skaluje. Wymóg „zanim zaczniesz”: Directory Readers (§3.4).

---

## 2. Model autoryzacji: application permissions vs rola Entra (lekcja M5 udokumentowana)

Dwa niezależne tory przyznawania app-only — dokumentacja podaje je wprost jako alternatywy:

> „Apart from being assigned Microsoft Graph application permissions, an app might also be granted the privileges it needs through one of the following conditions:
> * When the app is assigned ownership of the resource that it intends to manage.
> * **When the app is assigned permissions through an RBAC system or custom administrative roles.**” [docs: https://learn.microsoft.com/en-us/graph/permissions-overview]

I druga strona medalu — application permissions są zawsze tenant-wide:

> „*Application permissions*, also called *app roles*, work in the app-only access scenario, without a signed-in user present. **The application can access *any* data that the permission is associated with.** For example, an application granted the *Files.Read.All* application permission can read any file in the organization.” [docs: jw.]

> „With the *User.ReadWrite.All* application permission, an application can update many of the writable user properties supported by Microsoft Graph, **including for users assigned privileged admin roles**.” [docs: jw.]

Z tej samej strony jeszcze: „Only Privileged Role Administrator and Global Administrator can consent to application permissions.” oraz „Permissions granted through Microsoft Entra built-in roles **don't limit the app to calling Microsoft Graph APIs only**.” [docs: jw.] — rola na SP daje uprawnienia nieco szersze niż sam Graph (np. portal), ale zawężone do obiektów w zasięgu roli.

**Lekcja M5 zgeneralizowana:** w M5 (DELTA `panel-teams-app/AGENTS.md`) zrezygnowaliśmy z `Mail.Send` w Entra na rzecz Exchange *RBAC for Applications* zawężonego do skrzynki, właśnie dlatego, że „uprawnienia się sumują”:

> „RBAC for Applications in Exchange Online allows admins to grant permissions to an application that's independently accessing data in Exchange Online. **This grant can be paired with a scope of access (resource scope) to specify which mailboxes an app can access.**” [docs: https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac]

W Entra tor „resource scope” dla Graph-app-permissions **nie istnieje** ([wniosek własny] — brak takiego mechanizmu w dokumentacji; jw. mówi „*any* data”). Zasięg do podzbioru obiektów daje **wyłącznie rola Entra nadana SP z zasięgiem AU** (§3). Konsekwencja zasady sumowania: *jeśli SP dostanie jakąkolwiek katalogową application permission (np. `User.ReadWrite.All`), AU-scope przestaje chronić — app permission pokrywa cały tenant.* Wniosek dla B2: **nie nadawać katalogowych app permission** (`User.ReadWrite.All`, `Directory.ReadWrite.All`, `LicenseAssignment.ReadWrite.All`, `GroupMember.ReadWrite.All`), tylko role AU + uprawnienie TAP-specific (§4.4) + Directory Readers (§3.4). Dokumentacja Graph PREFIKSUJE to samo ostrzeżenie:

> „Directory permissions grant broad access to directory (Microsoft Entra ID) resources such as user, group, and device in an organization. Whenever possible, choose permissions specific to these resources and avoid using directory permissions.” [docs: https://learn.microsoft.com/en-us/graph/permissions-reference]

---

## 3. AU „Członkowie MR” — fundament operacyjny D1

### 3.1. Czym jest AU i co może zawierać

> „An administrative unit is a Microsoft Entra resource that can be a container for other Microsoft Entra resources. **An administrative unit can contain only users, groups, or devices.**” [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/administrative-units]

> „Administrative units restrict permissions in a role to any portion of your organization that you define. […] Note that if you assign a role to a user that is not a member of an administrative unit, the scope of the role is the entire tenant.” [docs: jw.]

Zasięg AU działa wyłącznie na *management permissions* — nie blokuje zwykłego odczytu domyślnych uprawnień:

> „Administrative units apply scope only to management permissions. They don't prevent members or administrators from using their default user permissions to browse other users, groups, or resources outside the administrative unit.” [docs: jw.]

Semantyka zasięgu z oficjalnej strony przypisywania ról:

> „The scope `/administrativeUnits/<ID>` means the principal can manage the members of the administrative unit (based on the role the principal is assigned), **not the administrative unit itself**.” [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/manage-roles-portal]

Licencjonowanie AU (mamy Business Premium, a więc Entra ID P1 — spełnione):

> „Using administrative units requires a Microsoft Entra ID P1 license for each administrative unit administrator who is assigned directory roles over the scope of the administrative unit, and a Microsoft Entra ID Free license for each administrative unit member.” [docs: administrative-units, jw.]

Ograniczenie dotyczące grup w AU (istotne dla B4 — mapowanie organ→grupa):

> „Adding a group to an administrative unit brings the group itself into the management scope of the administrative unit, but **not** the members of the group. In other words, an administrator scoped to the administrative unit can manage properties of the group, such as group name or membership, but they cannot manage properties of the users or devices within that group (unless those users and devices are separately added as members of the administrative unit).” [docs: jw.]

**[wniosek własny]** Dla B4 to wystarcza: Panel zmienia *członkostwo grupy* (relację `members` grupy), a nie obiekty użytkowników — grupy organów muszą być **w zasięgu AU**; użytkownicy będą tam i tak (reguła dynamiczna, §3.3).

Sanity-table z tej samej strony potwierdza wprost wsparcie Graph/PowerShell dla naszych operacji (portale admina tylko częściowo):

> „Administrative unit-scoped management of user properties, passwords ✅ | Administrative unit-scoped management of user licenses ✅ | Administrative unit-scoped blocking and unblocking of user sign-ins ✅ | Administrative unit-scoped management of user multifactor authentication credentials ✅ | Administrative unit-scoped creation and deletion of groups ✅ | Administrative unit-scoped management of group properties and membership for Microsoft 365 groups ✅ | Administrative unit-scoped management of group licensing ✅” (wyciąg z tabel „User management|Group management”; kolumna „Microsoft Graph/PowerShell” w całości ✅) [docs: administrative-units, jw.]

### 3.2. Role nadawalne z zasięgiem AU

> „The following Microsoft Entra roles can be assigned with administrative unit scope. Additionally, any custom role can be assigned with administrative unit scope as long as the custom role's permissions include at least one permission relevant to users, groups, or devices.” [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-assign-roles]

Wyciąg z pełnej tabeli EN (tłumaczenia w tabeli zbiorczej §8):

- **Authentication Administrator** — „Has access to view, set, and reset authentication method information for any non-admin user in the assigned administrative unit only.”
- **Groups Administrator** — „Can manage all aspects of groups in the assigned administrative unit only.”
- **License Administrator** — „Can assign, remove, and update license assignments within the administrative unit only.”
- **User Administrator** — „Can manage all aspects of users and groups, including resetting passwords for limited admins within the assigned administrative unit only. Cannot currently manage users' profile photographs.” [docs: admin-units-assign-roles, jw.]

**BRAK w tabeli (sprawdzone w całości):** *Privileged Authentication Administrator* oraz *Authentication Policy Administrator* — **nie da się scalić do AU** [wniosek własny na podstawie pełnej listy na jw.; pozostałe role w tabeli to m.in.: Attribute Assignment Admin/Reader, Cloud Device Admin, Helpdesk Admin, Password Admin, Printer Admin, Teams Administrator, Teams Devices Admin]. Konsekwencje:

- TAP *policy* (włączenie metody) — Authentication Policy Administrator ma zakres wyłącznie tenantowy; to jednorazowa operacja właściciela (§5 pkt 6), Panel nie potrzebuje jej w runtime;
- rola-variant dla TAP create/delete w runtime to **Authentication Administrator @ AU** (ograniczenie: tylko użytkownicy bez roli administratora — nasi członkowie/kandydaci takich nie mają); niemniej **rekomendowany tor to application permission `UserAuthMethod-TAP.ReadWrite.All`** (§4.4), bo w kolumnie *application permissions* dokumentacja TAP wskazuje uprawnienia, nie role.

Uwaga na AU-scoped role kontra obiekty adminów:

> „Certain role permissions apply only to nonadministrator users when assigned with the scope of an administrative unit. In other words, administrative unit scoped Helpdesk Administrators can reset passwords for users in the administrative unit only if those users don't have administrator roles. The following permissions are restricted when the target of an action is a user with another role or an administrator: Read and modify user authentication methods; Reset user passwords; Modify sensitive user properties…; Delete or restore user accounts.” [docs: admin-units-assign-roles, jw.]

**[wniosek własny]** Żaden członek/kandydat nie będzie miał roli administratora, więc wyjątek nas nie dotyka — ale B4/B5 nigdy nie mogą zarządzać kontami sztabu (te celowo zostają **poza** AU, §5.1).

### 3.3. Mechanizm auto-dołączania userów do AU — reguła dynamiczna

Panel jako AU-scoped admin **nie może** sam dodawać obiektów do AU — [community] potwierdza scenę portalową z 2022 r. („Add member” wyszarzone; moderator MSFT: „you need to be either a Global Admin or a Privileged Role Administrator to add new members”; User Administrator ma na `administrativeUnits` tylko odczyt) [https://learn.microsoft.com/en-us/answers/questions/732338/impossible-to-add-user-in-administrative-unit-with]. Rozwiązanie: **dynamiczna reguła członkostwa AU** — oficjalnie wspierana dla *użytkowników*:

> „With dynamic membership groups, you can add or remove users or devices for administrative units dynamically using rules. […] it's currently not possible to create an administrative unit with rules for dynamic membership groups that includes more than one object type. For example, you can create administrative units with rules for dynamic membership groups for users or devices, but not both. Administrative units with rules for dynamic membership groups **for groups are currently not supported**.” [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-members-dynamic]

Właściwość `userPrincipalName` jest oficjalnie wspierana w regułach (typ string; operatorzy `-eq/-match/-startsWith/-endsWith`, z ostrzeżeniem o wydajności `Match`):

> „| `userPrincipalName` | Any string value | `user.userPrincipalName -eq 'alias@domain'` |” [docs: https://learn.microsoft.com/en-us/entra/identity/users/groups-dynamic-membership]
> „> [!CAUTION] For best results, minimize the use of Match or Contains as much as possible.” [docs: jw.]

Przetwarzanie reguł jest asynchroniczne:

> „The initial update of an administrative unit can take a few minutes depending on your tenant size and the current Microsoft Entra ID load.” [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-faq-troubleshoot]

Po włączeniu trybu dynamicznego ręczne dodawanie/wyjmowanie jest zablokowane:

> „When an administrative unit has been configured for rules for dynamic membership groups, the usual commands to add or remove members for the administrative unit are disabled as the dynamic membership groups engine retains the sole ownership of adding or removing members.” [docs: admin-units-members-dynamic, jw.]

Licencja dynamicznych AU — P1 na **każdego unikalnego użytkownika**:

> „Dynamic membership rules for administrative units require a Microsoft Entra ID P1 license for each unique user that is a member of one or more dynamic administrative units. You don't have to assign licenses to users for them to be members of dynamic administrative units, but you must have the minimum number of licenses in the Microsoft Entra organization to cover all such users.” [docs: admin-units-members-dynamic, jw.]

**[wniosek własny — rekomendowany design AU]:** AU „Członkowie MR” tworzy właściciel raz (`membershipType="Dynamic"`, reguła np. `user.userType -eq 'Member' and user.userPrincipalName -match '^\d{6}@mlodzirazem\.org$'`). Konta członków (`000NNN@mlodzirazem.org`, numery `%06d`) i triale (`99NNNN@mlodzirazem.org`, D4) pasują jednym wzorcem 6 cyfr. Składnię (znak ucieczki backslash!) validate'ować w rule builderze. Wtedy:

- każdy user utworzony przez Panel automatycznie wpada do AU w kilka minut — SP dostaje zakres na niego „sam”, bez uprawnień dodawania do AU (których mieć nie może);
- **okno między create a zadziałaniem reguły to realne 403** przy natychmiastowym PATCH/assignLicense — worker B2 traktuje je jako *retryable* (§4.1, §4.3, ryzyko R3 w §7);
- Business Premium (P1) pokrywa wymóg licencyjny; **[do weryfikacji na koncie licencyjnym]** licznik „unique users w dynamicznych AU” przy 804+ obiektach.

### 3.4. Service principal, Directory Readers i nadanie ról

Rola w zasięgu AU może być nadana service principallowi — oficjalnie:

> „The following security principals can be assigned to a role with an administrative unit scope: Users; Microsoft Entra role-assignable groups; **Service principals**.” [docs: admin-units-assign-roles, jw.]

Ale SP nie ma domyślnie prawa odczytu katalogu — najważniejszy „dokładny” wymóg z całego researchu:

> „**Service principals and guest users won't be able to use a role assignment scoped to an administrative unit unless they're also assigned corresponding permissions to read the objects.** This is because service principals and guest users don't receive directory read permissions by default, which are required to perform administrative actions. To enable a service principal or guest user to use a role assignment scoped to an administrative unit, **you must assign the Directory Readers role (or another role that includes read permissions) at a tenant scope. It isn't currently possible to assign directory read permissions scoped to an administrative unit.**” [docs: admin-units-assign-roles, jw.]

**[wniosek własny]** Konsekwencje: (1) Directory Readers na SP jest **obowiązkowy** i z definicji tenant-wide — to cena D1 i zarazem ryzyko odczytu PII (§7 R2). (2) Istniejący `GraphEntraUserLookup` (GET `/users/{id}`) dzisiaj prawdopodobnie działa na `User.Read.All` app permission — docelowo do **zamiany na Directory Readers** (spójnie z zasadą „nie sumuj”), z osobnym smoke-testem lookupu przed zdjęciem permission. (3) Format nadania roli — Graph API `POST /roleManagement/directory/roleAssignments` z `directoryScopeId`:

> „For the directory (Microsoft Entra ID) provider, this property supports the following formats: `/` for tenant-wide scope * **`/administrativeUnits/{administrativeunit-ID}` to scope to an administrative unit** * `/{application-objectID}` to scope to a resource application.” [spec: https://learn.microsoft.com/en-us/graph/api/rbacapplication-post-roleassignments]

Uprawnienie do samego nadania ról (dla właściciela, nie dla Panelu): `RoleManagement.ReadWrite.Directory`; „Privileged Role Administrator is the least privileged role supported for this operation” [spec: jw.]. Tworzenie AU: „You must be assigned at least the Privileged Role Administrator role to create an administrative unit” [docs: admin-units-faq-troubleshoot].

---

## 4. Operacje provisioningowe — rozdziały per operacja

> **Throttling bazowy (wspólny, żeby nie powtarzać w każdym rozdziale):** limity tożsamościowe Graph obejmują *user, group, subscribedSku, servicePrincipal…* („The preceding limits apply to the following resources: … group … subscribedSku … user”): **S (<50 użytkowników): 3 500 RU / 10 s; M (50–500): 5 000 RU / 10 s; L (>500): 8 000 RU / 10 s; write quota: 3 000 zapisów / 2 m 30 s (app+tenant), 35 000 / 5 min (app), 18 000 / 5 min (tenant, wszystkie aplikacje)** [spec: https://learn.microsoft.com/en-us/graph/throttling-limits (sekcja „Identity and access service limits”; identyczny include źródłowy: https://github.com/microsoftgraph/microsoft-graph-docs-contrib/blob/main/includes/throttling-identity-and-access.md]. Koszty bazowe: „GET `users` → 2 RU”; „GET `subscribedSkus` → 3 RU”; „GET `groups/{id}/members` → 3 RU”; „POST/PATCH/PUT/DELETE Any identity path not listed in the table → 1 RU / Write cost 1”; „Using `$select` decreases cost by 1” [spec: jw.]. Limit globalny: „Any | 130,000 requests per 10 seconds” (per app across all tenants) [spec: jw.]. Reakcja na przekroczenie: **HTTP 429 Too Many Requests + nagłówek `Retry-After`**; „If no `Retry-After` header is provided by the response, we recommend implementing an exponential backoff retry policy.”; „Avoid immediate retries, because all requests accrue against your usage limits.”; „Microsoft Graph SDKs already implement handlers that rely on the `Retry-After` header or default to an exponential backoff retry policy.” [docs: https://learn.microsoft.com/en-us/graph/throttling]. Uwaga: „It's possible to receive `429` responses even when the following limits aren't reached, in situations when the services are under an important load or based on data volume for a specific tenant.” [spec: jw.] — worker B2 zawsze honoruje `Retry-After`.
>
> **Kody błędów bazowe** [docs: https://learn.microsoft.com/en-us/graph/errors]: 400 „Can't process the request because it's malformed or incorrect.”; 403 „Access is denied to the requested resource. The user does not have enough permission or does not have a required license.”; 404 „The requested resource doesn't exist.”; 409 „The request can't be processed due to a conflict with the current state. … For a Directory_ConcurrencyViolation exception, you can repeat the request after some delay.”; 429 „Client application has been throttled and shouldn't attempt to repeat the request until an amount of time has elapsed.”

### 4.1. Utworzenie użytkownika — `POST /users`

**Endpoint i wymagane pola** [spec: https://learn.microsoft.com/en-us/graph/api/user-post-users]:

> „In the request body, supply a JSON representation of user object. The following table lists the properties that are required when you create a user.” — wymagane: `accountEnabled`, `displayName`, `mailNickname`, `onPremisesImmutableId` *(tylko UPN w domenie federacyjnej)*, `passwordProfile`, `userPrincipalName`. Odpowiedź: „If successful, this method returns a `201 Created` response code and user object in the response body.”

> UPN: „The general format is alias@domain, where domain **must be present in the tenant's collection of verified domains**. […] NOTE: This property cannot contain accent characters. Only the following characters are allowed `A - Z`, `a - z`, `0 - 9`, `' . - _ ! # ^ ~`.” [spec: jw.]

**[wniosek]** konta `%06d@mlodzirazem.org` (członkowie) i `99NNNN@mlodzirazem.org` (triale D4) spełniają reguły (same cyfry, zweryfikowana domena). Przy tworzeniu ustawiamy od razu: `usageLocation=PL` (wymóg licencyjny, §4.3), `employeeId` (członek; „The employee identifier assigned to the user by the organization. The maximum length is 16 characters.” [spec: https://learn.microsoft.com/en-us/graph/api/user-update]), `givenName/surname`, `department` (okręg), `passwordProfile` z `forceChangePasswordNextSignIn`. Wszystkie występują jako parametry `New-MgUser` — `-AccountEnabled -DisplayName -MailNickname -UserPrincipalName -PasswordProfile -UsageLocation -EmployeeId -GivenName -Surname -JobTitle -Department` [docs: https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.users/new-mguser].

**Minimalne uprawnienie: dwie wersje** [spec: user-post-users, tabela „Permissions”]:

- application permission: least privileged **`User.Create`** (stan na 2026-11-22; wyżej `User.ReadWrite.All`, `Directory.ReadWrite.All`);
- rola Entra: **User Administrator** / w modelu D1: **User Administrator @ AU** [docs: admin-units-assign-roles, tabela §3.2].

**[do weryfikacji na tenancie — R6]** czy SP *wyłącznie* z rolą User Administrator @ AU (bez żadnej katalogowej app permission) może wykonać `POST /users`. Tabela roli mówi „manage all aspects of users… within the assigned administrative unit only” [docs: admin-units-assign-roles], ale „Currently supported scenarios” wypisuje jawnie zarządzanie *właściwościami* userów, nie tworzenie [docs: administrative-units]. Fallback: nadać **tylko** `User.Create` — najwęższe znane app permission tej operacji (tworzenie bez odczytu/zapisu) [spec: user-post-users]. B2 projektuje klienta tak, by obie ścieżki były zamienne.

**Idempotencja / duplikacja:**

- `POST /users` **nie** jest idempotentny. Wykrywanie przed ponowieniem: `GET /v1.0/users/{userPrincipalName}` → 200 (istnieje — porównaj `employeeId`, uznaj „already provisioned”) vs 404 (można tworzyć) [spec: https://learn.microsoft.com/en-us/graph/api/user-get].
- Kolizja UPN przy powtórnym POST: **HTTP 400 BadRequest**, `PropertyErrorCode ObjectConflict` — [community: https://learn.microsoft.com/en-us/answers/questions/1099166/how-to-use-try-catch-to-handle-error-on-new-azurea — „PropertyName - userPrincipalName, PropertyErrorCode - ObjectConflict HttpStatusCode: BadRequest”; kategoria 400 potwierdzona oficjalnie, jw. bazowe]. Powszechny komunikat „Another object with the same value for property userPrincipalName already exists” [community: https://learn.microsoft.com/en-us/answers/questions/2245101/another-object-with-the-same-value-for-property-us] — **[unverified]** dokładny string.
- **[wniosek dla B2]** outbox: przed create — GET po UPN; po timeout/5xx — ponowny GET przed retry; po 400/ObjectConflict — STOP z raportem rozbieżności (nigdy nie nadpisujemy cudzego konta).

**Throttling operacji:** POST na identity path → 1 RU / 1 write [spec: throttling jw.] — pojedyncze create to trywialne obciążenie (limit 3 000 zapisów / 2 m 30 s na parę app+tenant).

**Odwracalność:** usunięcie usera = soft-delete 30 dni: „some deleted resources *might* be fully restored within 30 days of deletion - such as user, group and application resources” [docs: https://learn.microsoft.com/en-us/graph/best-practices-concept]. B2/B3 nie usuwa userów (wyłączenie §4.5); usuwanie triala po okresie retencji (C1, D4) jest więc odwracalne w tym oknie.

### 4.2. `employeeId` — `PATCH /users/{id | userPrincipalName}`

> „Update the properties of a user object.” [spec: https://learn.microsoft.com/en-us/graph/api/user-update]

**Minimalne uprawnienie:**

- application permission: least **`User.ReadUpdate.All`** (application i delegated; wyżej `User.ReadWrite.All`, `Directory.ReadWrite.All`) [spec: user-update, tabela Permissions];
- granularne scenariusze z tej samej strony: „*User-Mail.ReadWrite.All* is the least privileged permission to update the **otherMails** property”; „*User-Phone.ReadWrite.All* … businessPhones and mobilePhone”; „*User-PasswordProfile.ReadWrite.All* … passwordProfile”; „*User.ManageIdentities.All* … identities” [spec: jw.];
- rola Entra: **User Administrator** / **User Administrator @ AU** w modelu D1 [docs: admin-units-assign-roles].

**[wniosek]** `employeeId` (≤16 znaków, `%06d` mieści się z zapasem) to zwykła property PATCHa — nie potrzeba żadnego dodatkowego uprawnienia poza rolą AU. PATCH z tą samą wartością = no-op → operacja naturalnie idempotentna. B3 ustawia `employeeId` = numer członka przy konwersji triala (§4.7) lub przy create członka.

**Throttling:** PATCH identity path → 1 RU / 1 write [spec: jw.]. **Kody:** 204 No Content przy sukcesie [spec: user-update]; 400 zła wartość; 403 cel poza AU (okno reguły §3.3); 404 brak id/UPN. **Odwracalność:** pełna (nadpisujemy property z powrotem; historię trzyma Panel, nie Entra).

### 4.3. Licencja — `POST /users/{id | userPrincipalName}/assignLicense` + `GET /subscribedSkus`

**Endpoint** [spec: https://learn.microsoft.com/en-us/graph/api/user-assignlicense]:

> body: `addLicenses` (kolekcja `{skuId, disabledPlans[]}`), `removeLicenses` (kolekcja skuIds, „Required. Can be an empty collection.”); „If successful, this method returns a `200 OK` response code and user object in the response body.”

**Minimalne uprawnienie** [spec: user-assignlicense, tabela]:

- application permission: least **`LicenseAssignment.ReadWrite.All`** (application i delegated; wyżej `User.ReadWrite.All`, `Directory.ReadWrite.All`);
- rola Entra: **License Administrator** — AU: „Can assign, remove, and update license assignments within the administrative unit only.” [docs: admin-units-assign-roles].

Katalog SKU: `GET /v1.0/subscribedSkus` — „Get the list of commercial subscriptions that an organization has acquired.”; least `LicenseAssignment.Read.All`; „This method supports only the `$select` OData query parameter… It doesn't support `$filter`.”; 200 OK [spec: https://learn.microsoft.com/en-us/graph/api/subscribedsku-list].

**Wymóg `usageLocation` przed nadaniem licencji** (istota D2-config):

> „You can only assign licenses to user accounts that have the **UsageLocation** property set to a valid ISO 3166-1 alpha-2 country code. For example, US for the United States, and FR for France.” [docs: https://learn.microsoft.com/en-us/microsoft-365/enterprise/assign-licenses-to-user-accounts-with-microsoft-365-powershell]
> „You can't assign a license to a user account unless the account has this value configured.” [docs: https://learn.microsoft.com/en-us/microsoft-365/enterprise/create-user-accounts-with-microsoft-365-powershell]

**[wniosek]** B3 ustawia `usageLocation=PL` już w body create (§4.1) — assignLicense nie trafi na barierę.

**Limit 300 miejsc (tenant-variant: 300 BB + F1):**

> „Our Microsoft 365 Business base per user plans are designed for organizations with up to 300 users only. Organizations may only provision **up to 300 seats total across all of our Business family of plans** (Business Basic, Business Standard, Business Premium).” [docs: https://learn.microsoft.com/en-us/microsoft-365/business-premium/m365bp-faqs]
> „Microsoft reserves the right to enforce the tenant limit of 300 provisioned licenses across the Business family of plans. Organizations with more than 300 users should consider subscribing to Microsoft 365 for enterprise plans.” [docs: jw.]

**[wniosek własny]** konfiguracja spójna z D2: `panel.directory.default-license-sku` jako konfiguracja; przy wykonywaniu zadania worker robi `GET /subscribedSkus` (3 RU) i porównuje `consumedUnits` vs `prepaidUnits.enabled` — ostrzeżenie, nie hard-block (decyzja B3/C2). Wątek „licencja triala wg D2” (B3) używa tego samego mechanizmu z osobną pozycją konfiguracji.

**Idempotencja:** assignLicense nie jest wprost idempotentny. **[unverified]** dokumentacja nie podaje kodu błędu dla powtórnego nadania tego samego SKU. **[wniosek dla B2]** przed add: `GET /users/{id}?$select=assignedLicenses` ( dzięki `$select` koszt -1 RU), jeśli SKU już jest — zamknij zadanie jako sukces („idempotentny replay”); przy retry po 5xx — ta sama procedura; po błędzie oznaczającym „już przypisana” — traktuj jak sukces stanu docelowego.

**Throttling:** POST assignLicense (identity path niewymieniony) → 1 RU / 1 write; subscribedSkus GET 3 RU [spec: jw.]. Przy przyjęciach sztucznych — pomijalne.

**Kody:** 200 OK (body: user) [spec]; 400 (m.in. brak usageLocation, zły skuId — kategoria wg errors); 403 (cel poza AU — okno reguły §3.3; także „does not have enough permission or does not have a required license” w sensie licencji); 404; 429. **Odwracalność:** pełna (`removeLicenses`).

### 4.4. TAP — create/delete (+ policy)

**Włączenie metody w tenancie (właściciel, raz):**

> „Before users can sign in with a TAP, you need to enable this method in the Authentication methods policy and choose which users and groups can sign in by using a TAP. Although you can create a TAP for any user, only users included in the policy can sign in with it. You need the **Authentication Policy Administrator** role to update the TAP Authentication methods policy.” [docs: https://learn.microsoft.com/en-us/entra/identity/authentication/howto-authentication-temporary-access-pass]

Policy przez Graph: `PATCH /policies/authenticationMethodsPolicy/authenticationMethodConfigurations/TemporaryAccessPass`; permission `Policy.ReadWrite.AuthenticationMethod` (delegated **i application**); „*Authentication Policy Administrator* is the least privileged role supported for this operation.” [spec: https://learn.microsoft.com/en-us/graph/api/temporaryaccesspassauthenticationmethodconfiguration-update].

**Create** [spec: https://learn.microsoft.com/en-us/graph/api/authentication-post-temporaryaccesspassmethods]:

- endpoint: `POST /users/{id | userPrincipalName}/authentication/temporaryAccessPassMethods`; odpowiedź `201 Created`; body: `startDateTime`, `lifetimeInMinutes`, `isUsableOnce`;
- **jedyny moment dostępności wartości** — odpowiedź create zawiera `temporaryAccessPass`; analogicznie w UI: „Make a note of the actual TAP value, because you provide this value to the user. **You can't view this value after you select Ok.**” [docs: howto TAP, jw.];
- semantyka nadpisania:

> „**A user can only have one Temporary Access Pass that's usable within its specified lifetime.** If the user requires a new Temporary Access Pass while the current Temporary Access Pass is valid, the admin can create a new Temporary Access Pass for the user, **the previous Temporary Access Pass will be deleted**, and a new Temporary Access Pass will be created.” [spec: jw.]

**Minimalne uprawnienie (create)** [spec: jw., tabela Permissions]:

- Application: least „`UserAuthMethod-TAP.Read.All`” *(sic — nazwa „Read” dla operacji create wygląda na artefakt dokumentacji)*, higher: `UserAuthenticationMethod.ReadWrite.All`, `UserAuthenticationMethod.Read.All`, **`UserAuthMethod-TAP.ReadWrite.All`**. **[wniosek własny] dla B2 wybieramy `UserAuthMethod-TAP.ReadWrite.All`** — semantyka zapisu, zakres tematyczny ograniczony do TAP; dziwną kolumnę least odnotowujemy wprost.
- Delegated: least `UserAuthMethod-TAP.Read`; „This operation supports the following built-in roles, which provide only the least privilege necessary: **Authentication Administrator; Privileged Authentication Administrator.**” [spec: jw.]
- wariant rolą w D1: **Authentication Administrator @ AU** („for any non-admin user in the assigned administrative unit only” [docs: admin-units-assign-roles]); Privileged Authentication Administrator i Authentication Policy Administrator **nie skalują się do AU** (§3.2). **[do weryfikacji]** czy tor rol działa dla SP w app-only (dokument opisuje role przy *delegated*; dla application kolumna mówi o permissions) — „default rekomendowany” to app permission TAP-specific.

**Delete** [spec: https://learn.microsoft.com/en-us/graph/api/temporaryaccesspassauthenticationmethod-delete]: `DELETE /users/{id | userPrincipalName}/authentication/temporaryAccessPassMethods/{id}` → `204 No Content`; Application least `UserAuthenticationMethod.ReadWrite.All`, higher `UserAuthMethod-TAP.ReadWrite.All`; role delegated: Authentication Administrator, Privileged Authentication Administrator.

**Ograniczenia TAP** [docs: howto TAP]:

- policy: „Minimum lifetime | 1 hour | 10 – 43,200 Minutes (30 days)”; „Default lifetime | 1 hour”; „Maximum lifetime | 8 hours”; „**One-time use | False | True/False** … If you enforce one-time use in the TAP policy, all passes created in the tenant are one-time use.”; „Length | 8 | 8-48 characters”;
- „Each user can only have one TAP. […] If the existing TAP is valid, the admin can create a new TAP to override the existing valid TAP. If the existing TAP has expired, a new TAP overrides the TAP.”;
- replikacja: „It can take a few minutes for changes to replicate. Because of this, after a TAP is added to an account, it can take a while for the prompt to appear. For the same reason, after a TAP expires, users may still see a prompt for TAP.”;
- „An expired or deleted TAP can't be used for interactive or non-interactive authentication.”;
- goście: „Temporary Access Pass cannot be added to an external guest user.”;
- one-time TAP: „the user must complete the registration within 10 minutes of sign-in with the one-time TAP” (rejestracja metody passwordless).

PowerShell (właściciel): `New-MgUserAuthenticationTemporaryAccessPassMethod -UserId <up> -BodyParameter @{isUsableOnce=$true; lifetimeInMinutes=…}`; metadane: `Get-MgUserAuthenticationTemporaryAccessPassMethod`; usuwanie: `Remove-MgUserAuthenticationTemporaryAccessPassMethod -UserId <up> -TemporaryAccessPassAuthenticationMethodId <id>` [docs: howto TAP — bloki „PowerShell”].

**Throttling:** POST/DELETE na `users/{id}/authentication/…` → 1 RU / 1 write (identity path niewymieniony w tabeli kosztów) [spec: throttling jw.].

**Idempotencja:** create ma semantykę *override* (cytat wyżej) — automatyczny retry po niepewnym wyniku **nadmazuje** dotychczasowy TAP: bezpieczny dla konta, ale **unieważnia już wysłany link**. **[wniosek dla B2/B3]** zadanie „wydaj TAP” po sukcesie nigdy nie jest ponawiane automatycznie; 4xx poza „oknem AU” = awaria do rozpatrzenia; 5xx/429 → retry do pierwszego sukcesu.

**Kody:** 201 / 204 / 400 (złe parametry czasu) / 403 / 404 / 429 / 5xx [bazowe + spec].

**Odwracalność:** wartość TAP **nieodtwarzalna po	create** (UI: cyt. „can't view this value after”; API create zwraca raz); delete trwale likwiduje passcode. To fundamentalne dla mechaniki D3 (§6).

### 4.5. Wyłączenie konta (`accountEnabled=false`) — `PATCH /users/{id}`

To zwykły PATCH usera z `accountEnabled: false`. Minimalne uprawnienie ma dedykowany, wąski notes na stronie user-update:

> „**`User.EnableDisableAccount.All` + `User.Read.All` is the least privileged combination** of permissions to update the **accountEnabled** property.” [spec: https://learn.microsoft.com/en-us/graph/api/user-update]

Rola Entra: **User Administrator** / @ AU; wsparcie AU wprost w „Currently supported scenarios”: „**Administrative unit-scoped blocking and unblocking of user sign-ins ✅**” [docs: administrative-units, §3.1].

**[wniosek]** W modelu D1 nie nadajemy `User.EnableDisableAccount.All` (to alternatywa tenant-wide app permission); rola User Administrator @ AU wystarcza. Granularny wariant zostaje jako opcja na wypadek decyzji o rozdzieleniu „disable” od „edycja danych”. Stan docelowy dla REJECTED (B3) i końca członkostwa (B5): **wyłączenie, nie usuwanie** — odwracalne; usuwanie triala dopiero po okresie retencji (C1, D4).

**Throttling:** jak §4.2 (PATCH = 1 RU / 1 write). **Idempotencja:** pełna (ta sama wartość = no-op). **Kody:** jak §4.2. **Odwracalność:** pełna (`accountEnabled=true` przywraca).

### 4.6. Członkostwo w grupie — add member / remove member

**Add member** [spec: https://learn.microsoft.com/en-us/graph/api/group-post-members]:

> „Add a member to a security or Microsoft 365 group. **When using the API to add multiple members in one request, you can add up to only 20 members.**”

> „This request might have replication delays for groups that were recently created. When a group is created, it can take a short time for the object to fully replicate across Microsoft Entra ID directory replicas. During this window, requests to add members to the group might return a **`400 Bad Request`** error with the message: *\"The source resource object or one of the objects being referenced don't exist.\"* **Retry after a brief delay** — wait a few seconds and retry the request.” [spec: jw.]

Body: `{"@odata.id": "https://graph.microsoft.com/v1.0/users/{user-id}"}`; odpowiedź `204 No Content` [spec: jw.].

**Minimalne uprawnienie (add)** [spec: jw.]: dla member=user — delegated least **`GroupMember.ReadWrite.All`**, application **`GroupMember.ReadWrite.All`** (kolumna wyżej: `Directory.ReadWrite.All`, `Group.ReadWrite.All`). Role (delegated): „the signed-in user must also be assigned a supported Microsoft Entra role or a custom role with the `microsoft.directory/groups/members/update` role permission. The following least privileged roles are supported for this operation, **except for role-assignable groups**: Group owners; Directory Writers; **Groups Administrator**; Identity Governance Administrator; **User Administrator**; Exchange/SharePoint/Teams/Yammer Administrator — only for Microsoft 365 groups; Intune Administrator — only for security groups.” [spec: jw.]

> Do grup role-assignable: „To add members to a role-assignable group, the app must also be assigned the *RoleManagement.ReadWrite.Directory* permission and the calling user must be assigned a supported Microsoft Entra role. **Privileged Role Administrator** is the least privileged role that is supported for this operation.” [spec: jw.] — **nie nasz tor** (grupy organów MR nie są role-assignable).

**[wniosek]** W D1: **User Administrator @ AU** z grupami organów **umieszczonymi w AU** (§3.1: „an administrator scoped to the administrative unit can manage properties of the group, such as group name or **membership**”). Nie nadajemy `GroupMember.ReadWrite.All` (tenant-wide, see §2).

**Remove member** [spec: https://learn.microsoft.com/en-us/graph/api/group-delete-members]:

> „Remove a member from a group via the **members** navigation property. **You can't remove a member from groups with dynamic memberships.**” — `DELETE /groups/{id}/members/{member-id}/`**`$ref`** → `204 No Content`.

Uprawnienia i role jak przy add (least `GroupMember.ReadWrite.All`, role z `microsoft.directory/groups/members/update`: User Administrator itd.). **FATAL-uwaga (caution box, verbatim):**

> „If `/$ref` is not appended to the request and the calling app has permissions to manage the member object type, **the member object will also be deleted from Microsoft Entra ID**; otherwise, a `403 Forbidden` error is returned. For example, an app with both *GroupMember.ReadWrite.All* and *User.ReadWrite.All* permissions will delete a user. You can restore specific objects through the Restore deleted items API.” [spec: group-delete-members]

**[wniosek dla B2/B4]** klient Graph musi mieć **assert na sufiks `$ref` w delete member** + test WireMock sprawdzający kształt wywołania (ryzyko R4, §7).

- Do role-assignable groups przy remove: wymagane `RoleManagement.ReadWrite.Directory` + rola Privileged Role Administrator [spec: jw.].

**Throttling:** `GET groups/{id}/members` = **3 RU**; POST/DELETE na tym path → 1 RU / 1 write [spec: throttling jw.]. Przy nocnej rekoncyliacji B4 liczyć RU (GET członkostw to najdroższa pętla).

**Idempotencja:** add nie jest wprost idempotentny — **[unverified]** powtórne dodanie zwraca błąd (dokumentacja nie opisuje kodu; [community] praktyka: 400/409 Request_BadRequest, obiekt już jest członkiem). **[wniosek dla B2]** przed add — odczyt członkostwa (GET members, 3 RU; albo porównanie w rekoncyliacji B4); po niepewnym wyniku — drugi odczyt decyduje. Remove idempotentny *de facto*: 204/404 → stan docelowy osiągnięty (404 **[unverified]**).

**Kody:** 204; 400 (replikacja świeżej grupy — „Retry after a brief delay” [spec]; zły `@odata.id`); 403 (poza AU; rola-assignable); 404 (brak obiektu); 429/5xx [bazowe].

**Odwracalność:** pełna (add/remove); **bez sufiksu `$ref` delete usuwa użytkownika** (soft-delete 30 dni, ale zakłócenie produkcyjne) [spec caution].

### 4.7. Zmiana UPN / displayName (konwersja triala → konto członka, D4)

Zwykły `PATCH /users/{id | userPrincipalName}` na `userPrincipalName`, `displayName` (i opcjonalnie `givenName/surname/department`). Reguły UPN — te same co przy create (§4.1: znaki, zweryfikowana domena; `onPremisesImmutableId` dotyczy tylko federacji — tenant MR nie federuje [wniosek własny na podstawie cytatu §4.1]).

**Minimalne uprawnienie:** jak §4.2 — rola **User Administrator @ AU** (alternatywa app: `User.ReadUpdate.All`/`User.ReadWrite.All` [spec: user-update]).

**Adresy pocztowe przy zmianie UPN** — Graph nie radzi `proxyAddresses`:

> „Both **mail** and **proxyAddresses** can be retrieved through the GET user API. You can **update the mail** via the Update user API, **but can't update proxyAddresses through Microsoft Graph**.” [spec: https://learn.microsoft.com/en-us/graph/api/resources/user — za notatką researchową z 2026-11-22, fetch owocny do ~32 KB: „captured via search-engine snippet of the page” — trzymać tag [spec → snippet]]

**[wniosek + do weryfikacji na tenancie]** dla konwersji triala `99NNNN@` → członka `%06d@` + publiczny adres `imię.nazwisko@` (B3): sekwencja PATCH: (1) `userPrincipalName` → `%06d@mlodzirazem.org`; (2) `mail` → adres publiczny; (3) `employeeId` = numer członka. Czy Exchange Online automatycznie przeliczy primary SMTP po zmianie UPN — **[unverified]**, sprawdzić na dev-tenancie (jeśli nie — patched `mail` wystarczy dla adresacji, proxyAddresses utrzymuje EXO).

Ryzyka miękkie: sesje/tokeny zbudowane na starym UPN wygasają przy kolejnym odświeżeniu; historia czatu Teams / odnośniki OneDrive mogą przełączyć się na nowy UPN — **[community/unverified]**, bez cytatu — do sprawdzenia przy E2E B3.

**Throttling:** PATCH = 1 RU / 1 write [spec: jw.]. **Idempotencja:** naturalnie idempotentny; przy retry — najpierw GET po nowym UPN (200 → sprawdź `employeeId`), potem po starym (404 → konwersja zrobiona). **Kody:** jak §4.2 + duplikat UPN → 400 `ObjectConflict` [community, §4.1] — dla B3 liczone jako kolizja adresowa → alert (deterministyczny resolver z planu B3). **Odwracalność:** technicznie pełna, funkcjonalnie z efektami ubocznymi (powyżej).

---

## 5. Procedura dla właściciela — jednorazowa konfiguracja D1 (PowerShell, Microsoft.Graph)

> Wszystkie kroki uruchamia **właściciel** (rola Privileged Role Administrator / Global Administrator); Panel nigdy nie dostaje tych uprawnień. Sekrety (certyfikat aplikacji Panelu) pozostają po stronie właściciela — tu wyłącznie identyfikatory obiektów i role. Nie ma konta, na którym te czynności wykonuje agent.

**0. Instalacja / połączenie** [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-assign-roles — wymagania „Privileged Role Administrator”, „Microsoft Graph PowerShell module”]:

```powershell
Install-Module Microsoft.Graph -Scope CurrentUser
Connect-MgGraph -Scopes "RoleManagement.ReadWrite.Directory","AdministrativeUnit.ReadWrite.All","Application.Read.All","User.Read.All"
```

**1. Utworzenie AU „Członkowie MR” z dynamiczną regułą UPN** [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-members-dynamic — `New-MgDirectoryAdministrativeUnit -BodyParameter @{ membershipType='Dynamic'; membershipRule=…; membershipRuleProcessingState='On' }`; lista właściwości reguł: https://learn.microsoft.com/en-us/entra/identity/users/groups-dynamic-membership]:

```powershell
$params = @{
  displayName = "Członkowie MR"
  description = "Konta członków i kandydatów zarządzane przez Panel MR (D1)"
  membershipType = "Dynamic"
  membershipRule = "(user.userType -eq 'Member' and user.userPrincipalName -match '^\d{6}@mlodzirazem\.org$')"
  membershipRuleProcessingState = "On"
}
$au = New-MgDirectoryAdministrativeUnit -BodyParameter $params
$au.Id
```

*(Regułę validate'ować w rule builderze w Entra admin center — ostrzeżenie CAUTION o `-match` dotyczy wydajności wielkich tenantów, przy ~800 obiektach pomijalne; escapowanie `\d`/`\.` potwierdzić w builderze)*

**2. Obiekt SP Panelu** (`PANEL_ENTRA_CLIENT_ID` — identyfikator aplikacji Panelu):

```powershell
$sp = Get-MgServicePrincipal -Filter "appId eq '<GUID aplikacji Panelu>'"
$sp.Id   # principalId dla nadania ról
```

**3. Directory Readers (tenant-wide — wymagane, §3.4):**

```powershell
$dirReaders = Get-MgRoleManagementDirectoryRoleDefinition -Filter "displayName eq 'Directory Readers'"
New-MgRoleManagementDirectoryRoleAssignment -DirectoryScopeId '/' -PrincipalId $sp.Id -RoleDefinitionId $dirReaders.Id
```

**4. Role AU dla SP Panelu** [docs: admin-units-assign-roles („Security principals… Service principals”), docs: manage-roles-portal (`directoryScopeId = "/administrativeUnits/<ID>"`), spec: rbacapplication-post-roleassignments; alternatywa UI: Entra ID > Roles & admins > Admin units > [AU] > Roles and administrators]:

```powershell
foreach ($roleName in @('User Administrator','License Administrator','Authentication Administrator')) {
  $role = Get-MgRoleManagementDirectoryRoleDefinition -Filter ("displayName eq '" + $roleName + "'")
  New-MgRoleManagementDirectoryRoleAssignment `
     -DirectoryScopeId ("/administrativeUnits/" + $au.Id) `
     -PrincipalId $sp.Id `
     -RoleDefinitionId $role.Id
}
```

**5. Uprawnienie aplikacyjne Panelu (jedno!):** `UserAuthMethod-TAP.ReadWrite.All` (Graph application permission + admin consent) — Microsoft Entra admin center > App registrations > Panel MR > API permissions > Microsoft Graph > Application permissions. **Nie dodawać** `User.ReadWrite.All` / `Directory.ReadWrite.All` / `LicenseAssignment.ReadWrite.All` / `GroupMember.ReadWrite.All` — te operacje pokrywają role AU (zasada §2: uprawnienia się sumują). **[do decyzji z wynikiem smoke'a §5 pkt 7]** czy zachować obecne permission lookupu (`User.Read.All`?), czy zastąpić je rolą Directory Readers — zamienić dopiero po zielonym smoke lookupu.

**6. Włączenie metody TAP w policy** (Authentication Policy Administrator; D3 wymaga one-time use):

```powershell
Connect-MgGraph -Scopes "Policy.ReadWrite.AuthenticationMethod"
$params = @{
  "@odata.type" = "#microsoft.graph.temporaryAccessPassAuthenticationMethodConfiguration"
  state = "enabled"
  includeTargets = @(@{ targetType = "group"; id = "<id grupy obejmującej członków AU>" })
  isUsableOnce = $true        # D3: jednorazowy kod
}
# cmdlet z Microsoft.Graph.Identity.SignIns (navigation po authenticationMethodsPolicy;
# identyfikatorem konkretnej metody jest "TemporaryAccessPass"):
Update-MgPolicyAuthenticationMethodPolicyAuthenticationMethodConfiguration `
  -AuthenticationMethodConfigurationId "TemporaryAccessPass" -BodyParameter $params
# odpowiednik REST: PATCH /policies/authenticationMethodsPolicy/authenticationMethodConfigurations/TemporaryAccessPass
# [spec: https://learn.microsoft.com/en-us/graph/api/temporaryaccesspassauthenticationmethodconfiguration-update]
# [cmdlet: https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.identity.signins/update-mgpolicyauthenticationmethodpolicyauthenticationmethodconfiguration?view=graph-powershell-1.0]
```

**7. Weryfikacja smoke (zanim B3 dostanie flagę `panel.directory.provisioning.enabled=true`):**

```powershell
# a) przypisania ról SP: trzy z directoryScopeId=/administrativeUnits/{auId} + jeden /' (Directory Readers)
Get-MgRoleManagementDirectoryRoleAssignment -Filter ("principalId eq " + $sp.Id + "")

# b) create testowy (dev/stage): czy rola AU sama wystarcza do POST /users bez app permission (R6)
$user = New-MgUser -DisplayName "Smoke B1" -UserPrincipalName "999999@mlodzirazem.org" `
  -MailNickname "999999" -AccountEnabled:$true -UsageLocation "PL" -PasswordProfile @{ Password = '<losowe-od-wlasciciela>' }
# c) po kilku minutach: user w AU (regula dynamiczna):
Get-MgDirectoryAdministrativeUnitMember -AdministrativeUnitId $au.Id -All
# d) sprzątanie: Remove-MgUser -UserId $user.Id   (soft-delete 30 dni)
```

**8. Rollback (demontaż całej konfiguracji):** usunąć przypisania ról (`Remove-MgRoleManagementDirectoryRoleAssignment -UnifiedRoleAssignmentId <id>`), cofnąć admin consent `UserAuthMethod-TAP.ReadWrite.All`, usunąć AU („Create or delete administrative units” — usuwanie AU nie usuwa użytkowników [docs: https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-manage]). Wszystko odwracalne; **cofnięcie Directory Readers reaktywuje brak odczytu w lookup** — patrz §3.4.

### 5.1. Konta sztabu poza AU i grupy organów

**[wniosek własny]** konta sztabu/adminów nie spełniają reguły AU (UPN nieliczbowe) → nie wchodzą w zakres ról Panelu. Gruppeny organów (B4) **nie mogą być dodane do dynamicznej AU** („Administrative units with rules… for groups are currently not supported” [docs: admin-units-members-dynamic]). **Decyzja dla B4/właściciela:** rekomendujemy **drugą AU „Grupy organów MR"** typu `Assigned` (tworzy ją właściciel analogicznie, bez reguły: `New-MgDirectoryAdministrativeUnit -BodyParameter @{ displayName="Grupy organów MR"; membershipType="Assigned" }`), członkami są **grupy**; rolą User Administrator/Groups Administrator nadaną SP na tej AU Panel zarządza członkostwami. Alternatywa (rola tenant-wide Groups Administrator) rozszerzałaby zakres na wszystkie grupy — sprzeczna z duchem D1. UWAGA: w AU Assigned ręczne operacje członkostwa AU (dodanie grupy) wykonuje **właściciel** (PRA), nie Panel (§3.3).

---

## 6. D3 — mechanika doręczenia TAP bez zapisu w bazie/logach (propozycja)

Fakty uziemiające (§4.4): wartość TAP dostępna **wyłącznie w odpowiedzi 201**; ponowne create nadpisuje; UI/API nie odsłania wartości po fakcie; passcode jednorazowy (policy `isUsableOnce=true`), żyje podług `lifetimeInMinutes`; użytkownik musi być w policy TAP.

**Rekomendowany przepływ (B2/B3; wzorzec = magic-link e-deklaracji — token hash-only, 1 żywa sesja):**

1. Zadanie „wydaj TAP" enqueue'owane po `ACCEPTED` (członek) / `DECLARATION_RECEIVED` (trial, D4) tworzy TAP przez Graph → `temporaryAccessPass` **istnieje tylko w pamięci żądania** workera.
2. Panel buduje **szyfrogram**: `payload = AES-GCM-Encrypt(kPanel, TAP + exp)` — klucz w konfiguracji/secret, nigdy w bazie; plaintext TAP nie ląduje ani w bazie, ani w logach (audyt jak zawsze: **nazwy pól**, §0.5 AGENTS).
3. Link jednorazowy `https://panel…/konto/start?c=<ciphertext>", wstawiony do maila powitalnego (TransactionalMail z M5 — guardy domeny nadawcy, limity TERRL). W bazie wiersz doręczenia z **HMAC-hashem szyfrogramu** jako kluczem, `expiresAt` (>= lifetime TAP), `consumedAt`, statusy SENT/CONSUMED/EXPIRED.
4. Odbiorca klika → endpoint odszyfrowuje, renderuje TAP, **znakuje consumption** (unikalny indeks na hashu) → drugi odczyt = jednolity ekran „wygasło" (anti-enumeration jak w e-deklaracji). Audyt zdarzenia: klucze `[case, consumedAt]` — wartości nie ma.
5. Reissue (TAP wygasł/nieużyty): jawnie, od osoby z uprawnieniem — nowe zadanie create (nadpisuje stary TAP [spec: §4.4]), nowy link, stary hash oznaczony `supersededBy`.
6. ZO widzi w Panelu wyłącznie status: „wysłano / odebrano / wygasło" — nigdy wartości [zgodne z D3].

**[wniosek]** Dlaczego link, a nie sam kod mailem: wartość w mailu = trwała kopia w skrzynkach — rozjazd z D3 („sam TAP nigdy w bazie ani w logach"); link z szyfrogramem daje jednorazowy odczyt + wygasanie + brak kopii poza chwilą odszyfrowania. Alternatywna infrastruktura Password Pusher (użyta w ręcznym mailu `mail-templates/m365-onboarding/`) — dla w pełni zautomatyzowanego toru zbędna; szablon powitalny dostaje `{{LINK_TAP}}` zamiast kodu. Tryb trial (D4) — ta sama mechanika na e-mail kandydata; trial nie ma `employeeId` członka i nie loguje się do Panelu jako członek (definicja B3).

---

## 7. Ryzyka dla B2 (top 6) + lekcja RBAC-for-Applications vs role Entra

| # | Ryzyko | Dlaczego (źródło) | Mitygacja |
|---|---|---|---|
| R1 | **AU-scope nie działa „sam": SP wymaga Directory Readers tenant-wide; każde nadane application permission sumuje się do tenant-wide** | §3.4 cytat wprost; §2 — lekcja M5 („uprawnienia się sumują"); permissions-overview: „The application can access *any* data that the permission is associated with." | nadać **wyłącznie** role AU + TAP-specific permission + Directory Readers; osobny smoke lookupu po zdjęciu `User.Read.All`; checklista przeglądu uprawnień aplikacji przy każdym wdrożeniu (B2 security review) |
| R2 | **odczyt PII tenant-wide przez Directory Readers** | rola daje read na wszystkie obiekty katalogu (UPN/e-mail/employeeId wszystkich) — cena D1 | RODO: Panel czyta tylko po `id` z claimu (jak dziś lookup); żadnych list/eksportów userów w API; audyt; (opcja) restricted management AU chroni przed adminami tenantowymi [docs: admin-units-faq-troubleshoot — restricted management AUs] — **[do decyzji właściciela]** |
| R3 | **okno 403 po create (dynamic rule)** — assignLicense/PATCH na koncie tuż po create może dostać 403, zadania w pętli | replikacja reguły „can take a few minutes" (§3.3) | worker B2: 403 na operacji na świeżo utworzonym koncie = retry z backoff (cap ~15 min); test WireMock symulujący 403→success; kolejność zadań: create → (wait) → licencja/TAP |
| R4 | **DELETE member bez sufiksu `$ref` usuwa konto użytkownika** | caution box §4.6 verbatim [spec: group-delete-members] | assert w kliencie na URL; test jednostkowy kształtu zapytania; rekoncyliacja B4 robi tylko `$ref` |
| R5 | **TAP leak do logów** (ciało odpowiedzi 201 z przypadkowym serialize) | wartość dostępna tylko w create [docs: howto TAP]; log TRACE może zebrać body | filtr/redactor na body odpowiedzi klienta TAP; test „TAP nie pojawia się w logach i tabelach" [plan §5 ryzyka]; audyt zawsze nazwy pól |
| R6 | **create-user bez app permission może zwrócić 403** — zatrzymanie przyjęcia w runtime | brak jednoznacznego zdania dokumentacji o create-by-AU-role (§4.1) | smoke-test przed B3 (§5 pkt 7); fallback = nadanie minimalnego `User.Create` (tworzenie-only) [spec: user-post-users]; 403 na create = blocker konfiguracyjny (alert ownera), nie cień kandydata |

**Lekcja M5 przeniesiona na katalog:** w Exchange scopes to osobny moduł RBAC for Applications (resource scope na skrzynki — cyt. §2); w Entra takiego „resource scope" dla Graph-app-permissions **nie ma** — ich zawężeniem są tylko role+AU. Dlatego w katalogu trzymamy się ról AU (D1) zamiast powielać pattern z M5. **Nigdy nie łączyć obu torów dla tej samej operacji:** jedna katalogowa application permission (np. `User.ReadWrite.All`) dewaluuje cały konstruk AU.

---

## 8. Tabela zbiorcza

| Operacja | Endpoint | Minimalne uprawnienie (rekomendowane D1) | AU-scope możliwy? | Throttling (identity bucket) | Idempotencja / duplikacja | Odwracalność |
|---|---|---|---|---|---|---|
| Create user | `POST /users` | rola **User Administrator @ AU** (+ Directory Readers na SP); fallback app **`User.Create`** | ✔ [do smoke: R6] | POST 1 RU / 1 write; write-quota 3 000/2 min 30 s (app+tenant) | nieidempotentny; pre-check GET po UPN; duplikat → 400 `ObjectConflict` [community] | soft-delete 30 dni [docs] |
| `employeeId` / props | `PATCH /users/{id}` | rola **User Administrator @ AU**; alternative app: `User.ReadUpdate.All` | ✔ | PATCH 1 RU / 1 write | idempotentny (no-op przy tej samej wartości) | pełna |
| Licencja | `POST /users/{id}/assignLicense`; `GET /subscribedSkus` | rola **License Administrator @ AU**; alternative app `LicenseAssignment.ReadWrite.All` | ✔ („within the administrative unit only") | assignLicense 1 RU/1 write; subscribedSkus 3 RU | nieidempotentny wprost; check-first `$select=assignedLicenses`; wymóg `usageLocation` | pełna (removeLicenses) |
| TAP create | `POST /users/{id}/authentication/temporaryAccessPassMethods` | **app `UserAuthMethod-TAP.ReadWrite.All`**; rola-variant Authentication Administrator @ AU [do weryfikacji app-only] | ✗ dla app permission (tenant-wide); ✔ dla roli AU | POST 1 RU / 1 write | create = **override** istn. TAP — nie ponawiać po sukcesie | wartość nieodtwarzalna po create |
| TAP delete | `DELETE /users/{id}/authentication/temporaryAccessPassMethods/{id}` | jw. | jw. | DELETE 1 RU / 1 write | idempotentny de facto (204/404 = cel) | nie (passcode zlikwidowany) |
| Wyłączenie konta | `PATCH /users/{id}` `accountEnabled=false` | rola **User Administrator @ AU**; alternative granularne app `User.EnableDisableAccount.All` + `User.Read.All` | ✔ („blocking sign-ins" wprost) | PATCH 1 RU / 1 write | idempotentny | pełna |
| Add member grupy | `POST /groups/{id}/members/$ref` | rola **User Administrator @ AU** (grupa w AU); alternative app `GroupMember.ReadWrite.All` | ✔ (grupa musi być w AU) | POST 1 RU / 1 write; GET members 3 RU | pre-check listy; powtórka → 400 [unverified]; świeża grupa → 400 „don't exist" + retry [spec] | pełna |
| Remove member | `DELETE /groups/{id}/members/{id}/`**`$ref`** | jw. | ✔ | DELETE 1 RU / 1 write | 204/404 → cel osiągnięty [unverified 404] | pełna; **bez `$ref` = usuwa usera [spec caution]** |
| Zmiana UPN / displayName | `PATCH /users/{id}` | jak employeeId (User Administrator @ AU) | ✔ | PATCH 1 RU / 1 write | idempotentny; duplikat UPN → 400 [community] | technicznie pełna; efekty uboczne [unverified] |
| (bazowy) Directory Readers na SP | `POST /roleManagement/directory/roleAssignments` z `/` | **Directory Readers** — obowiązkowy | ✗ („It isn't currently possible to assign directory read permissions scoped to an administrative unit.") | n/c | n/c | pełna |

Grupa błędów wspólna [docs: graph/errors]: 400/403/404/409 (retry po	delay)/429 (Retry-After)/503/504.

---

## 9. Pozycje otwarte (dla B2/B3/vłaściciela)

1. **[do weryfikacji — smoke przed B3]** create-user przez SP z rolą AU bez app permission (R6); jeśli 403 — decyzja: nadasz `User.Create`?
2. **[do weryfikacji]** czy `GET/LIST temporaryAccessPassMethods` zwraca plaintext pass przy odczycie (UI zakazuje, API nie przebadane przez B1 — strona list twierdzi „will only return a single object in the collection…", wartość niepotwierdzona [https://learn.microsoft.com/en-us/graph/api/authentication-list-temporaryaccesspassmethods]).
3. **[do weryfikacji]** automatyczne przeliczanie `mail`/primary SMTP po zmianie UPN w EXO (§4.7).
4. **[decyzja B4]** druga AU „Assigned" dla grup organów (rekomendacja §5.1) vs rola tenant-wide Groups Administrator.
5. **[licencje]** wartość `panel.directory.default-license-sku` z D2 (300 BB + F1 — cyt. §4.3 + panel-transition.md §4) — do ustalenia przed B3.
6. **[pytanie do właściciela]** czy dla AU członków włączyć ochronę restricted management AU („restricted management administrative units" — ochrona przed adminami tenantowymi).

---

## Aneks A — lista źródeł (wszystkie pobrane/zweryfikowane 2026-11-22/24)

- https://learn.microsoft.com/en-us/graph/permissions-overview — app-only „any data", tor RBAC-role, consent tylko PRA/GA.
- https://learn.microsoft.com/en-us/graph/permissions-reference — „Directory permissions grant broad access…".
- https://learn.microsoft.com/en-us/graph/errors — kody 400/403/404/409/429/5xx.
- https://learn.microsoft.com/en-us/graph/throttling + https://learn.microsoft.com/en-us/graph/throttling-limits (identity RU/write quotas); include źródłowy: https://github.com/microsoftgraph/microsoft-graph-docs-contrib/blob/main/includes/throttling-identity-and-access.md.
- https://learn.microsoft.com/en-us/graph/api/user-post-users — POST /users, wymagane pola, UPN, least `User.Create`, 201.
- https://learn.microsoft.com/en-us/graph/api/user-update — PATCH, `User.ReadUpdate.All`, granularne scenariusze (accountEnabled, otherMails, passwordProfile…).
- https://learn.microsoft.com/en-us/graph/api/user-assignlicense — assignLicense, least `LicenseAssignment.ReadWrite.All`.
- https://learn.microsoft.com/en-us/graph/api/subscribedsku-list — SKU, `$select` only.
- https://learn.microsoft.com/en-us/microsoft-365/enterprise/create-user-accounts-with-microsoft-365-powershell — `New-MgUser`, usageLocation wymagane do licencji.
- https://learn.microsoft.com/en-us/microsoft-365/enterprise/assign-licenses-to-user-accounts-with-microsoft-365-powershell — UsageLocation, `Set-MgUserLicense`.
- https://learn.microsoft.com/en-us/graph/api/authentication-post-temporaryaccesspassmethods — TAP create (app perms, override).
- https://learn.microsoft.com/en-us/graph/api/temporaryaccesspassauthenticationmethod-delete — TAP delete, 204.
- https://learn.microsoft.com/en-us/graph/api/temporaryaccesspassauthenticationmethodconfiguration-update — TAP policy, `Policy.ReadWrite.AuthenticationMethod`.
- https://learn.microsoft.com/en-us/entra/identity/authentication/howto-authentication-temporary-access-pass — TAP policy/roles/lifetime, „can't view this value after", replikacja, goście.
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/administrative-units — AU overview, groups-in-AU, licencja P1, tablice scenariuszy.
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-assign-roles — tabela ról AU, SP + Directory Readers, wyjątek „nonadministrator users", PowerShell.
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-members-add — dodawanie do AU ręczne (PS + Graph).
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-members-dynamic — dynamic AU (rule, PS, licencjonowanie P1, tylko users/devices).
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/admin-units-faq-troubleshoot — PRA do create AU, restricted AUs, replikacja reguły.
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/manage-roles-portal — semantyka `/administrativeUnits/<ID>`.
- https://learn.microsoft.com/en-us/entra/identity/role-based-access-control/permissions-reference — tabela ról i template IDs.
- https://learn.microsoft.com/en-us/graph/api/rbacapplication-post-roleassignments — unifiedRoleAssignment, formaty directoryScopeId.
- https://learn.microsoft.com/en-us/graph/api/group-post-members — add member (20/req, replikacja 400+retry), permissions/roles.
- https://learn.microsoft.com/en-us/graph/api/group-delete-members — remove member, dynamic groups, caution `$ref`.
- https://learn.microsoft.com/en-us/graph/api/team-put-teams — „Create team from group", 404 replication, retry 3×10 s, „SharePoint provisioning doesn't occur in real time".
- https://learn.microsoft.com/en-us/entra/identity/users/groups-dynamic-membership — właściwości reguł (`userPrincipalName`), CAUTION `-match`.
- https://learn.microsoft.com/en-us/microsoft-365/business-premium/m365bp-faqs — limit 300 miejsc Business family.
- https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac — RBAC for Applications (Exchange) — kontrast do lekcji M5.
- https://learn.microsoft.com/en-us/graph/best-practices-concept — soft-delete ~30 dni.
- PowerShell: https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.users/new-mguser ; https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.identity.directorymanagement/new-mgdirectoryadministrativeunit ; …/new-mgdirectoryadministrativeunitmemberbyref ; https://learn.microsoft.com/en-us/powershell/module/microsoft.graph.identity.signins/update-mgpolicyauthenticationmethodpolicyauthenticationmethodconfiguration (patch konfiguracji metody, id = "TemporaryAccessPass").
- [community]: https://learn.microsoft.com/en-us/answers/questions/732338/impossible-to-add-user-in-administrative-unit-with (AU-scoped UserAdmin nie dodaje do AU), https://learn.microsoft.com/en-us/answers/questions/1099166/how-to-use-try-catch-to-handle-error-on-new-azurea (ObjectConflict UPN), https://learn.microsoft.com/en-us/answers/questions/2245101/another-object-with-the-same-value-for-property-us (string błędu duplikatu), https://stackoverflow.com/questions/62406629 (filtrowanie eq po employeeId).
- Wewnętrzne: `PLAN-WORKFLOW.md` §2–§3 (D1–D4, B1/B2/B3/B4); DELTA `panel-teams-app/AGENTS.md` (M5: „uprawnienia się sumują", RBAC for Applications); `/tmp/user-provisioning-research/panel-transition.md` (matryca faz, TERRL, warianty licencji) i `notes/03-graph-powershell.md` (pełne cytaty POST /users, assignLicense, throttling, New-MgUser — zweryfikowane 2026-11-22); `mail-templates/m365-onboarding/` (miejsce `{{LINK_TAP}}`).

## Aneks B — minimal dla B4: Entra group vs Teams team

- Członkostwo w zespole Teams jest **członkostwem grupy**: „Create team from group — Create a new team under a group. In order to create a team, the group must have at least one owner." [spec: https://learn.microsoft.com/en-us/graph/api/team-put-teams]. Zarządzanie członkostwem idzie przez **grupę** (`POST/DELETE /groups/{id}/members/$ref`, §4.6) — team odziedziczy skład; Teams nie musi być otwarte.
- Zespół zakłada się **raz, ręcznie** (D5: „Panel zarządza członkostwem istniejących grup/zespołów; zakładanie zespołów zostaje ręczne wg szablonów").
- Czasy propagacji: „If the group was created less than 15 minutes ago, calls to create a team might fail with a `404`… retry up to three times, with a 10-second delay" oraz „SharePoint provisioning doesn't occur in real time, it's a background process." [spec: jw.] — B4 nie dotyczy (team istnieje), ale uwaga przy świeżo zakładanych organach w UAT.

*Koniec dokumentu B1. Przekazane do B2 jako wejście.*
