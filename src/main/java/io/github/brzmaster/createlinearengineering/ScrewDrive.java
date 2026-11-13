package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** A lead screw advances one quarter block per revolution. Sable owns the servo. */
final class ScrewDrive {
    static final double PITCH = .25;
    // A finite, mass-scaled force lets contact constraints stall the drive.
    private static final double MAX_DRIVE_ACCELERATION = 40;
    // The native limits own position holding. Make the motor predominantly a
    // velocity controller, so load resistance does not require a stored phase error.
    private static final double STIFFNESS = 1_000;
    private static final double DAMPING = 1_000;
    private double permittedMin = Double.NEGATIVE_INFINITY, permittedMax = Double.POSITIVE_INFINITY;

    void reset() {
        permittedMin = Double.NEGATIVE_INFINITY;
        permittedMax = Double.POSITIVE_INFINITY;
    }

    void update(GenericConstraintHandle joint, double position, float rpm, Direction frameAxis,
                double timeStep, double mass, double obstacleMin, double obstacleMax) {
        // Advance with the native physics clock, not a staircase at game-tick rate.
        double velocity = Mth.clamp(rpm * PITCH / 60d, -9.8, 9.8) * frameAxis.getAxisDirection().getStep();
        // A screw cannot be back-driven. Only accept motion that the previous
        // screw rotation permitted, never a solver overshoot caused by an impact.
        double held = Mth.clamp(Mth.clamp(position, permittedMin, permittedMax), obstacleMin, obstacleMax);
        double next = Mth.clamp(held + velocity * timeStep, obstacleMin, obstacleMax);
        permittedMin = Math.min(held, next);
        permittedMax = Math.max(held, next);
        // Rapier treats limits as stationary within its internal solver steps.
        // Give the moving end one substep of lookahead so ordinary travel does
        // not hit a stationary stop. Never accept that lookahead as extra travel:
        // the interval stored above remains the one-step screw command.
        double lookahead = velocity * timeStep;
        joint.setLimit(ConstraintJointAxis.LINEAR_Z,
                Math.max(obstacleMin, permittedMin + Math.min(lookahead, 0) - RailGeometry.CONTACT_TOLERANCE),
                Math.min(obstacleMax, permittedMax + Math.max(lookahead, 0) + RailGeometry.CONTACT_TOLERANCE));
        // Sable exposes only the position motor. This offset supplies the same
        // velocity term as a native PD servo: P*error + D*(commanded - actual v).
        // Drive from the accepted screw position, without accumulating a phase
        // error against a stopped carriage or against this step's own limit.
        double motorTarget = next + DAMPING / STIFFNESS * velocity;
        joint.setMotor(ConstraintJointAxis.LINEAR_Z, motorTarget, STIFFNESS, DAMPING,
                true, mass * MAX_DRIVE_ACCELERATION);
    }
}
