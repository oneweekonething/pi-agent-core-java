package com.earendil.pi.tool;

import com.earendil.pi.CancellationToken;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToolsTest {
    @Test public void timeoutBecomesObservation(){
        Tools.Tool never=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("never","never",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){return new CompletableFuture<Tools.Execution>();}
        };
        try(Tools.Registry registry=new Tools.Registry()){
            registry.register(never);
            Tools.Result result=registry.execute(Tools.Call.create("never",null),50).join();
            assertTrue(result.isError());
            assertTrue(result.getContent().toLowerCase().contains("timed out"));
        }
    }

    @Test public void timeoutCancelsToolToken(){
        final List<CancellationToken> seen=new ArrayList<CancellationToken>();
        Tools.Tool never=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("never","never",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){return new CompletableFuture<Tools.Execution>();}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments,CancellationToken cancellation){
                seen.add(cancellation);
                return new CompletableFuture<Tools.Execution>();
            }
        };
        try(Tools.Registry registry=new Tools.Registry()){
            registry.register(never);
            registry.execute(Tools.Call.create("never",null),50).join();
            assertEquals(1,seen.size());
            assertTrue(seen.get(0).isCancelled());
        }
    }

    @Test public void parentCancellationReachesTool(){
        Tools.Tool probe=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("probe","probe",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){return CompletableFuture.completedFuture(Tools.Execution.ok("unaware"));}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments,CancellationToken cancellation){
                return CompletableFuture.completedFuture(Tools.Execution.ok(cancellation.isCancelled()?"aware":"unaware"));
            }
        };
        try(Tools.Registry registry=new Tools.Registry()){
            registry.register(probe);
            CancellationToken parent=CancellationToken.create();
            parent.cancel();
            Tools.Result result=registry.execute(Tools.Call.create("probe",null),1000,parent).join();
            assertEquals("aware",result.getContent());
        }
    }

    @Test public void invalidArgumentsBecomeObservationsWithoutExecuting(){
        final java.util.concurrent.atomic.AtomicInteger executions=new java.util.concurrent.atomic.AtomicInteger();
        Tools.Tool gated=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("gated","gated",Collections.singletonList(new Tools.Parameter("path","p",true)));}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){executions.incrementAndGet();return CompletableFuture.completedFuture(Tools.Execution.ok("ran"));}
        };
        try(Tools.Registry registry=new Tools.Registry()){
            registry.register(gated);
            Tools.Result missing=registry.execute(Tools.Call.create("gated",null),1000).join();
            assertTrue(missing.isError());
            assertTrue(missing.getContent().contains("missing required argument: path"));
            Tools.Result wrongType=registry.execute(Tools.Call.create("gated",Collections.<String,Object>singletonMap("path",5)),1000).join();
            assertTrue(wrongType.isError());
            assertTrue(wrongType.getContent().contains("expected string"));
            assertEquals(0,executions.get());
            Tools.Result valid=registry.execute(Tools.Call.create("gated",Collections.<String,Object>singletonMap("path","a")),1000).join();
            assertTrue(!valid.isError());
            assertEquals(1,executions.get());
        }
    }

    @Test public void oversizedResultTruncated(){
        Tools.Tool chatty=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("chatty","chatty",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){
                StringBuilder big=new StringBuilder();
                for(int i=0;i<1000;i++)big.append('x');
                return CompletableFuture.completedFuture(Tools.Execution.ok(big.toString()));
            }
        };
        try(Tools.Registry registry=new Tools.Registry(64)){
            registry.register(chatty);
            Tools.Result result=registry.execute(Tools.Call.create("chatty",null),1000).join();
            assertEquals(64,result.getContent().indexOf("...[truncated "));
            assertTrue(result.getContent().endsWith("[truncated 936 chars]"));
        }
    }
}
