# sb-utils
helper utils for spring boot projects

## usage info

Set of utilities. Mostly for logging soap and rest requests. 

 - branch sb2 (vsesions 2.x) for spring boot 2.x.x
 - branch sb3 (vsesions 3.x) for spring boot 3.x.x

implementation uses runtime dependece for

 - io.github.antonsjava:json for json messages formatting 
 - io.github.antonsjava:jaul for xml messages formatting 

if you use this functionality add those dependece to your pom.


## JsonExceptionHandler

Helper class for creating exception advice

 - it logs exception
 - it searches for exception with defined http code
 - if such exception not exists root exception is used
 - converts exception to json 

Http code for exception is determined ba

 - Exception class is annotated with ResponseStatus (@ResponseStatus(HttpStatus.BAD_GATEWAY))
 - Exception class has method public static int httpCode()
 - Exception class has http code determined in builder .statusResolver(JsonExceptionHandler.DefaultStatusResolver.instance().status(MyAppException.class, HttpStatus.CONFLICT))

~~~java
 @ControllerAdvice(basePackages = "sk.antons.project.api")
 public class JsonExceptionAdvice {
     private static Logger log = LoggerFactory.getLogger(JsonExceptionAdvice.class);
 
     JsonExceptionHandler handler = JsonExceptionHandler.instance()
         .logger(t -> log.info("request failed {} ", Stk.trace(t)))
         // this is optional .statusResolver(JsonExceptionHandler.DefaultStatusResolver.instance().status(MyAppException.class, HttpStatus.CONFLICT))
         // this is default .processor(JsonExceptionHandler.DefaultExceptionProcessor.instance())
         );
 
     {@code @}ExceptionHandler(Throwable.class)
     public ResponseEntity{@code <ObjectNode>} throwable(final Throwable ex) {
         return handler.process(ex);
     }
 }
~~~

## RestTemplateClient

Simple wrapper over RestTemplate to call http requests


~~~java
 private RestTemplateClient client;
 private RestTemplateClient client() {
     if(client == null) {
         client = RestTemplateClient.Builder.instance()
         .template(template) // RestTemplate instance
         .headers(RestTemplateClient.Headers.builder() // header modifier
             .add("Host", host)
             .basicAuth(user, password)
             .build()
             )
         .root("https://somethere.com/api") // root for urls
         .client();
     }
     return client;
 }
~~~

and usage 

~~~java
 RestTemplateClient.Request request = client()
 	 .get() //method
     .path("/item/123"); //path
 MyItem data = request.call(MyItem.class);
~~~

~~~java
 RestTemplateClient.Request request = client()
 	 .post() //method
	 .content(myItem)
     .path("/item"); //path
 request.call(MyItem.class);
~~~

## LoggingInterceptor

simple http client logging interceptor

~~~java
 LoggingInterceptor.instance()
     .requestHeaders(LoggingInterceptor.Headers.all()) // which headers to print
     .requestBody(LoggingInterceptor.Body.json().forceOneLine().transform()) // how body is formatted
     .responseHeaders(LoggingInterceptor.Headers.all()) // which headers to print
     .responseBody(LoggingInterceptor.Body.json().forceOneLine().transform()) // how body is fomratted
     .loggerEnabled( () -> true) // if interceptor is enabled
     .logger(m -> System.out.println(m)) // how to log message
     .addToTemplate(template); // add interceptor to template 
~~~



## CxfLogInterceptor

simple webcservice client log interceptor for cxf.

~~~java
 CxfLogInterceptor.out()
     // optional .format(is -> formatInputStreamToOneLineXml(is)) 
     .loggerEnabled( () -> true) // if interceptor is enabled
     .logger(m -> System.out.println(m)) // how to log message
~~~

## SBWSLoggingInterceptor

simple web service client log interceptor for SB web services.

~~~java
 SBWSLoggingInterceptor.instance()
     // optional .format(is -> formatInputStreamToOneLineXml(is)) 
     .loggerEnabled( () -> true) // if interceptor is enabled
     .logger(m -> System.out.println(m)) // how to log message
~~~

