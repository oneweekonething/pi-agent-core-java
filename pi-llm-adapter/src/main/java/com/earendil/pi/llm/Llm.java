package com.earendil.pi.llm;

import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class Llm {
    private Llm() {}

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

    public interface Client { CompletableFuture<Response> complete(Request request); }

    public static final class FunctionalClient implements Client {
        private final Function<Request,Response> function;
        public FunctionalClient(Function<Request,Response> function){this.function=function;}
        public CompletableFuture<Response> complete(Request request){
            try{return CompletableFuture.completedFuture(function.apply(request));}
            catch(Throwable e){CompletableFuture<Response> f=new CompletableFuture<Response>();f.completeExceptionally(e);return f;}
        }
    }
}
