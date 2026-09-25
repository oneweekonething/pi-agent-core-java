package com.earendil.pi.security;

import com.earendil.pi.tool.Tools;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class Security {
    private Security() {}

    public static final class Decision {
        private final boolean allowed; private final String reason;
        private Decision(boolean allowed,String reason){this.allowed=allowed;this.reason=reason==null?"":reason;}
        public static Decision allow(){return new Decision(true,"");}
        public static Decision deny(String reason){return new Decision(false,reason);}
        public boolean isAllowed(){return allowed;} public String getReason(){return reason;}
    }

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
