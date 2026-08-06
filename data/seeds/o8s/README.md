# O8S cloud-linkage seed

`poc-default-plan.json` is a deliberately small eight-track test plan for O8S
(Territory 755). It exists to verify the cloud-to-game lifecycle without requiring
an eight-person DMU entry:

- create and publish a plan in the Web application;
- match the published immutable snapshot by Territory, strategy, and track mode;
- start automatically on `InCombat`;
- confirm Sage actions through local `ActionEffect` events;
- upload one post-fight execution batch for personal review.

The two markers at 10 and 20 seconds are linkage probes, not claims about the real
O8S encounter timeline. The plan therefore remains `POC_PENDING` and must not be
used as encounter guidance or promoted to `VERIFIED` from automated tests alone.
