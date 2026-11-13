package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Create impact at 1 RPM, using Sable mass units (an ordinary full block is 1).
 * The native merged tracker includes attached Create contraptions. Several screw
 * carriages on one rigid body share that mass, each retaining its own base cost.
 */
final class RailLoad {
    private record Payload(double mass, int supports) {}
    private RailLoad() {}

    static float calculate(Collection<CarriageEntity> carriages) {
        if (carriages.isEmpty()) return 0;
        Map<ServerSubLevel, Payload> payloads = new HashMap<>();
        double impact = 0;
        for (CarriageEntity carriage : carriages) {
            if (!(Sable.HELPER.getContaining(carriage.getLevel(), carriage.getBlockPos()) instanceof ServerSubLevel body)) continue;
            Payload payload = payloads.computeIfAbsent(body, RailLoad::payload);
            impact += (.25 + payload.mass() / payload.supports() / 128)
                    * carriage.getBlockState().getValue(LinearBlock.GREASE).stress;
        }
        return (float) impact;
    }

    private static Payload payload(ServerSubLevel body) {
        int supports = 0;
        for (var actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof CarriageEntity carriage && !carriage.isRemoved() && carriage.isBound()
                    && carriage.hasJoint() && LinearBlock.isScrew(carriage.getBlockState())) supports++;
        }
        return new Payload(body.getMassTracker().getMass(), Math.max(1, supports));
    }
}
