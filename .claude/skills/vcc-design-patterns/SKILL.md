---
name: vcc-design-patterns
description: Reference guide for VCC design system classes and patterns
context: fork
agent: frontend-saas-specialist
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# VCC Design System Patterns

Complete reference for the **Validation Control Center (VCC)** design system implemented in `style-saas.css`.

## Design Tokens (CSS Custom Properties)

```css
:root {
    /* Primary Colors */
    --vcc-primary: #3d5afe;          /* Primary blue */
    --vcc-secondary: #757ce8;        /* Secondary blue */
    --vcc-success: #4caf50;          /* Green */
    --vcc-warning: #ff9800;          /* Orange */
    --vcc-danger: #f44336;           /* Red */

    /* Sidebar Colors */
    --sidebar-bg: #1a1f3a;           /* Dark blue */
    --sidebar-text: #a4adc5;         /* Light gray */
    --sidebar-active: #3d5afe;       /* Primary blue */
    --sidebar-hover: #262d4a;        /* Slightly lighter */

    /* Surface Colors */
    --surface-page: #f5f7fa;         /* Page background */
    --surface-card: #ffffff;         /* Card background */
    --surface-input: #fafbfc;        /* Input background */

    /* Typography */
    --font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    --font-size-sm: 12px;
    --font-size-base: 14px;
    --font-size-lg: 16px;
    --font-size-xl: 18px;
    --font-size-2xl: 24px;

    /* Spacing Scale */
    --spacing-xs: 4px;
    --spacing-sm: 8px;
    --spacing-md: 16px;
    --spacing-lg: 24px;
    --spacing-xl: 32px;

    /* Border Radius */
    --radius-sm: 4px;
    --radius-md: 8px;
    --radius-lg: 12px;

    /* Shadows */
    --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.1);
    --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.1);
    --shadow-lg: 0 10px 25px rgba(0, 0, 0, 0.1);

    /* Transitions */
    --transition-duration: 200ms;
    --transition-timing: cubic-bezier(0.4, 0, 0.2, 1);
}
```

## Layout System

### Sidebar Navigation
```html
<aside class="vcc-sidebar">
    <!-- Fixed left sidebar: 260px width -->
    <!-- Dark background (#1a1f3a) -->
    <!-- Navigation items with hover/active states -->
</aside>
```

**Features**:
- Fixed position, left side
- Width: 260px on desktop, hamburger on mobile (<1024px)
- Dark blue background
- Active item highlighted with primary color

### Top Navigation Bar
```html
<div class="vcc-topbar">
    <h1 class="vcc-topbar__title">Page Title</h1>
    <div class="vcc-topbar__actions">
        <!-- Action buttons -->
    </div>
</div>
```

**Features**:
- Sticky position at top
- White background
- Shadow for depth
- Action buttons right-aligned

### Main Content Area
```html
<main class="vcc-main">
    <div class="vcc-page">
        <div class="vcc-page-content">
            <!-- Page content -->
        </div>
    </div>
</main>
```

**Features**:
- Flex layout with sidebar
- Responsive on mobile
- Consistent margins

## Component Patterns

### Buttons

```html
<!-- Primary (main action) -->
<button class="vcc-btn vcc-btn--primary">Save</button>

<!-- Secondary (alternative action) -->
<button class="vcc-btn vcc-btn--secondary">Cancel</button>

<!-- Danger (destructive action) -->
<button class="vcc-btn vcc-btn--danger">Delete</button>

<!-- Small variant -->
<button class="vcc-btn vcc-btn--small vcc-btn--primary">Details</button>

<!-- Disabled state -->
<button class="vcc-btn vcc-btn--primary" disabled>Disabled</button>
```

**Button Colors**:
- `.vcc-btn--primary`: Blue (#3d5afe)
- `.vcc-btn--secondary`: Gray
- `.vcc-btn--danger`: Red (#f44336)
- `.vcc-btn--success`: Green (#4caf50)
- `.vcc-btn--warning`: Orange (#ff9800)

### Forms

```html
<!-- Form Container -->
<form class="vcc-form">
    <!-- Form Sections -->
    <fieldset class="vcc-form-section">
        <legend>Section Title</legend>

        <!-- Form Row (2 columns) -->
        <div class="vcc-form-row">
            <!-- Input Group -->
            <div class="vcc-form-group">
                <label class="vcc-form-label">Field Label</label>
                <input type="text" class="vcc-form-input" placeholder="Placeholder">
                <span class="vcc-form-help">Helper text</span>
            </div>

            <!-- Select -->
            <div class="vcc-form-group">
                <label class="vcc-form-label">Select</label>
                <select class="vcc-form-select">
                    <option>Option 1</option>
                </select>
            </div>
        </div>

        <!-- Textarea -->
        <div class="vcc-form-group">
            <label class="vcc-form-label">Message</label>
            <textarea class="vcc-form-textarea" rows="5"></textarea>
        </div>

        <!-- Checkbox -->
        <label class="vcc-form-checkbox">
            <input type="checkbox">
            <span>I agree to terms</span>
        </label>

        <!-- Radio -->
        <label class="vcc-form-radio">
            <input type="radio" name="option">
            <span>Option A</span>
        </label>
    </fieldset>

    <!-- Error Display -->
    <span class="vcc-form-error">Validation error message</span>

    <!-- Required Indicator -->
    <span class="vcc-form-required">*</span>

    <!-- Form Actions -->
    <div class="vcc-form-actions">
        <button type="submit" class="vcc-btn vcc-btn--primary">Submit</button>
        <button type="reset" class="vcc-btn vcc-btn--secondary">Reset</button>
    </div>
</form>
```

### Data Tables

```html
<!-- Table Container -->
<div class="vcc-data-card">
    <table class="vcc-table">
        <thead>
            <tr>
                <th>Column 1</th>
                <th>Column 2</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>Data 1</td>
                <td>Data 2</td>
                <td>
                    <a class="vcc-btn vcc-btn--small vcc-btn--primary">Edit</a>
                </td>
            </tr>
        </tbody>
    </table>
</div>

<!-- Pagination -->
<div class="vcc-pagination">
    <a href="#" class="vcc-pagination__link vcc-pagination__link--prev">← Previous</a>
    <span class="vcc-pagination__info">Page 1 of 10</span>
    <a href="#" class="vcc-pagination__link vcc-pagination__link--next">Next →</a>
</div>
```

**Table Features**:
- Striped rows (alternating colors)
- Hover effect on rows
- Sticky header (stays on scroll)
- Responsive on mobile

### Status Badges

```html
<!-- Success Status -->
<span class="vcc-status vcc-status--valid">Valid</span>

<!-- Warning Status -->
<span class="vcc-status vcc-status--warning">Warning</span>

<!-- Error Status -->
<span class="vcc-status vcc-status--invalid">Invalid</span>

<!-- Pending Status -->
<span class="vcc-status vcc-status--pending">Pending</span>

<!-- Completed Status -->
<span class="vcc-status vcc-status--completed">Completed</span>
```

### Flash Messages (Alerts)

```html
<!-- Success Message -->
<div class="vcc-flash vcc-flash--success">
    ✓ Operation completed successfully
</div>

<!-- Error Message -->
<div class="vcc-flash vcc-flash--error">
    ✗ An error occurred. Please try again.
</div>

<!-- Warning Message -->
<div class="vcc-flash vcc-flash--warning">
    ⚠ Warning: This action cannot be undone
</div>

<!-- Info Message -->
<div class="vcc-flash vcc-flash--info">
    ℹ Note: This information is important
</div>
```

### Modals

```html
<div class="vcc-modal-overlay" id="myModal">
    <div class="vcc-modal-content">
        <div class="vcc-modal-header">
            <h3>Modal Title</h3>
            <button class="vcc-modal-close" onclick="closeModal()">&times;</button>
        </div>
        <div class="vcc-modal-body">
            <!-- Modal content -->
        </div>
        <div class="vcc-modal-footer">
            <button class="vcc-btn vcc-btn--secondary" onclick="closeModal()">Cancel</button>
            <button class="vcc-btn vcc-btn--primary">Confirm</button>
        </div>
    </div>
</div>
```

### Empty State

```html
<div class="vcc-empty-state">
    <span class="vcc-empty-state__icon">📋</span>
    <p class="vcc-empty-state__text">No data available</p>
    <p class="vcc-empty-state__subtext">Create a new item to get started</p>
    <a href="#" class="vcc-btn vcc-btn--primary">Create New</a>
</div>
```

## Color Palette

| Purpose | Color | CSS |
|---------|-------|-----|
| **Primary** | Blue | `#3d5afe` |
| **Secondary** | Light Blue | `#757ce8` |
| **Success** | Green | `#4caf50` |
| **Warning** | Orange | `#ff9800` |
| **Danger** | Red | `#f44336` |
| **Info** | Cyan | `#00bcd4` |
| **Sidebar** | Dark Blue | `#1a1f3a` |
| **Page BG** | Light Gray | `#f5f7fa` |
| **Card BG** | White | `#ffffff` |
| **Text Primary** | Dark Gray | `#212121` |
| **Text Secondary** | Medium Gray | `#757575` |
| **Border** | Light Gray | `#e0e0e0` |

## Responsive Design

### Breakpoints

```css
/* Mobile: < 640px (default) */
/* Tablet: 640px - 1024px */
/* Desktop: > 1024px */

@media (max-width: 1024px) {
    .vcc-sidebar { /* Convert to hamburger */ }
    .vcc-form-row { /* Stack columns */ }
}

@media (max-width: 640px) {
    .vcc-table { /* Horizontal scroll */ }
    .vcc-topbar { /* Smaller title */ }
}
```

### Mobile-First Approach

```html
<!-- Default (mobile): single column -->
<div class="vcc-form-row">
    <!-- Stack vertically on mobile -->
</div>

<!-- Tablet+: two columns -->
@media (min-width: 768px) {
    .vcc-form-row { display: grid; grid-template-columns: 1fr 1fr; }
}
```

## Animation Patterns

```css
/* Fade In */
.vcc-animate-fade-in {
    animation: fadeIn 0.3s ease-in-out;
}

/* Slide Up */
.vcc-animate-slide-up {
    animation: slideUp 0.3s ease-in-out;
}

/* Pulse */
.vcc-animate-pulse {
    animation: pulse 2s infinite;
}

/* Hover Effects */
.vcc-btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transition: all var(--transition-duration) var(--transition-timing);
}
```

## Accessibility Features

- **Focus Indicators**: Blue outline on keyboard navigation
- **Semantic HTML**: Proper heading hierarchy (h1 → h6)
- **Color Contrast**: WCAG AA compliant (4.5:1 ratio for text)
- **Labels**: All inputs have associated labels
- **Error Messages**: Clear, readable red text
- **Keyboard Navigation**: Tab-accessible form elements

## Best Practices

1. **Use CSS Custom Properties**: Reference `--vcc-primary` instead of hardcoding colors
2. **Consistent Spacing**: Use `--spacing-*` scale (4px, 8px, 16px, 24px, 32px)
3. **Status Badges**: Use `.vcc-status--*` classes (valid, invalid, pending, etc.)
4. **Button Hierarchy**: Primary action is primary color, secondary is gray
5. **Form Validation**: Show errors near fields in red
6. **Mobile First**: Design mobile layouts first, then expand to desktop
7. **Semantic HTML**: Use proper heading tags, fieldsets for form sections
8. **CSRF Protection**: Always include CSRF token in forms

## File Location

All CSS is in: `src/main/resources/static/css/style-saas.css` (977 lines, 24KB)

**No external CDN dependencies** - completely self-hosted and Polish-optimized.
