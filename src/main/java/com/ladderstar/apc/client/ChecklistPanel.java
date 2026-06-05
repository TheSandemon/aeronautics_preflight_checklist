package com.ladderstar.apc.client;

import com.ladderstar.apc.ChecklistDiagnostics;
import com.ladderstar.apc.ChecklistDiagnostics.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class ChecklistPanel {
    public static final int MAX_WIDTH = 180;

    public static int render(GuiGraphics guiGraphics, Font font, int guiLeft, int guiTop, int windowWidth, int windowHeight, float offset, List<Entry> entries, int scrollOffset) {
        if (offset <= 0) return 0;

        int xStart = guiLeft + windowWidth;
        int xEnd = xStart + (int) offset;
        int yStart = guiTop;
        int yEnd = yStart + windowHeight;

        // Draw cream paper background
        guiGraphics.fill(xStart, yStart, xEnd, yEnd, 0xFFF7F0DD);

        // Draw dark border matching diagram screen line color
        int borderColor = 0xFF4F5257;
        guiGraphics.fill(xStart, yStart, xStart + 1, yEnd, borderColor); // Divider line
        guiGraphics.fill(xStart, yStart, xEnd, yStart + 1, borderColor); // Top border
        guiGraphics.fill(xStart, yEnd - 1, xEnd, yEnd, borderColor); // Bottom border
        guiGraphics.fill(xEnd - 1, yStart, xEnd, yEnd, borderColor); // Right border

        // If offset is too small, don't render text (avoid clipping issues)
        if (offset < 40) return 0;

        int textX = xStart + 8;
        int textY = yStart + 10;

        // Draw Header (fixed, not affected by scroll)
        guiGraphics.drawString(font, "✈ Preflight Checklist", textX, textY, 0xFF4F5257, false);
        guiGraphics.fill(textX, textY + 11, xStart + MAX_WIDTH - 8, textY + 12, 0xFF4F5257); // Underline

        // Apply scissor to clip scrolling entries below the header
        guiGraphics.enableScissor(xStart + 3, yStart + 24, xEnd - 3, yEnd - 4);

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
            List<String> wrappedLines = wrapText(font, msg, MAX_WIDTH - 24);
            for (String line : wrappedLines) {
                guiGraphics.drawString(font, line, textX + 12, msgY, 0xFF777777, false);
                msgY += 9;
            }

            entryY = msgY + 4;
        }

        guiGraphics.disableScissor();

        // Calculate total content height (excluding static header)
        int totalContentHeight = entryY + scrollOffset - initialEntryY;

        // Render scrollbar if content exceeds visible area
        int visibleHeight = windowHeight - 30; // height below header
        if (totalContentHeight > visibleHeight) {
            int maxScroll = totalContentHeight - visibleHeight;
            int trackX = xStart + MAX_WIDTH - 6;
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
