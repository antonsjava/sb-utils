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
package sk.antons.sbutils.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper implementation for RestTemplate usage.
 *
 * {@code <pre>}
 *  private RestTemplateClient client() {
 *      RestTemplate template = templateBuilder
 *          .additionalInterceptors(LoggingInterceptor
 *              .instance()
 *              .logger(m -> log.info(m))
 *              .loggerEnabled(() -> log.isInfoEnabled())
 *              .responseBody(LoggingInterceptor.Body.json().indent("  ").transform())
 *              )
 *          .build();
 *      return RestTemplateClient.Builder.instance()
 *          .template(template)
 *          .root("https://dummy.restapiexample.com/api/v1")
 *          .client();
 *  }
 *
 *  public EmployeeResult getEmployees() {
 *      return client().get().path("/employees").call(EmployeeResult.class);
 *  }
 * {@code </pre>}
 * @author antons
 */
public class RestTemplateClient {
    private static Logger log = LoggerFactory.getLogger(RestTemplateClient.class);

    protected String root;
    protected RestTemplate template = null;
    protected BiFunction<String, Object, HttpHeaders> headers = null;
    protected Predicate<ResponseEntity> responseValidator = null;

    private RestTemplateClient() {
    }

    protected BiFunction<String, Object, HttpHeaders> headers() {
        if(headers == null) headers = Headers.contentTypeOnly(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected Predicate<ResponseEntity> responseValidator() {
        if(responseValidator == null) responseValidator = ResponseValidator.successful();
        return responseValidator;
    }


    public Request get() { return request(HttpMethod.GET); }
    public Request post() { return request(HttpMethod.POST); }
    public Request delete() { return request(HttpMethod.DELETE); }
    public Request head() { return request(HttpMethod.HEAD); }
    public Request put() { return request(HttpMethod.PUT); }

    private Request request(HttpMethod method) {
        Request r = this.new Request();
        r.method(method);
        return r;
    }

    public static int counter = 1;

    /**
     * RestTemplateClient builder
     */
    public class Request {
        private HttpMethod method;
        private String path;
        private Object content;
        private HttpHeaders headers;

        private Request() {}
        private Request method(HttpMethod value) { this.method = value; return this; }
        public Request path(String value) { this.path = value; return this; }
        public Request content(Object value) { this.content = value; return this; }
        public Request headers(HttpHeaders value) { this.headers = value; return this; }

        public void call() { call(String.class, null); }
        public <T> T call(Class<T> clazz) { return call(clazz, null); }
        public <T> T call(ParameterizedTypeReference<T> type) { return call(null, type); }
        private <T> T call(Class<T> clazz, ParameterizedTypeReference<T> type) {
            int id = counter++;
            if(log.isDebugEnabled()) log.debug("req[{}] {} {}", id, method.name(), url());
            long starttime = System.currentTimeMillis();
            long requesttime = 0;
            try {

                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url());

                HttpEntity<?> entity = new HttpEntity<>(content, headers == null ? RestTemplateClient.this.headers().apply(path, content) : headers);

                ResponseEntity<T> response = null;
                if(clazz != null) {
                    response = RestTemplateClient.this.template
                        .exchange(builder.build().toUriString()
                            , method
                            , entity
                            , clazz);
                } else {
                    response = RestTemplateClient.this.template
                        .exchange(builder.build().toUriString()
                            , method
                            , entity
                            , type);
                }
                requesttime = System.currentTimeMillis()- starttime;

                if(log.isDebugEnabled()) log.debug("res[{}] {} {} status: {}, time: {}", id, method.name(), url(), response.getStatusCodeValue(), requesttime);
                if(RestTemplateClient.this.responseValidator().test(response)) {
                    return response.getBody();
                } else {
                    throw new HttpException(response).method(method).url(url());
                }
            } catch (Throwable e) {
                if(log.isDebugEnabled()) log.debug("res[{}] {} {} err: {}", id, method.name(), url(), e.toString());
                if(e instanceof HttpException) throw (HttpException)e;
                else throw new HttpException(e).url(url()).method(method);
            }

        }

        private String url() {
            return (RestTemplateClient.this.root == null ? "" : RestTemplateClient.this.root)
                    + (path == null ? "" : path);
        }
    }



    /**
     * RestTemplateClient builder
     */
    public static class Builder {
        private String root;
        private RestTemplate template = null;
        private BiFunction<String, Object, HttpHeaders> headers = null;
        private Predicate<ResponseEntity> responseValidator = null;

        public static Builder instance() { return new Builder(); }
        /**
         * Url prefix. will be added to request paths.
         */
        public Builder root(String value) { this.root = value; return this; }
        /**
         * RestTemplate used for call.
         */
        public Builder template(RestTemplate value) { this.template = value; return this; }
        /**
         * HttpHeaders added to request. (Default is Content-Type: application/json)
         */
        public Builder headers(BiFunction<String, Object, HttpHeaders> value) { this.headers = value; return this; }
        /**
         * Response checker for OK responses. (Default is result code is 2xx)
         */
        public Builder responseValidator(Predicate<ResponseEntity> value) { this.responseValidator = value; return this; }

        public RestTemplateClient client() {
            if(template == null) throw new IllegalStateException("No template");
            RestTemplateClient client = new RestTemplateClient();
            client.root = this.root;
            client.template = this.template;
            client.responseValidator = this.responseValidator;
            client.headers = this.headers;
            return client;
        }
    }

    /**
     * Default headers provider
     */
    public static class Headers {

        /**
         * Add header Content-Type: xxx
         */
        public static BiFunction<String, Object, HttpHeaders> contentTypeOnly(MediaType contentType) {
            return  (path, content) -> {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(contentType);
                return headers;
            };
        }

        /**
         * Add headers specified in map
         */
        public static BiFunction<String, Object, HttpHeaders> simple(Map<String, String> map) {
            return  (path, content) -> {
                HttpHeaders headers = new HttpHeaders();
                for(Map.Entry<String, String> entry : map.entrySet()) {
                    headers.set(entry.getKey(), entry.getValue());
                }
                return headers;
            };
        }

    }

    /**
     * ResponseValidator builder (check if response is ok - if not exception is thrown.)
     */
    public static class ResponseValidator {

        /**
         * Checks if response code is 2xx
         */
        public static Predicate<ResponseEntity> successful() {
            return (response) -> {
                if(response == null) return false;
                if(response.getStatusCode() == null) return false;
                return response.getStatusCode().series() == HttpStatus.Series.SUCCESSFUL;
            };
        }

        /**
         * Checks if response code is one of listed
         */
        public static Predicate<ResponseEntity> listedCodes(final int... codes) {
            return (response) -> {
                if(codes == null) return false;
                if(response == null) return false;
                if(response.getStatusCode() == null) return false;
                for(int code : codes) {
                    if(response.getStatusCodeValue() == code) return true;
                }
                return false;
            };
        }
    }

    /**
     * Exception thrown if an error ocures od ResponseValidator returns false.
     * It provides exception which was received or data of response if
     * ResponseValidator returns false.
     */
    public static class HttpException extends RuntimeException {

        private String url = null;
        private HttpMethod method = null;
        private HttpHeaders headers = null;
        private HttpStatus status = null;
        private Object body = null;
        private Throwable error = null;

        public HttpException(Throwable throwable) {
            this.error = throwable;
        }

        public HttpException(ResponseEntity entity) {
            this.headers = entity.getHeaders();
            this.status = entity.getStatusCode();
            this.body = entity.getBody();
        }
        public HttpException url(String value) { this.url = value; return this; }
        public HttpException method(HttpMethod value) { this.method = value; return this; }

        @JsonIgnore
        public HttpHeaders getHeaders() {
            return headers;
        }

        public HttpStatus getStatus() {
            return status;
        }

        @JsonIgnore
        public Object getBody() {
            return body;
        }

        public Throwable getError() {
            return error;
        }

        @Override
        public String getMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append("request failed -");
            if(method != null) sb.append(" ").append(method);
            if(url != null) sb.append(" to ").append(url);
            if(status != null) sb.append(" status: ").append(status);
            if(error != null) sb.append(" because of: ").append(error.toString());
            //if(headers != null) sb.append(" headers: ").append(headers);
            //if(body != null) sb.append(" body: ").append(body.getClass());
            return sb.toString();
        }

    }
}
