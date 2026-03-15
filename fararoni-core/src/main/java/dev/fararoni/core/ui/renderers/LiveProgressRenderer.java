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
package dev.fararoni.core.ui.renderers;

import dev.fararoni.bus.agent.api.ui.model.TaskNode;
import dev.fararoni.bus.agent.api.ui.model.TaskState;
import dev.fararoni.bus.agent.api.ui.model.TaskTreeModel;
import dev.fararoni.core.ui.ProgressAnimator;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LiveProgressRenderer {
    private static final String[] SPINNER_FRAMES = {
        "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };

    private static final String ICON_SUCCESS = "✔";

    private static final String ICON_FAILURE = "✘";

    private static final String ICON_WARNING = "⚠";

    private static final String ICON_SKIPPED = "↷";

    private static final String ICON_CANCELED = "⊖";

    private static final String ICON_PENDING = "◦";

    private static final String ICON_INITIALIZING = "[INIT]";

    private static final String ICON_COMPACTING = "[CMP]";

    private static final String ICON_THROTTLING = "[THR]";

    private static final String ICON_SYSTEM_OP = "[SYS]";

    private static final String GUIDE_VERTICAL = "│  ";

    private static final String GUIDE_CHILD = "├─ ";

    private static final String GUIDE_LAST = "└─ ";

    private static final String GUIDE_SPACE = "   ";

    private static final int DEFAULT_WIDTH = 80;

    private static final long SPINNER_SPEED_MS = 80;

    private final Terminal terminal;

    private final Display display;

    public LiveProgressRenderer(Terminal terminal) {
        this.terminal = Objects.requireNonNull(terminal, "terminal no puede ser null");
        this.display = new Display(terminal, true);
    }

    public void render(TaskTreeModel model) {
        Objects.requireNonNull(model, "model no puede ser null");

        List<AttributedString> lines = new ArrayList<>();
        int width = getTerminalWidth();

        renderHeader(lines, model, width);

        List<TaskNode> nodes = model.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            boolean isLast = (i == nodes.size() - 1);
            renderNode(lines, nodes.get(i), "", isLast, width);
        }

        display.resize(terminal.getHeight(), width);
        display.update(lines, -1);
    }

    public void clear() {
        display.clear();
    }

    private void renderHeader(List<AttributedString> lines, TaskTreeModel model, int width) {
        AttributedStringBuilder sb = new AttributedStringBuilder();

        if (model.rootState().isActive()) {
            sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));
            sb.append(getSpinnerFrame()).append(" ");
        } else {
            appendIcon(sb, model.rootState());
            sb.append(" ");
        }

        sb.style(AttributedStyle.DEFAULT.bold());
        String status = model.rootStatus();
        sb.append(status);

        if (model.metaInfo() != null && !model.metaInfo().isEmpty()) {
            sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK));
            sb.append(" (").append(model.metaInfo()).append(")");
        }

        lines.add(sb.toAttributedString());
    }

    private void renderNode(List<AttributedString> lines, TaskNode node,
                           String prefix, boolean isLast, int maxWidth) {
        AttributedStringBuilder sb = new AttributedStringBuilder();

        sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK));
        sb.append(prefix);

        sb.append(isLast ? GUIDE_LAST : GUIDE_CHILD);

        appendIcon(sb, node.state());

        sb.append(" ");

        applyTextStyle(sb, node.state());

        String label = node.label();
        int usedWidth = prefix.length() + 3 + 2;
        int available = maxWidth - usedWidth - 1;

        if (available > 0) {
            if (label.length() > available) {
                sb.append(label.substring(0, available - 3)).append("...");
            } else {
                sb.append(label);
            }
        }

        lines.add(sb.toAttributedString());

        if (node.hasChildren()) {
            String childPrefix = prefix + (isLast ? GUIDE_SPACE : GUIDE_VERTICAL);
            List<TaskNode> children = node.children();

            for (int i = 0; i < children.size(); i++) {
                boolean isChildLast = (i == children.size() - 1);
                renderNode(lines, children.get(i), childPrefix, isChildLast, maxWidth);
            }
        }
    }

    private void appendIcon(AttributedStringBuilder sb, TaskState state) {
        switch (state) {
            case SUCCESS -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
                sb.append(ICON_SUCCESS);
            }
            case FAILURE -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold());
                sb.append(ICON_FAILURE);
            }
            case WARNING -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                sb.append(ICON_WARNING);
            }
            case SKIPPED -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK));
                sb.append(ICON_SKIPPED);
            }
            case CANCELED -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));
                sb.append(ICON_CANCELED);
            }
            case RUNNING -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold());
                sb.append(getSpinnerFrame());
            }
            case PENDING -> {
                sb.style(AttributedStyle.DEFAULT.faint());
                sb.append(ICON_PENDING);
            }
            case INITIALIZING -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));
                sb.append(ICON_INITIALIZING);
            }
            case COMPACTING -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
                sb.append(ICON_COMPACTING);
            }
            case THROTTLING -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
                sb.append(ICON_THROTTLING);
            }
            case SYSTEM_OP -> {
                sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
                sb.append(ICON_SYSTEM_OP);
            }
            default -> {
                sb.style(AttributedStyle.DEFAULT.faint());
                sb.append(ICON_PENDING);
            }
        }
    }

    private void applyTextStyle(AttributedStringBuilder sb, TaskState state) {
        switch (state) {
            case RUNNING, SYSTEM_OP -> sb.style(AttributedStyle.DEFAULT.bold());
            case PENDING, SKIPPED -> sb.style(AttributedStyle.DEFAULT
                .foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK));
            case FAILURE -> sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
            case WARNING, THROTTLING -> sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
            case INITIALIZING -> sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));
            case COMPACTING -> sb.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE));
            default -> sb.style(AttributedStyle.DEFAULT);
        }
    }

    private String getSpinnerFrame() {
        int frameIndex = (int) ((System.currentTimeMillis() / SPINNER_SPEED_MS) % SPINNER_FRAMES.length);
        return SPINNER_FRAMES[frameIndex];
    }

    private int getTerminalWidth() {
        int width = terminal.getWidth();
        return width > 0 ? width : DEFAULT_WIDTH;
    }
}
