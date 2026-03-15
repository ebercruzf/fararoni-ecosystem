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
package dev.fararoni.core.core.security.auth;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

public class ConsoleQrRenderer {
    private static final String BLOCK = "\u2588\u2588";
    private static final String SPACE = "  ";

    public void printQrToConsole(String data) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, "UTF-8",
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN, 1
            );

            BitMatrix matrix = new MultiFormatWriter()
                    .encode(data, BarcodeFormat.QR_CODE, 0, 0, hints);

            StringBuilder sb = new StringBuilder("\n");
            for (int y = 0; y < matrix.getHeight(); y++) {
                sb.append("     ");
                for (int x = 0; x < matrix.getWidth(); x++) {
                    sb.append(matrix.get(x, y) ? BLOCK : SPACE);
                }
                sb.append('\n');
            }
            System.out.println(sb);
        } catch (Exception e) {
            System.err.println("  No se pudo renderizar el QR: " + e.getMessage());
        }
    }
}
