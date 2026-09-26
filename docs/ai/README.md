# AI workflow artifacts

- `../spec/E-commerce Order Management.pdf` — the original assignment (raw input).
- `../superpowers/plans/2026-09-26-ecommerce-order-management.md` — the implementation plan produced with the
  `writing-plans` skill; every task maps to one commit.
- `sdd-ledger.md` — the raw SDD progress ledger: every task, its review verdict, fix rounds and the rulings made
  along the way (e.g. the largest-remainder discount fix, the compensating-void-on-rollback fix, the double-receive
  refund lock).
- `skills/` — the Claude Code "superpowers" skills (v6.4.1) used during development:
  - writing-plans: turned the open-ended spec into scoped decisions and a TDD task list
  - subagent-driven-development: ran each task with a fresh implementer subagent plus a fresh reviewer
    subagent, with fix rounds until the review was clean
  - test-driven-development: red → green on every task
  - verification-before-completion: full-suite runs before each task was marked "done"
  - requesting-code-review: used for per-task reviews and for the whole-branch review before submission
  - finishing-a-development-branch: used to integrate the finished branch
- `../../CLAUDE.md` — the agent instructions file used throughout.
