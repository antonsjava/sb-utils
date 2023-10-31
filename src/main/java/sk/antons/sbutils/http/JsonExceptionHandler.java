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
package sk.antons.sbutils.http;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Helper class for creating exception advice class.
 *
 * It allows you to define following
 * {@code <ul>}
 * {@code <li>} method for logging exception
 * {@code <li>} method for resolving http status code
 * {@code <li>} method for converting exception to json node response
 * {@code </ul>}
 *
 * Exception is handled in following order
 * {@code <ul>}
 * {@code <li>} if logger is defined exception is send to logger
 * {@code <li>} then traversing causedBy chain is searching for first exception for which statusResolver returns code (if not nound firstone is used with code 500)
 * {@code <li>} then processor converts exception to json http response
 * {@code </ul>}
 *
 * {@code <pre>}
 * {@code @}ControllerAdvice(basePackages = "sk.antons.project.api")
 * public class JsonExceptionAdvice {
 *     private static Logger log = LoggerFactory.getLogger(JsonExceptionAdvice.class);
 *
 *     JsonExceptionHandler handler = JsonExceptionHandler.instance()
 *         .logger(t -> log.info("request failed {} ", Stk.trace(t)))
 *         // .statusResolver(JsonExceptionHandler.DefaultStatusResolver.instance()
 *         //          .status(MyAppException.class, HttpStatus.CONFLICT))
 *         // .processor(JsonExceptionHandler.DefaultExceptionProcessor.instance())
 *         );
 *
 *     {@code @}ExceptionHandler(Throwable.class)
 *     public ResponseEntity{@code <ObjectNode>} throwable(final Throwable ex) {
 *         return handler.process(ex);
 *     }
 * }
 * {@code </pre>}
 *
 * @author antons
 */
public class JsonExceptionHandler {
    private static Logger log = LoggerFactory.getLogger(JsonExceptionHandler.class);

    private Consumer<Throwable> logger = null;
    private Function<Throwable, HttpStatus> statusResolver = DefaultStatusResolver.instance();
    private Function<Throwable, ObjectNode> processor = null;

    public static JsonExceptionHandler instance() { return new JsonExceptionHandler(); }

    /**
     * method for logging exception
     * @param value method
     * @return this
     */
    public JsonExceptionHandler logger(Consumer<Throwable> value) { this.logger = value; return this; }
    /**
     * method for resolving http status from exception
     * @param value method
     * @return this
     */
    public JsonExceptionHandler statusResolver(Function<Throwable, HttpStatus> value) { this.statusResolver = value; return this; }
    /**
     * method for converting exception to json response
     * @param value method
     * @return this
     */
    public JsonExceptionHandler processor(Function<Throwable, ObjectNode> value) { this.processor = value; return this; }

    /**
     * Process Exception to http response. (main implementation)
     * @param ex
     * @return
     */
    public ResponseEntity<ObjectNode> process(final Throwable ex) {

        if(logger != null) logger.accept(ex);

        ExceptionInfo info = new ExceptionInfo();
        resolve(ex, info);

        Throwable t = info.markedExc;
        if(t == null) t = info.lastNonJavaExc;
        if(t == null) t = info.lastExc;
        if(t == null) t = ex;

        HttpStatus status = info.status;
        if(status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        if(processor == null) processor = DefaultExceptionProcessor.instance();
        ObjectNode node = processor instanceof DefaultExceptionProcessor
            ? ((DefaultExceptionProcessor)processor).apply(t, info) : processor.apply(t);

        return new ResponseEntity<>(node, status);
    }

    private void resolve(Throwable t, ExceptionInfo ei) {
        if(t == null) return;

        if(ei.lastExc == null) ei.lastExc = t;

        if(ei.lastNonJavaExc == null) {
            String clazz = t.getClass().getName();
            if(!clazz.startsWith("java")) ei.lastNonJavaExc = t;
        }

        String message = t.getMessage();
        if(message == null) ei.allMessages.add(t.getClass().getSimpleName());
        else ei.allMessages.add(t.getClass().getSimpleName() + ": " + message);


        if(ei.status == null) {
            HttpStatus status = null;
            if(statusResolver != null) status = statusResolver.apply(t);
            if(status != null) {
                ei.markedExc = t;
                ei.status = status;
            }
        }

        resolve(t.getCause(), ei);
    }

    private static class ExceptionInfo {
        Throwable lastExc;
        Throwable lastNonJavaExc;
        Throwable markedExc;
        List<String> allMessages = new ArrayList<>();
        HttpStatus status;
    }

    /**
     * Simple exception to ObjectNode mapper. Use ObjectMapper to create object tree.
     * Add class property and allMessages property.
     */
    public static class DefaultExceptionProcessor implements Function<Throwable, ObjectNode> {

        public static DefaultExceptionProcessor instance() { return new DefaultExceptionProcessor(); }

        @Override
        public ObjectNode apply(Throwable t) {
            ObjectNode node = null;
            try {
                node = (ObjectNode)om().valueToTree(t);
            } catch(Exception e) {
                node = new ObjectNode(JsonNodeFactory.instance);
            }
            node.put("class", t.getClass().getSimpleName());
            return node;
        }

        private ObjectNode apply(Throwable t, ExceptionInfo info) {
            ObjectNode node = null;
            try {
                node = (ObjectNode)om().valueToTree(t);
            } catch(Exception e) {
                node = new ObjectNode(JsonNodeFactory.instance);
            }
            node.put("class", t.getClass().getSimpleName());
            if(info.allMessages.size() > 1) {
                ArrayNode arr = om().createArrayNode();
                for(String message : info.allMessages) {
                    arr.add(message);
                }
                node.put("allMesages", arr);
            }
            return node;
        }

        private ObjectMapper om = null;
        private ObjectMapper om() {
            if(om == null) {
                ObjectMapper o = new ObjectMapper();
                o.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                o.configure(SerializationFeature.INDENT_OUTPUT, true);
                o.addMixIn(Throwable.class, MixIn.class);
                om = o;
            }
            return om;
        }


        private static abstract class MixIn {
            @JsonIgnore abstract Throwable getCause();
            @JsonIgnore abstract Throwable[] getSuppressed();
            @JsonIgnore abstract StackTraceElement[] getStackTrace();
            @JsonIgnore abstract String getLocalizedMessage();
        }

    }


    /**
     * Resolve http status from exception class. It is searching ResponseStatus annotation or
     * static int httpCode() function. After status is resolved it is cached for given class.
     * Allows also define status for custom exception classes.
     */
    public static class DefaultStatusResolver implements Function<Throwable, HttpStatus> {

        private Map<Class, HttpStatus> cache = new HashMap<>();

        public static DefaultStatusResolver instance() { return new DefaultStatusResolver(); }

        /**
         * Define custom code for exception class
         * @param exceptionClass
         * @param status
         * @return this
         */
        public DefaultStatusResolver status(Class exceptionClass, HttpStatus status) {
            if(exceptionClass == null) throw new IllegalArgumentException("no exceptionClass");
            if(status == null) throw new IllegalArgumentException("no status");
            cache.put(exceptionClass, status);
            return this;
        }

        @Override
        public HttpStatus apply(Throwable t) {
            if(t == null) return HttpStatus.INTERNAL_SERVER_ERROR;
            return resolveStatus(t.getClass());
        }

        private HttpStatus resolveStatus(Class clazz) {
            if(clazz == null) return HttpStatus.INTERNAL_SERVER_ERROR;
            HttpStatus status = cache.get(clazz);
            if(status == null) {
                status = resolveAnnotation(clazz);
                if(status == null) status = resolveMethod(clazz);
                if(status == null) status = resolveStatus(clazz.getSuperclass());
                if(status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
                cache.put(clazz, status);
            }
            return status;
        }

        private static HttpStatus resolveAnnotation(Class clazz) {
            ResponseStatus responseStatus = (ResponseStatus)clazz.getAnnotation(ResponseStatus.class);
            return responseStatus == null ? null : (responseStatus.value() == null ? responseStatus.code() : responseStatus.value());
        }

        private static HttpStatus resolveMethod(Class clazz) {
            Method[] methods = clazz.getDeclaredMethods();
            if(methods == null) return null;
            for(Method method : methods) {
                if(!Modifier.isPublic(method.getModifiers())) continue;
                if(!Modifier.isStatic(method.getModifiers())) continue;
                if(method.getParameterCount() != 0) continue;
                if(!"httpCode".equals(method.getName())) continue;
                if(!int.class.equals(method.getReturnType())) continue;
                try {
                    int value = (int)method.invoke(null, new Object[]{});
                    if(value > 0) return HttpStatus.valueOf(value);
                } catch(Exception e) {
                }
                return null;
            }
            return null;
        }
    }

}
