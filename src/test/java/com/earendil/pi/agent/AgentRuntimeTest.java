package com.earendil.pi.agent;

import com.earendil.pi.internal.Asyncs;
import com.earendil.pi.CancellationToken;
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
        final CancellationToken cancellation=CancellationToken.create();
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

    @Test public void concurrentRunOnSameSessionIsRejected(){
        final CompletableFuture<Llm.Response> gate=new CompletableFuture<Llm.Response>();
        Llm.Client gated=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){return gate;}
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,gated,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000))){
            CompletableFuture<AgentRuntime.Result> first=runtime.run("s","one");
            Throwable rejection=runtime.run("s","two").handle((r,e)->e).join();
            assertTrue(rejection instanceof IllegalStateException);
            gate.complete(Llm.Response.answer("done"));
            assertEquals(AgentRuntime.StopReason.COMPLETED,first.join().getReason());
            assertEquals(AgentRuntime.StopReason.COMPLETED,runtime.run("s","three").join().getReason());
        }
    }

    @Test public void llmTimeoutCancelsClientToken(){
        final java.util.List<CancellationToken> seen=new java.util.ArrayList<CancellationToken>();
        Llm.Client hanging=new Llm.Client(){
            public CompletableFuture<Llm.Response> complete(Llm.Request request){return new CompletableFuture<Llm.Response>();}
            public CompletableFuture<Llm.Response> complete(Llm.Request request,CancellationToken cancellation){
                seen.add(cancellation);
                return new CompletableFuture<Llm.Response>();
            }
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,hanging,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000,150))){
            runtime.run("s","go").handle((r,e)->e).join();
            assertEquals(1,seen.size());
            assertTrue(seen.get(0).isCancelled());
        }
    }

    @Test public void persistsAssistantBeforeToolAndEachResultAfter(){
        final java.util.List<Integer> saveSizes=new java.util.ArrayList<Integer>();
        final java.util.concurrent.atomic.AtomicInteger savesWhenToolRan=new java.util.concurrent.atomic.AtomicInteger(-1);
        Sessions.Repository recording=new Sessions.Repository(){
            private final java.util.concurrent.ConcurrentMap<String,Sessions.Tree> data=new java.util.concurrent.ConcurrentHashMap<String,Sessions.Tree>();
            public CompletableFuture<java.util.Optional<Sessions.Tree>> find(String id){
                return CompletableFuture.completedFuture(java.util.Optional.ofNullable(data.get(id)));
            }
            public CompletableFuture<Void> save(Sessions.Tree tree){
                data.put(tree.getId(),tree);
                saveSizes.add(tree.allNodes().size());
                return CompletableFuture.completedFuture(null);
            }
        };
        AtomicInteger turns=new AtomicInteger();
        Llm.Client llm=new Llm.FunctionalClient(request -> {
            if(turns.getAndIncrement()==0)return Llm.Response.tools("thinking",Collections.singletonList(Tools.Call.create("echo",Collections.<String,Object>singletonMap("text","ok"))));
            return Llm.Response.answer("done");
        });
        Tools.Tool echo=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("echo","echo",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){
                savesWhenToolRan.set(saveSizes.size());
                return CompletableFuture.completedFuture(Tools.Execution.ok("ok"));
            }
        };
        Sessions.Manager sessions=new Sessions.Manager(recording);
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000))){
            registry.register(echo);
            assertEquals(AgentRuntime.StopReason.COMPLETED,runtime.run("s","go").join().getReason());
        }
        assertEquals(java.util.Arrays.asList(0,1,2,3,4),saveSizes);
        assertTrue("tool must run only after the assistant node is persisted, saw "+savesWhenToolRan.get(),savesWhenToolRan.get()>=3);
    }

    @Test public void policyFailureBecomesPairedObservation(){
        com.earendil.pi.security.Security.Policy broken=new com.earendil.pi.security.Security.Policy(){
            public com.earendil.pi.security.Security.Decision evaluate(Tools.Call call){throw new IllegalStateException("policy service unavailable");}
        };
        AtomicInteger turns=new AtomicInteger();
        Llm.Client llm=new Llm.FunctionalClient(request -> {
            if(turns.getAndIncrement()==0)return Llm.Response.tools("thinking",Collections.singletonList(Tools.Call.create("echo",null)));
            return Llm.Response.answer("done");
        });
        Tools.Tool echo=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("echo","echo",Collections.<Tools.Parameter>emptyList());}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){return CompletableFuture.completedFuture(Tools.Execution.ok("must not run"));}
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),broken,new AgentRuntime.Config(4,1000))){
            registry.register(echo);
            AgentRuntime.Result result=runtime.run("s","go").join();
            assertEquals(AgentRuntime.StopReason.COMPLETED,result.getReason());
            java.util.Set<String> callIds=new java.util.HashSet<String>();
            java.util.Set<String> resultIds=new java.util.HashSet<String>();
            for(Sessions.Node node:sessions.find("s").join().get().activePath()){
                if(node.getRole()==Sessions.Role.ASSISTANT)for(Sessions.ToolCallSnapshot call:node.getToolCalls())callIds.add(call.getId());
                if(node.getRole()==Sessions.Role.TOOL_RESULT){resultIds.add(node.getToolCallId());assertTrue(node.isError());assertTrue(node.getContent().contains("tool call failed"));}
            }
            assertEquals(callIds,resultIds);
        }
    }

    @Test public void policySeesOnlyValidatedArguments(){
        final AtomicInteger policyEvaluations=new AtomicInteger();
        com.earendil.pi.security.Security.Policy casting=new com.earendil.pi.security.Security.Policy(){
            public com.earendil.pi.security.Security.Decision evaluate(Tools.Call call){
                policyEvaluations.incrementAndGet();
                String text=(String)call.getArguments().get("text");
                return text==null?com.earendil.pi.security.Security.Decision.deny("missing"):com.earendil.pi.security.Security.Decision.allow();
            }
        };
        AtomicInteger turns=new AtomicInteger();
        Llm.Client llm=new Llm.FunctionalClient(request -> {
            if(turns.getAndIncrement()==0)return Llm.Response.tools("thinking",Collections.singletonList(Tools.Call.create("echo",Collections.<String,Object>singletonMap("text",5))));
            return Llm.Response.answer("recovered");
        });
        Tools.Tool echo=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("echo","echo",Collections.singletonList(new Tools.Parameter("text","text",true,Tools.ParameterType.STRING)));}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> args){return CompletableFuture.completedFuture(Tools.Execution.ok(String.valueOf(args.get("text"))));}
        };
        Sessions.Manager sessions=new Sessions.Manager(new Sessions.InMemoryRepository());
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),casting,new AgentRuntime.Config(4,1000))){
            AgentRuntime.Result result=runtime.run("s","go").join();
            assertEquals(AgentRuntime.StopReason.COMPLETED,result.getReason());
            assertEquals(0,policyEvaluations.get());
            Sessions.Tree tree=sessions.find("s").join().get();
            assertTrue(tree.activePath().get(2).isError());
        }
    }

    @Test public void runRecoversAfterSynchronousRepositoryFailure(){
        final java.util.concurrent.atomic.AtomicBoolean failing=new java.util.concurrent.atomic.AtomicBoolean(true);
        Sessions.Repository flaky=new Sessions.Repository(){
            public CompletableFuture<java.util.Optional<Sessions.Tree>> find(String id){
                if(failing.get())throw new IllegalStateException("db down");
                return CompletableFuture.completedFuture(java.util.Optional.<Sessions.Tree>empty());
            }
            public CompletableFuture<Void> save(Sessions.Tree tree){return CompletableFuture.completedFuture(null);}
        };
        Llm.Client llm=new Llm.FunctionalClient(request -> Llm.Response.answer("ok"));
        Sessions.Manager sessions=new Sessions.Manager(flaky);
        try(Tools.Registry registry=new Tools.Registry();AgentRuntime runtime=new AgentRuntime(sessions,registry,llm,new Context.Assembler(Context.Config.defaults()),new Security.AllowAll(),new AgentRuntime.Config(4,1000))){
            Throwable first=runtime.run("s","one").handle((r,e)->e).join();
            assertTrue(Asyncs.unwrap(first) instanceof IllegalStateException);
            failing.set(false);
            AgentRuntime.Result second=runtime.run("s","two").join();
            assertEquals(AgentRuntime.StopReason.COMPLETED,second.getReason());
        }
    }
}
