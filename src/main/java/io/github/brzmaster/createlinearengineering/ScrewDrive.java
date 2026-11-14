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
    // The native limits hold position while the motor supplies the drive velocity.
    private static final double STIFFNESS = 1_000;
    private static final double DAMPING = 1_000;
    // A stalled screw may retain at most a small fraction of one thread pitch.
    private static final double MAX_PHASE_ERROR = PITCH / 16;
    private static final double PHASE_CORRECTION_GAIN = 4;
    private int lastDirection;
    private double permittedMin = Double.NEGATIVE_INFINITY, permittedMax = Double.POSITIVE_INFINITY;

    void reset() {
        lastDirection = 0;
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
        int direction = (int) Math.signum(velocity);
        // Recover only the untravelled part of the previous turn. Discard it
        // immediately on stop or reversal, and bound it during physical contact.
        double carry = 0;
        if (direction != 0 && direction == lastDirection) {
            double target = direction > 0 ? permittedMax : permittedMin;
            carry = Mth.clamp(target - held, -MAX_PHASE_ERROR, MAX_PHASE_ERROR);
        }
        lastDirection = direction;
        double step = velocity * timeStep;
        double next = Mth.clamp(held + step + carry, obstacleMin, obstacleMax);
        permittedMin = Math.min(held, next);
        permittedMax = Math.max(held, next);
        // Rapier treats limits as stationary within its internal solver steps.
        // Give the moving end one substep of lookahead so ordinary travel does
        // not hit a stationary stop. Never accept that lookahead as extra travel:
        // the interval stored above remains the one-step screw command.
        joint.setLimit(ConstraintJointAxis.LINEAR_Z,
                Math.max(obstacleMin, permittedMin + Math.min(step, 0) - RailGeometry.CONTACT_TOLERANCE),
                Math.min(obstacleMax, permittedMax + Math.max(step, 0) + RailGeometry.CONTACT_TOLERANCE));
        // Sable exposes only the position motor. This offset supplies the same
        // velocity term as a native PD servo: P*error + D*(commanded - actual v).
        // Correct a shortfall within the native step even when a slow velocity
        // alone rounds away at the body's world coordinate.
        double driveVelocity = velocity + PHASE_CORRECTION_GAIN * carry / timeStep;
        double motorTarget = next + DAMPING / STIFFNESS * driveVelocity;
        joint.setMotor(ConstraintJointAxis.LINEAR_Z, motorTarget, STIFFNESS, DAMPING,
                true, mass * MAX_DRIVE_ACCELERATION);
    }
}
