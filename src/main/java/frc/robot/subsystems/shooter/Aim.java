package frc.robot.subsystems.shooter;

import com.ctre.phoenix6.controls.MotionMagicExpoVoltage;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.hardware.TalonFX;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.units.Angle;
import edu.wpi.first.units.Measure;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.Mechanism2d;
import edu.wpi.first.wpilibj.smartdashboard.MechanismLigament2d;
import edu.wpi.first.wpilibj.smartdashboard.MechanismRoot2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.util.Color;
import edu.wpi.first.wpilibj.util.Color8Bit;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.CtreConfigs;

import static frc.robot.Constants.FieldK.*;
import static edu.wpi.first.units.Units.Degrees;
import static edu.wpi.first.units.Units.Radians;
import static edu.wpi.first.units.Units.Rotations;
import static frc.robot.Constants.AimK.*;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class Aim extends SubsystemBase {
    private final Supplier<Pose3d> m_robotPoseSupplier;

    private final TalonFX m_aim = new TalonFX(kAimId);
    private final CANcoder m_cancoder = new CANcoder(kCancoderId);
    private final MotionMagicExpoVoltage m_request = new MotionMagicExpoVoltage(0);

    private final DCMotor m_aimGearbox = DCMotor.getFalcon500(1);
    private final SingleJointedArmSim m_aimSim = new SingleJointedArmSim(
        m_aimGearbox, kGearRatio, 0.761, Units.inchesToMeters(19.75),
        kMinAngle.in(Radians), kMaxAngle.in(Radians), true, Units.degreesToRadians(0));

    private final Mechanism2d m_mech2d = new Mechanism2d(60, 60);
    private final MechanismRoot2d m_aimPivot = m_mech2d.getRoot("AimPivot", 30, 30);
    private final MechanismLigament2d m_aim2d = m_aimPivot.append(
        new MechanismLigament2d(
            "aim",
            30,
            Units.radiansToDegrees(m_aimSim.getAngleRads()),
            10,
            new Color8Bit(Color.kHotPink)));

    private Measure<Angle> m_targetAngle;
    private Translation3d m_speakerPose;
    private Translation3d m_ampPose;

    public Aim(Supplier<Pose3d> robotPoseSupplier) {
        m_robotPoseSupplier = robotPoseSupplier;
        m_aim.getConfigurator().apply(CtreConfigs.get().m_aimConfigs);
        m_cancoder.getConfigurator().apply(CtreConfigs.get().m_cancoderConfigs);
        m_targetAngle = Degrees.of(0);
        SmartDashboard.putData("Mech2d", m_mech2d);
    }

    private Command toAngle(Measure<Angle> angle) {
        return run(() -> {
            m_aim.setControl(m_request.withPosition(angle.in(Rotations)));
        }).until(() -> m_cancoder.getPosition().getValueAsDouble() == angle.in(Rotations));
    }

    public void getSpeakerPose() {
        var alliance = DriverStation.getAlliance();
        if (alliance.isPresent() && alliance.get() == Alliance.Blue) {
            m_speakerPose = kBlueSpeakerPose;
        } else {
            m_speakerPose = kRedSpeakerPose;
        }
    }

    public Command teleop(DoubleSupplier power) {
        return run(() -> {
            double powerVal = MathUtil.applyDeadband(power.getAsDouble(), 0.1);
            m_targetAngle = m_targetAngle.plus(Degrees.of(powerVal * 1.2));
            m_targetAngle = Degrees
                .of(MathUtil.clamp(m_targetAngle.magnitude(), kMinAngle.magnitude(), kMaxAngle.magnitude()));
            m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations)));
            SmartDashboard.putNumber("aim", m_aim.get());
            SmartDashboard.putNumber("aim position",
                Units.rotationsToDegrees(m_aim.getPosition().getValueAsDouble()));
        });
    }

    public Command goTo90() {
        return runOnce(() -> {
            m_targetAngle = Degrees.of(90);
            m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations)));
        });
    }

    public Command goTo30() {
        return runOnce(() -> {
            m_targetAngle = Degrees.of(30);
            m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations)));
        });
    }

    public Command aimAtSpeaker() {
        return runOnce(() -> {
            var translation = m_robotPoseSupplier.get().getTranslation();
            var poseToSpeaker = m_speakerPose.minus(translation);
            m_targetAngle = Radians.of(Math.atan((poseToSpeaker.getZ()) / (poseToSpeaker.getX())));
            m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations)));
        });
        // var toTarget = runOnce(() ->
        // m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations))));

        // return Commands.sequence(
        // getTarget,
        // toTarget);
    }

    public Command aimAtAmp() {
        var getTarget = runOnce(() -> {
            var translation = m_robotPoseSupplier.get().getTranslation();
            var poseToAmp = m_ampPose.minus(translation);
            m_targetAngle = Radians.of(Math.atan((poseToAmp.getZ()) / (poseToAmp.getX())));
        });
        var toTarget = runOnce(() -> m_aim.setControl(m_request.withPosition(m_targetAngle.in(Rotations))));

        return Commands.sequence(
            getTarget,
            toTarget);
    }

    /**
     * IF RETURN 0, bad :(
     * 
     * @return aprilTag ID of closest trap
     */
    public int getTrapId() {
        double center;
        double right;
        double left;

        if (DriverStation.getAlliance().isPresent() && DriverStation.getAlliance().get().equals(Alliance.Blue)) {
            Translation3d centerTag = kFieldLayout.getTagPose(kBlueCenterTrapId).get().getTranslation();
            Translation3d rightTag = kFieldLayout.getTagPose(kBlueRightTrapId).get().getTranslation();
            Translation3d leftTag = kFieldLayout.getTagPose(kBlueLeftTrapId).get().getTranslation();

            center = centerTag.getDistance(m_robotPoseSupplier.get().getTranslation());
            right = rightTag.getDistance(m_robotPoseSupplier.get().getTranslation());
            left = leftTag.getDistance(m_robotPoseSupplier.get().getTranslation());

            if (center < right && center < left) {
                return kBlueCenterTrapId;
            } else if (right < center && right < left) {
                return kBlueRightTrapId;
            } else {
                return kBlueLeftTrapId;
            }
        } else if (DriverStation.getAlliance().isPresent() && DriverStation.getAlliance().get().equals(Alliance.Red)) {
            Translation3d centerTag = kFieldLayout.getTagPose(kRedCenterTrapId).get().getTranslation();
            Translation3d rightTag = kFieldLayout.getTagPose(kRedRightTrapId).get().getTranslation();
            Translation3d leftTag = kFieldLayout.getTagPose(kRedLeftTrapId).get().getTranslation();

            center = centerTag.getDistance(m_robotPoseSupplier.get().getTranslation());
            right = rightTag.getDistance(m_robotPoseSupplier.get().getTranslation());
            left = leftTag.getDistance(m_robotPoseSupplier.get().getTranslation());

            if (center < right && center < left) {
                return kRedCenterTrapId;
            } else if (right < center && right < left) {
                return kRedRightTrapId;
            } else {
                return kRedLeftTrapId;
            }
        }
        return 0;
    }

    public Command aimAtTrap() {
        var getAngleCmd = run(() -> {
            var translation = m_robotPoseSupplier.get();
            var poseToTrap = kFieldLayout.getTagPose(getTrapId()).get().minus(translation);
            m_targetAngle = Radians.of(Math.atan((poseToTrap.getZ()) / (poseToTrap.getX())));
        });
        var toAngleCmd = toAngle(m_targetAngle);

        return Commands.repeatingSequence(
            getAngleCmd,
            toAngleCmd);
    }

    public void simulationPeriodic() {
        m_aim.getSimState().setSupplyVoltage(12);
        var volts = m_aim.getSimState().getMotorVoltage();
        m_aimSim.setInputVoltage(volts);
        m_aimSim.update(0.020);
        double angle = Units.radiansToRotations(m_aimSim.getAngleRads());
        double velocity = Units.radiansToRotations(m_aimSim.getVelocityRadPerSec());
        m_aim.getSimState().setRawRotorPosition(angle);
        m_aim.getSimState().setRotorVelocity(velocity);
        m_cancoder.getSimState().setRawPosition(angle);
        m_cancoder.getSimState().setVelocity(velocity);
        m_aim2d.setAngle(Units.rotationsToDegrees(
            m_aim.getPosition().getValueAsDouble())); // TODO: make this render correctly with the real robot too
        SmartDashboard.putNumber("cancoder position",
            m_cancoder.getPosition().getValueAsDouble());
        SmartDashboard.putNumber("sim voltage", volts);
        SmartDashboard.putNumber("sim velocity", m_aimSim.getVelocityRadPerSec());
        SmartDashboard.putNumber("sim angle", angle);
        SmartDashboard.putNumber("target", m_targetAngle.in(Degrees));
    }
}
