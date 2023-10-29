/*
 *
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
 * Helper class for creating excption advice class.
 * {@code <pre>}
 * @ControllerAdvice(basePackages = "sk.antons.project.api")
 * public class JsonExceptionAdvice {
 *     private static Logger log = LoggerFactory.getLogger(JsonExceptionAdvice.class);
 *
 *     JsonExceptionHandler handler = JsonExceptionHandler.instance()
 *         .logger(t -> log.info("request failed {} ", Stk.trace(t)))
 *         // .statusResolver(JsonExceptionHandler.DefaultStatusResolver.instance().status(MyAppException.class, HttpStatus.CONFLICT))
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

    public JsonExceptionHandler logger(Consumer<Throwable> value) { this.logger = value; return this; }
    public JsonExceptionHandler statusResolver(Function<Throwable, HttpStatus> value) { this.statusResolver = value; return this; }
    public JsonExceptionHandler processor(Function<Throwable, ObjectNode> value) { this.processor = value; return this; }

    /**
     * Process Exception to http response
     * {@code <li>} if logger is defined prints exception by logger
     * {@code <li>} search first exception in casedBy() chain which define http code.
     * {@code <li>} use that exception (or firstone) and produce ObjectNode as response from that exception.
     * {@code <li>} ObjectNode is created using provided processor.
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
     * Simple sxception to ObjectNode mapper. Use ObjectMapper to create object tree.
     * Add class property and allMessages proeprty.
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
     * static int httpCode() function. After ststaus is resolved it is cached for given class.
     */
    public static class DefaultStatusResolver implements Function<Throwable, HttpStatus> {

        private Map<Class, HttpStatus> cache = new HashMap<>();

        public static DefaultStatusResolver instance() { return new DefaultStatusResolver(); }

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

//    //@ResponseStatus(HttpStatus.BAD_GATEWAY)
//    private static class MyException extends Exception {
//        public static int httpCode() { return 403; }
//    }
//
//    public static void main(String[] argv) {
//        MyException e = new MyException();
//        //DefaultStatusResolver resolver = DefaultStatusResolver.instance().status(MyException.class, HttpStatus.CONTINUE);
//        //DefaultStatusResolver resolver = DefaultStatusResolver.instance().status(Throwable.class, HttpStatus.CHECKPOINT);
//        DefaultStatusResolver resolver = DefaultStatusResolver.instance();
//        System.out.println(" -- " + resolver.resolveStatus(e));
//    }
}
