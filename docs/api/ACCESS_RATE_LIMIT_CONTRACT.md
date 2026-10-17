# Participant access rate-limit contract

Related Jira: SCRUM-70

`POST /api/access/enter` applies a database-backed IP failure window to participant-code authentication failures:

- 10 failed code attempts from one source IP within 10 minutes cause a 15-minute block.
- A blocked request returns HTTP `429`, body `{ "code": "TOO_MANY_ATTEMPTS" }`, and a `Retry-After` header in seconds.
- Requests received while blocked do not extend the block expiry.
- Successful access clears the failure row for that IP.
- Photo retries do not use this counter.

The controller uses the servlet remote address only. Trusted-proxy forwarding and a bounded shared-store cleanup policy remain deployment work; forwarded headers are not trusted by this implementation.

Validation: Docker/Testcontainers PostgreSQL integration tests cover the tenth failure, fixed 15-minute expiry, non-extension, reset, Flyway V4, and the HTTP 429 response.
