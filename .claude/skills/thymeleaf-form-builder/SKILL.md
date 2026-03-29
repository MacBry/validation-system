---
name: thymeleaf-form-builder
description: Build Thymeleaf form templates with validation and VCC design system
context: fork
agent: frontend-saas-specialist
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# Thymeleaf Form Builder

Generate **form-based Thymeleaf templates** using the VCC design system with validation and error display.

## Form Architecture

### 1. Form Page Structure

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:sec="http://www.thymeleaf.org/extras/spring-security"
      lang="pl">

<head>
    <meta charset="UTF-8">
    <title>Dodaj urządzenie</title>
    <link rel="stylesheet" th:href="@{/css/style-saas.css}">
</head>

<body class="vcc-page">

<!-- Sidebar Navigation -->
<aside th:replace="~{fragments/sidebar :: sidebar('devices')}"></aside>

<main class="vcc-main">

    <!-- Page Header -->
    <div class="vcc-topbar">
        <h1 class="vcc-topbar__title">Dodaj urządzenie</h1>
        <div class="vcc-topbar__actions">
            <a th:href="@{/devices}" class="vcc-btn vcc-btn--secondary">Anuluj</a>
        </div>
    </div>

    <!-- Content Area -->
    <div class="vcc-page-content">
        <section class="vcc-form-card">

            <!-- Form with DTO binding -->
            <form th:object="${coolingDeviceDto}"
                  method="POST"
                  th:action="@{/devices}"
                  class="vcc-form">

                <!-- CSRF Token -->
                <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">

                <!-- Section 1: Basic Info -->
                <fieldset class="vcc-form-section">
                    <legend>Podstawowe informacje</legend>

                    <!-- Input: Inventory Number -->
                    <div class="vcc-form-group">
                        <label for="inventoryNumber" class="vcc-form-label">
                            Numer inwentarzowy <span class="vcc-form-required">*</span>
                        </label>
                        <input type="text"
                               id="inventoryNumber"
                               th:field="*{inventoryNumber}"
                               class="vcc-form-input vcc-form-input--mono"
                               placeholder="np. INV-2024-001"
                               maxlength="50">
                        <span th:if="${#fields.hasErrors('inventoryNumber')}"
                              th:errors="*{inventoryNumber}"
                              class="vcc-form-error">Błąd</span>
                    </div>

                    <!-- Input: Device Name -->
                    <div class="vcc-form-group">
                        <label for="name" class="vcc-form-label">
                            Nazwa urządzenia <span class="vcc-form-required">*</span>
                        </label>
                        <input type="text"
                               id="name"
                               th:field="*{name}"
                               class="vcc-form-input"
                               placeholder="np. Lodówka farmaceutyczna A"
                               maxlength="100">
                        <span th:if="${#fields.hasErrors('name')}"
                              th:errors="*{name}"
                              class="vcc-form-error">Błąd</span>
                        <small class="vcc-form-help">Opisowa nazwa dla laboratorium</small>
                    </div>
                </fieldset>

                <!-- Section 2: Organization -->
                <fieldset class="vcc-form-section">
                    <legend>Przypisanie organizacyjne</legend>

                    <div class="vcc-form-row">
                        <!-- Select: Department -->
                        <div class="vcc-form-group">
                            <label for="departmentId" class="vcc-form-label">
                                Dział <span class="vcc-form-required">*</span>
                            </label>
                            <select id="departmentId"
                                    th:field="*{departmentId}"
                                    class="vcc-form-select"
                                    data-cascade-target="laboratoryId">
                                <option value="">-- Wybierz dział --</option>
                                <option th:each="dept : ${departments}"
                                        th:value="${dept.id}"
                                        th:text="${dept.name}">
                                </option>
                            </select>
                            <span th:if="${#fields.hasErrors('departmentId')}"
                                  th:errors="*{departmentId}"
                                  class="vcc-form-error">Błąd</span>
                        </div>

                        <!-- Select: Laboratory (cascading) -->
                        <div class="vcc-form-group">
                            <label for="laboratoryId" class="vcc-form-label">
                                Pracownia <span class="vcc-form-help">(opcjonalnie)</span>
                            </label>
                            <select id="laboratoryId"
                                    th:field="*{laboratoryId}"
                                    class="vcc-form-select"
                                    data-cascade-source="departmentId">
                                <option value="">-- Wybierz pracownię --</option>
                                <option th:each="lab : ${laboratories}"
                                        th:value="${lab.id}"
                                        th:text="${lab.fullName}"
                                        th:data-department-id="${lab.department.id}">
                                </option>
                            </select>
                        </div>
                    </div>
                </fieldset>

                <!-- Section 3: Equipment Type -->
                <fieldset class="vcc-form-section">
                    <legend>Parametry urządzenia</legend>

                    <div class="vcc-form-row">
                        <!-- Select: Chamber Type -->
                        <div class="vcc-form-group">
                            <label for="chamberType" class="vcc-form-label">
                                Typ komory <span class="vcc-form-required">*</span>
                            </label>
                            <select id="chamberType"
                                    th:field="*{chamberType}"
                                    class="vcc-form-select">
                                <option value="">-- Wybierz typ --</option>
                                <option th:each="type : ${T(com.mac.bry.validationsystem.device.ChamberType).values()}"
                                        th:value="${type}"
                                        th:text="${type.displayName}">
                                </option>
                            </select>
                            <span th:if="${#fields.hasErrors('chamberType')}"
                                  th:errors="*{chamberType}"
                                  class="vcc-form-error">Błąd</span>
                        </div>

                        <!-- Select: Material Type -->
                        <div class="vcc-form-group">
                            <label for="materialTypeId" class="vcc-form-label">
                                Typ materiału <span class="vcc-form-required">*</span>
                            </label>
                            <select id="materialTypeId"
                                    th:field="*{materialTypeId}"
                                    class="vcc-form-select">
                                <option value="">-- Wybierz typ --</option>
                                <option th:each="material : ${materialTypes}"
                                        th:value="${material.id}"
                                        th:text="${material.name}">
                                </option>
                            </select>
                            <span th:if="${#fields.hasErrors('materialTypeId')}"
                                  th:errors="*{materialTypeId}"
                                  class="vcc-form-error">Błąd</span>
                        </div>
                    </div>

                    <div class="vcc-form-group">
                        <label for="volume" class="vcc-form-label">
                            Objętość (m³)
                        </label>
                        <input type="number"
                               id="volume"
                               th:field="*{volume}"
                               class="vcc-form-input"
                               step="0.01"
                               min="0"
                               placeholder="0.50">
                        <small class="vcc-form-help">Będzie auto-obliczona kategoria (S/M/L)</small>
                        <span th:if="${#fields.hasErrors('volume')}"
                              th:errors="*{volume}"
                              class="vcc-form-error">Błąd</span>
                    </div>
                </fieldset>

                <!-- Form Actions -->
                <div class="vcc-form-actions">
                    <button type="submit" class="vcc-btn vcc-btn--primary">
                        Zapisz urządzenie
                    </button>
                    <a th:href="@{/devices}" class="vcc-btn vcc-btn--secondary">
                        Anuluj
                    </a>
                </div>

            </form>

            <!-- Flash Messages (displayed after save) -->
            <div th:if="${successMessage}" class="vcc-flash vcc-flash--success">
                <span th:text="${successMessage}"></span>
            </div>

        </section>
    </div>

</main>

<!-- Scripts -->
<script th:src="@{/js/cascading-select.js}"></script>
<script>
    // Cascading select: Department → Laboratory
    CascadingSelect.init({
        sourceSelector: '[data-cascade-source="departmentId"]',
        targetSelector: '[data-cascade-target="laboratoryId"]',
        parentAttribute: 'data-department-id'
    });
</script>

</body>
</html>
```

## Form Components

### Input: Text Field
```html
<div class="vcc-form-group">
    <label for="fieldName" class="vcc-form-label">Label</label>
    <input type="text" id="fieldName" th:field="*{fieldName}" class="vcc-form-input">
    <span th:if="${#fields.hasErrors('fieldName')}" th:errors="*{fieldName}" class="vcc-form-error"></span>
</div>
```

### Select: Dropdown
```html
<div class="vcc-form-group">
    <label for="selectField" class="vcc-form-label">Select</label>
    <select id="selectField" th:field="*{selectField}" class="vcc-form-select">
        <option value="">-- Choose --</option>
        <option th:each="item : ${items}" th:value="${item.id}" th:text="${item.name}"></option>
    </select>
</div>
```

### Select: Enum (Java)
```html
<select id="status" th:field="*{status}" class="vcc-form-select">
    <option value="">-- Choose --</option>
    <option th:each="status : ${T(com.mac.bry.validationsystem.validation.ValidationStatus).values()}"
            th:value="${status}"
            th:text="${status.displayName}"></option>
</select>
```

### Textarea
```html
<div class="vcc-form-group">
    <label for="description" class="vcc-form-label">Description</label>
    <textarea id="description" th:field="*{description}" class="vcc-form-textarea" rows="5"></textarea>
    <span th:if="${#fields.hasErrors('description')}" th:errors="*{description}" class="vcc-form-error"></span>
</div>
```

### Checkbox
```html
<div class="vcc-form-group">
    <label class="vcc-form-checkbox">
        <input type="checkbox" th:field="*{acknowledged}" class="vcc-form-input--checkbox">
        <span>I acknowledge this action</span>
    </label>
</div>
```

### Radio Group
```html
<fieldset class="vcc-form-section">
    <legend>Choose Option</legend>
    <label class="vcc-form-radio">
        <input type="radio" th:field="*{option}" value="A">
        <span>Option A</span>
    </label>
    <label class="vcc-form-radio">
        <input type="radio" th:field="*{option}" value="B">
        <span>Option B</span>
    </label>
</fieldset>
```

## Error Display Patterns

### Field-level Errors
```html
<span th:if="${#fields.hasErrors('fieldName')}"
      th:errors="*{fieldName}"
      class="vcc-form-error">Błąd walidacji</span>
```

### Global Errors (non-field)
```html
<div th:if="${#fields.hasErrors('global')}" class="vcc-flash vcc-flash--error">
    <span th:each="error : ${#fields.errors('global')}" th:text="${error}"></span>
</div>
```

## CSS Classes (from style-saas.css)

| Class | Purpose |
|-------|---------|
| `.vcc-form-card` | Form container |
| `.vcc-form-section` | Fieldset styling |
| `.vcc-form-row` | 2-column layout |
| `.vcc-form-group` | Input container |
| `.vcc-form-label` | Label styling |
| `.vcc-form-input` | Text input |
| `.vcc-form-select` | Dropdown |
| `.vcc-form-textarea` | Textarea |
| `.vcc-form-error` | Error message (red) |
| `.vcc-form-help` | Helper text (gray) |
| `.vcc-form-required` | Required indicator (*) |
| `.vcc-form-actions` | Button group at bottom |
| `.vcc-btn` | Base button |
| `.vcc-btn--primary` | Primary action (blue) |
| `.vcc-btn--secondary` | Secondary action (gray) |

## Steps

1. **Read** existing form from `src/main/resources/templates/device/form.html`
2. **Analyze** DTO structure (fields, validations, constraints)
3. **Create** form template with proper structure:
   - Sidebar + topbar
   - Form card container
   - Sections (fieldset) for grouping related fields
   - Each input with label, field, error display
   - Submit button
4. **Add** validation error messages in Polish
5. **Add** helper text for complex fields
6. **Test** with `mvn clean compile`
7. **Verify** form renders with Thymeleaf bindings

## Form Validation Checklist

- [ ] @Valid on DTO in controller
- [ ] th:field="*{fieldName}" binds to DTO
- [ ] th:errors displays validation messages
- [ ] Required fields marked with <span class="vcc-form-required">*</span>
- [ ] Helper text for complex fields
- [ ] CSRF token included
- [ ] Submit and Cancel buttons present
- [ ] Form method="POST", th:action="@{/endpoint}"
- [ ] Sidebar active link correct
- [ ] CSS classes follow .vcc-form-* pattern
