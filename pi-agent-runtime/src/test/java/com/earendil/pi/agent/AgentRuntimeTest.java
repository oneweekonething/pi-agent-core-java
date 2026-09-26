package com.earendil.pi.agent;

import com.earendil.pi.common.Asyncs;
import com.earendil.pi.common.Cancellation;
import com.earendil.pi.context.Context;
import com.earendil.pi.llm.Llm;
import com.earendil.pi.security.Security;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000))){
            registry.register(echo);
            AgentRuntime.Result result=runtime.run("s","go").join();
            assertEquals(AgentRuntime.StopReason.COMPLETED,result.getReason());
            assertEquals("done:ok",result.getText());
            Sessions.Tree tree=sessions.find("s").join().get();
            assertEquals(4,tree.activePath().size());
            assertFalse(tree.activePath().get(2).isError());
        }
    }

    @Test public void cancellationMidBatchRecordsEveryToolResult(){
        final Cancellation cancellation=Cancellation.create();
        Llm.Client llm=new Llm.FunctionalClient(request -> Llm.Response.tools("thinking",java.util.Arrays.asList(
                Tools.Call.create("first",null),Tools.Call.create("second",null))));
        final AtomicInteger secondExecutions=new AtomicInteger();
        Tools.Tool first=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("first","first",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){
                cancellation.cancel();
                return CompletableFuture.completedFuture(Tools.Execution.ok("one"));
            }
        };
        Tools.Tool second=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("second","second",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){secondExecutions.incrementAndGet();return CompletableFuture.completedFuture(Tools.Execution.ok("two"));}
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000))){
            registry.register(first);
            registry.register(second);
            AgentRuntime.Result result=runtime.run("s","go",cancellation).join();
            assertEquals(AgentRuntime.StopReason.CANCELLED,result.getReason());
            assertEquals(0,secondExecutions.get());
            Sessions.Tree tree=sessions.find("s").join().get();
            Set<String> callIds=new HashSet<String>();
            Set<String> resultIds=new HashSet<String>();
            for(Sessions.Node node:tree.activePath()){
                if(node.getRole()==Sessions.Role.ASSISTANT)for(Sessions.ToolCallSnapshot call:node.getToolCalls())callIds.add(call.getId());
                if(node.getRole()==Sessions.Role.TOOL_RESULT)resultIds.add(node.getToolCallId());
            }
            assertEquals(callIds,resultIds);
            assertEquals(2,resultIds.size());
            assertTrue(tree.activePath().get(3).isError());
        }
    }

    @Test public void llmTimeoutFailsTheRun(){
        Llm.Client hanging=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){return new CompletableFuture<Llm.Response>();}
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,hanging,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000,150))){
            Throwable error=runtime.run("s","go").handle((r,e)->e).join();
            assertTrue(Asyncs.unwrap(error) instanceof TimeoutException);
        }
    }
}
