# Model organów i funkcji wg statutu 2026 (dane)

Stan danych po migracji modelu organów (A4a). Źródło: statut z 3.10.2026, decyzje D12 i G-2.

## Zmiany względem modelu 2024

| element | zmiana | podstawa |
|---|---|---|
| Rada Krajowa (`NATIONAL_COUNCIL`) | nowy organ krajowy; Prezydium to typy funkcji w RK, nie osobny organ | art. 14, 23–24 |
| 17 organów „Osoba Skarbnicza Okręgu” | usunięte; skarbnik to funkcja `TREASURER` w ZO (grupa „District treasurer”, patrz niżej) | art. 43 ust. 3, D12 |
| ZK | dodany `MEMBER` (domyślny), usunięty `VICE_CHAIR` | art. 22 ust. 1, D12 |
| ZO | dodany `MEMBER` (domyślny), usunięty `VICE_COORDINATOR` | art. 43 ust. 2, D12 |
| zarząd komisaryczny | limity z 2024 r. zastąpione przez 6 miesięcy bez przedłużeń (≤ 184 dni); dokładne 186 dni należy do kodu (A4b) | art. 44 |

Migracja przerywa się, gdy cokolwiek (także zakończony lub unieważniony mandat) odwołuje się do usuwanego
elementu. Historii nie przenosimy ani nie nadpisujemy automatycznie.

Limitów liczebności nie ma w bazie: to stałe statutowe egzekwowane w kodzie wg (rodzaj organu, kod funkcji).

## Rada Krajowa

RK jest w Panelu organem czysto informacyjnym (decyzja właściciela z 25.11.2026): jej funkcje, także w Prezydium, to
same etykiety bez uprawnień, limitów i reguł, a art. 18 ust. 1 pkt 7 i kompetencje z art. 23 nie są kodowane.

## Uprawnienia skarbnika ZO (decyzja właściciela z 25.11.2026)

Skarbnik jest członkiem ZO (art. 43 ust. 3), więc grupa LOCAL „District treasurer” obejmuje wszystko, co grupa
„District board”, oprócz tego, co miała wcześniej. Zakres to nadal tylko własny okręg. Migracja kopiuje uprawnienia
jednorazowo; kto doda uprawnienie grupie „District board”, musi dodać je też skarbnikowi (pilnuje tego
`SeedMigrationTest`).

## Pary niezgodne i wyjątek jednoosobowego ZO (G-2)

* 10 par w grupie {ZK, KSK, KKR, KKW, ZO} (art. 18 ust. 1 pkt 6). RK nie należy do tej grupy.
* Pary KSK/KKR/KKW × ZO nie są egzekwowane, dopóki ZO ma jedną osobę. To **polityka właściciela, nie przepis
  statutu**: statut nie przewiduje wyjątku, ale dopuszcza jednoosobowy ZO (art. 43 ust. 1 pkt 1, ust. 4), więc bez
  wyjątku nikt z najmniejszych okręgów nie mógłby wejść do KKR, KSK ani KKW.
* ZK × ZO nie ma wyjątku.
