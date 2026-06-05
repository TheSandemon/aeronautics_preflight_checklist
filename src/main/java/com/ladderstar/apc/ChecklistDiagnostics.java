package com.ladderstar.apc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup.PointForce;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;

public class ChecklistDiagnostics {

    public enum Status {
        PASS("✔", 0xFF2E7D32),
        WARNING("⚠", 0xFFD84315),
        FAIL("✖", 0xFFC62828);

        public final String icon;
        public final int color;

        Status(String icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }

    public static class Entry {
        public final String title;
        public final Status status;
        public final String message;

        public Entry(String title, Status status, String message) {
            this.title = title;
            this.status = status;
            this.message = message;
        }
    }

    public static List<Entry> performChecks(ClientSubLevel subLevel, DiagramDataPacket packet) {
        List<Entry> entries = new ArrayList<>();

        if (subLevel == null || packet == null) {
            entries.add(new Entry("Mod Info", Status.WARNING, "No ship data available. Ensure diagram is working."));
            return entries;
        }

        Vector3d com = subLevel.logicalPose().rotationPoint();
        double mass = packet.mass();

        // 1. Force retrieval
        double gravityY = 0;
        double totalLift = 0;
        Vector3d centerOfLift = new Vector3d();
        double liftForceSum = 0;

        Vector3d totalThrust = new Vector3d();
        double totalThrustMag = 0;
        Vector3d propulsionTorque = new Vector3d();

        for (Map.Entry<ForceGroup, List<PointForce>> forceEntry : packet.forces().entrySet()) {
            ForceGroup fg = forceEntry.getKey();
            List<PointForce> points = forceEntry.getValue();
            net.minecraft.resources.ResourceLocation loc = ForceGroups.REGISTRY.getKey(fg);
            if (loc == null) continue;
            String path = loc.getPath();

            if (path.equals("gravity")) {
                for (PointForce pf : points) {
                    gravityY += pf.force().y();
                }
            } else if (path.equals("lift") || path.equals("balloon_lift") || path.equals("levitation")) {
                for (PointForce pf : points) {
                    double fy = pf.force().y();
                    if (fy > 0) {
                        totalLift += fy;
                        centerOfLift.add(pf.point().x() * fy, pf.point().y() * fy, pf.point().z() * fy);
                        liftForceSum += fy;
                    }
                }
            } else if (path.equals("propulsion")) {
                for (PointForce pf : points) {
                    totalThrust.add(pf.force());
                    totalThrustMag += pf.force().length();

                    // Torque: r x F
                    Vector3d r = new Vector3d(pf.point()).sub(com);
                    Vector3d torque = new Vector3d();
                    r.cross(pf.force(), torque);
                    propulsionTorque.add(torque);
                }
            }
        }

        if (gravityY == 0 && mass > 0) {
            gravityY = mass * -9.81; // estimation
        }

        // 2. Scan the blocks on the sublevel
        LevelPlot plot = subLevel.getPlot();
        BoundingBox3ic bounds = plot.getBoundingBox();
        EmbeddedPlotLevelAccessor accessor = plot.getEmbeddedLevelAccessor();

        int seatCount = 0;
        int steeringCount = 0;
        int propellerCount = 0;
        int obstructedPropellers = 0;
        int heavyCargoCount = 0;
        int topHeavyCargoCount = 0;

        double leftCargoWeight = 0;
        double rightCargoWeight = 0;

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = accessor.getBlockState(pos);
                    if (state.isAir()) continue;

                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

                    // Check seat
                    if (blockId.contains("seat") || blockId.contains("chair")) {
                        seatCount++;
                    }

                    // Check steering helm
                    if (blockId.contains("steering") || blockId.contains("helm")) {
                        steeringCount++;
                    }

                    // Check propeller / engine
                    boolean isPropeller = blockId.contains("propeller") || blockId.contains("thruster");
                    if (isPropeller) {
                        propellerCount++;

                        Direction facing = null;
                        if (state.hasProperty(BlockStateProperties.FACING)) {
                            facing = state.getValue(BlockStateProperties.FACING);
                        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                        }

                        if (facing != null) {
                            BlockState frontState = accessor.getBlockState(pos.relative(facing));
                            if (frontState.isCollisionShapeFullBlock(accessor, pos.relative(facing))) {
                                obstructedPropellers++;
                            }
                        }
                    }

                    // Check cargo placement
                    boolean isCargo = blockId.contains("chest") || blockId.contains("barrel") 
                                    || blockId.contains("vault") || blockId.contains("shulker")
                                    || blockId.contains("crate") || blockId.contains("storage");
                    if (isCargo) {
                        heavyCargoCount++;
                        // Top heavy cargo check
                        if (pos.getY() > com.y() + 3.0) {
                            topHeavyCargoCount++;
                        }
                        // Left/Right balance
                        double dx = pos.getX() - com.x();
                        if (dx > 1.5) {
                            rightCargoWeight += 1.0;
                        } else if (dx < -1.5) {
                            leftCargoWeight += 1.0;
                        }
                    }
                }
            }
        }

        // --- RUN CHECK RULES ---

        // Check 1: Mass warnings
        Status massStatus = Status.PASS;
        String massMsg = "Mass: " + format(mass) + " kpg. Ship weight is within safe limits.";
        if (mass == 0) {
            massStatus = Status.WARNING;
            massMsg = "Mass: 0.00 kpg. No blocks registered.";
        } else if (totalLift < Math.abs(gravityY)) {
            massStatus = Status.FAIL;
            massMsg = "Too Heavy! Total lift cannot lift mass of " + format(mass) + " kpg.";
        } else if (totalLift < Math.abs(gravityY) * 1.15) {
            massStatus = Status.WARNING;
            massMsg = "Weight Limit Near! Lift margin is very narrow (" + format((totalLift / Math.abs(gravityY)) * 100.0) + "%).";
        }
        entries.add(new Entry("Mass Check", massStatus, massMsg));

        // Check 2: Lift issues
        Status liftStatus = Status.PASS;
        String liftMsg = "Lift: " + format(totalLift) + "N (Overcomes gravity of " + format(Math.abs(gravityY)) + "N).";
        if (totalLift == 0) {
            liftStatus = Status.FAIL;
            liftMsg = "No Lift! Add balloons, wings, or levitite blocks.";
        } else if (totalLift < Math.abs(gravityY)) {
            liftStatus = Status.FAIL;
            liftMsg = "Insufficient Lift! Lacking " + format(Math.abs(gravityY) - totalLift) + "N of lift force.";
        }
        entries.add(new Entry("Lift Check", liftStatus, liftMsg));

        // Check 3: Balance problems
        Status balanceStatus = Status.PASS;
        String balanceMsg = "Flight Balance: Center of Lift aligned with Center of Mass.";
        if (liftForceSum > 0) {
            centerOfLift.div(liftForceSum);
            double rx = centerOfLift.x() - com.x();
            double rz = centerOfLift.z() - com.z();

            if (Math.abs(rx) > 0.8 || Math.abs(rz) > 0.8) {
                balanceStatus = Status.WARNING;
                List<String> deviations = new ArrayList<>();
                if (Math.abs(rx) > 0.8) {
                    deviations.add(format(Math.abs(rx)) + "m " + (rx > 0 ? "right" : "left"));
                }
                if (Math.abs(rz) > 0.8) {
                    deviations.add(format(Math.abs(rz)) + "m " + (rz > 0 ? "tailward" : "noseward"));
                }
                balanceMsg = "Imbalance! Center of Lift offset by: " + String.join(", ", deviations) + ".";
            }
        } else {
            balanceStatus = Status.WARNING;
            balanceMsg = "Imbalance: No lift sources to calculate Center of Lift.";
        }
        entries.add(new Entry("Balance Check", balanceStatus, balanceMsg));

        // Check 4: Thrust concerns
        Status thrustStatus = Status.PASS;
        String thrustMsg = "Propulsion: Good forward thrust configuration.";
        if (totalThrustMag == 0) {
            thrustStatus = Status.WARNING;
            thrustMsg = "No Engines! No active engines or propellers detected.";
        } else {
            double yawTorque = propulsionTorque.y();
            double pitchTorque = propulsionTorque.x();

            if (Math.abs(yawTorque) > 15.0 || Math.abs(pitchTorque) > 15.0) {
                thrustStatus = Status.WARNING;
                if (Math.abs(yawTorque) > 15.0 && Math.abs(pitchTorque) > 15.0) {
                    thrustMsg = "Uneven Thrust! Engines cause both pitch and yaw rotation.";
                } else if (Math.abs(yawTorque) > 15.0) {
                    thrustMsg = "Asymmetric Thrust! Engine placement will pull the ship sideways.";
                } else {
                    thrustMsg = "Pitching Thrust! Vertical engine offset will cause ship to flip.";
                }
            }
        }
        entries.add(new Entry("Thrust Check", thrustStatus, thrustMsg));

        // Check 5: Blocked parts
        Status blockedStatus = Status.PASS;
        String blockedMsg = "Clearance: All thrusters/propellers have clear airflow.";
        if (propellerCount == 0) {
            blockedMsg = "Clearance: No thrusters/propellers found.";
        } else if (obstructedPropellers > 0) {
            blockedStatus = Status.FAIL;
            blockedMsg = "Obstructed Parts! " + obstructedPropellers + " propeller(s) blocked by solid blocks.";
        }
        entries.add(new Entry("Clearance Check", blockedStatus, blockedMsg));

        // Check 6: Missing essentials
        Status essentialStatus = Status.PASS;
        String essentialMsg = "Essentials: Helm and Seat installed.";
        if (seatCount == 0 && steeringCount == 0) {
            essentialStatus = Status.FAIL;
            essentialMsg = "Unflyable! Missing pilot seat and steering helm.";
        } else if (seatCount == 0) {
            essentialStatus = Status.FAIL;
            essentialMsg = "Missing pilot seat! Pilot cannot board the ship.";
        } else if (steeringCount == 0) {
            essentialStatus = Status.FAIL;
            essentialMsg = "Missing steering helm! The ship cannot be steered.";
        }
        entries.add(new Entry("Essentials Check", essentialStatus, essentialMsg));

        // Check 7: Cargo placement risks
        Status cargoStatus = Status.PASS;
        String cargoMsg = "Cargo: Securely placed and balanced.";
        if (heavyCargoCount > 0) {
            if (topHeavyCargoCount > 3) {
                cargoStatus = Status.WARNING;
                cargoMsg = "Top-Heavy Cargo! Heavy containers placed high above CoM, risk of capsizing.";
            } else if (Math.abs(leftCargoWeight - rightCargoWeight) > 4.0) {
                cargoStatus = Status.WARNING;
                cargoMsg = "Cargo Imbalance! Heavy cargo is lopsided to the " + (leftCargoWeight > rightCargoWeight ? "left" : "right") + ".";
            }
        }
        entries.add(new Entry("Cargo Check", cargoStatus, cargoMsg));

        return entries;
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
