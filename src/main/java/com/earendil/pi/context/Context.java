package com.earendil.pi.context;

import com.earendil.pi.llm.Llm;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 上下文组装：从会话活跃路径构造模型请求，并按 token 预算从最老的消息开始裁剪。 */
public final class Context {
    private Context() {}

    public static final class Config {
        private final String systemPrompt; private final int maxTokens,reserveTokens;
        public Config(String systemPrompt,int maxTokens,int reserveTokens){
            if(maxTokens<=0||reserveTokens<0||reserveTokens>=maxTokens)throw new IllegalArgumentException("invalid token budget");
            this.systemPrompt=systemPrompt==null?"":systemPrompt;this.maxTokens=maxTokens;this.reserveTokens=reserveTokens;
        }
        public static Config defaults(){return new Config("You are a coding agent. Use tools when needed, then use their observations before answering.",32000,4000);}
    }

    /** 上下文组装器：保持 assistant tool-call 与 tool-result 配对，估算计入参数与工具 schema。 */
    public static final class Assembler {
        private final Config config;
        public Assembler(Config config){this.config=config;}
        public Llm.Request assemble(Sessions.Tree session,Collection<Tools.Definition> defs){
            List<Llm.Message> all=new ArrayList<Llm.Message>();
            for(Sessions.Node node:session.activePath())all.add(convert(node));
            int budget=config.maxTokens-config.reserveTokens-estimate(config.systemPrompt)-estimateTools(defs);
            List<Llm.Message> trimmed=trim(all,Math.max(1,budget));
            return new Llm.Request(config.systemPrompt,trimmed,new ArrayList<Tools.Definition>(defs));
        }
        private Llm.Message convert(Sessions.Node n){
            return switch(n.getRole()){
                case USER -> Llm.Message.user(n.getContent());
                case SYSTEM -> Llm.Message.system(n.getContent());
                case TOOL_RESULT -> Llm.Message.tool(n.getToolCallId(),n.getToolName(),n.getContent(),n.isError());
                case ASSISTANT -> {
                    List<Tools.Call> calls=new ArrayList<>();
                    for(Sessions.ToolCallSnapshot c:n.getToolCalls())calls.add(new Tools.Call(c.getId(),c.getName(),new LinkedHashMap<>(c.getArguments())));
                    yield Llm.Message.assistant(n.getContent(),calls);
                }
            };
        }
        private List<Llm.Message> trim(List<Llm.Message> messages,int budget){
            if(messages.isEmpty())return Collections.emptyList();
            int start=messages.size()-1,used=0;
            for(int i=messages.size()-1;i>=0;i--){
                int cost=estimate(messages.get(i));
                if(i<messages.size()-1&&used+cost>budget){start=i+1;break;}
                used+=cost;start=i;
            }
            if("tool".equals(messages.get(start).getRole())){
                while(start>0&&"tool".equals(messages.get(start).getRole()))start--;
                if(!"assistant".equals(messages.get(start).getRole()))start++;
            }
            return new ArrayList<Llm.Message>(messages.subList(start,messages.size()));
        }
        private int estimate(Llm.Message message){
            int n=estimate(message.getContent())+8;
            for(Tools.Call call:message.getToolCalls())n+=estimate(call.getName())+estimateValue(call.getArguments())+8;
            return n;
        }
        private int estimateValue(Object value){
            if(value==null)return 4;
            if(value instanceof Map<?,?> map){
                int n=2;
                for(Map.Entry<?,?> entry:map.entrySet())n+=estimate(String.valueOf(entry.getKey()))+estimateValue(entry.getValue())+4;
                return n;
            }
            if(value instanceof List<?> list){
                int n=2;
                for(Object item:list)n+=estimateValue(item)+2;
                return n;
            }
            if(value instanceof Number||value instanceof Boolean)return 8;
            return estimate(String.valueOf(value));
        }
        private int estimateTools(Collection<Tools.Definition> defs){
            int n=0;
            for(Tools.Definition d:defs){
                n+=estimate(d.getName())+estimate(d.getDescription())+16;
                for(Tools.Parameter p:d.getParameters())n+=estimate(p.getName())+estimate(p.getDescription())+16;
            }
            return n;
        }
        private int estimate(String s){return s==null||s.isEmpty()?0:Math.max(1,(s.length()+3)/4);}
    }
}
