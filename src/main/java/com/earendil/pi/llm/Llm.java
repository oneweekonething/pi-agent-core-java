package com.earendil.pi.llm;

import com.earendil.pi.internal.Asyncs;
import com.earendil.pi.CancellationToken;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** LLM 领域类型：供应商中立的请求/响应/消息抽象，以及客户端接口与重试装饰器。 */
public final class Llm {
    private Llm() {}

    /** 单条模型消息：user/system/assistant（可携带 tool calls）或 tool 结果。 */
    public static final class Message {
        private final String role,content,toolCallId,toolName;
        private final List<Tools.Call> toolCalls;
        private final boolean error;
        private Message(String role,String content,List<Tools.Call> calls,String callId,String toolName,boolean error){
            this.role=role;this.content=content==null?"":content;
            this.toolCalls=Collections.unmodifiableList(calls==null?new ArrayList<Tools.Call>():new ArrayList<Tools.Call>(calls));
            this.toolCallId=callId;this.toolName=toolName;this.error=error;
        }
        public static Message user(String text){return new Message("user",text,null,null,null,false);}
        public static Message system(String text){return new Message("system",text,null,null,null,false);}
        public static Message assistant(String text,List<Tools.Call> calls){return new Message("assistant",text,calls,null,null,false);}
        public static Message tool(String id,String name,String text,boolean error){return new Message("tool",text,null,id,name,error);}
        public String getRole(){return role;} public String getContent(){return content;} public List<Tools.Call> getToolCalls(){return toolCalls;}
        public String getToolCallId(){return toolCallId;} public String getToolName(){return toolName;} public boolean isError(){return error;}
    }

    public static final class Request {
        private final String systemPrompt;
        private final List<Message> messages;
        private final List<Tools.Definition> tools;
        public Request(String systemPrompt,List<Message> messages,List<Tools.Definition> tools){
            this.systemPrompt=systemPrompt==null?"":systemPrompt;
            this.messages=Collections.unmodifiableList(new ArrayList<Message>(messages));
            this.tools=Collections.unmodifiableList(new ArrayList<Tools.Definition>(tools));
        }
        public String getSystemPrompt(){return systemPrompt;} public List<Message> getMessages(){return messages;} public List<Tools.Definition> getTools(){return tools;}
    }

    public static final class Response {
        private final String text; private final List<Tools.Call> calls;
        public Response(String text,List<Tools.Call> calls){this.text=text==null?"":text;this.calls=Collections.unmodifiableList(calls==null?new ArrayList<Tools.Call>():new ArrayList<Tools.Call>(calls));}
        public static Response answer(String text){return new Response(text,Collections.<Tools.Call>emptyList());}
        public static Response tools(String text,List<Tools.Call> calls){return new Response(text,calls);}
        public String getText(){return text;} public List<Tools.Call> getToolCalls(){return calls;}
    }

    /** 供应商中立的模型客户端接口；实现方可对接 OpenAI、Claude 等，可选支持取消令牌。 */
    public interface Client {
        CompletableFuture<Response> complete(Request request);
        default CompletableFuture<Response> complete(Request request,CancellationToken cancellation){
            return complete(request);
        }
    }

    public static final class FunctionalClient implements Client {
        private final Function<Request,Response> function;
        public FunctionalClient(Function<Request,Response> function){this.function=function;}
        public CompletableFuture<Response> complete(Request request){
            try{return CompletableFuture.completedFuture(function.apply(request));}
            catch(Throwable e){CompletableFuture<Response> f=new CompletableFuture<Response>();f.completeExceptionally(e);return f;}
        }
    }

    /** 重试装饰器：指数退避加抖动；可通过 {@link RetryPolicy} 限定只重试瞬态错误，取消令牌生效后停止调度新尝试。 */
    public static final class RetryClient implements Client {
        public interface RetryPolicy { boolean shouldRetry(Throwable error); }
        private final Client delegate;
        private final RetryPolicy policy;
        private final int maxAttempts;
        private final long baseBackoffMillis,maxBackoffMillis;
        private final java.util.concurrent.ConcurrentMap<CompletableFuture<Response>,CancellationToken> pending=
                new java.util.concurrent.ConcurrentHashMap<CompletableFuture<Response>,CancellationToken>();
        private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean(false);
        private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t=new Thread(r,"pi-llm-retry"); t.setDaemon(true); return t;
        });
        public RetryClient(Client delegate,int maxAttempts,long baseBackoffMillis,long maxBackoffMillis){
            this(delegate,maxAttempts,baseBackoffMillis,maxBackoffMillis,new RetryPolicy(){
                public boolean shouldRetry(Throwable error){return true;}
            });
        }
        public RetryClient(Client delegate,int maxAttempts,long baseBackoffMillis,long maxBackoffMillis,RetryPolicy policy){
            this.delegate=Asyncs.require(delegate,"delegate");
            this.policy=Asyncs.require(policy,"policy");
            if(maxAttempts<1)throw new IllegalArgumentException("maxAttempts must be >= 1");
            if(baseBackoffMillis<=0||maxBackoffMillis<baseBackoffMillis)throw new IllegalArgumentException("invalid backoff config");
            this.maxAttempts=maxAttempts;this.baseBackoffMillis=baseBackoffMillis;this.maxBackoffMillis=maxBackoffMillis;
        }
        public CompletableFuture<Response> complete(Request request){return complete(request,null);}
        public CompletableFuture<Response> complete(final Request request,final CancellationToken cancellation){
            final CancellationToken token=cancellation==null?CancellationToken.create():CancellationToken.linkedTo(cancellation);
            final CompletableFuture<Response> result=new CompletableFuture<Response>();
            pending.put(result,token);
            result.whenComplete((response,error)->pending.remove(result));
            if(closed.get()){
                token.cancel();
                result.completeExceptionally(new CancellationException("retry client closed"));
                return result;
            }
            attempt(request,1,token,result);
            return result;
        }
        private void attempt(final Request request,final int attempt,final CancellationToken token,final CompletableFuture<Response> result){
            if(result.isDone())return;
            if(closed.get()){
                token.cancel();
                result.completeExceptionally(new CancellationException("retry client closed"));
                return;
            }
            if(token.isCancelled()){result.completeExceptionally(new CancellationException("llm call cancelled"));return;}
            CompletableFuture<Response> call;
            try{call=Asyncs.require(delegate.complete(request,token),"delegate future");}
            catch(Throwable e){call=new CompletableFuture<Response>();call.completeExceptionally(e);}
            call.whenComplete((response,error)->{
                try{
                    if(error==null){result.complete(response);return;}
                    if(token.isCancelled()){result.completeExceptionally(new CancellationException("llm call cancelled"));return;}
                    if(attempt>=maxAttempts||!policy.shouldRetry(Asyncs.unwrap(error))){
                        result.completeExceptionally(Asyncs.unwrap(error));return;
                    }
                    scheduler.schedule(new Runnable(){
                        public void run(){attempt(request,attempt+1,token,result);}
                    },delayFor(attempt),TimeUnit.MILLISECONDS);
                }catch(Throwable callbackError){
                    result.completeExceptionally(Asyncs.unwrap(callbackError));
                }
            });
        }
        private long delayFor(int attempt){
            long backoff=baseBackoffMillis;
            for(int i=1;i<attempt&&backoff<maxBackoffMillis;i++)backoff=Math.min(maxBackoffMillis,backoff*2);
            return backoff+ThreadLocalRandom.current().nextLong(backoff/2+1);
        }
        public void close(){
            if(closed.compareAndSet(false,true)){
                scheduler.shutdownNow();
                CancellationException closedError=new CancellationException("retry client closed");
                for(java.util.Map.Entry<CompletableFuture<Response>,CancellationToken> waiting:pending.entrySet()){
                    waiting.getValue().cancel();
                    waiting.getKey().completeExceptionally(closedError);
                }
                pending.clear();
            }
        }
    }
}
