# Changelog

## 1.0.1
- **Aeronautics Force Line Visibility**: Fixed a scissor-test leak that hid the mod's force arrows by isolating GUI graphic buffers.
- **Top-Down Balloon Search**: Reimplemented envelope detection using a top-down layer search to correctly identify multi-layered balloons without leaking out of the bottom opening.
- **Burner Configuration Capping**: Added dynamic simulation capping based on combined hot air/steam burner capacity configs.
- **Thrust Bypass Improvements**: Allowed simulation of inactive propellers and engines by ignoring self-obstructions from rotor hubs, gearboxes, casing, and shafts.
- **Checklist Visual Clean-up**: Hidden distracting ruled notebook lines and coffee stains while keeping the hand-drawn paper aesthetic.


## 1.0.0
- Initial release of Aeronautics Preflight Checklist.
- Expands Diagram item with client-side sliding preflight checklist panel.
- Added diagnostics for mass, lift, balance, thrust symmetry, propeller obstruction, missing essentials, and cargo distribution.
