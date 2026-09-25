package com.earendil.pi.session;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SessionsTest {
    @Test public void branchPreservesHistory(){
        Sessions.Tree tree=new Sessions.Tree("s");
        Sessions.Node first=tree.appendUser("one");
        Sessions.Node second=tree.appendAssistant("two",null);
        tree.branchFrom(first.getId(),"alt");
        assertEquals(2,tree.activePath().size());
        assertEquals(3,tree.allNodes().size());
        tree.rewindTo(second.getId());
        assertEquals("two",tree.activePath().get(1).getContent());
    }
}
