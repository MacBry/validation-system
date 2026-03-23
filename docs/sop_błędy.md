# SOP: Postępowanie w przypadku błędów i incydentów (VCC-SOP-02)

## 1. Cel i Zakres
Celem procedury jest określenie ścieżki postępowania w przypadku wystąpienia błędów systemowych, awarii infrastruktury lub wykrycia błędnie wprowadzonych danych pomiarowych.

---

## 2. Klasyfikacja Incydentów
1. **NISKA**: Błędy kosmetyczne interfejsu, literówki w raportach.
2. **ŚREDNIA**: Problem z dostępem dla pojedynczego użytkownika, błędy podczas importu pojedynczego pliku `.vi2`.
3. **WYSOKA**: Brak dostępu do systemu dla wszystkich użytkowników, utrata danych, błędy w obliczeniach statystycznych.

---

## 3. Ścieżka Postępowania

### 3.1 Zgłaszanie błędu
Użytkownik po wykryciu błędu zobowiązany jest do:
1. Wykonania zrzutu ekranu błędu (jeśli dotyczy).
2. Spisania numeru walidacji/urządzenia, przy którym wystąpił problem.
3. Przekazania informacji do Administratora Systemu.

### 3.2 Analiza techniczna
Administrator Systemu w celu diagnozy:
1. Sprawdza **Audit Log** systemu pod kątem ostatnich zmian.
2. Weryfikuje logi serwera Spring Boot (Actuator).
3. Sprawdza dostępność bazy danych oraz serwera Redis.

### 3.3 Korekta błędnych danychpomiarowych
W przypadku wykrycia błędu po zatwierdzeniu walidacji (`APPROVED`):
1. Administrator lub osoba z rolą `Approver` musi odrzucić walidację (`REJECT`), co przywraca ją do stanu edycji.
2. Każda zmiana w tym procesie jest rejestrowana w Audit Trail.
3. Należy dodać komentarz uzasadniający korektę.

---

## 4. Plan przywracania po awarii (Disaster Recovery)
- System VCC posiada automatyczny backup bazy danych wykonywany codziennie o godzinie 01:00.
- W przypadku awarii krytycznej, Administrator przywraca ostatni spójny backup na nowej instancji serwera.

---

## 5. Dokumentacja korekt
Wszystkie korekty danych wpływające na wyniki walidacji muszą zostać udokumentowane w formie noty służbowej przypiętej do wydrukowanego raportu z walidacji, zgodnie z lokalnymi wymogami jakościowymi.
