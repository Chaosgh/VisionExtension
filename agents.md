# Agents

## Vision Orientation Overrides
- `vision_activity` and its patrol variants now expose `forcedLookEnabled`, `forcedLookYaw`, and `forcedLookPitch` floats so overrides are explicit and non-null.
- `VisionActivity` normalizes/clamps those floats once and reapplies the pose whenever no player steering occurs.
- When `forcedLookEnabled` is false the activity behaves as before; toggling it true locks yaw/pitch unless `lookAtPlayer` is actively tracking someone.

## Detection Indicator Hygiene
- Text indicators are spawned and tracked per viewer, hidden from other players, and invalid displays are cleaned up before reuse.
- Pending indicator spawns use concurrent sets/maps to avoid duplicates and race conditions.
- Progress bars use ASCII-compatible `\u2588` / `\u2591` escapes to render reliably.
