package com.earendil.pi.context;

import com.earendil.pi.llm.Llm;
import com.earendil.pi.session.Sessions;
import com.earendil.pi.tool.Tools;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ContextTest {
    @Test public void toolCallArgumentsCountTowardBudget(){
        Sessions.Tree tree=new Sessions.Tree("s");
        tree.appendUser("a");
        StringBuilder big=new StringBuilder();
        for(int i=0;i<5000;i++)big.append('x');
        Map<String,Object> arguments=new LinkedHashMap<String,Object>();
        arguments.put("query",big.toString());
        tree.appendAssistant("",Collections.singletonList(new Sessions.ToolCallSnapshot("c1","search",arguments)));
        tree.appendTool("c1","search","ok",false);
        tree.appendAssistant("final",null);
        Context.Assembler assembler=new Context.Assembler(new Context.Config("sys",200,0));
        Llm.Request request=assembler.assemble(tree,Collections.<Tools.Definition>emptyList());
        assertEquals(3,request.getMessages().size());
        assertEquals("assistant",request.getMessages().get(0).getRole());
    }
}
