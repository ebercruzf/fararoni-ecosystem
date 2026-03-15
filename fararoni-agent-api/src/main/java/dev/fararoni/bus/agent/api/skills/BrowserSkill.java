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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RateLimit;
import dev.fararoni.bus.agent.api.security.RequiresRole;
import dev.fararoni.bus.agent.api.state.StatefulSkill;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Contract for browser automation and web interaction.
 *
 * <p>This interface defines web automation operations using a headless browser.
 * Maintains browser sessions for efficient multi-step web interactions.</p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Web scraping with JavaScript rendering</li>
 *   <li>E2E testing automation</li>
 *   <li>Form automation and data entry</li>
 *   <li>Screenshot and PDF generation</li>
 *   <li>Web API testing (in-browser)</li>
 * </ul>
 *
 * <h2>Session Management</h2>
 * <pre>
 * Browser Session Lifecycle:
 * ┌────────────────────────────────────────────────────────────┐
 * │ openSession() ──► Navigate ──► Interact ──► closeSession() │
 * │      │               │            │               │        │
 * │      │         Multiple pages     │               │        │
 * │      │         ◄─────────────────►│               │        │
 * │      │                            │               │        │
 * │      │         Cookies/Auth       │               │        │
 * │      │         persisted          │               │        │
 * └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>URLs validated against allowlist (configurable)</li>
 *   <li>Credential injection requires elevated role</li>
 *   <li>All navigation logged for audit</li>
 *   <li>Rate limited to prevent abuse</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Open browser session
 * FNLResult<SessionHandle> session = browserSkill.openSession("{}");
 * String sessionId = session.data().sessionId();
 *
 * // Navigate to page
 * browserSkill.navigate(sessionId, "https://example.com/login");
 *
 * // Fill and submit form
 * browserSkill.type(sessionId, "#username", "user@example.com");
 * browserSkill.type(sessionId, "#password", "secret");
 * browserSkill.click(sessionId, "button[type='submit']");
 *
 * // Wait for navigation
 * browserSkill.waitForSelector(sessionId, ".dashboard", 5000);
 *
 * // Extract data
 * FNLResult<String> text = browserSkill.getText(sessionId, ".welcome-message");
 *
 * // Take screenshot
 * browserSkill.screenshot(sessionId, "/tmp/dashboard.png", true);
 *
 * // Close session
 * browserSkill.closeSession(sessionId);
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see StatefulSkill
 */
public interface BrowserSkill extends StatefulSkill {

    // ==================== Navigation ====================

    /**
     * Navigates to a URL.
     *
     * @param sessionId the browser session
     * @param url the URL to navigate to
     * @return result indicating success
     */
    @AgentAction(
        name = "navigate",
        description = "Navigates to a URL in the browser"
    )
    @RateLimit(calls = 30, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "INFO", category = "BROWSER_NAVIGATE")
    FNLResult<PageInfo> navigate(
        String sessionId,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.DEFAULT) String url
    );

    /**
     * Navigates back in history.
     *
     * @param sessionId the browser session
     * @return result indicating success
     */
    @AgentAction(
        name = "go_back",
        description = "Navigates to the previous page"
    )
    FNLResult<PageInfo> goBack(String sessionId);

    /**
     * Navigates forward in history.
     *
     * @param sessionId the browser session
     * @return result indicating success
     */
    @AgentAction(
        name = "go_forward",
        description = "Navigates to the next page"
    )
    FNLResult<PageInfo> goForward(String sessionId);

    /**
     * Reloads the current page.
     *
     * @param sessionId the browser session
     * @return result indicating success
     */
    @AgentAction(
        name = "reload",
        description = "Reloads the current page"
    )
    FNLResult<PageInfo> reload(String sessionId);

    // ==================== Element Interaction ====================

    /**
     * Clicks an element.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the element
     * @return result indicating success
     */
    @AgentAction(
        name = "click",
        description = "Clicks an element matched by CSS selector"
    )
    @AuditLog(severity = "INFO", category = "BROWSER_INTERACT")
    FNLResult<Void> click(String sessionId, String selector);

    /**
     * Types text into an input element.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the input
     * @param text the text to type
     * @return result indicating success
     */
    @AgentAction(
        name = "type",
        description = "Types text into an input field"
    )
    @AuditLog(severity = "INFO", category = "BROWSER_INTERACT", captureInputs = false)
    FNLResult<Void> type(String sessionId, String selector, String text);

    /**
     * Clears an input field.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the input
     * @return result indicating success
     */
    @AgentAction(
        name = "clear",
        description = "Clears an input field"
    )
    FNLResult<Void> clear(String sessionId, String selector);

    /**
     * Selects an option from a dropdown.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the select element
     * @param value the value to select
     * @return result indicating success
     */
    @AgentAction(
        name = "select",
        description = "Selects an option from a dropdown by value"
    )
    FNLResult<Void> select(String sessionId, String selector, String value);

    /**
     * Checks or unchecks a checkbox.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the checkbox
     * @param checked desired state
     * @return result indicating success
     */
    @AgentAction(
        name = "set_checked",
        description = "Sets checkbox/radio button state"
    )
    FNLResult<Void> setChecked(String sessionId, String selector, boolean checked);

    /**
     * Uploads a file to a file input.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the file input
     * @param filePath path to the file to upload
     * @return result indicating success
     */
    @AgentAction(
        name = "upload_file",
        description = "Uploads a file to a file input element"
    )
    @RequiresRole("browser:upload")
    @AuditLog(severity = "WARN", category = "BROWSER_UPLOAD")
    FNLResult<Void> uploadFile(
        String sessionId,
        String selector,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String filePath
    );

    // ==================== Element Queries ====================

    /**
     * Gets the text content of an element.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the element
     * @return result containing text content
     */
    @AgentAction(
        name = "get_text",
        description = "Gets the text content of an element"
    )
    FNLResult<String> getText(String sessionId, String selector);

    /**
     * Gets an attribute value from an element.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the element
     * @param attribute the attribute name
     * @return result containing attribute value
     */
    @AgentAction(
        name = "get_attribute",
        description = "Gets an attribute value from an element"
    )
    FNLResult<String> getAttribute(String sessionId, String selector, String attribute);

    /**
     * Gets the HTML content of an element.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the element
     * @param outer true for outerHTML, false for innerHTML
     * @return result containing HTML
     */
    @AgentAction(
        name = "get_html",
        description = "Gets the HTML content of an element"
    )
    FNLResult<String> getHtml(String sessionId, String selector, boolean outer);

    /**
     * Checks if an element exists and is visible.
     *
     * @param sessionId the browser session
     * @param selector CSS selector for the element
     * @return result containing visibility state
     */
    @AgentAction(
        name = "is_visible",
        description = "Checks if an element is visible on the page"
    )
    FNLResult<Boolean> isVisible(String sessionId, String selector);

    /**
     * Counts elements matching a selector.
     *
     * @param sessionId the browser session
     * @param selector CSS selector
     * @return result containing element count
     */
    @AgentAction(
        name = "count_elements",
        description = "Counts elements matching the selector"
    )
    FNLResult<Integer> countElements(String sessionId, String selector);

    // ==================== Waiting ====================

    /**
     * Waits for an element to appear.
     *
     * @param sessionId the browser session
     * @param selector CSS selector to wait for
     * @param timeoutMs maximum wait time in milliseconds
     * @return result indicating success
     */
    @AgentAction(
        name = "wait_for_selector",
        description = "Waits for an element to appear on the page"
    )
    FNLResult<Void> waitForSelector(String sessionId, String selector, long timeoutMs);

    /**
     * Waits for navigation to complete.
     *
     * @param sessionId the browser session
     * @param timeoutMs maximum wait time
     * @return result containing new page info
     */
    @AgentAction(
        name = "wait_for_navigation",
        description = "Waits for a page navigation to complete"
    )
    FNLResult<PageInfo> waitForNavigation(String sessionId, long timeoutMs);

    /**
     * Waits for a specified duration.
     *
     * @param sessionId the browser session
     * @param milliseconds time to wait
     * @return result indicating completion
     */
    @AgentAction(
        name = "wait",
        description = "Pauses execution for the specified time"
    )
    FNLResult<Void> wait(String sessionId, long milliseconds);

    // ==================== JavaScript Execution ====================

    /**
     * Executes JavaScript in the page context.
     *
     * @param sessionId the browser session
     * @param script JavaScript code to execute
     * @return result containing return value as JSON string
     */
    @AgentAction(
        name = "execute_script",
        description = "Executes JavaScript in the browser"
    )
    @RequiresRole("browser:script")
    @AuditLog(severity = "WARN", category = "BROWSER_SCRIPT")
    FNLResult<String> executeScript(String sessionId, String script);

    // ==================== Capture ====================

    /**
     * Takes a screenshot of the page.
     *
     * @param sessionId the browser session
     * @param outputPath path to save the screenshot
     * @param fullPage whether to capture full page or viewport only
     * @return result containing screenshot info
     */
    @AgentAction(
        name = "screenshot",
        description = "Takes a screenshot of the page"
    )
    FNLResult<ScreenshotInfo> screenshot(
        String sessionId,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String outputPath,
        boolean fullPage
    );

    /**
     * Generates a PDF of the page.
     *
     * @param sessionId the browser session
     * @param outputPath path to save the PDF
     * @param options PDF generation options
     * @return result containing PDF info
     */
    @AgentAction(
        name = "pdf",
        description = "Generates a PDF of the page"
    )
    FNLResult<PdfInfo> pdf(
        String sessionId,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String outputPath,
        PdfOptions options
    );

    // ==================== Page Info ====================

    /**
     * Gets current page information.
     *
     * @param sessionId the browser session
     * @return result containing page info
     */
    @AgentAction(
        name = "get_page_info",
        description = "Gets information about the current page"
    )
    FNLResult<PageInfo> getPageInfo(String sessionId);

    /**
     * Gets all cookies for the current domain.
     *
     * @param sessionId the browser session
     * @return result containing cookies
     */
    @AgentAction(
        name = "get_cookies",
        description = "Gets all cookies for the current domain"
    )
    FNLResult<List<Cookie>> getCookies(String sessionId);

    /**
     * Sets a cookie.
     *
     * @param sessionId the browser session
     * @param cookie the cookie to set
     * @return result indicating success
     */
    @AgentAction(
        name = "set_cookie",
        description = "Sets a cookie"
    )
    @AuditLog(severity = "INFO", category = "BROWSER_COOKIE")
    FNLResult<Void> setCookie(String sessionId, Cookie cookie);

    // ==================== Nested Types ====================

    /**
     * Information about the current page.
     *
     * @param url current URL
     * @param title page title
     * @param loadTimeMs page load time in milliseconds
     * @param statusCode HTTP status code
     */
    record PageInfo(
        String url,
        String title,
        long loadTimeMs,
        int statusCode
    ) {}

    /**
     * Screenshot information.
     *
     * @param path saved file path
     * @param width image width in pixels
     * @param height image height in pixels
     * @param sizeBytes file size in bytes
     */
    record ScreenshotInfo(
        String path,
        int width,
        int height,
        long sizeBytes
    ) {}

    /**
     * PDF information.
     *
     * @param path saved file path
     * @param pageCount number of pages
     * @param sizeBytes file size in bytes
     */
    record PdfInfo(
        String path,
        int pageCount,
        long sizeBytes
    ) {}

    /**
     * PDF generation options.
     *
     * @param landscape true for landscape orientation
     * @param printBackground include background graphics
     * @param scale scale factor (1.0 = 100%)
     * @param paperWidth paper width in inches
     * @param paperHeight paper height in inches
     * @param marginTop top margin in inches
     * @param marginBottom bottom margin in inches
     * @param marginLeft left margin in inches
     * @param marginRight right margin in inches
     */
    record PdfOptions(
        boolean landscape,
        boolean printBackground,
        double scale,
        double paperWidth,
        double paperHeight,
        double marginTop,
        double marginBottom,
        double marginLeft,
        double marginRight
    ) {
        public static PdfOptions defaults() {
            return new PdfOptions(false, true, 1.0, 8.5, 11, 0.5, 0.5, 0.5, 0.5);
        }
    }

    /**
     * Browser cookie.
     *
     * @param name cookie name
     * @param value cookie value
     * @param domain cookie domain
     * @param path cookie path
     * @param expires expiration timestamp (epoch millis, -1 for session)
     * @param httpOnly HTTP-only flag
     * @param secure secure flag
     * @param sameSite SameSite policy
     */
    record Cookie(
        String name,
        String value,
        String domain,
        String path,
        long expires,
        boolean httpOnly,
        boolean secure,
        String sameSite
    ) {}
}
