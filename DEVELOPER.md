# 🛠️ Aeronautics Preflight Checklist - Developer Documentation

This document contains technical details, mathematical formulations, and build instructions for the **Aeronautics Preflight Checklist** mod.

---

## 🏗️ Building From Source

To compile the mod locally:
1. Clone the repository.
2. Run the Gradle build command:
   ```bash
   ./gradlew build
   ```
3. The compiled client-only JAR file will be generated at `build/libs/aeronautics_preflight_checklist-1.0.0.jar`.

---

## 📐 Mathematical & Physics Formulations

The calculations in the mod are executed client-side on the sublevel representations of the ship. Below are the equations used:

### 1. Mass & Lift Equilibrium
Checks if the upward lift force overcomes gravity:
* Gravity Force ($F_g$): Calculated from the `gravity` force group or estimated from mass:
  $$F_g = m \cdot (-9.81)$$
* Lift Force ($F_{lift}$): Sum of positive Y forces from `lift`, `balloon_lift`, and `levitation` force groups.
* A warning is triggered if $F_{lift} < |F_g| \cdot 1.15$ (less than 15% safety margin).

### 2. Flight Balance (Center of Lift vs Center of Mass)
Calculates the center of lift centroid position ($\mathbf{r}_{CoL}$) using a force-weighted average of lift points:
$$\mathbf{r}_{CoL} = \frac{\sum_{i} \mathbf{r}_i \cdot F_{y, i}}{\sum_{i} F_{y, i}}$$
Where:
* $\mathbf{r}_i$ is the local position of point force $i$.
* $F_{y, i}$ is the upward Y force magnitude at point $i$.
* An offset alert is generated if the distance from the Center of Mass ($CoM$) along the X (lateral) or Z (longitudinal) axes exceeds $0.8\text{ blocks}$.

### 3. Propulsion Torque
Computes the net torque cross-product about the Center of Mass ($CoM$) due to propulsion/engine thrust:
$$\boldsymbol{\tau} = \sum_{i} (\mathbf{r}_i - \mathbf{r}_{CoM}) \times \mathbf{F}_i$$
Where:
* $\mathbf{F}_i$ is the thrust force vector of engine $i$.
* The X component ($\tau_x$) corresponds to pitch torque.
* The Y component ($\tau_y$) corresponds to yaw torque.
* Warning thresholds are set at $|\tau_x| > 15.0\text{ N}\cdot\text{m}$ or $|\tau_y| > 15.0\text{ N}\cdot\text{m}$.

### 4. Cargo Imbalance
Computes lateral cargo imbalance based on X-axis offset from the $CoM$:
* Left weight ($W_{left}$): Sum of containers with local X offset $dx < -1.5$.
* Right weight ($W_{right}$): Sum of containers with local X offset $dx > 1.5$.
* An imbalance warning is flagged if $|W_{left} - W_{right}| > 4.0$ blocks of cargo.

---

## 🔌 API & Client Hooks
* **Sable Sublevel Access**: Block scanning is performed using the `LevelPlot` and `EmbeddedPlotLevelAccessor` APIs to avoid loading dedicated server-side threads:
  ```java
  LevelPlot plot = subLevel.getPlot();
  EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();
  ```
* **Force Group Keys**: Resolves forces using the registration keys of the Sable/Veil API:
  ```java
  String path = ForceGroups.REGISTRY.getKey(forceGroup).getPath();
  ```
