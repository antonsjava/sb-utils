/*
 *
 */
package sk.antons.sbutils.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;
import sk.antons.jaul.binary.Bytes;
import sk.antons.jaul.util.AsRuntimeEx;
import sk.antons.jaul.xml.XmlFormat;
/**
 *
 * @author antons
 */
public class CxfLogInterceptor extends AbstractSoapInterceptor {
    private static final String LOG_SETUP = CxfLogInterceptor.class.getName() + ".log-setup";
    private Consumer<String> logger = null;
    private BooleanSupplier loggerEnabled = null;
    private boolean out = false;
    private boolean forceOneLine = false;

    public CxfLogInterceptor(boolean out) {
        super(out?Phase.MARSHAL:Phase.RECEIVE);
        this.out = out;
    }
    public static CxfLogInterceptor out() { return new CxfLogInterceptor(true); }
    public static CxfLogInterceptor in() { return new CxfLogInterceptor(false); }

    public static CxfLogInterceptor instance(boolean out) {
        return new CxfLogInterceptor(out);
    }

    public CxfLogInterceptor logger(Consumer<String> value) { this.logger = value; return this; }
    public CxfLogInterceptor loggerEnabled(BooleanSupplier value) { this.loggerEnabled = value; return this; }
    public CxfLogInterceptor forceOneLine(boolean value) { this.forceOneLine = value; return this; }


    private static int counter = 0;
    private static ThreadLocal<Integer> counterCache = new ThreadLocal<Integer>();
    private static ThreadLocal<SnifferOutputStream> outstreamCache = new ThreadLocal<SnifferOutputStream>();

    private int counter() {
        if(counterCache.get() == null) return 0;
        return counterCache.get();
    }
    private int counterNext() {
        int c = ++counter;
        counterCache.set(c);
        return c;
    }

    private void printOutStream() {
        if(outstreamCache.get() == null) return;
        outstreamCache.get().print();
    }

    private void registerOutStream(SnifferOutputStream stream) {
        outstreamCache.set(stream);
    }


    @Override
    public void handleMessage(org.apache.cxf.binding.soap.SoapMessage message) throws Fault {
        try {
            boolean logged = message.containsKey(LOG_SETUP);
            if (!logged) {
                message.put(LOG_SETUP, Boolean.TRUE);
                if(out) {
                    OutputStream os = message.getContent(OutputStream.class);
                    if(os != null)  {
                        SnifferOutputStream stream = SnifferOutputStream.instance(os, counterNext(), forceOneLine, logger, loggerEnabled);
                        registerOutStream(stream);
                        message.setContent(OutputStream.class, stream);
                    } else {
                        if((loggerEnabled != null) && loggerEnabled.getAsBoolean() && (logger != null)) logger.accept("soap-out["+counterNext()+"]: no data to log");
                    }
                } else {
                    printOutStream();
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    InputStream is = message.getContent(InputStream.class);
                    if(is != null)  {
                        Bytes.transfer(is, bout);
                        String xml = bout.toString();
                        message.setContent(InputStream.class, new ByteArrayInputStream(bout.toByteArray()));
                        if(forceOneLine) xml = XmlFormat.instance(xml, 0).forceoneline().format();
                        if((loggerEnabled != null) && loggerEnabled.getAsBoolean() && (logger != null)) logger.accept(" soap-in["+counter()+"]: "+ xml);
                    } else {
                        if((loggerEnabled != null) && loggerEnabled.getAsBoolean() && (logger != null)) logger.accept(" soap-in["+counter()+"]: no data to log");
                    }
                }
            }
        } catch (Exception e) {
            throw AsRuntimeEx.state(e);
        }
    }


    private static class SnifferOutputStream extends OutputStream {
        OutputStream os;
        int counter;
        boolean forceOneLine;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Consumer<String> logger = null;
        BooleanSupplier loggerEnabled = null;

        public SnifferOutputStream(OutputStream os, int counter, boolean forceOneLine
            , Consumer<String> logger
            , BooleanSupplier loggerEnabled
        ) {
            this.os = os;
            this.counter = counter;
            this.forceOneLine = forceOneLine;
            this.logger = logger;
            this.loggerEnabled = loggerEnabled;
        }

        public static SnifferOutputStream instance(OutputStream os, int counter, boolean format, Consumer<String> logger, BooleanSupplier loggerEnabled) { return new SnifferOutputStream(os, counter, format, logger, loggerEnabled); }

        @Override
        public void close() throws IOException {
            //super.close();
            os.close();
            print();
        }

        @Override
        public void flush() throws IOException {
            //super.flush();
            os.flush();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            //super.write(b, off, len);
            os.write(b, off, len);
            baos.write(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            //super.write(b);
            os.write(b);
            baos.write(b);
        }

        @Override
        public void write(int i) throws IOException {
            os.write(i);
            baos.write(i);
        }

        private boolean notprinted = true;
        public void print() {
            if((loggerEnabled != null) && loggerEnabled.getAsBoolean() && (logger != null)) {
                if(notprinted) {
                    notprinted = false;
                    String xml = baos.toString();
                    if(forceOneLine) xml = XmlFormat.instance(xml, 0).forceoneline().format();
                    logger.accept("soap-out["+counter+"]: "+ xml);
                }
            }
        }
    }
}
