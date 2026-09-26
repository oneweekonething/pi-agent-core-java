package com.earendil.pi.internal;

import java.util.concurrent.atomic.AtomicBoolean;

/** 协作式取消令牌：可链接到父令牌，父令牌取消时子令牌一并视为已取消。 */
public final class Cancellation {
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private final Cancellation parent;

    private Cancellation(Cancellation parent){this.parent=parent;}

    public static Cancellation create(){return new Cancellation(null);}

    public static Cancellation linkedTo(Cancellation parent){return new Cancellation(Asyncs.require(parent,"parent"));}

    public void cancel(){cancelled.set(true);}

    public boolean isCancelled(){return cancelled.get()||(parent!=null&&parent.isCancelled());}
}
