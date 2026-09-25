package com.earendil.pi.agent;

import com.earendil.pi.context.Context;
import com.earendil.pi.llm.Llm;
import com.earendil.pi.security.Security;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentRuntime {
    public enum StopReason { COMPLETED, MAX_TURNS, CANCELLED }

    public static final class Config {
        private final int maxTurns; private final long toolTimeoutMillis;
        public Config(int maxTurns,long toolTimeoutMillis){
            if(maxTurns<=0||toolTimeoutMillis<=0)throw new IllegalArgumentException("invalid runtime config");
            this.maxTurns=maxTurns;this.toolTimeoutMillis=toolTimeoutMillis;
        }
        public static Config defaults(){return new Config(16,30000);}
    }

    public static final class Cancellation {
        private final AtomicBoolean cancelled=new AtomicBoolean(false);
        public void cancel(){cancelled.set(true);} public boolean isCancelled(){return cancelled.get();}
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

    public AgentRuntime(Sessions.Manager sessions,Tools.Registry tools,Llm.Client llm,Context.Assembler context,Security.Policy policy,Config config){
        this.sessions=sessions;this.tools=tools;this.llm=llm;this.context=context;this.policy=policy;this.config=config;
    }

    public CompletableFuture<Result> run(String sessionId,String userMessage){return run(sessionId,userMessage,new Cancellation());}

    public CompletableFuture<Result> run(final String sessionId,final String userMessage,final Cancellation cancellation){
        return sessions.getOrCreate(sessionId).thenCompose(session -> {
            session.appendUser(userMessage);
            return sessions.save(session).thenCompose(v -> loop(session,1,"",cancellation));
        });
    }

    private CompletableFuture<Result> loop(final Sessions.Tree session,final int turn,final String lastText,final Cancellation cancellation){
        if(cancellation.isCancelled())return finish(session,lastText,Math.max(0,turn-1),StopReason.CANCELLED);
        if(turn>config.maxTurns)return finish(session,lastText,config.maxTurns,StopReason.MAX_TURNS);
        return llm.complete(context.assemble(session,tools.definitions()))
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
        if(index>=calls.size()||cancellation.isCancelled())return CompletableFuture.completedFuture(null);
        final Tools.Call call=calls.get(index);
        Security.Decision decision=policy.evaluate(call);
        CompletableFuture<Tools.Result> future=decision.isAllowed()
                ? tools.execute(call,config.toolTimeoutMillis)
                : CompletableFuture.completedFuture(new Tools.Result(call.getId(),call.getName(),"tool denied: "+decision.getReason(),true,0));
        return future.thenCompose(result -> {
            session.appendTool(result.getCallId(),result.getToolName(),result.getContent(),result.isError());
            return executeSequential(session,calls,index+1,cancellation);
        });
    }

    private CompletableFuture<Result> finish(Sessions.Tree session,String text,int turns,StopReason reason){
        Result result=new Result(session.getId(),text,turns,reason);
        return sessions.save(session).thenApply(v -> result);
    }
}
