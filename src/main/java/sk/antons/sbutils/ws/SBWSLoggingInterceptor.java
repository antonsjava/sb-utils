package sk.antons.sbutils.ws;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import sk.antons.sbutils.util.XmlStreamToString;


public class SBWSLoggingInterceptor implements ClientInterceptor {

    private Consumer<String> logger = null;
    private BooleanSupplier loggerEnabled = null;
    private Function<InputStream, String> format;

    private SBWSLoggingInterceptor() {}
    public static SBWSLoggingInterceptor instance() { return new SBWSLoggingInterceptor(); }

    public SBWSLoggingInterceptor logger(Consumer<String> value) { this.logger = value; return this; }
    public SBWSLoggingInterceptor loggerEnabled(BooleanSupplier value) { this.loggerEnabled = value; return this; }
    public SBWSLoggingInterceptor format(Function<InputStream, String> value) { this.format = value; return this; }

    public static class Format {
        public static XmlStreamToString xml() { return XmlStreamToString.instance(); }
    }

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
                String xml = format == null ? stream.toString(StandardCharsets.UTF_8) : format.apply(new ByteArrayInputStream(stream.toByteArray()));
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
