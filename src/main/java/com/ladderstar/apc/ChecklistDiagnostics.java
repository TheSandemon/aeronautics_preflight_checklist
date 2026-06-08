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
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayDeque;

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

        int envelopeCount = 0;
        int sailCount = 0;
        int wingCount = 0;
        int levititeCount = 0;
        Vector3d centerOfLiftProp = new Vector3d();
        double liftPropSum = 0;
        double liftProviderPropSum = 0;

        Vector3d centerOfLiftBalloons = new Vector3d();
        double liftBalloonsSum = 0;

        Vector3d centerOfLiftEnvelopes = new Vector3d();
        double envelopeLiftSum = 0;

        Vector3d centerOfLiftWings = new Vector3d();

        double hotAirStrength = 1.5;
        double steamStrength = 8.5;
        try {
            hotAirStrength = ((Number) dev.eriksonn.aeronautics.config.AeroConfig.server().physics.hotAirStrength.get()).doubleValue();
        } catch (Throwable t) {}
        try {
            steamStrength = ((Number) dev.eriksonn.aeronautics.config.AeroConfig.server().physics.steamStrength.get()).doubleValue();
        } catch (Throwable t) {}
        double defaultBalloonStrength = hotAirStrength;

        net.minecraft.world.level.Level level = subLevel.getLevel();
        double gravityVal = 9.80665;
        double airPressureVal = 1.075;
        try {
            dev.ryanhcode.sable.companion.math.Pose3d pose = subLevel.logicalPose();
            org.joml.Vector3d posVec = new org.joml.Vector3d(pose.position());
            org.joml.Vector3d gravVec = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level, posVec);
            if (gravVec != null) {
                gravityVal = gravVec.length();
            }
            airPressureVal = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getAirPressure(level, posVec);
        } catch (Throwable t) {
            // fallback
        }
        double balloonLiftMultiplier = gravityVal * airPressureVal;

        Vector3d predictedThrust = new Vector3d();
        Vector3d predictedTorque = new Vector3d();
        double predictedThrustMag = 0;

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
            }
        }

        double gravityForce = Math.abs(gravityY);
        if (gravityForce == 0 && mass > 0) {
            gravityForce = mass * gravityVal;
        }

        // 2. Scan the blocks on the sublevel using loaded chunks directly
        LevelPlot plot = subLevel.getPlot();
        BoundingBox3ic bounds = plot.getBoundingBox();
        BlockPos center = plot.getCenterBlock();

        java.util.Map<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk> loadedChunksMap = new java.util.HashMap<>();
        for (dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder holder : plot.getLoadedChunks()) {
            net.minecraft.world.level.chunk.LevelChunk chunk = holder.getChunk();
            if (chunk != null) {
                loadedChunksMap.put(chunk.getPos(), chunk);
            }
        }

        class GasProvider {
            final BlockPos pos;
            final BlockPos castPos;
            final double strength;
            final double maxCapacity;
            GasProvider(BlockPos pos, BlockPos castPos, double strength, double maxCapacity) {
                this.pos = pos;
                this.castPos = castPos;
                this.strength = strength;
                this.maxCapacity = maxCapacity;
            }
        }
        List<GasProvider> providers = new ArrayList<>();

        int controllerCount = 0;
        int propellerCount = 0;
        int obstructedPropellers = 0;

        for (net.minecraft.world.level.chunk.LevelChunk chunk : loadedChunksMap.values()) {
            net.minecraft.world.level.ChunkPos chunkPos = chunk.getPos();
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            int minY = chunk.getMinBuildHeight();
            int maxY = chunk.getMaxBuildHeight();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir()) continue;

                        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

                        // Check steering helm, wheel, joystick, or controller
                        if (blockId.contains("steering") || blockId.contains("helm") 
                            || blockId.contains("wheel") || blockId.contains("joystick") 
                            || blockId.contains("controller")) {
                            controllerCount++;
                        }

                        // Check if block is a burner or vent (gas provider)
                        boolean isBurner = blockId.contains("burner");
                        boolean isVent = blockId.contains("vent");
                        if (isBurner || isVent) {
                            BlockPos castPos = null;
                            double providerStrength = isVent ? steamStrength : hotAirStrength;
                            double maxCapacity = isVent ? 5000.0 : 500.0;
                            try {
                                if (isVent) {
                                    maxCapacity = ((Number) dev.eriksonn.aeronautics.config.AeroConfig.server().blocks.steamVentMaxHotAir.get()).doubleValue();
                                } else {
                                    maxCapacity = ((Number) dev.eriksonn.aeronautics.config.AeroConfig.server().blocks.hotAirBurnerMaxHotAir.get()).doubleValue();
                                }
                            } catch (Throwable t) {}

                            net.minecraft.world.level.block.entity.BlockEntity be = chunk.getBlockEntity(pos);
                            if (be instanceof dev.eriksonn.aeronautics.content.blocks.hot_air.BlockEntityLiftingGasProvider gasProvider) {
                                castPos = gasProvider.getCastPosition();
                                try {
                                    providerStrength = gasProvider.getLiftingGasType().getLiftStrength();
                                } catch (Throwable t) {}
                            }
                            if (castPos == null) {
                                // Simulate upwards raycast in local sublevel space to find the balloon ceiling
                                for (int checkY = pos.getY() + 1; checkY <= bounds.maxY(); checkY++) {
                                    BlockPos checkPos = new BlockPos(pos.getX(), checkY, pos.getZ());
                                    BlockState s = getBlockState(loadedChunksMap, checkPos);
                                    String checkBlockId = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                                    if (!isGasOrAir(s, checkBlockId)) {
                                        castPos = checkPos.below();
                                        break;
                                    }
                                }
                            }
                            if (castPos == null) {
                                castPos = pos.above();
                            }
                            providers.add(new GasProvider(pos, castPos, providerStrength, maxCapacity));
                        }

                        // Check lift sources
                        boolean isEnvelope = blockId.contains("envelope");
                        boolean isLevitite = blockId.contains("levitite");
                        boolean isLiftProvider = state.getBlock() instanceof dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
                        boolean isSailOrWing = blockId.contains("sail") || blockId.contains("wing");

                        if (isEnvelope) {
                            envelopeCount++;
                            double w = defaultBalloonStrength * balloonLiftMultiplier;
                            centerOfLiftEnvelopes.add((pos.getX() + 0.5) * w, (pos.getY() + 0.5) * w, (pos.getZ() + 0.5) * w);
                            envelopeLiftSum += w;
                        } else if (isLevitite) {
                            levititeCount++;
                            double w = 10.0 * gravityVal;
                            centerOfLiftProp.add((pos.getX() + 0.5) * w, (pos.getY() + 0.5) * w, (pos.getZ() + 0.5) * w);
                            liftPropSum += w;
                        } else if (isLiftProvider || isSailOrWing) {
                            float liftScalar = 0.475f; // default lift scalar fallback
                            if (isLiftProvider) {
                                float actualScalar = ((dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider) state.getBlock()).sable$getLiftScalar();
                                if (actualScalar > 0.0f) {
                                    liftScalar = actualScalar;
                                }
                            }
                            double estimatedLift = liftScalar * 18.0; // potential max lift at flight speeds
                            centerOfLiftWings.add((pos.getX() + 0.5) * estimatedLift, (pos.getY() + 0.5) * estimatedLift, (pos.getZ() + 0.5) * estimatedLift);
                            liftProviderPropSum += estimatedLift;
                            
                            if (blockId.contains("sail")) {
                                sailCount++;
                            } else {
                                wingCount++;
                            }
                        }

                        // Check propeller / engine potential thrust
                        boolean isPropeller = blockId.contains("propeller") || blockId.contains("thruster") || blockId.contains("engine");
                        if (isPropeller) {
                            propellerCount++;

                            Direction facing = null;
                            if (state.hasProperty(BlockStateProperties.FACING)) {
                                facing = state.getValue(BlockStateProperties.FACING);
                            } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                                facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                            } else if (state.hasProperty(BlockStateProperties.AXIS)) {
                                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                                facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                            } else {
                                facing = Direction.NORTH;
                            }

                            if (facing != null) {
                                boolean isBearing = blockId.contains("bearing");
                                BlockPos frontPos = isBearing ? pos.relative(facing, 2) : pos.relative(facing);
                                net.minecraft.world.level.ChunkPos frontChunkPos = new net.minecraft.world.level.ChunkPos(frontPos);
                                net.minecraft.world.level.chunk.LevelChunk frontChunk = loadedChunksMap.get(frontChunkPos);
                                 boolean isObstructed = false;
                                 if (frontChunk != null) {
                                     BlockState frontState = frontChunk.getBlockState(frontPos);
                                     String frontBlockId = BuiltInRegistries.BLOCK.getKey(frontState.getBlock()).getPath();
                                     if (frontState.isCollisionShapeFullBlock(frontChunk, frontPos)
                                         && !frontBlockId.contains("propeller")
                                         && !frontBlockId.contains("blade")
                                         && !frontBlockId.contains("sail")
                                         && !frontBlockId.contains("wing")
                                         && !frontBlockId.contains("bearing")
                                         && !frontBlockId.contains("engine")
                                         && !frontBlockId.contains("thruster")
                                         && !frontBlockId.contains("shaft")
                                         && !frontBlockId.contains("cogwheel")
                                         && !frontBlockId.contains("gearbox")
                                         && !frontBlockId.contains("gear")
                                         && !frontBlockId.contains("clutch")
                                         && !frontBlockId.contains("gearshift")
                                         && !frontBlockId.contains("motor")
                                         && !frontBlockId.contains("coupling")
                                         && !frontBlockId.contains("pulley")
                                         && !frontBlockId.contains("belt")
                                         && !frontBlockId.contains("casing")) {
                                         obstructedPropellers++;
                                         isObstructed = true;
                                     }
                                 }
                                
                                if (!isObstructed) {
                                    // Calculate predicted thrust vector
                                    double thrustMag = 15.0; // default max thrust per propeller
                                    if (isBearing) {
                                        thrustMag = 30.0; // bearings have higher thrust
                                    }
                                    Vector3d thrustDir = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                                    Vector3d force = new Vector3d(thrustDir).mul(thrustMag);
                                    predictedThrust.add(force);
                                    predictedThrustMag += thrustMag;
                                    
                                    // Torque = r x F
                                    Vector3d r = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).sub(com);
                                    Vector3d torque = new Vector3d();
                                    r.cross(force, torque);
                                    predictedTorque.add(torque);
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- RUN CHECK RULES ---

        // BFS flood fill starting from burner cast positions to calculate potential cold balloon capacity
        Set<BlockPos> visitedAir = new HashSet<>();
        double simulatedBalloonLift = 0;

        for (GasProvider provider : providers) {
            if (visitedAir.contains(provider.castPos)) continue;
            
            BlockState startState = getBlockState(loadedChunksMap, provider.castPos);
            String startBlockId = BuiltInRegistries.BLOCK.getKey(startState.getBlock()).getPath();
            if (!isGasOrAir(startState, startBlockId)) {
                continue;
            }
            
            // Run a top-down balloon search starting at provider.castPos
            Set<BlockPos> balloonBlocks = new HashSet<>();
            balloonBlocks.add(provider.castPos);
            
            int startY = provider.castPos.getY();
            for (int y = startY; y >= bounds.minY(); y--) {
                List<BlockPos> upperBlocks = new ArrayList<>();
                for (BlockPos p : balloonBlocks) {
                    if (p.getY() == y + 1) {
                        upperBlocks.add(p);
                    }
                }
                if (upperBlocks.isEmpty() && y < startY) {
                    break;
                }
                
                for (BlockPos upper : upperBlocks) {
                    BlockPos below = upper.below();
                    if (balloonBlocks.contains(below)) continue;
                    
                    BlockState state = getBlockState(loadedChunksMap, below);
                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (!isGasOrAir(state, blockId)) {
                        continue;
                    }
                    
                    Queue<BlockPos> queue = new ArrayDeque<>();
                    Set<BlockPos> visited = new HashSet<>();
                    queue.add(below);
                    visited.add(below);
                    
                    boolean leaked = false;
                    int count = 0;
                    int maxVolume = 16384;
                    
                    while (!queue.isEmpty() && count < maxVolume) {
                        BlockPos curr = queue.poll();
                        count++;
                        
                        if (!bounds.contains(curr.getX(), curr.getY(), curr.getZ())) {
                            leaked = true;
                            break;
                        }
                        
                        Direction[] dirs = { Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
                        for (Direction dir : dirs) {
                            BlockPos neighbor = curr.relative(dir);
                            if (!visited.contains(neighbor)) {
                                if (balloonBlocks.contains(neighbor)) {
                                    visited.add(neighbor);
                                    continue;
                                }
                                BlockState s = getBlockState(loadedChunksMap, neighbor);
                                String currBlockId = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                                if (isGasOrAir(s, currBlockId)) {
                                    visited.add(neighbor);
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                    
                    if (!leaked && count < maxVolume) {
                        balloonBlocks.addAll(visited);
                    }
                }
            }
            
            if (balloonBlocks.size() > 1 || isGasOrAir(getBlockState(loadedChunksMap, provider.castPos), BuiltInRegistries.BLOCK.getKey(getBlockState(loadedChunksMap, provider.castPos).getBlock()).getPath())) {
                visitedAir.addAll(balloonBlocks);
                
                double sumOfCapacities = 0;
                double maxStrengthInComponent = 0;
                for (GasProvider other : providers) {
                    if (balloonBlocks.contains(other.pos.above())) {
                        sumOfCapacities += other.maxCapacity;
                        maxStrengthInComponent = Math.max(maxStrengthInComponent, other.strength);
                    }
                }
                if (maxStrengthInComponent == 0) {
                    maxStrengthInComponent = provider.strength;
                    sumOfCapacities = provider.maxCapacity;
                }

                double effectiveVolume = Math.min((double) balloonBlocks.size(), sumOfCapacities);

                double avgX = 0, avgY = 0, avgZ = 0;
                for (BlockPos p : balloonBlocks) {
                    avgX += p.getX() + 0.5;
                    avgY += p.getY() + 0.5;
                    avgZ += p.getZ() + 0.5;
                }
                avgX /= balloonBlocks.size();
                avgY /= balloonBlocks.size();
                avgZ /= balloonBlocks.size();

                org.joml.Vector3d localAvg = new org.joml.Vector3d(avgX, avgY, avgZ);
                dev.ryanhcode.sable.companion.math.Pose3d pose = subLevel.logicalPose();
                org.joml.Vector3d worldAvg = new org.joml.Vector3d(localAvg).sub(pose.rotationPoint());
                pose.orientation().transform(worldAvg);
                worldAvg.add(pose.position());

                double compGravityVal = 9.80665;
                double compAirPressureVal = 1.075;
                try {
                    org.joml.Vector3d gravVec = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level, worldAvg);
                    if (gravVec != null) {
                        compGravityVal = gravVec.length();
                    }
                    compAirPressureVal = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getAirPressure(level, worldAvg);
                } catch (Throwable t) {}

                double compLiftMultiplier = compGravityVal * compAirPressureVal;
                double compLift = effectiveVolume * maxStrengthInComponent * compLiftMultiplier;
                simulatedBalloonLift += compLift;
                
                for (BlockPos p : balloonBlocks) {
                    centerOfLiftBalloons.add((p.getX() + 0.5) * compLift / balloonBlocks.size(), 
                                            (p.getY() + 0.5) * compLift / balloonBlocks.size(), 
                                            (p.getZ() + 0.5) * compLift / balloonBlocks.size());
                }
                liftBalloonsSum += compLift;
            }
        }

        double calculatedMaxLift = 0;
        
        // 1. Balloon lift (requires burners)
        boolean hasBurners = !providers.isEmpty();
        if (hasBurners) {
            if (simulatedBalloonLift > 0) {
                calculatedMaxLift += simulatedBalloonLift;
            }
            
            if (liftBalloonsSum > 0) {
                centerOfLiftProp.add(centerOfLiftBalloons);
                liftPropSum += liftBalloonsSum;
            }
        }
        
        // 2. Levitite lift (does not require engines or burners)
        calculatedMaxLift += levititeCount * 10.0 * gravityVal;
        
        // 3. Wing / sail lift (requires forward thrust)
        boolean hasThrust = predictedThrustMag > 0;
        if (hasThrust && liftProviderPropSum > 0) {
            calculatedMaxLift += liftProviderPropSum;
            
            centerOfLiftProp.add(centerOfLiftWings);
            liftPropSum += liftProviderPropSum;
        }

        double maxPossibleLift = calculatedMaxLift; // Preflight checklist uses simulated/calculated max lift only

        Status massLiftStatus = Status.PASS;
        StringBuilder massLiftMsg = new StringBuilder();

        if (mass == 0) {
            massLiftStatus = Status.WARNING;
            massLiftMsg.append("Mass: 0.00 kpg. No blocks registered.");
        } else {
            if (maxPossibleLift < gravityForce) {
                massLiftStatus = Status.FAIL;
                massLiftMsg.append("Insufficient Lift! Max potential lift (simulated at max heat/thrust: ").append(format(maxPossibleLift))
                        .append(" pN) cannot overcome gravity (").append(format(gravityForce))
                        .append(" pN) for mass of ").append(format(mass)).append(" kpg.");
            } else if (maxPossibleLift < gravityForce * 1.15) {
                massLiftStatus = Status.WARNING;
                massLiftMsg.append("Weight Limit Near! Max potential lift margin is very narrow (")
                        .append(format((maxPossibleLift / gravityForce) * 100.0)).append("%). Max lift: ")
                        .append(format(maxPossibleLift)).append(" pN, Gravity: ").append(format(gravityForce)).append(" pN.");
            } else {
                massLiftStatus = Status.PASS;
                massLiftMsg.append("Sufficient Lift! Max potential lift capacity (simulated at max heat/thrust: ").append(format(maxPossibleLift))
                        .append(" pN) overcomes gravity (").append(format(gravityForce))
                        .append(" pN) for mass of ").append(format(mass)).append(" kpg.");
            }
            
            // Contextual notes
            List<String> notes = new ArrayList<>();
            if (envelopeCount > 0 && !hasBurners) {
                notes.add("balloon lift is disabled because no burners (hot air creation blocks) were detected");
            }
            if (liftProviderPropSum > 0 && !hasThrust) {
                if (obstructedPropellers > 0 && propellerCount == obstructedPropellers) {
                    notes.add("wing/sail lift is disabled because all thrust sources are obstructed");
                } else {
                    notes.add("wing/sail lift is disabled because no thrust sources (engines/propellers) were detected");
                }
            }
            
            if (!notes.isEmpty()) {
                massLiftMsg.append(" Note: ").append(String.join("; ", notes)).append(".");
            }
            
            if (massLiftStatus == Status.FAIL) {
                massLiftMsg.append(" Suggestion: Add more balloons (with burners), sails/wings (with engines), or levitite blocks.");
            }
        }
        entries.add(new Entry("Mass & Lift Check", massLiftStatus, massLiftMsg.toString()));

        // Check 2: Balance problems
        Status balanceStatus = Status.PASS;
        String balanceMsg = "Flight Balance: Center of Lift aligned with Center of Mass.";
        Vector3d finalCenterOfLift = null;
        if (liftPropSum > 0) {
            centerOfLiftProp.div(liftPropSum);
            finalCenterOfLift = centerOfLiftProp;
        }

        if (finalCenterOfLift != null) {
            double rx = finalCenterOfLift.x() - com.x();
            double rz = finalCenterOfLift.z() - com.z();

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

        // Check 3: Thrust concerns
        Status thrustStatus = Status.PASS;
        String thrustMsg = "Propulsion: Good forward thrust configuration.";
        if (predictedThrustMag == 0) {
            thrustStatus = Status.WARNING;
            if (propellerCount > 0 && obstructedPropellers == propellerCount) {
                thrustMsg = "Uneven Thrust! All engines/propellers are obstructed.";
            } else {
                thrustMsg = "No Engines! No active engines or propellers detected.";
            }
        } else {
            double yawTorque = predictedTorque.y();
            double pitchTorque = predictedTorque.x();

            if (Math.abs(yawTorque) > 15.0 || Math.abs(pitchTorque) > 15.0) {
                thrustStatus = Status.WARNING;
                if (Math.abs(yawTorque) > 15.0 && Math.abs(pitchTorque) > 15.0) {
                    thrustMsg = "Uneven Thrust! Engines cause both pitch and yaw rotation (simulated at max spin).";
                } else if (Math.abs(yawTorque) > 15.0) {
                    thrustMsg = "Asymmetric Thrust! Engine placement will pull the ship sideways (simulated at max spin).";
                } else {
                    thrustMsg = "Pitching Thrust! Vertical engine offset will cause ship to flip (simulated at max spin).";
                }
            } else {
                thrustMsg = "Propulsion: Good forward thrust configuration (" + (propellerCount - obstructedPropellers) + " engine(s) simulated at max spin).";
            }
        }
        entries.add(new Entry("Thrust Check", thrustStatus, thrustMsg));
        return entries;
    }

    private static boolean isGasOrAir(BlockState state, String blockId) {
        return state.isAir() 
            || blockId.contains("hot_air") 
            || blockId.contains("gas") 
            || blockId.contains("steam")
            || state.getBlock().getClass().getSimpleName().contains("HotAir")
            || state.getBlock().getClass().getSimpleName().contains("LiftingGas")
            || state.getBlock().getClass().getSimpleName().contains("Steam");
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private static BlockState getBlockState(java.util.Map<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.LevelChunk> map, BlockPos pos) {
        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(pos);
        net.minecraft.world.level.chunk.LevelChunk chunk = map.get(cp);
        return chunk != null ? chunk.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }
}
