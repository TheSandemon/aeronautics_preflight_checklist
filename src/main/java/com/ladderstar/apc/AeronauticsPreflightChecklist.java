package com.ladderstar.apc;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AeronauticsPreflightChecklist.MODID)
public class AeronauticsPreflightChecklist {
    public static final String MODID = "aeronautics_preflight_checklist";
    public static final Logger LOGGER = LogManager.getLogger();

    public AeronauticsPreflightChecklist(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[APC] Loading Aeronautics Preflight Checklist...");
        LOGGER.info("[APC] Loaded successfully!");
    }
}
