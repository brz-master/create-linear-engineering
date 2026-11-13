package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.List;

@GameTestHolder(CreateLinearEngineering.ID)
public final class PhysicsTestCleanup {
    @AfterBatch(batch = GameTestBatch.DEFAULT_BATCH_NAME)
    public static void removeFixtureBodies(ServerLevel level) {
        // Fixtures use absolute positions outside their empty test structures,
        // so Sable's structure-bounded success cleanup cannot find these bodies.
        // The GameTest runner releases forced chunks after this batch callback.
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        for (var body : List.copyOf(container.getAllSubLevels())) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }
}
