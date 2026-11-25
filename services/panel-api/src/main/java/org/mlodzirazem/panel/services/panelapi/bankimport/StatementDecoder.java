/*
 * Copyright (C) 2025 Stowarzyszenie Młodzi Razem
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mlodzirazem.panel.services.panelapi.bankimport;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

final class StatementDecoder {
    private static final Charset WINDOWS_1250 = Charset.forName("windows-1250");
    private static final Charset ISO_8859_2 = Charset.forName("ISO-8859-2");
    private static final String POLISH_LETTERS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ";

    private StatementDecoder() {
    }

    static String decode(byte[] data) {
        int offset = hasUtf8Bom(data) ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, data.length - offset);
        String utf8 = strict(StandardCharsets.UTF_8, buffer);
        if (utf8 != null) {
            return utf8;
        }
        String windows = strict(WINDOWS_1250, buffer);
        String iso = new String(data, offset, data.length - offset, ISO_8859_2);
        if (windows == null) {
            return iso;
        }
        return polishScore(windows) >= polishScore(iso) ? windows : iso;
    }

    private static boolean hasUtf8Bom(byte[] data) {
        return data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF;
    }

    private static @Nullable String strict(Charset charset, ByteBuffer buffer) {
        try {
            return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buffer.duplicate())
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static int polishScore(String text) {
        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            if (POLISH_LETTERS.indexOf(text.charAt(i)) >= 0) {
                score++;
            }
        }
        return score;
    }
}
