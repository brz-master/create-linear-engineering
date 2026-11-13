package io.github.brzmaster.createlinearengineering;

import net.minecraft.util.StringRepresentable;

/** Persistent lubrication; no timer, inventory or separate physics state. */
public enum Grease implements StringRepresentable {
    NONE(1, 1), HIGH(.5, .25), LOW(0, .0625);
    public final double friction;
    public final double stress;
    Grease(double friction, double stress) { this.friction = friction; this.stress = stress; }
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
