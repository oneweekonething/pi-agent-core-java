package com.earendil.pi.llm;

import com.earendil.pi.CancellationToken;
import com.earendil.pi.tool.Tools;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CancellationException;
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

    @Test public void cancelledTokenStopsRetries(){
        final AtomicInteger attempts=new AtomicInteger();
        Llm.Client failing=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){throw new UnsupportedOperationException();}
            public CompletableFuture<Llm.Response> complete(Llm.Request request,CancellationToken cancellation){
                attempts.incrementAndGet();
                if(attempts.get()==1)cancellation.cancel();
                CompletableFuture<Llm.Response> failed=new CompletableFuture<Llm.Response>();
                failed.completeExceptionally(new IllegalStateException("down"));
                return failed;
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(failing,5,1,8);
        try{
            Throwable error=client.complete(request()).handle((r,e)->e).join();
            assertTrue(error instanceof CancellationException);
        } finally {
            client.close();
        }
        assertEquals(1,attempts.get());
    }

    @Test public void policyCanRejectRetries(){
        final AtomicInteger attempts=new AtomicInteger();
        Llm.Client failing=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){
                attempts.incrementAndGet();
                CompletableFuture<Llm.Response> failed=new CompletableFuture<Llm.Response>();
                failed.completeExceptionally(new IllegalStateException("permanent"));
                return failed;
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(failing,5,1,8,new Llm.RetryClient.RetryPolicy(){
            public boolean shouldRetry(Throwable error){return false;}
        });
        try{
            Throwable error=client.complete(request()).handle((r,e)->e).join();
            assertTrue(error instanceof IllegalStateException);
        } finally {
            client.close();
        }
        assertEquals(1,attempts.get());
    }

    @Test(timeout=5000) public void callbackFailureCompletesResult(){
        Llm.Client failing=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){
                CompletableFuture<Llm.Response> failed=new CompletableFuture<Llm.Response>();
                failed.completeExceptionally(new IllegalStateException("down"));
                return failed;
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(failing,3,1,8,new Llm.RetryClient.RetryPolicy(){
            public boolean shouldRetry(Throwable error){throw new IllegalStateException("policy broken");}
        });
        try{
            Throwable error=client.complete(request()).handle((r,e)->e).join();
            assertTrue(error instanceof IllegalStateException);
        } finally {
            client.close();
        }
    }

    @Test(timeout=5000) public void closeDuringBackoffCompletesPending(){
        final AtomicInteger attempts=new AtomicInteger();
        Llm.Client failing=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){
                attempts.incrementAndGet();
                CompletableFuture<Llm.Response> failed=new CompletableFuture<Llm.Response>();
                failed.completeExceptionally(new IllegalStateException("down"));
                return failed;
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(failing,10,5000,10000);
        CompletableFuture<Llm.Response> pendingCall=client.complete(request());
        client.close();
        Throwable error=pendingCall.handle((r,e)->e).join();
        assertTrue(error instanceof CancellationException);
        assertEquals(1,attempts.get());
    }

    @Test(timeout=5000) public void closeCancelsInFlightTokenAndRejectsNewCalls(){
        final java.util.List<CancellationToken> tokens=new java.util.concurrent.CopyOnWriteArrayList<CancellationToken>();
        Llm.Client hanging=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){return new CompletableFuture<Llm.Response>();}
            public CompletableFuture<Llm.Response> complete(Llm.Request request,CancellationToken cancellation){
                tokens.add(cancellation);
                return new CompletableFuture<Llm.Response>();
            }
        };
        Llm.RetryClient client=new Llm.RetryClient(hanging,3,10000,20000);
        CompletableFuture<Llm.Response> inFlight=client.complete(request());
        client.close();
        assertTrue(inFlight.handle((r,e)->e).join() instanceof CancellationException);
        assertEquals(1,tokens.size());
        assertTrue(tokens.get(0).isCancelled());
        assertTrue(client.complete(request()).handle((r,e)->e).join() instanceof CancellationException);
    }
}
