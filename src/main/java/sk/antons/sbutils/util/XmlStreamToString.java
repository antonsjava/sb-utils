/*
 *
 */
package sk.antons.sbutils.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Function;
import java.util.stream.Collectors;
import sk.antons.jaul.xml.XmlFormat;


/**
 *
 * @author antons
 */
public class XmlStreamToString {
    String encoding = "utf-8";
    boolean formated;
    boolean forceOneLine;
    String indent;
    int cufStringLiteralsLength;
    public static XmlStreamToString instance() { return new XmlStreamToString(); }
    public XmlStreamToString encoding(String value) { this.encoding = value; return this; }
    public XmlStreamToString forceOneLine() { this.forceOneLine = true; this.formated = true; return this; }
    public XmlStreamToString indent(String value) { this.indent = value; this.formated = true; return this; }
    public XmlStreamToString cufStringLiterals(int value) { this.cufStringLiteralsLength = value; this.formated = true; return this; }

    public Function<InputStream, String> transform() {
        if(formated) return formatted();
        else return  asIs();
    }

    private Function<InputStream, String> asIs() {
        return is -> {
            return readStream(is, encoding);
        };
    }

    private Function<InputStream, String> formatted() {
        return is -> {
            String xml = readStream(is, encoding);
            XmlFormat format = XmlFormat.instance(xml, 0);
            if(cufStringLiteralsLength > 0) format.cutStringLiterals(cufStringLiteralsLength);
            if(indent != null) format.indent(indent);
            else if(forceOneLine) format.forceoneline();
            return format.format();
        };
    }

    private static String readStream(InputStream is, String encoding) {
        if(is == null) return null;
        try {
            InputStreamReader isr = new InputStreamReader(is, encoding);
            String s = new BufferedReader(isr).lines()
                .collect(Collectors.joining("\n"));
            return s;
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }

}

