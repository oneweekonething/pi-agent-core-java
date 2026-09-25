package com.earendil.pi.agent;

import com.earendil.pi.context.Context;
import com.earendil.pi.llm.Llm;
import com.earendil.pi.security.Security;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AgentRuntimeTest {
    @Test public void toolResultFeedsNextTurn(){
        AtomicInteger turns=new AtomicInteger();
        Llm.Client llm=new Llm.FunctionalClient(request -> {
            if(turns.getAndIncrement()==0)return Llm.Response.tools("thinking",Collections.singletonList(Tools.Call.create("echo",Collections.<String,Object>singletonMap("text","ok"))));
            return Llm.Response.answer("done:"+request.getMessages().get(request.getMessages().size()-1).getContent());
        });
        Tools.Tool echo=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("echo","echo",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){return CompletableFuture.completedFuture(Tools.Execution.ok(String.valueOf(args.get("text"))));}
        };
        Sessions.InMemoryRepository repo=new Sessions.InMemoryRepository();
        Sessions.Manager sessions=new Sessions.Manager(repo);
        try(Tools.Registry registry=new Tools.Registry()){
            registry.register(echo);
            AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000));
            AgentRuntime.Result result=runtime.run("s","go").join();
            assertEquals(AgentRuntime.StopReason.COMPLETED,result.getReason());
            assertEquals("done:ok",result.getText());
            Sessions.Tree tree=sessions.find("s").join().get();
            assertEquals(4,tree.activePath().size());
            assertFalse(tree.activePath().get(2).isError());
        }
    }
}
