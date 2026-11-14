package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Collection;
import java.util.WeakHashMap;
import java.util.Map;

/** Create impact at 1 RPM, using Sable mass units (an ordinary full block is 1).
 * The native merged tracker includes attached Create contraptions. Several screw
 * carriages on one rigid body share that mass, each retaining its own base cost.
 */
final class RailLoad {
    private record Payload(long tick, double massPerSupport) {}
    // Server-thread only. Values do not retain their body or its level.
    private static final Map<ServerSubLevel, Payload> PAYLOADS = new WeakHashMap<>();
    private RailLoad() {}

    static float calculate(Collection<CarriageEntity> carriages) {
        if (carriages.isEmpty()) return 0;
        double impact = 0;
        for (CarriageEntity carriage : carriages) {
            if (!(Sable.HELPER.getContaining(carriage.getLevel(), carriage.getBlockPos()) instanceof ServerSubLevel body)) continue;
            impact += (.25 + massPerSupport(body) / 128)
                    * carriage.getBlockState().getValue(LinearBlock.GREASE).stress;
        }
        return (float) impact;
    }

    private static double massPerSupport(ServerSubLevel body) {
        long tick = body.getLevel().getGameTime();
        Payload cached = PAYLOADS.get(body);
        if (cached != null && cached.tick() == tick) return cached.massPerSupport();
        int supports = 0;
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof CarriageEntity carriage && !carriage.isRemoved() && carriage.isBound()
                    && carriage.hasJoint() && LinearBlock.isScrew(carriage.getBlockState())) supports++;
        }
        double share = body.getMassTracker().getMass() / Math.max(1, supports);
        PAYLOADS.put(body, new Payload(tick, share));
        return share;
    }
}
