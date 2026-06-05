# ✈️ Aeronautics Preflight Checklist

**A safer launch starts with a better look at your ship.**

**Aeronautics Preflight Checklist** is a client-only utility mod for Minecraft (NeoForge) that expands the `Diagram` item from **Create: Aeronautics** with an expandable preflight checklist menu, giving players clearer diagnostics before they send their airship into the sky.

Use the Diagram to inspect your contraption and catch common problems before takeoff — from poor balance and weak lift to blocked propulsion, missing controls, unsafe cargo placement, and other issues that could turn your beautiful flying machine into a falling brick.

---

## 🚀 Features

* **Cream Slide-out Sidebar**: Blends seamlessly with the native Create/Catnip blueprint aesthetics.
* **Scrollable Preflight Readouts**: Interactive scroll bar with clipping support so you can see all warnings on larger ships.
* **Client-Only Utility**: Works locally on your client. It is **not** required on multiplayer servers for you to use it!
* **Magnifying Glass Toggle**: A custom magnifying glass button integrates directly into the Diagram interface.

---

## 🔍 Preflight Diagnostics Included

1. **Mass Check**: Toggles mass readouts (displayed in **kpg** formatted to the hundredths place) and warns if your ship's weight limit is near or exceeded.
2. **Lift Check**: Assesses total lift force (in Newtons) and flags deficiencies.
3. **Flight Balance (CoL vs CoM)**: Computes the Center of Lift centroid ($\mathbf{r}_{CoL} = \frac{\sum \mathbf{r}_i F_i}{\sum F_i}$) and flags imbalance if it deviates by $>0.8$ blocks from the Center of Mass ($CoM$).
4. **Propulsion Torque**: Computes the net torque cross-product ($\boldsymbol{\tau} = \mathbf{r} \times \mathbf{F}$) about the $CoM$ to flag asymmetric engine configurations that would pull the ship sideways or cause it to flip.
5. **Propeller Clearance**: Scans for solid blocks obstructing thrusters/propeller airflow.
6. **Missing Essentials**: Ensures pilot seats and steering helms are present on the contraption.
7. **Cargo Placement Risks**: Audits container blocks and alerts if cargo is lopsided or makes the ship top-heavy.

---

## 🛠️ Building From Source

To compile the mod yourself:
1. Clone the repository.
2. Run the Gradle build command:
   ```bash
   ./gradlew build
   ```
3. The built JAR file will be located at `build/libs/aeronautics_preflight_checklist-1.0.0.jar`.

---

## 🤝 Contributing & Issues
Issues, bug reports, and pull requests are welcome! Please open an issue on the GitHub issues tracker if you run into any bugs or have feature requests.
