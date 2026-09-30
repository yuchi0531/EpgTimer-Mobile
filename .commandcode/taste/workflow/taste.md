# Engineering Workflow Preferences

- Investigate the real upstream source (e.g. the reference project's C#/C++ code) before designing; report findings with file:line evidence. Do not invent a parallel/own spec. Confidence: 0.9
- Treat the original reference application/server as the source of truth: the new client is read-only and must faithfully mirror its settings, conditions, and display rules. Confidence: 0.9
- When a runtime value cannot be explained by the reference implementation, suspect the local environment (server config, missing data files) before rewriting client code — and say so explicitly. Confidence: 0.85
- No code comments in the implementation. KISS/YAGNI. Confidence: 0.8
- Every non-trivial change should be covered by a test; keep the full test suite green as the completion gate, and verify against a real server when one is available. Confidence: 0.85
- Do not commit machine-specific absolute paths or build outputs; use env vars for environment-dependent test fixtures. Confidence: 0.85
