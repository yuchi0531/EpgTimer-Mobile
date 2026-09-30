# Tooling & Delivery Preferences

- Do NOT automatically upload build artifacts (APK/archives) to a file host (e.g. tmpfiles.org) after every build. Only upload when the user explicitly asks for an attachment / distribution link. Confidence: 0.95
- When delegating to subagents, always specify the `space-bunny-alpha` model explicitly (via the `model` field) rather than relying on defaults. Confidence: 0.85
