package com.earendil.pi.tool;

import com.earendil.pi.internal.Asyncs;
import com.earendil.pi.CancellationToken;

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
import java.util.concurrent.TimeoutException;

/** 工具领域类型：定义、调用、注册表与执行结果；工具失败一律归一化为 error observation。 */
public final class Tools {
    private Tools() {}

    public enum ParameterType { STRING, NUMBER, INTEGER, BOOLEAN, OBJECT, ARRAY }

    /** 工具参数定义：名称、描述、是否必填、类型（用于执行前校验）。 */
    public static final class Parameter {
        private final String name,description;
        private final boolean required;
        private final ParameterType type;
        public Parameter(String name,String description,boolean required){this(name,description,required,ParameterType.STRING);}
        public Parameter(String name,String description,boolean required,ParameterType type){
            this.name=Asyncs.nonBlank(name,"name");this.description=description==null?"":description;this.required=required;
            this.type=Asyncs.require(type,"type");
        }
        public String getName(){return name;} public String getDescription(){return description;} public boolean isRequired(){return required;}
        public ParameterType getType(){return type;}
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

    /** 模型发起的一次工具调用：call id、工具名、参数。 */
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

    /** 工具执行结果：call id、工具名、内容、错误标志与耗时，作为 observation 回给模型。 */
    public static final class Result {
        private final String callId,toolName,content; private final boolean error; private final long durationMillis;
        public Result(String callId,String toolName,String content,boolean error,long durationMillis){
            this.callId=callId;this.toolName=toolName;this.content=content==null?"":content;this.error=error;this.durationMillis=durationMillis;
        }
        public String getCallId(){return callId;} public String getToolName(){return toolName;} public String getContent(){return content;}
        public boolean isError(){return error;} public long getDurationMillis(){return durationMillis;}
    }

    /** 工具参数校验：必填缺失与类型不匹配返回错误消息，执行前拦截。 */
    public static final class Arguments {
        private Arguments() {}
        public static String validate(Definition definition,Map<String,Object> arguments){
            Map<String,Object> safe=arguments==null?Collections.<String,Object>emptyMap():arguments;
            for(Parameter parameter:definition.getParameters()){
                if(!safe.containsKey(parameter.getName())||safe.get(parameter.getName())==null){
                    if(parameter.isRequired())return "missing required argument: "+parameter.getName();
                    continue;
                }
                String mismatch=mismatch(safe.get(parameter.getName()),parameter.getType());
                if(mismatch!=null)return "argument "+parameter.getName()+" "+mismatch;
            }
            return null;
        }
        private static String mismatch(Object value,ParameterType type){
            switch(type){
                case STRING:return value instanceof String?null:"expected string";
                case NUMBER:return value instanceof Number?null:"expected number";
                case INTEGER:{
                    if(value instanceof Integer||value instanceof Long)return null;
                    if(value instanceof Number){
                        double d=((Number)value).doubleValue();
                        return d==Math.floor(d)?null:"expected integer";
                    }
                    return "expected integer";
                }
                case BOOLEAN:return value instanceof Boolean?null:"expected boolean";
                case OBJECT:return value instanceof Map?null:"expected object";
                case ARRAY:return value instanceof List?null:"expected array";
                default:return null;
            }
        }
    }

    /** 工具契约：提供定义并以 future 返回执行结果；可重写带取消令牌的重载以支持协作取消。 */
    public interface Tool {
        Definition definition();
        CompletableFuture<Execution> execute(Map<String,Object> arguments);
        default CompletableFuture<Execution> execute(Map<String,Object> arguments,CancellationToken cancellation){
            return execute(arguments);
        }
    }

    /** 工具注册与执行入口：参数校验、JDK 8 超时竞速、结果截断与 error observation 归一化。 */
    public static final class Registry implements AutoCloseable {
        public static final int DEFAULT_MAX_RESULT_CHARS=16384;
        private final ConcurrentMap<String,Tool> tools=new ConcurrentHashMap<String,Tool>();
        private final int maxResultChars;
        private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t=new Thread(r,"pi-tool-timeout"); t.setDaemon(true); return t;
        });
        public Registry(){this(DEFAULT_MAX_RESULT_CHARS);}
        public Registry(int maxResultChars){
            Asyncs.check(maxResultChars>0,"maxResultChars must be > 0");
            this.maxResultChars=maxResultChars;
        }
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
        /** 执行前校验：未知工具或参数不合法时返回错误消息，合法返回 null。策略评估应基于通过校验的调用。 */
        public String validate(Call call){
            Tool tool=tools.get(Asyncs.require(call,"call").getName());
            if(tool==null)return "unknown tool: "+call.getName();
            return Arguments.validate(tool.definition(),call.getArguments());
        }
        public CompletableFuture<Result> execute(Call call,long timeoutMillis){
            return execute(call,timeoutMillis,null);
        }
        public CompletableFuture<Result> execute(final Call call,long timeoutMillis,final CancellationToken cancellation){
            final Tool tool=tools.get(Asyncs.require(call,"call").getName());
            if(tool==null)return CompletableFuture.completedFuture(new Result(call.getId(),call.getName(),"unknown tool: "+call.getName(),true,0));
            String invalid=Arguments.validate(tool.definition(),call.getArguments());
            if(invalid!=null)return CompletableFuture.completedFuture(new Result(call.getId(),call.getName(),invalid,true,0));
            final CancellationToken token=cancellation==null?CancellationToken.create():CancellationToken.linkedTo(cancellation);
            final long start=System.nanoTime();
            final CompletableFuture<Execution> future;
            try{future=Asyncs.require(tool.execute(call.getArguments(),token),"tool future");}
            catch(Throwable e){return CompletableFuture.completedFuture(new Result(call.getId(),call.getName(),truncate(message(e)),true,elapsed(start)));}
            return Asyncs.withTimeout(future,timeoutMillis,TimeUnit.MILLISECONDS,scheduler).handle((execution,error)->{
                if(error!=null){
                    if(Asyncs.unwrap(error) instanceof TimeoutException)token.cancel();
                    return new Result(call.getId(),call.getName(),truncate(message(Asyncs.unwrap(error))),true,elapsed(start));
                }
                Execution safe=execution==null?Execution.error("tool returned null"):execution;
                return new Result(call.getId(),call.getName(),truncate(safe.getContent()),safe.isError(),elapsed(start));
            });
        }
        private String truncate(String content){
            if(content==null||content.length()<=maxResultChars)return content;
            return content.substring(0,maxResultChars)+"...[truncated "+(content.length()-maxResultChars)+" chars]";
        }
        private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
        private static String message(Throwable e){String m=e==null?null:e.getMessage();return m==null||m.trim().isEmpty()?(e==null?"tool failed":e.getClass().getSimpleName()):m;}
        public void close(){scheduler.shutdownNow();}
    }
}
