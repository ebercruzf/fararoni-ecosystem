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
package dev.fararoni.core.cli;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ConsoleUtils {
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    private ConsoleUtils() {
    }

    public static void printUserPrompt() {
        System.out.print(BOLD + BLUE + "Tu" + RESET + " > ");
    }

    public static void printBotResponse(String text) {
        System.out.println(BOLD + PURPLE + "Fararoni" + RESET + " > " + text);
    }

    public static void printAgentThinking(String agentRole, String action) {
        System.out.println(CYAN + "  [" + agentRole + "] " + RESET + action);
    }

    public static void printSystemInfo(String text) {
        System.out.println(YELLOW + "i " + text + RESET);
    }

    public static void printError(String text) {
        System.out.println(RED + "X " + text + RESET);
    }

    public static void printSuccess(String text) {
        System.out.println(GREEN + "OK " + text + RESET);
    }

    public static void printWarning(String text) {
        System.out.println(YELLOW + "! " + text + RESET);
    }

    public static void printMessageFlow(String from, String to, String messageType) {
        System.out.println(DIM + "  [" + from + "] --(" + messageType + ")--> [" + to + "]" + RESET);
    }

    public static void printSeparator(int width) {
        System.out.println(DIM + "-".repeat(width) + RESET);
    }

    public static void printHeader(String title) {
        int padding = (40 - title.length()) / 2;
        String line = "=".repeat(Math.max(padding, 4));
        System.out.println(BOLD + line + " " + title + " " + line + RESET);
    }

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void printColored(String text, String color) {
        System.out.println(color + text + RESET);
    }

    public static String colored(String text, String color) {
        return color + text + RESET;
    }
}
