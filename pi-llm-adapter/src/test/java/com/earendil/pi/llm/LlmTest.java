package com.earendil.pi.llm;

import com.earendil.pi.tool.Tools;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LlmTest {
    private static Llm.Request request(){
        return new Llm.Request("sys",Collections.<Llm.Message>emptyList(),Collections.<Tools.Definition>emptyList());
    }

    @Test public void retriesUntilSuccess(){
        final AtomicInteger attempts=new AtomicInteger();
        Llm.Client flaky=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){
                if(attempts.incrementAndGet()<3)throw new IllegalStateException("boom");
                return CompletableFuture.completedFuture(Llm.Response.answer("ok"));
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(flaky,3,1,8);
        try{
            assertEquals("ok",client.complete(request()).join().getText());
        } finally {
            client.close();
        }
        assertEquals(3,attempts.get());
    }

    @Test public void exhaustsAttemptsAndReportsLastFailure(){
        final AtomicInteger attempts=new AtomicInteger();
        Llm.Client broken=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){
                attempts.incrementAndGet();
                CompletableFuture<Llm.Response> failed=new CompletableFuture<Llm.Response>();
                failed.completeExceptionally(new IllegalStateException("down"));
                return failed;
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(broken,2,1,8);
        try{
            Throwable error=client.complete(request()).handle((r,e)->e).join();
            assertTrue(error instanceof IllegalStateException);
        } finally {
            client.close();
        }
        assertEquals(2,attempts.get());
    }
}
