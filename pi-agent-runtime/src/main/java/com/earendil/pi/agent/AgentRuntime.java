package com.earendil.pi.agent;

import com.earendil.pi.common.Asyncs;
import com.earendil.pi.common.Cancellation;
import com.earendil.pi.context.Context;
import com.earendil.pi.llm.Llm;
import com.earendil.pi.security.Security;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AgentRuntime implements AutoCloseable {
    public enum StopReason { COMPLETED, MAX_TURNS, CANCELLED }

    public static final class Config {
        private final int maxTurns; private final long toolTimeoutMillis,llmTimeoutMillis;
        public Config(int maxTurns,long toolTimeoutMillis,long llmTimeoutMillis){
            if(maxTurns<=0||toolTimeoutMillis<=0||llmTimeoutMillis<=0)throw new IllegalArgumentException("invalid runtime config");
            this.maxTurns=maxTurns;this.toolTimeoutMillis=toolTimeoutMillis;this.llmTimeoutMillis=llmTimeoutMillis;
        }
        public Config(int maxTurns,long toolTimeoutMillis){this(maxTurns,toolTimeoutMillis,120000);}
        public static Config defaults(){return new Config(16,30000,120000);}
    }

    public static final class Result {
        private final String sessionId,text; private final int turns; private final StopReason reason;
        public Result(String sessionId,String text,int turns,StopReason reason){this.sessionId=sessionId;this.text=text==null?"":text;this.turns=turns;this.reason=reason;}
        public String getSessionId(){return sessionId;} public String getText(){return text;} public int getTurns(){return turns;} public StopReason getReason(){return reason;}
    }

    private final Sessions.Manager sessions;
    private final Tools.Registry tools;
    private final Llm.Client llm;
    private final Context.Assembler context;
    private final Security.Policy policy;
    private final Config config;
    private final ConcurrentMap<String,CompletableFuture<Result>> activeRuns=new ConcurrentHashMap<String,CompletableFuture<Result>>();
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"pi-llm-timeout"); t.setDaemon(true); return t;
    });

    public AgentRuntime(Sessions.Manager sessions,Tools.Registry tools,Llm.Client llm,Context.Assembler context,Security.Policy policy,Config config){
        this.sessions=sessions;this.tools=tools;this.llm=llm;this.context=context;this.policy=policy;this.config=config;
    }

    public CompletableFuture<Result> run(String sessionId,String userMessage){return run(sessionId,userMessage,Cancellation.create());}

    public CompletableFuture<Result> run(final String sessionId,final String userMessage,final Cancellation cancellation){
        Asyncs.nonBlank(sessionId,"sessionId");
        while(true){
            CompletableFuture<Result> inFlight=activeRuns.get(sessionId);
            if(inFlight!=null&&!inFlight.isDone()){
                CompletableFuture<Result> rejected=new CompletableFuture<Result>();
                rejected.completeExceptionally(new IllegalStateException("agent is already processing a prompt for session "+sessionId));
                return rejected;
            }
            if(inFlight!=null)activeRuns.remove(sessionId,inFlight);
            final CompletableFuture<Result> created=new CompletableFuture<Result>();
            if(activeRuns.putIfAbsent(sessionId,created)!=null)continue;
            created.whenComplete((result,error)->activeRuns.remove(sessionId,created));
            execute(sessionId,userMessage,cancellation).whenComplete((result,error)->{
                if(error!=null)created.completeExceptionally(error);
                else created.complete(result);
            });
            return created;
        }
    }

    private CompletableFuture<Result> execute(final String sessionId,final String userMessage,final Cancellation cancellation){
        return sessions.getOrCreate(sessionId).thenCompose(session -> {
            session.appendUser(userMessage);
            return sessions.save(session).thenCompose(v -> loop(session,1,"",cancellation));
        });
    }

    private CompletableFuture<Result> loop(final Sessions.Tree session,final int turn,final String lastText,final Cancellation cancellation){
        if(cancellation.isCancelled())return finish(session,lastText,Math.max(0,turn-1),StopReason.CANCELLED);
        if(turn>config.maxTurns)return finish(session,lastText,config.maxTurns,StopReason.MAX_TURNS);
        final Cancellation token=Cancellation.linkedTo(cancellation);
        return Asyncs.withTimeout(llm.complete(context.assemble(session,tools.definitions()),token),config.llmTimeoutMillis,TimeUnit.MILLISECONDS,scheduler)
                .whenComplete((response,error)->{
                    if(error!=null&&Asyncs.unwrap(error) instanceof java.util.concurrent.TimeoutException)token.cancel();
                })
                .thenCompose(response -> handle(session,turn,response,cancellation));
    }

    private CompletableFuture<Result> handle(final Sessions.Tree session,final int turn,final Llm.Response response,final Cancellation cancellation){
        List<Sessions.ToolCallSnapshot> snapshots=new ArrayList<Sessions.ToolCallSnapshot>();
        for(Tools.Call c:response.getToolCalls())snapshots.add(new Sessions.ToolCallSnapshot(c.getId(),c.getName(),c.getArguments()));
        session.appendAssistant(response.getText(),snapshots);
        if(response.getToolCalls().isEmpty())return finish(session,response.getText(),turn,StopReason.COMPLETED);
        return executeSequential(session,response.getToolCalls(),0,cancellation)
                .thenCompose(v -> sessions.save(session))
                .thenCompose(v -> loop(session,turn+1,response.getText(),cancellation));
    }

    private CompletableFuture<Void> executeSequential(final Sessions.Tree session,final List<Tools.Call> calls,final int index,final Cancellation cancellation){
        if(cancellation.isCancelled()){appendSkipped(session,calls,index);return CompletableFuture.completedFuture(null);}
        if(index>=calls.size())return CompletableFuture.completedFuture(null);
        final Tools.Call call=calls.get(index);
        Security.Decision decision=policy.evaluate(call);
        CompletableFuture<Tools.Result> future=decision.isAllowed()
                ? tools.execute(call,config.toolTimeoutMillis,cancellation)
                : CompletableFuture.completedFuture(new Tools.Result(call.getId(),call.getName(),"tool denied: "+decision.getReason(),true,0));
        return future.thenCompose(result -> {
            session.appendTool(result.getCallId(),result.getToolName(),result.getContent(),result.isError());
            return executeSequential(session,calls,index+1,cancellation);
        });
    }

    private static void appendSkipped(Sessions.Tree session,List<Tools.Call> calls,int from){
        for(int i=from;i<calls.size();i++){
            Tools.Call call=calls.get(i);
            session.appendTool(call.getId(),call.getName(),"tool call cancelled before execution",true);
        }
    }

    private CompletableFuture<Result> finish(Sessions.Tree session,String text,int turns,StopReason reason){
        Result result=new Result(session.getId(),text,turns,reason);
        return sessions.save(session).thenApply(v -> result);
    }

    public void close(){scheduler.shutdownNow();}
}
