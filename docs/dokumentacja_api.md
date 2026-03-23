# Dokumentacja API: Validation Cold Control (VCC)

## 1. Informacje ogólne
Obecna wersja systemu VCC wykorzystuje głównie model SSR (Server-Side Rendering) z Thymeleaf. Jednakże, kluczowe funkcje dynamiczne są obsługiwane przez dedykowane punkty końcowe REST i AJAX.

---

## 2. Powiadomienia (Notification API)

### 2.1 Pobieranie wszystkich powiadomień
Zwraca listę aktywnych alertów dla zalogowanego użytkownika (walidacje, wzorcowania, hasła).

- **URL**: `/api/notifications`
- **Metoda**: `GET`
- **Autoryzacja**: Wymagana (Zalogowany użytkownik)
- **Format odpowiedzi**: `JSON`

**Przykładowa odpowiedź:**
```json
[
  {
    "type": "VALIDATION",
    "title": "Zbliża się termin walidacji",
    "message": "Urządzenie CH-01 wymaga walidacji za 15 dni.",
    "link": "/measurements/1",
    "daysLeft": 15,
    "severity": "WARNING"
  },
  {
    "type": "PASSWORD",
    "title": "Hasło wygasa",
    "message": "Twoje hasło wygaśnie za 3 dni.",
    "link": "/profile/change-password",
    "daysLeft": 3,
    "severity": "DANGER"
  }
]
```

---

## 3. Wyszukiwanie (Search AJAX API)

### 3.1 Wyszukiwanie globalne
Zwraca fragment HTML z wynikami wyszukiwania urządzeń, rejestratorów i walidacji.

- **URL**: `/search`
- **Metoda**: `GET`
- **Parametry**: 
    - `q` (string, min. 2 znaki): Fraza wyszukiwania.
- **Odpowiedź**: `HTML (Thymeleaf Fragment)`

**Przeznaczenie**: Wykorzystywane przez dynamiczne pole wyszukiwania na Dashboardzie do natychmiastowego wyświetlania wyników bez przeładowania całej strony.

---

## 4. Przyszły rozwój API
System jest przygotowany pod rozbudowę o pełne sterowanie przez REST API w celu:
- Integracji z zewnętrznymi systemami LIMS/ERP.
- Obsługi przez aplikacje mobilne.
- Automatycznego importu danych z systemów chmurowych (Cloud-to-Cloud).
