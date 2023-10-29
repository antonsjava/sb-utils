package sk.antons.sbutils.ws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import sk.antons.jaul.xml.XmlFormat;


public class SBWSLoggingInterceptor implements ClientInterceptor {

    private Consumer<String> logger = null;
    private BooleanSupplier loggerEnabled = null;
    private boolean forceOneLine = false;

    private SBWSLoggingInterceptor() {}
    public static SBWSLoggingInterceptor instance() { return new SBWSLoggingInterceptor(); }

    public SBWSLoggingInterceptor logger(Consumer<String> value) { this.logger = value; return this; }
    public SBWSLoggingInterceptor loggerEnabled(BooleanSupplier value) { this.loggerEnabled = value; return this; }
    public SBWSLoggingInterceptor forceOneLine(boolean value) { this.forceOneLine = value; return this; }

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        log(messageContext.getRequest(), "soap-out["+counterNext()+"]");
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) {
        log(messageContext.getResponse(), " soap-in["+counter()+"]");
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) {
        log(messageContext.getResponse(), " soap-in["+counter()+"]");
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) {
        // nothing to do here
    }

    private void log(WebServiceMessage message, String messageType) {
        if (loggerEnabled.getAsBoolean()) {
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                message.writeTo(stream);
                String xml = stream.toString(StandardCharsets.UTF_8);
                if(forceOneLine) xml = XmlFormat.instance(xml, 0).forceoneline().format();
                logger.accept(messageType + ": " + xml);
            } catch (IOException e) {
                throw new WebServiceIOException("Error logging " + messageType, e);
            }
        }
    }

    private static int counter = 0;
    private static ThreadLocal<Integer> counterCache = new ThreadLocal<Integer>();

    private int counter() {
        if(counterCache.get() == null) return 0;
        return counterCache.get();
    }
    private int counterNext() {
        int c = ++counter;
        counterCache.set(c);
        return c;
    }
}
