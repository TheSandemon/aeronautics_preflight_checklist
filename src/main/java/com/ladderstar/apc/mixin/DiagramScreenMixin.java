package com.ladderstar.apc.mixin;

import com.ladderstar.apc.ChecklistDiagnostics;
import com.ladderstar.apc.client.ChecklistPanel;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramButton;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramStickyNote;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DiagramScreen.class)
public abstract class DiagramScreenMixin extends net.createmod.catnip.gui.AbstractSimiScreen {

    protected DiagramScreenMixin(Component title) {
        super(title);
    }

    @Shadow(remap = false)
    public ClientSubLevel subLevel;

    @Shadow(remap = false)
    private DiagramDataPacket serverData;

    @Shadow(remap = false)
    private DiagramStickyNote note;

    @Unique
    private static boolean apc$checklistVisible = true;

    @Unique
    private float apc$checklistOffset = 0f;

    @Unique
    private float apc$lastChecklistOffset = 0f;

    @Unique
    private int apc$checklistScroll = 0;

    @Unique
    private int apc$maxScroll = 0;

    @Inject(method = "init", at = @At("TAIL"))
    private void apc$onInit(CallbackInfo ci) {
        int blueprintWidth = 256;
        int blueprintHeight = 192;
        int correctGuiLeft = (this.width / 2) - (blueprintWidth / 2);
        int correctGuiTop = (this.height / 2) - (blueprintHeight / 2);

        DiagramButton checklistBtn = new DiagramButton(
            dev.simulated_team.simulated.index.SimGUITextures.DIAGRAM_ICON_MAGNIFYING_GLASS,
            correctGuiLeft + 9,
            correctGuiTop + 9 + 80,
            Component.empty(),
            () -> {
                apc$checklistVisible = !apc$checklistVisible;
                if (apc$checklistVisible && this.note != null) {
                    this.note.deactivate();
                }
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f
                    )
                );
            }
        );
        checklistBtn.setDiagramTooltip(() -> Component.literal("Preflight Checklist"));
        this.addRenderableWidget(checklistBtn);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void apc$onTick(CallbackInfo ci) {
        this.apc$lastChecklistOffset = this.apc$checklistOffset;
        int targetWidth = Math.max(100, Math.min(180, (this.width - 256) / 2 - 4));
        if (apc$checklistVisible) {
            if (this.apc$checklistOffset < targetWidth) {
                this.apc$checklistOffset = Math.min(targetWidth, this.apc$checklistOffset + 20f);
            }
            if (this.note != null) {
                this.note.deactivate();
            }
        } else {
            if (this.apc$checklistOffset > 0) {
                this.apc$checklistOffset = Math.max(0f, this.apc$checklistOffset - 20f);
            } else {
                this.apc$checklistScroll = 0;
            }
        }
    }

    @Inject(method = "renderWindow", at = @At("TAIL"), remap = false)
    private void apc$onRenderWindow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        int targetWidth = Math.max(100, Math.min(180, (this.width - 256) / 2 - 4));
        if (this.apc$checklistOffset > targetWidth) {
            this.apc$checklistOffset = targetWidth;
        }
        if (this.apc$lastChecklistOffset > targetWidth) {
            this.apc$lastChecklistOffset = targetWidth;
        }
        float offset = Mth.lerp(partialTicks, this.apc$lastChecklistOffset, this.apc$checklistOffset);
        if (offset > 0) {
            int blueprintWidth = 256;
            int blueprintHeight = 192;
            int correctGuiLeft = (this.width / 2) - (blueprintWidth / 2);
            int correctGuiTop = (this.height / 2) - (blueprintHeight / 2);
            List<ChecklistDiagnostics.Entry> entries = ChecklistDiagnostics.performChecks(this.subLevel, this.serverData);
            
            // Clamp scroll offset to current max scroll
            this.apc$checklistScroll = Mth.clamp(this.apc$checklistScroll, 0, this.apc$maxScroll);
            
            int totalContentHeight = ChecklistPanel.render(guiGraphics, this.font, correctGuiLeft, correctGuiTop, blueprintWidth, blueprintHeight, offset, entries, this.apc$checklistScroll, targetWidth);
            
            int visibleHeight = blueprintHeight - 44; // visible area height below header
            this.apc$maxScroll = Math.max(0, totalContentHeight - visibleHeight);
        }
    }

    @Inject(method = "updateNote", at = @At("HEAD"), cancellable = true, remap = false)
    private void apc$onUpdateNote(double mouseX, double mouseY, boolean clicked, CallbackInfo ci) {
        if (apc$checklistVisible) {
            ci.cancel();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (apc$checklistVisible) {
            int targetWidth = Math.max(100, Math.min(180, (this.width - 256) / 2 - 4));
            int blueprintWidth = 256;
            int correctGuiLeft = (this.width / 2) - (blueprintWidth / 2);
            if (mouseX >= correctGuiLeft + blueprintWidth && mouseX <= correctGuiLeft + blueprintWidth + targetWidth) {
                this.apc$checklistScroll = Mth.clamp(this.apc$checklistScroll - (int)(scrollY * 15), 0, this.apc$maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
