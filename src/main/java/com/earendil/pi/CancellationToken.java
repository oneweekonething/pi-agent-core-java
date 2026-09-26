package com.earendil.pi;

import com.earendil.pi.internal.Asyncs;

import java.util.concurrent.atomic.AtomicBoolean;

/** 协作式取消令牌：可链接到父令牌，父令牌取消时子令牌一并视为已取消。出现在公开 SPI 签名中，属于正式公共 API。 */
public final class CancellationToken {
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private final CancellationToken parent;

    private CancellationToken(CancellationToken parent){this.parent=parent;}

    public static CancellationToken create(){return new CancellationToken(null);}

    public static CancellationToken linkedTo(CancellationToken parent){return new CancellationToken(Asyncs.require(parent,"parent"));}

    public void cancel(){cancelled.set(true);}

    public boolean isCancelled(){return cancelled.get()||(parent!=null&&parent.isCancelled());}
}
