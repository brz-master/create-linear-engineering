package io.github.brzmaster.createlinearengineering;

import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.function.BiFunction;

/** The same signed basis drives models, collision shapes and Sable joint frames. */
public final class RailGeometry {
    public static final double CONTACT_REACH = 1; // rail half-length + full-length carriage half-length
    public static final double CONTACT_TOLERANCE = 1d / 1024; // native solver settling at touching faces
    // These shapes depend only on block type and mount. Reuse Minecraft's memoizer
    // rather than rebuilding the screw's CSG boxes for every collision query.
    private static final BiFunction<BlockState, Boolean, VoxelShape> SHAPES = Util.memoize(RailGeometry::buildShape);
    private static final java.util.function.Function<BlockState, VoxelShape> CARRIAGE_COLLISIONS =
            Util.memoize(state -> buildShape(state, true, CONTACT_TOLERANCE));
    private RailGeometry() {}

    static boolean sameMount(BlockState a, BlockState b) {
        return a != null && a.is(b.getBlock()) && a.getValue(LinearBlock.FACING) == b.getValue(LinearBlock.FACING)
                && normal(a) == normal(b);
    }

    public static boolean compatible(BlockState a, BlockState b) {
        return LinearBlock.isScrew(a) == LinearBlock.isScrew(b)
                && a.getValue(LinearBlock.FACING).getAxis() == b.getValue(LinearBlock.FACING).getAxis()
                && normal(a) == normal(b);
    }

    // Default mounting for v0.1 saves, which only persisted the rail direction.
    public static Direction normal(Direction facing) {
        return switch (facing) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    public static Direction normal(BlockState state) {
        Direction facing = state.getValue(LinearBlock.FACING);
        Direction normal = state.getValue(LinearBlock.NORMAL);
        return facing.getAxis() == normal.getAxis() ? normal(facing) : normal;
    }

    public static Quaterniond frame(BlockState state) {
        return frame(state.getValue(LinearBlock.FACING), normal(state));
    }

    public static Quaterniond frame(Direction facing) { return frame(facing, normal(facing)); }

    public static Quaterniond frame(Direction facing, Direction normal) {
        Vector3d z = vector(facing), y = vector(normal), x = y.cross(z, new Vector3d());
        return new Quaterniond().setFromNormalized(new Matrix3d().setColumn(0, x).setColumn(1, y).setColumn(2, z));
    }

    private static Vector3d vector(Direction d) { return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ()); }

    public static VoxelShape shape(BlockState state, boolean carriage) {
        return SHAPES.apply(LinearBlock.bare(state), carriage);
    }

    static VoxelShape collisionShape(BlockState state, boolean carriage) {
        return carriage ? CARRIAGE_COLLISIONS.apply(LinearBlock.bare(state)) : shape(state, false);
    }

    private static VoxelShape buildShape(BlockState state, boolean carriage) {
        return buildShape(state, carriage, 0);
    }

    private static VoxelShape buildShape(BlockState state, boolean carriage, double clearance) {
        // Microscopic running clearance keeps native voxel seam contacts from
        // catching the sleeve. Its axial length and visible outline remain 16/16.
        AABB[] boxes = carriage ? new AABB[]{
                new AABB(0, .75 + clearance, 0, 1, 1, 1),
                new AABB(0, .25 + clearance, 0, .375 - clearance, .75 + clearance, 1),
                new AABB(.625 + clearance, .25 + clearance, 0, 1, .75 + clearance, 1)
        } : new AABB[]{new AABB(0, 0, 0, 1, .25, 1), new AABB(.375, .25, 0, .625, .75, 1)};
        if (LinearBlock.isScrew(state)) {
            var screwBoxes = new java.util.ArrayList<AABB>();
            if (carriage) {
                screwBoxes.add(new AABB(0, .75 + clearance, 0, 1, 1, 1));
                screwBoxes.add(new AABB(0, .25 + clearance, 0, .25 - clearance, .75 + clearance, 1));
                screwBoxes.add(new AABB(.75 + clearance, .25 + clearance, 0, 1, .75 + clearance, 1));
                for (int z : new int[]{3, 7, 11}) {
                    screwBoxes.add(new AABB(4/16d, 10.5/16d, z/16d, 6.5/16d, 12/16d, (z+2)/16d));
                    screwBoxes.add(new AABB(9.5/16d, 10.5/16d, z/16d, 12/16d, 12/16d, (z+2)/16d));
                }
            } else {
                screwBoxes.add(new AABB(0, 0, 0, 1, .25, 1));
                screwBoxes.add(new AABB(6/16d, 4/16d, 0, 10/16d, 5/16d, 1));
                screwBoxes.add(new AABB(6/16d, 6/16d, 0, 10/16d, 10/16d, 1));
            }
            boxes = screwBoxes.toArray(AABB[]::new);
        }
        VoxelShape shape = Shapes.empty();
        Quaterniond rotation = frame(state);
        for (AABB box : boxes) {
            Vector3d min = new Vector3d(Double.POSITIVE_INFINITY), max = new Vector3d(Double.NEGATIVE_INFINITY);
            for (int i = 0; i < 8; i++) {
                Vector3d corner = new Vector3d((i & 1) == 0 ? box.minX : box.maxX,
                        (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
                rotation.transform(corner.sub(.5, .5, .5)).add(.5, .5, .5);
                min.min(corner); max.max(corner);
            }
            // Snap exact quarter turns to the model grid, avoiding thin collision faces.
            min.mul(1024).round().div(1024); max.mul(1024).round().div(1024);
            shape = Shapes.or(shape, Shapes.create(new AABB(min.x, min.y, min.z, max.x, max.y, max.z)));
        }
        return shape;
    }
}
