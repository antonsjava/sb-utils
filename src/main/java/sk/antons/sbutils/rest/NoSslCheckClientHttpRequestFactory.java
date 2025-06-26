/*
 *
 */
package sk.antons.sbutils.rest;

import ch.qos.logback.core.util.Duration;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 *
 * @author antons
 */
public class NoSslCheckClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    public static NoSslCheckClientHttpRequestFactory instance() { return new NoSslCheckClientHttpRequestFactory(); }
    public NoSslCheckClientHttpRequestFactory readTimeout(Duration timeout) { this.setReadTimeout((int)timeout.getMilliseconds()); return  this; }
    public NoSslCheckClientHttpRequestFactory connectTimeout(Duration timeout) { this.setConnectTimeout((int)timeout.getMilliseconds()); return  this; }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (DUMMY_SSLCONTEXT != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setHostnameVerifier(DUMMY_VERIFIER);
            ((HttpsURLConnection) connection).setSSLSocketFactory(DUMMY_SSLCONTEXT.getSocketFactory());
        }
        super.prepareConnection(connection, httpMethod);
    }

    private static final HostnameVerifier DUMMY_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private static final TrustManager[] DUMMY_TRUST_MANAGER = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {

                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {

                }
            }
    };

    private static SSLContext DUMMY_SSLCONTEXT = null;

    static {
        try {
            DUMMY_SSLCONTEXT = SSLContext.getInstance("SSL");
            DUMMY_SSLCONTEXT.init(null, DUMMY_TRUST_MANAGER, new SecureRandom());
        } catch (Exception e) {
            if(e instanceof RuntimeException) throw (RuntimeException)e;
            else throw new RuntimeException(e);
        }
    }


}
