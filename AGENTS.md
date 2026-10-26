# Agent Collaboration Policy

## Main-agent ownership

The main agent owns:

- Architecture and implementation decisions.
- NeoForge lifecycle and thread-safety decisions.
- BlueMap refresh and storage integration.
- SkinRestorer API compatibility.
- Concurrency correctness.
- Final review, build, and validation.

Keep critical integration work with the main agent unless delegation has a clear benefit.

## Delegate bounded supporting work

When subagents are available, use them for independent, well-scoped tasks such as:

- Finding official documentation and exact release artifacts.
- Inspecting a dependency's API signatures.
- Summarizing relevant source or bytecode.
- Drafting README and troubleshooting sections.
- Reviewing build metadata and dependency declarations.
- Drafting focused test cases.
- Reviewing logs or test results for a specific failure.

Give each task a clear question, a limited scope, and an expected output. Do not ask multiple agents to perform the same search.

## Model selection

Use the fastest, lowest-cost model suitable for the task.

- Prefer Luna, Terra, or an equivalent lightweight model if actually available for documentation, straightforward searches, summaries, metadata checks, and routine test drafting.
- Use low reasoning effort for straightforward extraction or writing tasks.
- Use medium reasoning effort when reconciling several sources or reviewing nontrivial behavior.
- Escalate to a stronger model only for unresolved ambiguity, integration failures, concurrency hazards, or complex architecture.

Model names are preferences, not assumptions about tool availability. Inspect available choices and use supported identifiers.

If model selection or subagents are unavailable, continue directly. Do not block the project or invent unsupported tool calls.

## Token and concurrency limits

- Prefer at most two concurrent supporting subagents by default.
- Increase concurrency only for clearly independent work with a concrete time benefit.
- Pass only the relevant files, excerpts, and context.
- Avoid full-history forks for narrow tasks when context can be supplied explicitly.
- Ask for concise findings with file paths, class/method names, and source references.
- Do not return complete source dumps or lengthy intermediate reasoning.
- Reuse existing agents for related follow-ups where practical.
- Stop redundant or unproductive tasks.
- Do not delegate trivial operations whose coordination cost exceeds their value.

## Evidence and review

Research agents must prioritize primary sources and exact target versions.

Every report should distinguish:

- Verified facts.
- Inferences.
- Unresolved questions.

The main agent must verify critical findings before using them in production code. Lightweight-agent output is supporting evidence, not automatic approval.

## File ownership

Assign separate files or directories to agents performing edits.

Avoid concurrent edits to shared build files, core integration classes, or `AGENTS.md`. The main agent coordinates shared changes and reviews all delegated edits.

## No recursive delegation by default

Supporting agents must not spawn further agents unless the main agent explicitly authorizes a bounded subtask.
