package com.earendil.pi.security;

import com.earendil.pi.tool.Tools;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** 工具授权策略：在执行前评估调用，拒绝将转换为 error observation 而不是异常。 */
public final class Security {
    private Security() {}

    public static final class Decision {
        private final boolean allowed; private final String reason;
        private Decision(boolean allowed,String reason){this.allowed=allowed;this.reason=reason==null?"":reason;}
        public static Decision allow(){return new Decision(true,"");}
        public static Decision deny(String reason){return new Decision(false,reason);}
        public boolean isAllowed(){return allowed;} public String getReason(){return reason;}
    }

    /** 授权策略接口：实现方可基于工具名、参数或更复杂的上下文做判定。 */
    public interface Policy { Decision evaluate(Tools.Call call); }

    public static final class AllowAll implements Policy {
        public Decision evaluate(Tools.Call call){return Decision.allow();}
    }

    public static final class AllowList implements Policy {
        private final Set<String> names;
        public AllowList(Collection<String> names){this.names=new HashSet<String>(names);}
        public Decision evaluate(Tools.Call call){return names.contains(call.getName())?Decision.allow():Decision.deny("tool not allowed: "+call.getName());}
    }
}
