package com.earendil.pi.tool;

import com.earendil.pi.common.Asyncs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Tools {
    private Tools() {}

    public static final class Parameter {
        private final String name,description;
        private final boolean required;
        public Parameter(String name,String description,boolean required){
            this.name=Asyncs.nonBlank(name,"name");this.description=description==null?"":description;this.required=required;
        }
        public String getName(){return name;} public String getDescription(){return description;} public boolean isRequired(){return required;}
    }

    public static final class Definition {
        private final String name,description;
        private final List<Parameter> parameters;
        public Definition(String name,String description,List<Parameter> parameters){
            this.name=Asyncs.nonBlank(name,"name");this.description=description==null?"":description;
            this.parameters=Collections.unmodifiableList(parameters==null?new ArrayList<Parameter>():new ArrayList<Parameter>(parameters));
        }
        public String getName(){return name;} public String getDescription(){return description;} public List<Parameter> getParameters(){return parameters;}
    }

    public static final class Call {
        private final String id,name;
        private final Map<String,Object> arguments;
        public Call(String id,String name,Map<String,Object> arguments){
            this.id=Asyncs.nonBlank(id,"id");this.name=Asyncs.nonBlank(name,"name");
            this.arguments=Collections.unmodifiableMap(arguments==null?new LinkedHashMap<String,Object>():new LinkedHashMap<String,Object>(arguments));
        }
        public static Call create(String name,Map<String,Object> arguments){return new Call(UUID.randomUUID().toString(),name,arguments);}
        public String getId(){return id;} public String getName(){return name;} public Map<String,Object> getArguments(){return arguments;}
    }

    public static final class Execution {
        private final String content; private final boolean error;
        private Execution(String content,boolean error){this.content=content==null?"":content;this.error=error;}
        public static Execution ok(String content){return new Execution(content,false);}
        public static Execution error(String content){return new Execution(content,true);}
        public String getContent(){return content;} public boolean isError(){return error;}
    }

    public static final class Result {
        private final String callId,toolName,content; private final boolean error; private final long durationMillis;
        public Result(String callId,String toolName,String content,boolean error,long durationMillis){
            this.callId=callId;this.toolName=toolName;this.content=content==null?"":content;this.error=error;this.durationMillis=durationMillis;
        }
        public String getCallId(){return callId;} public String getToolName(){return toolName;} public String getContent(){return content;}
        public boolean isError(){return error;} public long getDurationMillis(){return durationMillis;}
    }

    public interface Tool {
        Definition definition();
        CompletableFuture<Execution> execute(Map<String,Object> arguments);
    }

    public static final class Registry implements AutoCloseable {
        private final ConcurrentMap<String,Tool> tools=new ConcurrentHashMap<String,Tool>();
        private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t=new Thread(r,"pi-tool-timeout"); t.setDaemon(true); return t;
        });
        public void register(Tool tool){
            Tool safe=Asyncs.require(tool,"tool");
            String name=Asyncs.require(safe.definition(),"definition").getName();
            if(tools.putIfAbsent(name,safe)!=null)throw new IllegalArgumentException("tool already registered: "+name);
        }
        public Collection<Definition> definitions(){
            List<Definition> result=new ArrayList<Definition>();
            for(Tool tool:tools.values())result.add(tool.definition());
            Collections.sort(result,(a,b)->a.getName().compareTo(b.getName()));
            return Collections.unmodifiableList(result);
        }
        public CompletableFuture<Result> execute(final Call call,long timeoutMillis){
            final Tool tool=tools.get(Asyncs.require(call,"call").getName());
            if(tool==null)return CompletableFuture.completedFuture(new Result(call.getId(),call.getName(),"unknown tool: "+call.getName(),true,0));
            final long start=System.nanoTime();
            final CompletableFuture<Execution> future;
            try{future=Asyncs.require(tool.execute(call.getArguments()),"tool future");}
            catch(Throwable e){return CompletableFuture.completedFuture(new Result(call.getId(),call.getName(),message(e),true,elapsed(start)));}
            return Asyncs.withTimeout(future,timeoutMillis,TimeUnit.MILLISECONDS,scheduler).handle((execution,error)->{
                if(error!=null)return new Result(call.getId(),call.getName(),message(Asyncs.unwrap(error)),true,elapsed(start));
                Execution safe=execution==null?Execution.error("tool returned null"):execution;
                return new Result(call.getId(),call.getName(),safe.getContent(),safe.isError(),elapsed(start));
            });
        }
        private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
        private static String message(Throwable e){String m=e==null?null:e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"tool failed":e.getClass().getSimpleName()):m;}
        public void close(){scheduler.shutdownNow();}
    }
}
