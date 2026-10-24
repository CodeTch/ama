package ama.storage.s3;

import ama.storage.SourceReadLifetime;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.utils.AttributeMap;

/** Binds the actual Apache request before call(), including headers and SDK error/control reads. */
final class ScopedS3HttpClient implements SdkHttpClient {
    private final SdkHttpClient delegate;
    private ScopedS3HttpClient(SdkHttpClient delegate){this.delegate=delegate;}
    static final class Builder implements SdkHttpClient.Builder<Builder> {
        private final SdkHttpClient.Builder<?> delegate;
        Builder(SdkHttpClient.Builder<?> delegate){this.delegate=delegate;}
        public SdkHttpClient buildWithDefaults(AttributeMap defaults){return new ScopedS3HttpClient(delegate.buildWithDefaults(defaults));}
    }
    public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request){return new OwnedRequest(delegate.prepareRequest(request));}
    public String clientName(){return delegate.clientName();}
    public void close(){delegate.close();}

    private static final class OwnedRequest implements ExecutableHttpRequest {
        private final ExecutableHttpRequest delegate;
        private final AtomicBoolean aborted=new AtomicBoolean();
        private final SourceReadLifetime.Registration registration;
        OwnedRequest(ExecutableHttpRequest delegate){this.delegate=delegate;registration=SourceReadLifetime.track(this::abortTransport);}
        private void abortTransport(){if(aborted.compareAndSet(false,true))delegate.abort();}
        public void abort(){try{abortTransport();}finally{registration.close();}}
        public HttpExecuteResponse call() throws IOException {
            try {
                var response=delegate.call();
                if(response.responseBody().isEmpty()){registration.close();return response;}
                var body=response.responseBody().orElseThrow();
                var owned=new FilterInputStream(body){
                    private final AtomicBoolean closed=new AtomicBoolean();
                    @Override public void close() throws IOException {
                        if(closed.compareAndSet(false,true))try{body.close();}finally{registration.close();}
                    }
                };
                return HttpExecuteResponse.builder().response(response.httpResponse())
                        .responseBody(AbortableInputStream.create(owned,this::abort)).build();
            }catch(IOException|RuntimeException|Error failure){registration.close();throw failure;}
        }
    }
}
