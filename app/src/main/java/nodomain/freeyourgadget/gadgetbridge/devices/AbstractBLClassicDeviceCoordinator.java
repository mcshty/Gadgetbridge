package nodomain.freeyourgadget.gadgetbridge.devices;

public abstract class AbstractBLClassicDeviceCoordinator extends AbstractDeviceCoordinator {
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.BT_CLASSIC;
    }
}
