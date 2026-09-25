package com.earendil.pi.tool;

import org.junit.Test;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
}
