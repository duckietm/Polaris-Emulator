# Polaris compatibility requirements

- Security fixes and new functionality must remain self-contained within Polaris.
- Do not require CMSs, clients, proxies, or other external integrations to change code, schemas, payloads, or behavior.
- Preserve compatibility with existing deployments where external systems continue using the established Polaris interface.
- If a proposed fix cannot be implemented safely at the emulator level (including Polaris-owned database migrations), do not implement it as a breaking cross-system requirement. Document the limitation and seek an emulator-local design instead.
- Keep this `AGENTS.md` file local and uncommitted.
