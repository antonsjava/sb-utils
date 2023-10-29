/*
 *
 */
package sk.antons.sbutils.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author antons
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private Consumer<String> logger = null;
    private BooleanSupplier loggerEnabled = null;
    private boolean logRequestHeader = true;
    private boolean logRequestBody = true;
    private boolean logResponseHeader = true;
    private boolean logResponseBody = true;
    private Function<String, String> requestBodyFormatter = null;
    private Function<String, String> responseBodyFormatter = null;

    private LoggingInterceptor() {}

    public static LoggingInterceptor instance() { return new LoggingInterceptor(); }
    public LoggingInterceptor logRequestHeader(boolean value) { this.logRequestHeader = value; return this; }
    public LoggingInterceptor logRequestBody(boolean value) { this.logRequestBody = value; return this; }
    public LoggingInterceptor requestBodyFormatter(Function<String, String> value) { this.requestBodyFormatter = value; return this; }
    public LoggingInterceptor logResponseHeader(boolean value) { this.logResponseHeader = value; return this; }
    public LoggingInterceptor logResponseBody(boolean value) { this.logResponseBody = value; return this; }
    public LoggingInterceptor responseBodyFormatter(Function<String, String> value) { this.responseBodyFormatter = value; return this; }
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
            if(logRequestHeader) {
                sb.append(" headers[");
                if(request.getHeaders() != null) {
                    boolean first = true;
                    for(Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
                        String key = entry.getKey();
                        for(String string : entry.getValue()) {
                            if(first) first = false;
                            else sb.append(", ");
                            sb.append(key).append(": ").append(string);
                        }
                    }
                }
                sb.append("]");
            }
            if(logRequestBody) {
                sb.append(" body[");
                String s = new String(body);
                if(requestBodyFormatter != null) s = requestBodyFormatter.apply(s);
                sb.append(s);
                sb.append("]");
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
            if(logResponseHeader) {
                sb.append(" headers[");
                if(response.getHeaders() != null) {
                    boolean first = true;
                    for(Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
                        String key = entry.getKey();
                        for(String string : entry.getValue()) {
                            if(first) first = false;
                            else sb.append(", ");
                            sb.append(key).append(": ").append(string);
                        }
                    }
                }
                sb.append("]");
            }
            if(logResponseBody) {
                sb.append(" body[");
                InputStreamReader isr = new InputStreamReader(
                    response.getBody(), StandardCharsets.UTF_8);
                String s = new BufferedReader(isr).lines()
                    .collect(Collectors.joining("\n"));
                InputStream is = null;
                try {
                    is = new ByteArrayInputStream(s.getBytes("utf-8"));
                } catch(Exception e) {
                }

                if(responseBodyFormatter != null) s = responseBodyFormatter.apply(s);
                sb.append(s);
                sb.append("]");
                response = DummyClientHttpResponse.instance(response, is);
            }
            logger.accept(sb.toString());
        }
        return response;
    }

    private static class DummyClientHttpResponse implements ClientHttpResponse {
        private ClientHttpResponse delegate;
        private InputStream body;
        public static DummyClientHttpResponse instance(ClientHttpResponse delegate, InputStream body) {
            DummyClientHttpResponse rv = new DummyClientHttpResponse();
            rv.delegate = delegate;
            rv.body = body;
            return rv;
        }

        public HttpStatus getStatusCode() throws IOException { return delegate.getStatusCode(); }
        public int getRawStatusCode() throws IOException { return delegate.getRawStatusCode(); }
        public String getStatusText() throws IOException { return delegate.getStatusText(); }
        public void close() { delegate.close(); }
        public HttpHeaders getHeaders() { return delegate.getHeaders(); }
        public InputStream getBody() throws IOException { return body; }

    }
}
