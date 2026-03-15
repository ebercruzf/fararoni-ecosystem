/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.ui.menu;

import dev.fararoni.core.ui.OutputCoordinator;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import dev.fararoni.core.ui.TerminalCapabilityDetector;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class InteractiveMenu {
    private final TerminalCapabilityDetector.DisplayMode displayMode;
    private final OutputCoordinator outputCoordinator;
    private Terminal terminal;

    private final String topLeft, topRight, bottomLeft, bottomRight;
    private final String horizontal, vertical, horizontalDown;

    public InteractiveMenu(TerminalCapabilityDetector.DisplayMode displayMode,
                           OutputCoordinator outputCoordinator) {
        this.displayMode = displayMode;
        this.outputCoordinator = outputCoordinator;

        if (displayMode.supportsColors() && displayMode != TerminalCapabilityDetector.DisplayMode.COMPATIBLE) {
            this.topLeft = "╭";
            this.topRight = "╮";
            this.bottomLeft = "╰";
            this.bottomRight = "╯";
            this.horizontal = "─";
            this.vertical = "│";
            this.horizontalDown = "├";
        } else {
            this.topLeft = "+";
            this.topRight = "+";
            this.bottomLeft = "+";
            this.bottomRight = "+";
            this.horizontal = "-";
            this.vertical = "|";
            this.horizontalDown = "+";
        }
    }

    public ConfirmationResult showConfirmationMenu(String question, String filename) {
        List<MenuOption> options = List.of(
            new MenuOption("1", "Yes", "Approve this change"),
            new MenuOption("2", "Yes, allow all edits during this session", "Auto-approve all future changes"),
            new MenuOption("3", "No, and tell me what to do differently", "Provide feedback for refinement"),
            new MenuOption("ESC", "Cancel", "Cancel the operation")
        );

        var result = showGenericMenu(question, filename, options);

        return switch (result.selectedKey()) {
            case "1" -> new ConfirmationResult(ConfirmationDecision.APPROVE, null);
            case "2" -> new ConfirmationResult(ConfirmationDecision.APPROVE_ALL, null);
            case "3" -> handleRefinementFeedback();
            case "ESC" -> new ConfirmationResult(ConfirmationDecision.CANCELLED, "CANCELLED");
            default -> new ConfirmationResult(ConfirmationDecision.CANCELLED, "UNKNOWN");
        };
    }

    public MenuResult showGenericMenu(String title, String subtitle, List<MenuOption> options) {
        try {
            terminal = TerminalBuilder.builder().system(true).build();
            terminal.enterRawMode();

            int selectedIndex = 0;
            boolean selecting = true;

            if (displayMode.supportsColors()) {
                terminal.puts(InfoCmp.Capability.cursor_invisible);
                terminal.flush();
            }

            while (selecting) {
                clearAndRedraw(title, subtitle, options, selectedIndex);

                int key = terminal.reader().read();

                switch (key) {
                    case 65 -> {
                        selectedIndex = (selectedIndex > 0) ? selectedIndex - 1 : options.size() - 1;
                    }
                    case 66 -> {
                        selectedIndex = (selectedIndex < options.size() - 1) ? selectedIndex + 1 : 0;
                    }
                    case 'w', 'W' -> {
                        selectedIndex = (selectedIndex > 0) ? selectedIndex - 1 : options.size() - 1;
                    }
                    case 's', 'S' -> {
                        selectedIndex = (selectedIndex < options.size() - 1) ? selectedIndex + 1 : 0;
                    }
                    case 13, 10 -> {
                        selecting = false;
                    }
                    case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                        int numericChoice = key - '1';
                        if (numericChoice < options.size()) {
                            selectedIndex = numericChoice;
                            selecting = false;
                        }
                    }
                    case 27 -> {
                        cleanup();
                        return new MenuResult("ESC", "Cancelled", -1);
                    }
                    case 91 -> {
                        int next = terminal.reader().read();
                        if (next == 65) {
                            selectedIndex = (selectedIndex > 0) ? selectedIndex - 1 : options.size() - 1;
                        } else if (next == 66) {
                            selectedIndex = (selectedIndex < options.size() - 1) ? selectedIndex + 1 : 0;
                        }
                    }
                }
            }

            cleanup();

            var selectedOption = options.get(selectedIndex);
            return new MenuResult(selectedOption.key(), selectedOption.text(), selectedIndex);
        } catch (IOException e) {
            outputCoordinator.printError("Error en menu interactivo: " + e.getMessage());
            cleanup();
            return new MenuResult("ERROR", "Terminal error", -1);
        }
    }

    private void clearAndRedraw(String title, String subtitle, List<MenuOption> options, int selectedIndex) {
        try {
            if (displayMode == TerminalCapabilityDetector.DisplayMode.RICH) {
                terminal.puts(InfoCmp.Capability.clear_screen);
            } else {
                for (int i = 0; i < 10; i++) {
                    System.out.println();
                }
            }

            terminal.flush();

            drawMenuBox(title, subtitle, options, selectedIndex);
        } catch (Exception e) {
            System.out.println("\nError con terminal, usando modo fallback:");
            drawMenuBoxFallback(title, subtitle, options, selectedIndex);
        }
    }

    private void drawMenuBox(String title, String subtitle, List<MenuOption> options, int selectedIndex) {
        int boxWidth = calculateBoxWidth(title, subtitle, options);
        String borderColor = displayMode.supportsColors() ? ansi().fgCyan().toString() : "";
        String resetColor = displayMode.supportsColors() ? ansi().reset().toString() : "";

        System.out.println(borderColor + topLeft + horizontal.repeat(boxWidth - 2) + topRight + resetColor);

        System.out.println(borderColor + vertical + resetColor + " " +
                          (displayMode.supportsColors() ? ansi().bold().a(title).reset() : title) +
                          " ".repeat(boxWidth - title.length() - 3) +
                          borderColor + vertical + resetColor);

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            System.out.println(borderColor + vertical + resetColor + " " +
                              (displayMode.supportsColors() ? ansi().fgYellow().a(subtitle).reset() : subtitle) +
                              " ".repeat(boxWidth - subtitle.length() - 3) +
                              borderColor + vertical + resetColor);
        }

        System.out.println(borderColor + horizontalDown + horizontal.repeat(boxWidth - 2) +
                          horizontal.replace(horizontalDown.charAt(0), '┤') + resetColor);

        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            boolean isSelected = (i == selectedIndex);

            String prefix = isSelected ? " ❯ " : "   ";
            String optionText = option.text();
            String line = prefix + optionText;

            if (isSelected && displayMode.supportsColors()) {
                System.out.println(borderColor + vertical + ansi().fgGreen().bold().a(line).reset() +
                                  " ".repeat(boxWidth - line.length() - 2) + borderColor + vertical + resetColor);
            } else {
                String textColor = displayMode.supportsColors() ? ansi().fgBrightBlack().toString() : "";
                System.out.println(borderColor + vertical + resetColor + textColor + line + ansi().reset() +
                                  " ".repeat(boxWidth - line.length() - 2) + borderColor + vertical + resetColor);
            }
        }

        System.out.println(borderColor + bottomLeft + horizontal.repeat(boxWidth - 2) + bottomRight + resetColor);

        String helpText = displayMode.supportsColors() ?
            ansi().fgBrightBlack().a("Use ↑↓ arrows, w/s keys, or numbers to navigate. Enter to select.").reset().toString() :
            "Use arrows, w/s keys, or numbers to navigate. Enter to select.";
        System.out.println("\n" + helpText);
    }

    private void drawMenuBoxFallback(String title, String subtitle, List<MenuOption> options, int selectedIndex) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(" " + title);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            System.out.println(" " + subtitle);
        }
        System.out.println("=".repeat(60));

        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            String marker = (i == selectedIndex) ? " > " : "   ";
            System.out.println(marker + option.text());
        }

        System.out.println("=".repeat(60));
        System.out.println("Use w/s keys or numbers to navigate. Enter to select.");
    }

    private int calculateBoxWidth(String title, String subtitle, List<MenuOption> options) {
        int maxWidth = Math.max(title.length(), subtitle != null ? subtitle.length() : 0);

        for (MenuOption option : options) {
            maxWidth = Math.max(maxWidth, option.text().length() + 6);
        }

        return Math.min(Math.max(maxWidth + 4, 50), 100);
    }

    private ConfirmationResult handleRefinementFeedback() {
        cleanup();

        System.out.print(ansi().fgYellow().a("\n📝 Feedback (describe what should be changed): ").reset());
        Scanner scanner = new Scanner(System.in);
        String feedback = scanner.nextLine().trim();

        if (feedback.isEmpty()) {
            return new ConfirmationResult(ConfirmationDecision.CANCELLED, "No feedback provided");
        }

        return new ConfirmationResult(ConfirmationDecision.REJECT_AND_REFINE, feedback);
    }

    private void cleanup() {
        try {
            if (terminal != null) {
                if (displayMode.supportsColors()) {
                    terminal.puts(InfoCmp.Capability.cursor_normal);
                    terminal.flush();
                }

                terminal.close();
            }
        } catch (IOException e) {
        }
    }

    public record MenuOption(String key, String text, String description) {
        public MenuOption(String key, String text) {
            this(key, text, null);
        }
    }

    public record MenuResult(String selectedKey, String selectedText, int selectedIndex) {}

    public enum ConfirmationDecision {
        APPROVE,
        APPROVE_ALL,
        REJECT_AND_REFINE,
        CANCELLED
    }

    public record ConfirmationResult(ConfirmationDecision decision, String feedback) {}

    public static InteractiveMenu createOptimal(TerminalCapabilityDetector.DisplayMode displayMode,
                                               OutputCoordinator outputCoordinator) {
        return new InteractiveMenu(displayMode, outputCoordinator);
    }

    public static InteractiveMenu createDebug(OutputCoordinator outputCoordinator) {
        return new InteractiveMenu(TerminalCapabilityDetector.DisplayMode.RICH, outputCoordinator);
    }
}
