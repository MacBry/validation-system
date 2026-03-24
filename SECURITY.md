# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 2.12.x  | Yes                |
| < 2.12  | No                 |

## Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

### How to Report

1. **Email:** Send a detailed report to the repository maintainer via email.
2. **GitHub Security Advisories:** Use the [GitHub Security Advisory](https://github.com/MacBry/validation-system/security/advisories/new) feature to privately report a vulnerability.

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Response Timeline

- **Acknowledgment:** Within 48 hours
- **Assessment:** Within 7 days
- **Fix:** Critical vulnerabilities will be addressed within 72 hours of confirmation

### Scope

This security policy covers:
- The application source code
- Configuration files
- Docker and deployment configurations
- Dependencies (via Dependabot)

### Security Practices

This project follows these security practices:
- All secrets are managed via environment variables (never hardcoded)
- Dependencies are monitored for known vulnerabilities via Dependabot
- CSRF protection is enabled on all state-changing endpoints
- BCrypt (strength 12) is used for password hashing
- Rate limiting is enforced on authentication endpoints
- Audit trails are maintained for GMP compliance (Hibernate Envers)
- Content Security Policy headers are enforced
- HSTS is enabled for transport security
