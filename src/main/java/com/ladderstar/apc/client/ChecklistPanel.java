package com.ladderstar.apc.client;

import com.ladderstar.apc.ChecklistDiagnostics;
import com.ladderstar.apc.ChecklistDiagnostics.Entry;
import dev.simulated_team.simulated.index.SimGUITextures;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class ChecklistPanel {
    public static final int MAX_WIDTH = 180;

    public static int render(GuiGraphics guiGraphics, Font font, int guiLeft, int guiTop, int windowWidth, int windowHeight, float offset, List<Entry> entries, int scrollOffset, int panelWidth) {
        if (offset <= 0) return 0;

        // Flush any pending rendering to ensure previous elements (like force lines) are drawn before we change scissor states
        guiGraphics.flush();

        // Save OpenGL scissor state
        boolean wasScissorEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int[] oldScissor = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, oldScissor);

        int xStart = guiLeft + windowWidth;
        int yStart = guiTop;
        int yEnd = yStart + windowHeight;

        int xStartScissor = xStart;
        int xEndScissor = xStart + (int) offset;

        if (xStartScissor >= xEndScissor) return 0;

        // Enable scissor for the entire panel to clip anything sliding in/out
        guiGraphics.enableScissor(xStartScissor, yStart, xEndScissor, yEnd);

        // Draw the diagram paper texture scaled to panelWidth and windowHeight using PoseStack scaling (static relative to xStart)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(xStart, yStart, 0.0f);
        guiGraphics.pose().scale(panelWidth / (float) SimGUITextures.DIAGRAM_PAPER.width, windowHeight / (float) SimGUITextures.DIAGRAM_PAPER.height, 1.0f);
        SimGUITextures.DIAGRAM_PAPER.render(guiGraphics, 0, 0);
        guiGraphics.pose().popPose();

        // Fill the background of the checklist to hide the ruled lines and coffee stain, preserving borders
        int fillLeft = xStart + (int) (5.0 * panelWidth / 96.0);
        int fillRight = xStart + (int) (90.0 * panelWidth / 96.0);
        int fillTop = yStart + (int) (5.0 * windowHeight / 128.0);
        int fillBottom = yStart + (int) (121.0 * windowHeight / 128.0);
        guiGraphics.fill(fillLeft, fillTop, fillRight, fillBottom, 0xFFF9F4E5);

        int textX = xStart + 8;
        int textY = yStart + 10;

        // Draw Header (fixed relative to panel, revealed as it unrolls)
        guiGraphics.drawString(font, "✈ Preflight Checklist", textX, textY, 0xFF4F5257, false);

        // Apply scissor to clip scrolling entries below the header, adjusted for unrolling panel bounds
        int scissorLeft = xStart + 3;
        int scissorRight = xStart + (int) offset - 3;
        int totalContentHeight = 0;

        if (scissorLeft < scissorRight) {
            guiGraphics.enableScissor(scissorLeft, yStart + 24, scissorRight, yEnd - 20);

            int entryY = yStart + 26 - scrollOffset;
            int initialEntryY = yStart + 26;

            // Draw Entries
            for (Entry entry : entries) {
                // Status Icon
                guiGraphics.drawString(font, entry.status.icon, textX, entryY, entry.status.color, false);
                
                // Title
                guiGraphics.drawString(font, entry.title, textX + 12, entryY, 0xFF4F5257, false);

                // Message (wrapped beneath title)
                int msgY = entryY + 10;
                String msg = entry.message;
                List<String> wrappedLines = wrapText(font, msg, panelWidth - 24);
                for (String line : wrappedLines) {
                    guiGraphics.drawString(font, line, textX + 12, msgY, 0xFF777777, false);
                    msgY += 9;
                }

                entryY = msgY + 4;
            }

            guiGraphics.disableScissor(); // Disable entry scissor

            // Calculate total content height (excluding static header)
            totalContentHeight = entryY + scrollOffset - initialEntryY;
        }

        // Render scrollbar if content exceeds visible area (revealed at the end of unrolling)
        int visibleHeight = windowHeight - 44; // height below header excluding bottom transparent margin
        if (totalContentHeight > visibleHeight) {
            int maxScroll = totalContentHeight - visibleHeight;
            int trackX = xStart + panelWidth - 6;
            int trackY = yStart + 26;
            int trackHeight = visibleHeight - 4;
            int trackColor = 0x224F5257;

            int thumbHeight = Math.max(15, (trackHeight * trackHeight) / totalContentHeight);
            int thumbY = trackY + (scrollOffset * (trackHeight - thumbHeight)) / maxScroll;

            // Draw track
            guiGraphics.fill(trackX, trackY, trackX + 3, trackY + trackHeight, trackColor);
            // Draw thumb
            guiGraphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF4F5257);
        }

        // Flush our checklist rendering before we disable and restore the scissor
        guiGraphics.flush();

        // Disable outer panel scissor
        guiGraphics.disableScissor();

        // Restore OpenGL scissor state
        if (wasScissorEnabled) {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            org.lwjgl.opengl.GL11.glScissor(oldScissor[0], oldScissor[1], oldScissor[2], oldScissor[3]);
        } else {
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        }

        return totalContentHeight;
    }

    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (font.width(testLine) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}
