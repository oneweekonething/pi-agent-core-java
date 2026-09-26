package com.earendil.pi.api;

import com.earendil.pi.agent.AgentRuntime;
import com.earendil.pi.context.Context;
import com.earendil.pi.llm.Llm;
import com.earendil.pi.security.Security;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** 面向最终用户的 SDK 门面：组装会话、工具、策略与运行时，并提供可运行 Demo。 */
public final class PiAgent implements AutoCloseable {
    private final AgentRuntime runtime;
    private final Tools.Registry registry;
    private final Sessions.Manager sessions;

    private PiAgent(AgentRuntime runtime,Tools.Registry registry,Sessions.Manager sessions){
        this.runtime=runtime;this.registry=registry;this.sessions=sessions;
    }

    public static Builder builder(Llm.Client llm){return new Builder(llm);}
    public CompletableFuture<AgentRuntime.Result> run(String sessionId,String message){return runtime.run(sessionId,message);}
    public Sessions.Manager sessions(){return sessions;}
    public void close(){runtime.close();registry.close();}

    /** 构建器：注册工具、选择 Repository/策略/配置并组装 {@link PiAgent}。 */
    public static final class Builder {
        private final Llm.Client llm;
        private final List<Tools.Tool> tools=new ArrayList<Tools.Tool>();
        private Sessions.Repository repository=new Sessions.InMemoryRepository();
        private Security.Policy policy=new Security.AllowAll();
        private Context.Config context=Context.Config.defaults();
        private AgentRuntime.Config runtime=AgentRuntime.Config.defaults();

        private Builder(Llm.Client llm){this.llm=llm;}
        public Builder tool(Tools.Tool tool){tools.add(tool);return this;}
        public Builder repository(Sessions.Repository repository){this.repository=repository;return this;}
        public Builder policy(Security.Policy policy){this.policy=policy;return this;}
        public Builder context(Context.Config context){this.context=context;return this;}
        public Builder runtime(AgentRuntime.Config runtime){this.runtime=runtime;return this;}
        public PiAgent build(){
            Sessions.Manager sessions=new Sessions.Manager(repository);
            Tools.Registry registry=new Tools.Registry();
            for(Tools.Tool tool:tools)registry.register(tool);
            AgentRuntime engine=new AgentRuntime(sessions,registry,llm,new Context.Assembler(context),policy,runtime);
            return new PiAgent(engine,registry,sessions);
        }
    }

    public static void main(String[] args){
        final AtomicInteger turn=new AtomicInteger();
        Llm.Client llm=new Llm.FunctionalClient(request -> {
            if(turn.getAndIncrement()==0){
                Map<String,Object> values=new LinkedHashMap<String,Object>();
                values.put("text","hello from the tool");
                return Llm.Response.tools("calling echo",Collections.singletonList(Tools.Call.create("echo",values)));
            }
            Llm.Message last=request.getMessages().get(request.getMessages().size()-1);
            return Llm.Response.answer("Observed: "+last.getContent());
        });

        Tools.Tool echo=new Tools.Tool(){
            public Tools.Definition definition(){return new Tools.Definition("echo","Returns supplied text.",Arrays.asList(new Tools.Parameter("text","Text to return.",true)));}
            public CompletableFuture<Tools.Execution> execute(Map<String,Object> arguments){
                return CompletableFuture.completedFuture(Tools.Execution.ok(String.valueOf(arguments.get("text"))));
            }
        };

        try(PiAgent agent=PiAgent.builder(llm).tool(echo).build()){
            AgentRuntime.Result result=agent.run("demo","Use echo, then answer.").join();
            System.out.println(result.getReason()+": "+result.getText());
        }
    }
}
