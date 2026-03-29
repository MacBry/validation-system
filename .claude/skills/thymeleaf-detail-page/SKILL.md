---
name: thymeleaf-detail-page
description: Build Thymeleaf detail pages with sections, tables, modals, and edit capabilities
context: fork
agent: frontend-saas-specialist
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# Thymeleaf Detail Page Builder

Generate **detail page templates** with read-only sections, data tables, modals, and action buttons using VCC design system.

## Detail Page Architecture

### 1. Detail Page Structure

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      lang="pl">

<head>
    <meta charset="UTF-8">
    <title th:text="${coolingDevice.name}">Device Details</title>
    <link rel="stylesheet" th:href="@{/css/style-saas.css}">
</head>

<body class="vcc-page">

<!-- Sidebar Navigation -->
<aside th:replace="~{fragments/sidebar :: sidebar('devices')}"></aside>

<main class="vcc-main">

    <!-- Breadcrumb Navigation -->
    <nav class="vcc-breadcrumb">
        <a th:href="@{/}" class="vcc-breadcrumb__link">Strona główna</a>
        <span class="vcc-breadcrumb__separator">›</span>
        <a th:href="@{/devices}" class="vcc-breadcrumb__link">Urządzenia</a>
        <span class="vcc-breadcrumb__separator">›</span>
        <span class="vcc-breadcrumb__current" th:text="${coolingDevice.name}"></span>
    </nav>

    <!-- Detail Header: Title + Status + Actions -->
    <div class="vcc-detail-header">
        <div class="vcc-detail-header__title-group">
            <h1 class="vcc-detail-header__title" th:text="${coolingDevice.name}"></h1>
            <span class="vcc-status vcc-status--active" th:text="${coolingDevice.status}"></span>
        </div>
        <div class="vcc-detail-header__actions">
            <sec:authorize access="hasRole('ADMIN')">
                <a th:href="@{/devices/{id}/edit(id=${coolingDevice.id})}"
                   class="vcc-btn vcc-btn--primary">Edytuj</a>
            </sec:authorize>
            <form th:action="@{/devices/{id}/delete(id=${coolingDevice.id})}"
                  method="POST"
                  style="display: inline;">
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
                <button type="submit" class="vcc-btn vcc-btn--danger"
                        onclick="return confirm('Czy na pewno?');">Usuń</button>
            </form>
            <a th:href="@{/devices}" class="vcc-btn vcc-btn--secondary">Wróć</a>
        </div>
    </div>

    <!-- Content Area -->
    <div class="vcc-page-content">

        <!-- Section 1: Basic Information -->
        <section class="vcc-detail-section">
            <h2 class="vcc-detail-section__title">Informacje o urządzeniu</h2>
            <div class="vcc-detail-grid">
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Numer inwentarzowy</span>
                    <span class="vcc-detail-grid__value vcc-detail-grid__value--mono"
                          th:text="${coolingDevice.inventoryNumber}"></span>
                </div>
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Nazwa</span>
                    <span class="vcc-detail-grid__value" th:text="${coolingDevice.name}"></span>
                </div>
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Dział</span>
                    <span class="vcc-detail-grid__value"
                          th:text="${coolingDevice.department.name}"></span>
                </div>
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Pracownia</span>
                    <span class="vcc-detail-grid__value"
                          th:text="${coolingDevice.laboratory != null ? coolingDevice.laboratory.fullName : 'brak'}"></span>
                </div>
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Typ komory</span>
                    <span class="vcc-detail-grid__value"
                          th:text="${coolingDevice.chamberType.displayName}"></span>
                </div>
                <div class="vcc-detail-grid__item">
                    <span class="vcc-detail-grid__label">Typ materiału</span>
                    <span class="vcc-detail-grid__value"
                          th:text="${coolingDevice.materialType.name}"></span>
                </div>
            </div>
        </section>

        <!-- Section 2: Parameters Table -->
        <section class="vcc-detail-section">
            <h2 class="vcc-detail-section__title">Parametry i ustawienia</h2>
            <div class="vcc-data-card">
                <table class="vcc-detail-table">
                    <tbody>
                        <tr>
                            <td class="vcc-detail-table__label">Objętość</td>
                            <td class="vcc-detail-table__value"
                                th:text="${#numbers.formatDecimal(coolingDevice.volume, 1, 2)} + ' m³'"></td>
                        </tr>
                        <tr>
                            <td class="vcc-detail-table__label">Kategoria kubatury</td>
                            <td class="vcc-detail-table__value"
                                th:text="${coolingDevice.volumeCategory != null ? coolingDevice.volumeCategory.name : 'brak'}"></td>
                        </tr>
                        <tr>
                            <td class="vcc-detail-table__label">Min. temperatura operacyjna</td>
                            <td class="vcc-detail-table__value"
                                th:text="${coolingDevice.minOperatingTemp} + ' °C'"></td>
                        </tr>
                        <tr>
                            <td class="vcc-detail-table__label">Max. temperatura operacyjna</td>
                            <td class="vcc-detail-table__value"
                                th:text="${coolingDevice.maxOperatingTemp} + ' °C'"></td>
                        </tr>
                        <tr>
                            <td class="vcc-detail-table__label">Data utworzenia</td>
                            <td class="vcc-detail-table__value"
                                th:text="${#temporals.format(coolingDevice.createdAt, 'dd.MM.yyyy HH:mm')}"></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>

        <!-- Section 3: Data List (Related Items) -->
        <section class="vcc-detail-section">
            <div class="vcc-detail-section__header">
                <h2 class="vcc-detail-section__title">Historia walidacji</h2>
                <a th:href="@{/devices/{id}/validations(id=${coolingDevice.id})}"
                   class="vcc-btn vcc-btn--small vcc-btn--secondary">Wszystkie walidacje</a>
            </div>

            <!-- Empty State -->
            <div th:if="${#lists.isEmpty(validations)}" class="vcc-empty-state">
                <span class="vcc-empty-state__icon">📋</span>
                <p class="vcc-empty-state__text">Brak walidacji dla tego urządzenia</p>
                <a th:href="@{/wizard/new}" class="vcc-btn vcc-btn--primary">Nowa walidacja</a>
            </div>

            <!-- Table with Data -->
            <div th:unless="${#lists.isEmpty(validations)}" class="vcc-data-card">
                <table class="vcc-table">
                    <thead>
                        <tr>
                            <th>Numer</th>
                            <th>Status</th>
                            <th>Typ</th>
                            <th>Data</th>
                            <th>Akcje</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr th:each="val : ${validations}">
                            <td th:text="${val.validationNumber}"></td>
                            <td>
                                <span th:class="'vcc-status vcc-status--' + ${val.status.name().toLowerCase()}"
                                      th:text="${val.status.displayName}"></span>
                            </td>
                            <td th:text="${val.procedureType.displayName}"></td>
                            <td th:text="${#temporals.format(val.createdAt, 'dd.MM.yyyy')}"></td>
                            <td>
                                <a th:href="@{/validations/{id}(id=${val.id})}"
                                   class="vcc-btn vcc-btn--small vcc-btn--primary">Szczegóły</a>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>

        <!-- Section 4: Audit History (from Envers) -->
        <section class="vcc-detail-section">
            <h2 class="vcc-detail-section__title">Historia zmian</h2>
            <button class="vcc-btn vcc-btn--secondary vcc-btn--small"
                    id="loadHistoryBtn">Załaduj historię</button>

            <!-- AJAX-loaded history -->
            <div id="auditHistory" class="vcc-envers-history" style="display: none;">
                <!-- Loaded via AJAX -->
            </div>
        </section>

        <!-- Section 5: Modal for Edit -->
        <div id="editModal" class="vcc-modal-overlay" style="display: none;">
            <div class="vcc-modal-content">
                <h3>Edytuj urządzenie</h3>
                <form th:action="@{/devices/{id}(id=${coolingDevice.id})}"
                      method="POST"
                      class="vcc-form">
                    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
                    <!-- Form fields here -->
                    <button type="submit" class="vcc-btn vcc-btn--primary">Zapisz</button>
                    <button type="button" class="vcc-btn vcc-btn--secondary" onclick="closeModal()">Anuluj</button>
                </form>
            </div>
        </div>

    </div>

</main>

<!-- Scripts -->
<script>
document.getElementById('loadHistoryBtn').addEventListener('click', function() {
    fetch(`/devices/${th:text = "#{coolingDevice.id}"}/audit-history`)
        .then(r => r.html())
        .then(html => {
            document.getElementById('auditHistory').innerHTML = html;
            document.getElementById('auditHistory').style.display = 'block';
        });
});

function closeModal() {
    document.getElementById('editModal').style.display = 'none';
}
</script>

</body>
</html>
```

## Detail Page Components

### Grid Layout (2 columns)
```html
<div class="vcc-detail-grid">
    <div class="vcc-detail-grid__item">
        <span class="vcc-detail-grid__label">Label</span>
        <span class="vcc-detail-grid__value">Value</span>
    </div>
</div>
```

### Data Table
```html
<table class="vcc-detail-table">
    <tbody>
        <tr>
            <td class="vcc-detail-table__label">Label</td>
            <td class="vcc-detail-table__value">Value</td>
        </tr>
    </tbody>
</table>
```

### List Table
```html
<table class="vcc-table">
    <thead>
        <tr>
            <th>Column 1</th>
            <th>Column 2</th>
        </tr>
    </thead>
    <tbody>
        <tr th:each="item : ${items}">
            <td th:text="${item.field1}"></td>
            <td th:text="${item.field2}"></td>
        </tr>
    </tbody>
</table>
```

### Empty State
```html
<div class="vcc-empty-state">
    <span class="vcc-empty-state__icon">📋</span>
    <p class="vcc-empty-state__text">No data available</p>
</div>
```

### Status Badge
```html
<span th:class="'vcc-status vcc-status--' + ${entity.status.name().toLowerCase()}"
      th:text="${entity.status.displayName}"></span>
```

### Modal Overlay
```html
<div id="myModal" class="vcc-modal-overlay">
    <div class="vcc-modal-content">
        <h3>Modal Title</h3>
        <!-- Content -->
        <button onclick="closeModal()" class="vcc-btn">Close</button>
    </div>
</div>
```

## CSS Classes

| Class | Purpose |
|-------|---------|
| `.vcc-detail-header` | Header with title + status + actions |
| `.vcc-detail-section` | Section container |
| `.vcc-detail-grid` | 2-column key-value layout |
| `.vcc-detail-table` | Read-only data table |
| `.vcc-table` | Data list table (striped, hover) |
| `.vcc-status` | Status badge |
| `.vcc-empty-state` | No data placeholder |
| `.vcc-modal-overlay` | Modal dialog |
| `.vcc-breadcrumb` | Navigation breadcrumb |

## Steps

1. **Read** existing detail page from `src/main/resources/templates/device/details.html`
2. **Analyze** entity structure and relationships
3. **Create** detail template with:
   - Breadcrumb navigation
   - Detail header (title, status, actions)
   - Sections for organizing data
   - Key-value grids for structured data
   - Tables for lists
   - Empty states for no data
   - Modal overlays for edit forms
4. **Add** AJAX loading for audit history
5. **Test** with `mvn clean compile`

## Detail Page Checklist

- [ ] Breadcrumb navigation present
- [ ] Detail header with title + status + action buttons
- [ ] Data organized in logical sections
- [ ] Key-value pairs in grids
- [ ] Tables for related data lists
- [ ] Empty state when no data
- [ ] Edit button for authorized users
- [ ] Delete button with confirmation
- [ ] CSRF token on forms
- [ ] Status badges with appropriate styling
- [ ] Dates formatted in Polish locale
- [ ] Numbers formatted with proper decimals
