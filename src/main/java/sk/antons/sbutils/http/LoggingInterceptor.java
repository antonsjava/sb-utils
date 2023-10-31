/*
 *
 */
package sk.antons.sbutils.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import sk.antons.sbutils.util.JsonStreamToString;
import sk.antons.sbutils.util.XmlStreamToString;

/**
 *
 * @author antons
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private Consumer<String> logger = null;
    private BooleanSupplier loggerEnabled = null;
    private Function<HttpHeaders, String> requestHeaders = null;
    private Function<HttpHeaders, String> responseHeaders = null;
    private Function<InputStream, String> requestBody = null;
    private Function<InputStream, String> responseBody = null;

    private LoggingInterceptor() {}

    public static LoggingInterceptor instance() { return new LoggingInterceptor(); }
    public LoggingInterceptor requestHeaders(Function<HttpHeaders, String> value) { this.requestHeaders = value; return this; }
    public LoggingInterceptor responseHeaders(Function<HttpHeaders, String> value) { this.responseHeaders = value; return this; }
    public LoggingInterceptor requestBody(Function<InputStream, String> value) { this.requestBody = value; return this; }
    public LoggingInterceptor responseBody(Function<InputStream, String> value) { this.responseBody = value; return this; }
    public LoggingInterceptor logger(Consumer<String> value) { this.logger = value; return this; }
    public LoggingInterceptor loggerEnabled(BooleanSupplier value) { this.loggerEnabled = value; return this; }


    public void addToTemplate(RestTemplate template) {
        List<ClientHttpRequestInterceptor> interceptors = template.getInterceptors();
        if(interceptors == null) interceptors = new ArrayList();
        boolean alreadyin = false;
        for(ClientHttpRequestInterceptor interceptor : interceptors) {
            if(interceptor.equals(this)) {
                alreadyin = true;
                break;
            }
        }
        if(!alreadyin) interceptors.add(this);
        template.setInterceptors(interceptors);
    }

    private static int counter = 1;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        int reqnum = counter++;
        if((loggerEnabled != null) && (loggerEnabled.getAsBoolean()) && (logger != null)) {
            StringBuilder sb = new StringBuilder();
            sb.append("http-req[").append(reqnum)
                .append("] ").append(request.getMethodValue())
                .append(" ").append(request.getURI())
                ;
            if(requestHeaders != null) {
                sb.append(" headers[").append(requestHeaders.apply(request.getHeaders())).append(']');
            }
            if(requestBody != null) {
                sb.append(" body[")
                    .append(requestBody.apply(new ByteArrayInputStream(body)))
                    .append("]");
            }
            logger.accept(sb.toString());
        }
        long starttime = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long endtime = System.currentTimeMillis();
        if((loggerEnabled != null) && (loggerEnabled.getAsBoolean()) && (logger != null)) {
            StringBuilder sb = new StringBuilder();
            sb.append("http-res[").append(reqnum)
                .append("] ").append(request.getMethodValue())
                .append(" ").append(request.getURI())
                .append(" status:").append(response.getRawStatusCode())
                .append(" time:").append(endtime - starttime)
                ;
            if(responseHeaders != null) {
                sb.append(" headers[").append(responseHeaders.apply(response.getHeaders())).append(']');
            }
            if(responseBody != null) {
                response = DuplicatedClientHttpResponse.instance(response, response.getBody());
                sb.append(" body[")
                    .append(responseBody.apply(response.getBody()))
                    .append("]");
            }
            logger.accept(sb.toString());
        }
        return response;
    }

    private static class DuplicatedClientHttpResponse implements ClientHttpResponse {
        private ClientHttpResponse delegate;
        private byte[] body;
        public static DuplicatedClientHttpResponse instance(ClientHttpResponse delegate, InputStream body) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                body.transferTo(baos);
                DuplicatedClientHttpResponse rv = new DuplicatedClientHttpResponse();
                rv.delegate = delegate;
                rv.body = baos.toByteArray();
                return rv;
            } catch(Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public HttpStatusCode getStatusCode() throws IOException { return delegate.getStatusCode(); }
        public int getRawStatusCode() throws IOException { return delegate.getRawStatusCode(); }
        public String getStatusText() throws IOException { return delegate.getStatusText(); }
        public void close() { delegate.close(); }
        public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        public InputStream getBody() throws IOException { return new ByteArrayInputStream(body); }

    }

    public static class Headers {
        public static Function<HttpHeaders, String> all() {
            return headers -> {
                StringBuffer sb = new StringBuffer();
                sb.append("headers[");
                if(headers != null) {
                    boolean first = true;
                    for(Map.Entry<String, List<String>> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        for(String string : entry.getValue()) {
                            if(first) first = false;
                            else sb.append(", ");
                            sb.append(key).append(": ").append(string);
                        }
                    }
                }
                sb.append("]");
                return sb.toString();
            };
        }
        public static Function<HttpHeaders, String> listed(final String... name) {
            return headers -> {
                StringBuffer sb = new StringBuffer();
                sb.append("headers[");
                if((headers != null) && (name != null)) {
                    boolean first = true;
                    for(Map.Entry<String, List<String>> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        boolean match = false;
                        for(String string : name) {
                            if(key.equalsIgnoreCase(string)) {
                                match = true;
                                break;
                            }
                        }
                        if(match) {
                            for(String string : entry.getValue()) {
                                if(first) first = false;
                                else sb.append(", ");
                                sb.append(key).append(": ").append(string);
                            }
                        }
                    }
                }
                sb.append("]");
                return sb.toString();
            };
        }
    }

    public static class Body {
        public static XmlStreamToString xml() {
            return XmlStreamToString.instance();
        }
        public static JsonStreamToString json() {
            return JsonStreamToString.instance();
        }
    }

}
