# SOP: Zarządzanie Użytkownikami i Uprawnieniami (VCC-SOP-01)

## 1. Cel i Zakres
Celem procedury jest zapewnienie bezpiecznego i kontrolowanego dostępu do systemu VCC. Procedura dotyczy wszystkich użytkowników aplikacji w każdej z firm (Multi-tenancy).

---

## 2. Role i Odpowiedzialności
- **Administrator Systemu**: Odpowiedzialny za tworzenie kont administratorów firm i nadzór techniczny.
- **Administrator Firmy**: Odpowiedzialny za tworzenie kont użytkowników wewnątrz własnej organizacji i przypisywanie im działów.
- **Właściciel Systemu**: Odpowiedzialny za zatwierdzanie wniosków o nadanie uprawnień specjalnych (`Approver`).

---

## 3. Procedura nadawania uprawnień

### 3.1 Utworzenie konta
1. Administrator przechodzi do sekcji **Zarządzanie Użytkownikami**.
2. Wypełnia formularz danymi użytkownika (Imię, Nazwisko, E-mail, Nazwa użytkownika).
3. Wybiera rolę bazową (`ROLE_USER`, `ROLE_APPROVER` itp.).

### 3.2 Przypisanie Działów (Multi-tenancy)
1. Każdy użytkownik musi zostać przypisany do co najmniej jednego **Działu** (`Department`).
2. Bez przypisanego działu, użytkownik po zalogowaniu nie będzie widział żadnych urządzeń ani walidacji.

### 3.3 Aktywacja konta
1. Po utworzeniu konta, system generuje tymczasowe hasło.
2. Użytkownik przy pierwszym logowaniu jest zobligowany do zmiany hasła na własne.

---

## 4. Procedura blokowania konta
- **Automatyczna blokada**: Po 5 nieudanych próbach logowania konto zostaje zablokowane na 30 minut.
- **Manualna blokada**: W przypadku odejścia pracownika, Administrator ma obowiązek bezzwłocznie odznaczyć pole `Enabled` w profilu użytkownika.

---

## 5. Przegląd uprawnień
Raz na kwartał Administrator Firmy zobowiązany jest do przeprowadzenia audytu aktywnych kont i weryfikacji, czy zakres przypisanych działów jest nadal zgodny z obowiązkami służbowymi pracowników.
