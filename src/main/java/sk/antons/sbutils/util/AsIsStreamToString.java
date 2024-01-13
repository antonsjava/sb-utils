/*
 * Copyright 2018 Anton Straka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sk.antons.sbutils.util;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.function.Function;


/**
 * Converts stream to json string using io.github.antonsjava:json.
 * You can configure
 * {@code <ul>}
 * {@code <li>} encoding - encoding for conversion tpo chars
 * {@code <li>} forceOneLine - true if json should be printed as one line
 * {@code <li>} indent - indent text if json should be printed as formated
 * {@code <li>} cufStringLiterals - max length of string literal (rest will be cut)
 * {@code </ul>}
 *
 * @author antons
 */
public class AsIsStreamToString {
    String encoding = "utf-8";
    String forceOneLine;
    String newlineReplacer;
    int cutTo;
    public static AsIsStreamToString instance() { return new AsIsStreamToString(); }
    public AsIsStreamToString encoding(String value) { this.encoding = value; return this; }
    public AsIsStreamToString forceOneLine() { this.newlineReplacer = " "; return this; }
    public AsIsStreamToString newlineReplacer(String value) { this.newlineReplacer = value; return this; }
    public AsIsStreamToString cutTo(int value) { this.cutTo = value; return this; }

    public Function<InputStream, String> transform() {
        return is -> {
            return readStream(is, encoding);
        };
    }

    private String readStream(InputStream is, String encoding) {
        if(is == null) return null;
        try {
            Reader reader = new InputStreamReader(is, encoding);
            StringBuilder sb = new StringBuilder(4096);
            int index = 0;
            int c;
            while ((c = reader.read()) != -1) {
                if((cutTo > 0) && (index++ > cutTo)) {
                    sb.append("...");
                    break;
                }
                if((newlineReplacer != null) && (c == '\n')) {
                    sb.append(newlineReplacer);
                } else {
                    sb.append((char)c);
                }
            }
            return sb.toString();
        } catch(Exception e) {
            return "unable to read stream content " + e;
        }
    }

}

