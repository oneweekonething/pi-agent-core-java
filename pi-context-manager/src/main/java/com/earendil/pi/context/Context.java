package com.earendil.pi.context;

import com.earendil.pi.llm.Llm;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

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
            if(n.getRole()==Sessions.Role.USER)return Llm.Message.user(n.getContent());
            if(n.getRole()==Sessions.Role.SYSTEM)return Llm.Message.system(n.getContent());
            if(n.getRole()==Sessions.Role.TOOL_RESULT)return Llm.Message.tool(n.getToolCallId(),n.getToolName(),n.getContent(),n.isError());
            List<Tools.Call> calls=new ArrayList<Tools.Call>();
            for(Sessions.ToolCallSnapshot c:n.getToolCalls())calls.add(new Tools.Call(c.getId(),c.getName(),new LinkedHashMap<String,Object>(c.getArguments())));
            return Llm.Message.assistant(n.getContent(),calls);
        }
        private List<Llm.Message> trim(List<Llm.Message> messages,int budget){
            if(messages.isEmpty())return Collections.emptyList();
            int start=messages.size()-1,used=0;
            for(int i=messages.size()-1;i>=0;i--){
                int cost=estimate(messages.get(i).getContent())+8;
                if(i<messages.size()-1&&used+cost>budget){start=i+1;break;}
                used+=cost;start=i;
            }
            if("tool".equals(messages.get(start).getRole())){
                while(start>0&&"tool".equals(messages.get(start).getRole()))start--;
                if(!"assistant".equals(messages.get(start).getRole()))start++;
            }
            return new ArrayList<Llm.Message>(messages.subList(start,messages.size()));
        }
        private int estimateTools(Collection<Tools.Definition> defs){int n=0;for(Tools.Definition d:defs)n+=estimate(d.getName())+estimate(d.getDescription())+16;return n;}
        private int estimate(String s){return s==null||s.isEmpty()?0:Math.max(1,(s.length()+3)/4);}
    }
}
